# PsychoCare - 사진 기반 심리상담 앱

## 📱 앱 개요
갤러리의 촬영 사진을 분석하여 사용자의 감정 상태·취미·생활 패턴을 파악하고,
Gemma on-device LLM으로 심리상담 서비스를 제공하는 Android 앱입니다.

---

## 🏗️ 전체 흐름

```
[DCIM/Camera 폴더]
    │
    ▼ [파일명 알고리즘] FilenameParser.kt
    │  ├─ 다운로드/공유 파일 제외 (카카오톡, 스크린샷 등)
    │  ├─ 촬영 사진 패턴 매칭 (삼성/아이폰/일반 Android)
    │  └─ 파일명에서 날짜/시간 파싱
    │
    ▼ [시간대 알고리즘] TimezoneAlgorithm.kt
    │  ├─ 1순위: EXIF OffsetTime 태그 (신뢰도: HIGH)
    │  ├─ 2순위: GPS 좌표 → 시간대 추정 (신뢰도: MEDIUM)
    │  └─ 3순위: 기기 시간대 사용 (신뢰도: LOW)
    │
    ▼ [ML Kit 분석] PhotoAnalyzer.kt
    │  ├─ Face Detection → 얼굴 표정 → 감정 분류
    │  │   (HAPPY / SAD / ANGRY / SURPRISED / NEUTRAL)
    │  ├─ Image Labeling → 장소·활동·사물 태그
    │  └─ 취미 매핑 테이블 (60+ 카테고리)
    │
    ▼ [프로필 생성] UserProfileBuilder.kt
    │  ├─ 감정 분포 계산
    │  ├─ 감정 점수 (-1.0 ~ +1.0)
    │  ├─ 감정 변동성 (표준편차)
    │  ├─ 취미/관심사 Top 5
    │  ├─ 활동 시간대 패턴
    │  └─ 사회성 수준 (혼자/소규모/대규모)
    │
    ▼ [Gemma 챗봇] GemmaChatbot.kt
       ├─ 프로필 컨텍스트 시스템 프롬프트 주입
       ├─ 스트리밍 응답 (토큰 단위)
       ├─ 대화 기록 관리 (최대 10턴)
       └─ 위기 신호 감지 → 전문가 연계 안내
```

---

## 📂 프로젝트 구조

```
app/src/main/java/com/psychocare/
├── algorithm/
│   ├── FilenameParser.kt      # 파일명 알고리즘
│   └── TimezoneAlgorithm.kt   # 시간대 알고리즘
├── analyzer/
│   ├── PhotoAnalyzer.kt       # ML Kit 감정/라벨 분석
│   └── UserProfileBuilder.kt  # 사용자 프로필 생성
├── chatbot/
│   └── GemmaChatbot.kt        # Gemma LLM 챗봇
├── data/
│   └── Models.kt              # 데이터 클래스
├── repository/
│   └── PhotoRepository.kt     # 사진 로드 (파일명+시간대 알고리즘 적용)
├── viewmodel/
│   └── MainViewModel.kt       # 앱 상태 관리
├── ui/screens/
│   ├── HomeScreen.kt          # 사진 분석 화면
│   ├── ChatScreen.kt          # 심리상담 채팅 화면
│   └── ProfileScreen.kt       # 사용자 프로필 화면
└── MainActivity.kt
```

---

## ⚙️ 설치 방법

### 1. Gemma 모델 다운로드
```
https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-gpu-int4
```
다운로드 후 → `app/src/main/assets/models/gemma-2b-it-gpu-int4.bin` 에 배치

### 2. 권한 설정
앱 실행 시 자동으로 권한 요청:
- `READ_MEDIA_IMAGES` - 사진 읽기
- `ACCESS_MEDIA_LOCATION` - GPS EXIF 접근 (시간대 알고리즘용)

### 3. 특정 폴더 지정
기본: `/sdcard/DCIM/Camera` (자동 탐색)
변경: 홈 화면 우상단 폴더 아이콘 → 경로 입력

---

## 🔍 핵심 알고리즘 상세

### 파일명 알고리즘 (FilenameParser)
| 제조사 | 패턴 | 예시 |
|--------|------|------|
| 삼성 | `YYYYMMDD_HHMMSS.jpg` | `20240315_143022.jpg` |
| Android 기본 | `IMG_YYYYMMDD_HHMMSS.jpg` | `IMG_20240315_143022.jpg` |
| 동영상 | `VID_YYYYMMDD_HHMMSS.mp4` | `VID_20240315_143022.mp4` |
| 아이폰 | `IMG_NNNN.HEIC` | `IMG_1234.HEIC` |

**제외 패턴**: Screenshot, KakaoTalk, Telegram, LINE, Instagram 등

### 시간대 알고리즘 (TimezoneAlgorithm)
```
EXIF OffsetTime (+09:00) → HIGH 신뢰도
    ↓ 없으면
GPS (위도/경도) → 시간대 DB 조회 → MEDIUM 신뢰도
    ↓ 없으면
기기 시간대 (Asia/Seoul) → LOW 신뢰도
```

---

## 📋 감정 분류

| ML Kit 출력 | 감정 | 판단 기준 |
|------------|------|----------|
| smilingProbability > 0.8 | HAPPY 😊 | 웃음 확률 80%+ |
| smilingProbability > 0.5 | HAPPY 😊 | 웃음 50%+ + 눈 뜸 |
| eyeOpenAvg < 0.3 | SAD 😢 | 눈 감음 |
| eyeOpenAvg > 0.9 | SURPRISED 😲 | 눈 크게 뜸 + 무표정 |
| 기타 | NEUTRAL 😐 | 기본값 |

---

## ⚠️ 주의사항
- Gemma 2B 모델 파일 크기: ~1.5GB (기기 저장공간 필요)
- 최소 4GB RAM 권장 (on-device LLM 구동)
- ACCESS_MEDIA_LOCATION 권한: GPS EXIF 읽기에 필수
- 심리상담 AI는 전문 의료 서비스를 대체하지 않습니다
