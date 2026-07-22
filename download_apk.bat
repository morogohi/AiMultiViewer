@echo off
chcp 65001 >nul
REM ============================================================
REM  AI MultiViewer - GitHub 최신 릴리스 APK 다운로드
REM  (더블클릭으로 실행하면 다운로드 폴더에 저장됩니다)
REM ============================================================
setlocal

set "REPO=morogohi/AiMultiViewer"
set "URL=https://github.com/%REPO%/releases/latest/download/app-release.apk"
set "OUTDIR=%USERPROFILE%\Downloads"
set "OUTFILE=%OUTDIR%\AiMultiViewer-latest.apk"

echo.
echo  [1/2] 최신 릴리스 APK 다운로드 중...
echo        %URL%
echo.

curl -L --fail --progress-bar -o "%OUTFILE%" "%URL%"
if errorlevel 1 (
    echo.
    echo  [실패] 다운로드에 실패했습니다. 인터넷 연결 또는 릴리스 페이지를 확인하세요.
    echo         https://github.com/%REPO%/releases
    pause
    exit /b 1
)

echo.
echo  [2/2] 완료!
for %%F in ("%OUTFILE%") do echo        저장 위치: %%~fF  (%%~zF 바이트)
echo.
echo  스마트폰 설치 방법:
echo   - 이 APK를 폰으로 전송(카톡/드라이브/USB)한 뒤 파일을 열어 설치
echo   - 또는 USB 디버깅 연결 후: adb install "%OUTFILE%"
echo.
pause
