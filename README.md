# Live Screenshot Capture

Lightweight Java 21 / JavaFX desktop app to capture screenshots from livestreams —
including moments you noticed a second too late (circular frame buffer + burst).

## Run (dev)

```powershell
mvn verify
mvn javafx:run
```

## Default hotkeys

| Action | Shortcut |
|---|---|
| Capture camera | Ctrl + Shift + F9 |
| Capture full stream | Ctrl + Shift + F10 |
| Camera burst (-1s..+1s) | Ctrl + Shift + F11 |
| Switch layout 1..9 | Ctrl + 1 .. 9 |

All capture hotkeys are editable in Settings → Hotkeys. `Ctrl+1..9` always switches layouts.

## Data locations (Windows)

- Profiles: `%APPDATA%\LiveScreenshotCapture\profiles\*.json`
- Settings: `%APPDATA%\LiveScreenshotCapture\settings.json`
- Logs: `%APPDATA%\LiveScreenshotCapture\logs\app.log` (rolling, 14 days / 100 MB)
- Captures: `captures\<profile>\<yyyy-MM-dd>\{camera,stream,burst}\` (change in Settings)

Only one instance runs at a time (guarded by `app.lock`).
Captures are auto-cleaned oldest-first when over the quota in Settings → General
(default 2048 MB, 0 = unlimited).

## Package (Windows)

Requires JDK 21+ with `jpackage`. Recommended: **Azul Zulu 27 FX**
(Java + JavaFX in a single JDK). Point to it via a local `.env`
(git-ignored):

```
JAVA_HOME=C:\Program Files\Zulu\zulu-27
```

`scripts\package-windows.ps1` resolves the JDK in this order:
`.env` `JAVA_HOME` > `$env:JAVA_HOME` > `java` on PATH, and always
invokes that JDK's `jpackage`/`jdeps` explicitly. When the JDK already
bundles JavaFX (Zulu FX), the script drops the Maven `javafx-*.jar`
from the staging dir and builds a trimmed runtime with `--add-modules`
(computed via `jdeps` + a curated baseline with `java.desktop`,
`javafx.controls/swing/media`, etc.); otherwise it falls back to the
legacy mode (JavaFX from Maven jars on the classpath).

```powershell
# 1. App image (sempre funciona, sem ferramentas extras)
.\scripts\package-windows.ps1

# 2a. Instalador EXE via jpackage (requer Inno Setup 6+ com iscc no PATH)
.\scripts\package-windows.ps1 -Type exe

# 2b. Instalador MSI via jpackage (requer WiX Toolset 3.x)
.\scripts\package-windows.ps1 -Type msi

# 2c. Instalador Inno customizado (recomendado: PT-BR, atalhos, autostart)
.\scripts\package-windows.ps1          # gera dist\LiveScreenshotCapture
iscc installer\windows\LiveScreenshotCapture.iss
```

| Metodo | Requer | Resultado |
|---|---|---|
| `app-image` | so JDK | `dist\LiveScreenshotCapture\LiveScreenshotCapture.exe` portavel |
| `jpackage exe` | Inno Setup 6+ | Setup EXE simples (menu, atalho, per-user) |
| `jpackage msi` | WiX 3.x | Pacote MSI corporativo |
| `LiveScreenshotCapture.iss` | Inno Setup 6+ | `dist\LiveScreenshotCapture-Setup-1.0.0.exe` com assistente PT-BR/EN, icone desktop e iniciar com Windows (opcionais) |

Icone customizado: coloque um `app.ico` em `packaging\windows\` (ver `packaging\windows\README.md`).
Versao do instalador e derivada do `pom.xml` (`1.0-SNAPSHOT` -> `1.0.0`); alinhe `MyAppVersion` no `.iss` ao fazer release.
For "Launch on Windows startup" to work, run the packaged launcher —
in IDE mode the toggle explains the limitation.
