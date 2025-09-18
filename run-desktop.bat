@echo off
REM Wireless Communication App - Desktop Runner (Windows)
echo Starting Wireless Communication Desktop App...
echo This will start the WebSocket server on port 8765
echo Android devices can connect to this server for chat
echo.

REM Check if gradlew.bat exists
if not exist "gradlew.bat" (
    echo Error: gradlew.bat not found. Make sure you're in the project root directory.
    pause
    exit /b 1
)

REM Run the desktop application
echo Starting desktop application...
gradlew.bat :composeApp:run

echo.
echo Desktop app stopped.
pause
