# ConnectSphere Backend - Startup Script (UC-8 Non-Docker)
# This script starts all microservices in separate terminal windows.

$services = @(
    "eureka-server",
    "auth-service",
    "post-service",
    "comment-service",
    "like-service",
    "follow-service",
    "notification-service",
    "media-service",
    "search-service",
    "api-gateway"
)

Write-Host "Starting ConnectSphere Backend..." -ForegroundColor Cyan

# 1. Start Eureka Server first
Write-Host "Starting Eureka Server..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd eureka-server; .\mvnw spring-boot:run" -WindowStyle Normal

Write-Host "Waiting 15 seconds for Eureka to initialize..."
Start-Sleep -Seconds 15

# 2. Start all other services
foreach ($service in $services) {
    if ($service -ne "eureka-server" -and $service -ne "api-gateway") {
        Write-Host "Starting $service..." -ForegroundColor Green
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd $service; .\mvnw spring-boot:run" -WindowStyle Normal
        Start-Sleep -Seconds 2 # Subtle delay to prevent CPU spike
    }
}

# 3. Start API Gateway last
Write-Host "Starting API Gateway..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd api-gateway; .\mvnw spring-boot:run" -WindowStyle Normal

Write-Host "All services have been triggered. Please check the individual windows for logs." -ForegroundColor Cyan
Write-Host "Eureka Dashboard: http://localhost:8761"
Write-Host "API Gateway: http://localhost:8080"
