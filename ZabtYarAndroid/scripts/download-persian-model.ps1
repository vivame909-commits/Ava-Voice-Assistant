$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$assetsModel = Join-Path $projectRoot "app\src\main\assets\model"
$tempRoot = Join-Path $env:TEMP "zabtyar-vosk-model"
$zipPath = Join-Path $tempRoot "vosk-model-small-fa-0.42.zip"
$extractPath = Join-Path $tempRoot "extract"
$url = "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip"

New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
if (Test-Path $extractPath) { Remove-Item -Recurse -Force $extractPath }
New-Item -ItemType Directory -Force -Path $extractPath | Out-Null

Write-Host "Downloading Persian Vosk model (~53 MB)..."
Invoke-WebRequest -Uri $url -OutFile $zipPath
Write-Host "Extracting model..."
Expand-Archive -Path $zipPath -DestinationPath $extractPath -Force

$sourceModel = Join-Path $extractPath "vosk-model-small-fa-0.42"
if (-not (Test-Path (Join-Path $sourceModel "am\final.mdl"))) {
    throw "Model files not found in archive. Please check the official Vosk model page."
}

if (Test-Path $assetsModel) { Remove-Item -Recurse -Force $assetsModel }
New-Item -ItemType Directory -Force -Path $assetsModel | Out-Null
Copy-Item -Path (Join-Path $sourceModel "*") -Destination $assetsModel -Recurse -Force
Write-Host "Model installed at: $assetsModel"
Write-Host "You can now open the project in Android Studio."
