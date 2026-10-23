# 검증 기록

## 2026-10-23 템플릿 미커밋 변경 정리

웹페이지 로딩과 15분 제한에 묶였던 업데이트 확인을 앱 실행·복귀마다 수행하도록 정리했다. 진행 중인 확인·다운로드는 합치며, 취소 유예는 해당 versionCode에만 적용한다. 더 높은 버전의 발견과 네트워크·설치 준비 실패 후 다음 복귀의 재시도를 막지 않는다. 최상위 README의 옛 15분 제한 설명도 수정했다.

- Python 생성·서명 보존 검사 **6개**, Node HTTP fixture 검사 **4개** 통과. 기존 키·APK·운영 앱은 변경하지 않았다.
- 템플릿의 `UpdateController.java`는 `new-home`의 메모 앱에 2026-10-19 커밋 `dabf3b4`로 반영된 파일과 바이트가 같다. 설치된 Codex 스킬은 이 저장소의 원본을 가리키는 링크이며 별도 사본 차이가 없다. VNC와 코딩노트 등 기존 앱의 개별 구현을 템플릿으로 덮어쓰지 않았다.
- notes 설정(`com.readiz.example.notes`, 0.1.0/code 1)의 **debug·release Java 컴파일과 Android 검사 18개가 통과**했다(실패·오류·skip 0). 실행·복귀, 중복 요청, 버전별 유예, 권한 거절·허용, 오프라인, 설치 준비 실패, 이전 설정 이관과 종료 뒤 UI 호출 방지를 검사했다. library 설정과 release lint의 완료 결과는 아직 없다.
- 기존 Gradle 8.13의 `metadata.bin`과 AGP 의존성이 없어 처음에는 오프라인 빌드가 시작되지 않았다. 실제 SMB 연결을 확인한 전용 NAS 캐시에서 의존성을 복구했다. 실행 캐시를 NAS에 두면 파일 잠금에서 `Operation not supported`가 발생하므로 잠금·변환은 Mac, 다운로드 원본만 NAS로 분리했다. 받은 jar 123개(106,002,613 bytes)는 NAS에서 다시 읽어 Gradle 주소 해시와 대조했다. 기존 캐시를 삭제하거나 전체 출력을 Mac으로 전환하지 않았다.
- 디스크 정리 후 초기 여유 기준 8 GiB를 이번 한정 **7 GiB**로 조정했다. 다운로드 원본은 NAS에 두며, 로컬 검증 출력은 512 MiB, Gradle은 단일 worker·512 MiB heap으로 제한한다. 최소 7 GiB는 기존 NAS 운영 문서의 4 GiB 기준보다 3 GiB를 더 남기는 조건이다. 시스템 swap과 다른 앱에는 변경하지 않는다. AGP의 별도 lint worker에도 `-Pandroid.experimental.lint.heapSize=512m`을 지정하도록 재개 명령을 준비했다.
- notes의 `lintAnalyzeRelease` 도중 가용 공간이 6.42 GiB로 내려가 감시가 검증을 중단했다. 로그에 소스 lint 오류는 없으며 **공간 기준에 따른 중단**이다. 컴파일·JUnit을 lint와 분리한 재시도도 데몬 시작 때 6.91 GiB가 되어 중단했다. 이후에도 가용 공간이 줄어 추가 실행하지 않았다. 이번 작업의 Gradle·worker 프로세스는 남아 있지 않으며, notes의 통과 결과와 생성 fixture를 보존했다.

검증 로그·통과 결과의 체크포인트와 생성 fixture는 Git에서 제외한 `artifacts/reconcile-20261023/`에 남긴다. 이 상태를 새 APK 빌드·배포 성공으로 취급하지 않는다. 실제 Android 기기는 현재 없어 설치·로그인·업데이트 확인을 반복하지 않았다.

## 2026-10-24 남은 Android 검사 재개

7 GiB 중단·9 GiB 재개 조건을 다시 평가했다. 검증 출력은 이미 약 262 MiB였고 예상 증분은 수십 MiB였으며, 다운로드 원본은 실제 SMB 마운트의 NAS에 보관하고 있었다. 기존 SDK와 Robolectric SDK 23/26/35 jar를 그대로 재사용하고, 새 Java 임시파일·Android 사용자캐시·Gradle 잠금·변환 결과는 전용 로컬 출력 안으로 제한했다. 이에 이번 남은 검사에는 기존 NAS 운영 기준과 같은 **4 GiB 보호선**을 적용하되, **로컬 출력 512 MiB·단일 worker·Gradle/test/lint heap 각각 최대 512 MiB**와 3초 간격 감시를 유지했다. 원본·서명키·기존 앱·다른 프로세스·swap 파일은 변경하지 않았다.

