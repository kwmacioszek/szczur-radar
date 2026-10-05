---
project: "Szczur Radar"
version: 2
status: draft # draft | active | locked
created: 2026-09-27
updated: 2026-10-06
prd_version: 2
main_goal: speed
top_blocker: time # secondary: decisions (LLM contract, filter scope and signal decision resolved 2026-09-27 — PRD v2)
milestone_id: mvp-wykop-signal-radar
milestone_seq: 1
milestone_status: open
---

# Roadmap: Szczur Radar

> Derived from `context/foundation/prd.md` (v2) + `context/foundation/llm-assessment/` (kontrakt oceny LLM V2 — `contract-v2.md`; V1 w archiwum) + `shape-notes.md` + `tech-stack-api.md` / `tech-stack-web.md` / `infrastructure.md` / `deploy-plan.md` + auto-researched codebase baseline.
> Edit-in-place; archive when superseded.
> Slices below are listed in dependency order. The "At a glance" table is the index.
> v2 (2026-09-27): kontrakt oceny LLM V1 rozstrzyga PRD Open Question 2 → S-02 odblokowane; warstwa kontraktu wydzielona jako F-02; S-04 zablokowane decyzją o filtrowaniu po spółce.
> v2.1 (2026-09-27): PRD v2 — filtr po spółce tekstowy, „sentyment” = stanowisko wobec celu, rozpoznawanie spółek i decyzja „wartościowy sygnał” poza MVP → S-04 odblokowane.
> v2.2 (2026-10-01): kontrakt oceny LLM V2 (`assessment-contract-harness`, po pilotażach na prawdziwych postach) zastępuje V1 — wynik oceny to trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z cytatem, flagi niepewności; bez grafu argumentów. F-02 mierzy modele na zestawie wzorcowym zamiast 12 wymyślonych przypadków.
> v2.3 (2026-10-01): nowy fundament F-03 (`java-api-rewrite`) — backend przepisany z Pythona/FastAPI na Javę (Spring Boot), bo autor zna Javę i może rzetelnie oceniać kod agenta. F-02 po fazie 1 (kontrakt V2 i dokumenty) czeka na F-03; implementacja kontraktu (p2–p4) zostanie przeplanowana w Javie. S-07 też czeka na F-03, żeby nie dokładać kodu w Pythonie. PR #23 (Python F-02) zamknięty bez merge'a.
> v2.4 (2026-10-02): triage planu F-03 — sesje Spring Session JDBC zamiast JWT/`token_version`, 30 dni bezczynności z odnawianiem ciasteczka, Flyway fix-forward, czysta baza i ponowne utworzenie admina; bez powrotu do Pythona. Szkielet Embabel z wyborem jednego dostawcy przez `LLM_API_PROVIDER`, `LLM_MODEL`, `LLM_API_KEY`: Gemini (Google AI Studio), DeepSeek bezpośrednio lub Luna przez OpenRouter. F-03 pozostaje w planowaniu; ocena jakości modeli nadal należy do F-02.

> v2.5 (2026-10-03): F-03 zakończone po wdrożeniu #26 (`7a406f2`) i #27 (`6bc15db`) oraz weryfikacji produkcji — migracje, gotowość API i logowanie admina potwierdzone w [podsumowaniu issue #25](https://github.com/kwmacioszek/szczur-radar/issues/25#issuecomment-5965517657). F-02 może przeplanować fazy p2–p4 w Javie, a warunek F-03 dla S-07 jest spełniony. Poprawki z przeglądu implementacji idą osobnym PR (gałąź `fix/java-api-rewrite-review-fixes`): `context/archive/2026-10-01-java-api-rewrite/follow-ups/review-fixes.md`.

