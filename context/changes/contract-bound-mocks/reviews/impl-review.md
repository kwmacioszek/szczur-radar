<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-43: atrapy odpowiadają jak prawdziwe API

- **Plan**: context/changes/contract-bound-mocks/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-11-11
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 5 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | WARNING |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Kryteria Automated uruchomione ponownie 2026-11-11, wszystkie zielone:
- `npm test`: 730/730;
- `npm run build`;
- SVG roadmapy zgodny z rendererem;
- `tools/deploy-gate/test.sh`;
- `tools/pipeline-trigger/test.sh`;
- `./mvnw -B verify`: 370 testów jednostkowych i 1098 integracyjnych, BUILD SUCCESS.

Granice „What We're NOT Doing” są zachowane. Diff jest pusty dla `ContractRegistry`, `ContractRoutesIT`, `ContractVariantsTest`, `shapes.test.ts`, `rules.test.ts`, `rules.json`, stuba CLI `railway` i planów IaC. W `trigger.sh` zmieniony jest tylko komentarz.

## Findings

### F1 — Atrapa logowania daje 400 tam, gdzie API daje 401

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-web/dev/mock-api.js:501, szczur-radar-web/src/contract/mockApi.test.ts:476-477
- **Detail**: Atrapa odpowiada 400 `invalid_request` na pusty e-mail, puste hasło i hasło liczbowe. API ma tylko `@NotNull @Size` (`auth/LoginRequest.java:13-14`), więc pusty e-mail przechodzi do limitera i uwierzytelnienia i kończy się 401 `invalid_credentials`. Jackson 3 zamienia liczbę `1` na `"1"`. Plan (`plan.md:186`) przewiduje 400 tylko przy braku `email`/`password`. Wiersze „an empty e-mail” i „a number as password” utrwalają więc zachowanie, którego API nie ma.
- **Fix**: 400 tylko dla brakującego lub `null` pola (i obiektu albo tablicy). Wiersze „an empty e-mail” i „a number as password” zmienić na oczekiwane 401 `invalid_credentials` w `LOGIN='invalid'` albo je usunąć.
- **Decision**: FIXED — 400 tylko dla brakującego, null, obiektu lub tablicy; pusty e-mail lub hasło → 401 w każdym trybie poza limited; liczba jako hasło czytana jako tekst

### F2 — Start `accepted` daje 202 mimo aktywnego przebiegu w danych atrapy

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-web/dev/mock-api.js:545-567 (przebieg 1 `running`: :252)
- **Detail**: API odmawia 409 `pipeline_busy`, gdy jakikolwiek przebieg jest queued lub running (`PipelineRunService.java:219-220`, `runs.existsActive()`). Atrapa w domyślnym `START='accepted'` przyjmuje start, chociaż jej przebieg 1 jest `running`. Przyjmuje też kolejne starty, gdy nowe przebiegi stoją `queued`. Tak zapisano tryb w planie (`plan.md:198`), więc to wada planu, nie implementacji. Dla E2E w M-5 to stan, którego produkcja nie wytworzy.
- **Fix A ⭐ Recommended**: w trybie `accepted` drugi start, gdy atrapa ma przebieg `queued` z wcześniejszego startu, daje 409 `pipeline_busy`. Nagłówek atrapy mówi wprost, że `accepted` udaje potok bez aktywnego przebiegu mimo przebiegu 1 na liście.
  - Strength: Zachowuje dzisiejsze zrzuty i przepływ startu, a dodaje drugi start z 409, jak w API.
  - Tradeoff: Pierwszy start nadal jest przyjęty przy przebiegu `running`, czego API nie robi.
  - Confidence: MED — tryb `busy` już istnieje dla pierwszego startu, więc zmiana jest mała.
  - Blind spot: Nie sprawdzono, czy widok `/admin` po starcie zależy od drugiego udanego startu.
- **Fix B**: Start odpowiada 409, gdy dane atrapy mają przebieg aktywny, a `START='accepted'` daje 202 tylko przy `MODE` bez aktywnego przebiegu.
  - Strength: Atrapa nie wytworzy stanu, którego API nie zna.
  - Tradeoff: Domyślna atrapa daje 409 na start, więc zrzut przyjętego startu wymaga innego trybu. Zmienia to semantykę `START` zapisaną w planie i nagłówku.
  - Confidence: MED — wymaga przejrzenia wierszy startu w tabeli zachowań.
  - Blind spot: Wpływ na procedurę `/10x-ui` i zrzuty `/admin`.
