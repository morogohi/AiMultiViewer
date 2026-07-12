# =============================================================================
#  AiMultiViewer → llm-wiki 볼트 수집 스크립트
#
#  앱에서 열람한 문서는 기기 Documents/llm-wiki/*.md 로 자동 변환·축적됩니다.
#  이 스크립트는 그 파일들을 PC의 Obsidian 볼트(inbox/aimultiviewer/)로 가져옵니다.
#
#  사용법:
#    powershell -ExecutionPolicy Bypass -File .\sync_wiki_from_device.ps1
#  (USB 연결된 실기기 또는 실행 중인 에뮬레이터 필요)
#
#  자동화(선택): Windows 작업 스케줄러에 등록하면 주기적으로 수집됩니다.
#    schtasks /create /tn "llm-wiki-sync" /sc hourly `
#      /tr "powershell -ExecutionPolicy Bypass -File C:\Users\morog\AiMultiViewer\sync_wiki_from_device.ps1"
# =============================================================================
param(
    [string]$VaultInbox = 'C:\Users\morog\llm-wiki-research\inbox\aimultiviewer'
)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = "$env:USERPROFILE\Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $adb)) { Write-Error 'adb.exe를 찾을 수 없습니다.'; exit 1 }

$devices = (& $adb devices) -match "device$"
if (-not $devices) { Write-Warning '연결된 기기/에뮬레이터가 없습니다.'; exit 1 }

# 폴더 단위로 임시 디렉터리에 pull (한글 파일명 안전)
$tmp = Join-Path $env:TEMP ("llm-wiki-pull-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
& $adb pull /sdcard/Documents/llm-wiki $tmp 2>&1 | Out-Null
if (-not (Test-Path $tmp)) { Write-Output '기기에 수집된 문서가 없습니다.'; exit 0 }

New-Item -ItemType Directory -Force $VaultInbox | Out-Null
$pulled = 0
Get-ChildItem $tmp -Filter *.md | ForEach-Object {
    Move-Item -Force $_.FullName (Join-Path $VaultInbox $_.Name)
    $pulled++
    Write-Output ("  + " + $_.Name)
}
Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue

Write-Output ("수집 완료: {0}개 문서 → {1}" -f $pulled, $VaultInbox)
Write-Output 'Obsidian에서 inbox/aimultiviewer/ 를 확인하세요.'
Write-Output '위키 반영(개념/엔티티 페이지 갱신)은 Cursor에서: ingest inbox/aimultiviewer/<파일명>'
