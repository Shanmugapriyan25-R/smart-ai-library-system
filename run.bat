@echo off
title Smart AI Library Management System

echo =====================================================================
echo       SMART AI LIBRARY MANAGEMENT SYSTEM - ONE TOUCH LAUNCHER
echo =====================================================================
echo.

cd /d "%~dp0"

:: 1. Check Java runtime environment
echo [1/4] Checking Java runtime environment...
where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java is not installed or not found in PATH.
    echo Please install Java 17 or Java 21 LTS: https://adoptium.net/
    echo.
    pause
    exit /b 1
)
echo [OK] Java runtime found.

:: 2. Check and start MySQL service if stopped
echo [2/4] Checking MySQL Database Service...
sc query MySQL80 | findstr RUNNING >nul 2>nul
if errorlevel 1 (
    echo [INFO] Attempting to start MySQL80 Windows Service...
    net start MySQL80 >nul 2>nul
) else (
    echo [OK] MySQL80 Service is running.
)

:: 3. Ensure Port 8080 is Free
echo [3/4] Ensuring port 8080 is available...
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }" >nul 2>nul
echo [OK] Port 8080 is ready.

:: 4. Verify or package executable JAR
echo [4/4] Verifying application package...
set JAR_FILE=target\smart-library-system-1.0.0.jar
if not exist "%JAR_FILE%" (
    echo [INFO] Executable JAR not found. Building with Maven wrapper...
    call mvnw.cmd package -DskipTests
    if errorlevel 1 (
        echo.
        echo [ERROR] Maven build failed. See error output above.
        pause
        exit /b 1
    )
)
echo [OK] Application JAR verified.

echo.
echo =====================================================================
echo  APPLICATION READY - STARTING SERVER
echo =====================================================================
echo  PORTAL URL    : http://localhost:8080
echo.
echo  PRE-CONFIGURED LOGINS:
echo   * Student : username=student   password=student123
echo   * Staff   : username=staff     password=staff123
echo   * Admin   : username=admin     password=admin123
echo =====================================================================
echo.
echo (NOTE: Keep this window open while using the application)
echo (Press Ctrl+C to stop the application)
echo.

:: Launch default web browser in background when server responds
start "" powershell -NoProfile -WindowStyle Hidden -Command "Start-Sleep -Seconds 3; for ($i=0; $i -lt 40; $i++) { try { $r = [System.Net.WebRequest]::Create('http://localhost:8080/login').GetResponse(); if ($r.StatusCode -eq 200) { Start-Process 'http://localhost:8080/login'; break } } catch { Start-Sleep -Seconds 1 } }"

:: Run the Spring Boot application (stays running in foreground)
java -jar "%JAR_FILE%"

echo.
echo =====================================================================
echo The application has stopped.
echo =====================================================================
echo.
pause
