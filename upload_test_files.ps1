# 테스트 파일을 실행 중인 에뮬레이터의 다운로드 폴더로 업로드합니다.
# 사용법: 아래 $files 목록에 본인 PC의 문서 경로를 추가한 뒤
#         powershell -ExecutionPolicy Bypass -File .\upload_test_files.ps1
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'C:\Users\' + $env:USERNAME + '\Android\Sdk\platform-tools\adb.exe' }

# src: PC의 원본 파일 경로 / dst: 에뮬레이터 다운로드 폴더 경로
$files = @(
    @{ src = "$PSScriptRoot\samples\sample.md"; dst = '/sdcard/Download/sample.md' }
    # @{ src = 'C:\path\to\your\document.pdf'; dst = '/sdcard/Download/document.pdf' }
    # @{ src = 'C:\path\to\your\document.hwpx'; dst = '/sdcard/Download/document.hwpx' }
)

& $adb wait-for-device | Out-Null
foreach ($f in $files) {
    if (Test-Path -LiteralPath $f.src) {
        & $adb push --sync $f.src $f.dst
        & $adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d ("file://" + $f.dst) | Out-Null
    } else {
        Write-Warning ("원본 없음: " + $f.src)
    }
}
Write-Output "업로드 완료. 앱에서 '문서 추가'로 다운로드 폴더에서 선택하세요."
