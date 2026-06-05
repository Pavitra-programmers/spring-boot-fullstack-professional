@echo off
REM LaborFlex - Stop any running instance and restart

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot

echo Checking for existing instance on port 8080...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
    echo Stopping process %%a...
    taskkill /PID %%a /F
    timeout /t 2 /nobreak >nul
)

echo.
echo ================================
echo LaborFlex Starting...
echo ================================
echo Database: Supabase
echo Redis: localhost:6379
echo Server: http://localhost:8080
echo ================================
echo.

mvnw.cmd spring-boot:run
