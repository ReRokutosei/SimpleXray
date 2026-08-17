# SimpleXray Comprehensive Benchmark Runner
# Usage: powershell -ExecutionPolicy Bypass -File tools/benchmark.ps1 [-Mode all|wifi|usb|loopback] [-Duration 10]

param (
    [ValidateSet("all", "wifi", "usb", "loopback")]
    [string]$Mode = "all",
    [string]$WifiServerIp = "10.35.20.200",
    [string]$UsbServerIp = "192.168.232.59",
    [int]$Duration = 10,
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

# 1. Ensure Binaries
function Ensure-Binaries {
    if (Test-Path $HostIperf3) {
        Log-Info "Found Windows iperf3 at: $HostIperf3"
    } else {
        $iperf3OnPath = Get-Command iperf3 -ErrorAction SilentlyContinue
        if ($iperf3OnPath) {
            $script:HostIperf3 = $iperf3OnPath.Source
            Log-Info "Using host iperf3 from PATH: $HostIperf3"
        } else {
            $wingetPkg = Get-ChildItem -Path "$env:LOCALAPPDATA\Microsoft\WinGet" -Recurse -Filter "iperf3.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($wingetPkg) {
                Copy-Item "$($wingetPkg.DirectoryName)\*" $BinDir -Recurse -Force
                $script:HostIperf3 = Join-Path $BinDir "iperf3.exe"
                Log-Info "Copied iperf3 from WinGet: $HostIperf3"
            }
        }
    }
}

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

function Get-App-Uid {
    $out = Adb-Exec "shell `"pm list packages --user 0 -U | grep com.simplexray.re.debug`""
    if ($out -match "uid:(\d+)") {
        return $matches[1]
    }
    return ""
}

function Set-App-State($backend, $mtu, $cmd = "start") {
    $pkg = "com.simplexray.re.debug"
    Log-Info "Sending Benchmark command: cmd=$cmd, backend=$backend, mtu=$mtu to $pkg"
    Adb-Exec "shell am broadcast --user 0 -a com.simplexray.re.action.BENCHMARK -p $pkg --es cmd $cmd --es backend $backend --ei mtu $mtu"
}

function Get-App-Memory-MB {
    $memRaw = Adb-Exec "shell `"dumpsys meminfo com.simplexray.re.debug | grep 'TOTAL PSS:'`""
    if ($memRaw -match "TOTAL PSS:\s+(\d+)") {
        $pssKb = [double]$matches[1]
        return [math]::Round($pssKb / 1024.0, 1)
    }
    return 0.0
}

function Run-With-Cpu-Profiling($iperfCmd, $appUid, $sampleDuration) {
    $topCount = [math]::Max(2, [int]$sampleDuration)
    $topScript = {
        param($dev, $uid, $count)
        $flag = if ($dev -ne "") { "-s $dev" } else { "" }
        $cmd = "adb $flag shell top -b -d 1 -n $count -u $uid"
        Invoke-Expression $cmd
    }
    $topJob = Start-Job -ScriptBlock $topScript -ArgumentList $AdbDevice, $appUid, $topCount

    $iperfRaw = Adb-Exec $iperfCmd

    $topOutput = Wait-Job $topJob -Timeout ($sampleDuration + 5) | Receive-Job
    Remove-Job $topJob -Force -ErrorAction SilentlyContinue

    $cpuSamples = @()
    foreach ($line in $topOutput) {
        $trimmed = $line.Trim()
        if ($trimmed -match "(com\.simplexray|libxray)") {
            $parts = $trimmed -split '\s+'
            if ($parts.Length -ge 10) {
                $cpuCandidate = $parts[8]
                if ($cpuCandidate -match "^[0-9\.]+$") {
                    $cpuSamples += [double]$cpuCandidate
                }
            }
        }
    }

    $avgCpu = 0.0
    $peakCpu = 0.0
    if ($cpuSamples.Count -gt 0) {
        $measure = $cpuSamples | Measure-Object -Average -Maximum
        $avgCpu = [math]::Round($measure.Average, 1)
        $peakCpu = [math]::Round($measure.Maximum, 1)
    }

    return @{
        IperfOutput = $iperfRaw
        AvgCpu = $avgCpu
        PeakCpu = $peakCpu
    }
}

