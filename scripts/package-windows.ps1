<#
.SYNOPSIS
  Empacota o Live Screenshot Capture para Windows usando jpackage.
.DESCRIPTION
  Gera app-image (sempre funciona, JDK 21+), ou instalador nativo:
   - exe : requer Inno Setup 6+ (iscc.exe no PATH)
   - msi : requer WiX Toolset 3.x
  O JDK usado vem do arquivo .env (JAVA_HOME), com fallback para o
  JAVA_HOME do ambiente e depois para o java do PATH. Recomendado:
  Azul Zulu 27 FX (Java + JavaFX no mesmo JDK), ex:
    JAVA_HOME=C:\Program Files\Zulu\zulu-27
  Quando o JDK ja contem JavaFX (jmods/javafx.*.jmod), o script remove
  os jars Maven do JavaFX do staging e monta o runtime com --add-modules
  calculado via jdeps (runtime menor e sem duplicidade). Caso contrario,
  usa o modo legado (JavaFX via classpath, como antes).
  Uso:
    .\scripts\package-windows.ps1                  # app-image em dist\
    .\scripts\package-windows.ps1 -Type exe        # Setup EXE via jpackage+Inno
    .\scripts\package-windows.ps1 -Type msi        # MSI via jpackage+WiX
    .\scripts\package-windows.ps1 -SkipBuild       # reaproveita target\*.jar
  Para instalador Inno totalmente customizado (atalhos, autostart,
  pagina de diretorio em PT), use apos o app-image:
    iscc installer\windows\LiveScreenshotCapture.iss
#>
param(
  [ValidateSet('app-image', 'exe', 'msi')]
  [string]$Type = 'app-image',
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# --- 1. Resolve JDK: .env > $env:JAVA_HOME > PATH ---
$dotEnv = Join-Path $root ".env"
if (Test-Path $dotEnv) {
  Get-Content $dotEnv | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $k = $line.Substring(0, $idx).Trim()
    $v = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
    if ($k -ne "") { Set-Item -Path ("env:" + $k) -Value $v }
  }
  Write-Host "Lido: $dotEnv" -ForegroundColor DarkGray
}

$javaHome = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHome)) {
  $j = Get-Command java -ErrorAction SilentlyContinue
  if ($j) { $javaHome = Split-Path -Parent (Split-Path -Parent $j.Source) }
}
if ([string]::IsNullOrWhiteSpace($javaHome) -or !(Test-Path (Join-Path $javaHome "bin\java.exe"))) {
  throw "JDK nao encontrado. Crie um .env com JAVA_HOME (ex: JAVA_HOME=C:\Program Files\Zulu\zulu-27)."
}
$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome "bin") + ";" + $env:PATH

$javaBin = Join-Path $javaHome "bin\java.exe"
$jpackageBin = Join-Path $javaHome "bin\jpackage.exe"
$jdepsBin = Join-Path $javaHome "bin\jdeps.exe"
if (!(Test-Path $jpackageBin)) { throw "jpackage nao encontrado em $jpackageBin. Use um JDK Full (com jpackage)." }

Write-Host "JDK: $javaHome" -ForegroundColor Cyan
$prevPref = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
  $verLines = & $javaBin -version 2>&1
} finally {
  $ErrorActionPreference = $prevPref
}
foreach ($vl in $verLines) { Write-Host ("  " + $vl) -ForegroundColor DarkGray }

# --- 2. Metadata (manter versao sincronizada com pom.xml) ---
$AppName = "LiveScreenshotCapture"
$Vendor = "Balbucio"
$Description = "Live Screenshot Capture - capture livestream moments, even seconds late."
$Copyright = "Copyright (c) 2026 Balbucio"
$MainClass = "balbucio.livescreenshotcapture.Main"

# Versao do pom.xml, sanitizada para o formato X.Y[.Z] exigido pelo jpackage.
[xml]$pom = Get-Content (Join-Path $root "pom.xml")
$rawVersion = $pom.project.version
$AppVersion = ($rawVersion -replace '-SNAPSHOT', '' -replace '[^0-9.]', '')
if ($AppVersion -notmatch '^\d+(\.\d+){1,2}$') { $AppVersion = "1.0.0" }
if (($AppVersion.Split('.')).Count -eq 2) { $AppVersion += ".0" }

