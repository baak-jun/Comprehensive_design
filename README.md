# Counseling

온디바이스 상담 보조 Android 앱입니다. Jetpack Compose UI와 LiteRT-LM 기반 로컬 모델 실행을 사용하며, 사용자가 선택한 `.litertlm` 모델 파일로 텍스트/음성/이미지 첨부 기반 상담 흐름을 테스트할 수 있습니다.

## 주요 기능

- LiteRT-LM `.litertlm` 모델 파일 선택 및 로컬 추론
- 한국어 상담 보조 응답 스트리밍
- 텍스트 입력, Android 음성 인식 입력, 앱 내부 WAV 녹음 첨부
- 이미지/오디오 첨부를 포함한 대화 메시지 관리
- 대화 세션 자동 저장, 세션 전환, JSON 가져오기/내보내기
- 중요 기억 저장 및 SQLite FTS 기반 관련 기억 검색
- Health Connect 데이터 요약 및 상담 프롬프트 포함 옵션
- 통화 기록과 앱 사용 시간 기반 생활 패턴 요약 및 상담 프롬프트 포함 옵션
- 갤러리 이미지 조회 화면
- GitHub Actions debug APK 빌드

## 프로젝트 구조

```text
.
├── .github/workflows/android-debug.yml
└── Frontend
    ├── app
    │   └── src/main/java/com/example/counseling
    ├── docs
    └── gradle
```

Android 앱 본체는 `Frontend` 폴더에 있습니다.

## 빌드

로컬 Windows 환경에서는 Android Studio 내장 JBR을 사용해 빌드합니다.

```powershell
cd Frontend
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

빌드 산출물 예시:

```text
Frontend/app/build/outputs/apk/debug/Counseling_05_30_v1.0.0_debug.apk
```

GitHub에서는 `Android Debug Build` 워크플로가 `main` 브랜치 push 또는 수동 실행 시 debug APK를 빌드하고 artifact로 업로드합니다.

## 모델 파일

모델 파일은 저장소에 포함하지 않습니다. 앱 실행 후 설정에서 `.litertlm` 모델 파일을 직접 선택해 로드합니다.

권장 위치는 앱 외부 파일 영역의 `models` 디렉터리이며, 앱은 사용자가 선택한 모델 파일을 앱 전용 저장소로 복사해 사용합니다.

## 권한

앱이 선언하는 권한과 사용 목적은 아래와 같습니다.

| 권한 | 사용 목적 |
| --- | --- |
| `READ_EXTERNAL_STORAGE` | Android 12 이하에서 갤러리 이미지를 읽기 위해 사용합니다. `maxSdkVersion=32`로 제한되어 있습니다. |
| `READ_MEDIA_IMAGES` | Android 13 이상에서 갤러리 이미지를 조회하기 위해 사용합니다. |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Android 14 이상에서 사용자가 선택한 일부 사진만 접근하는 부분 갤러리 권한에 사용합니다. |
| `RECORD_AUDIO` | 앱 내부에서 마이크 녹음을 시작하고 16kHz mono WAV 첨부 파일을 만들기 위해 사용합니다. Android 음성 인식 입력도 음성 입력 흐름에서 사용됩니다. |
| `READ_CALL_LOG` | 패턴 화면에서 최근 통화 수, 부재중 비율, 평균 통화 시간, 연락 단절 기간 같은 통화 패턴 요약을 계산하기 위해 사용합니다. |
| `PACKAGE_USAGE_STATS` | 패턴 화면에서 최근 앱별 사용 시간, 일평균 스크린타임, 야간 사용량, 최장 연속 세션 등을 계산하기 위해 사용합니다. 일반 런타임 권한이 아니므로 사용자가 Android 설정의 사용 정보 접근 화면에서 직접 허용해야 합니다. |
| `android.permission.health.READ_STEPS` | Health Connect에서 걸음 수를 읽어 건강 요약과 상담 프롬프트 옵션에 사용합니다. |
| `android.permission.health.READ_DISTANCE` | Health Connect에서 이동 거리를 읽어 건강 요약과 상담 프롬프트 옵션에 사용합니다. |
| `android.permission.health.READ_TOTAL_CALORIES_BURNED` | Health Connect에서 총 소모 칼로리를 읽어 건강 요약에 사용합니다. |
| `android.permission.health.READ_ACTIVE_CALORIES_BURNED` | Health Connect에서 활동 칼로리를 읽어 건강 요약에 사용합니다. |
| `android.permission.health.READ_HEART_RATE` | Health Connect에서 심박수 기록을 읽어 평균 심박수 요약에 사용합니다. |
| `android.permission.health.READ_SLEEP` | Health Connect에서 수면 세션을 읽어 수면 시간 요약에 사용합니다. |

Health Connect 권한은 사용자가 앱의 건강 화면에서 권한 연결을 눌러 허용해야 합니다. 패턴과 건강 데이터는 기본 상담에 항상 포함되지 않으며, 상담 설정에서 `패턴 포함` 또는 `건강 포함`을 켰을 때 프롬프트에 요약 형태로 들어갑니다.

## 민감 정보와 데이터 처리

- `.litertlm` 모델 파일은 Git 저장소에 포함하지 않습니다.
- 상담 세션, 중요 기억, 선택한 모델 파일은 기본적으로 기기 로컬 저장소에서 사용합니다.
- Health Connect, 통화 기록, 앱 사용 시간 데이터는 화면 표시와 선택적 프롬프트 요약에 사용됩니다.
- 이 앱은 상담 보조 도구이며 진단, 치료, 응급 대응을 대체하지 않습니다.

## 현재 버전

- 앱 버전: `1.0.0`
- 패키지 ID: `com.example.counseling`
- 최소 SDK: 28
- 대상 SDK: 36

