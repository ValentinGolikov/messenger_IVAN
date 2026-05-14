# run-test.ps1 - Script to run k6 tests with Grafana output and file logging
#
# Usage:
#   .\run-test.ps1 -TestName smoke
#   .\run-test.ps1 -TestName stress -UserCount 100 -Duration 5m
#
# Parameters:
#   -TestName     : Name of the test file without .js (smoke, rest-api, websocket, stress)
#   -UserCount    : Number of virtual users (optional, default from config.js)
#   -Duration     : Test duration (optional, default from config.js)
#   -BaseUrl      : Target URL (optional, default: http://localhost:8081)

param(
    [Parameter(Mandatory=$true)]
    [string]$TestName,
    
    [int]$UserCount,
    [string]$Duration,
    [string]$BaseUrl = "http://localhost:8081"
)

# Validate test name
$testFile = "$TestName.js"
if (-not (Test-Path $testFile)) {
    Write-Error "Test file '$testFile' not found in current directory."
    Write-Host "Available tests: smoke, rest-api, websocket, stress, seed"
    exit 1
}

# Generate timestamp for filename
$timestamp = Get-Date -Format "HH_mm_ss"
$resultFile = "${TestName}_result_${timestamp}.txt"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Running test: $TestName" -ForegroundColor Cyan
Write-Host "Result file:  $resultFile" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Build k6 command
$k6Args = @(
    "run",
    "--env", "BASE_URL=$BaseUrl",
    "--out", "influxdb=http://localhost:8086/k6"
)

if ($UserCount -gt 0) {
    $k6Args += @("--env", "USER_COUNT=$UserCount")
}

if ($Duration) {
    $k6Args += @("--env", "DURATION=$Duration")
}

$k6Args += $testFile

# Run k6 and capture output
try {
    $output = & k6 @k6Args 2>&1
    
    # Write to console
    $output | ForEach-Object { Write-Host $_ }
    
    # Write to file
    $header = @"
===========================================
Test: $TestName
Date: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
Base URL: $BaseUrl
User Count: $(if($UserCount) { $UserCount } else { "default" })
Duration: $(if($Duration) { $Duration } else { "default" })
===========================================

"@
    
    $header | Out-File -FilePath $resultFile -Encoding UTF8
    $output | Out-File -FilePath $resultFile -Encoding UTF8 -Append
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "Test completed!" -ForegroundColor Green
    Write-Host "Results saved to: $resultFile" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
}
catch {
    Write-Error "Test failed: $_"
    exit 1
}
