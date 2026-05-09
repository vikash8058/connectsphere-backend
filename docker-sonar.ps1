# ConnectSphere Master SonarQube Scan Script
# Run this from the root of connectsphere-backend

$services = @(
    @{ Name="Auth Service";         Folder="auth-service";         Key="connectsphere-auth-service";         Token="sqp_4233cbc93224ef7c591c0da03025ed55ec14d87b" },
    @{ Name="Post Service";         Folder="post-service";         Key="connectsphere-post-service";         Token="sqp_d2d5a37171e5491763da1823eb46b412b19cf629" },
    @{ Name="Comment Service";      Folder="comment-service";      Key="connectsphere-comment-service";      Token="sqp_4a60657089f3c7e273c484888de856391c3ca173" },
    @{ Name="Like Service";         Folder="like-service";         Key="connectsphere-like-service";         Token="sqp_843997fa8a7486c025a51a28ee4620902ddbcce4" },
    @{ Name="Follow Service";       Folder="follow-service";       Key="connectsphere-follow-service";       Token="sqp_c934612518eee401ebbef2ce74464fd1de43cdcd" },
    @{ Name="Notification Service"; Folder="notification-service"; Key="connectsphere-notification-service"; Token="sqp_88e118b2841ca3d15e3289b416f86dfbe8999d18" },
    @{ Name="Media Service";        Folder="media-service";        Key="connectsphere-media-service";        Token="sqp_75a1cfdc5aac09fca32497f4eb1010ad2870ea4b" },
    @{ Name="Search Service";       Folder="search-service";       Key="connectsphere-search-service";       Token="sqp_8b260b2a2b5cbbb592e0122a9cbefdf628c9eebf" },
    @{ Name="Payment Service";      Folder="payment-service";      Key="connectsphere-payment-service";      Token="sqp_a408158fd109268ab4b98eabce193ff230b0b1e1" },
    @{ Name="Gateway Service";      Folder="api-gateway";          Key="connectsphere-gateway-service";      Token="sqp_56e6c14c7f4a737a2d870c3a67a2526ebe535ea8" }
)

Write-Host "===============================================" -ForegroundColor Green
Write-Host "🛡️  ConnectSphere Full System Security Scan 🛡️" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Green

foreach ($s in $services) {
    $startTime = Get-Date
    Write-Host "`n🚀 Starting Scan for: $($s.Name)..." -ForegroundColor Cyan
    Write-Host "   Folder: $($s.Folder)" -ForegroundColor Gray

    docker run --rm `
      -v "${PWD}:/app" `
      -v "${HOME}/.m2:/root/.m2" `
      -w "/app/$($s.Folder)" `
      --network connectsphere-backend_connectsphere-net `
      maven:3.9.6-eclipse-temurin-17 `
      mvn clean verify sonar:sonar `
      "-Dsonar.projectKey=$($s.Key)" `
      "-Dsonar.projectName=ConnectSphere $($s.Name)" `
      "-Dsonar.host.url=http://sonarqube:9000" `
      "-Dsonar.token=$($s.Token)"

    $endTime = Get-Date
    $duration = $endTime - $startTime
    Write-Host "✅ Finished $($s.Name) in $($duration.Minutes)m $($duration.Seconds)s" -ForegroundColor Green
}

Write-Host "`n===============================================" -ForegroundColor Green
Write-Host "🏁  ALL SCANS COMPLETE  🏁" -ForegroundColor Green
Write-Host "Check your dashboard: http://localhost:9001" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Green