# Measure Remote Network Case (Wi-Fi / USB)
function Run-Remote-Case($name, $backend, $mtu, $appUid, $serverIp, $parallel = 1, $medium = "Wi-Fi") {
    $parDesc = if ($parallel -gt 1) { " [P=$parallel]" } else { " [Single Stream]" }
    $fullName = "$name$parDesc"

    Write-Host "`n=======================================================" -ForegroundColor Magenta
    Log-Info "Running: $fullName ($medium, Backend: $backend, MTU: $mtu, Target: $serverIp)"
    Write-Host "=======================================================" -ForegroundColor Magenta

    # Stop previous VPN
    Set-App-State -backend $backend -mtu $mtu -cmd "stop"
    Start-Sleep -Seconds 2

    # If testing VPN mode, start it
    if ($backend -ne "direct_none") {
        Set-App-State -backend $backend -mtu $mtu -cmd "start"
        Log-Info "Waiting 4s for VPN & Core to initialize..."
        Start-Sleep -Seconds 4
    }

    # Start PC iperf3 server in background
    $serverProcess = Start-Process -FilePath $HostIperf3 -ArgumentList "-s -1" -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 1

    $parFlags = if ($parallel -gt 1) { "-P $parallel -l 64K -w 4M" } else { "" }
    
    # 1. Upload
    Log-Info ">>> [1/2] Testing UPLOAD (Android -> PC, duration: ${Duration}s)..."
    $upResult = Run-With-Cpu-Profiling -iperfCmd "shell /data/local/tmp/iperf3 -c $serverIp -t $Duration $parFlags -J" -appUid $appUid -sampleDuration $Duration
    $uploadBps = 0
    try {
        $uploadJson = ($upResult.IperfOutput -join "`n") | ConvertFrom-Json
        $uploadBps = $uploadJson.end.sum_received.bits_per_second
    } catch {
        Log-Warn "Failed to parse upload iperf3 JSON"
    }
    $uploadMbps = [math]::Round($uploadBps / 1000000.0, 2)
    $uploadMemMb = Get-App-Memory-MB
    Log-Success "Upload: $uploadMbps Mbps | CPU: $($upResult.AvgCpu)% (Peak: $($upResult.PeakCpu)%) | MEM: $uploadMemMb MB"

    # Restart PC Server for Download
    if (!$serverProcess.HasExited) { Stop-Process -Id $serverProcess.Id -Force -ErrorAction SilentlyContinue }
    $serverProcess = Start-Process -FilePath $HostIperf3 -ArgumentList "-s -1" -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 1

    # 2. Download
    Log-Info ">>> [2/2] Testing DOWNLOAD (PC -> Android, duration: ${Duration}s)..."
    $downResult = Run-With-Cpu-Profiling -iperfCmd "shell /data/local/tmp/iperf3 -c $serverIp -R -t $Duration $parFlags -J" -appUid $appUid -sampleDuration $Duration
    $downloadBps = 0
    try {
        $downloadJson = ($downResult.IperfOutput -join "`n") | ConvertFrom-Json
        $downloadBps = $downloadJson.end.sum_received.bits_per_second
    } catch {
        Log-Warn "Failed to parse download iperf3 JSON"
    }
    $downloadMbps = [math]::Round($downloadBps / 1000000.0, 2)
    $downloadMemMb = Get-App-Memory-MB
    Log-Success "Download: $downloadMbps Mbps | CPU: $($downResult.AvgCpu)% (Peak: $($downResult.PeakCpu)%) | MEM: $downloadMemMb MB"

    if (!$serverProcess.HasExited) { Stop-Process -Id $serverProcess.Id -Force -ErrorAction SilentlyContinue }
    
    if ($backend -ne "direct_none") {
        Set-App-State -backend $backend -mtu $mtu -cmd "stop"
        Start-Sleep -Seconds 2
    }

    $overallPeakCpu = [math]::Max($upResult.PeakCpu, $downResult.PeakCpu)
    $peakMemMb = [math]::Max($uploadMemMb, $downloadMemMb)

    return [PSCustomObject]@{
        Name = $fullName
        Backend = $backend
        MTU = $mtu
        Medium = $medium
        Upload = "$uploadMbps Mbps"
        Download = "$downloadMbps Mbps"
        UpCpu = "$($upResult.AvgCpu)%"
        DownCpu = "$($downResult.AvgCpu)%"
        PeakCpu = "$overallPeakCpu%"
        PeakMem = "$peakMemMb MB"
    }
}

