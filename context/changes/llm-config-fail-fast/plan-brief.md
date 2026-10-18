# S-10 llm-config-fail-fast — Plan Brief

> Full plan: `context/changes/llm-config-fail-fast/plan.md`
> Research: `context/changes/llm-config-fail-fast/research.md`

## What & Why

Operator ma dostać czytelny błąd startu serwera i `migrate`, gdy ustawienie czasu jest złe. Dziś `LLM_TIMEOUT=120` przechodzi jako 120 ms: aplikacja startuje, a każdy przebieg kończy się `failed` na sprawdzeniu LLM, co noc (incydent z 2026-10-06). Przy okazji trzy ustawienia czasu dostają jedną regułę, a kontrakt `LLM_*` wychodzi z `config/` (roadmapa S-10, audyt B1 i E).

## Starting Point

`config/LlmConfigurationCheck` już waliduje `LLM_*` przed kontekstem: przy starcie serwera, w każdej komendzie CLI i w testach. Brakuje reguły i czytelnego wyjścia:

- trzy kopie parsowania czasu mają różne zasady (`LLM_TIMEOUT` przyjmuje gołą liczbę i ISO-8601);
- operator widzi stack trace Springa;
- komendy CLI kończą się kodem 1;
- enumy dostawcy siedzą w klasie konfiguracji i importuje je encja `posts/Assessment`.

## Desired End State

