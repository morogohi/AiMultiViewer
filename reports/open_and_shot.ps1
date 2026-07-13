param([string]$name, [string]$shot, [int]$settle = 8)
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. "$PSScriptRoot\ui_helpers.ps1"
Launch-App
Start-Sleep 2
$ok = Tap-Text $name 4
if (-not $ok) { exit 1 }
Start-Sleep -Seconds $settle
Take-Shot $shot
