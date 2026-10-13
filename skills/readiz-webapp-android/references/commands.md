# 생성·빌드·배포 명령

이 스킬 디렉터리의 `scripts/create_app.py`와 `assets/starter/`가 단일 원본이다. 생성된 앱은 독립된 `android/`, `server/`, `tools/`, `app.json`을 가진다. 템플릿 변경이 기존 앱에 자동 반영되지는 않으며, 기존 앱 갱신은 해당 저장소에서 선택적으로 반영한다.

```bash
python3 scripts/create_app.py --config /absolute/app.json --out /absolute/new-project
# 선택: --icon /absolute/square.png (48~1024px, 최대 1 MiB)
```

`app.json` 필드는 저장소의 `examples/notes.json`을 참고한다. `internalUrls`는 HTTPS origin+경로 접두사이며 경로 경계를 구분한다. `/app`은 `/application`을 허용하지 않는다. 웹 로그인은 실제로 필요한 origin/경로를 확인해서 추가한다. 인증 팝업, 파일 선택, 브리지, 미디어 백그라운드 재생은 기본 구현에 없다. 앱에 필요한 기능만 확장한다.

`updateBaseUrl`은 `/`로 끝나고 `app-version`, `app.apk`가 이어진다. 업데이트 HTTP 요청은 WebView 로그인 쿠키를 공유하지 않으므로 이 두 경로에는 해당 배포에 맞는 접근 정책이 필요하다. 예제 서버는 메타데이터와 APK만 제공하며 기본 바인딩은 localhost다.

생성 앱 루트에서:

```bash
# JAVA_HOME, ANDROID_HOME, GRADLE_HOME 지정은 signing.md 참고
python3 tools/init_signing.py --new-app  # 새 앱 최초 한 번만
python3 tools/build.py --output /outside/git/build-0.1.0
python3 tools/publish.py --apk /outside/git/build-0.1.0/app-0.1.0.apk --releases /outside/git/releases
node server/server.mjs --releases /outside/git/releases --port 8080
python3 tools/verify_deployment.py --manifest /outside/git/build-0.1.0/app-0.1.0.apk.json
```

`build.py`는 테스트·lint·release build와 APK 패키지/버전/서명 검증을 수행한다. 출력 디렉터리는 새 경로여야 한다. `publish.py`는 같은 인증서·패키지·증가 버전, 동일 versionName 파일 불변을 확인하고 최신 메타데이터를 마지막에 전환한다. 최초 게시도 실제 서명 검증을 한다. 게시 도중 중단되어 `.publish-lock`이 남으면 실행 중인 게시 프로세스와 파일 상태를 확인한 뒤 본 작업의 잠금만 정리한다.

운영에서는 기존 HTTPS 리버스 프록시로 `updateBaseUrl`의 두 경로를 연결한다. 프로젝트 폴더나 키 디렉터리를 정적 루트로 노출하지 않는다. 독립 서버 대신 `server/update-handler.mjs`의 `createUpdateHandler`를 기존 Node 서버에 연결할 수도 있다. HTTPS 설정과 서비스 재시작은 대상 프로젝트의 운영 지침을 따른다.

단위 테스트·빌드·공개 다운로드 확인은 실제 Android 설치 증거가 아니다. `verify_deployment.py`는 인증 없이 메타데이터·최신 APK·버전 고정 APK를 대조하며 실계정 로그인이나 기기 설치를 하지 않는다.
