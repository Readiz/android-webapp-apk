# 검증 기록

## 2026-10-23 템플릿 미커밋 변경 정리

웹페이지 로딩과 15분 제한에 묶였던 업데이트 확인을 앱 실행·복귀마다 수행하도록 정리했다. 진행 중인 확인·다운로드는 합치며, 취소 유예는 해당 versionCode에만 적용한다. 더 높은 버전의 발견과 네트워크·설치 준비 실패 후 다음 복귀의 재시도를 막지 않는다. 최상위 README의 옛 15분 제한 설명도 수정했다.

- Python 생성·서명 보존 검사 **6개**, Node HTTP fixture 검사 **4개** 통과. 기존 키·APK·운영 앱은 변경하지 않았다.
- 템플릿의 `UpdateController.java`는 `new-home`의 메모 앱에 2026-10-19 커밋 `dabf3b4`로 반영된 파일과 바이트가 같다. 설치된 Codex 스킬은 이 저장소의 원본을 가리키는 링크이며 별도 사본 차이가 없다. VNC와 코딩노트 등 기존 앱의 개별 구현을 템플릿으로 덮어쓰지 않았다.
- Android 테스트 소스는 총 **18개**이며 실행·복귀, 중복 요청, 버전별 유예, 권한 거절·허용, 오프라인, 설치 준비 실패, 이전 설정 이관과 종료 뒤 UI 호출 방지를 검토했다. **이번 정리에서 Android 컴파일·JUnit·lint는 완료하지 못했다.** 기존 Gradle 8.13의 `metadata.bin`과 AGP 의존성이 없어 오프라인 빌드가 시작되지 않았다.
- 실제 SMB 연결을 확인한 전용 NAS 캐시에서 의존성을 복구했다. Gradle 실행 캐시를 NAS에 두면 파일 잠금에서 `Operation not supported`가 발생하므로 이를 중단하고 잠금·변환은 Mac, 다운로드 원본만 NAS로 분리했다. 이후 Mac의 사전 여유 공간 기준 **8 GiB** 아래로 내려가 빌드를 중단했다. 기존 캐시를 삭제하거나 전체 출력을 Mac으로 전환하지 않았다.

검증 로그와 작은 생성 fixture는 Git에서 제외한 `artifacts/reconcile-20261023/`에 남긴다. Mac에 충분한 여유를 확보한 뒤 서로 다른 설정의 생성 앱에 `:app:testDebugUnitTest :app:lintRelease :app:compileReleaseJavaWithJavac`를 실행해야 한다. 이 상태를 새 APK 빌드·배포 성공으로 취급하지 않는다. 실제 Android 기기는 현재 없어 설치·로그인·업데이트 확인을 반복하지 않았다.

## 2026-10-13 초기 보일러플레이트 검증

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
