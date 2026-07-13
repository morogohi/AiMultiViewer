# UI Automator 기반 에뮬레이터 조작 헬퍼
$ErrorActionPreference = 'Continue'
$script:ADB = "$env:USERPROFILE\Android\Sdk\platform-tools\adb.exe"

function Get-UiDump {
    & $script:ADB shell uiautomator dump /sdcard/ui.xml 2>$null | Out-Null
    & $script:ADB shell cat /sdcard/ui.xml 2>$null
}

function Find-NodeBounds([string]$textSub) {
    $xml = Get-UiDump
    if (-not $xml) { return $null }
    $pattern = 'text="([^"]*' + [regex]::Escape($textSub) + '[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $m = [regex]::Match($xml, $pattern)
    if ($m.Success) {
        return @{
            x = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
            y = ([int]$m.Groups[3].Value + [int]$m.Groups[5].Value) / 2
            text = $m.Groups[1].Value
        }
    }
    return $null
}

function Tap-Text([string]$textSub, [int]$maxScroll = 4) {
    for ($i = 0; $i -le $maxScroll; $i++) {
        $n = Find-NodeBounds $textSub
        if ($n) {
            & $script:ADB shell input tap $n.x $n.y | Out-Null
            Write-Output "TAP '$($n.text)' at $($n.x),$($n.y)"
            return $true
        }
        & $script:ADB shell input swipe 540 1600 540 700 300 | Out-Null
        Start-Sleep -Milliseconds 900
    }
    Write-Output "NOT FOUND: $textSub"
    return $false
}

function Take-Shot([string]$outFile) {
    $tmp = "/sdcard/cap_$([guid]::NewGuid().ToString('N').Substring(0,8)).png"
    & $script:ADB shell screencap -p $tmp | Out-Null
    & $script:ADB pull $tmp $outFile | Out-Null
    & $script:ADB shell rm $tmp | Out-Null
    Write-Output "SHOT -> $outFile"
}

function Launch-App {
    & $script:ADB shell am force-stop com.aimultiviewer | Out-Null
    Start-Sleep -Milliseconds 800
    & $script:ADB shell am start -n com.aimultiviewer/.MainActivity | Out-Null
    Start-Sleep -Seconds 3
}

function Add-Doc([string]$fileNameSub) {
    Launch-App
    # FAB (문서 추가)
    & $script:ADB shell input tap 870 2220 | Out-Null
    Start-Sleep -Seconds 3
    # SAF 피커에서 파일 탭 (다운로드 최근 목록에 보이지 않으면 스크롤)
    $ok = Tap-Text $fileNameSub 6
    Start-Sleep -Seconds 3
    return $ok
}

function Open-Doc([string]$fileNameSub, [string]$shotFile, [int]$settleSec = 6) {
    Launch-App
    $ok = Tap-Text $fileNameSub 5
    if (-not $ok) { return $false }
    Start-Sleep -Seconds $settleSec
    if ($shotFile) { Take-Shot $shotFile }
    return $true
}
