package com.maxrave.simpmusic.access

import com.maxrave.logger.Logger
import com.maxrave.simpmusic.machineId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import java.util.prefs.Preferences

/**
 * DplayMusic access control: the same panel API the Android app uses.
 *
 * Request: `POST code=<code>&device_id=<id>` (form-urlencoded).
 * Response: `{ "ok": bool, "reason": "not_found|expired|max_devices_reached|db_not_found|server_error",
 *              "expires_at": "yyyy-MM-dd[ HH:mm:ss]", "days_left": int }`.
 */
object DplayAccess {
    private const val TAG = "DplayAccess"
    const val API_URL = "https://spotify.rebrandapps.xyz/login/api/spotube_api.php"

    private const val KEY_CODE = "saved_code"
    private const val KEY_EXPIRES = "expires_at"
    private const val KEY_DEVICE = "device_id"

    private val prefs: Preferences by lazy { Preferences.userRoot().node("DplayMusic/Access") }

    private val http: HttpClient by lazy {
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    sealed interface Result {
        data class Granted(
            val expiresAt: String,
            val daysLeft: Int?,
        ) : Result

        data class Denied(
            val message: String,
            val clearCode: Boolean,
        ) : Result

        data class NetworkError(
            val message: String,
        ) : Result
    }

    val savedCode: String?
        get() = prefs.get(KEY_CODE, null)?.takeIf { it.isNotBlank() }

    /** Expiry date stored by the last successful validation, if any. */
    val savedExpiresAt: String?
        get() = prefs.get(KEY_EXPIRES, null)?.takeIf { it.isNotBlank() }

    fun clear() {
        prefs.remove(KEY_CODE)
        prefs.remove(KEY_EXPIRES)
        runCatching { prefs.flush() }
    }

    /**
     * A stable id for this computer: a hash of the OS machine id when available, otherwise a random id
     * persisted on first use. Blocking.
     */
    private fun deviceId(): String {
        prefs.get(KEY_DEVICE, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = "pc-" + (machineId()?.take(24) ?: UUID.randomUUID().toString().replace("-", "").take(24))
        prefs.put(KEY_DEVICE, id)
        runCatching { prefs.flush() }
        return id
    }

    /** True when [date] (yyyy-MM-dd…) is today or later. Unparseable dates count as valid. */
    fun isNotExpired(date: String?): Boolean {
        if (date.isNullOrBlank()) return true
        return runCatching { !LocalDate.parse(date.trim().take(10)).isBefore(LocalDate.now()) }.getOrDefault(true)
    }

    suspend fun validate(code: String): Result =
        withContext(Dispatchers.IO) {
            val trimmed = code.trim()
            try {
                val body =
                    "code=" + URLEncoder.encode(trimmed, "UTF-8") +
                        "&device_id=" + URLEncoder.encode(deviceId(), "UTF-8")
                val request =
                    HttpRequest
                        .newBuilder(URI.create(API_URL))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("Accept", "application/json")
                        .header("Cache-Control", "no-cache")
                        .header("User-Agent", "DplayMusicDesktop")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                Logger.i(TAG, "HTTP ${response.statusCode()}")
                val obj: JsonObject =
                    runCatching { json.parseToJsonElement(response.body()).jsonObject }.getOrNull()
                        ?: return@withContext Result.Denied("Respuesta inválida del servidor.", clearCode = false)
                val ok = obj["ok"].asBoolean()
                val reason = obj["reason"].asString().orEmpty()
                val expiresAt = obj["expires_at"].asString().orEmpty()
                val daysLeft = obj["days_left"].asString()?.toIntOrNull()
                Logger.i(TAG, "ok=$ok reason=$reason expires=$expiresAt daysLeft=$daysLeft")
                if (ok && isNotExpired(expiresAt)) {
                    prefs.put(KEY_CODE, trimmed)
                    prefs.put(KEY_EXPIRES, expiresAt)
                    runCatching { prefs.flush() }
                    Result.Granted(expiresAt, daysLeft)
                } else {
                    when (reason) {
                        "max_devices_reached" -> Result.Denied("❌ Límite de dispositivos alcanzado.", clearCode = true)
                        "not_found" -> Result.Denied("Código inválido ❌", clearCode = true)
                        "expired" -> Result.Denied("Código expirado ❌\nExpiró: $expiresAt", clearCode = true)
                        "db_not_found" -> Result.Denied("Error de configuración del servidor.", clearCode = false)
                        "server_error" -> Result.Denied("Error del servidor.", clearCode = false)
                        else ->
                            if (ok) {
                                Result.Denied("Código expirado ❌\nExpiró: $expiresAt", clearCode = true)
                            } else {
                                Result.Denied("Acceso denegado ❌", clearCode = true)
                            }
                    }
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Connection error: ${e.javaClass.simpleName}")
                Result.NetworkError("Error de conexión con el servidor.\nRevisa tu internet e inténtalo de nuevo.")
            }
        }

    private fun kotlinx.serialization.json.JsonElement?.asString(): String? = (this as? JsonPrimitive)?.content

    private fun kotlinx.serialization.json.JsonElement?.asBoolean(): Boolean {
        val p = this as? JsonPrimitive ?: return false
        return when (p.content.lowercase()) {
            "true", "1", "yes" -> true
            else -> false
        }
    }
}