- **Decision**: FIXED via Fix A — drugi start przy przebiegu queued z wcześniejszego startu → 409 pipeline_busy; nagłówek opisuje założenie trybu accepted

### F3 — Część zawieszeń 60 s nie ma testu na sztucznych zegarach

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-web/src/contract/mockApi.test.ts:667-714
- **Detail**: Plan (Critical Implementation Details, Testing Strategy) wymaga sprawdzenia zawieszeń atrapy na sztucznych zegarach. Jest pełny test `hang()` dla admina w `MODE='loading'` i połowiczny dla `ACTIVATION='checking'` (sprawdzone tylko 59 s). Brakuje feedu (`MODE` `loading` i `loading-next`, `mock-api.js:623`), postu i `/raw` (`loading` i `raw-loading`, `:645`) oraz `ACTIVATION='sending'`. M-5 postawi E2E stanów ładowania właśnie na tych trybach.
- **Fix**: Dodać wiersze „nie ma odpowiedzi po 59 s, jest po 60 s” dla każdego brakującego miejsca zawieszenia, a w `ACTIVATION='checking'` jawnie sprawdzić 60 s.
- **Decision**: FIXED — testy 59 s / 60 s dla feedu (loading, loading-next), postu i /raw (loading, raw-loading), ACTIVATION sending i checking

### F4 — `context/foundation/roadmap.md` niezacommitowany

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/foundation/roadmap.md:124, :332
- **Detail**: W HEAD renderer i SVG mają S-43 jako `progress`, a `roadmap.md` nadal `proposed`. Zmiana na `in-progress` (z wcześniejszym `planning` i datą `updated`) leży w drzewie roboczym, bo plik był zmieniony przed startem implementacji. Plan i reguła repo każą zmieniać roadmapę razem z rendererem i SVG.
- **Fix**: Zacommitować `context/foundation/roadmap.md` (diff dotyczy tylko statusu S-43 i daty `updated`).
- **Decision**: FIXED (commit 29f44b1)

### F5 — Atrapa crona składa stany niemożliwe w API

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: tools/pipeline-trigger/test.sh:105-106, :213-216, :428-434
- **Detail**: Stałe `COUNTERS` (270/251/249/2) zawsze nadpisują liczniki z plików:
  - `running` i `interrupted` mają w pliku `assessTotal: 10`, więc domyślne tętno to „assessed 251/10”;
  - `succeeded`/`partial` biorą plik przebiegu nieudanego przed oceną (`assessTotal: null`), a `failed` przed oceną ma `assessedOk: 249`.

  Kształt jest związany z plikiem, bo obcy klucz daje `STUB ERROR`, ale wartości tworzą stany, których API nie wytworzy. Właściwość z kryterium 3.5 (brak `assessTotal` przed oceną) pokazuje przypadek `failed-before-assess` przez nadpisanie przypadku, a nie przez plik `run-state-failed.json`. Do tego `fill()` robi `times[key]`, więc nowy klucz `<instant>` w pliku da KeyError z tracebackiem zamiast komunikatu `STUB ERROR`.
- **Fix**: Liczniki zależne od stanu:
  - `running`, `interrupted`, `succeeded` i `partial` dostają `COUNTERS` razem ze spójnym `assessTotal` (np. 251, a w `running` więcej niż ocenione);
  - `failed` zostaje przy wartościach z pliku (zera i null), a asercje logu przypadku `failed` przechodzą na te wartości;
  - brak klucza w `times` jest zgłaszany przez `stub_error`.
  - Strength: Atrapa przestaje wytwarzać sprzeczne liczby, a 3.5 wynika z pliku, nie z nadpisania.
  - Tradeoff: Zmiana kilku oczekiwanych linii logu w `test.sh` (przypadki `failed`, `retry-failed`, `empty`).
  - Confidence: MED — zakres asercji opartych na 270/251/249/2 nie jest policzony.
  - Blind spot: Przypadki `old_api` i `heartbeat` mogą zależeć od obecnych liczników.
- **Decision**: FIXED — liczniki zależne od stanu (running 121/251, ended 251/251, failed z pliku), retry bez fetch/store, stub_error dla nieznanego <instant>