# Measure On-Device Loopback Case
function Run-Loopback-Case($name, $backend, $mtu, $appUid, $parallel = 1) {
    $parDesc = if ($parallel -gt 1) { " [P=$parallel]" } else { " [Single Stream]" }
    $fullName = "$name$parDesc"

    Write-Host "`n=======================================================" -ForegroundColor Magenta
    Log-Info "Running: $fullName (On-Device Loopback, Backend: $backend, MTU: $mtu)"
    Write-Host "=======================================================" -ForegroundColor Magenta

    Set-App-State -backend $backend -mtu $mtu -cmd "stop"
    Start-Sleep -Seconds 2

    if ($backend -ne "direct_none") {
        Set-App-State -backend $backend -mtu $mtu -cmd "start"
        Log-Info "Waiting 4s for VPN & Core to initialize..."
        Start-Sleep -Seconds 4
    }

    # Start local iperf3 server on phone port 5202
    Adb-Exec "shell `"pkill iperf3; (nohup /data/local/tmp/iperf3 -s -p 5202 > /dev/null 2>&1 &)`""
    Start-Sleep -Seconds 1

    $parFlags = if ($parallel -gt 1) { "-P $parallel -l 64K -w 4M" } else { "" }
    Log-Info ">>> Testing ON-DEVICE LOOPBACK (duration: ${Duration}s)..."
    $loopResult = Run-With-Cpu-Profiling -iperfCmd "shell /data/local/tmp/iperf3 -c 127.0.0.1 -p 5202 -t $Duration $parFlags -J" -appUid $appUid -sampleDuration $Duration
    
    Adb-Exec "shell pkill iperf3"
    
    $loopBps = 0
    try {
        $loopJson = ($loopResult.IperfOutput -join "`n") | ConvertFrom-Json
        $loopBps = $loopJson.end.sum_received.bits_per_second
    } catch {
        Log-Warn "Failed to parse loopback iperf3 JSON"
    }
    $loopGbps = [math]::Round($loopBps / 1000000000.0, 2)
    $loopMbps = [math]::Round($loopBps / 1000000.0, 2)
    $loopMemMb = Get-App-Memory-MB
    Log-Success "Loopback: $loopGbps Gbps ($loopMbps Mbps) | CPU: $($loopResult.AvgCpu)% (Peak: $($loopResult.PeakCpu)%) | MEM: $loopMemMb MB"

    if ($backend -ne "direct_none") {
        Set-App-State -backend $backend -mtu $mtu -cmd "stop"
        Start-Sleep -Seconds 2
    }

    return [PSCustomObject]@{
        Name = $fullName
        Backend = $backend
        MTU = $mtu
        Medium = "On-Device Loopback"
        Upload = "$loopGbps Gbps ($loopMbps Mbps)"
        Download = "$loopGbps Gbps ($loopMbps Mbps)"
        UpCpu = "$($loopResult.AvgCpu)%"
        DownCpu = "$($loopResult.AvgCpu)%"
        PeakCpu = "$($loopResult.PeakCpu)%"
        PeakMem = "$loopMemMb MB"
    }
}

# --- Main Flow ---
Ensure-Binaries
Push-Android-Iperf3
$appUid = Get-App-Uid
Log-Info "Detected App UID: $appUid"

$allResults = @()

