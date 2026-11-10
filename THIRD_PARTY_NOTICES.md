# 제3자 구성요소 고지 (Third-Party Notices)

AI 멀티뷰어 앱이 쓰는 제3자 구성요소입니다(`app/build.gradle.kts` 기준). 각 구성요소는 표기된 라이선스를 따르며, [LICENSE](LICENSE)의 제한은 여기에 적용되지 않습니다.

| 구성요소 | 용도 | 라이선스 |
|---|---|---|
| androidx.core, lifecycle, activity-compose, navigation-compose | 앱 기반 | Apache License 2.0 |
| Jetpack Compose (ui, material3, material-icons-extended, Compose BOM) | 화면 | Apache License 2.0 |
| kotlinx-coroutines-android, kotlinx-coroutines-play-services | 비동기 처리 | Apache License 2.0 |
| com.tom-roush:pdfbox-android 2.0.27.0 | PDF 텍스트 추출 | Apache License 2.0 |
| com.google.mlkit:text-recognition-korean 16.0.1 | 이미지 글자 인식 | ML Kit 이용약관 (Google APIs Terms of Service) |
| Gradle Wrapper (`gradle/`, `gradlew`) | 빌드 | Apache License 2.0 |

## 확인 필요

- 자체 HWP 5.0 파서는 한글과컴퓨터가 공개한 한글 문서 파일 형식 문서를 참고한 것으로 보입니다. 해당 공개 문서가 요구하는 출처 표시 문구가 있는지 원문을 확인해 앱 정보 화면과 이 파일에 반영하세요.
- 위 라이브러리의 라이선스는 각 프로젝트의 공개 라이선스 기준이며, 배포 APK에 포함되는 전이 의존성 전체 목록은 아직 뽑지 않았습니다(Gradle 라이선스 보고서 플러그인 등으로 확인 권장).
- `docs/assets/shot_*.png` 스크린샷에 보이는 예시 문서 내용의 출처를 확인하세요.