### F6 — Kształt przypięty podwójnie w `InternalPipelineRunIT`

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/InternalPipelineRunIT.java:240, :257-260
- **Detail**: Plan mówi „zamiast literału albo `containsOnlyKeys`”. Obok porównania z plikiem został ścisły literał `isStrictlyEqualTo("[{\"id\":…,\"kind\":\"range\"}]")` i `containsOnlyKeys(...)` z 16 polami. Zmiana kształtu wymaga więc edycji w dwóch miejscach.
- **Fix**: Usunąć literał i `containsOnlyKeys` tam, gdzie kształt pilnuje plik; zostawić asercje wartości.
- **Decision**: FIXED — usunięte literały i containsOnlyKeys obok fixture() w InternalPipelineRunIT

### F7 — Pułapki w dokumentacji testów narzędzi

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/test/java/pl/szczurradar/api/health/HealthIT.java:23-27, tools/deploy-gate/README.md, tools/pipeline-trigger/test.sh:36
- **Detail**:
  - `HealthIT` ma dwa javadoki pod rząd nad `UNAVAILABLE`; pierwszy opisuje `READY`, który nie ma żadnego.
  - 503 z `/api/ready` jest przypięty plikiem `deploy-safety-unavailable.json`, a README bramki przypisuje `/api/ready` tylko `ready.json`.
  - Nagłówek `tools/pipeline-trigger/test.sh` wymienia Docker i openssl, a krok 0 wymaga teraz `jq` na hoście.
- **Fix**: Przenieść komentarz nad `READY`, dopisać w README, że `deploy-safety-unavailable.json` obsługuje też 503 `/api/ready`, a w nagłówku `test.sh` dopisać `jq`.
- **Decision**: FIXED — javadoc nad READY, README bramki o 503 /api/ready, jq w nagłówku test.sh

### F8 — Wytyczne obiecują więcej, niż wymusza strażnik

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: REPOSITORY_GUIDELINES.md (akapit o `src/contract/`), szczur-radar-web/src/contract/mockApi.test.ts:371-379
- **Detail**: Nowe zdanie w wytycznych mówi, że nowa trasa SPA dostaje wiersz w tabeli tras strażnika. Pokrycie liczy się jednak po plikach kontraktu, nie po trasach: trasa zwracająca istniejący plik przejdzie bez wiersza (plan to przyznaje, `plan.md:76`). `SESSION_ROUTES` (401/403) to osobna ręczna lista, której nic nie wiąże z `ROUTES`.
- **Fix**: Wyprowadzić `SESSION_ROUTES` z `ROUTES`, a w wytycznych napisać, że każdy plik kontraktu musi mieć trasę, a nową trasę na istniejącym pliku dopisuje się ręcznie razem z wpisem w `ContractRegistry.SHAPES`.
- **Decision**: FIXED — SESSION_ROUTES wyprowadzone z ROUTES (bez PUBLIC_ROUTES), zdanie w wytycznych doprecyzowane

### F9 — Szum w testach strażnika

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-web/src/contract/mockApi.test.ts:412-413, :549, :560-564, :710-713
- **Detail**:
  - Doklejka „[holds before S-43 phase 2]” zostaje w nazwach testów na stałe. To artefakt kryterium 2.3, a historia jest już w planie.
  - Test „every error code the table expects is one of the API's” dubluje skan literałów `detail` z `rules.test.ts:249-278`.
  - „a start in START busy does not hang” pilnuje zachowania, które nigdy nie wisiało.
- **Fix**: Usunąć znacznik z nazw testów i dwa zbędne testy.
- **Decision**: FIXED — usunięty znacznik i pole before, test kodów błędów (dubluje rules.test.ts) i test „START busy does not hang”

### F10 — Daty wierszy startu liczone raz przy ładowaniu modułu

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/contract/mockApi.test.ts:137-138, :439; szczur-radar-web/dev/mock-api.js:423
- **Detail**: `dMinus()` liczy się raz, przy ładowaniu modułu (`ROWS`), a „dziś” w atrapie przy każdym `installMock`. Gdy północ w Warszawie wypadnie między tymi chwilami, wiersz „to after today” da 202 zamiast 400. Okno ma sekundę lub dwie i recenzent nie odtworzył go w próbach (TZ Auckland, Los Angeles, dni zmiany czasu), ale to źródło rzadkiego, niestabilnego testu w CI.
- **Fix**: Przypiąć czas przez `vi.setSystemTime` w wierszach startu albo liczyć ciała wierszy leniwie (funkcja zamiast wartości).
- **Decision**: FIXED — tabela zachowań na przypiętym Date (vi.setSystemTime, NOW = 2026-10-20T10:00:00Z)
