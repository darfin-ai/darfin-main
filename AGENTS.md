# darfin-main

Spring Boot 2.7 central backend — auth, persistence, REST API, WebSocket. Proxies LLM work to Python microservices.

## Commands

```bash
./gradlew bootRun    # Dev server :8080
./gradlew build      # Compile + package
./gradlew test       # JUnit tests
./gradlew clean      # Clean build output
```

Config: `src/main/resources/application.properties` (MariaDB, port 8080, JPA `ddl-auto=update`).

## Structure

```
src/main/java/com/kosta/darfin/
  DarfinMainApplication.java
  controller/     # REST endpoints by domain (auth, analysis, disclosure, fund, community)
  service/        # Business logic
  repository/     # JPA repositories
  entity/         # JPA entities
  dto/            # Request/response DTOs
  global/         # config, exception handling, JWT
  security/       # Spring Security
  websocket/      # Real-time (paper trading)
ddl.sql           # DB schema (source of truth)
```

## API patterns

- `@RestController` + `@RequestMapping`, Lombok `@RequiredArgsConstructor`
- Return `ResponseEntity<T>`
- Route prefixes: `/api/v1/*`, `/api/*`, `/funds/*`
- Global errors: `global/exception/GlobalExceptionHandler.java`
- Frontend client: `../darfin-front/src/app/shared/api/apiClient.js`

## Read before changing

| Area | Location |
|------|----------|
| DB schema | [ddl.sql](ddl.sql) |
| Frontend data contract | [../darfin-front/src/mocks/companyAnalysis/types.js](../darfin-front/src/mocks/companyAnalysis/types.js) |
| Pipeline design | [../darfin-company-analysis/IMPLEMENTATION_PLAN.md](../darfin-company-analysis/IMPLEMENTATION_PLAN.md) |
| Architecture | [../docs/architecture.md](../docs/architecture.md) |

## External services

| Service | Port | Purpose |
|---------|------|---------|
| darfin-investment | 8001 | Portfolio AI analysis |
| darfin-disclosure | 8002 | 수시공시 LLM summarize/analyze |
| MariaDB | 3306 | Primary datastore |

## Conventions

- Base package: `com.kosta.darfin`
- Java 11, Spring Boot 2.7.18
- Schema changes: update `ddl.sql` first, then entities/repos
- API response shapes must match frontend `types.js` when serving `/company`
- Disclosure responses use camelCase to match Python `schemas.py`

## Do not

- Put business logic in controllers — use service layer
- Change `ddl.sql` without updating JPA entities
- Mix 정기공시 and 수시공시 table logic — separate domains
- Commit `application.properties` with real credentials

## Related repos

- `../darfin-front` — React SPA consumer
- `../darfin-company-analysis` — writes company analysis data to shared MySQL
- `../darfin-disclosure` — stateless LLM microservice for 수시공시
- `../darfin-investment` — stateless LLM microservice for portfolio reports