# -------------------------------------------------------------
# 1. 5GHz Wi-Fi Benchmark Suite
# -------------------------------------------------------------
if ($Mode -in @("wifi", "all")) {
    Log-Info "`n======================================================="
    Log-Info "               SUITE 1: 5GHz Wi-Fi BENCHMARK"
    Log-Info "======================================================="
    # Physical Baseline
    $allResults += Run-Remote-Case -name "Wi-Fi Baseline (No VPN)" -backend "direct_none" -mtu 0 -appUid $appUid -serverIp $WifiServerIp -parallel 1 -medium "5GHz Wi-Fi"
    $allResults += Run-Remote-Case -name "Wi-Fi Baseline (No VPN)" -backend "direct_none" -mtu 0 -appUid $appUid -serverIp $WifiServerIp -parallel 8 -medium "5GHz Wi-Fi"
    
    # Single Stream (P=1)
    $allResults += Run-Remote-Case -name "Hev (MTU 1500)" -backend "hev" -mtu 1500 -appUid $appUid -serverIp $WifiServerIp -parallel 1 -medium "5GHz Wi-Fi"
    $allResults += Run-Remote-Case -name "Xray TUN (MTU 1500)" -backend "xray" -mtu 1500 -appUid $appUid -serverIp $WifiServerIp -parallel 1 -medium "5GHz Wi-Fi"
    $allResults += Run-Remote-Case -name "Hev (MTU 8500)" -backend "hev" -mtu 8500 -appUid $appUid -serverIp $WifiServerIp -parallel 1 -medium "5GHz Wi-Fi"
    $allResults += Run-Remote-Case -name "Xray TUN (MTU 8500)" -backend "xray" -mtu 8500 -appUid $appUid -serverIp $WifiServerIp -parallel 1 -medium "5GHz Wi-Fi"

    # Multi-Stream (P=8)
    $allResults += Run-Remote-Case -name "Hev (MTU 1500)" -backend "hev" -mtu 1500 -appUid $appUid -serverIp $WifiServerIp -parallel 8 -medium "5GHz Wi-Fi"
    $allResults += Run-Remote-Case -name "Xray TUN (MTU 1500)" -backend "xray" -mtu 1500 -appUid $appUid -serverIp $WifiServerIp -parallel 8 -medium "5GHz Wi-Fi"
    $allResults += Run-Remote-Case -name "Hev (MTU 8500)" -backend "hev" -mtu 8500 -appUid $appUid -serverIp $WifiServerIp -parallel 8 -medium "5GHz Wi-Fi"
    $allResults += Run-Remote-Case -name "Xray TUN (MTU 8500)" -backend "xray" -mtu 8500 -appUid $appUid -serverIp $WifiServerIp -parallel 8 -medium "5GHz Wi-Fi"
}

# -------------------------------------------------------------
# 2. USB 3.2 / 4.0 Tethering Suite
# -------------------------------------------------------------
if ($Mode -in @("usb", "all")) {
    Log-Info "`n======================================================="
    Log-Info "         SUITE 2: USB 3.2 / 4.0 TETHERING BENCHMARK"
    Log-Info "======================================================="
    # Physical Baseline
    $allResults += Run-Remote-Case -name "USB Baseline (No VPN)" -backend "direct_none" -mtu 0 -appUid $appUid -serverIp $UsbServerIp -parallel 1 -medium "USB 3.2 / 4.0"
    $allResults += Run-Remote-Case -name "USB Baseline (No VPN)" -backend "direct_none" -mtu 0 -appUid $appUid -serverIp $UsbServerIp -parallel 8 -medium "USB 3.2 / 4.0"

    # Single Stream (P=1)
    $allResults += Run-Remote-Case -name "Hev (MTU 1500)" -backend "hev" -mtu 1500 -appUid $appUid -serverIp $UsbServerIp -parallel 1 -medium "USB 3.2 / 4.0"
    $allResults += Run-Remote-Case -name "Xray TUN (MTU 1500)" -backend "xray" -mtu 1500 -appUid $appUid -serverIp $UsbServerIp -parallel 1 -medium "USB 3.2 / 4.0"
    $allResults += Run-Remote-Case -name "Hev (MTU 8500)" -backend "hev" -mtu 8500 -appUid $appUid -serverIp $UsbServerIp -parallel 1 -medium "USB 3.2 / 4.0"
    $allResults += Run-Remote-Case -name "Xray TUN (MTU 8500)" -backend "xray" -mtu 8500 -appUid $appUid -serverIp $UsbServerIp -parallel 1 -medium "USB 3.2 / 4.0"

    # Multi-Stream (P=8)
    $allResults += Run-Remote-Case -name "Hev (MTU 1500)" -backend "hev" -mtu 1500 -appUid $appUid -serverIp $UsbServerIp -parallel 8 -medium "USB 3.2 / 4.0"
    $allResults += Run-Remote-Case -name "Xray TUN (MTU 1500)" -backend "xray" -mtu 1500 -appUid $appUid -serverIp $UsbServerIp -parallel 8 -medium "USB 3.2 / 4.0"
    $allResults += Run-Remote-Case -name "Hev (MTU 8500)" -backend "hev" -mtu 8500 -appUid $appUid -serverIp $UsbServerIp -parallel 8 -medium "USB 3.2 / 4.0"
    $allResults += Run-Remote-Case -name "Xray TUN (MTU 8500)" -backend "xray" -mtu 8500 -appUid $appUid -serverIp $UsbServerIp -parallel 8 -medium "USB 3.2 / 4.0"
}

