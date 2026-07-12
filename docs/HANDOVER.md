# 인수인계 문서 (상세 개발 문서)

> 이 문서는 AI 멀티뷰어(Android) 프로젝트를 이어받는 개발자를 위한 상세 인수인계 자료입니다.
> 요약 수준의 가이드는 [DEVELOPMENT.md](DEVELOPMENT.md), 사용자 관점은 [USER_GUIDE.md](USER_GUIDE.md),
> 최근 개발·테스트 결과는 [TEST_REPORT.md](TEST_REPORT.md)를 참고하세요.

**기준 버전**: v0.3.0 (2026-07-12) · **저장소**: https://github.com/morogohi/AiMultiViewer

---

## 1. 프로젝트 개요

여러 문서 포맷(HWPX/DOCX/PPTX/XLSX/PDF/ODF/이미지/Google 문서/TXT/MD)을 하나의 안드로이드 앱에서 열람하고,
AI 요약·질의응답·비교·회의록·기술분석을 수행하며, **열람한 모든 문서를 Obsidian(llm-wiki) 마크다운으로
자동 변환·축적**하는 앱이다.

- 언어/프레임워크: Kotlin 2.0, Jetpack Compose (Material 3), 단일 Activity
- minSdk 26 (Android 8.0) / targetSdk·compileSdk 34 / JDK 17 / Gradle 8.9 (공식 wrapper)
- 외부 서비스 의존: 없음(기본). 클라우드 AI는 사용자가 설정에서 OpenAI 호환 API를 켤 때만 사용

## 2. 개발 환경 셋업

1. Android Studio(Koala+)로 저장소 루트 열기 → Gradle Sync
2. JDK 17 필요 (`JAVA_HOME` 설정). SDK 경로는 통상 `%LOCALAPPDATA%\Android\Sdk`
3. 명령줄 빌드: `gradlew.bat assembleDebug` / `gradlew.bat assembleRelease`
4. 에뮬레이터 실행 + 설치는 루트의 `run_app.bat` 사용 가능

### 저장소 루트 구성
```
app/                        # 앱 모듈 (아래 3절 상세)
docs/                       # 개발/사용자/보고/인수인계 문서
samples/                    # 테스트용 샘플 문서
screenshots/*.ps1           # 에뮬레이터 UI 자동화 검증 스크립트 (git 미추적)
run_app.bat                 # 에뮬레이터 기동 + 앱 설치/실행
upload_test_files.ps1       # 테스트 문서를 에뮬레이터로 push
sync_wiki_from_device.ps1   # 기기 → PC Obsidian 볼트 수집 (llm-wiki)
.github/workflows/android-ci.yml  # CI/CD
keystore.properties, release.jks  # 릴리스 서명 (git 미추적 — 8절 참조)
```

## 3. 코드 아키텍처 상세

### 3-1. 계층과 데이터 흐름
```
[SAF 문서 선택] → HomeViewModel.onDocumentPicked
   └ DocFormat.fromName(확장자) → 실패 시 DocFormat.fromMime(MIME)
   └ DocumentStore(SharedPreferences)에 URI+권한 영속화
[뷰어 진입] → ViewerViewModel.load
   ├ 저장 포맷이 UNKNOWN이면 이름/MIME으로 재감지 (구버전 데이터 호환)
   ├ ParserRegistry.parse(context, uri, format) → DocumentContent
   ├ UI 상태(StateFlow) 갱신 → ViewerScreen 렌더
   └ wikiAutoExport가 켜져 있으면 WikiExporter.export (백그라운드, 실패해도 열람에 영향 없음)
[AI 탭] → AiRepository
   ├ 요약: 클라우드 준비되면 LlmClient, 아니면 Summarizer(온디바이스)
   └ Q&A/비교/회의록/기술분석: LlmClient 필요 (없으면 안내)
```

### 3-2. 핵심 모델 (`domain/model/`)
- `Document(id, name, uri, format, addedAt)` — 목록 항목. URI는 SAF persistable 권한과 함께 저장
- `DocFormat` — 포맷 enum + `fromName`/`fromMime` 감지. 새 포맷은 여기부터 추가
- `DocumentContent(plainText, pages, renderableImageUri, …)` — 파싱 결과.
  `plainText`는 AI/위키 수집의 공통 입력, `renderableImageUri`가 있으면 이미지 뷰어로 렌더

