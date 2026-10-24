<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-16 — Wykop nie gubi postów po cichu

- **Plan**: context/changes/wykop-ingest-completeness/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-24
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 6 observations

Przegląd po merge'u: kod z squasha `2ed995c` (PR #94) na `main` @ `6f2a85c`. Automatyczne kryteria na `main`: `./mvnw -B -Pmeasurement test-compile` OK; `./mvnw -B verify` — 758 testów jednostkowych i 232 IT zielone (`WykopApiClientTest` 25, `WykopRepositoryTest` 16, `PipelineAgentIT` 15); `WykopPagingProbe` nie uruchamia się w `verify`. Wszystkie wiersze Manual odhaczone z dowodami w `change.md` (sonda 30 dni, mapa sekretów, przebieg w PR env, porównanie z `total`, decyzje człowieka 3.4 i 4.3); weryfikacja produkcji po nocy 2026-10-24: `fetched` 709 = `total` sondy 709.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — SVG roadmapy nieodświeżone po zmianie statusu S-16

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: tools/render_roadmap_svg.py:171,286; docs/visuals/roadmap.svg
- **Detail**: Zmiana przestawiła S-16 na `in-progress` w `context/foundation/roadmap.md:105,261`, a reguła repo („Whenever context/foundation/roadmap.md changes, update the data in `tools/render_roadmap_svg.py` and regenerate `docs/visuals/roadmap.svg`”) nie została wykonana: skrypt nadal ma `"ready"` dla S-16. Powód z czasu implementacji: skrypt nie znał statusu `in-progress`; dziś go zna (S-19, F-04).
- **Fix**: W commicie archiwizacji ustawić S-16 na `done` w obu miejscach skryptu i wygenerować SVG (`python3 tools/render_roadmap_svg.py`).
- **Decision**: FIXED — w commicie archiwizacji (S-16 → `done` w skrypcie i w roadmapie, SVG wygenerowane).

