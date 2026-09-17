; =====================================================================
; Live Screenshot Capture — Instalador Inno Setup (Windows x64)
; Uso:
;   1. .\scripts\package-windows.ps1            (gera dist\LiveScreenshotCapture)
;   2. iscc installer\windows\LiveScreenshotCapture.iss
; Saída: dist\LiveScreenshotCapture-Setup-<versão>.exe
;
; Requer: Inno Setup 6+ (https://jrsoftware.org/isinfo.php)
; Opcional: packaging\windows\app.ico (ícone do setup + atalhos).
; =====================================================================

#define MyAppName "Live Screenshot Capture"
#define MyAppExe "LiveScreenshotCapture.exe"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Balbucio"
#define MyAppURL "https://github.com/"
#define MyAppId "Balbucio.LiveScreenshotCapture"

[Setup]
AppId={#MyAppId}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
DefaultDirName={autopf}\LiveScreenshotCapture
DefaultGroupName=Live Screenshot Capture
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\..\dist
OutputBaseFilename=LiveScreenshotCapture-Setup-{#MyAppVersion}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
UninstallDisplayName={#MyAppName}
UninstallDisplayIcon={app}\{#MyAppExe}
; Descomente apos adicionar packaging\windows\app.ico:
; SetupIconFile=..\..\packaging\windows\app.ico
; WizardImageFile=compiler:WizModernImage-IS.bmp
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "portuguese"; MessagesFile: "compiler:Languages\Portuguese.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "startupicon"; Description: "Iniciar com o Windows"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Tudo que o jpackage gerou no app-image (runtime + app + launcher).
Source: "..\..\dist\LiveScreenshotCapture\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Live Screenshot Capture"; Filename: "{app}\{#MyAppExe}"
Name: "{group}\{cm:UninstallProgram,Live Screenshot Capture}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Live Screenshot Capture"; Filename: "{app}\{#MyAppExe}"; Tasks: desktopicon
Name: "{userstartup}\Live Screenshot Capture"; Filename: "{app}\{#MyAppExe}"; Tasks: startupicon

[Run]
Filename: "{app}\{#MyAppExe}"; Description: "{cm:LaunchProgram,Live Screenshot Capture}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Remove logs/lock deixados em %APPDATA% apenas se o usuário confirmar?
; Mantido conservador: não apaga perfis nem capturas.
Type: files; Name: "{userappdata}\LiveScreenshotCapture\app.lock"

[Code]
function InitializeSetup(): Boolean;
begin
  Result := True;
end;