### 3-3. 파서 계층 (`data/parser/`) — 각 파서의 동작 원리
| 파서 | 원리 | 주의점 |
|------|------|--------|
| `TxtParser` | UTF-8/EUC-KR 자동 판별 | BOM·인코딩 휴리스틱 |
| `MarkdownParser` | 원문 유지 + 경량 렌더(`ui/components/MarkdownText`) | |
| `PdfParser` | 렌더는 `PdfRenderer`, 텍스트는 PDFBox(android 포팅) | PDFBox는 `App.kt`에서 초기화 필요 |
| `DocxParser` | zip에서 `word/document.xml` → `XmlTextExtractor` | 필드코드/도형 메타 제거 로직이 핵심 (아래) |
| `PptxParser` | `ppt/slides/slide*.xml` + `notesSlide*.xml` 순회 | 슬라이드 번호순 정렬 필요 |
| `XlsxParser` | `sharedStrings.xml` 인덱스 → 시트 셀 값 치환 (XmlPullParser 스트리밍) | inline string(`t=inlineStr`)도 처리 |
| `OdfParser` | `content.xml` → `XmlTextExtractor` | ODT/ODS/ODP 공통 |
| `HwpxParser` | OWPML zip의 section XML 파싱 | 구형 HWP와 혼동 금지 |
| `GoogleDocParser` | SAF `openTypedAssetFileDescriptor(uri, "application/pdf")`로 Drive가 PDF로 변환한 스트림을 받아 PdfParser 재사용 | Drive 앱/네트워크 필요. 실기기에서만 완전 검증 가능 |
| `ImageParser` | ML Kit `TextRecognition`(한국어+라틴 모델)로 온디바이스 OCR | 첫 실행 시 모델 다운로드 발생 가능 |
| `UnsupportedBinaryParser` | HWP/DOC 구형 바이너리 안내 메시지 | |

**`XmlTextExtractor`**: OOXML/ODF 공통 텍스트 추출기. ①문단 태그(`w:p`, `a:p`, `text:p` 등)에서 줄바꿈 생성
②`invisibleBlocks` 정규식으로 `w:instrText`(필드코드), `w:delText`, `mc:Fallback`, `wp:posOffset`·`wp14:pctPos*`
(도형 위치 숫자) 블록을 먼저 제거 ③나머지 태그 strip. **DOCX에서 숫자 쓰레기가 보이면 이 목록에 태그를 추가**하면 된다.

**`ZipUtils.readEntries`**: OOXML 계열 파서 공통의 zip 엔트리 스트리밍 헬퍼. 새 zip 기반 포맷 파서를 만들 때 재사용.

**새 포맷 추가 절차**: `DocFormat`에 enum·확장자·MIME 추가 → `DocumentParser` 구현 → `ParserRegistry` 등록. 끝.

### 3-4. AI 계층 (`data/ai/`)
- `Summarizer` — 오프라인 추출 요약. 문장 분리 → 불용어 제외 단어 빈도 → 문장 점수화 → 상위 N문장을 원문 순서로.
  위키 자동 수집의 요약에도 이 모듈을 그대로 사용
- `LlmClient` — OpenAI 호환 `/chat/completions` 호출 (Base URL/Key/모델은 `SettingsStore`)
- `AiRepository` — 기능별 프롬프트 구성과 온디바이스/클라우드 라우팅

### 3-5. llm-wiki 자동 수집 (`data/wiki/WikiExporter.kt`) — v0.3.0 신규
1. `ViewerViewModel.load`에서 파싱 성공 + `plainText` 비어있지 않음 + `SettingsStore.wikiAutoExport=true`면 실행
2. 마크다운 조립: YAML 프런트매터(title/type: source/format/source/original/collected/updated/tags/status)
   + `## 자동 요약`(Summarizer 6문장) + `## 전문`(최대 20만 자, 초과 시 절단 표기)
3. 저장:
   - API 29+: MediaStore Files 컬렉션, `RELATIVE_PATH = "Documents/llm-wiki/"`.
     **같은 경로+파일명 row를 먼저 query하여 있으면 `openOutputStream(uri, "wt")`로 덮어씀** → 재열람 중복 방지.
     앱이 만든 파일이므로 별도 저장소 권한 불필요
   - API 26~28: `getExternalFilesDir()/llm-wiki` 폴백
4. 파일명: 원본 이름에서 금지 문자만 `_` 치환 후 `.md` 부착 (예: `견적서_….xlsx.md`)

### PC 수집 파이프라인
- `sync_wiki_from_device.ps1`: adb로 `/sdcard/Documents/llm-wiki`를 **폴더 단위로** 임시 폴더에 pull 후
  볼트 `C:\Users\morog\llm-wiki-research\inbox\aimultiviewer\`로 이동.
  (파일 단위 pull은 PowerShell 5.1 콘솔 인코딩 때문에 한글 파일명이 깨져 실패한 이력 있음 — 폴더 pull 유지할 것)
- Windows 작업 스케줄러에 `llm-wiki-sync` 작업이 매시간 실행되도록 등록되어 있음.
  확인: `schtasks /query /tn llm-wiki-sync` · 해제: `schtasks /delete /tn llm-wiki-sync /f`
- 볼트 반영: inbox 파일을 Cursor에서 "ingest inbox/aimultiviewer/<파일명>" 요청으로 위키 페이지화

## 4. 화면(UI) 구성

- `ui/AppNavigation.kt` — home / viewer/{docId} / settings 라우팅
- `ui/home/` — 문서 목록(카드), SAF 문서 추가 FAB, 삭제, 설정 진입
- `ui/viewer/` — 탭 2개: 보기(`ViewerScreen` — PDF 페이지 스와이프, 이미지면 `ImageViewer`, 그 외 텍스트/마크다운),
  AI(요약·회의록·기술분석·비교 버튼 + Q&A 입력)
- `ui/settings/` — 클라우드 AI(스위치/BaseURL/Key/모델) + **열람 문서 자동 수집 스위치**. 저장 버튼으로 일괄 반영

## 5. 영속화

- `DocumentStore` — 문서 목록을 SharedPreferences에 JSON으로 저장. SAF 권한은 `takePersistableUriPermission`으로 유지
- `SettingsStore` — cloudEnabled/baseUrl/apiKey/model/**wikiAutoExport**(기본 true)

## 6. 빌드 · 서명 · 배포

### 로컬 릴리스 서명
- `keystore.properties`(git 미추적)가 루트에 있으면 자동으로 서명 구성 로드:
  `storeFile=release.jks / storePassword / keyAlias=aimultiviewer / keyPassword`
- 없으면 debug 서명으로 폴백 (경고만 출력). 키스토어 파일: 루트 `release.jks` (git 미추적)
- **인수인계 시 `release.jks`와 `keystore.properties`를 안전한 채널로 별도 전달할 것** (비밀번호는 이 문서에 기재하지 않음)

### CI/CD (GitHub Actions, `.github/workflows/android-ci.yml`)
- push/PR: debug APK 빌드 + 아티팩트 업로드
- `v*` 태그 push: release APK 서명 빌드 → GitHub Release 자동 생성 + APK 첨부
- 필요한 GitHub Secrets (Settings → Secrets and variables → Actions):
  `KEYSTORE_BASE64`(release.jks의 base64), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`
