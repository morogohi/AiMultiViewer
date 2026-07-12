# 개발 문서 (DEVELOPMENT)

AI 멀티뷰어 안드로이드 앱의 개발 환경 구성, 아키텍처, 빌드/배포 절차를 설명합니다.

## 1. 개발 환경

| 항목 | 요구사항 |
|------|----------|
| JDK | 17 (Temurin/Oracle) |
| Android SDK | compileSdk 34, minSdk 26, targetSdk 34 |
| Gradle | 8.9 (Wrapper 포함) |
| Kotlin | 2.0 + Compose Compiler Plugin |
| IDE | Android Studio Koala 이상 권장 |

`local.properties`에 SDK 경로를 지정합니다 (커밋 금지, `.gitignore` 처리됨):

```properties
sdk.dir=C:\\Users\\<사용자>\\Android\\Sdk
```

## 2. 프로젝트 구조

```
app/src/main/java/com/aimultiviewer/
├── App.kt                     # Application (PDFBox 초기화 등)
├── MainActivity.kt            # 단일 액티비티 + Compose 진입점
├── domain/model/              # Document, DocumentContent, DocFormat
├── data/
│   ├── DocumentStore.kt       # 문서 목록 영속화 (SharedPreferences)
│   ├── SettingsStore.kt       # 클라우드 AI 설정 (Base URL/API Key/모델)
│   ├── parser/                # 포맷별 파서
│   │   ├── DocumentParser.kt  # 파서 공통 인터페이스
│   │   ├── ParserRegistry.kt  # 확장자 → 파서 매핑
│   │   ├── TxtParser.kt       # UTF-8/EUC-KR 자동 인식
│   │   ├── MarkdownParser.kt
│   │   ├── PdfParser.kt       # PdfRenderer(렌더) + PDFBox(텍스트)
│   │   ├── DocxParser.kt      # OOXML(zip) 파싱
│   │   ├── PptxParser.kt      # 슬라이드 텍스트 + 발표자 노트
│   │   ├── XlsxParser.kt      # sharedStrings + 시트 셀 (XmlPullParser)
│   │   ├── OdfParser.kt       # ODT/ODS/ODP (content.xml)
│   │   ├── GoogleDocParser.kt # Drive 가상 파일 → PDF 내보내기 판독
│   │   ├── ImageParser.kt     # ML Kit 온디바이스 OCR (한국어+라틴)
│   │   ├── HwpxParser.kt      # OWPML(zip) 파싱
│   │   └── UnsupportedBinaryParser.kt  # HWP/DOC 안내 메시지
│   └── ai/
│       ├── AiRepository.kt    # AI 기능 오케스트레이션
│       ├── Summarizer.kt      # 온디바이스 추출 요약 (오프라인)
│       └── LlmClient.kt       # OpenAI 호환 Chat Completions 클라이언트
└── ui/
    ├── AppNavigation.kt       # Navigation Compose 라우팅
    ├── home/                  # 문서 목록 화면
    ├── viewer/                # 문서 보기 + AI 패널 (탭)
    ├── settings/              # 클라우드 AI 설정 화면
    ├── components/            # MarkdownText 등 공용 컴포넌트
    └── theme/                 # Material 3 테마
```

### 아키텍처 원칙
- **단방향 데이터 흐름**: ViewModel(StateFlow) → Compose UI
- **파서 플러그인 구조**: 새 포맷은 `DocumentParser` 구현 후 `ParserRegistry`에 등록만 하면 됨
- **AI 이중화**: 온디바이스 요약(키 불필요) + 클라우드 LLM(OpenAI 호환 API)
- 문서 접근은 SAF(Storage Access Framework) URI 권한 유지 방식 사용

## 3. 빌드

```bash
# 디버그 APK
./gradlew assembleDebug        # macOS/Linux
gradlew.bat assembleDebug      # Windows

# 릴리스 APK (서명 설정 필요, 아래 참고)
gradlew.bat assembleRelease
```

산출물 위치:
- 디버그: `app/build/outputs/apk/debug/app-debug.apk`
- 릴리스: `app/build/outputs/apk/release/app-release.apk`

## 4. 릴리스 서명

릴리스 빌드는 `keystore.properties`(로컬) 또는 환경변수(CI)에서 서명 정보를 읽습니다.
둘 다 없으면 디버그 키로 서명됩니다 (개인 테스트용).

### 로컬: keystore.properties (커밋 금지)

