# 작업 지침

한국어로 답한다. 보일러플레이트 원본은 `skills/readiz-webapp-android/assets/starter/` 하나로 유지한다. 앱별 값을 Java 코드에 하드코딩하지 않는다. 스킬 절차 변경 때 관련 references와 실제 명령을 함께 검증한다.

서명키·비밀번호·APK·빌드 결과는 Git 밖에 둔다. 기존 키를 새로 만들거나 다른 앱의 키를 재사용하지 않는다. 키 생성은 새 앱 최초 생성 명령으로 명시적으로 수행하며 빌드·게시는 분리한다. 시스템 설치 확인을 기본으로 유지한다.

생성기/서버 변경은 `python3 -m unittest discover -s tests -v`, `node --test tests/update-server.test.mjs`를 실행한다. Android 템플릿이나 설정 변경은 서로 다른 설정으로 생성한 앱의 실제 컴파일 및 관련 Android 테스트를 수행한다. 빌드·공개 배포·실기기 확인을 구별해서 기록한다.
