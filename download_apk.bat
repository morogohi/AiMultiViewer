@echo off
chcp 65001 >nul
REM ============================================================
REM  AI MultiViewer - GitHub 최신 릴리스 APK 다운로드
REM
REM  - 저장 위치 : <프로젝트>\release\AiMultiViewer-v<버전>.apk
REM  - 버전 체계 : MAJOR.MINOR.PATCH 3자리 (예: v0.6.1)
REM  (더블클릭으로 실행)
REM ============================================================
setlocal enabledelayedexpansion

set "REPO=morogohi/AiMultiViewer"
set "OUTDIR=%~dp0release"

echo.
echo  [1/3] 최신 릴리스 버전 조회 중...
set "TAG="
for /f "tokens=2 delims=:" %%A in ('curl -s https://api.github.com/repos/%REPO%/releases/latest ^| findstr /c:"\"tag_name\""') do set "TAG=%%A"
if not defined TAG (
    echo  [실패] 버전 정보를 가져오지 못했습니다. 인터넷 연결을 확인하세요.
    pause
    exit /b 1
)
REM 따옴표/쉼표/공백 제거 → v0.6.1 형태만 남김
set "TAG=%TAG:"=%"
set "TAG=%TAG:,=%"
set "TAG=%TAG: =%"
echo        최신 버전: %TAG%

set "URL=https://github.com/%REPO%/releases/download/%TAG%/app-release.apk"
set "OUTFILE=%OUTDIR%\AiMultiViewer-%TAG%.apk"

echo.
echo  [2/3] APK 다운로드 중...
echo        %URL%
if not exist "%OUTDIR%" mkdir "%OUTDIR%"
curl -L --fail --progress-bar -o "%OUTFILE%" "%URL%"
if errorlevel 1 (
    echo.
    echo  [실패] 다운로드에 실패했습니다. 릴리스 페이지를 확인하세요.
    echo         https://github.com/%REPO%/releases
    pause
    exit /b 1
)

echo.
echo  [3/3] 완료!
for %%F in ("%OUTFILE%") do echo        저장 위치: %%~fF  (%%~zF 바이트)
echo.
echo  release 폴더의 버전별 APK 목록:
for %%F in ("%OUTDIR%\AiMultiViewer-*.apk") do echo   - %%~nxF
echo.
echo  스마트폰 설치 방법:
echo   - 이 APK를 폰으로 전송(카톡/드라이브/USB)한 뒤 파일을 열어 설치
echo   - 또는 USB 디버깅 연결 후: adb install "%OUTFILE%"
echo.
pause
