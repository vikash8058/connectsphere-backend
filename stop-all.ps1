# ConnectSphere Backend - Stop Script
# This script kills all Java processes running on the backend ports.

$ports = @(8761, 8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8095)

Write-Host "Stopping all ConnectSphere Backend services..." -ForegroundColor Cyan

foreach ($port in $ports) {
    $proc = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -First 1
    if ($proc) {
        Write-Host "Killing process $proc on port $port" -ForegroundColor Red
        Stop-Process -Id $proc -Force
    }
}

Write-Host "Done." -ForegroundColor Cyan
