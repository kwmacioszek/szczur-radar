# Backend w Javie (Spring Boot) — Plan Brief

> Full plan: `context/changes/java-api-rewrite/plan.md`
> Research: `context/changes/java-api-rewrite/research.md`

## What & Why

Zastępujemy `szczur-radar-api/` (Python/FastAPI) aplikacją Spring Boot 4 na Javie 25, która zachowuje zachowanie S-01 widoczne dla SPA i Railway. Powód: autor zna Javę i może rzetelnie oceniać kod generowany przez agenta, a w Pythonie nie może. To fundament F-03 roadmapy; F-02 (ocena LLM) wraca po nim, już w Javie z Embabel.

## Starting Point

Na `main` backend ma tylko zakres S-01: 5 tras (`/api/health`, `/api/ready`, `/api/auth/login|logout|me`), własny JWT w ciasteczku `sr_session` z `token_version`, limiter prób logowania, filtr Origin, CLI `create-admin`, jedną migrację Alembic (`users`) i ~1200 linii testów opisujących zachowanie. Produkcja ma jednego admina; baza zostanie wyczyszczona.

## Desired End State

API w Javie działa na produkcji pod tym samym adresem: SPA loguje, chroni trasy i wylogowuje bez żadnej zmiany po swojej stronie; sesja jest w Postgresie i przeżywa uśpienie API; admin jest utworzony od nowa przez `java -jar … create-admin`; Embabel jest w kontekście aplikacji i startuje z jednym dostawcą wybranym przez `LLM_API_PROVIDER`, modelem `LLM_MODEL` i prawdziwym `LLM_API_KEY` z sekretów środowiska, bez wywołań LLM; CI i dokumentacja opisują stack Javy.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Framework / język | Spring Boot 4, Java 25 LTS, Maven | Autor zna Javę; Boot 4 wymagany przez Embabel 1.5; Maven jak w szablonach Embabel | Research / Plan |
| Katalog | Java zastępuje `szczur-radar-api/` w miejscu | Railway i ścieżki bez zmian, brak dwóch backendów | Research |
| Dane produkcyjne | Baza czyszczona, admin od nowa | Na produkcji jest tylko jedno konto — migracja danych bez sensu | Research |
| Sesja | Spring Security + Spring Session JDBC zamiast JWT | Prawie wszystko z pudełka; sesja w Postgresie przeżywa uśpienie API | Plan |
| Czas sesji | 30 dni bezczynności; ciasteczko odnawiane po poprawnych uwierzytelnionych żądaniach API | Odnowienie `Max-Age` usuwa sztywny termin 30 dni od logowania; błędy, sondy i logout nie odnawiają ciasteczka | Plan / triage F2: Fix A |
| CSRF | Filtr Origin + `SameSite=Strict`, CSRF Springa wyłączony | Zero zmian w SPA, zgodne z kontraktem S-01 | Plan |
| Limit logowań | Port obecnego limitera ruchomego okna z Pythona do Javy | Zachowuje limit 5/20 porażek w 15 min; atomowe rezerwacje chronią przed równoległymi próbami, także gdy jedna kończy się sukcesem | Plan / triage F1: Fix A |
| Migracje | Flyway, fix-forward + PITR | Proste pliki SQL; darmowy Flyway nie ma `undo`, więc zmieniamy regułę repo | Research |
| Testy | JUnit 6 + AssertJ; `*Test` (unit) / `*IT` (Spring + Testcontainers Postgres 18) | Standard Boot; testy DB nie mogą się cicho pominąć | Plan |
| CLI | Ten sam jar: `migrate`, `create-admin` bez serwera WWW | Jeden artefakt; `migrate` w pre-deploy Railway | Plan |
| Przełączenie produkcji | Operator czyści schemat przed merge | Kod nigdy nie zawiera migracji kasującej dane | Plan |
| Ścieżka awaryjna | Naprawa i ponowne wdrożenie Javy; bez powrotu do Pythona | Decyzja użytkownika; błędy schematu naprawiamy kolejną migracją Flyway | Plan / triage F4: inne rozwiązanie |
| Struktura kodu | Pakiety wg funkcji `pl.szczurradar.api.*` | Jak dzisiejsze `app/`; łatwo dodać `agents`, `llm`, `sources` | Plan |
| Embabel | Szkielet już teraz; `LLM_API_PROVIDER` + `LLM_MODEL` + prawdziwy `LLM_API_KEY` wymagane w runtime i teście startu; bez wywołań LLM | Jeden aktywny dostawca: Gemini przez Google AI Studio, DeepSeek bezpośrednio lub Luna przez OpenRouter; bez wymagania pozostałych kluczy i bez fallbacku wyłączającego Embabel | Plan / triage F5: wspólna konfiguracja dostawcy |
| Fixture'y | Prawdziwe JSON-y usług w `src/test/resources/fixtures/` | Konwencja na F-02/S-02 | Plan |

## Scope

