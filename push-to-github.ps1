# Creates a private GitHub repo from this folder and pushes it, so GitHub Actions builds the APK for you.
# Needs git and the GitHub CLI (winget install GitHub.cli), then `gh auth login` once.
param([string]$Name = "music-app")
Set-Location $PSScriptRoot
if (-not (Test-Path .git)) { git init -b main }
git add -A
git commit -m "Music app: SimpMusic + radio" 2>$null
gh repo create $Name --private --source . --push
Write-Host "Pushed. Open the repo's Actions tab: 'Build debug APK' runs automatically; download the APK from the run's Artifacts."
