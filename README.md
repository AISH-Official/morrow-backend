# Morrow Backend

Morrow의 Java 21·Spring Boot API입니다. Apple Watch, iPhone, 웹에서 들어오는 건강 요약과 체크인을 사용자별로 저장하고, 타임라인·추천·주간 리포트·AI 개인화·APNs 알림으로 연결합니다.

- 프론트엔드: [AISH-Official/morrow-frontend](https://github.com/AISH-Official/morrow-frontend)
- 제품·API 문서: [AISH-Official/morrow-docs](https://github.com/AISH-Official/morrow-docs)

## 기술 구성

| 영역 | 기술 |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.5.4 |
| API | Spring MVC, Bean Validation |
| Persistence | Spring Data JPA, H2, PostgreSQL |
| AI | OpenAI Responses API, 역할별 모델 라우팅, 안전 필터, 사용자별 컨텍스트 메모리 |
| Notification | APNs token authentication, iOS·watchOS device token 관리 |
| Operations | Spring Boot Actuator, Maven, GitHub Actions |

## 전체 구조

```text
Watch · iPhone · Web
        │
        │ HTTPS + device Bearer token
        ▼
┌─────────────────────────────────────────────┐
│ API / Validation                            │
│ Auth · Health · Check-in · Dashboard · AI   │
│ Report · Personalization · Notification     │
├─────────────────────────────────────────────┤
│ Domain services                             │
│ 사용자 격리 · 회복 부하 계산 · 추천 · 학습 │
│ 안전 필터 · 전체 삭제 · APNs 전송           │
├─────────────────────────────────────────────┤
│ Spring Data JPA repositories                │
├─────────────────────────────────────────────┤
│ H2 file DB (local) / PostgreSQL (deploy)    │
└─────────────────────────────────────────────┘
        │                         │
        ├── OpenAI API            └── Apple APNs
        └── fallback response         iPhone · Watch
```

## 디렉터리 구조

```text
.
├── pom.xml
├── .env.example
├── .github/workflows/ci.yml
└── src
    ├── main
    │   ├── java/app/morrow
    │   │   ├── MorrowApplication.java
    │   │   ├── api/              REST Controller와 요청·응답 DTO
    │   │   ├── auth/             기기 등록·페어링·Bearer 인증
    │   │   ├── health/           HealthKit 파생 요약 저장·중복 방지
    │   │   ├── checkin/          상태 체크인과 후속 처리 오케스트레이션
    │   │   ├── timeline/         사용자 타임라인
    │   │   ├── recommendation/   행동 추천과 효과 피드백
    │   │   ├── personalization/  설명 가능한 사용자별 장기 메모리
    │   │   ├── assistant/        안전 필터·컨텍스트·OpenAI·fallback
    │   │   ├── dashboard/        회복 부하·점수·건강 지표 집계
│   │   ├── report/           최근 7일 패턴과 주간 리포트
│   │   ├── recovery/         회복 행동 실행·완료·효과 학습
│   │   ├── notification/     APNs 기기·게이트웨이·회복 알림
│   │   ├── demo/             심사용 합성 시나리오
    │   │   ├── privacy/          사용자 데이터 전체 삭제
    │   │   └── config/           CORS와 발표용 데모 데이터
    │   └── resources
    │       ├── application.yml
    │       └── application-postgres.yml
    └── test
        ├── java/app/morrow/api/DashboardControllerTest.java
        └── resources/application.yml
```

## 패키지 책임

| 패키지 | 핵심 클래스 | 책임 |
| --- | --- | --- |
| `api` | `*Controller` | `/api/v1` 계약, 입력 검증, HTTP 응답 변환 |
| `auth` | `DeviceAuthService`, `DeviceAuthFilter`, `RequestUserResolver` | 기기 토큰 발급·SHA-256 저장, 6자리 코드 페어링, 사용자 범위 확인 |
| `health` | `HealthSignalSnapshotService` | iPhone·Watch 건강 요약을 `clientSnapshotId` 기준으로 멱등 저장 |
| `checkin` | `CheckInService` | 체크인 저장 후 타임라인·추천·개인화 학습을 한 트랜잭션 흐름으로 연결 |
| `dashboard` | `DashboardService` | 최근 체크인과 최신 건강 요약으로 회복 부하·점수·지표 구성 |
| `recommendation` | `RecommendationService` | 활성 추천, 완료·도움 여부 피드백, 개인화 학습 연결 |
| `personalization` | `PersonalizationService` | 원인 패턴·회복 전략·선호·목표 메모리와 근거 수·신뢰도 관리 |
| `assistant` | `AssistantService`, `SafetyFilter`, `UserContextCollector`, `OpenAIClient` | 안전 우선 AI 응답, 최근 기록·메모리 컨텍스트, 장애 시 개인화 fallback |
| `notification` | `HealthPushListener`, `PushNotificationService`, `HttpApnsGateway` | 건강 부하 이벤트 평가, 6시간 cooldown, iOS·watchOS APNs 전송 |
| `privacy` | `DataPrivacyService` | 체크인부터 건강 데이터·AI 대화·푸시 토큰까지 사용자 데이터 일괄 삭제 |

## 핵심 데이터 흐름

### 1. 기기 등록과 페어링

```text
POST /auth/account
  → 처음과 이후 로그인 모두 accountId를 사용
  → 기기별 access token 발급, 서버에는 SHA-256 hash만 저장

POST /auth/pairing-code → POST /auth/pair
  → 로그인 후 설정 화면에서만 연결 코드 생성·입력
  → iPhone, Watch, Web을 같은 userId에 영구 연결
  → 코드는 짧게 만료되지만 연결은 로그아웃·기기 해제 전까지 유지

POST /auth/logout
  → 현재 기기의 device session 삭제
  → 발급했던 Bearer token 즉시 무효화

Authorization: Bearer <device-token>
  → DeviceAuthFilter 인증
  → RequestUserResolver가 다른 userId 접근 차단
```

로컬 데모에서는 인증이 선택 사항입니다. 배포 환경에서는 `MORROW_AUTH_REQUIRED=true`로 인증을 강제합니다.

### 2. HealthKit 요약과 알림

```text
iPhone / Watch 건강 요약
  → POST /health/snapshots
  → clientSnapshotId 중복 확인
  → HealthSignalSnapshot 저장
  → AFTER_COMMIT HealthSnapshotCreatedEvent
  → 수면·HRV·안정 심박 기반 recovery load 계산
  → 기준 이상이면 APNs 회복 알림
```

서버는 HealthKit 원본 표본이 아니라 앱에서 집계한 수면, 심박, HRV, 걸음, 활동 에너지, 운동 시간 등의 파생 요약을 저장합니다. 최근 수면은 시작·종료와 코어·깊은·REM·깨어있음 시간을, 최근 운동은 종류·구간·시간·에너지·거리·평균/최대 심박과 추정 강도를 함께 보관합니다.

### 3. 체크인과 개인화 추천

```text
Watch / iPhone / Web 체크인
  → CheckIn 저장
  → Timeline 항목 생성
  → 상태·원인 기반 기본 행동 선택
  → 과거 추천 피드백으로 행동 조정
  → OpenAI가 최근 체크인·선호·효과 기록과 동의된 건강 요약으로 한 번 더 맞춤 선택
  → 행동 종류와 실행 시간을 포함한 Recommendation 저장
  → 원인 패턴을 UserMemory에 학습
```

AI 추천은 `BREATH`, `WALK`, `WATER_WALK`, `STRETCH`, `FOCUS`, `SCREEN_BREAK` 중 하나와 30초~30분 사이의 실행 시간을 구조화해 저장합니다. 형식 검증이나 안전 기준을 통과하지 못하거나 OpenAI 연결이 없으면 기존 규칙·개인화 메모리 추천으로 즉시 대체합니다. `clientEventId`가 같은 재전송은 기존 체크인을 반환해 WatchConnectivity 재시도로 인한 중복 학습을 막습니다. 여기서 학습은 범용 모델 파인튜닝이 아니라 사용자 계정별 설명 가능한 메모리 갱신입니다.

### 4. AI 어시스턴트

```text
사용자 메시지
  → 위기·의료 표현 SafetyFilter
  → 최근 7일 체크인·타임라인·추천·피드백
  → 최근 24시간 대화 + 활성 UserMemory
  → OpenAI Responses API 요청
  → 채팅은 gpt-5.6-sol, 추천·짧은 알림은 gpt-5.6-terra
  → 재시도 가능한 429·타임아웃·5xx는 gpt-5.6-luna로 대체
  → 비활성·연결 실패 시 안전한 개인화 fallback
  → 대화 기록 저장
```

위기 표현은 119·112·자살예방상담전화 109 안내를 우선하고, 의료 진단·처방 요청에는 전문가 상담을 안내합니다.

### 5. 회복 행동 효과 학습

```text
건강·체크인 신호 감지
  → 지금 필요한 이유와 신뢰도 포함 알림
  → Watch / iPhone / Web에서 행동 타이머 실행
  → 나아짐·그대로·불편 결과 기록
  → 계정별 행동 선호 학습
  → 다음 알림과 주간 효과 리포트에 반영
```

### 6. 개인정보 전체 삭제

`DELETE /api/v1/users/me/data`는 해당 사용자의 체크인, 타임라인, 추천·피드백, AI 대화, 개인화 메모리, 건강 요약, APNs 기기 토큰을 하나의 서비스에서 삭제합니다.

## 주요 데이터 모델

| 모델 | 내용 |
| --- | --- |
| `DeviceSession` | 기기 ID, 플랫폼, 사용자 ID, 토큰 해시, 페어링 코드 |
| `HealthSignalSnapshot` | 기기에서 집계된 HealthKit 요약과 기록 시각 |
| `CheckIn` | 상태, 원인, 메모, 입력 기기, 클라이언트 이벤트 ID |
| `Timeline` | 사용자에게 노출되는 신호·체크인·회복 기록 |
| `Recommendation` | 행동 제목, 설명 가능한 근거, 활성·완료 상태 |
| `RecommendationFeedback` | 실행 여부, 도움 여부, 사용자 메모 |
| `UserMemory` | 반복 원인, 회복 전략, 선호·목표, 근거와 신뢰도 |
| `RecoveryAttempt` | 제안 행동, 감지 근거·신뢰도, 실행 상태, 사용자 체감 결과 |
| `AssistantMessage` | 사용자·AI 대화와 안전 검사 결과 |
| `PushDevice` | APNs 토큰, 플랫폼, sandbox·production 환경, 활성 상태 |

## API 요약

기준 URL은 `http://localhost:8080/api/v1`입니다.

| Method | Path | 기능 |
| --- | --- | --- |
| `POST` | `/auth/account` | 계정 ID 로그인과 기기 Bearer 세션 발급 |
| `POST` | `/auth/pairing-code` | 로그인한 기기의 설정용 연결 코드 갱신 |
| `POST` | `/auth/device` | 로컬·호환용 기기 세션 등록 |
| `POST` | `/auth/pair` | 다른 기기를 기존 사용자에게 연결 |
| `POST` | `/health/snapshots` | HealthKit 파생 건강 요약 저장 |
| `POST` | `/check-ins` | 체크인·타임라인·추천·학습 생성 |
| `DELETE` | `/check-ins/{id}` | 체크인 삭제 |
| `GET` | `/dashboard` | 회복 부하, 점수, 지표, 타임라인, 추천 조회 |
| `PATCH` | `/timeline/{id}` | 타임라인 내용·사용자 확인 상태 수정 |
| `POST` | `/recommendations/{id}/feedback` | 추천 완료·도움 여부 기록 |
| `GET/POST` | `/recovery-attempts` | 최근 회복 행동 조회·실행 시작 |
| `PATCH` | `/recovery-attempts/{id}/start` | 알림으로 제안된 행동 시작 |
| `PATCH` | `/recovery-attempts/{id}/complete` | 실행 뒤 체감 효과 기록·개인화 학습 |
| `GET` | `/reports/weekly` | 최근 7일 상태·원인 패턴 조회 |
| `POST` | `/demo/scenarios/{scenario}` | 데모 계정 전용 합성 시나리오 적용 |
| `GET` | `/personalization/profile` | 개인화 메모리 통계 조회 |
| `GET/POST` | `/personalization/memories` | 활성 메모리 조회·사용자 선호/목표 생성 |
| `PATCH/DELETE` | `/personalization/memories/{id}` | 메모리 수정·삭제 |
| `POST` | `/personalization/rebuild` | 기록으로 자동 학습 메모리 재구성 |
| `POST/GET` | `/assistant/messages` | AI 대화 생성·이력 조회 |
| `DELETE` | `/assistant/messages` | AI 대화 기억만 삭제 |
| `POST` | `/assistant/proactive-insight` | 최근 건강·체크인 흐름 기반 AI 알림 필요 여부와 문구 생성 |
| `GET` | `/assistant/status` | OpenAI 활성·키·모델 준비 상태 확인 |
| `POST` | `/auth/login` | 해커톤 데모 계정 로그인 후 기기 Bearer 세션 발급 |
| `POST` | `/auth/logout` | 현재 기기 세션 삭제와 Bearer 토큰 폐기 |
| `POST/DELETE` | `/notifications/devices` | APNs 기기 토큰 등록·비활성화 |
| `POST` | `/notifications/test` | 사용자 기기로 테스트 알림 전송 |
| `GET` | `/notifications/status` | APNs 설정과 활성 기기 상태 조회 |
| `DELETE` | `/users/me/data` | 사용자 웰니스 데이터 전체 삭제 |
| `DELETE` | `/users/me/account` | 계정·기기 연결·전체 데이터 완전 삭제 |
| `GET` | `/auth/devices` | 현재 계정에 연결된 기기 조회 |
| `DELETE` | `/auth/devices/{id}` | 특정 기기 연결 해제 |
| `GET/PATCH` | `/privacy/ai-health-consent` | AI 건강 요약 사용 동의 조회·변경 |

상세 요청·응답은 [API 계약](https://github.com/AISH-Official/morrow-docs/blob/main/docs/api.md)을 참고하세요.

## 로컬 실행

```bash
cp .env.example .env
mvn spring-boot:run
```

- API: <http://localhost:8080/api/v1>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- 상태 확인: <http://localhost:8080/actuator/health>
- 기본 DB: `./data/morrow.mv.db` H2 파일
- 데모 사용자: `default-user`

기본값은 영구 H2 DB, 선택적 기기 인증, 생성형 AI·APNs 비활성 상태입니다. `MORROW_DEMO_SEED=true`이면 데이터가 없을 때 발표용 체크인과 타임라인을 생성합니다.

## 환경 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:h2:file:./data/morrow;MODE=PostgreSQL` | JDBC 연결 주소 |
| `DATABASE_USERNAME` | `sa` | DB 사용자 |
| `DATABASE_PASSWORD` | 빈 값 | DB 비밀번호 |
| `JPA_DDL_AUTO` | `update` | Hibernate 스키마 전략 |
| `MORROW_DEMO_SEED` | `true` | 발표용 데이터 자동 생성 |
| `MORROW_AUTH_REQUIRED` | `false` | 기기 Bearer 인증 강제 |
| `SWAGGER_ENABLED` | `true` | Swagger UI와 OpenAPI 문서 활성화 |
| `OPENAI_ENABLED` | `false` | OpenAI 실시간 응답 활성화 |
| `OPENAI_API_KEY` | 빈 값 | OpenAI API 키 |
| `OPENAI_CHAT_MODEL` | `gpt-5.6-sol` | 사용자 채팅 모델 |
| `OPENAI_SHORT_MODEL` | `gpt-5.6-terra` | 추천·Watch 알림 모델 |
| `OPENAI_FALLBACK_MODEL` | `gpt-5.6-luna` | 일시 장애·TPM 제한 시 대체 모델 |
| `MORROW_TIME_ZONE` | `Asia/Seoul` | 날짜·시간 질문과 상대 날짜 계산의 기준 시간대 |
| `MORROW_AI_INCLUDE_HEALTH_DATA` | `false` | 명시적 동의 후 Watch/iPhone 건강 데이터를 AI 컨텍스트에 포함 |
| `MORROW_DEMO_LOGIN_ENABLED` | `false` | 단일 해커톤 데모 로그인 활성화 |
| `MORROW_DEMO_LOGIN_USERNAME` | 빈 값 | 데모 사용자 이름 |
| `MORROW_DEMO_LOGIN_PASSWORD` | 빈 값 | Secret으로 주입할 데모 비밀번호 |
| `MORROW_DEMO_LOGIN_USER_ID` | `hackathon-demo` | 데모 계정이 사용할 내부 사용자 ID |
| `APNS_ENABLED` | `false` | APNs 실제 전송 활성화 |
| `MORROW_CHECK_IN_REMINDERS_ENABLED` | `true` | 오전 8시~오후 9시 매시간 체크인 푸시 활성화 |
| `MORROW_CHECK_IN_CRON` | `0 0 * * * *` | 체크인 푸시 실행 주기(Spring cron) |
| `APNS_TEAM_ID`, `APNS_KEY_ID` | 빈 값 | Apple token authentication 식별자 |
| `APNS_PRIVATE_KEY`, `APNS_PRIVATE_KEY_PATH` | 빈 값 | APNs `.p8` 키 본문 또는 절대 경로 |
| `APNS_IOS_TOPIC`, `APNS_WATCH_TOPIC` | 앱 bundle ID | iPhone·Watch APNs topic |

실제 키와 비밀번호는 커밋하지 않고 `.env` 또는 배포 환경의 secret으로 주입합니다.

## PostgreSQL 실행

기본 설정에 PostgreSQL JDBC URL을 직접 주입할 수 있습니다.

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/morrow \
DATABASE_USERNAME=morrow \
DATABASE_PASSWORD=morrow \
mvn spring-boot:run
```

`postgres` 프로필을 사용할 때는 `application-postgres.yml`의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 설정합니다.

## EC2 자동 배포

`main` 브랜치에 변경이 반영되면 `.github/workflows/ci.yml`이 다음 순서로 운영 서버를 배포합니다.

1. Java 21 환경에서 Maven 테스트 실행
2. 실행 가능한 Spring Boot jar 빌드
3. GitHub Actions 전용 SSH 키로 EC2에 릴리스 업로드
4. `current.jar` 심볼릭 링크를 새 릴리스로 교체하고 `morrow-api` 재시작
5. `/actuator/health`가 `UP`인지 확인하고, 실패하면 직전 릴리스로 롤백

저장소 Actions secrets에는 `EC2_HOST`, `EC2_SSH_KEY_B64`, `EC2_KNOWN_HOSTS`, `OPENAI_API_KEY`, `OPENAI_CHAT_MODEL`, `OPENAI_SHORT_MODEL`, `OPENAI_FALLBACK_MODEL`, `OPENAI_ENABLED`가 필요합니다. OpenAI 설정은 배포할 때 EC2의 `/etc/morrow-openai.env`로 동기화되고, 데이터베이스 비밀번호는 EC2의 `/etc/morrow.env`에만 보관하며 GitHub에는 복사하지 않습니다.

## 테스트와 CI

```bash
mvn --batch-mode test
```

통합 테스트는 H2 메모리 DB와 비활성 OpenAI 설정으로 다음 흐름을 검증합니다.

- 대시보드 기본 계약과 안전 고지
- 건강 요약·체크인 재전송의 멱등성
- 체크인 → 타임라인 → 추천 흐름
- 사용자 데이터 격리와 전체 삭제
- 위기 표현 안전 응답
- 추천 피드백 → 개인화 메모리 → 다음 행동 변경
- 회복 실행 결과 → 행동별 개인화 학습과 주간 효과 리포트
- 데모 계정의 감지 → 실행 → 효과 학습 시나리오
- 기기 페어링과 Bearer 사용자 범위
- iPhone·Watch APNs 토큰 등록

Pull Request와 `main` 푸시에서는 GitHub Actions가 동일한 Maven 테스트를 자동 실행합니다.

## 안전 원칙

이 서비스는 의료 진단이나 치료를 제공하지 않습니다. 생체 신호만으로 사용자의 상태를 단정하지 않고 직접 체크인과 사용자 피드백을 함께 사용하며, 사용자는 개인화 메모리와 전체 웰니스 데이터를 확인·수정·삭제할 수 있습니다.