- **Jedna reguła.** `LLM_TIMEOUT` (`10s`–`30m`), `ACTIVATION_LINK_TTL` (`1m`–`7d`) i `PIPELINE_STALL_TIMEOUT` (`1ms`–`1d`, czyli 24h) przyjmują tylko zapis `<liczba><jednostka>` i korzystają z jednego parsera w `settings/`.
- **Czytelny błąd.** Zły `LLM_*` (serwer i każda komenda) albo `PIPELINE_STALL_TIMEOUT` (serwer) daje blok `APPLICATION FAILED TO START` z nazwą ustawienia i podaną wartością, bez stack trace'u. Komenda CLI kończy się kodem `2`. Zły `ACTIVATION_LINK_TTL` dalej daje dzisiejszy komunikat `create-user` / `reset-password` z kodem `2`.
- **Kontrakt `LLM_*` w `llm/`.** Rekord i enumy dostawcy żyją w korzeniu `llm/`.
- **Weryfikacja.** W Railway PR env `LLM_TIMEOUT=120` zatrzymuje pre-deploy `migrate`.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Dolna granica `LLM_TIMEOUT` | `10s` (górna bez zmian `30m`) | Łapie pomyłki jednostek i literówki, a zostawia swobodę strojenia. | Plan |
| Format czasu | Tylko `<liczba całkowita><jednostka>` (`ns`…`d`); ISO-8601 odrzucone | Jeden zapis w dokumentach i komunikatach; nikt nie używa ISO. | Plan |
| Błąd startu | Własny wyjątek + `FailureAnalyzer` (blok bez stack trace'u) | Jeden mechanizm Boot dla serwera i każdej komendy CLI. | Plan |
| Kod wyjścia CLI | `USAGE` (2) przy błędzie konfiguracji, `FAILED` (1) przy innej awarii | Zgodne z istniejącym opisem `ExitCode.USAGE` („missing settings”). | Plan |
| Miejsce parsera | Nowy pakiet-liść `settings/` | `ActivationSettings` zostaje bez Springa i bez zależności od `config/`. | Plan |
| Enumy dostawcy | Enumy i rekord `LlmProviderProperties` do korzenia `llm/` | Bez nowego cyklu `config ↔ llm`; encja zależy od `llm/`, nie od `config/`. | Plan |
| Granice per ustawienie | Parametry parsera, nie stała reguły | Stall timeout potrzebuje `500ms` w testach; link `1m`–`7d`. | Research |
| Zgłaszanie błędów | Każdy wywołujący zachowuje swój styl (lista, `Result`, wyjątek) | Parser jest czystą funkcją, więc nie trzeba przepisywać trzech ścieżek. | Research |
| Granice w rekordzie | Tylko w `from()`, nie w konstruktorze | Testy klientów budują rekord z 300 ms. | Research |

## Scope

**In scope:**
- przeniesienie rekordu i enumów do `llm/` (refaktor bez zmiany zachowania);
- `settings/`: `DurationSetting`, `SettingText`, `InvalidSettingsException`;
- reguła dla trzech ustawień czasu;
- `FailureAnalyzer`;
- wspólny pomocnik kodu wyjścia CLI;
- testy;
- README, deploy-plan, CLAUDE.md i `REPOSITORY_GUIDELINES`;
- weryfikacja w PR env i odczyt produkcji przed merge'em.

**Out of scope:**
- `spring.session.timeout` i `Retry-After`;
- zmiana domyślnych wartości i górnych granic;
- ujednolicenie stylu zgłaszania błędów;
- przycinanie w `WykopConfiguration` i `PipelineTriggerToken`;
- zmiany IaC i CI;
- testy architektury;
- edycje `context/archive/**`.

## Architecture / Approach

Parser `settings/DurationSetting.parse(name, text, min, max, problems)` sprawdza po kolei:

1. gołą liczbę;
2. format prosty (`DurationStyle.SIMPLE`);
3. zakres.

Zwraca `Duration` albo dopisuje problem do listy. `LlmProviderProperties.from` i `PipelineStallSettings.from` rzucają `InvalidSettingsException` (dziedziczy po `IllegalStateException`). `config/InvalidSettingsFailureAnalyzer` (w `spring.factories`) zamienia go w blok raportu Boot, także gdy kontekst jeszcze nie istnieje (błąd z listenera). Komendy CLI szukają tego wyjątku w łańcuchu przyczyn i zwracają `USAGE`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Kontrakt LLM w `llm/` | Rekord i enumy w `llm/`, zmienione importy (~44 pliki) | Pominięty import albo zmieniona nazwa stałej (pilnują kompilator i `JpaSchemaIT`) |
| 2. Jedna reguła czasu | `settings/` i trzy ustawienia z granicami; `LLM_TIMEOUT` 10s–30m | Zmienione komunikaty łamią asercje w 5 klasach testów |
| 3. Czytelny błąd startu | Wyjątek, `FailureAnalyzer`, kod `2` w CLI | Analyzer musi działać bez kontekstu (błąd z listenera) |
| 4. Dokumenty i weryfikacja | Dokumenty, PR env z `LLM_TIMEOUT=120`, odczyt produkcji | Wartość na produkcji niezgodna z regułą zatrzymałaby deploy |

**Prerequisites:**
- gałąź `feature/llm-config-fail-fast` z aktualnego `origin/main` i issue S-10 (faza 1 `/10x-implement`);
- Docker dla `./mvnw -B verify`;
- dostęp do Railway PR env i odczytu zmiennych produkcji.

**Estimated effort:** ~2 sesje w 4 fazach. Fazy 1 i 4 są głównie mechaniczne.

## Open Risks & Assumptions

- **Produkcja.** `LLM_TIMEOUT` jest udokumentowany jako `300s` (ostatni zapis 2026-10-15), ale niesprawdzony na żywo. Krok 4.4 odczytuje go przed merge'em, a merge czeka, aż wartość spełnia regułę.
- **Zachowanie Boot.** Opis blokowego raportu pochodzi z kodu Boot 4.0.8. Test startu z `OutputCaptureExtension` i ręczne kroki 3.3–3.4 to potwierdzą.
- **Zmiana zachowania dla operatora.** ISO-8601 przestaje działać w `ACTIVATION_LINK_TTL` i `PIPELINE_STALL_TIMEOUT`, a kod wyjścia CLI przy złej konfiguracji zmienia się z 1 na 2.

## Success Criteria (Summary)

- `LLM_TIMEOUT=120` (albo `PT2M`, `9s`) zatrzymuje start serwera i pre-deploy `migrate` blokiem z nazwą ustawienia, bez stack trace'u i bez klucza w wyjściu.
- Trzy ustawienia czasu korzystają z jednego parsera, a w `src/main` nie ma już `detectAndParse`.
- W `config/` nie ma typów kontraktu LLM. `./mvnw -B verify` i CI są zielone.