프로젝트 루트에 `keystore.properties` 생성:

```properties
storeFile=release.jks
storePassword=<스토어 비밀번호>
keyAlias=aimultiviewer
keyPassword=<키 비밀번호>
```

키스토어 생성 (최초 1회):

```bash
keytool -genkeypair -v -keystore release.jks -alias aimultiviewer \
  -keyalg RSA -keysize 2048 -validity 10000
```

> `release.jks`, `keystore.properties`는 `.gitignore`에 등록되어 있어 커밋되지 않습니다.
> **키스토어를 분실하면 같은 서명으로 업데이트를 배포할 수 없으니 안전한 곳에 백업하세요.**

### CI: GitHub Actions Secrets

`v*` 태그 푸시 시 릴리스 APK를 자동 빌드/서명하려면 저장소 Secrets에 등록:

| Secret | 내용 |
|--------|------|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` 결과 |
| `SIGNING_STORE_PASSWORD` | 스토어 비밀번호 |
| `SIGNING_KEY_ALIAS` | 키 별칭 (예: aimultiviewer) |
| `SIGNING_KEY_PASSWORD` | 키 비밀번호 |

## 5. CI/CD (GitHub Actions)

`.github/workflows/android-ci.yml`:

- **push / PR (master, main)**: 디버그 APK 빌드 → 아티팩트 업로드
- **`v*` 태그 푸시**: 릴리스 APK 서명 빌드 → GitHub Release 자동 생성 + APK 첨부

릴리스 배포 절차:

```bash
# 1. versionCode / versionName 업데이트 (app/build.gradle.kts)
# 2. 태그 생성 및 푸시
git tag v0.1.0
git push origin v0.1.0
# 3. Actions 완료 후 Releases 페이지에서 APK 확인
```

## 6. 새 문서 포맷 추가하기

1. `data/parser/`에 `DocumentParser` 구현 클래스 작성:

```kotlin
class OdtParser : DocumentParser {
    override fun canParse(format: DocFormat) = format == DocFormat.ODT
    override suspend fun parse(context: Context, uri: Uri): DocumentContent { ... }
}
```

2. `DocFormat` enum에 포맷 추가 (확장자 매핑 포함)
3. `ParserRegistry`에 파서 등록
4. 필요 시 `ViewerScreen`에 전용 렌더러 추가 (기본은 텍스트 뷰)

## 7. AI 기능 상세

| 기능 | 엔진 | 비고 |
|------|------|------|
| 요약 | 온디바이스(기본) / 클라우드 | 온디바이스는 빈도 기반 추출 요약 |
| 질의응답(Q&A) | 클라우드 | 문서 본문을 컨텍스트로 주입 (최대 12,000자) |
| 문서 비교 | 클라우드 | 두 문서 각각 6,000자 컨텍스트 |
| 회의록 생성 | 클라우드 | 안건/논의/결정/액션아이템 구조화 |
| 기술문서 분석 | 클라우드 | 개요/용어/구성요소/이슈 |

클라우드는 OpenAI 호환 Chat Completions API를 사용하므로 OpenAI, Azure OpenAI,
Ollama(로컬), LM Studio 등 호환 서버 어디든 연결 가능합니다.
기본값: `https://api.openai.com/v1` / `gpt-4o-mini`.

## 8. 테스트 / 검증

에뮬레이터 수동 검증 절차 (자동 테스트는 로드맵):

```powershell
# 에뮬레이터 시작 후
gradlew.bat installDebug
# 테스트 문서 업로드 (스크립트 내 경로를 본인 파일로 수정)
powershell -ExecutionPolicy Bypass -File .\upload_test_files.ps1
```

검증 체크리스트:
- [ ] 문서 추가(SAF 픽커) → 목록 표시
- [ ] TXT/MD/PDF/DOCX/HWPX 각각 열람
- [ ] AI 탭 → 요약 (키 없이 온디바이스 동작)
- [ ] 설정 → 클라우드 AI 켜고 Q&A/비교/회의록/기술분석
- [ ] 앱 재시작 후 문서 목록 유지

## 9. 로드맵

- M2: HWP/DOC 네이티브 바이너리 파서
- M3: 온디바이스 LLM(MediaPipe/Gemma) + 벡터 검색 기반 RAG
- M4: Obsidian 볼트 내보내기 (SAF, Markdown + 위키링크)
- 단위 테스트/UI 테스트 도입, Play 스토어 배포(AAB)
