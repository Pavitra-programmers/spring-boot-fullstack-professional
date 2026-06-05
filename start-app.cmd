@echo off
REM LaborFlex Application Starter
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot

echo ================================
echo LaborFlex Starting...
echo ================================
echo Database: Supabase (configured in application.yml)
echo Redis: localhost:6379 (optional - app works without it)
echo Server: http://localhost:8080
echo ================================
echo.

mvnw.cmd spring-boot:run
