<#
.SYNOPSIS
	Builds the Triggered Device Actions debug APK, installs it onto the connected ADB device, and launches the app.
#>
param (
	[string]$stringDeviceId = "auto"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir

$fileAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $fileAdb)) {
	$fileAdb = (Get-Command adb -ErrorAction SilentlyContinue).Source
}

if (-not $fileAdb -or -not (Test-Path $fileAdb)) {
	Write-Error "[RunAdbDevice] Error locating adb.exe - FileNotFoundError: Ensure Android platform-tools is installed"
	exit 1
}

Write-Host "Checking connected ADB devices..."
$listRawDevices = & $fileAdb devices -l | Select-String -Pattern "^\s*([^\s]+)\s+device\b"
$listConnectedDevices = @()
foreach ($match in $listRawDevices) {
	if ($match.Line -match "^\s*([^\s]+)\s+device\b") {
		$listConnectedDevices += $matches[1]
	}
}

if ($listConnectedDevices.Count -eq 0) {
	Write-Error "[RunAdbDevice] Error finding ADB device - DeviceNotFoundError: Enable USB debugging and connect USB cable"
	exit 1
}

if ($stringDeviceId -eq "auto" -or [string]::IsNullOrWhiteSpace($stringDeviceId)) {
	$stringTargetDevice = $listConnectedDevices | Where-Object { $_ -notlike "emulator-*" } | Select-Object -First 1
	if (-not $stringTargetDevice) {
		$stringTargetDevice = $listConnectedDevices[0]
	}
	$stringDeviceId = $stringTargetDevice
} elseif ($stringDeviceId -notin $listConnectedDevices) {
	$stringFallback = $listConnectedDevices | Where-Object { $_ -notlike "emulator-*" } | Select-Object -First 1
	$stringDeviceId = if ($stringFallback) { $stringFallback } else { $listConnectedDevices[0] }
}

$stringModel = (& $fileAdb -s $stringDeviceId shell getprop ro.product.model).Trim()
$stringRelease = (& $fileAdb -s $stringDeviceId shell getprop ro.build.version.release).Trim()
$stringSdk = (& $fileAdb -s $stringDeviceId shell getprop ro.build.version.sdk).Trim()

Write-Host "Target Device: $stringModel ($stringDeviceId) - Android $stringRelease (API $stringSdk)"

Write-Host "Compiling debug APK..."
Set-Location $projectDir
& "$projectDir\gradlew.bat" assembleDebug
if ($LASTEXITCODE -ne 0) {
	Write-Error "[RunAdbDevice] Error building debug APK - ProcessError: Exit code $LASTEXITCODE"
	exit $LASTEXITCODE
}

$fileApk = "$projectDir\app\build\outputs\apk\debug\app-debug.apk"
Write-Host "Installing APK onto $stringModel ($stringDeviceId)..."
& $fileAdb -s $stringDeviceId install -r $fileApk
if ($LASTEXITCODE -ne 0) {
	Write-Error "[RunAdbDevice] Error installing APK - ProcessError: Exit code $LASTEXITCODE"
	exit $LASTEXITCODE
}

Write-Host "Launching Triggered Device Actions on $stringModel..."
& $fileAdb -s $stringDeviceId shell am start -n "com.antigravity.triggeredactions/.MainActivity"
Write-Host "Triggered Device Actions launched successfully on $stringModel ($stringDeviceId)!"
