@echo off
REM ==============================================================================
REM Application Stop Script for Windows
REM ==============================================================================

REM UTF-8 코드 페이지 설정
chcp 65001 >NUL

SETLOCAL EnableDelayedExpansion

REM ==============================================================================
REM 설정 변수
REM ==============================================================================
SET PROC_NAME=TX24_LIB
SET SCRIPT_DIR=%~dp0
SET BIN_DIR=%SCRIPT_DIR%
SET PID_FILE=%BIN_DIR%\%PROC_NAME%.pid

REM ==============================================================================
REM Main
REM ==============================================================================

REM PID 파일 확인
IF NOT EXIST "%PID_FILE%" (
    echo Error: PID file not found: %PID_FILE%
    echo.
    
    REM 실행 중인 프로세스 찾기
    wmic process where "name='java.exe' and commandline like '%%%PROC_NAME%%%'" get processid,commandline 2>NUL | findstr /R "[0-9]" >NUL
    IF !ERRORLEVEL! EQU 0 (
        echo Found running process:
        wmic process where "name='java.exe' and commandline like '%%%PROC_NAME%%%'" get processid,commandline
        echo Please check and stop manually
    ) ELSE (
        echo %PROC_NAME% is not running
    )
    exit /b 1
)

REM PID 가져오기
SET /P PID=<"%PID_FILE%"

IF NOT DEFINED PID (
    echo Error: Invalid PID file
    del /Q "%PID_FILE%" 2>NUL
    exit /b 1
)

REM 프로세스 실행 확인
tasklist /FI "PID eq %PID%" 2>NUL | find /I "java.exe" >NUL
IF %ERRORLEVEL% NEQ 0 (
    echo %PROC_NAME% is not running (stale PID: %PID%)
    del /Q "%PID_FILE%" 2>NUL
    exit /b 0
)

REM 강제 종료
echo Stopping %PROC_NAME% (PID: %PID%)...
echo Force killing process...

taskkill /F /PID %PID% >NUL 2>&1

timeout /t 1 /nobreak >NUL

REM 종료 확인
tasklist /FI "PID eq %PID%" 2>NUL | find /I "java.exe" >NUL
IF %ERRORLEVEL% NEQ 0 (
    del /Q "%PID_FILE%" 2>NUL
    echo %PROC_NAME% stopped
    exit /b 0
) ELSE (
    echo Error: Failed to stop process
    exit /b 1
)

ENDLOCAL