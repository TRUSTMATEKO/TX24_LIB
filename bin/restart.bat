@echo off
REM ==============================================================================
REM Application Restart Script for Windows
REM ==============================================================================

chcp 65001 >NUL 2>&1

SETLOCAL EnableDelayedExpansion

REM ==============================================================================
REM Configuration Variables
REM ==============================================================================
SET "SCRIPT_DIR=%~dp0"

REM ==============================================================================
REM Main
REM ==============================================================================

cd /d "%SCRIPT_DIR%"

echo Restarting application...
echo.

REM Stop
IF EXIST "stop.bat" (
    call stop.bat
) ELSE (
    echo Error: stop.bat not found
    exit /b 1
)

echo.
timeout /t 2 /nobreak >NUL

REM Start
IF EXIST "start.bat" (
    call start.bat %*
) ELSE (
    echo Error: start.bat not found
    exit /b 1
)

ENDLOCAL