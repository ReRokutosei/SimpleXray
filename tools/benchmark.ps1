# SimpleXray TUN Benchmark Runner
# Usage: powershell -ExecutionPolicy Bypass -File tools/benchmark.ps1 [-ServerIp 10.35.20.200] [-Duration 15]

param (
    [string]$ServerIp = "10.35.20.200",
    [int]$Duration = 15,
    [string]$AdbDevice = ""
)

$ErrorActionPreference = "Stop"

$ToolsDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BinDir = Join-Path $ToolsDir "bin"
if (!(Test-Path $BinDir)) { New-Item -ItemType Directory -Path $BinDir | Out-Null }

$HostIperf3 = Join-Path $BinDir "iperf3.exe"
$AndroidIperf3 = Join-Path $BinDir "iperf3-arm64"

function Log-Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Log-Success($msg) { Write-Host "[SUCCESS] $msg" -ForegroundColor Green }
function Log-Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Log-Error($msg) { Write-Host "[ERROR] $msg" -ForegroundColor Red }

# 1. Check & Download Binaries
function Ensure-Binaries {
    # Check host iperf3 in tools/bin or PATH
    if (Test-Path $HostIperf3) {
        Log-Info "Found Windows iperf3 at: $HostIperf3"
    } else {
        $iperf3OnPath = Get-Command iperf3 -ErrorAction SilentlyContinue
        if ($iperf3OnPath) {
            $script:HostIperf3 = $iperf3OnPath.Source
            Log-Info "Using host iperf3 from PATH: $HostIperf3"
        } else {
            # Try to find from winget package location
            $wingetPkg = Get-ChildItem -Path "$env:LOCALAPPDATA\Microsoft\WinGet" -Recurse -Filter "iperf3.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($wingetPkg) {
                Copy-Item "$($wingetPkg.DirectoryName)\*" $BinDir -Recurse -Force
                $script:HostIperf3 = Join-Path $BinDir "iperf3.exe"
                Log-Info "Copied iperf3 from WinGet: $HostIperf3"
            } else {
                Log-Error "Could not locate host iperf3.exe"
            }
        }
    }
}

# ADB helper
function Adb-Exec($argsStr) {
    $devFlag = if ($AdbDevice -ne "") { "-s $AdbDevice" } else { "" }
    $cmd = "adb $devFlag $argsStr"
    return (Invoke-Expression $cmd)
}