- notes와 새 폐기용 library fixture의 Java 소스·검사 파일 각 8개가 구현 커밋 `ef1bc3e`와 바이트 단위로 같음을 확인했다. 설정 JSON과 각 파일의 SHA-256은 별도 manifest에 보존했다. library 설정은 `com.readiz.example.library`, 1.2.3/code 7과 예약된 example.org URL을 사용하며 키나 APK를 생성하지 않았다.
- 첫 library 시도는 `ANDROID_USER_HOME`과 구형 `ANDROID_PREFS_ROOT`의 중복 설정으로 Android 캐시 위치 초기화에서 실패했다. 중복 변수를 제거한 뒤 실제 debug Java 컴파일을 마치고 JUnit worker 실행까지 진행했다. worker 명령에서 `-Xmx512m`, Robolectric offline 모드와 기존 jar 재사용을 확인했다.
- JUnit 실행 중 시스템 가용량이 4 GiB 바로 아래로 내려가 다시 중단했다(로그 표시 4.00 GiB). **JUnit 실패나 소스 오류가 아니라 공간 보호선에 따른 중단**이다. 실패 직전 저장된 최소 정상 샘플은 4.037 GiB였으며, 임계값을 넘은 샘플의 정확한 byte 수는 당시 감시가 예외 전에 저장하지 않아 남아 있지 않다. 관측된 로컬 논리 출력 최대는 329.0 MiB였고 종료 후 실제 할당량은 약 264 MiB였다. 중단 후 작업용 Gradle·worker JVM이 없음을 확인했다.

notes의 기존 debug·release 컴파일과 Android 검사 18개 통과 결과는 보존돼 있다. library는 debug Java 컴파일까지만 확인했고, **library JUnit·release Java 컴파일과 두 설정의 release lint는 아직 완료하지 못했다**. 세 JVM heap을 384 MiB, metaspace를 각각 256 MiB로 줄인 재시도도 준비했으나 시작 여유 6 GiB를 만족하지 않아 실행하지 않았다. 로컬 Android 실행은 여기서 중단하고 아래 원격 CI로 남은 검사를 넘긴다. NAS의 현재 jar 168개(423,530,314 bytes)를 다시 읽어 주소의 SHA-1과 모두 대조했고, 로컬 실행용 캐시·실패 로그·통과 체크포인트를 보존했다.

### 원격 Android CI 준비

[Android workflow](.github/workflows/android.yml)는 Ubuntu 24.04, JDK 17, Gradle 8.13에서 SDK 36과 build-tools 35.0.0의 설치를 확인한다. [검증 스크립트](tests/verify_android.py)는 저장소의 `examples/notes.json`과 `examples/library.json`으로 새 fixture를 만든 뒤 각 앱의 `:app:testDebugUnitTest :app:compileReleaseJavaWithJavac :app:lintRelease`를 단일 job에서 차례로 실행한다. 실패·오류·skip 없는 JUnit 결과와 lint 오류 0개를 확인한다.

수동 실행과 템플릿·생성기·예시 설정·검증 스크립트·workflow 변경에만 실행되며 `contents: read`, 25분 제한을 사용한다. Gradle 캐시 업로드는 끄고, artifact에는 JUnit XML·lint XML/HTML·설정 JSON·소스 커밋/Java SHA-256 JSON만 복사한다. 서명·APK 생성·운영 배포는 실행하지 않으며 NAS 자료를 가져오지 않는다.

로컬에서는 다음 준비 명령만 실행해 서로 다른 두 설정과 Java 파일 16개의 원본 일치, 보고서 업로드 디렉터리의 JSON 범위, 키·APK 미생성을 확인했다. **workflow 추가 자체는 Android 검사 완료를 뜻하지 않으며, 원격 실행 결과는 아직 대기 중이다.**

```bash
python3 -B tests/verify_android.py --out <새 임시 디렉터리> --prepare-only
```

실제 Android 검사는 같은 명령에서 `--prepare-only`를 빼고 실행한다. 기존 출력 디렉터리는 덮어쓰지 않는다.

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