**In scope:** sondy, migracje (`users` bez `token_version` + tabele sesji), auth (login JSON, `/me`, logout, 401/403/429 w JSON, Origin, limiter, kompletność autoryzacji), CLI `create-admin` i `migrate`, szkielet Embabel, CI na Javie, `railway.ts`, dokumentacja, weryfikacja PR env, przełączenie produkcji.

**Out of scope:** logika F-02, przenoszenie danych, zgodność JWT/hashy z Pythonem, powrót do backendu Python po przełączeniu, zmiany w SPA, komendy S-07, AOT cache, `undo` migracji.

## Architecture / Approach

Jeden jar Spring Boot: bez argumentów startuje serwer na `:8000`, z argumentem uruchamia komendę operatora bez serwera WWW. Spring Security z deny-by-default i jawną listą tras publicznych; sesja w tabelach `SPRING_SESSION*`; principal = UUID użytkownika. Railway: Railpack buduje `./mvnw` (JDK 25), pre-deploy `java -jar … migrate`, start `java -jar …`; baza przez zmienne `PG*`. Całość na jednej gałęzi i w jednym PR, zweryfikowana w PR env przed merge przez człowieka.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Szkielet Java i budowanie | Python usunięty, Maven + sondy + Flyway + `migrate`, CI i Railway na Javie | Railpack/IaC (JDK 25, zmienne `PG*`, usunięcie `JWT_SECRET` przez bramkę) |
| 2. Uwierzytelnianie i autoryzacja | Logowanie, sesja w Postgresie, 401/403/429, Origin, limiter, test kompletności | Detale Spring Security (zapis kontekstu, `SameSite` w Boot 4, wyrównanie czasu) |
| 3. CLI i szkielet Embabel | `create-admin` z advisory lockiem, agent Embabel z prawdziwym kluczem, bez wywołań LLM | Klucz musi być dostarczony do CI i PR env przed weryfikacją fazy; Embabel pozostaje włączony |
| 4. Dokumentacja, PR env, produkcja | Aktualne dokumenty i roadmapa (Markdown, generator, grafika), weryfikacja end-to-end, przełączenie na czystą bazę | Okno bez logowania między czyszczeniem bazy a deployem; zimny start JVM |

**Prerequisites:** gałąź `feature/java-api-rewrite`, Docker lokalnie (Testcontainers), JDK 25, dostęp operatora do Railway (`railway connect`, `railway ssh`), włączone PITR.
Od fazy 3 wymagane `LLM_API_PROVIDER` (`gemini`, `deepseek`, `openrouter`), `LLM_MODEL` (dokładne ID modelu dostawcy) i prawdziwy `LLM_API_KEY`: lokalnie w środowisku procesu, w CI dostawca/model jako GitHub Actions variables i klucz jako secret, oraz osobno w Railway PR env. Produkcyjna konfiguracja ustawiona przed merge; produkcyjny klucz niedostępny w CI/PR env. `preserve()` w IaC zachowuje wartości, ale ich nie tworzy. Test startu nie wykonuje wywołań LLM. Zmiana dostawcy wymaga spójnej zmiany tej trójki ustawień.
**Estimated effort:** ~3–4 sesje z agentem w 4 fazach + jedno okno operatorskie na przełączenie.

## Open Risks & Assumptions

- Brak wymaganej konfiguracji `LLM_API_PROVIDER`/`LLM_MODEL`/`LLM_API_KEY` lub nieznany dostawca zatrzymuje start kontekstu i weryfikację CI od fazy 3. Klucze niewybranych dostawców nie są potrzebne. Problemy integracji Embabel trzeba rozwiązać przed zakończeniem fazy; test i serwer używają tej samej konfiguracji, bez fallbacku wyłączającego platformę. Sam start bez wywołań LLM nie potwierdza ważności klucza u dostawcy.
- Zimny start JVM po uśpieniu może być odczuwalny — mierzymy w PR env, ewentualna optymalizacja to osobna decyzja.
- Bramka `railway-config` może uznać usunięcie `JWT_SECRET` za destrukcyjne — wtedy zostaje `preserve()` z komentarzem.
- Powrót do Pythona jest wykluczony. Awaria po przełączeniu wymaga poprawki Javy/konfiguracji i ponownego wdrożenia; nieudane pierwsze wdrożenie po czyszczeniu bazy wydłuża przerwę do czasu naprawy. PITR służy odzyskaniu danych; odtworzony schemat musi być zgodny z Javą i Flyway przed podłączeniem aplikacji.

## Success Criteria (Summary)

- Na produkcji SPA działa bez zmian: logowanie, ochrona tras, wylogowanie, 403 dla obcego Origin, 429 po limicie.
- Sesja przeżywa uśpienie API; admin utworzony przez CLI w Javie loguje się na publicznym adresie.
- `./mvnw verify` i cały workflow CI są zielone, a dokumentacja repo nie zawiera aktywnych instrukcji Pythona.