function Push-Android-Iperf3 {
    if (!(Test-Path $AndroidIperf3)) {
        Write-Error "Android iperf3 binary not found at $AndroidIperf3"
    }
    Log-Info "Pushing iperf3 to Android (/data/local/tmp/iperf3)..."
    Adb-Exec "push `"$AndroidIperf3`" /data/local/tmp/iperf3"
    Adb-Exec "shell chmod 755 /data/local/tmp/iperf3"
}

# Control App via Broadcast
function Set-App-State($backend, $mtu, $cmd = "start") {
    $pkg = "com.simplexray.re.debug"
    Log-Info "Sending Benchmark command: cmd=$cmd, backend=$backend, mtu=$mtu to $pkg"
    Adb-Exec "shell am broadcast --user 0 -a com.simplexray.re.action.BENCHMARK -p $pkg --es cmd $cmd --es backend $backend --ei mtu $mtu"
}

# Measure Single Case
function Run-Benchmark-Case($name, $backend, $mtu) {
    Write-Host "`n=======================================================" -ForegroundColor Magenta
    Log-Info "Running Benchmark: $name (Backend: $backend, MTU: $mtu)"
    Write-Host "=======================================================" -ForegroundColor Magenta

    # 1. Stop existing VPN
    Set-App-State -backend $backend -mtu $mtu -cmd "stop"
    Start-Sleep -Seconds 2

    # 2. Start VPN in configured mode
    Set-App-State -backend $backend -mtu $mtu -cmd "start"
    Log-Info "Waiting 4s for VPN & Core to fully initialize..."
    Start-Sleep -Seconds 4

    # 3. Start PC iperf3 server in background
    Log-Info "Starting PC iperf3 server on port 5201..."
    $serverProcess = Start-Process -FilePath $HostIperf3 -ArgumentList "-s -1" -PassThru -WindowStyle Hidden

    Start-Sleep -Seconds 1

    # 4. Run Upload Test (Android -> PC)
    Log-Info ">>> [1/2] Testing UPLOAD (Android -> PC, duration: ${Duration}s)..."
    $uploadJsonRaw = Adb-Exec "shell /data/local/tmp/iperf3 -c $ServerIp -t $Duration -J"
    $uploadBps = 0
    try {
        $uploadJson = ($uploadJsonRaw -join "`n") | ConvertFrom-Json
        $uploadBps = $uploadJson.end.sum_received.bits_per_second
    } catch {
        Log-Warn "Failed to parse upload iperf3 JSON, raw: $uploadJsonRaw"
    }
    $uploadMbps = [math]::Round($uploadBps / 1000000.0, 2)
    Log-Success "Upload Bandwidth: $uploadMbps Mbps"

    # 5. Restart PC server for download test
    if (!$serverProcess.HasExited) { Stop-Process -Id $serverProcess.Id -Force -ErrorAction SilentlyContinue }
    $serverProcess = Start-Process -FilePath $HostIperf3 -ArgumentList "-s -1" -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 1

    # 6. Run Download Test (PC -> Android, Reverse mode)
    Log-Info ">>> [2/2] Testing DOWNLOAD (PC -> Android, duration: ${Duration}s)..."
    $downloadJsonRaw = Adb-Exec "shell /data/local/tmp/iperf3 -c $ServerIp -R -t $Duration -J"
    $downloadBps = 0
    try {
        $downloadJson = ($downloadJsonRaw -join "`n") | ConvertFrom-Json
        $downloadBps = $downloadJson.end.sum_received.bits_per_second
    } catch {
        Log-Warn "Failed to parse download iperf3 JSON, raw: $downloadJsonRaw"
    }
    $downloadMbps = [math]::Round($downloadBps / 1000000.0, 2)
    Log-Success "Download Bandwidth: $downloadMbps Mbps"

    # 7. Sample CPU
    $cpuRaw = Adb-Exec "shell top -b -n 1 -m 5"
    
    # 8. Clean up
    if (!$serverProcess.HasExited) { Stop-Process -Id $serverProcess.Id -Force -ErrorAction SilentlyContinue }
    Set-App-State -backend $backend -mtu $mtu -cmd "stop"
    Start-Sleep -Seconds 2

    return [PSCustomObject]@{
        Name = $name
        Backend = $backend
        MTU = $mtu
        UploadMbps = $uploadMbps
        DownloadMbps = $downloadMbps
    }
}

# --- Main Flow ---
Ensure-Binaries
Push-Android-Iperf3

$results = @()

# Case 1: Hev (MTU 8500 - Hev default)
$results += Run-Benchmark-Case -name "Hev (MTU 8500)" -backend "hev" -mtu 8500

# Case 2: Hev (MTU 1500 - Standard MTU)
$results += Run-Benchmark-Case -name "Hev (MTU 1500)" -backend "hev" -mtu 1500

# Case 3: Xray Native TUN (MTU 1500 - Xray default)
$results += Run-Benchmark-Case -name "Xray TUN (MTU 1500)" -backend "xray" -mtu 1500

# Case 4: Xray Native TUN (MTU 8500 - Jumbo MTU)
$results += Run-Benchmark-Case -name "Xray TUN (MTU 8500)" -backend "xray" -mtu 8500

Write-Host "`n=======================================================" -ForegroundColor Green
Write-Host "                BENCHMARK RESULTS" -ForegroundColor Green
Write-Host "=======================================================" -ForegroundColor Green

$results | Format-Table -AutoSize -Property Name, Backend, MTU, UploadMbps, DownloadMbps

# Markdown output
Write-Host "`n### Markdown Summary Table:`n"
Write-Host "| Test Case | Backend | MTU | Upload (Mbps) | Download (Mbps) |"
Write-Host "| :--- | :--- | :--- | :--- | :--- |"
foreach ($r in $results) {
    Write-Host "| $($r.Name) | $($r.Backend) | $($r.MTU) | $($r.UploadMbps) Mbps | $($r.DownloadMbps) Mbps |"
}