- 릴리스 절차: 버전 올리기(`app/build.gradle.kts`의 versionCode/versionName) → 커밋/푸시 →
  `git tag vX.Y.Z && git push origin vX.Y.Z` → Actions 완료 후 Releases 확인

## 7. 테스트 / 검증 방법

- 샘플: `samples/` + 실제 문서를 `upload_test_files.ps1`로 에뮬레이터 Download에 push
- UI 자동화(에뮬레이터): `screenshots/add_doc.ps1`(문서 추가 — FAB 좌표 탭), `screenshots/open_doc.ps1 -Name <검색어> -Shot <이름>`
  (열람 + uiautomator 텍스트 덤프 + 스크린샷 pull)
  - 스크린샷은 반드시 기기에 저장 후 `adb pull` (PowerShell 리다이렉트는 바이너리 깨짐)
  - 스크립트는 UTF-8 **BOM** 인코딩 유지 (PowerShell 5.1 한글 리터럴 깨짐 방지)
- 위키 수집 검증: 문서 열람 → `adb shell ls /sdcard/Documents/llm-wiki/` → `sync_wiki_from_device.ps1` → 볼트 inbox 확인
- 상세 결과는 [TEST_REPORT.md](TEST_REPORT.md)

## 8. 버전 이력

| 버전 | 내용 |
|------|------|
| v0.1.0 | 최초 릴리스: TXT/MD/PDF/DOCX/HWPX 뷰어 + AI 요약/Q&A/비교/회의록/기술분석, CI/CD·서명 구축 |
| v0.2.0 | PPTX/XLSX/ODF/Google 문서/이미지(OCR) 판독, MIME 포맷 감지, DOCX 추출 품질 수정 |
| v0.3.0 | llm-wiki(Obsidian) 자동 수집: WikiExporter + 설정 토글 + PC 동기화 스크립트 + 시간별 스케줄 |

## 9. 알려진 이슈 · 트러블슈팅

| 증상 | 원인/해결 |
|------|-----------|
| CI에서 gradlew Permission denied | `git update-index --chmod=+x gradlew` 후 커밋 (이미 적용됨). gradlew는 `.gitattributes`로 LF 강제 |
| CI "Could not find or load main class -Xmx64m" | gradlew 스크립트 손상 — `gradlew.bat wrapper --gradle-version 8.9`로 공식 스크립트 재생성 |
| Release 생성 403 | 워크플로에 `permissions: contents: write` 필요 (이미 적용됨) |
| DOCX에 숫자 쓰레기 | `XmlTextExtractor.invisibleBlocks`에 해당 태그 추가 |
| PowerShell 스크립트 한글 깨짐 | UTF-8 BOM으로 저장, 필요 시 `[Console]::OutputEncoding = UTF8` |
| adb로 한글 파일명 pull 실패 | 파일 단위가 아닌 **폴더 단위** pull 사용 |
| HWP/DOC 안 열림 | 사양 (구형 바이너리 미지원). HWPX/DOCX 변환 안내가 정상 동작 |

## 10. 로드맵 / 후속 과제

- **M2**: HWP(HWP 5.0 바이너리)/DOC 네이티브 파서 — hwplib 포팅 또는 자체 레코드 파서
- **M3**: 온디바이스 LLM(MediaPipe/Gemma) + 청크 임베딩 벡터 검색 기반 진짜 RAG
- **위키 고도화(제안)**: 클라우드 AI 활성 시 추상 요약을 프런트매터에 추가, 문서 내 개념 자동 태깅(위키링크),
  Syncthing 등으로 USB 없이 실기기 자동 동기화
- **Google 문서 실기기 E2E**: Drive 계정이 있는 실기기에서 최종 확인 필요
