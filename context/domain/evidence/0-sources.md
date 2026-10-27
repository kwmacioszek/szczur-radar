# Sources: code

- repo_root: /Users/krzysztofmacioszek/Develop/Projects/10xDevs/szczur-radar-worktrees/m2-close-m3-open
- code_scope: `szczur-radar-api/`, `szczur-radar-web/`, `tools/pipeline-trigger/`, `tools/db-backup/` (produkt: usługa API z zadaniami w tle, SPA i dwa zadania cykliczne)
- head: 3f744c6 (dirty: `context/changes/post-m2-audit/`, `context/domain/` — nowe, nieśledzone pliki tego uruchomienia; kod czysty)
- path_alias: `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`; `web/` = `szczur-radar-web/src/`

## Stack

- Java 25, Spring Boot 4 (web, Spring Security, Spring Session JDBC, Spring Data JPA / Hibernate), Flyway, Spring AI 2.0.1 (klienci modeli językowych), PostgreSQL 18; Maven (`./mvnw`).
- Vue 3 + TypeScript + Vite, `vue-router`; bez biblioteki stanu; Vitest + `@vue/test-utils`.
- Bash + `curl` + `jq` (zadania cykliczne w `tools/`), `pg_dump`/`pg_restore`, `rclone`.
- Testy: JUnit + AssertJ + Mockito (`*Test`), `@SpringBootTest` + Testcontainers (`*IT`).

## Layers

| layer | path patterns | notes |
|---|---|---|
| entry points | `api/*/api/*Controller.java`, `api/auth/*Controller.java`, `api/auth/activation/*Controller.java`, `api/health/*.java`, `api/internal/*.java`, `api/llm/api/*.java` | kontrolery HTTP (`@*Mapping`); łańcuchy filtrów i reguły dostępu w `api/config/SecurityConfig.java` |
| entry points (CLI) | `api/cli/*Command.java`, `api/cli/CommandType.java`, `api/SzczurRadarApiApplication.java` | komendy operatora uruchamiane z tego samego jara |
| entry points (jobs) | `tools/pipeline-trigger/trigger.sh`, `tools/db-backup/backup.sh`, `tools/db-backup/restore-check.sh`, `tools/db-backup/nas-retention.sh` | zadania cykliczne; harmonogramy i zmienne środowiskowe w `.railway/railway.ts` (poza zakresem kodu, tylko do wglądu jako konfiguracja) |
| application logic | `api/pipeline/*.java`, `api/pipeline/flow/`, `api/pipeline/step/`, `api/pipeline/assess/`, `api/agents/assessor/*.java`, `api/agents/assessor/validation/`, `api/llm/*.java`, `api/llm/transport/`, `api/llm/{openrouter,gemini,deepseek}/`, `api/sources/*.java`, `api/sources/wykop/`, `api/alerts/`, `api/notifications/*.java`, `api/auth/*.java`, `api/auth/activation/*.java`, `api/user/*.java`, `api/settings/`, `api/text/` | serwisy, kroki, klienci usług zewnętrznych, reguły walidacji |
| application logic (resources) | `szczur-radar-api/src/main/resources/assessor/system-prompt.md`, `szczur-radar-api/src/main/resources/assessor/assessment-output-v2.schema.json`, `szczur-radar-api/src/main/resources/application.properties` | prompt i schemat wyjścia modelu ładowane przez kod; ustawienia |
| domain types | `api/agents/assessor/contract/`, `api/posts/*.java` (bez `api/`), `api/pipeline/run/`, `api/user/*.java`, `api/sources/Source.java`, `api/raw/*.java`, `api/llm/*.java` (enumy i rekordy), `api/alerts/AlertEvent.java`, `api/config/ErrorCode.java`, `api/config/ApiError.java` | rekordy, encje JPA, enumy zamkniętych zbiorów wartości; brak osobnej warstwy „domain” — typy leżą w pakietach funkcji |
| persistence | `szczur-radar-api/src/main/resources/db/migration/V*.sql`, `api/**/*Repository.java`, `api/persistence/`, `api/persistence/converters/`, `api/pipeline/state/`, `api/raw/PostgresRawPayloadStore.java`, `api/auth/activation/ActivationLinks.java`, `tools/db-backup/tables.sh` | schemat tylko z migracji (Hibernate `ddl-auto=none`); część zapisów surowym SQL (`JdbcClient`/`JdbcTemplate`) obok JPA; klasyfikacja tabel dla kopii |
| UI | `web/views/`, `web/components/`, `web/api/`, `web/auth/`, `web/router/`, `web/*.ts` (`labels.ts`, `runs.ts`, `feedFilter.ts`, `missingDays.ts`, `notificationsStore.ts`, `polling.ts`), `web/style.css`, `web/App.vue`, `web/main.ts`, `szczur-radar-web/dev/mock-api.js` | SPA; `dev/mock-api.js` to ręcznie utrzymywana atrapa API do zrzutów ekranu (tylko dev) |
| tests | `szczur-radar-api/src/test/java/**/*Test.java`, `**/*IT.java`, `szczur-radar-api/src/test/resources/fixtures/{wykop,assessor,openrouter,gemini,deepseek}/`, `szczur-radar-api/src/test/java/**/measurement/` (harness pomiarowy, nie w jarze), `web/**/*.test.ts`, `tools/pipeline-trigger/test.sh`, `tools/db-backup/test.sh` | fixture'y to prawdziwe odpowiedzi usług zewnętrznych |

## Excluded

| path | reason |
|---|---|
| `context/` (całość, w tym `context/map/`, `context/domain/`, `context/archive/`) | dokumenty i wcześniejsze analizy — czyta tylko agent dokumentów; mapa projektu i archiwa analiz nie mogą zasilać agentów kodu |
| `**/CLAUDE.md`, `**/README.md`, `AGENTS.md`, `REPOSITORY_GUIDELINES.md`, `CLAUDE.md` | instrukcje dla agentów i dokumentacja opisująca produkt — dokumenty referencyjne, tylko dla agenta dokumentów |
| `docs/` | wizualizacje (SVG roadmapy) |
| `.claude/`, `.agents/`, `.github/`, `.railway/` (poza odczytem harmonogramów), `tools/deploy-gate/`, `tools/ci-runner/`, `tools/healthchecks/`, `tools/render_roadmap_svg.py`, `compose.local.yaml`, `*/Dockerfile`, `szczur-radar-web/Caddyfile`, `.playwright/`, `package.json` (root) | narzędzia deweloperskie, CI, IaC i konfiguracja wdrożenia — nie zachowanie produktu |
| `szczur-radar-api/src/test/resources/fixtures/llm/recordings/` | 480 nagranych odpowiedzi modeli — dane, nie kod |
| `szczur-radar-api/src/test/resources/fixtures/llm/models.json` | katalog modeli do pomiarów |
| `szczur-radar-web/public/`, `szczur-radar-web/index.html`, `szczur-radar-web/*.json`, `szczur-radar-web/vite.config.ts`, `szczur-radar-web/vitest.config.ts`, `szczur-radar-api/pom.xml`, `szczur-radar-api/.mvn/`, `szczur-radar-api/mvnw*` | zasoby statyczne i konfiguracja budowania |
| `szczur-radar-web/src/views/KitchenSinkView.vue` | katalog komponentów tylko w dev; nie niesie reguł |
