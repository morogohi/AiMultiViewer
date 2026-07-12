# AI 멀티뷰어 (Android)

여러 문서 포맷을 한 앱에서 보고, AI로 요약·질의응답·비교·회의록 생성·기술분석을 수행하는 안드로이드 앱입니다.

## 설치 (일반 사용자)

[Releases](../../releases) 페이지에서 최신 `app-release.apk`를 내려받아 설치하세요. (Android 8.0 이상)

## 문서

- [사용자 가이드](docs/USER_GUIDE.md) — 설치, 문서 열기, AI 기능, 클라우드 설정, FAQ
- [개발 문서](docs/DEVELOPMENT.md) — 개발 환경, 아키텍처, 빌드/서명/CI/CD, 포맷 확장

## 지원 문서

| 포맷 | 보기 | AI 분석 | 비고 |
|------|:----:|:-------:|------|
| TXT | ✅ | ✅ | UTF-8/EUC-KR 자동 인식 |
| Markdown | ✅ | ✅ | 경량 렌더러 |
| PDF | ✅ | ✅ | 페이지 렌더 + 텍스트 추출 |
| DOCX | ✅(텍스트) | ✅ | OOXML 파싱 |
| PPTX | ✅(텍스트) | ✅ | 슬라이드별 텍스트 + 발표자 노트 |
| XLSX | ✅(텍스트) | ✅ | 시트별 셀 값 추출 |
| HWPX | ✅(텍스트) | ✅ | OWPML(한글 개방형) 파싱 |
| ODT/ODS/ODP | ✅(텍스트) | ✅ | OpenDocument (Google Docs 내보내기 등) |
| Google 문서 | ✅(PDF 렌더) | ✅ | Drive에서 선택 시 PDF로 내보내 판독 |
| 이미지 (JPG/PNG 등) | ✅ | ✅ | 온디바이스 OCR(한국어+영문)로 텍스트 판독 |
| HWP | ⏳ | ⏳ | 구 바이너리, 후속 지원(HWPX 권장) |
| DOC | ⏳ | ⏳ | 구 바이너리, 후속 지원(DOCX 권장) |

## AI 기능

- **문서 자동 요약**: 온디바이스(오프라인, 기본) 또는 클라우드(고품질)
- **문서 질의응답(RAG/Q&A)**: 문서 내용 기반 답변 (클라우드)
- **문서 비교**: 두 문서의 공통점/차이점 분석 (클라우드)
- **회의록 자동 생성**: 안건/결정/액션아이템 구조화 (클라우드)
- **기술문서 분석**: 개요/용어/구성요소/이슈 정리 (클라우드)

> 클라우드 기능은 OpenAI 호환 API를 사용합니다. 설정에서 Base URL / API Key / 모델명을 입력하세요.
> 요약은 키 없이 온디바이스로 즉시 동작합니다.

## llm-wiki 자동 수집 (Obsidian 연동)

앱에서 문서를 열람하면 자동으로 Obsidian 호환 마크다운(YAML 프런트매터 + 자동 요약 + 전문)으로
변환되어 기기의 `Documents/llm-wiki/` 폴더에 축적됩니다. 같은 문서를 다시 열면 갱신되어 중복이 생기지 않습니다.

PC의 Obsidian 볼트로 수집하려면:

```powershell
# 기기(USB) 또는 에뮬레이터 연결 후
powershell -ExecutionPolicy Bypass -File .\sync_wiki_from_device.ps1
```

기본적으로 `llm-wiki-research/inbox/aimultiviewer/` 로 가져오며, Windows 작업 스케줄러에
`llm-wiki-sync` 작업으로 등록하면 매시간 자동 동기화됩니다. 끄고 싶으면 앱 설정에서
"열람 문서 자동 수집" 토글을 비활성화하세요.

## 빌드 / 실행

### 사전 요구사항
- Android Studio (Koala 이상 권장)
- Android SDK (compileSdk 34)
- JDK 17

### Android Studio 사용 (권장)
1. Android Studio에서 이 폴더를 **Open**
2. Gradle Sync 완료 대기 (의존성 자동 다운로드)
3. 스마트폰을 USB로 연결하고 **USB 디버깅** 활성화
4. ▶ Run 버튼으로 기기에 설치/실행

### 명령줄 빌드
```bash
# (최초 1회) Gradle Wrapper JAR 생성 — 시스템에 gradle이 설치된 경우
gradle wrapper --gradle-version 8.9

# 디버그 APK 빌드
./gradlew assembleDebug          # macOS/Linux
gradlew.bat assembleDebug        # Windows

# 연결된 기기에 설치
./gradlew installDebug
```
APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

### 릴리스 빌드 / 배포
릴리스 서명 설정과 GitHub Actions 자동 배포(`v*` 태그 푸시 → Release + APK 첨부)는
[개발 문서](docs/DEVELOPMENT.md#4-릴리스-서명)를 참고하세요.

## 아키텍처

```
ui/        Compose 화면 (home, viewer, settings) + 내비게이션
domain/    모델 (Document, DocumentContent, DocFormat)
data/
  parser/  포맷별 파서 (DocumentParser 인터페이스 + ParserRegistry)
  ai/      Summarizer(온디바이스), LlmClient(클라우드), AiRepository
  *Store   문서/설정 영속화 (SharedPreferences)
```

새 포맷 지원: `DocumentParser` 구현 후 `ParserRegistry`에 등록.

## 환경 정보
- minSdk 26 (Android 8.0) / targetSdk 34
- Kotlin 2.0 + Jetpack Compose (Material 3)

## 로드맵
- M2: HWP/DOC 네이티브 바이너리 파서
- M3: 온디바이스 LLM(MediaPipe/Gemma) + 벡터 검색 기반 진짜 RAG
- ~~M4: Obsidian 볼트 내보내기~~ ✅ v0.3.0 완료 (llm-wiki 자동 수집)
