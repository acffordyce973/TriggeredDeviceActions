<#
.SYNOPSIS
	Ensures the Android emulator is running, installs the debug APK, and launches Triggered Device Actions.
#>
param (
	[string]$stringAvdName = "auto"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir

& "$scriptDir\LaunchEmulator.ps1" -stringAvdName $stringAvdName

Write-Host "Building and installing Triggered Device Actions APK..."
Set-Location $projectDir
& "$projectDir\gradlew.bat" installDebug
if ($LASTEXITCODE -ne 0) {
	Write-Error "[RunApp] Error running gradlew installDebug - ProcessError: Exit code $LASTEXITCODE"
	exit $LASTEXITCODE
}

Write-Host "Launching Triggered Device Actions on emulated device..."
$fileAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $fileAdb)) {
	$fileAdb = (Get-Command adb -ErrorAction SilentlyContinue).Source
}

$stringEmulatorDevice = (& $fileAdb devices 2>$null | Select-String "emulator-\d+\s+device" | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1)

if ($stringEmulatorDevice) {
	& $fileAdb -s $stringEmulatorDevice shell am start -n "com.antigravity.triggeredactions/.MainActivity"
} else {
	& $fileAdb shell am start -n "com.antigravity.triggeredactions/.MainActivity"
}

Write-Host "Triggered Device Actions launched successfully on emulated device!"
