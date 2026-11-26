@echo off
setlocal
set "batDir=%~dp0"
echo Building Triggered Device Actions Debug APK...
call "%batDir%gradlew.bat" assembleDebug
if errorlevel 1 (
	echo [BuildDebug] Error executing Gradle assembleDebug - ProcessError: Execution exited with code %errorlevel% >&2
	exit /b %errorlevel%
)
echo.
echo ==========================================================
echo  Debug Build Complete!
echo  APK: %batDir%app\build\outputs\apk\debug\app-debug.apk
echo ==========================================================
echo.
endlocal
