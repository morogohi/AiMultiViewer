param([string]$shot, [int]$times = 3)
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. "$PSScriptRoot\ui_helpers.ps1"
for ($i = 0; $i -lt $times; $i++) {
    & $script:ADB shell input swipe 540 1900 540 500 400 | Out-Null
    Start-Sleep -Milliseconds 1000
}
Start-Sleep -Seconds 1
Take-Shot $shot
