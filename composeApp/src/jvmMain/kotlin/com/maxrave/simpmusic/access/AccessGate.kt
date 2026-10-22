package com.maxrave.simpmusic.access

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.circle_app_icon

private val BrandRed = Color(0xFFDC023E)
private val BrandRedLight = Color(0xFFFF2D55)
private val PageBg = Color(0xFF0B0B0D)
private val CardBg = Color(0xFF141417)
private val CardStroke = Color(0xFF2A2A30)
private val FieldBg = Color(0xFF26262B)
private val Muted = Color(0xFFAAAAAA)

private sealed interface GateState {
    data object Checking : GateState

    data class NeedCode(
        val message: String? = null,
    ) : GateState

    data object Granted : GateState
}

/** How often a running app re-checks its code with the panel. */
private const val RECHECK_MS = 6L * 60 * 60 * 1000

/**
 * DplayMusic access gate: shows [content] only after the panel accepts this computer's code.
 * A saved code is re-validated silently at launch and every [RECHECK_MS]; when the panel cannot be
 * reached, a code whose saved expiry has not passed keeps working offline.
 */
@Composable
fun AccessGate(content: @Composable () -> Unit) {
    var state by remember {
        mutableStateOf<GateState>(if (DplayAccess.savedCode != null) GateState.Checking else GateState.NeedCode())
    }

    LaunchedEffect(Unit) {
        while (true) {
            val code = DplayAccess.savedCode
            if (code != null) {
                when (val r = DplayAccess.validate(code)) {
                    is DplayAccess.Result.Granted -> state = GateState.Granted
                    is DplayAccess.Result.Denied -> {
                        if (r.clearCode) DplayAccess.clear()
                        state = GateState.NeedCode(r.message)
                    }
                    is DplayAccess.Result.NetworkError ->
                        state =
                            if (DplayAccess.isNotExpired(DplayAccess.savedExpiresAt)) {
                                GateState.Granted
                            } else {
                                GateState.NeedCode(r.message)
                            }
                }
            }
            delay(RECHECK_MS)
        }
    }

    when (val s = state) {
        GateState.Granted -> content()
        GateState.Checking -> AccessScreen(initialMessage = null, checking = true, onGranted = { state = GateState.Granted })
        is GateState.NeedCode -> AccessScreen(initialMessage = s.message, checking = false, onGranted = { state = GateState.Granted })
    }
}

@Composable
private fun AccessScreen(
    initialMessage: String?,
    checking: Boolean,
    onGranted: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember(initialMessage) { mutableStateOf(initialMessage) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val value = code.trim()
        if (value.isEmpty()) {
            message = "Por favor ingresa tu código."
            return
        }
        if (loading) return
        loading = true
        message = "Conectando con el servidor..."
        scope.launch {
            when (val r = DplayAccess.validate(value)) {
                is DplayAccess.Result.Granted -> {
                    message = "Conectado ✅" + if (r.expiresAt.isNotBlank()) " Expira en: ${r.expiresAt}" else ""
                    loading = false
                    onGranted()
                }
                is DplayAccess.Result.Denied -> {
                    message = r.message
                    loading = false
                }
                is DplayAccess.Result.NetworkError -> {
                    message = r.message
                    loading = false
                }
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF3A0010), PageBg),
                        radius = 1400f,
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(400.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBg)
                    .border(1.dp, CardStroke, RoundedCornerShape(24.dp))
                    .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.circle_app_icon),
                contentDescription = "DplayMusic",
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("DplayMusic", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (checking) "Verificando tu acceso..." else "Ingresa tu código de acceso",
                color = Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            if (checking) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(color = BrandRedLight, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                return@Column
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.trim().take(64) },
                singleLine = true,
                enabled = !loading,
                placeholder = { Text("Ej: 123456", color = Color(0xFF8E8E93)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = RoundedCornerShape(14.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.White,
                        focusedContainerColor = FieldBg,
                        unfocusedContainerColor = FieldBg,
                        disabledContainerColor = FieldBg,
                        focusedBorderColor = BrandRedLight,
                        unfocusedBorderColor = Color(0xFF3A3A40),
                        cursorColor = BrandRedLight,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.horizontalGradient(listOf(BrandRed, BrandRedLight)))
                        .clickable(enabled = !loading) { submit() },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                } else {
                    Text("CONECTAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
                }
            }
            message?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            Text("By NiuCode", color = Color(0xFF5A5A62), fontSize = 12.sp)
        }
    }
}