# -------------------------------------------------------------
# 3. On-Device Loopback Suite
# -------------------------------------------------------------
if ($Mode -in @("loopback", "all")) {
    Log-Info "`n======================================================="
    Log-Info "         SUITE 3: ON-DEVICE LOOPBACK BENCHMARK"
    Log-Info "======================================================="
    # Physical Baseline
    $allResults += Run-Loopback-Case -name "Loopback Baseline (No VPN)" -backend "direct_none" -mtu 0 -appUid $appUid -parallel 1
    $allResults += Run-Loopback-Case -name "Loopback Baseline (No VPN)" -backend "direct_none" -mtu 0 -appUid $appUid -parallel 8

    # Single Stream (P=1)
    $allResults += Run-Loopback-Case -name "Hev (MTU 1500)" -backend "hev" -mtu 1500 -appUid $appUid -parallel 1
    $allResults += Run-Loopback-Case -name "Xray TUN (MTU 1500)" -backend "xray" -mtu 1500 -appUid $appUid -parallel 1
    $allResults += Run-Loopback-Case -name "Hev (MTU 8500)" -backend "hev" -mtu 8500 -appUid $appUid -parallel 1
    $allResults += Run-Loopback-Case -name "Xray TUN (MTU 8500)" -backend "xray" -mtu 8500 -appUid $appUid -parallel 1

    # Multi-Stream (P=8)
    $allResults += Run-Loopback-Case -name "Hev (MTU 1500)" -backend "hev" -mtu 1500 -appUid $appUid -parallel 8
    $allResults += Run-Loopback-Case -name "Xray TUN (MTU 1500)" -backend "xray" -mtu 1500 -appUid $appUid -parallel 8
    $allResults += Run-Loopback-Case -name "Hev (MTU 8500)" -backend "hev" -mtu 8500 -appUid $appUid -parallel 8
    $allResults += Run-Loopback-Case -name "Xray TUN (MTU 8500)" -backend "xray" -mtu 8500 -appUid $appUid -parallel 8
}

Write-Host "`n==========================================================================================" -ForegroundColor Green
Write-Host "                             COMPREHENSIVE BENCHMARK RESULTS" -ForegroundColor Green
Write-Host "==========================================================================================" -ForegroundColor Green

$allResults | Format-Table -AutoSize -Property Name, Backend, MTU, Medium, Upload, Download, UpCpu, DownCpu, PeakCpu, PeakMem

Write-Host "`n### Markdown Summary Table:`n"
Write-Host "| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |"
Write-Host "| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |"
foreach ($r in ($allResults | Where-Object { $_ -and $_.Name })) {
    Write-Host "| $($r.Name) | $($r.Backend) | $($r.MTU) | $($r.Medium) | $($r.Upload) | $($r.Download) | $($r.UpCpu) | $($r.DownCpu) | $($r.PeakCpu) | $($r.PeakMem) |"
}
