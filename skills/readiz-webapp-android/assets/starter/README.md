# Android 웹앱

앱별 설정은 `app.json`이다. `android/`는 HTTPS WebView와 APK 업데이터, `server/`는 메타데이터·APK HTTP 서버, `tools/`는 서명·빌드·게시 도구다. 생성 후 변경한 코드는 이 프로젝트에서 관리한다.

필수 도구: Python 3.9+, Node 20+, JDK 17, Android SDK platform 36/build-tools 35.0.0, Gradle 8.13. 환경 변수 `JAVA_HOME`, `ANDROID_HOME`, `GRADLE_HOME`을 실제 설치 위치로 지정한다.

```bash
python3 tools/init_signing.py --new-app
python3 tools/build.py --output /outside/project/release-0.1.0
python3 tools/publish.py --apk /outside/project/release-0.1.0/app-0.1.0.apk --releases /outside/project/releases
node server/server.mjs --releases /outside/project/releases --port 8080
python3 tools/verify_deployment.py --manifest /outside/project/release-0.1.0/app-0.1.0.apk.json
```

새 키 기본 경로는 `~/.config/android-webapp-apk/<applicationId>/signing/`이다. 기존 앱에서는 원래 `release.jks`와 `password`, `signingAlias`를 유지한다. 외부 경로는 `WEBAPP_SIGNING_DIR`으로 지정한다. 빌드는 키를 자동 생성하지 않는다. 상세 생성·백업·복원 방법은 `docs/signing.md`에 있다.

앱 실행·복귀 시 업데이트를 확인한다(15분 중복 확인 제한). 자동 다운로드 후 크기·SHA-256·패키지·버전·동일 서명을 검증하며 설치는 Android 확인 화면을 거친다. 취소 후 24시간 유예하며 앱 종료 중 백그라운드 확인 작업은 없다. 웹 페이지 배포는 웹 서버에서 별도로 처리한다.

서버 기본 바인딩은 localhost이며 HTTPS 프록시는 서비스별로 구성한다. 공개 응답은 앱 버전 정보와 APK뿐이다. 예시 URL을 실제 서비스 주소로 설정해야 한다. WebView와 TWA의 인증 저장소는 다르므로 기존 앱을 무조건 이 템플릿으로 교체하지 않는다.
