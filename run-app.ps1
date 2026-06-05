# LaborFlex Application Startup Script
# Sets environment variables and runs the Spring Boot application

# Set Java environment
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

# Database Configuration (Supabase)
$env:DB_URL = "jdbc:postgresql://aws-1-ap-south-1.pooler.supabase.com:5432/postgres"
$env:DB_USERNAME = "postgres.hhadzvrmoaghrlqzngaq"
$env:DB_PASSWORD = "BFzbWQnluMdr2XO8"

# Redis Configuration (defaults to localhost)
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"

# CORS Configuration
$env:CORS_ALLOWED_ORIGINS = "http://localhost:3000,http://localhost:8080"

Write-Host "================================" -ForegroundColor Green
Write-Host "LaborFlex Starting..." -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green
Write-Host "Database: Supabase (aws-1-ap-south-1)" -ForegroundColor Cyan
Write-Host "Redis: localhost:6379 (app will start even if Redis is down)" -ForegroundColor Cyan
Write-Host "Server: http://localhost:8080" -ForegroundColor Cyan
Write-Host "================================`n" -ForegroundColor Green

# Run the application
./mvnw.cmd spring-boot:run
