$ErrorActionPreference = "Stop"

Write-Host "=== Step 1: Running Shared & Desktop JVM Tests ==="
gradle :shared:test :desktopApp:test --console=plain

Write-Host "=== Step 2: Building Self-Contained Windows 64-bit Distributable ==="
gradle :desktopApp:createDistributable --console=plain

$distDir = "desktopApp\build\compose\binaries\main\app\HisabPro"
if (-not (Test-Path "$distDir\HisabPro.exe")) { throw "HisabPro.exe missing from distributable!" }
if (-not (Test-Path "$distDir\runtime\bin\java.dll")) { throw "Bundled Windows JRE runtime missing!" }

Write-Host "=== Step 3: Compiling NSIS Windows Installer (HisabPro_Setup.exe) ==="
makensis desktopApp\installer\HisabPro.nsi
if (-not (Test-Path "desktopApp\installer\HisabPro_Setup.exe")) {
    throw "HisabPro_Setup.exe was not generated."
}

Write-Host "=== Step 4: Staging Clean Windows-Only Release Folder ==="
$releaseDir = "HisabPro_Windows_Release"
if (Test-Path $releaseDir) { Remove-Item -Recurse -Force $releaseDir }
New-Item -ItemType Directory -Path $releaseDir | Out-Null

Copy-Item "desktopApp\installer\HisabPro_Setup.exe" "$releaseDir\HisabPro_Setup.exe"
Copy-Item "desktopApp\installer\README.txt" "$releaseDir\README.txt"
Copy-Item "desktopApp\installer\LICENSE.txt" "$releaseDir\LICENSE.txt"

$bytes = [System.IO.File]::ReadAllBytes("$releaseDir\HisabPro_Setup.exe")
if ($bytes[0] -ne 0x4D -or $bytes[1] -ne 0x5A) {
    throw "Generated HisabPro_Setup.exe is not a valid Windows PE executable!"
}

if (Test-Path "HisabPro_Windows_Complete.zip") { Remove-Item -Force "HisabPro_Windows_Complete.zip" }
Compress-Archive -Path "$releaseDir\*" -DestinationPath "HisabPro_Windows_Complete.zip" -CompressionLevel Optimal

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead("$PWD\HisabPro_Windows_Complete.zip")
$entries = $zip.Entries | ForEach-Object { $_.FullName }
$zip.Dispose()
$forbidden = $entries | Where-Object { $_ -match "\.apk$|\.kt$|\.java$|android|gradle" }
if ($forbidden) { throw "Forbidden Android/source files detected in release ZIP: $forbidden" }

Write-Host "=== Ready ==="
Get-ChildItem $releaseDir
Get-Item "HisabPro_Windows_Complete.zip"
