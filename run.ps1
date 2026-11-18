<#
.SYNOPSIS
    One-touch launcher for Smart AI Library Management System.
.DESCRIPTION
    Checks Java, checks MySQL, frees port 8080 if held, packages JAR if needed, starts Spring Boot, and auto-opens browser.
#>

$Host.UI.RawUI.WindowTitle = "Smart AI Library Management System - One Touch Launcher"
Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host "      SMART AI LIBRARY MANAGEMENT SYSTEM - ONE TOUCH LAUNCHER" -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host ""

Set-Location $PSScriptRoot

# 1. Check Java
Write-Host "[1/4] Checking Java runtime environment..." -ForegroundColor Yellow
try {
    $javaVer = & java -version 2>&1
    Write-Host "[OK] Found Java: $($javaVer[0])" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Java is not installed or not found in PATH." -ForegroundColor Red
    Write-Host "Please install Java 17 or Java 21 LTS: https://adoptium.net/" -ForegroundColor Red
    pause
    exit 1
}

# 2. Check MySQL Service
Write-Host "[2/4] Checking MySQL Database Service..." -ForegroundColor Yellow
$mysqlSvc = Get-Service -Name "MySQL80" -ErrorAction SilentlyContinue
if ($mysqlSvc) {
    if ($mysqlSvc.Status -ne "Running") {
        Write-Host "[INFO] Starting MySQL80 Windows Service..." -ForegroundColor Yellow
        Start-Service -Name "MySQL80" -ErrorAction SilentlyContinue
    } else {
        Write-Host "[OK] MySQL80 service is running." -ForegroundColor Green
    }
} else {
    Write-Host "[INFO] MySQL service check skipped (custom MySQL setup)." -ForegroundColor Gray
}

# 3. Ensure Port 8080 is Free
Write-Host "[3/4] Ensuring port 8080 is available..." -ForegroundColor Yellow
$portOccupied = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($portOccupied) {
    Write-Host "[INFO] Freeing port 8080 from previous background process..." -ForegroundColor Yellow
    foreach ($conn in $portOccupied) {
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 1
}
Write-Host "[OK] Port 8080 is ready." -ForegroundColor Green

# 4. Check and prepare JAR
Write-Host "[4/4] Verifying application package..." -ForegroundColor Yellow
$jarPath = Join-Path $PSScriptRoot "target\smart-library-system-1.0.0.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "[INFO] Executable JAR not found. Building with Maven wrapper..." -ForegroundColor Yellow
    & "$PSScriptRoot\mvnw.cmd" package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "[ERROR] Maven build failed." -ForegroundColor Red
        pause
        exit 1
    }
} else {
    Write-Host "[OK] Application JAR verified: $jarPath" -ForegroundColor Green
}

# 5. Display Credentials & Info
Write-Host ""
Write-Host "=====================================================================" -ForegroundColor White
Write-Host " APPLICATION READY - STARTING SERVER" -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor White
Write-Host " PORTAL URL    : http://localhost:8080" -ForegroundColor Cyan
Write-Host ""
Write-Host " PRE-CONFIGURED LOGINS:" -ForegroundColor White
Write-Host "   * Student : username=student   password=student123" -ForegroundColor Green
Write-Host "   * Staff   : username=staff     password=staff123" -ForegroundColor Yellow
Write-Host "   * Admin   : username=admin     password=admin123" -ForegroundColor Magenta
Write-Host "=====================================================================" -ForegroundColor White
Write-Host ""
Write-Host "(NOTE: Keep this window open while using the application)" -ForegroundColor Gray
Write-Host "(Press Ctrl+C to stop the application)" -ForegroundColor Gray
Write-Host ""

# Background job to open browser once ready
Start-Job -ScriptBlock {
    Start-Sleep -Seconds 3
    for ($i = 0; $i -lt 40; $i++) {
        try {
            $req = [System.Net.WebRequest]::Create("http://localhost:8080/login")
            $res = $req.GetResponse()
            if ($res.StatusCode -eq 200) {
                Start-Process "http://localhost:8080/login"
                break
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
} | Out-Null

Write-Host "Starting Spring Boot application..." -ForegroundColor Green
Write-Host ""

try {
    & java -jar $jarPath
} finally {
    Write-Host ""
    Write-Host "=====================================================================" -ForegroundColor Yellow
    Write-Host "The application has stopped." -ForegroundColor Yellow
    Write-Host "=====================================================================" -ForegroundColor Yellow
    Write-Host "Press enter to exit..." -ForegroundColor Gray
    Read-Host
}
