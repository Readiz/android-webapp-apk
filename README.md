# Android WebApp APK

Readiz 웹앱을 **자체 APK 업데이트가 가능한 Android 앱**으로 만드는 보일러플레이트와 Codex 스킬이다. 앱 설정 하나로 독립 프로젝트를 생성한다. 검증된 AOA WebView 구현을 출발점으로 패키지·URL·서명·배포 경로를 분리했다.

## 빠른 시작

```bash
python3 skills/readiz-webapp-android/scripts/create_app.py \
  --config examples/notes.json --out /path/to/new-app
```

`examples/notes.json`을 복사해 앱 이름·applicationId·시작 URL·내부 탐색 URL·업데이트 URL·버전·서명 alias를 지정한다. 기본 아이콘은 범용 벡터이며 `--icon square.png`로 바꿀 수 있다. 대상 폴더가 있으면 덮어쓰지 않는다.

생성된 프로젝트 안에서 최초 키 생성 → 테스트·빌드 → 별도 게시 → 운영 다운로드 대조를 수행한다. [명령 안내](skills/readiz-webapp-android/references/commands.md)와 [서명키 생성·보관·복원](skills/readiz-webapp-android/references/signing.md)을 참고한다. 기존 앱의 키와 applicationId를 바꾸면 기존 앱 업데이트로 설치되지 않으므로 원래 값을 유지한다.

## 포함한 기능

- HTTPS WebView, origin/경로별 내부 탐색, 외부 링크 분리, 뒤로 가기, 연결 실패 재시도
- 웹 저장소 유지, 파일 접근·혼합 콘텐츠 제한
- 실행·복귀 시 버전 확인, 자동 다운로드, SHA-256·크기·패키지·버전·서명 검증
- 최초 설치 허용 설정 연결, Android 설치 확인, 취소 유예, 오프라인 처리
- 메타데이터와 버전 고정 APK만 제공하는 무의존성 Node 서버
- 명시적 키 생성, 서명 APK 빌드, 버전 불변·동일 서명 게시와 운영 대조 도구

자동 확인은 15분 중복 제한, 설치 취소는 24시간 유예다. 앱이 종료된 동안의 백그라운드 확인이나 무인 설치는 포함하지 않는다. TV 재생·리모컨, Music MediaSession, Invest 위젯·TWA는 앱별 확장으로 남긴다. 기존 앱 세 개를 일괄 이관하지 않는다.

## 구조와 단일 원본

`skills/readiz-webapp-android/assets/starter/`가 배포 가능한 보일러플레이트 원본이다. 같은 스킬의 `scripts/create_app.py`가 프로젝트를 생성하고 `references/`에 운영 절차가 있다. 생성된 프로젝트는 이 저장소 없이도 빌드할 수 있다. 이후 템플릿 변경을 기존 앱에 자동 덮어쓰지는 않는다.

`python3 tools/install-skill.py`로 [스킬](skills/readiz-webapp-android/SKILL.md)을 로컬 Codex에 연결한다. 기존에 다른 스킬이 있으면 덮어쓰지 않는다. 이 저장소를 옮기면 링크도 다시 연결해야 한다. 다른 환경에서는 스킬 폴더를 복사해 설치할 수 있다. 복사할 때 `assets`, `scripts`, `references`도 함께 가져간다.

## 검증

```bash
python3 -m unittest discover -s tests -v
node --test tests/update-server.test.mjs
```

생성된 앱의 `tools/build.py`는 Android 테스트·release lint·서명 build를 수행한다. [검증 기록](TESTING.md)에 실제 확인 범위와 제한을 기록한다. 실기기 설치와 실계정 로그인은 자동으로 수행하지 않는다.

출발 코드: Readiz/aoa-research `b63552d`, Readiz/invest `eee300e`. 실제 서비스 URL·서명키·APK·개인 데이터는 가져오지 않았다. SDK/Gradle은 기존 환경을 사용하며 자동 설치하지 않는다.
