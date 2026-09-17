# Build a Windows app image with jpackage (JDK 21+, no WiX needed for app-image).
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

mvn -q package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }

$input = "target\dist-input"
New-Item -ItemType Directory -Force -Path $input | Out-Null
Copy-Item "target\*.jar" $input -Force
mvn -q dependency:copy-dependencies -DoutputDirectory=$input -DincludeScope=runtime
if ($LASTEXITCODE -ne 0) { throw "copy-dependencies failed" }

$jar = (Get-ChildItem "$input\live-screenshot-capture-*.jar" | Select-Object -First 1).Name

New-Item -ItemType Directory -Force -Path dist | Out-Null

jpackage `
  --type app-image `
  --input $input `
  --dest dist `
  --name LiveScreenshotCapture `
  --app-version 1.0.0 `
  --main-jar $jar `
  --main-class balbucio.livescreenshotcapture.Main `
  --java-options "-Dfile.encoding=UTF-8"

Write-Host "App image ready at dist\LiveScreenshotCapture" -ForegroundColor Green
