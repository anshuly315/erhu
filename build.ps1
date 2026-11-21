# One-click build for Windows. Run in PowerShell from this folder:  .\build.ps1
# Finds the Android SDK and a JDK, builds the debug APK, copies it to .\dist and saves any errors
# to build-errors.txt (send me that file if the build fails).
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Android SDK
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path $sdk)) { Write-Host "Android SDK not found at $sdk. Install Android Studio first, then run again."; exit 1 }
if (-not (Get-ChildItem (Join-Path $sdk "platforms") -Directory -Filter "android-37*" -ErrorAction SilentlyContinue)) {
  Write-Host "SDK Platform 37 is missing. Trying to install it..."
  $sm = Get-ChildItem (Join-Path $sdk "cmdline-tools") -Recurse -Filter sdkmanager.bat -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($sm) { "y" * 10 | & $sm.FullName "platforms;android-37" | Out-Null } else { Write-Host "Open Android Studio > SDK Manager and install Android SDK Platform 37, then run again."; exit 1 }
}
"sdk.dir=" + ($sdk -replace '\\','/') | Set-Content local.properties -Encoding ASCII

# JDK: use the first folder that really contains bin\java.exe
function Find-Jdk {
  $c = @()
  if ($env:JAVA_HOME) { $c += $env:JAVA_HOME }
  $c += "$env:ProgramFiles\Android\Android Studio\jbr", "$env:ProgramFiles\Android\Android Studio1\jbr", "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
  foreach ($root in @("$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Zulu", "$env:ProgramFiles\Amazon Corretto")) {
    if (Test-Path $root) { $c += (Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match "jdk|jre" } | Sort-Object Name -Descending | ForEach-Object { $_.FullName }) }
  }
  foreach ($p in $c) { if ($p -and (Test-Path (Join-Path $p "bin\java.exe"))) { return $p } }
  return $null
}
$jdk = Find-Jdk
if (-not $jdk) {
  Write-Host "No working JDK found. Installing Microsoft OpenJDK 21 with winget..."
  winget install --id Microsoft.OpenJDK.21 -e --accept-package-agreements --accept-source-agreements
  $jdk = Find-Jdk
}
if (-not $jdk) { Write-Host "Still no JDK. Install JDK 21 from https://learn.microsoft.com/java/openjdk/download , reopen PowerShell, run again."; exit 1 }
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
Write-Host "Using JDK: $jdk"
& "$jdk\bin\java.exe" -version

if (-not (Select-String -Path gradle.properties -Pattern "isFullBuild" -Quiet)) { Add-Content gradle.properties "`nisFullBuild=true" }

Write-Host "Building (first run downloads a lot and can take 15 to 30 minutes)..."
& .\gradlew.bat exportLibraryDefinitions --stacktrace 2>&1 | Tee-Object -FilePath build-log.txt
& .\gradlew.bat :androidApp:assembleDebug --stacktrace 2>&1 | Tee-Object -FilePath build-log.txt -Append
$ok = $LASTEXITCODE -eq 0

if ($ok) {
  New-Item -ItemType Directory -Force dist | Out-Null
  Get-ChildItem androidApp\build\outputs\apk -Recurse -Filter *.apk | ForEach-Object { Copy-Item $_.FullName dist\ -Force }
  Write-Host "`nDONE. APK files are in $PWD\dist"
  Get-ChildItem dist
} else {
  Select-String -Path build-log.txt -Pattern "^e: |error:|FAILED|What went wrong" -Context 0,3 | Out-File build-errors.txt -Encoding UTF8
  Write-Host "`nBUILD FAILED. The errors are saved in $PWD\build-errors.txt. Send me that file."
  exit 1
}
