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

## Package (Windows app image)

Requires JDK 21+ with `jpackage` (and WiX only for `.msi`):

```powershell
.\scripts\package-windows.ps1
```

This builds an app image under `dist\LiveScreenshotCapture` with a native launcher.
For "Launch on Windows startup" to work, run the packaged launcher —
in IDE mode the toggle explains the limitation.