Write-Host "App: $AppName Versao: $AppVersion (pom: $rawVersion) Tipo: $Type" -ForegroundColor Cyan

# --- 3. Detecta JavaFX embutido no JDK (ex: Zulu FX) ---
$fxJmods = Join-Path $javaHome "jmods"
$hasFx = Test-Path (Join-Path $fxJmods "javafx.controls.jmod")
if ($hasFx) {
  Write-Host "JavaFX detectado no JDK (modo runtime enxuto)." -ForegroundColor Green
} else {
  Write-Host "JDK sem JavaFX embutido: usando modo legado (JavaFX via Maven)." -ForegroundColor DarkYellow
}

# --- 4. Build ---
if (-not $SkipBuild) {
  Write-Host "==> mvn package (JAVA_HOME=$javaHome)..." -ForegroundColor Yellow
  mvn -q package -DskipTests
  if ($LASTEXITCODE -ne 0) { throw "mvn package falhou" }
}

$stageDir = "target\dist-input"
New-Item -ItemType Directory -Force -Path $stageDir | Out-Null
Copy-Item "target\*.jar" $stageDir -Force
Write-Host "==> copiando dependencias runtime..." -ForegroundColor Yellow
# IMPORTANTE: o valor de -D precisa estar entre aspas: PowerShell nao expande
# $variavel apos "=" em argumento de comando nativo (mvn.cmd) se nao estiver citado.
mvn -q dependency:copy-dependencies "-DoutputDirectory=$stageDir" -DincludeScope=runtime
if ($LASTEXITCODE -ne 0) { throw "copy-dependencies falhou" }

$jarFile = Get-ChildItem "$stageDir\live-screenshot-capture-*.jar" | Select-Object -First 1
if (-not $jarFile) { throw "JAR principal nao encontrado" }
$jar = $jarFile.Name
Write-Host "Main JAR: $jar"

$stageFull = Join-Path $root $stageDir

# Se o JDK ja traz JavaFX, os jars Maven do openjfx sao redundantes: remove.
if ($hasFx) {
  $fxJars = @(Get-ChildItem -Path $stageFull -Filter "javafx*.jar" -ErrorAction SilentlyContinue)
  foreach ($fj in $fxJars) { Remove-Item -Force $fj.FullName }
  $left = @(Get-ChildItem -Path $stageFull -Filter "javafx*.jar" -ErrorAction SilentlyContinue)
  if ($left.Count -gt 0) { throw "Falha ao remover jars do JavaFX do staging." }
  Write-Host "Removidos $($fxJars.Count) jars Maven do JavaFX (runtime ja fornece)." -ForegroundColor DarkGray
}

New-Item -ItemType Directory -Force -Path dist | Out-Null
$oldApp = Join-Path $root "dist\LiveScreenshotCapture"
if (Test-Path $oldApp) {
  Write-Host "==> removendo app-image anterior..." -ForegroundColor Yellow
  Remove-Item -Recurse -Force $oldApp
}

