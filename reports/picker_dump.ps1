[Console]::OutputEncoding = [Text.Encoding]::UTF8
. "$PSScriptRoot\ui_helpers.ps1"
Launch-App
& $script:ADB shell input tap 870 2220 | Out-Null
Start-Sleep -Seconds 4
Take-Shot "$env:TEMP\picker.png"
$xml = Get-UiDump
([regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }) -join "`n"
