[Console]::OutputEncoding = [Text.Encoding]::UTF8
. "$PSScriptRoot\ui_helpers.ps1"
Launch-App
Start-Sleep 2
$xml = Get-UiDump
([regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }) -join "`n"
