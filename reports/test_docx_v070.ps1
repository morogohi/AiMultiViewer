# v0.7.0 DOCX rendering verification
. "$PSScriptRoot\ui_helpers.ps1"

$shots = "$PSScriptRoot\shots_v070"
New-Item -ItemType Directory -Force -Path $shots | Out-Null

# 1) add ontology docx via SAF picker
Add-Doc "ontology_plan_rev2"
Start-Sleep -Seconds 5
Take-Shot "$shots\docx_ontology_open.png"

# back to list, reopen to confirm from list
& $script:ADB shell input keyevent 4 | Out-Null
Start-Sleep -Seconds 2

# 2) add SK draft docx
Add-Doc "251209_SK_Electlink"
Start-Sleep -Seconds 8
Take-Shot "$shots\docx_sk_draft_open.png"

Write-Output "DONE"
