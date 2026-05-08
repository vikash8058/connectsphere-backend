# ============================================================
#   ConnectSphere - SonarQube Analysis for ALL Services
#   Run from: the ROOT folder containing all service folders
#   Usage: Right-click > Run with PowerShell
#          OR: powershell -ExecutionPolicy Bypass -File .\run-all-sonar.ps1
# ============================================================

$BASE_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$pass = 0
$fail = 0
$failedServices = @()

# Define all services: FolderName, ProjectKey, ProjectName, Token
$services = @(
    @{
        Folder = "auth-service"
        Key    = "connectsphere-auth-service"
        Name   = "ConnectSphere Auth Service"
        Token  = "sqp_e6f968bff276f9709074b7b54eca59f92d50dc68"
    },
    @{
        Folder = "gateway-service"
        Key    = "connectsphere-gateway-service"
        Name   = "ConnectSphere Gateway Service"
        Token  = "sqp_1bee828058ba4c48f1e6c8b4d4d2ae3bebe3f73a"
    },
    @{
        Folder = "post-service"
        Key    = "connectsphere-post-service"
        Name   = "ConnectSphere Post Service"
        Token  = "sqp_0ec369a95d9fd0a8dfaaa1882a6ffc43c1c7a5aa"
    },
    @{
        Folder = "comment-service"
        Key    = "connectsphere-comment-service"
        Name   = "ConnectSphere Comment Service"
        Token  = "sqp_fe1704bdce450bd27dc1af90fa43a9e7febf517b"
    },
    @{
        Folder = "like-service"
        Key    = "connectsphere-like-service"
        Name   = "ConnectSphere Like Service"
        Token  = "sqp_33b2250cad99018d3f67f6815e77644275576b79"
    },
    @{
        Folder = "follow-service"
        Key    = "connectsphere-follow-service"
        Name   = "ConnectSphere Follow Service"
        Token  = "sqp_7dc99ab616f651b90fa8a7b1a2e870db51322d5b"
    },
    @{
        Folder = "notification-service"
        Key    = "connectsphere-notification-service"
        Name   = "ConnectSphere Notification Service"
        Token  = "sqp_ac72efaa243b3e65a178f7792dbb3c305c10ab8f"
    },
    @{
        Folder = "media-service"
        Key    = "connectsphere-media-service"
        Name   = "ConnectSphere Media Service"
        Token  = "sqp_f4be3362b9a8acb4b90c86450558eb012dbd3c75"
    },
    @{
        Folder = "search-service"
        Key    = "connectsphere-search-service"
        Name   = "ConnectSphere Search Service"
        Token  = "sqp_7e745d2db91f43112bb7c70e668dc94184f18014"
    },
    @{
        Folder = "payment-service"
        Key    = "connectsphere-payement-service"
        Name   = "ConnectSphere Payment Service"
        Token  = "sqp_f7ae3b803b561fe469f24cc93046a9f59abaebe7"
    }
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  ConnectSphere - SonarQube Analysis for ALL Services" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$total = $services.Count
$current = 0

foreach ($svc in $services) {
    $current++
    $servicePath = Join-Path $BASE_DIR $svc.Folder

    Write-Host "[$current/$total] Analyzing: $($svc.Name)" -ForegroundColor Yellow

    # Check if folder exists
    if (-not (Test-Path $servicePath)) {
        Write-Host "  [SKIP] Folder not found: $servicePath" -ForegroundColor DarkYellow
        $fail++
        $failedServices += "$($svc.Folder) (folder missing)"
        Write-Host ""
        continue
    }

    Set-Location $servicePath

    # Run the Maven SonarQube command
    & .\mvnw clean verify sonar:sonar `
        "-Dsonar.projectKey=$($svc.Key)" `
        "-Dsonar.projectName=$($svc.Name)" `
        "-Dsonar.token=$($svc.Token)"

    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [PASS] $($svc.Name) - Analysis uploaded to SonarQube" -ForegroundColor Green
        $pass++
    } else {
        Write-Host "  [FAIL] $($svc.Name) - Maven/Sonar command failed" -ForegroundColor Red
        $fail++
        $failedServices += $svc.Folder
    }

    Write-Host ""
}

# ──────────────────────────────────────────────
# SUMMARY
# ──────────────────────────────────────────────
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  SONARQUBE ANALYSIS SUMMARY" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Total Services : $total"
Write-Host "  Passed         : $pass" -ForegroundColor Green
Write-Host "  Failed         : $fail" -ForegroundColor $(if ($fail -gt 0) { "Red" } else { "Green" })

if ($failedServices.Count -gt 0) {
    Write-Host ""
    Write-Host "  Failed Services:" -ForegroundColor Red
    foreach ($s in $failedServices) {
        Write-Host "    - $s" -ForegroundColor Red
    }
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Open SonarQube dashboard: http://localhost:9000" -ForegroundColor Cyan
Write-Host ""

Read-Host "Press Enter to exit"
