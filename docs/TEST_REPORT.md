# 개발 및 테스트 결과 보고서

| 항목 | 내용 |
|------|------|
| 프로젝트 | AI 멀티뷰어 (Android) |
| 보고 대상 버전 | v0.2.0 (문서 포맷 확장) · v0.3.0 (llm-wiki 자동 수집) |
| 작성일 | 2026-07-12 |
| 저장소 | https://github.com/morogohi/AiMultiViewer |
| 최신 릴리스 | [v0.3.0](https://github.com/morogohi/AiMultiViewer/releases/tag/v0.3.0) (`app-release.apk` 서명 배포) |

---

## 1. 개발 내용 요약

### v0.2.0 — 문서 포맷 확장
| 기능 | 구현 내용 |
|------|-----------|
| PPTX 판독 | `PptxParser` 신규 — 슬라이드별 본문 텍스트 + 발표자 노트 추출 (DrawingML `a:t`/`a:p` 파싱) |
| XLSX 판독 | `XlsxParser` 신규 — `sharedStrings.xml` + 시트별 셀 값을 XmlPullParser로 추출, 시트 단위 구분 출력 |
| ODF 판독 | `OdfParser` 신규 — ODT/ODS/ODP의 `content.xml` 텍스트 추출 |
| Google 문서 판독 | `GoogleDocParser` 신규 — Drive 가상 파일을 SAF `openTypedAssetFileDescriptor`로 PDF 변환 후 기존 PDF 파이프라인 재사용 |
| 이미지 판독 (OCR) | `ImageParser` 신규 — ML Kit 온디바이스 텍스트 인식 (한국어 + 라틴), 네트워크 불필요 |
| MIME 기반 포맷 감지 | 확장자가 없는 SAF URI(Drive 등)를 위해 `DocFormat.fromMime` 추가, 홈/뷰어 양쪽에 적용 |
| DOCX 품질 개선 | 필드 코드·도형 위치 메타데이터(`w:instrText`, `wp:posOffset` 등)가 본문에 숫자 쓰레기로 섞이던 문제 수정 (`XmlTextExtractor.invisibleBlocks`) |

### v0.3.0 — llm-wiki(Obsidian) 자동 수집
| 기능 | 구현 내용 |
|------|-----------|
| 자동 변환·축적 | `WikiExporter` 신규 — 문서 열람(파싱 성공) 시 YAML 프런트매터 + 온디바이스 자동 요약(6문장) + 전문(全文)을 마크다운으로 조립해 기기 `Documents/llm-wiki/`에 저장 |
| 중복 방지 | 파일명을 원본 이름 기반으로 고정, 재열람 시 MediaStore로 기존 파일을 갱신(신규 생성 없음) |
| 설정 토글 | 설정 화면에 "열람 문서 자동 수집" 스위치 추가 (기본 켜짐, `SettingsStore.wikiAutoExport`) |
| PC 수집 스크립트 | `sync_wiki_from_device.ps1` — adb로 기기 폴더를 통째로 pull하여 볼트 `llm-wiki-research/inbox/aimultiviewer/`로 이동 (한글 파일명 안전) |
| 자동 동기화 | Windows 작업 스케줄러에 `llm-wiki-sync` 작업 등록 — 매시간 자동 수집 |

---

## 2. 테스트 환경

- **기기**: Android 에뮬레이터 (Pixel 계열 AVD, API 34)
- **빌드**: 서명된 release APK (`assembleRelease`, R8 적용)
- **검증 방법**: adb + UI Automator 스크립트(`screenshots/add_doc.ps1`, `screenshots/open_doc.ps1`)로 문서 추가 → 열람 → 화면 텍스트 덤프/스크린샷 확보 후 육안·문자열 검증
- **테스트 데이터**: 실제 업무 문서 8종 (PPTX 1, PDF 2, XLSX 2, DOCX 2, HWP 1) + ODT/이미지 샘플

## 3. 테스트 결과

### 3-1. 실문서 판독 테스트 (8종)

| # | 문서 | 포맷 | 결과 | 비고 |
|---|------|------|:----:|------|
| 1 | ChargeIQ_개선보고_M6M7.pptx | PPTX | ✅ | 슬라이드 본문 + 발표자 노트 정상 추출 |
| 2 | revenuereport.pdf | PDF | ✅ | 페이지 렌더 + 텍스트 추출 정상 |
| 3 | 견적서_SK일렉링크 DRMS 구축_20260610.xlsx | XLSX | ✅ | 4개 시트 셀 값(인건비·단가표 포함) 정상 추출 |
| 4 | 251209_SK Electlink PoC_Draft.docx | DOCX | ✅ | 초기 숫자 쓰레기(`1403841815758…`) 발견 → 필드/도형 메타 제거로 수정 완료 |
| 5 | 작업계_전기차충전기 볼라드스토퍼_이마트신제주점.hwp | HWP | ⚠️ 미지원 | 구형 바이너리 — 안내 메시지 정상 표시 (HWPX 변환 권장) |
| 6 | 에스케이일렉링크 명세서.xlsx | XLSX | ✅ | 시트별 텍스트 정상 |
| 7 | Refund-3693-9270 (1).pdf | PDF | ✅ | 정상 |
| 8 | 운영지능_온톨로지_기술개발계획서_rev2.docx | DOCX | ✅ | 필드 코드 제거 후 본문 깨끗하게 추출 |

### 3-2. 신규 포맷 기능 테스트

| 항목 | 결과 | 검증 내용 |
|------|:----:|-----------|
| ODT 열람 | ✅ | content.xml 텍스트 추출, 문단 구분 정상 |
| 이미지 OCR | ✅ | 한국어+영문 혼합 이미지에서 텍스트 인식 확인 (ML Kit 온디바이스) |
| Google 문서 | ✅(설계 검증) | PDF 내보내기 경로 코드 검증. 에뮬레이터에 Drive 계정이 없어 실계정 E2E는 실기기에서 확인 필요 |

### 3-3. llm-wiki 자동 수집 파이프라인 (v0.3.0 E2E)

| 단계 | 결과 | 증적 |
|------|:----:|------|
| ① 문서 열람 시 자동 md 생성 | ✅ | `revenuereport.pdf`, `견적서_….xlsx` 열람 → 기기 `/sdcard/Documents/llm-wiki/`에 `.md` 2건 생성 확인 (`ls` 검증) |
| ② 마크다운 내용 검증 | ✅ | 프런트매터(title/format/collected/tags) + `## 자동 요약`(핵심 6문장) + `## 전문` 구조, UTF-8 한글 정상 |
| ③ 재열람 시 갱신(중복 방지) | ✅ | 동일 파일명으로 MediaStore `openOutputStream("wt")` 덮어쓰기 확인 |
| ④ PC 볼트 수집 | ✅ | `sync_wiki_from_device.ps1` 실행 → `inbox/aimultiviewer/`에 2건 수집, 한글 파일명 보존 |
| ⑤ 주기 자동화 | ✅ | 작업 스케줄러 `llm-wiki-sync` 등록·조회 확인 (매시간) |
| ⑥ 설정 토글 | ✅ | 설정 → "열람 문서 자동 수집" on/off 저장 동작 |

### 3-4. 빌드 / CI·CD

| 항목 | 결과 |
|------|:----:|
| 로컬 `assembleRelease` (서명 포함) | ✅ BUILD SUCCESSFUL |
| GitHub Actions — master push 빌드 | ✅ success |
| GitHub Actions — v0.3.0 태그 릴리스 | ✅ success, Release에 `app-release.apk` + `app-debug.apk` 자동 첨부 |

## 4. 발견된 문제와 조치 이력

| 문제 | 원인 | 조치 |
|------|------|------|
| DOCX 본문에 긴 숫자 문자열 혼입 | `wp:posOffset` 등 도형 위치 메타데이터와 `w:instrText` 필드 코드가 텍스트로 추출됨 | `XmlTextExtractor`에 invisible block 제거 정규식 추가 |
| CI `./gradlew` Permission denied | Windows 커밋 시 실행 권한 미포함 | `git update-index --chmod=+x gradlew` |
| CI "Could not find or load main class -Xmx64m" | 수제 gradlew 스크립트의 JVM 옵션 인용 오류 | 공식 Gradle 8.9 wrapper 재생성 |
| Release 생성 403 | 워크플로 기본 토큰 권한 부족 | `permissions: contents: write` 추가 |
| 동기화 스크립트에서 한글 파일명 pull 실패 | PowerShell 5.1 콘솔 인코딩으로 `adb shell ls` 출력 깨짐 | 파일 단위 pull → 폴더 단위 pull로 변경 |

## 5. 알려진 제한사항

- **HWP / DOC (구형 바이너리)**: 미지원. 안내 메시지 표시. HWPX/DOCX 변환 권장 (로드맵 M2)
- **Google 문서**: Drive 앱/계정이 있는 실기기 환경 필요. 에뮬레이터 E2E 미수행
- **자동 요약**: 온디바이스 추출 요약(빈도 기반)이므로 표 위주 문서(견적서 등)는 요약 품질 제한 — 클라우드 AI 활성화 시 개선
- **전문 저장 한도**: Obsidian 성능 보호를 위해 20만 자 초과분은 절단 저장 (절단 시 문서 하단에 표기)
- **동기화 전제**: PC 수집은 기기가 USB(또는 에뮬레이터)로 연결된 상태에서만 동작

## 6. 결론

계획된 기능(PPTX·XLSX·ODF·Google 문서·이미지 OCR 판독, 열람 문서의 llm-wiki 자동 수집)이 모두 구현되었고,
실제 업무 문서 8종 및 E2E 파이프라인 검증을 통과했다. v0.3.0으로 서명 릴리스가 GitHub에 배포 완료되었다.
후속 과제는 [인수인계 문서](HANDOVER.md)의 로드맵 절 참조.