> v2.6 (2026-10-03): F-02 przeplanowane w Javie (fazy 2–6, `context/changes/assessment-contract-harness/plan.md` wersja 3). W `## Parked` dopisany self-hosted runner CI dla PR (issue #30), nice-to-have po MVP.

> v2.7 (2026-10-04): W `## Parked` dopisana ocena obrazków w postach (model multimodalny) — nice-to-have po MVP, do sprawdzenia, czy ma sens, na podstawie zestawu wzorcowego F-02.

> v2.8 (2026-10-05): W `## Parked` dopisany „LLM hardening: ironia i stanowisko” — nice-to-have po MVP, z pomiaru F-02 (żaden model nie spełnił progów kierunku i sprzeczności przez ironię Wykopu); źródła: `context/foundation/llm-assessment/hardening-sources.md`.

> v2.9 (2026-10-06): F-02 — model domyślny wybrany: GPT-6 Luna (`medium`) przez OpenRouter, tryb flex/standard wybiera operator; progi kierunku celów i sprzeczności złagodzone decyzją użytkownika (≥70 % / ≤10 % zamiast ≥75 % / ≤3 %; raport `reports/gold-2026-10-06.md`). Uzasadnienie „LLM hardening” poprawione: pilot 9 pokazał, że większość sprzeczności pada na postach dosłownych, nie ironicznych — wpis obejmuje kierunek i stanowisko ogólnie, ironia jest jednym z wątków.

## Milestone

**M-1: MVP — ocenione sygnały z Wykopu dla zaproszonych osób** — Status: open

- **Intent:** Codzienny, automatyczny przepływ zamienia posty z Wykopu (#gielda) w ocenione przez LLM sygnały inwestycyjne, które zaproszeni, zalogowani użytkownicy przeglądają i filtrują w aplikacji webowej, a administrator zarządza dostępem przez CLI i widzi historię przebiegów.
- **Source materials:** `context/foundation/prd.md` (v2); `context/foundation/llm-assessment/contract-v2.md` (V2 — kształt wejścia/wyjścia oceny LLM dla FR-004 / FR-011a)
- **Done when:** every F-NN and S-NN below is `done` — czyli wszystkie cztery główne kryteria sukcesu PRD (`## Success Criteria` → Primary) są spełnione na produkcji.
- **Scope anchors:** wszystkie wymagania konieczne (must-have) PRD: FR-001–FR-007, FR-009, FR-010, FR-011a, FR-012–FR-015, FR-018, FR-021, FR-026–FR-028, FR-030; historyjki US-01, US-02. Wymagania nice-to-have są odroczone (patrz `## Parked`).

## Vision recap

Na Wykopie (#gielda) i Reddicie codziennie pojawiają się setki postów o inwestowaniu; wartościowe informacje giną w szumie, a indywidualny inwestor nie ma czasu przeglądać obu platform ręcznie. Ogólne agregatory (RSS, alerty słów kluczowych) nie rozumieją treści, więc Szczur Radar ma własny przepływ: pobiera posty, a LLM według zamkniętego kontraktu opisuje ich znaczenie — czy treść dotyczy inwestowania, jakich spółek lub tematów dotyczy, jakie stanowisko autor zajmuje wobec każdej z nich oraz jakie tezy, opinie, fakty, pytania i działania przedstawia (każde z dosłownym cytatem). MVP startuje od jednego źródła — Wykopu; Reddit jest odroczonym rozszerzeniem.

## North star

**S-02: Zalogowany użytkownik widzi chronologiczną listę postów z Wykopu ocenionych przez LLM** — to pierwszy moment, w którym widać, czy ocena LLM faktycznie oddziela sygnał od szumu; przy celu `speed` wszystko inne (filtry, harmonogram, dashboard) ma sens tylko wtedy, gdy to działa.

> „Gwiazda przewodnia” (north star) oznacza tu najmniejszy wycinek od początku do końca, którego udane dostarczenie potwierdza główną hipotezę produktu — dlatego stoi tak wcześnie, jak pozwalają jego warunki wstępne.

## At a glance

| ID   | Change ID                      | Outcome (user can …)                                                                                                            | Prerequisites | PRD refs                                                                                                                                                                                 | Status   |
| ---- | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- | ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| F-01 | production-data-gate           | (foundation) produkcja jest gotowa na prawdziwe dane: limity kosztów, zweryfikowany restore, zapieczętowane sekrety             | —             | NFR (dane przechowywane bezterminowo; bezpieczeństwo przed utratą danych po stronie platformy), Open Question 4; guardrail kosztów LLM                                                   | ready    |
| F-03 | java-api-rewrite               | (foundation) backend w Javie obsługuje funkcje S-01 przez dotychczasowe API; sesje JDBC, migracje fix-forward i szkielet Embabel | S-01          | FR-012, FR-013, FR-014, FR-015, FR-026, NFR (każdy chroniony zasób wymaga uwierzytelnienia — komplet testów autoryzacji)                                                                 | done |
| F-02 | assessment-contract-harness    | (foundation) kontrakt oceny LLM V2 jest egzekwowany kodem, a model domyślny spełnia progi jakości na zestawie wzorcowym | F-03          | FR-004, Open Question 2 (rozstrzygnięte przez kontrakt, obecnie V2); guardrail jakości danych z LLM (bez halucynacji)                                                                     | in-progress |
| S-01 | admin-bootstrap-and-login      | operator tworzy pierwszego admina przez CLI; admin loguje się i wylogowuje; niezalogowany widzi tylko ekran logowania           | —             | FR-012, FR-013, FR-014, FR-015, FR-026, NFR (każdy chroniony zasób wymaga uwierzytelnienia — komplet testów autoryzacji), NFR (brak wglądu w dane cudzych kont)                          | done |
| S-02 | first-assessed-wykop-feed      | zalogowany użytkownik widzi chronologiczną listę postów z Wykopu ocenionych przez LLM                                           | S-01, F-02    | US-01, FR-001, FR-003, FR-004, FR-005, FR-006, FR-007, NFR (surowy payload oddzielony od danych aplikacji, bezstratny i kompresowalny), NFR (nowe źródło = tylko pozyskanie + mapowanie) | proposed |
| S-03 | post-detail-view               | użytkownik otwiera widok szczegółowy posta: ocena, surowe dane pod przełącznikiem, link do oryginału                            | S-02          | US-01, FR-009, FR-010                                                                                                                                                                    | proposed |
| S-04 | filter-by-assessment           | użytkownik filtruje listę po spółce/temacie (tekstowo) i stanowisku autora wobec niej                                           | S-02          | US-01, FR-011a                                                                                                                                                                           | proposed |
| S-05 | daily-scheduled-run-freshness  | przepływ uruchamia się sam codziennie, a użytkownik widzi datę ostatniej udanej aktualizacji                                    | S-02, F-01    | US-01, FR-002, FR-030                                                                                                                                                                    | proposed |
| S-06 | pipeline-run-history-dashboard | admin widzi historię przebiegów (kiedy, status, wynik) i wyraźny sygnał nieudanego przebiegu                                    | S-05, S-01    | FR-021, FR-015, NFR (operator dostaje widoczny sygnał awarii przebiegu)                                                                                                                  | proposed |
| S-07 | cli-user-onboarding            | admin tworzy konto przez CLI, zaproszony ustawia hasło przez jednorazowy link i się loguje                                      | S-01, F-03    | US-02, FR-018, FR-027, FR-028                                                                                                                                                            | proposed |

## Streams

Navigation aid — groups items that share a Prerequisites chain. Canonical ordering still lives in the dependency graph below; this table is the proposed reading order across parallel tracks.

| Stream | Theme             | Chain                             | Note                                                                                                                |
| ------ | ----------------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| A      | Dostęp i konta    | `S-01` → `F-03` → `S-07`          | F-03 przenosi backend na Javę i otwiera całą dalszą pracę backendu; S-07 może potem iść równolegle ze strumieniami B i C. |
| B      | Przegląd sygnałów | `F-03` → `F-02` → `S-02` → `S-03` → `S-04` | F-02 (faza 1 gotowa) wraca po F-03, już w Javie; S-03 i S-04 są wzajemnie równoległe.                               |
| C      | Codzienny automat | `F-01` → `S-05` → `S-06`          | Dołącza do strumienia B w `S-05` (wymaga S-02); F-01 to praca operatorska, do zrobienia od razu, równolegle.        |

## Baseline

What's already in place in the codebase as of `2026-09-27` (auto-researched + user-confirmed).
Foundations below assume these are present and do NOT re-scaffold them.

- **Frontend:** partial — SPA z neutralną stroną „W przygotowaniu” (`szczur-radar-web/src/App.vue`); brak routingu, klienta API, testów.
- **Backend / API:** partial — tylko sondy `/api/health` i `/api/ready` (`szczur-radar-api/app/routes.py`); brak tras danych i administracyjnych.
- **Data:** partial — Postgres działa na Railway, jest połączenie do bazy (`szczur-radar-api/app/database.py`, tylko `SELECT 1`); brak modeli, migracji, magazynu surowych payloadów.
- **Auth:** absent — brak modelu użytkownika, sesji, ochrony tras, CLI.
- **Deploy / infra:** present — infrastruktura jako kod (`.railway/railway.ts`), CI (`.github/workflows/ci.yml`), izolowane środowiska PR z własną bazą, PITR włączone; otwarte: limity kosztów i czysty restore drill (wymagają subskrypcji — `context/deployment/deploy-plan.md`), brak usługi harmonogramu.
- **Observability:** absent — tylko healthchecki platformy i logi JSON proxy; brak rejestru przebiegów i sygnału awarii.
- **Ocena LLM:** kontrakt — present (stan z 2026-09-27: V1 accepted; od 2026-10-01 zastąpiony przez `context/foundation/llm-assessment/contract-v2.md`); kod — absent (brak integracji z LLM, modeli kontraktu, walidatora, przypadków testowych).

## Foundations

### F-01: Bramka produkcyjnych danych

- **Outcome:** (foundation) produkcja jest gotowa na zbieranie prawdziwych danych: aktywna subskrypcja, ustawione limity kosztów, restore drill zakończony zdrowym, odpytywalnym odtworzeniem, sekrety Wykopu/LLM zapieczętowane i niedostępne w środowiskach PR.
- **Change ID:** production-data-gate
- **PRD refs:** NFR (dane przechowywane bezterminowo; bezpieczeństwo przed utratą danych po stronie platformy), Open Question 4; guardrail kosztów LLM
- **Unlocks:** S-05 (codzienne zbieranie prawdziwych danych na produkcji); ścieżka weryfikacji „odtworzenie backupu działa” wymagana przez `deploy-plan.md` przed fazą 6
- **Prerequisites:** —
- **Parallel with:** F-02, F-03, S-01, S-02, S-03, S-04, S-07
- **Blockers:** —
- **Unknowns:**
  - Czy czysty restore drill przejdzie po subskrypcji; jeśli nie — powrót do alternatywnej platformy z `infrastructure.md` (ryzyko nr 1). — Owner: user. Block: no.
- **Risk:** Praca operatorska bez kodu produktu; sekwencjonowana na starcie równolegle, żeby nie stała się niespodziewaną blokadą S-05 tuż przed terminem.
- **Status:** ready

### F-02: Egzekwowany kontrakt oceny LLM

- **Outcome:** (foundation) kontrakt oceny LLM V2 (trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z cytatem, flagi niepewności) jest źródłem prawdy; wejście i wyjście są odwzorowane jako zamknięte modele odrzucające obce pola; walidator sprawdza reguły kontraktu (cytaty jako fragmenty po normalizacji białych znaków, referencje, reguły stanowiska) z jawną klasyfikacją błędów; wpisy bez treści są odsiewane bez wywołania modelu; model domyślny wybrany pomiarem GPT-6 Luna przez OpenRouter, DeepSeek Flash bezpośrednio u dostawcy i Gemini 3.1 Flash-Lite przez Google AI Studio spełnia progi jakości na zestawie wzorcowym prawdziwych postów oznaczonych przez użytkownika.
- **Change ID:** assessment-contract-harness
- **PRD refs:** FR-004, Open Question 2 (rozstrzygnięte przez kontrakt, obecnie V2); guardrail jakości danych z LLM (bez halucynacji)
- **Unlocks:** S-02 (ocena każdego posta w przepływie); ścieżka weryfikacji „ocena spełnia kontrakt V2” wymagana przez S-02 i każdą przyszłą zmianę kontraktu (reguła zmiany: failing fixture → zmiana kontraktu → kod)
- **Prerequisites:** F-03
- **Parallel with:** F-01, S-07
- **Blockers:** — (faza 1 — kontrakt V2 i dokumenty — zrobiona; F-03 zakończone; fazy p2–p4 wymagają przeplanowania w Javie, kod w Pythonie z PR #23 nie jest przenoszony)
- **Unknowns:**
  - ~~Wybór dostawcy i modelu LLM z obsługą ustrukturyzowanego wyjścia oraz akceptowalny koszt jednej oceny (powiązane z Open Roadmap Question 5).~~ Rozstrzygnięte 2026-10-06: GPT-6 Luna (`medium`) przez OpenRouter, ~$5,9/mies. (standard) lub ~$2,9/mies. (flex) przy 12 000 postów; ustawienie na produkcji po merge — runbook „F-02: model domyślny” w `context/deployment/deploy-plan.md`. — Owner: user. Block: no.
  - ~~Jakość ustrukturyzowanego wyjścia przez Embabel (biblioteka wybrana w F-03) dla trzech kandydatów.~~ Zmierzone na zestawie wzorcowym (13 konfiguracji, prompt v2.5; `context/changes/assessment-contract-harness/reports/gold-2026-10-06.md`): zgodność z kontraktem 96–100 %; żaden model nie spełnił pierwotnych progów kierunku (≥75 %) i sprzeczności (≤3 %) — użytkownik złagodził je do ≥70 % / ≤10 %, Luna spełnia wszystkie z małym zapasem (kierunek 73,0 %, sprzeczności 8,3 %). — Owner: user. Block: no.
- **Risk:** Zamyka ryzyko jakości oceny przed budową przepływu: jeśli żaden model nie spełnia progów na zestawie wzorcowym, dowiadujemy się tego bez pobierania, zapisu i UI; celowo NIE obejmuje zapisu, pobierania z Wykopu ani widoku — te wchodzą w S-02.
- **Status:** in-progress

### F-03: Backend w Javie

- **Outcome:** (foundation) `szczur-radar-api` działa na Javie (Spring Boot) i obsługuje funkcje S-01 przez dotychczasowe API dla SPA — logowanie i wylogowanie, ochrona każdej trasy (komplet testów autoryzacji), CLI do utworzenia pierwszego admina oraz sondy `/api/health` i `/api/ready`. Sesje w ciasteczku `sr_session` są przechowywane przez Spring Session JDBC, przeżywają restart API, wygasają po 30 dniach bezczynności z odnawianiem ciasteczka i są unieważniane przez usuwanie sesji użytkownika. Migracje Flyway działają według fix-forward. Embabel rejestruje szkieletowego agenta i wybrany model z prawdziwym kluczem, bez wywołań LLM. Całość zweryfikowana na produkcji po wyczyszczeniu bazy i ponownym utworzeniu admina; kod Pythona usunięty, dokumenty stacku i roadmapa zaktualizowane.
- **Change ID:** java-api-rewrite
- **Decisions:** `context/archive/2026-10-01-java-api-rewrite/plan.md` i jego triage: bez migracji starych kont/sesji/hashy, bez `token_version`, bez powrotu do Pythona; awarie usuwane przez poprawkę Javy/konfiguracji i ponowne wdrożenie, PITR służy odzyskaniu danych. Jeden dostawca na uruchomienie: `LLM_API_PROVIDER` ∈ {`gemini`, `deepseek`, `openrouter`}, dokładne ID modelu w `LLM_MODEL`, prawdziwy klucz wybranego dostawcy w `LLM_API_KEY`; konfiguracja zapewniona od fazy 3 w CI i PR env, na produkcji przed merge. F-03 dostarcza szkielet Embabel, a logika i pomiary oceny pozostają w F-02.
- **PRD refs:** FR-012, FR-013, FR-014, FR-015, FR-026, NFR (każdy chroniony zasób wymaga uwierzytelnienia — komplet testów autoryzacji), NFR (brak wglądu w dane cudzych kont)
- **Unlocks:** F-02 (fazy p2–p4 w Javie), S-07; pośrednio cała dalsza praca backendu
- **Prerequisites:** S-01
- **Parallel with:** F-01
- **Blockers:** —
- **Unknowns:**
  - ROZSTRZYGNIĘTE: zimny start po uśpieniu w PR env wyniósł ok. 10–11 s dla całej ścieżki web→API (Spring + Embabel ok. 6–8 s); pomiar w `context/deployment/deploy-plan.md`. Ewentualna optymalizacja to osobna decyzja. — Owner: user. Block: no.
- **Risk:** Przepisanie uzasadnione możliwością rzetelnej oceny kodu Javy przez autora; zakres obejmuje funkcje S-01 i szkielet Embabel, bez przepisywania niedokończonej logiki F-02. Wyczyszczenie schematu przed merge rozpoczyna przerwę w logowaniu; nieudane wdrożenie Javy wydłuża ją do skutecznej naprawy, ponieważ powrót do Pythona jest wykluczony.
- **Status:** done
- **Verification:** produkcja po #26 (`7a406f2`) i #27 (`6bc15db`): V1/V2, `/api/ready` 200, admin utworzony przez CLI loguje się na publicznym adresie; [podsumowanie issue #25](https://github.com/kwmacioszek/szczur-radar/issues/25#issuecomment-5965517657). Poprawki przeglądu są śledzone oddzielnie w `context/archive/2026-10-01-java-api-rewrite/follow-ups/review-fixes.md`.

## Slices

### S-01: Pierwszy admin i logowanie

- **Outcome:** operator może utworzyć pierwsze konto administratora przez CLI; admin może zalogować się e-mailem i hasłem oraz wylogować; niezalogowany użytkownik trafia wyłącznie na ekran logowania.
- **Change ID:** admin-bootstrap-and-login
- **PRD refs:** FR-012, FR-013, FR-014, FR-015, FR-026, NFR (każdy chroniony zasób wymaga uwierzytelnienia — komplet testów autoryzacji), NFR (brak wglądu w dane cudzych kont)
- **Prerequisites:** —
- **Parallel with:** F-01, F-02
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Pierwszy wycinek wprowadził schemat bazy, migracje Alembic z działającym cofnięciem i wzorzec testów autoryzacji. F-03 zastępuje mechanizm migracji przez Flyway fix-forward; wymóg kompletności ochrony tras pozostaje obowiązujący dla dalszych zmian.
- **Status:** done

### S-02: Pierwsze ocenione posty z Wykopu na liście

- **Outcome:** zalogowany użytkownik widzi chronologiczną, stronicowaną listę postów z Wykopu (#gielda) z zadanego zakresu dat, bez duplikatów, każdy z oceną LLM zgodną z kontraktem V2 (trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z cytatem, flagi niepewności); przebieg jest uruchamiany ręcznie przez operatora.
- **Change ID:** first-assessed-wykop-feed
- **PRD refs:** US-01, FR-001, FR-003, FR-004, FR-005, FR-006, FR-007, NFR (surowy payload oddzielony od danych aplikacji, bezstratny i kompresowalny), NFR (nowe źródło = tylko pozyskanie + mapowanie)
- **Prerequisites:** S-01, F-02
- **Parallel with:** F-01, S-07
- **Blockers:** —
- **Unknowns:**
  - Gdzie gwiazda przewodnia jest weryfikowana z prawdziwymi wywołaniami Wykopu/LLM, zanim F-01 otworzy produkcję (lokalnie vs środowisko PR bez produkcyjnych sekretów). — Owner: user. Block: no.
  - Warunki i limity dostępu do API Wykopu przy codziennym użyciu. — Owner: user. Block: no.
  - Które typy zasobów Wykopu z #gielda wchodzą do MVP (wpis → `post`, znalezisko → `link`); komentarze są poza zakresem (PRD §Non-Goals), mimo że kształt wejścia kontraktu je obsługuje. — Owner: user. Block: no.
- **Risk:** Wciąż największy wycinek (pobranie, deduplikacja, ocena, zapis z pochodzeniem oceny, lista), ale ryzyko jakości oceny przeszło do F-02, więc tu zostaje integracja; jeśli `/10x-plan` uzna go za zbyt szeroki, może rozbić go na kilka zmian.
- **Status:** proposed

### S-03: Widok szczegółowy posta

- **Outcome:** użytkownik może otworzyć post i zobaczyć czytelną, przetworzoną ocenę (trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z dosłownym cytatem, flagi niepewności), surowe dane źródłowe pod opcjonalnym przełącznikiem oraz link do oryginału na Wykopie.
- **Change ID:** post-detail-view
- **PRD refs:** US-01, FR-009, FR-010
- **Prerequisites:** S-02
- **Parallel with:** F-01, S-04, S-05, S-06, S-07
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Pierwszy odczyt surowego payloadu przez aplikację — sprawdza w praktyce, że oddzielny magazyn surowych danych z S-02 daje bezstratny widok; struktura oceny V2 (cele ze stanowiskiem, tezy z cytatem, flagi niepewności) wymaga czytelnej prezentacji obok surowego posta — bez grafu argumentów.
- **Status:** proposed

### S-04: Filtrowanie po ocenie LLM

- **Outcome:** użytkownik może zawęzić listę postów po spółce/temacie (dopasowanie tekstowe do fragmentu wskazującego cel i rodzaju celu) oraz po stanowisku autora wobec tego celu (np. pozytywne / negatywne); brak wyników pokazuje czytelny stan pusty.
- **Change ID:** filter-by-assessment
- **PRD refs:** US-01, FR-011a
- **Prerequisites:** S-02
- **Parallel with:** F-01, S-03, S-05, S-06, S-07
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Filtr tekstowy bez rozpoznawania spółek (PRD v2) oznacza, że „CDR” i „CD Projekt” mogą dawać różne wyniki — świadomie przyjęte w MVP; stanowisko jest per cel, więc filtr musi łączyć cel i stanowisko tego samego celu, a nie dowolnego celu w poście.
- **Status:** proposed

### S-05: Codzienny automatyczny przebieg i świeżość danych

- **Outcome:** przepływ uruchamia się sam codziennie o ustalonej godzinie na produkcji, a użytkownik widzi nad listą datę i godzinę ostatniej UDANEJ aktualizacji danych.
- **Change ID:** daily-scheduled-run-freshness
- **PRD refs:** US-01, FR-002, FR-030
- **Prerequisites:** S-02, F-01
- **Parallel with:** S-03, S-04, S-07
- **Blockers:** —
- **Unknowns:**
  - Godzina uruchomienia i domyślny zakres dat/godzin pobierania dla przebiegu dziennego. — Owner: user. Block: no.
  - Jak traktować przebieg, w którym część ocen nie przeszła walidacji kontraktu (udany, częściowy czy nieudany dla etykiety świeżości). — Owner: user. Block: no.
- **Risk:** Wprowadza zapis każdego przebiegu (potrzebny do etykiety świeżości) oraz sprawdzenie zgodności wersji schematu przed zapisem — chroni przed scenariuszem z pre-mortem w `infrastructure.md`, w którym automat po cichu zapisuje uszkodzone rekordy.
- **Status:** proposed

### S-06: Dashboard historii przebiegów dla admina

- **Outcome:** admin widzi historię przebiegów (data/godzina, status, wynik) i jednoznaczny sygnał nieudanego lub częściowo nieudanego przebiegu; użytkownik z rolą `user` nie ma do tego dostępu.
- **Change ID:** pipeline-run-history-dashboard
- **PRD refs:** FR-021, FR-015, NFR (operator dostaje widoczny sygnał awarii przebiegu)
- **Prerequisites:** S-05, S-01
- **Parallel with:** S-03, S-04, S-07
- **Blockers:** —
- **Unknowns:**
  - Czy dashboard wystarcza jako „jednoznaczny, widoczny sygnał” awarii, czy potrzebne jest aktywne powiadomienie operatora. — Owner: user. Block: no.
- **Risk:** Pierwsza funkcja wyłącznie dla admina — weryfikuje rozdział ról na prawdziwej trasie; bez niej automat z S-05 działa bez nadzoru.
- **Status:** proposed

### S-07: Onboarding użytkownika przez CLI

- **Outcome:** admin może przez CLI utworzyć konto z rolą i dostać jednorazowy, wygasający link aktywacyjny; zaproszony może raz ustawić hasło i się zalogować; operator może wygenerować nowy link (unieważniając poprzedni) i odzyskać dostęp admina tym samym mechanizmem.
- **Change ID:** cli-user-onboarding
- **PRD refs:** US-02, FR-018, FR-027, FR-028
- **Prerequisites:** S-01, F-03
- **Parallel with:** F-01, F-02, S-02, S-03, S-04, S-05, S-06
- **Blockers:** —
- **Unknowns:**
  - Czas ważności linku aktywacyjnego (PRD sugeruje np. 24h). — Owner: user. Block: no.
- **Risk:** Niezależny od potoku — dobry kandydat do równoległej pracy agenta przy ograniczonym czasie; bez niego MVP ma tylko jednego użytkownika.
- **Status:** proposed

## Backlog Handoff

| Roadmap ID | Change ID                      | Suggested issue title                                                                          | Ready for `/10x-plan` | Notes                                                                            |
| ---------- | ------------------------------ | ---------------------------------------------------------------------------------------------- | --------------------- | -------------------------------------------------------------------------------- |
| F-01       | production-data-gate           | Bramka produkcyjnych danych: subskrypcja, limity, restore drill, sekrety                       | yes                   | Praca operatorska; zrób równolegle                                               |
| F-02       | assessment-contract-harness    | Kontrakt oceny LLM V2: modele, walidator, zestaw wzorcowy i wybór modelu                        | no                    | Faza 1 gotowa; F-03 zakończone, p2–p4 do przeplanowania w Javie (PR #23 zamknięty); odblokowuje gwiazdę przewodnią S-02 |
| F-03       | java-api-rewrite               | Backend w Javie: API S-01, sesje JDBC i szkielet Embabel                                       | — (done)              | Produkcja zweryfikowana po #26 i #27; issue #25 zamknięte. Poprawki przeglądu: `context/archive/2026-10-01-java-api-rewrite/follow-ups/review-fixes.md` |
| S-01       | admin-bootstrap-and-login      | Pierwszy admin przez CLI + logowanie/wylogowanie + ochrona tras                                | —                     | Wdrożone na produkcji 2026-09-29 (PR #13, #21); zarchiwizowane 2026-09-29 → `context/archive/2026-09-27-admin-bootstrap-and-login/` |
| S-02       | first-assessed-wykop-feed      | Lista postów z Wykopu ocenionych przez LLM (gwiazda przewodnia)                                | no                    | Po S-01 i F-02                                                                   |
| S-03       | post-detail-view               | Widok szczegółowy posta z oceną, surowymi danymi i linkiem do oryginału                        | no                    | Po S-02                                                                          |
| S-04       | filter-by-assessment           | Filtrowanie listy po spółce/temacie i stanowisku autora                                        | no                    | Po S-02                                                                          |
| S-05       | daily-scheduled-run-freshness  | Codzienny automatyczny przebieg + etykieta świeżości danych                                    | no                    | Po S-02 i F-01                                                                   |
| S-06       | pipeline-run-history-dashboard | Dashboard admina: historia przebiegów i sygnał awarii                                          | no                    | Po S-05                                                                          |
| S-07       | cli-user-onboarding            | Tworzenie kont przez CLI + aktywacja linkiem + recovery admina                                 | no                    | Po S-01 i F-03                                                                   |

## Open Roadmap Questions

1. **Czy `product_type` to "data-pipeline" czy "web-app"?** — Projekt to dwa moduły: pipeline+API jako rdzeń, pełnoprawna aplikacja webowa (logowanie, dashboard, lista/filtrowanie) jako warstwa konsumpcji. Wybrano `data-pipeline` jako najbliższą pojedynczą wartość; do doprecyzowania przy `/10x-tech-stack-selector`. Owner: użytkownik. Block: — (w praktyce rozstrzygnięte przez dwa osobne dokumenty stacku; nie wpływa na kolejność).
2. **Dokładny kształt pól w JSON zwracanym przez ocenę LLM** — ROZSTRZYGNIĘTE 2026-09-27 przez kontrakt V1; od 2026-10-01 obowiązuje `context/foundation/llm-assessment/contract-v2.md` (V2, bez grafu argumentów). Owner: użytkownik. Block: — (odblokowało S-02; PRD wskazuje kontrakt V2).
3. **Kanał i próg dla FR-025 (powiadomienia o obiecującym trendzie)** — nie ustalono jeszcze, czym jest "obiecujący trend" liczbowo ani gdzie ma trafiać powiadomienie. Owner: użytkownik. Block: — (FR-025 odroczone).
4. **Backup/retencja na docelowej platformie hostingowej** — PITR włączone, ale czysty restore drill i limity kosztów czekają na subskrypcję (`deploy-plan.md`). Owner: użytkownik. Block: F-01 → S-05.
5. **Budżet kosztów LLM na przebieg** — guardrail PRD każe minimalizować koszt zapytań, ale nie podaje pułapu (na dzień ani na post); kontrakt V2 wymaga zapisu zużycia tokenów, a F-02 mierzy koszt kandydatów na zestawie wzorcowym. Owner: użytkownik. Block: — (wpływa na F-02, S-02 i S-05, nie blokuje planowania).
6. **[ROZSTRZYGNIĘTE 2026-09-27 — PRD v2: bez automatycznej decyzji w MVP, §Business Logic + §Non-Goals]** **„Wartościowy sygnał” w MVP** — PRD §Business Logic mówi, że wynik oceny zawiera „czy jest wartościowy”, a kontrakt V1 zabrania LLM zwracania `is_valuable`/`is_signal` i przenosi decyzję o sygnale do osobnego, dalszego etapu, którego żaden FR nie opisuje. Czy MVP wystarcza trafność + stanowisko + teza z argumentami (bez decyzji „wartościowy”), czy potrzebna jest prosta reguła decyzji jako nowy FR? Owner: użytkownik. Block: — (S-02 może pokazać ocenę bez tej decyzji; wymaga aktualizacji PRD §Business Logic).
7. **[ROZSTRZYGNIĘTE 2026-09-27 — PRD v2: poza MVP, filtr tekstowy w FR-011a]** **Rozpoznawanie spółek (entity resolution)** — kontrakt V1 przenosi ustalanie, o którą spółkę chodzi, poza LLM; PRD nie ma dla tego FR. Wpływa na filtr po spółce (S-04) i przyszły wykres per spółka (FR-024). Owner: użytkownik. Block: — (odblokowało S-04).

## Parked

- **FR-008 / FR-011b — sortowanie po dacie, źródle i polach oceny** — Why parked: nice-to-have; chronologia wystarcza na start (cel `speed`).
- **FR-016 / FR-017 / FR-019 — panel kont w aplikacji + wysyłka maila z zaproszeniem** — Why parked: nice-to-have; CLI (S-07) daje ten sam efekt; PRD §Non-Goals wyklucza wymaganą wysyłkę e-maili.
- **FR-020 — własna nazwa wyświetlana** — Why parked: nice-to-have; e-mail wystarcza w MVP.
- **FR-022 — ręczne wywołanie przebiegu z dashboardu** — Why parked: nice-to-have; harmonogram (S-05) pokrywa potrzebę; wymaga API platformy (`infrastructure.md`).
- **FR-023 — Reddit jako drugie źródło** — Why parked: nice-to-have; kolejny kamień milowy — kontrakt V2 już obejmuje Reddit po normalizacji (`statement` w języku posta), a S-02 projektuje wspólny model źródeł zgodnie z NFR.
- **FR-024 / FR-025 — wykres giełdowy z trendem sygnałów i powiadomienia o trendzie** — Why parked: nice-to-have, poza MVP; wymaga integracji danych giełdowych, rozpoznawania spółek i rozstrzygnięcia Open Question 3.
- **FR-029 — automatyczne wygasanie krótkich sesji** — Why parked: nice-to-have; krótki timeout uciążliwy przy czytaniu. F-03 stosuje 30 dni bezczynności z odnawianiem ciasteczka; krótsze limity pozostają odroczone.
- **FR-031 — eksport widocznej listy do CSV/JSON** — Why parked: nice-to-have, nie blokuje głównego przepływu.
- **FR-032 — „Ask szczur” (pytanie do LLM o wybrany sygnał)** — Why parked: nice-to-have; dodatkowy koszt LLM wbrew guardrailowi kosztów.
- **Przetwarzanie komentarzy pod postami** — Why parked: PRD §Non-Goals (kontrakt V2 zachowuje typ `comment` i kontekst rodzica w kształcie wejścia — gotowe na przyszłość, nieużywane w MVP).
- **Rozpoznawanie spółek, automatyczna decyzja „wartościowy sygnał”, nowość informacji, trend, zaangażowanie, raporty** — Why parked: PRD v2 §Non-Goals; kontrakt V2 §15 umieszcza je poza oceną LLM jako osobne, dalsze etapy.
- **2FA, logowanie przez zewnętrznego dostawcę, samodzielny reset hasła, dodatkowe role** — Why parked: PRD §Non-Goals.
- **Wysoka dostępność / multi-region, własna logika backupu** — Why parked: PRD §Non-Goals; backup po stronie platformy (F-01).
- **Lekkie modele ML, embeddingi, klasyfikatory w potoku** — Why parked: `shape-notes.md` §Forward: technical-roadmap — kierunek na przyszłość, nie zakres MVP.
- **LLM hardening: kierunek i stanowisko (w tym ironia)** — Why parked: nice-to-have po MVP. Pomiar F-02 (`context/changes/assessment-contract-harness/reports/gold-2026-10-05.md`): żaden z 13 zmierzonych modeli/wariantów nie spełnił progów kierunku celów i sprzecznych kierunków — progi złagodzono do ≥70 % / ≤10 % (decyzja użytkownika 2026-10-06). Ironia (wg etykiet użytkownika 15 ze 120 postów) obniża trafność kierunku, ale większość sprzeczności pada na postach dosłownych (pilot 9 w `context/changes/assessment-contract-harness/frame.md`; Jev nie wykrywa ironii); więcej rozumowania nie pomagało. Cel: powrót do progów ≥75 % / ≤3 %. Tor: większy organiczny polski wzorzec z osobnym zbiorem kontrolnym → kierunek → ironia → stanowisko dosłowne vs zamierzone → ablacje kontekstu i promptu → porównanie modeli → regresja kosztu. Źródła (niezweryfikowane): `context/foundation/llm-assessment/hardening-sources.md`.
- **Ocena obrazków w postach (model multimodalny)** — Why parked: nice-to-have po MVP, do sprawdzenia, czy ma sens. PRD v2 i kontrakt V2 oceniają wyłącznie tekst (`prd.md:258`); w pilotażu #gielda ~44% wpisów ma zdjęcie, a część ma sens tylko w obrazku (`frame.md`, „Znane luki”). Zestaw wzorcowy F-02 (flaga `media.photo`, etykiety `unclear` z powodu obrazka, raport fazy 6) pokaże skalę; wdrożenie wymagałoby zmiany PRD, kontraktu (V3: cytat/dowód z obrazka nie jest podciągiem tekstu), wyboru modeli z wejściem obrazkowym i ponownego pomiaru, a koszt wejścia rośnie o tokeny obrazków.
- **Self-hosted runner CI dla PR (lokalny Docker), runnery GitHub Actions dla `main`** — Why parked: nice-to-have dla wygody developmentu, nie wymaganie PRD; odroczone po MVP, najwcześniej po F-02 ([issue #30](https://github.com/kwmacioszek/szczur-radar/issues/30)).

## Milestone History

(Pusta — to pierwszy kamień milowy. Wpisy dopisuje zamknięcie kamienia milowego.)

## Done

(Pusta przy pierwszym generowaniu. Wpisy dopisuje `/10x-archive` przy archiwizacji zmiany o pasującym `Change ID`.)

- **S-01: operator może utworzyć pierwsze konto administratora przez CLI; admin może zalogować się e-mailem i hasłem oraz wylogować; niezalogowany użytkownik trafia wyłącznie na ekran logowania.** — Archived 2026-09-29 → `context/archive/2026-09-27-admin-bootstrap-and-login/`. Lesson: —.
- **F-03: (foundation) `szczur-radar-api` działa na Javie (Spring Boot) i obsługuje funkcje S-01 przez dotychczasowe API dla SPA — logowanie i wylogowanie, ochrona każdej trasy (komplet testów autoryzacji), CLI do utworzenia pierwszego admina oraz sondy `/api/health` i `/api/ready`. Sesje w ciasteczku `sr_session` są przechowywane przez Spring Session JDBC, przeżywają restart API, wygasają po 30 dniach bezczynności z odnawianiem ciasteczka i są unieważniane przez usuwanie sesji użytkownika. Migracje Flyway działają według fix-forward. Embabel rejestruje szkieletowego agenta i wybrany model z prawdziwym kluczem, bez wywołań LLM. Całość zweryfikowana na produkcji po wyczyszczeniu bazy i ponownym utworzeniu admina; kod Pythona usunięty, dokumenty stacku i roadmapa zaktualizowane.** — Archived 2026-10-03 → `context/archive/2026-10-01-java-api-rewrite/`. Lesson: —.
