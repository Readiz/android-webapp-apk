# 서명키 생성·보관·복원

기존 앱은 **applicationId와 원래 서명키**를 유지한다. versionCode는 증가시킨다. 비밀번호만 같거나 alias만 같은 새 키는 원래 키를 대체하지 못한다. 아래 생성 명령은 새로운 앱의 첫 릴리스 전용이다. `build.py`는 키를 생성하지 않는다.

## 새 앱 최초 생성

생성한 앱 프로젝트 루트에서 JDK 17과 Android SDK 경로를 지정한다. Android 플랫폼 36, build-tools 35.0.0, Gradle 8.13을 사용한다. 도구는 SDK나 Gradle을 임의로 설치하지 않는다.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export GRADLE_HOME=/Users/readiz/.cache/readiz-tv-android/gradle-8.13
python3 tools/init_signing.py --new-app
```

위 경로는 현재 Mac의 예시다. 다른 환경은 설치 위치로 바꾼다. `GRADLE_HOME`은 Gradle 실행 배포본 경로이며 Gradle 캐시를 가리키는 `GRADLE_USER_HOME`과 다르다.

기본 생성 위치는 `~/.config/android-webapp-apk/<applicationId>/signing/`이다. 다른 위치를 쓸 때는 생성과 빌드 양쪽에 같은 `WEBAPP_SIGNING_DIR`을 지정한다. `app.json`의 `signingAlias`가 keytool alias가 된다.

생성기는 RSA 3072비트, 유효기간 10,000일, PKCS12 키스토어를 만든다. 파일 이름은 도구 규약상 `release.jks`이며 실제 포맷은 PKCS12다. 무작위 비밀번호는 소유자만 읽을 수 있는 `password` 파일에 기록한다. `certificate.pem`은 공개 인증서다. 키 디렉터리는 0700, 키·비밀번호는 0600으로 생성하며 비밀번호를 출력하거나 명령 인수에 직접 넣지 않는다. 대상 디렉터리가 이미 있으면 덮어쓰지 않는다.

## 무엇을 보관할지

| 항목 | 위치/보관 방법 |
|---|---|
| `release.jks`, `password` | 저장소 밖의 앱별 서명 디렉터리. 둘 다 복원에 필요 |
| `signingAlias`, applicationId | `app.json`에 저장. 비밀 아님 |
| `certificate.pem`, 인증서 SHA-256 | 키 디렉터리와 별도 배포 기록. 비밀 아님 |
| 버전 APK, 메타데이터 | Git 밖 릴리스 디렉터리. 원래 키와의 비교 근거 |
| 키의 암호화 백업 | 비밀번호를 별도로 관리하는 암호화 보관소. Git/공개 릴리스에 넣지 않음 |

기존 앱의 키를 이 표준 경로로 옮겨야만 하는 것은 아니다. 원래 키 경로를 `WEBAPP_SIGNING_DIR`으로 지정하고 `signingAlias`를 원래 값으로 설정하면 된다. PKCS12와 JKS 모두 keytool/Gradle이 지원하는 기존 포맷 그대로 사용한다. 이 템플릿은 store password와 key password가 같은 단일 signer 구성을 기본으로 한다. 다른 기존 구성은 비밀번호를 바꾸지 말고 빌드 설정을 맞춘다.

## 키 확인

`WEBAPP_SIGNING_DIR`을 해당 앱의 실제 경로로 설정한 뒤 실행한다. alias는 `app.json` 값으로 바꾼다.

```bash
"$JAVA_HOME/bin/keytool" -list -v \
  -keystore "$WEBAPP_SIGNING_DIR/release.jks" \
  -storepass:file "$WEBAPP_SIGNING_DIR/password" \
  -alias notes-release

"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs /path/to/previous.apk
```

인증서 SHA-256을 비교한다. 빌드 결과 `verification.json`에도 인증서 SHA-256이 기록된다. 비밀번호나 키 바이트를 출력·로그·문서·채팅에 남기지 않는다.

## 암호화 백업과 복원

NAS를 쓸 경우 SMB 실제 마운트, 서버/공유, 쓰기 권한과 여유 공간부터 확인한다. `/Volumes`에 경로가 있다는 것만으로 연결됐다고 판단하지 않는다. 현재 Readiz NAS의 알려진 연결은 `//readiz@192.168.0.5/readiz_private` → `/Volumes/readiz_private`이며 작업 때마다 다시 확인한다. 앱별 승인된 비공개 보관 경로를 사용하고 공개 서빙 경로를 사용하지 않는다.

`age`가 설치되어 있다면 다음은 사람이 백업 암호를 직접 입력하는 예시다. `backup_file`은 확인한 비공개 저장소의 **새 파일 경로**로 먼저 지정한다. 백업 암호는 키 비밀번호와 별도로 관리한다.

```bash
set -o pipefail
umask 077
# backup_file=/verified/private/location/notes-signing-YYYYMMDD.tar.age
# 기존 백업 파일에는 덮어쓰지 않는다.
test ! -e "$backup_file" && \
  tar -C "$WEBAPP_SIGNING_DIR" -cf - release.jks password certificate.pem | \
  age -p -o "$backup_file"
```

실제 자동화에서는 셸의 pipefail을 켜고 백업 파일을 다시 읽어 복호화·내용·인증서를 확인한 뒤 완료로 기록한다. 같은 NAS에 저장한 사본만으로 독립 백업을 확보했다고 간주하지 않는다. 백업 암호 입력이 필요한 복원은 사용자 직접 입력을 이용한다.

복원은 기존 키 디렉터리를 덮어쓰지 않고 새 소유자 전용 임시 디렉터리에서 수행한다. 신뢰하는 본인 백업만 해제하고, `release.jks`·`password`·alias 및 이전 APK와의 인증서 SHA-256을 대조한다. 일치하면 키 디렉터리 권한과 파일 권한을 복구하고 기존 빌드 경로에 연결한다. 확인이 끝나기 전에 새 키를 만들거나 원래 백업을 삭제하지 않는다.

원래 키를 분실했을 때 새 키를 만들어 같은 앱 업데이트라고 배포하지 않는다. 이 보일러플레이트는 키 교체/signing lineage를 구현하지 않는다. 교체는 별도 마이그레이션 작업이다.