### F2 — Drobne edycje dokumentacji i zachowania poza literą planu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md:213; szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md:40-46; szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java:163
- **Detail**: (1) `pipeline/CLAUDE.md` — poza zaplanowaną notą o kroku `fetch` zmieniono też punkt o `FIRE_ONCE` („Retries of a call live only below the step (`llm/transport/TransportRetry`, a source's client such as `sources/wykop/WykopApiClient`)”); (2) w `sources/CLAUDE.md` nowy punkt 5 przenumerował dawne 5–6 na 6–7; (3) drugie czytanie robi pauzę także przed swoją stroną 1 (test przypina 3 pauzy). Wszystkie trzy są spójne z intencją planu i nieszkodliwe.
- **Fix**: Bez zmian w kodzie — przyjąć jako zgodne z intencją (zapis w tym raporcie wystarcza).
- **Decision**: ACCEPTED — operator: zgodne z intencją planu, bez zmian.

### F3 — Klucz elementu z pola `resource` odpowiedzi, a nie z zasobu zapytania

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopRepository.java:250-252, :149
- **Detail**: Element z `id` ma klucz `item.resource() + ":" + id` (pole z odpowiedzi), element bez `id` — `resource.path`. Wyniki zasobów łączy `elements.putAll` (dawniej `putIfAbsent`), więc wpis i link z tym samym `id` i bez pola `resource` (`null:<id>`) nadpisałyby się — jeden element znika z `elements` i nie trafia do `skipped`, `fetched` zaniżony o 1. W prawdziwych odpowiedziach pole `resource` zawsze jest (sonda: 5643 elementy), a taki element i tak byłby niemapowalny.
- **Fix**: Kluczować przez `resource.path + ":" + id`, tak jak element bez `id` — zasoby rozdzielone z konstrukcji.
- **Decision**: FIXED — `WykopRepository.key` kluczuje zasobem zapytania; test `anEntryAndALinkWithTheSameIdAreBothKept` (break-check: ze starym kluczem czerwony tylko ten test).

### F4 — Budżet `get(path, query)` liczony ze stałej, nie z time-outu klienta

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiClient.java:140-143
- **Detail**: Skrót dla wywołań spoza repozytorium (sonda) bierze `DEFAULT_TIMEOUT × MAX_ATTEMPTS + 2 × MAX_RETRY_AFTER`, a nie `timeout` instancji, i nie liczy wymiany `auth`. Klient z time-outem > 50 s nie zmieściłby trzeciej próby, > 150 s — pierwszej. Dziś bez skutku: sonda używa 30 s, testy 5 s; repozytorium ma własny termin.
- **Fix**: `timeout.multipliedBy(MAX_ATTEMPTS).plus(MAX_RETRY_AFTER.multipliedBy(MAX_ATTEMPTS - 1))` (kształt `TransportRetry.maxCallDuration`).
- **Decision**: FIXED — budżet z `timeout` klienta; test `aSingleCallFitsTheClientsOwnTimeout` (time-out 200 s; break-check: ze starym budżetem `DEADLINE_EXCEEDED` przed pierwszą próbą).

### F5 — `clean()` maskuje w stałej kolejności

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/sources/wykop/WykopApiClient.java:349-357
- **Detail**: Kolejność: klucz, sekret, tokeny. Gdyby jedna wartość zawierała drugą (np. klucz w sekrecie), dłuższa zostałaby zamaskowana częściowo, a jej reszta wyciekłaby. Przy losowych danych Wykopu mało prawdopodobne; testy maskowania przechodzą.
- **Fix**: Maskować od najdłuższej wartości.
- **Decision**: FIXED — `clean()` maskuje od najdłuższej wartości; test `aKeyInsideTheSecretLeavesNoPartOfTheSecret` (break-check: od najkrótszej czerwony tylko ten test).

### F6 — Minimum watchdoga nie zna terminu pobierania

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/PipelineStallSettings.java (`minimum()`, poza diffem)
- **Detail**: Ostrzeżenie o zbyt krótkim `PIPELINE_STALL_TIMEOUT` liczy tylko wywołanie LLM (przy `LLM_TIMEOUT=10s` minimum to 7,5 min). Jawne `PIPELINE_STALL_TIMEOUT=8m` nie dostanie ostrzeżenia, a nieudane pobranie z Wykopu może trwać ~11 min — watchdog zatrzymałby przebieg jako „bez postępu” zamiast `DEADLINE_EXCEEDED`. Dziś bez skutku: domyślnie ≥ 15 min, produkcja 39 min; test przypina relację z `MIN_DEFAULT`.
- **Fix**: Dopisać w `pipeline/CLAUDE.md` (sekcja watchdoga), że jawny `PIPELINE_STALL_TIMEOUT` nie powinien schodzić poniżej `FETCH_DEADLINE + 2 × DEFAULT_TIMEOUT` (~11 min); ewentualnie uwzględnić to w `minimum()` przy S-17.
- **Decision**: FIXED — dopisek w `pipeline/CLAUDE.md` (nota o kroku `fetch`) i w `context/deployment/deploy-plan.md` (wiersz `PIPELINE_STALL_TIMEOUT`); `minimum()` bez zmian (S-17).

### F7 — Komentarze sondy opisują dawną regułę jako „dzisiejszą”

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/WykopPagingProbe.java:42, :168
- **Detail**: „WykopRepository stops here today: it counts received elements, duplicates included” i „today's stop rule” w javadoc — od fazy 3 repozytorium liczy unikalne elementy względem `total` strony 1, więc oba opisy dotyczą reguły sprzed S-16 (kod pomiarowy: najwyżej OBSERVATION, `measurement/CLAUDE.md`).
- **Fix**: Przeformułować na „the pre-S-16 stop rule”.
- **Decision**: FIXED — javadoc, komentarz i dwie etykiety wyjścia sondy oraz `measurement/README.md` mówią „pre-S-16”; nazwy pól bez zmian (kod pomiarowy).

### F8 — Luki w testach poza listą planu

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/WykopApiClientTest.java:95, :121; szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/WykopRepositoryTest.java:356
- **Detail**: Wszystkie przypadki z planu są pokryte. Niepokryte: liczba żądań w testach 403 (plan: „zostają z jednym żądaniem”), 401 trwałe po odświeżeniu, `total` znikający na dalszej stronie (gałąź „none”, `WykopRepository.java:206`), `DEADLINE_EXCEEDED` z repozytorium (także w drugim czytaniu), niespójny zasób `search/links`. `WykopRepositoryTest` buduje klienta konstruktorem 4-argumentowym (`Sleeper.REAL`) — przyszły przypadek z 5xx czekałby naprawdę 2 s + 4 s.
- **Fix**: Dopisać asercję liczby żądań w testach 403 i przypadek „none”; konstruktor z `Sleeper` bez czekania w `WykopRepositoryTest`; pozostałe przy następnej zmianie w pakiecie.
- **Decision**: FIXED — liczba żądań w `aFailedAuthNamesTheStatusButNeverTheSecret` i `anErrorQuotingTheTokenIsMasked` (break-check: 403 ponawiane → oba czerwone); `aTotalThatDisappearsOnALaterPageIsInconsistent` (break-check: brak `total` nie jest niespójnością → czerwony tylko ten test); `WykopRepositoryTest` buduje klienta z `Sleeper` bez czekania. Poza zakresem, przy następnej zmianie w pakiecie: 401 trwałe po odświeżeniu, `DEADLINE_EXCEEDED` z repozytorium, niespójny `search/links`.