# --- 5. Modulos do runtime (somente modo FX): jdeps + baseline ---
# jdeps --print-module-deps as vezes retorna o conjunto minimo (sem java.desktop,
# por ex.), entao unimos com um baseline curado. stdout/stderr sao capturados
# em arquivos separados para warnings nunca contaminarem a lista.
$moduleArgs = @()
if ($hasFx -and (Test-Path $jdepsBin)) {
  $deps = ""
  try {
    $allJars = ((Get-ChildItem -Path $stageFull -Filter "*.jar") | ForEach-Object { $_.FullName }) -join ";"
    $tmpOut = Join-Path ([System.IO.Path]::GetTempPath()) "lsc-jdeps-out.txt"
    $tmpErr = Join-Path ([System.IO.Path]::GetTempPath()) "lsc-jdeps-err.txt"
    if (Test-Path $tmpOut) { Remove-Item -Force $tmpOut }
    if (Test-Path $tmpErr) { Remove-Item -Force $tmpErr }
    $prevJdep = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
      & $jdepsBin --ignore-missing-deps --multi-release 21 --print-module-deps --class-path "$allJars" (Join-Path $stageFull $jar) > $tmpOut 2> $tmpErr
      $jdepsCode = $LASTEXITCODE
    } finally {
      $ErrorActionPreference = $prevJdep
    }
    if ($jdepsCode -ne 0) { throw "jdeps exit $jdepsCode" }
    $deps = ""
    if (Test-Path $tmpOut) { $deps = ((Get-Content $tmpOut -Raw) | Out-String).Trim() }
    if ($deps -match "[\s\\/]") { throw "jdeps retornou texto inesperado" }
  } catch {
    Write-Host "Aviso: jdeps indisponivel, usando baseline manual." -ForegroundColor DarkYellow
    $deps = ""
  }
  $mods = @()
  if ($deps -ne "") { $mods = @($deps.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" }) }
  $baseline = @("java.base", "java.desktop", "java.logging", "java.xml", "java.prefs",
    "java.datatransfer", "java.management", "java.naming", "jdk.unsupported",
    "javafx.controls", "javafx.swing", "javafx.media")
  foreach ($m in $baseline) { if ($mods -notcontains $m) { $mods += $m } }
  $fullList = $mods -join ","
  if ($fullList -notmatch "^[A-Za-z0-9_.,]+$") { throw "Lista de modulos invalida, abortando." }
    Write-Host "Modulos: $fullList" -ForegroundColor DarkGray
    $moduleArgs = @('--module-path', $fxJmods, '--add-modules', $fullList)
}

# --- 6. Icone opcional (packaging\windows\app.ico) ---
$iconArg = @()
$iconPath = Join-Path $root "packaging\windows\app.ico"
if (Test-Path $iconPath) {
  $iconArg = @('--icon', $iconPath)
} else {
  Write-Host "Aviso: sem icone customizado, seguindo com o padrao." -ForegroundColor DarkYellow
  Write-Host "Dica: coloque um .ico 256x256 em packaging\windows\app.ico" -ForegroundColor DarkYellow
}

# --- 7. jpackage ---
$jargs = @(
  '--type', $Type,
  '--input', $stageDir,
  '--dest', 'dist',
  '--name', $AppName,
  '--app-version', $AppVersion,
  '--vendor', $Vendor,
  '--description', $Description,
  '--copyright', $Copyright,
  '--main-jar', $jar,
  '--main-class', $MainClass,
  '--java-options', '-Dfile.encoding=UTF-8',
  '--java-options', '--enable-native-access=ALL-UNNAMED'
) + $moduleArgs + $iconArg

# --- Opcoes de instalador Windows (exe/msi) ---
if ($Type -ne 'app-image') {
  $jargs += @(
    '--win-menu',
    '--win-menu-group', 'Live Screenshot Capture',
    '--win-shortcut',
    '--win-shortcut-prompt',
    '--win-dir-chooser',
    '--win-per-user-install'
  )
}

Write-Host "==> jpackage ($jpackageBin)..." -ForegroundColor Yellow
& $jpackageBin @jargs
if ($LASTEXITCODE -ne 0) {
  if ($Type -eq 'exe') {
    throw "jpackage exe falhou. Instale o Inno Setup 6+ com iscc.exe no PATH."
  }
  if ($Type -eq 'msi') {
    throw "jpackage msi falhou. Instale o WiX Toolset 3.x."
  }
  throw "jpackage falhou."
}

Write-Host ""
if ($Type -eq 'app-image') {
  Write-Host "App image pronta em dist\LiveScreenshotCapture" -ForegroundColor Green
  Write-Host "Instalador customizado (recomendado):" -ForegroundColor Green
  Write-Host "  iscc installer\windows\LiveScreenshotCapture.iss" -ForegroundColor White
} else {
  $pattern = "dist\*" + $AppName + "*." + $Type
  $outs = Get-ChildItem $pattern
  foreach ($f in $outs) {
    Write-Host "OK" -ForegroundColor Green
    Write-Host $f.FullName
  }
}
