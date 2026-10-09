@echo off
rem Change the working directory to this script's folder (project root),
rem so the app keeps using <project>\data\data.db instead of <project>\target\data\data.db.
rem NOTE: keep this file ASCII-only; cmd.exe parses .bat files with the OEM code page.
cd /d "%~dp0"

if not exist "target\StudyTimeTracker.jar" (
    echo Cannot find target\StudyTimeTracker.jar
    echo Please run "mvn clean package" in the project root first.
    pause
    exit /b 1
)

start "" javaw -jar "target\StudyTimeTracker.jar"
