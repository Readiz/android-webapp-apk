# 검증 기록

2026-10-13 Mac에서 Python 3.9, Node, JDK 17, Gradle 8.13, Android SDK platform 36/build-tools 35.0.0을 사용했다. 기존 SDK/Gradle 캐시를 재사용했다. 임시 생성 프로젝트·빌드·서명키는 Git 밖 `<임시 작업 폴더>/android-webapp-apk-validation`에 두었으며 약 16 MiB였다. 빌드 시작 전 2 GiB 여유 공간 기준을 적용했다.

## 통과한 검증

- Python 6개: 서로 다른 앱의 독립 생성, 기존 프로젝트 보존, 잘못된 URL/패키지/버전 거절, 잘못된 아이콘 거절, 기존 키 보존, 키 생성 실패 시 부분 파일 정리.
- Node HTTP 4개: 메타데이터 필드 제한, 최신·버전 고정·HEAD 응답, 잘못된 쿼리/경로 차단, 파일 손상/과도한 크기/다른 패키지 거절, 최신 게시 불완전 중 이전 버전 유지.
- 최종 템플릿에서 notes (`com.readiz.example.notes`, 0.1.1/code 2)와 library (`com.readiz.example.library`, 1.2.3/code 7)를 생성했다. 각 14개 Android 검사 통과, 총 28개. 양쪽 release lint 오류 0개와 하위 Android 호환 속성의 기존 UnusedAttribute 경고 각 2개. 양쪽 서명 APK 빌드와 aapt/apksigner 검증 통과.
- 실제 서명 APK 통합 검사 8항목: 첫 게시, 동일 재게시, 앱 격리, notes 0.1.0 → 0.1.1 동일 키 갱신, 버전 하향 거절, **같은 패키지를 다른 키로 다시 서명한 APK 거절**, 실제 로컬 서버 메타데이터/두 APK 경로 대조, 이전 버전 고정 다운로드 유지.
- `skill-creator`의 `quick_validate.py` 통과. 로컬 `~/.codex/skills/readiz-webapp-android`가 이 저장소의 스킬을 가리키며 생성기·assets·references를 함께 사용할 수 있다.

실제 기기 설치, 실계정 로그인, 앱별 운영 HTTPS 프록시 연결은 이번 공통 저장소 검증 범위가 아니다. 예시 URL은 예약된 example 도메인이다. AOA/Invest/TV/Music 운영 앱을 새 템플릿으로 교체하지 않았다.

## 반복 검사

```bash
python3 -m unittest discover -s tests -v
node --test tests/update-server.test.mjs
```

Android는 생성된 각 프로젝트의 `tools/build.py --output <새 Git 외부 폴더>`로 테스트·lint·서명 빌드를 수행한다. `JAVA_HOME`, `ANDROID_HOME`, `GRADLE_HOME`, 필요하면 `WEBAPP_SIGNING_DIR`을 지정한다. 상세 키 생성 명령은 [서명 문서](skills/readiz-webapp-android/references/signing.md)에 있다.

실제 릴리스 통합 검사는 다음 외부 fixture 배치를 사용한다. `notes-final`은 notes 설정의 versionCode=2/versionName=0.1.1이며 notes와 같은 키를 사용한다. library는 별도 키를 생성한다. 생성 명령의 출력 폴더와 `tools/build.py`의 출력 폴더를 아래 이름에 맞춘다.

```text
<fixtures>/notes-release/app-0.1.0.apk
<fixtures>/notes-final-release/app-0.1.1.apk
<fixtures>/library-final-release/app-1.2.3.apk
<fixtures>/library-signing/release.jks
<fixtures>/library-signing/password
```

```bash
python3 tests/verify_real_release.py --fixtures /absolute/fixtures
```

이 검사는 자체 임시 게시 폴더와 loopback 서버를 만들고 종료 시 정리한다. 재서명 fixture는 PKCS12 store/key password가 같으므로 `apksigner`에 `--ks-pass file:...`만 사용한다. 비밀번호 원문은 인수·출력에 넣지 않는다. 위 fixture APK와 키는 실제 앱에 배포하지 않는 테스트 전용이다.
