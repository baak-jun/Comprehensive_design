# PhenoType — 온디바이스 심리 상담 앱 (디지털 피노타이핑)

Android 기기의 **사진 · 통화기록 · 앱 사용 통계**를 온디바이스에서 분석해
사용자의 심리·생활 상태를 지표화하고, 상담 LLM의 컨텍스트로 활용하는 졸업작품 프로젝트.

> 모든 분석은 기기 내부에서 수행되며, 원시 데이터는 외부로 전송되지 않습니다.

---

## 저장소 구성

```
PhenoType/
├── PsychoCareApp/      Android 앱 (Kotlin + Jetpack Compose)
├── dashboard/          개발자용 웹 대시보드 (프롬프트·분석 실시간 확인)
└── phenotype-module/   통화기록·앱사용 분석기 (다른 프로젝트 통합용 단독 모듈)
```

---

## 1. PsychoCareApp (Android 앱)

### 기술 스택
- Kotlin, Jetpack Compose (Material 3)
- ML Kit (얼굴 감정 · 이미지 라벨링)
- UsageStatsManager / CallLog (피노타입)
- MediaPipe LLM (Gemma 온디바이스) — *현재 검증을 위해 비활성화 상태*

### 화면 (하단 탭)
| 탭 | 기능 |
|---|---|
| **홈** | 사진 분석 (감정·취미), 권한 요청, 분석 결과 그리드 |
| **패턴** | 통화기록·앱사용·**수면/생활 리듬** 지표 (정상/주의/위험 등급) |
| **프로필** | 감정 분포·취미·생활 패턴 종합 |

### 주요 분석
- **갤러리**: ML Kit으로 얼굴 감정·활동 라벨 분석 → 감정 분포/취미/사회성
- **통화기록**: 주간 통화량·고유 연락처·부재중·연속 무연락 → 대인 교류/고립 신호
- **앱 사용**: 일평균 스크린타임·야간 사용·최장 세션
- **수면/생활 리듬 (최근 30일)**:
  - 야간(00~06시) 폰 사용일 수 — "한 달 중 며칠"
  - 수면 부족 추정일 (심야 01~05시 활동)
  - 평균 취침 추정 시각, 생활 리듬 점수(0~100), 리듬 붕괴 여부
- **영속화**: 분석 결과를 내부 저장소에 저장 → 앱 재실행 시 자동 복원

### 빌드 / 실행
```bash
# Android Studio로 PsychoCareApp 열기 → Run
# 또는 CLI:
cd PsychoCareApp
./gradlew installDebug
```
> `local.properties`에 `sdk.dir` 설정 필요 (Git 제외됨).

### Gemma 모델 (선택)
- 상담 LLM을 쓰려면 CPU 모델(`gemma-2b-it-cpu-int4.bin`)을 기기에 복사:
  ```bash
  adb push gemma-2b-it-cpu-int4.bin \
    /sdcard/Android/data/com.psychocare/files/models/
  ```
- 모델 파일은 **Git에 포함하지 않음** (`.gitignore` 처리, 1.3GB).
- 현재 코드에서는 검증 편의를 위해 Gemma 초기화가 주석 처리되어 있음
  (`MainViewModel.init` / `MainActivity` 주석 해제 시 복원).

---

## 2. dashboard (개발자 웹 대시보드)

핸드폰 앱이 LLM에 보내는 **프롬프트 전문**과 분석 지표를 컴퓨터에서 실시간 확인.

```powershell
# Windows PowerShell (별도 설치 불필요 — 내장 HttpListener 사용)
powershell -ExecutionPolicy Bypass -File dashboard/run-server.ps1
# → http://localhost:8770
```
- ADB로 기기의 `dev_session.json`을 읽어 3패널(프로필·채팅·분석)로 표시.
- `dashboard/run-server.ps1` 안의 `$ADB` 경로를 본인 환경에 맞게 수정.

---

## 3. phenotype-module (단독 통합 모듈)

통화기록·앱사용 분석기를 **다른 Android 프로젝트에 바로 붙일 수 있는** 형태로 분리.
- `CallLogAnalyzer.kt`, `AppUsageAnalyzer.kt`, `PhenotypeModels.kt`
- 통합 방법은 `phenotype-module/README_사용법.md` 참고.

---

## 권한
| 데이터 | 권한 | 요청 방식 |
|---|---|---|
| 사진 | `READ_MEDIA_IMAGES` | 런타임 |
| 통화기록 | `READ_CALL_LOG` | 런타임 |
| 앱 사용통계 | `PACKAGE_USAGE_STATS` | 설정 > 사용 정보 접근 |

> iOS는 통화기록·타 앱 사용시간 접근 불가 → Android 전용.
