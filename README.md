# Morrow Backend

Morrow의 Java 21·Spring Boot API 저장소입니다. 기기 인증, HealthKit 집계 동기화, 체크인·타임라인·추천, 설명 가능한 사용자별 개인화 메모리, AI 대화와 APNs 알림을 제공합니다.

프론트엔드 클라이언트는 [AISH-Official/morrow-frontend](https://github.com/AISH-Official/morrow-frontend), 통합 문서와 전체 아키텍처는 [AISH-Official/morrow-docs](https://github.com/AISH-Official/morrow-docs)에서 관리합니다.

## 빠른 실행

```bash
cp .env.example .env
mvn spring-boot:run
```

- API: http://localhost:8080/api/v1
- 상태 확인: http://localhost:8080/actuator/health

기본값은 로컬 H2 DB와 안전한 데모 모드입니다. 생성형 AI를 사용하려면 `.env`에 `OPENAI_API_KEY`와 `OPENAI_ENABLED=true`를 설정합니다.

## 주요 환경 변수

- `OPENAI_ENABLED`, `OPENAI_API_KEY`, `OPENAI_MODEL`
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `MORROW_DEMO_SEED`
- `APNS_ENABLED`, `APNS_TEAM_ID`, `APNS_KEY_ID`, `APNS_PRIVATE_KEY_PATH`

## 테스트

```bash
mvn --batch-mode test
```

Pull Request와 `main` 푸시에서 Maven 테스트가 자동 실행됩니다.

## PostgreSQL 실행

```bash
SPRING_PROFILES_ACTIVE=postgres mvn spring-boot:run
```

이 서비스는 의료 진단을 제공하지 않으며, 사용자 데이터는 계정별로 격리됩니다.
