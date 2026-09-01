@echo off
title CalcForge Launcher
cls

:: Ensure the working directory is the folder where this script is located
cd /d "%~dp0"

echo =======================================================================
echo                     Welcome to CalcForge Launcher
echo =======================================================================
echo.
echo This script will launch the CalcForge Backend and Frontend servers.
echo.
echo [Prerequisites Checklist]
echo 1. Ensure your local MySQL database is running and reachable.
echo 2. Ensure Java 17+ is installed.
echo.

:: Check for Java
where java >nul 2>nul
if %errorlevel% NEQ 0 (
    echo [ERROR] Java is not installed or not found in your PATH.
    echo Please install Java 17 or higher to run the backend.
    pause
    exit /b 1
)

:: Determine Frontend Server Command (Python or Node)
set "FRONTEND_PORT=5500"
set "FRONTEND_CMD="

where python >nul 2>nul
if %errorlevel% EQU 0 goto :found_python

where node >nul 2>nul
if %errorlevel% EQU 0 goto :found_node

goto :no_frontend

:found_python
set FRONTEND_CMD=python -m http.server %FRONTEND_PORT% -d "%~dp0frontend"
echo [Info] Found Python. Will serve frontend using python http.server on port %FRONTEND_PORT%.
goto :frontend_done

:found_node
set FRONTEND_CMD=npx -y serve -l %FRONTEND_PORT% "%~dp0frontend"
echo [Info] Found Node.js. Will serve frontend using npx serve on port %FRONTEND_PORT%.
goto :frontend_done

:no_frontend
echo [WARNING] Neither Python nor Node.js was found in your PATH.
echo The frontend static files cannot be served automatically.
echo You will need to serve the 'frontend' directory manually on port %FRONTEND_PORT%.
echo.

:frontend_done

:: Backend setup
set "JAR_PATH=%~dp0backend\target\calcforge-backend.jar"
if exist "%JAR_PATH%" goto :backend_ready

echo [WARNING] Prebuilt backend JAR not found at %JAR_PATH%
echo Attempting to build backend using Maven...

set "MVN_CMD=mvn"
where mvn >nul 2>nul
if %errorlevel% NEQ 0 (
    if exist "%~dp0.maven\bin\mvn.cmd" (
        set "MVN_CMD=%~dp0.maven\bin\mvn.cmd"
    ) else (
        echo [ERROR] Maven (mvn) is not in your PATH and bundled Maven was not found.
        echo Please build the backend JAR manually using your IDE or Maven.
        pause
        exit /b 1
    )
)

echo Building backend using %MVN_CMD%...
cd /d "%~dp0backend"
call "%MVN_CMD%" clean package -DskipTests
cd /d "%~dp0"

if not exist "%JAR_PATH%" (
    echo [ERROR] Failed to build backend JAR.
    pause
    exit /b 1
)

:backend_ready

:: Start Backend
echo Starting CalcForge Backend...
start "CalcForge Backend" /d "%~dp0backend" java -jar "%JAR_PATH%"

:: Start Frontend if command available
if not "%FRONTEND_CMD%"=="" (
    echo Starting CalcForge Frontend...
    start "CalcForge Frontend" /d "%~dp0frontend" %FRONTEND_CMD%
)

echo.
echo Launching default browser in 3 seconds...
timeout /t 3 /nobreak >nul
start http://localhost:%FRONTEND_PORT%

echo.
echo =======================================================================
echo CalcForge is running!
echo Backend API:  http://localhost:8080
echo Frontend UI:  http://localhost:%FRONTEND_PORT%
echo =======================================================================
echo.
echo To stop the servers, close the separate command prompt windows for:
echo - "CalcForge Backend"
echo - "CalcForge Frontend"
echo.
pause
