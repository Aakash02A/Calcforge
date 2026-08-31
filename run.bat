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
set "FRONTEND_CMD="

where python >nul 2>nul
if %errorlevel% EQU 0 goto :found_python

where node >nul 2>nul
if %errorlevel% EQU 0 goto :found_node

goto :no_frontend

:found_python
set "FRONTEND_CMD=python -m http.server 5500"
echo [Info] Found Python. Will serve frontend using python http.server.
goto :frontend_done

:found_node
set "FRONTEND_CMD=npx serve -l 5500"
echo [Info] Found Node.js. Will serve frontend using npx serve.
goto :frontend_done

:no_frontend
echo [WARNING] Neither Python nor Node.js was found in your PATH.
echo The frontend static files cannot be served automatically.
echo You will need to serve the 'frontend' directory manually on port 5500.
echo.

:frontend_done

:: Backend setup
set "JAR_PATH=backend\target\calcforge-backend.jar"
if exist "%JAR_PATH%" goto :backend_ready

echo [WARNING] Prebuilt backend JAR not found at %JAR_PATH%
echo Attempting to build backend using Maven...

where mvn >nul 2>nul
if %errorlevel% NEQ 0 (
    echo [ERROR] Maven (mvn) is not in your PATH.
    echo Please build the backend JAR manually using your IDE or Maven,
    echo or install Maven to build it automatically.
    pause
    exit /b 1
)

echo Building backend...
cd backend
call mvn clean package -DskipTests
cd ..

if not exist "%JAR_PATH%" (
    echo [ERROR] Failed to build backend JAR.
    pause
    exit /b 1
)

:backend_ready

:: Start Backend
echo Starting CalcForge Backend...
start "CalcForge Backend" cmd /c "java -jar %JAR_PATH%"

:: Start Frontend if command available
if not "%FRONTEND_CMD%"=="" (
    echo Starting CalcForge Frontend...
    start "CalcForge Frontend" cmd /c "cd frontend && %FRONTEND_CMD%"
)

echo.
echo Launching default browser in 3 seconds...
timeout /t 3 /nobreak >nul
start http://localhost:5500

echo.
echo =======================================================================
echo CalcForge is running!
echo Backend API:  http://localhost:8080
echo Frontend UI:  http://localhost:5500
echo =======================================================================
echo.
echo To stop the servers, close the separate command prompt windows for:
echo - "CalcForge Backend"
echo - "CalcForge Frontend"
echo.
pause
