# Morrow Java API

Java 21과 Spring Boot 기반 REST API입니다. 기본 실행은 H2 메모리 DB를 사용합니다.

```bash
mvn spring-boot:run
mvn test
```

PostgreSQL 사용:

```bash
SPRING_PROFILES_ACTIVE=postgres mvn spring-boot:run
```
