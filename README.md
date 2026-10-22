<p align="center">
  <img src="composeApp/icon/circle_app_icon.png" width="140" alt="DplayMusic"/>
</p>

<h1 align="center">DplayMusic</h1>
<p align="center"><b>Tu música, sin límites.</b> — By NiuCode</p>

DplayMusic es un reproductor de música para **Android, Windows, macOS y Linux**, con acceso mediante código del panel DplayMusic.

## Descargas (escritorio)

Ve a **[Releases](https://github.com/edaleplay/DplayMusic/releases/latest)**:

| Sistema | Archivo | Instalación |
|---|---|---|
| Windows 10/11 | `DplayMusic-Windows-installer.zip` | Descomprime y ejecuta `install.bat` |
| macOS 15+ (Apple Silicon) | `DplayMusic-*-mac-aarch64.dmg` | Arrastra DplayMusic a Aplicaciones |
| macOS 15+ (Intel) | `DplayMusic-*-mac-amd64.dmg` | Arrastra DplayMusic a Aplicaciones |
| Linux (x86_64) | `DplayMusic-x86_64.AppImage` | `chmod +x` y ejecútalo |

Al abrir la app por primera vez se pide el **código de acceso**.

## Compilar

Los instaladores se generan con GitHub Actions (`.github/workflows/dplaymusic-desktop.yml`):
**Actions → DplayMusic Desktop → Run workflow**, o publica un tag `vX.Y.Z` para crear un Release.
Requiere el secret `CONVEYOR_SIGNING_KEY`.

## Créditos y licencia

DplayMusic está basado en **[SimpMusic](https://github.com/maxrave-dev/SimpMusic)**,
© 2023-2026 Tuan Minh Nguyen Duc (maxrave-dev) y colaboradores, distribuido bajo la
**GNU General Public License v3.0**. Este proyecto, como obra derivada, se distribuye bajo la misma
licencia (ver [LICENSE](LICENSE)); el código fuente completo está en este repositorio.

Packaged with [Conveyor](https://hydraulic.dev).
