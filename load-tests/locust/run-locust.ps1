# run-locust.ps1 - Script to run Locust tests
#
# Usage:
#   .\run-locust.ps1 -TestFile locustfile.py -Users 10000 -Duration 15m
#   .\run-locust.ps1 -TestFile locust-extreme.py -Users 50000 -Duration 30m
#
# Parameters:
#   -TestFile    : Locust test file (locustfile.py or locust-extreme.py)
#   -Users       : Number of virtual users
#   -SpawnRate   : Users spawned per second (default: 100)
#   -Duration    : Test duration (e.g., 10m, 1h)
#   -Host        : Target URL (default: http://localhost:8081)

param(
    [string]$TestFile = "locustfile.py",
    [int]$Users = 10000,
    [int]$SpawnRate = 100,
    [string]$Duration = "10m",
    [string]$Host = "http://localhost:8081"
)

# Check if locust is installed
if (-not (Get-Command locust -ErrorAction SilentlyContinue)) {
    Write-Error "Locust is not installed. Run: pip install locust"
    exit 1
}

# Check if test file exists
if (-not (Test-Path $TestFile)) {
    Write-Error "Test file '$TestFile' not found."
    exit 1
}

# Generate timestamp for report
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$reportFile = "locust_report_$timestamp.html"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Locust Load Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test file:    $TestFile"
Write-Host "Users:        $Users"
Write-Host "Spawn rate:   $SpawnRate/s"
Write-Host "Duration:     $Duration"
Write-Host "Host:         $Host"
Write-Host "Report:       $reportFile"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Run Locust
locust -f $TestFile `
    --host=$Host `
    --headless `
    --users $Users `
    --spawn-rate $SpawnRate `
    --run-time $Duration `
    --html $reportFile `
    --csv locust_$timestamp

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Test completed!" -ForegroundColor Green
Write-Host "HTML Report: $reportFile" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
