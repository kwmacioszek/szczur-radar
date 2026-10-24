---
project: "Szczur Radar"
version: 3
status: draft # draft | active | locked
created: 2026-09-27
updated: 2026-10-24
prd_version: 2
main_goal: quality
top_blocker: decisions # 5 decyzji produktowych z audytu (context/archive/2026-10-17-post-mvp-pipeline-audit/research.md, Open Questions); najdroższa, kanał alarmowy poza aplikacją (F-04), rozstrzygnięta 2026-10-20: healthchecks.io → Telegram
milestone_id: mvp-quality-reliability-hardening
milestone_seq: 2
milestone_status: open
---

# Roadmap: Szczur Radar

> Derived from (M-2) the user's milestone description + `context/map/repo-map.md` (mapa projektu, 2026-10-17) + `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` (audyt diagnostyczny pipeline'u i operacji, HEAD `5b6eabb`) + PRD v2 NFRs + auto-researched codebase baseline (z mapy i audytu). M-1 derived from `context/foundation/prd.md` (v2) + `context/foundation/llm-assessment/` + `shape-notes.md` + `tech-stack-api.md` / `tech-stack-web.md` / `infrastructure.md` / `deploy-plan.md`.
> Edit-in-place; the next milestone continues this file. Archive only when an independent roadmap replaces it.
> Slices below are listed in dependency order. The "At a glance" table is the index.
> ID-y kontynuują numerację M-1 (F-04, S-08 …), bo `## Done` i issues GitHuba (`S-NN: …`) już używają F-01–F-03 i S-01–S-07.
> v2 (2026-09-27): kontrakt oceny LLM V1 rozstrzyga PRD Open Question 2 → S-02 odblokowane; warstwa kontraktu wydzielona jako F-02; S-04 zablokowane decyzją o filtrowaniu po spółce.
> v2.1 (2026-09-27): PRD v2 — filtr po spółce tekstowy, „sentyment” = stanowisko wobec celu, rozpoznawanie spółek i decyzja „wartościowy sygnał” poza MVP → S-04 odblokowane.
> v2.2 (2026-10-01): kontrakt oceny LLM V2 (`assessment-contract-harness`, po pilotażach na prawdziwych postach) zastępuje V1 — wynik oceny to trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z cytatem, flagi niepewności; bez grafu argumentów. F-02 mierzy modele na zestawie wzorcowym zamiast 12 wymyślonych przypadków.
> v2.3 (2026-10-01): nowy fundament F-03 (`java-api-rewrite`) — backend przepisany z Pythona/FastAPI na Javę (Spring Boot), bo autor zna Javę i może rzetelnie oceniać kod agenta. F-02 po fazie 1 (kontrakt V2 i dokumenty) czeka na F-03; implementacja kontraktu (p2–p4) zostanie przeplanowana w Javie. S-07 też czeka na F-03, żeby nie dokładać kodu w Pythonie. PR #23 (Python F-02) zamknięty bez merge'a.
> v2.4 (2026-10-02): triage planu F-03 — sesje Spring Session JDBC zamiast JWT/`token_version`, 30 dni bezczynności z odnawianiem ciasteczka, Flyway fix-forward, czysta baza i ponowne utworzenie admina; bez powrotu do Pythona. Szkielet Embabel z wyborem jednego dostawcy przez `LLM_API_PROVIDER`, `LLM_MODEL`, `LLM_API_KEY`: Gemini (Google AI Studio), DeepSeek bezpośrednio lub Luna przez OpenRouter. F-03 pozostaje w planowaniu; ocena jakości modeli nadal należy do F-02.

> v2.5 (2026-10-03): F-03 zakończone po wdrożeniu #26 (`7a406f2`) i #27 (`6bc15db`) oraz weryfikacji produkcji — migracje, gotowość API i logowanie admina potwierdzone w [podsumowaniu issue #25](https://github.com/kwmacioszek/szczur-radar/issues/25#issuecomment-5965517657). F-02 może przeplanować fazy p2–p4 w Javie, a warunek F-03 dla S-07 jest spełniony. Poprawki z przeglądu implementacji idą osobnym PR (gałąź `fix/java-api-rewrite-review-fixes`): `context/archive/2026-10-01-java-api-rewrite/follow-ups/review-fixes.md`.

> v2.6 (2026-10-03): F-02 przeplanowane w Javie (fazy 2–6, `context/archive/2026-09-27-assessment-contract-harness/plan.md` wersja 3). W `## Parked` dopisany self-hosted runner CI dla PR (issue #30), nice-to-have po MVP.

> v2.7 (2026-10-04): W `## Parked` dopisana ocena obrazków w postach (model multimodalny) — nice-to-have po MVP, do sprawdzenia, czy ma sens, na podstawie zestawu wzorcowego F-02.

> v2.8 (2026-10-05): W `## Parked` dopisany „LLM hardening: ironia i stanowisko” — nice-to-have po MVP, z pomiaru F-02 (żaden model nie spełnił progów kierunku i sprzeczności przez ironię Wykopu); źródła: `context/foundation/llm-assessment/hardening-sources.md`.

> v2.9 (2026-10-06): F-02 — model domyślny wybrany: GPT-6 Luna (`medium`) przez OpenRouter, tryb flex/standard wybiera operator; progi kierunku celów i sprzeczności złagodzone decyzją użytkownika (≥70 % / ≤10 % zamiast ≥75 % / ≤3 %; raport `reports/gold-2026-10-06.md`). Uzasadnienie „LLM hardening” poprawione: pilot 9 pokazał, że większość sprzeczności pada na postach dosłownych, nie ironicznych — wpis obejmuje kierunek i stanowisko ogólnie, ironia jest jednym z wątków.

> v2.10 (2026-10-06): S-05 — zajawka sposobu uruchamiania (cron woła wewnętrzny endpoint API zamiast startować kontener z obrazem API), z badania `api-docker-image` (wpis z 2026-10-04, przenumerowany przy scaleniu z F-02).

> v2.11 (2026-10-06): F-01 i pytanie 4 — PITR wyłączone, a jego bucket usunięty ze względu na koszty (`api-docker-image`). Backup bazy: brak do czasu subskrypcji; przed S-05 dzienna kopia wolumenu Railway i test odtwarzania (`Block: F-01 → S-05` bez zmian).

> v2.12 (2026-10-11): F-01 (`production-data-gate`) — Railway Hobby nie ma kopii wolumenu ani PITR, więc codzienny `pg_dump` z serwisu cron `db-backup`, sprawdzany odtworzeniem, w buckecie Railway i na NAS; wolumen 5 GB; limity $5/$10; limit $10/mies. na kluczu OpenRouter (pytanie 5). Pytanie 4 rozstrzygnięte; PRD (NFR, Non-Goal, pytanie 4) zaktualizowany.

> v2.13 (2026-10-13): W `## Parked` dopisany komunikat przy nieudanym odczycie daty aktualizacji danych (S-05, faza 4) — nice-to-have po MVP.

> v2.14 (2026-10-13): S-05 (`daily-scheduled-run-freshness`) — niewiadome rozstrzygnięte: serwis cron `pipeline-trigger` o 00:30 UTC woła `POST /internal/pipeline-runs/daily` (token, konto systemowe, tylko sieć prywatna), zakres D-2…D-1 czasu warszawskiego, przebieg `partial` liczy się do etykiety świeżości. Ryzyko poprawione: zapis przebiegów istnieje od S-02, a sprawdzenie wersji schematu wycofane. PRD (FR-030, NFR sygnału awarii, Access Control) doprecyzowany.

> v2.15 (2026-10-13): FR-022 (ręczne wywołanie przebiegu z dashboardu) zrealizowane w S-02: przycisk w `/admin`, przebieg w procesie API, bez API platformy Railway. Wpis usunięty z `## Parked`, notka przy S-02 w `## Done`, doprecyzowanie w PRD.

> v2.16 (2026-10-13): S-06 — dopisek do ryzyka z przeglądu S-05 (F3): odzyskiwanie przy starcie przerywa przebieg trwający w starym kontenerze podczas deployu; dziś chroni tylko okno bez merge'a.

> v2.17 (2026-10-12; numer po scaleniu z S-05): S-07 (`cli-user-onboarding`) — link aktywacyjny ważny domyślnie 12 h (parametr `ACTIVATION_LINK_TTL`), a `reset-password` operatora działa dla dowolnego konta, także admina (doprecyzowanie FR-027/FR-028 w PRD). Unknown S-07 rozstrzygnięte; wiersz S-07 w Backlog Handoff zgodny ze stanem.

> v2.18 (2026-10-12; numer po scaleniu z S-05): W `## Parked` dopisane unieważnianie sesji wersją poświadczeń — po MVP, z przeglądu implementacji S-07 (F1): logowanie w toku może przetrwać `reset-password` do aktywacji.

> v2.19 (2026-10-13): S-06 — dopisek z pierwszego przebiegu crona na produkcji: hardening monitorowania (postęp trwającego przebiegu w `/admin`, ostrzeżenie o braku postępu, linie postępu w logach API i crona), bo dziś trwającego przebiegu nie da się obserwować bez czytania bazy. W `## Parked` dopisany przybliżony czas zakończenia trwającego przebiegu (nice-to-have po MVP, na bazie postępu z S-06).

> v2.20 (2026-10-13): S-04 (`filter-by-assessment`) w realizacji. Do Parked dopisany TODO na przyszłość z impl-review S-04: wydajność filtra feedu przy dużej liczbie postów (pomiar i projekcja celów po ~20k postów).

> v2.21 (2026-10-16): Do Parked dopisany przebieg pipeline'u bez Embabel (prosta orkiestracja) — z przeglądu planu S-06 (F1), do sprawdzenia po MVP.

> v2.22 (2026-10-16): S-06 (`pipeline-run-history-dashboard`) — Unknown rozstrzygnięte: sygnał awarii to powiadomienia w aplikacji dla adminów (dzwoneczek w nagłówku, strona przebiegu), bez pusha i e-maila; `partial` z samymi postami `failed` do ponowienia nie daje powiadomienia (PRD, doprecyzowanie NFR o sygnale awarii). W zakresie S-06 ostrzeżenie o braku postępu zastąpione strażnikiem, który kończy przebieg jako `failed` po `PIPELINE_STALL_TIMEOUT` (decyzja 2026-10-15).

> v3 (2026-10-17): M-1 zamknięty (wszystkie F-01–F-03 i S-01–S-07 `done`). Otwarty M-2 „MVP Quality & Reliability Hardening” z opisu użytkownika i audytu `post-mvp-pipeline-audit`: fundament F-04 i wycinki S-08–S-19. Pozycja Parked „przebieg bez Embabel” przeszła do S-17. Nowe PRD niepotrzebne: M-2 utwardza istniejące NFR (m.in. sygnał awarii przebiegu, `prd.md:258`); wycinek, który zmieni ich rozumienie, dopisuje `> Doprecyzowanie` w PRD.

> v3.1 (2026-10-19): Pozycja Parked „Self-hosted runner CI” (#30) zrealizowana poza roadmapą w zmianie `self-hosted-pr-runner` (wyczerpany limit minut GitHub Actions 2026-10-18): przełącznik runnera w `ci.yml`, runner w VM w Proxmoxie i pula VM-ek na PC. W Parked zastąpiona drugim runnerem w Proxmoxie.

> v3.2 (2026-10-20): F-04 (`out-of-app-alert-channel`) — niewiadoma rozstrzygnięta: kanał to healthchecks.io (darmowy Hobbyist) z alarmem w Telegramie przez @HealthchecksBot, koszt $0, bez odpytywania produkcji i zegarów w API. Oba crony meldują start i kod wyjścia, API ma nadawcę zdarzeń i komendę `send-test-alert`. Notatki w S-13 (check każdego crona już istnieje) i S-18 (ścieżki ERROR podłącza do `OperatorAlerts`); doprecyzowanie NFR o sygnale awarii w PRD.

## Milestone

**M-2: MVP Quality & Reliability Hardening** — Status: open

- **Intent:** Istniejące MVP działa niezawodnie i nie milczy: złe noce, awarie dostawców i błędne wdrożenia są widoczne dla operatora, nie niszczą danych ani nie blokują pracy, a testy chronią te ścieżki — zanim ruszy kolejna roadmapa funkcjonalna.
- **Source materials:** opis użytkownika (kotwice poniżej); `context/map/repo-map.md`; `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` (ustalenia A1–A8, B1–B11, C1–C15, sekcje D–F); NFR z `context/foundation/prd.md` (v2).
- **Done when:** every F-NN and S-NN below is `done`.
- **Scope anchors** (z opisu: „poprawić niezawodność, bezpieczeństwo wdrożeń, jakość testów i obserwowalność istniejącego MVP przed rozpoczęciem kolejnej roadmapy funkcjonalnej”):
  - MS-01: niezawodność — przebiegi, posty i powiadomienia nie gubią się ani nie utykają przy awariach dostawców, instancji i źródła.
  - MS-02: bezpieczeństwo wdrożeń — merge do `main` nie wdroży czerwonego commita, nie zrestartuje produkcji po cichu i nie zablokuje kolejnych wdrożeń.
  - MS-03: jakość testów — ścieżki awarii z audytu mają testy, a testy silnika są skupione i niezależne od kolejności.
  - MS-04: obserwowalność — każda zła noc i każdy błąd, który wymaga reakcji, daje operatorowi jednoznaczny sygnał, nie wykrywany pośrednio przez brak danych.

## Vision recap

Na Wykopie (#gielda) i Reddicie codziennie pojawiają się setki postów o inwestowaniu; wartościowe informacje giną w szumie, a indywidualny inwestor nie ma czasu przeglądać obu platform ręcznie. Ogólne agregatory (RSS, alerty słów kluczowych) nie rozumieją treści, więc Szczur Radar ma własny przepływ: pobiera posty, a LLM według zamkniętego kontraktu opisuje ich znaczenie — czy treść dotyczy inwestowania, jakich spółek lub tematów dotyczy, jakie stanowisko autor zajmuje wobec każdej z nich oraz jakie tezy, opinie, fakty, pytania i działania przedstawia (każde z dosłownym cytatem). MVP (M-1) dostarczył to dla Wykopu; M-2 utwardza je, zanim dojdą nowe źródła i funkcje.

## North star

**S-08: Operator dostaje sygnał, gdy przebieg nic nie pobrał albo pominął elementy, a etykieta świeżości się wtedy nie przesuwa** — to jedyne ustalenie audytu o wadze wysokiej i wprost łamie NFR „operator nie dowiaduje się o awarii pośrednio, przez brak nowych danych” (`prd.md:258`); przy celu `quality` dowodzi, że utwardzanie domyka luki, które dziś pozwalają produktowi milczeć.

> „Gwiazda przewodnia” (north star) oznacza tu najmniejszy wycinek od początku do końca, którego udane dostarczenie potwierdza cel kamienia milowego — dlatego stoi tak wcześnie, jak pozwalają jego warunki wstępne.

## At a glance

| ID   | Change ID                   | Outcome (user can …)                                                                                                   | Prerequisites | PRD refs                                                   | Status   |
| ---- | --------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------- | ---------------------------------------------------------- | -------- |
| F-04 | out-of-app-alert-channel    | (foundation) kanał alarmowy poza aplikacją: healthchecks.io → Telegram, $0; crony się meldują, API ma nadawcę zdarzeń i sygnał testowy | —             | MS-04, NFR (sygnał awarii przebiegu); audyt C2, C11         | done |
| S-08 | empty-run-signal            | operator dostaje sygnał, gdy przebieg nic nie pobrał albo pominął elementy; etykieta świeżości wtedy stoi              | —             | MS-04, MS-01, NFR (sygnał awarii przebiegu); audyt C1, B5  | done |
| S-09 | attempt-accounting          | awaria dostawcy LLM ani błąd naszego kodu nie zabija postów; post z powtarzalnym błędem technicznym ma limit           | —             | MS-01, MS-03; audyt B2, B3, B9, A1                          | done |
| S-10 | llm-config-fail-fast        | operator dostaje błąd startu przy `LLM_TIMEOUT` bez jednostki; czasy są parsowane jedną regułą                        | —             | MS-01, MS-02; audyt B1, E                                   | done     |
| S-11 | stuck-run-self-heals        | przebieg po martwej instancji i post z wyścigu claim wracają do obiegu bez restartu API                                | —             | MS-01, MS-03; audyt A2, A4                                  | ready    |
| S-12 | reliable-run-notifications  | admin dostaje każde należne powiadomienie o przebiegu, a stan kroków zgadza się ze statusem przebiegu                  | S-08          | MS-04, MS-01, NFR (sygnał awarii przebiegu); audyt A3, A5, A6 | proposed |
| S-13 | missed-night-detection      | operator dowie się, że nocny przebieg albo backup nie wystartował, i widzi dni bez danych                              | F-04          | MS-04, NFR (sygnał awarii przebiegu, kopia bazy); audyt C2, C3 | proposed |
| S-14 | safe-deploy-gate            | merge nie wdroży czerwonego commita, crony czekają na CI, po apply jest kontrola zdrowia, ręczne zmienne nie blokują wdrożeń | —        | MS-02, MS-03; audyt C4, C5, C6, C7                          | done |
| S-15 | backup-check-completeness   | sprawdzenie kopii bazy obejmuje wszystkie tabele i odrzuca kopię pustej bazy                                           | —             | MS-01, MS-03, NFR (kopia sprawdzana odtworzeniem); audyt C9 | done |
| S-16 | wykop-ingest-completeness   | stronicowanie Wykopu nie gubi elementów po cichu, a chwilowy błąd Wykopu nie zabija nocy                               | —             | MS-01, MS-03; audyt B5, B6                                  | done |
| S-17 | pipeline-without-embabel    | przebieg działa bez Embabel, przerwanie dociera do oceny, a testy silnika są skupione i niezależne od kolejności       | S-09          | MS-01, MS-03; audyt E, A7, D                                | proposed |
| S-18 | error-visibility            | błędy wymagające reakcji (ERROR, `/api/ready`, awaria ścieżki alarmowej) docierają do operatora; klucze nie trafiają do logów | F-04     | MS-04; audyt C10, C11, B7                                   | proposed |
| S-19 | ops-docs-debt               | operator czyta prawdziwe runbooki i stan; zbędne zmienne IaC usunięte; otwarty dług operacyjny domknięty               | —             | MS-02, MS-04; audyt F, C12, C13                             | done |

## Streams

Navigation aid — groups items that share a Prerequisites chain. Canonical ordering still lives in the dependency graph below; this table is the proposed reading order across parallel tracks.

| Stream | Theme                   | Chain                                          | Note                                                                                                   |
| ------ | ----------------------- | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| A      | Sygnał w aplikacji      | `S-08` → `S-12`                                | Gwiazda przewodnia; sygnały w dzwonku nie czekają na decyzję o kanale zewnętrznym (F-04).             |
| B      | Kanał poza aplikacją    | `F-04` → `S-13` → `S-18`                        | Kanał rozstrzygnięty (2026-10-20): healthchecks.io → Telegram; przy celu `quality` fundament obserwowalności nie jest odkładany.          |
| C      | Próby i silnik          | `S-09` → `S-17` · `S-10` · `S-11` · `S-16`     | `S-10`, `S-11`, `S-16` są niezależne i równoległe; `S-17` przepisuje silnik po nowej semantyce prób.  |
| D      | Wdrożenia i dane        | `S-14` · `S-15` · `S-19`                       | Niezależne; `S-14` warto zrobić wcześnie, bo chroni każdy kolejny merge tego kamienia.                |

## Baseline

What's already in place in the codebase as of `2026-10-17` (HEAD `5b6eabb`; z mapy projektu i audytu, potwierdzone przez użytkownika).
Foundations below assume these are present and do NOT re-scaffold them.

- **Frontend:** present — SPA Vue z feedem, filtrem, widokiem posta, `/admin`, stroną przebiegu i dzwonkiem (`szczur-radar-web/src/views/`, `src/components/NotificationBell.vue`).
- **Backend / API:** present — Spring Boot 4, silnik przebiegów z kolejką, watchdogiem i odzyskiwaniem przy starcie (`szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/`).
- **Data:** present — Postgres 18, Flyway V1–V9, RAW osobno (`raw_payloads`), warunkowe zapisy stanu (`pipeline/state/`).
- **Auth:** present — sesje JDBC, deny-by-default, token crona dla `/internal/**` (`config/SecurityConfig.java`).
- **Deploy / infra:** present z lukami — IaC (`.railway/railway.ts`), CI (`.github/workflows/ci.yml`), PR env, crony `pipeline-trigger` i `db-backup`; luki: `railway-config` bez czekania na testy, crony bez Wait for CI, brak kontroli zdrowia po apply (audyt C4–C7).
- **Observability:** partial — logi tekstowe, powiadomienia admina w aplikacji (S-06), reguła Railway „deployment crashed” (ustawienie konta, poza IaC); brak kanału poza aplikacją, error trackera, metryk i sygnału „cron nie wystartował” (audyt C2, C11).

## Foundations

### F-04: Kanał alarmowy poza aplikacją

- **Outcome:** (foundation) jest jeden kanał alarmowy poza aplikacją, mieszczący się w limitach kosztów: healthchecks.io (usługa nadzoru cyklicznych zadań, darmowy plan) z alarmem w Telegramie przez @HealthchecksBot, $0. Oba crony meldują start i kod wyjścia z ostatnią linią logu, więc porażka, brak startu albo brak końca na czas (harmonogram + karencja) daje wiadomość; API ma nadawcę zdarzeń dla operatora (`alerts/OperatorAlerts`) i komendę `send-test-alert`, a działanie kanału jest raz sprawdzone na produkcji. Tylko minimalne podłączenie w API: jeden sygnał testowy; konkretne alarmy z API dodaje S-18, dni bez danych S-13.
- **Change ID:** out-of-app-alert-channel
- **PRD refs:** MS-04, NFR (sygnał awarii przebiegu); audyt C2, C11
- **Unlocks:** S-13 (wykrycie nocy, w której cron lub backup w ogóle nie wystartował — aplikacja śpi, więc sama tego nie zgłosi), S-18 (błędy wymagające reakcji poza dzwonkiem)
- **Prerequisites:** —
- **Parallel with:** S-08–S-12, S-14–S-17, S-19
- **Blockers:** —
- **Unknowns:**
  - Jaki kanał (push, e-mail, zewnętrzna usługa „dead-man's switch” — czyli taka, która alarmuje, gdy oczekiwany sygnał NIE przyjdzie) i czy dopuszczamy zewnętrzną usługę przy limitach $10 (Railway) i $10/mies. (OpenRouter)? PRD §Non-Goals wyklucza wymaganą wysyłkę e-maili do użytkowników, ale nie alarmów dla operatora. — Owner: user. Block: no — rozstrzygnięte 2026-10-20: healthchecks.io (dead-man's switch dla cronów, darmowy Hobbyist) → Telegram (@HealthchecksBot), koszt $0; sygnały tylko wychodzące, bez odpytywania produkcji i bez zegarów w API (`context/changes/out-of-app-alert-channel/frame.md`, `plan.md`).
- **Risk:** sekwencjonowane przed S-13 i S-18, bo bez kanału poza aplikacją nie da się zgłosić zdarzenia, którego aplikacja nie widzi (nieodpalony cron, uśpione API); ryzyko: nowa usługa zewnętrzna i jej sekret.
- **Status:** done

## Slices

### S-08: Pusty przebieg daje sygnał

- **Outcome:** operator dostaje sygnał (powiadomienie w aplikacji i niezerowe wyjście crona), gdy przebieg nic nie pobrał albo pominął elementy o nieoczekiwanym kształcie; etykieta „Dane zaktualizowane” nie przesuwa się na takim przebiegu.
- **Change ID:** empty-run-signal
- **PRD refs:** MS-04, MS-01, NFR (sygnał awarii przebiegu); audyt C1, B5
- **Prerequisites:** —
- **Parallel with:** F-04, S-09, S-10, S-11, S-13–S-19
- **Blockers:** —
- **Unknowns:**
  - Co jest „pustym” przebiegiem: 0 pobranych, 0 nowych czy udział pominiętych powyżej progu — i czy pusty dzień na tagu jest w ogóle możliwy (wtedy ostrzeżenie zamiast błędu)? — Owner: user. Block: no (rozstrzyga wywiad `/10x-plan`).
- **Risk:** najpierw, bo to jedyne ustalenie H i łamie NFR; zmienia status przebiegu, `needsAttention`, odpowiedź `/internal`, wyjście crona i regułę świeżości naraz — kontrakt crona trzeba zmienić po obu stronach.
- **Status:** done

### S-09: Awaria dostawcy nie zabija postów

- **Outcome:** awaria dostawcy LLM (seria 5xx/timeoutów, mieszane odmowy flex) zatrzymuje przebieg bez zużywania prób postów, flex przechodzi na tryb standardowy także przy mieszanych błędach, błąd naszego kodu w kliencie nie udaje odmowy modelu, a post z powtarzalnym błędem technicznym ma limit i trafia na listę nieudanych.
- **Change ID:** attempt-accounting
- **PRD refs:** MS-01, MS-03; audyt B2, B3, B9, A1
- **Prerequisites:** —
- **Parallel with:** F-04, S-08, S-10–S-16, S-18, S-19
- **Blockers:** —
- **Unknowns:**
  - Czy awaria dostawcy ma w ogóle zużywać próby, a jeśli nie — jaki próg bezpiecznika zatrzymuje przebieg (liczba kolejnych błędów, udział)? — Owner: user. Block: no. **Rozstrzygnięte (2026-10-20, plan `attempt-accounting`):** awaria dostawcy po ponowieniach nie zużywa prób; bezpiecznik to 3 takie wywołania z rzędu (liczba, nie udział; stała w kodzie), które kończą przebieg `failed`, a posty wracają bez prób; pojedyncza awaria jest błędem technicznym posta, a 3 błędy techniczne z rzędu wstrzymują post (bez automatycznego RETRY, ponowienie przez admina).
- **Risk:** zmienia model „próba = zapisany wynik `failed`” (`PostTransitions.Outcome`) i liczniki przebiegu — wspólna przyczyna czterech ustaleń, więc jedna zmiana zamiast czterech łat; przed S-17, żeby przepisany silnik niósł nową semantykę.
- **Status:** done

### S-10: Zła konfiguracja LLM zatrzymuje start

- **Outcome:** operator dostaje czytelny błąd startu (serwer i `migrate`), gdy `LLM_TIMEOUT` nie ma jednostki albo jest absurdalnie krótki; wszystkie ustawienia czasu w API są parsowane jedną regułą, a enumy dostawcy nie żyją w klasie konfiguracji.
- **Change ID:** llm-config-fail-fast
- **PRD refs:** MS-01, MS-02; audyt B1, E
- **Prerequisites:** —
- **Parallel with:** F-04, S-08, S-09, S-11–S-19
- **Blockers:** —
- **Unknowns:** —
- **Risk:** mały, ale zamyka klasę incydentu z 2026-10-06 (`LLM_TIMEOUT=120` = 120 ms); nowa reguła może odrzucić wartość dziś ustawioną na produkcji — sprawdzić zmienne przed merge'em.
- **Status:** done

### S-11: Utknięty przebieg sam wraca do obiegu

- **Outcome:** przebieg `running` po instancji, która zniknęła bez zamknięcia, oraz post przejęty w wyścigu z odzyskiwaniem wracają do obiegu bez restartu API, więc nocny cron nie dostaje godzinami `409 pipeline_busy`.
- **Change ID:** stuck-run-self-heals
- **PRD refs:** MS-01, MS-03; audyt A2, A4
- **Prerequisites:** —
- **Parallel with:** F-04, S-08–S-10, S-12–S-19
- **Blockers:** —
- **Unknowns:**
  - Jak długo Railway kieruje ruch do starej instancji po starcie nowej i jakie jest okno SIGTERM→SIGKILL? — Owner: operator. Block: no (plan może przyjąć ostrożne założenie).
- **Risk:** dotyka kolejki i odzyskiwania, które działają dziś przy jednej instancji; scenariusze trudne do odtworzenia bez testu na bazie — test wyścigu jest częścią wycinka.
- **Status:** ready

### S-12: Powiadomienia o przebiegach nie giną

- **Outcome:** admin dostaje każde należne powiadomienie o przebiegu także po chwilowym błędzie bazy, decyzja „wymaga uwagi” zgadza się z wynikiem przebiegu, a krok oceny nie jest `succeeded` w przebiegu `failed`.
- **Change ID:** reliable-run-notifications
- **PRD refs:** MS-04, MS-01, NFR (sygnał awarii przebiegu); audyt A3, A5, A6
- **Prerequisites:** S-08
- **Parallel with:** F-04, S-09–S-11, S-13–S-19
- **Blockers:** —
- **Unknowns:** —
- **Risk:** po S-08, bo ten dodaje nowy powód powiadomienia i zmienia `needsAttention`; obie zmiany w tym samym miejscu naraz dałyby konflikt.
- **Status:** proposed

### S-13: Brakująca noc nie przechodzi niezauważona

- **Outcome:** operator dostaje alarm przez kanał z F-04, gdy nocny przebieg albo backup nie wystartował lub nie zgłosił końca na czas, a dni bez pobranych danych są widoczne (lub uzupełniane).
- **Change ID:** missed-night-detection
- **PRD refs:** MS-04, NFR (sygnał awarii przebiegu, kopia bazy); audyt C2, C3
- **Prerequisites:** F-04
- **Parallel with:** S-08–S-12, S-14–S-19
- **Blockers:** —
- **Unknowns:**
  - Czy automatycznie uzupełniać dni starsze niż D-2, czy tylko je pokazywać i zostawić ręczny przebieg zakresu adminowi? — Owner: user. Block: no.
- **Risk:** aplikacja i crony nie mogą same zgłosić, że się nie uruchomiły — dlatego zależy od kanału zewnętrznego; obejmuje też synchronizację NAS → Google Drive tylko wtedy, gdy kanał to umożliwia.
- **Uwaga (F-04, 2026-10-20):** kanał F-04 to healthchecks.io → Telegram. Check każdego crona (`<środowisko>/pipeline-trigger`, `<środowisko>/db-backup`, harmonogram UTC i karencja) już istnieje i alarmuje, gdy noc albo backup nie wystartowały lub nie skończyły się na czas (ok. 03:45 i 04:15 UTC); dla S-13 zostają dni bez danych (pokazanie lub uzupełnianie) i ewentualny nadzór NAS → Google Drive (`deploy-plan.md`, sekcja F-04).
- **Status:** proposed

### S-14: Bezpieczna bramka wdrożeń

- **Outcome:** merge do `main` nie wdroży commita z czerwonymi testami (IaC stosowane dopiero po testach), crony czekają na CI, po apply i deployu jest automatyczna kontrola zdrowia produkcji, a zmienna ustawiona ręcznie nie blokuje kolejnych wdrożeń; logika bramki ma testy.
- **Change ID:** safe-deploy-gate
- **PRD refs:** MS-02, MS-03; audyt C4, C5, C6, C7
- **Prerequisites:** —
- **Parallel with:** F-04, S-08–S-13, S-15–S-19
- **Blockers:** —
- **Unknowns:**
  - Czy deploy wywołany przez `railway config apply` czeka na Wait for CI (audyt C4)? — Owner: operator (żywy Railway). Block: no (kolejność jobów w CI naprawia to niezależnie od odpowiedzi).
- **Risk:** zmienia CI, który bramkuje każdy deploy — błąd tu może zatrzymać wszystkie wdrożenia; warto wcześnie, bo chroni każdy kolejny merge tego kamienia (lekcje Railway IaC w `lessons.md`).
- **Status:** done

### S-15: Kopia bazy sprawdzana w całości

- **Outcome:** codzienne sprawdzenie kopii bazy zna wszystkie tabele z migracji (także `activation_tokens` i `notifications`) i odrzuca kopię bazy, która jest podejrzanie pusta, zanim wyprze dobre kopie z retencji.
- **Change ID:** backup-check-completeness
- **PRD refs:** MS-01, MS-03, NFR (kopia sprawdzana odtworzeniem); audyt C9
- **Prerequisites:** —
- **Parallel with:** F-04, S-08–S-14, S-16–S-19
- **Blockers:** —
- **Unknowns:** —
- **Risk:** mały; dotyka jedynej kopii danych — zmiana musi przejść `db-backup-test` i jeden przebieg w PR env (`BACKUP_IN_ANY_ENVIRONMENT`).
- **Status:** done

### S-16: Wykop nie gubi postów po cichu

- **Outcome:** pobieranie z Wykopu wykrywa niekompletne stronicowanie (zamiast kończyć się „kompletne” przy brakach), a chwilowy błąd Wykopu (429, 5xx, transport) jest ponawiany, zamiast kończyć całą noc `failed`.
- **Change ID:** wykop-ingest-completeness
- **PRD refs:** MS-01, MS-03; audyt B5, B6
- **Prerequisites:** —
- **Parallel with:** F-04, S-08–S-15, S-17–S-19
- **Blockers:** —
- **Unknowns:**
  - Czy wyszukiwarka Wykopu zwraca usunięte elementy i w jakim kształcie? — Owner: team. Block: no (do zbadania w `/10x-research` na prawdziwych odpowiedziach).
- **Risk:** zmienia źródło, ale nie silnik (zgodnie z NFR „nowe źródło = tylko pozyskanie + mapowanie”); ponowienia nie mogą przekroczyć limitów Wykopu ani progu watchdoga.
- **Status:** done

### S-17: Przebieg bez Embabel

- **Outcome:** przebieg pipeline'u działa jako zwykła sekwencja kroków bez Embabel, przerwanie i watchdog docierają do oceny bez „zombie” akcji, a testy silnika są skupione (zapisy stanu na bazie, logika bez Springa) i niezależne od kolejności.
- **Change ID:** pipeline-without-embabel
- **PRD refs:** MS-01, MS-03; audyt E, A7, D
- **Prerequisites:** S-09
- **Parallel with:** F-04, S-08, S-10–S-16, S-18, S-19
- **Blockers:** —
- **Unknowns:**
  - Wyjąć Embabel tylko z przebiegu czy z całego API (autokonfiguracja dostawców, `AssessorAgent`, wymóg `LLM_*` w `migrate`) — i zmiana decyzji w `tech-stack-api.md`? — Owner: user. Block: no (rozstrzyga research zmiany; dawna pozycja Parked z plan-review S-06).
- **Risk:** największa zmiana kamienia — przepisuje rdzeń, który działa na produkcji; po S-09, żeby nie przepisywać semantyki prób dwa razy; wymaga pełnej weryfikacji w PR env z przerwaniem przez deploy.
- **Status:** proposed

### S-18: Błędy wymagające reakcji docierają do operatora

- **Outcome:** błędy wymagające reakcji — linie ERROR (odzyskiwanie, watchdog, zamykanie przebiegu, nieudane powiadomienie), nieudane `/api/ready` z przyczyną w logu — docierają do operatora przez kanał z F-04, a łańcuch przyczyn wyjątków dostawców nie wynosi kluczy do logów.
- **Change ID:** error-visibility
- **PRD refs:** MS-04; audyt C10, C11, B7
- **Prerequisites:** F-04
- **Parallel with:** S-08–S-17, S-19
- **Blockers:** —
- **Unknowns:**
  - Error tracker (zewnętrzna usługa) czy alarm z logów przez kanał F-04 — i jaki koszt jest akceptowalny? — Owner: user. Block: no (wynika z decyzji F-04).
- **Risk:** łatwo zalać operatora alarmami — każdy alarm musi wymagać reakcji; maskowanie sekretów w logach to osobne, łatwe do pominięcia ryzyko.
- **Uwaga (F-04, 2026-10-20):** kanał F-04 to healthchecks.io → Telegram. API ma nadawcę `alerts/OperatorAlerts` (`report(AlertEvent, detail)` do checka `<środowisko>/api-events`: `/fail` z opisem i od razu sukces; nigdy nie rzuca) z jednym zdarzeniem `TEST_SIGNAL`. S-18 dopisuje zdarzenia do `AlertEvent` i podłącza do `OperatorAlerts` ścieżki ERROR; limit healthchecks.io to ≤5 pingów na minutę na check, czyli ≤2 zdarzenia na minutę (`deploy-plan.md`, sekcja F-04).
- **Uwaga (przegląd implementacji F-04, 2026-10-24):** ograniczenia nadawcy, które S-18 musi uwzględnić przy podłączaniu ścieżek: (1) `report()` na przerwanym wątku zwraca `FAILED` bez wysyłki, a `PipelineRunService` i `RunWatchdog` zamykają executory przez `shutdownNow()`, więc zdarzenie z workera przy zamknięciu lub anulowaniu nie wyjdzie; (2) wysyłka jest synchroniczna i blokuje wywołującego do ok. 2 × (5 s połączenie + 10 s żądanie); (3) bean `OperatorAlerts` istnieje tylko w serwerze (`@ConditionalOnWebApplication`), więc bean wspólny z kontekstem komend (`migrate`) bierze go przez `ObjectProvider` albo sam jest tylko webowy; (4) `HealthchecksAlerts.report` ma `@Nullable detail`, a `OperatorAlerts.report` nie — wyrównać przy pierwszej edycji pakietu (`context/changes/out-of-app-alert-channel/reviews/impl-review.md`, F8 i F10).
- **Status:** proposed

### S-19: Dokumenty operatora mówią prawdę

- **Outcome:** operator czyta aktualne runbooki i stan: `deploy-plan.md` bez nieaktualnych statusów (S-02, S-04, S-06) i linków do przeniesionych zmian, prawdziwe pomiary JVM, `infrastructure.md` bez LangGraph; zbędne zmienne IaC (`RAILPACK_JDK_VERSION`, `DATABASE_URL`, `JWT_SECRET`) usunięte bez czerwonej bramki; ostrzeżenia Hikari (#36) rozstrzygnięte; nieaktualne issues (#51) zamknięte.
- **Change ID:** ops-docs-debt
- **PRD refs:** MS-02, MS-04; audyt F, C12, C13
- **Prerequisites:** —
- **Parallel with:** F-04, S-08–S-18
- **Blockers:** —
- **Unknowns:** —
- **Risk:** niski; usunięcie zmiennych wymaga kolejności „najpierw operator w Railway, potem plik”, inaczej bramka odmówi `variable.delete`; `context/archive/` zostaje nietknięte (archiwum jest niezmienne).
- **Status:** done

## Backlog Handoff

| Roadmap ID | Change ID                   | Suggested issue title                                                     | Ready for `/10x-plan` | Notes                                                    |
| ---------- | --------------------------- | ------------------------------------------------------------------------- | --------------------- | -------------------------------------------------------- |
| F-04       | out-of-app-alert-channel    | F-04: kanał alarmowy poza aplikacją                                        | yes                   | W realizacji; kanał rozstrzygnięty: healthchecks.io → Telegram |
| S-08       | empty-run-signal            | S-08: pusty przebieg daje sygnał (gwiazda przewodnia M-2)                  | yes                   | Run `/10x-plan empty-run-signal`                         |
| S-09       | attempt-accounting          | S-09: awaria dostawcy LLM nie zużywa prób postów                           | yes                   | Run `/10x-plan attempt-accounting`                       |
| S-10       | llm-config-fail-fast        | S-10: zła konfiguracja LLM zatrzymuje start                                | yes                   | Run `/10x-plan llm-config-fail-fast`                     |
| S-11       | stuck-run-self-heals        | S-11: utknięty przebieg sam wraca do obiegu                                | yes                   | Run `/10x-plan stuck-run-self-heals`                     |
| S-12       | reliable-run-notifications  | S-12: powiadomienia o przebiegach nie giną                                 | no                    | Po S-08                                                  |
| S-13       | missed-night-detection      | S-13: brakująca noc nie przechodzi niezauważona                            | no                    | Po F-04                                                  |
| S-14       | safe-deploy-gate            | S-14: bezpieczna bramka wdrożeń                                            | yes                   | Run `/10x-plan safe-deploy-gate`; warto wcześnie          |
| S-15       | backup-check-completeness   | S-15: kopia bazy sprawdzana w całości                                      | yes                   | Run `/10x-plan backup-check-completeness`                |
| S-16       | wykop-ingest-completeness   | S-16: Wykop nie gubi postów po cichu                                       | yes                   | Run `/10x-plan wykop-ingest-completeness`                |
| S-17       | pipeline-without-embabel    | S-17: przebieg bez Embabel                                                 | no                    | Po S-09                                                  |
| S-18       | error-visibility            | S-18: błędy wymagające reakcji docierają do operatora                      | no                    | Po F-04                                                  |
| S-19       | ops-docs-debt               | S-19: dokumenty operatora mówią prawdę                                     | yes                   | Run `/10x-plan ops-docs-debt`                            |

## Open Roadmap Questions

1. **Czy awaria dostawcy LLM w ogóle zużywa próby postów?** — decyzja produktowa z audytu; od niej zależy semantyka prób w S-09 i to, co przepisze S-17. Owner: user. Block: — (rozstrzyga wywiad `/10x-plan attempt-accounting`). **Rozstrzygnięte (2026-10-20, plan `attempt-accounting`):** nie zużywa; 3 awarie z rzędu zatrzymują przebieg (bezpiecznik), szczegóły w S-09.
2. **Weryfikacja produkcji z M-1:** lokalizacja bazy dla filtra S-04 (runbook z issue #17, krok 2: `C`/`POSIX` po cichu psuje `KĘTY`→`Kęty`) i pierwsza noc S-06 (runbook z issue #19, 2026-10-18). Owner: operator. Block: — (runbooki w `deploy-plan.md`; wynik `C`/`POSIX` dałby nowy wycinek).
3. **Ustalenia z żywego Railway** (audyt, Open Questions 2–7): czy deploy po `config apply` czeka na CI (S-14); czy merge samej dokumentacji po #58 jest `SKIPPED`; czy reguła „deployment crashed” obejmuje crash loop API (F-04); okno SIGTERM→SIGKILL (S-11); aktualne ostrzeżenia Hikari (S-19); przyczyna przebiegu „trwającego” 22 h z 2026-10-09. Owner: operator. Block: — (każdy wycinek przyjmie ostrożne założenie, jeśli odpowiedzi nie będzie).
4. **Kanał i próg dla FR-025 (powiadomienia o obiecującym trendzie)** — nie ustalono jeszcze, czym jest "obiecujący trend" liczbowo ani gdzie ma trafiać powiadomienie; F-04 może wybrać kanał, który później obsłuży też FR-025. Owner: użytkownik. Block: — (FR-025 odroczone).
5. **Budżet kosztów LLM na przebieg** — guardrail PRD każe minimalizować koszt zapytań, ale nie podaje pułapu (na dzień ani na post). Twardy pułap od 2026-10-11 (F-01): limit $10/mies. na kluczu OpenRouter produkcji; budżet na przebieg nadal nieustalony. Dotyczy progu bezpiecznika w S-09 i wczesnego ostrzeżenia o koszcie. Owner: użytkownik. Block: — (nie blokuje planowania).

## Parked

- **FR-008 / FR-011b — sortowanie po dacie, źródle i polach oceny** — Why parked: nice-to-have; chronologia wystarcza na start (cel `speed`).
- **FR-016 / FR-017 / FR-019 — panel kont w aplikacji + wysyłka maila z zaproszeniem** — Why parked: nice-to-have; CLI (S-07) daje ten sam efekt; PRD §Non-Goals wyklucza wymaganą wysyłkę e-maili.
- **FR-020 — własna nazwa wyświetlana** — Why parked: nice-to-have; e-mail wystarcza w MVP.
- **FR-023 — Reddit jako drugie źródło** — Why parked: nice-to-have; kolejny kamień milowy funkcjonalny (po M-2) — kontrakt V2 już obejmuje Reddit po normalizacji (`statement` w języku posta), a S-02 projektuje wspólny model źródeł zgodnie z NFR.
- **FR-024 / FR-025 — wykres giełdowy z trendem sygnałów i powiadomienia o trendzie** — Why parked: nice-to-have, poza MVP; wymaga integracji danych giełdowych, rozpoznawania spółek i rozstrzygnięcia Open Question 4.
- **FR-029 — automatyczne wygasanie krótkich sesji** — Why parked: nice-to-have; krótki timeout uciążliwy przy czytaniu. F-03 stosuje 30 dni bezczynności z odnawianiem ciasteczka; krótsze limity pozostają odroczone.
- **FR-031 — eksport widocznej listy do CSV/JSON** — Why parked: nice-to-have, nie blokuje głównego przepływu.
- **FR-032 — „Ask szczur” (pytanie do LLM o wybrany sygnał)** — Why parked: nice-to-have; dodatkowy koszt LLM wbrew guardrailowi kosztów.
- **Przetwarzanie komentarzy pod postami** — Why parked: PRD §Non-Goals (kontrakt V2 zachowuje typ `comment` i kontekst rodzica w kształcie wejścia — gotowe na przyszłość, nieużywane w MVP).
- **Rozpoznawanie spółek, automatyczna decyzja „wartościowy sygnał”, nowość informacji, trend, zaangażowanie, raporty** — Why parked: PRD v2 §Non-Goals; kontrakt V2 §15 umieszcza je poza oceną LLM jako osobne, dalsze etapy.
- **2FA, logowanie przez zewnętrznego dostawcę, samodzielny reset hasła, dodatkowe role** — Why parked: PRD §Non-Goals.
- **Wysoka dostępność / multi-region, logika backupu w kodzie aplikacji** — Why parked: PRD §Non-Goals; kopię bazy robi osobny serwis infrastruktury (cron `db-backup`, F-01), nie API ani pipeline.
- **Lekkie modele ML, embeddingi, klasyfikatory w potoku** — Why parked: `shape-notes.md` §Forward: technical-roadmap — kierunek na przyszłość, nie zakres MVP.
- **LLM hardening: kierunek i stanowisko (w tym ironia)** — Why parked: nice-to-have po MVP. Pomiar F-02 (`context/archive/2026-09-27-assessment-contract-harness/reports/gold-2026-10-05.md`): żaden z 12 zmierzonych modeli/wariantów (pełny wzorzec lub próbka 30 postów; dodany później GPT-6.1 Sol też nie) nie spełnił progów kierunku celów i sprzecznych kierunków — progi złagodzono do ≥70 % / ≤10 % (decyzja użytkownika 2026-10-06). Ironia (wg etykiet użytkownika 15 ze 120 postów) obniża trafność kierunku, ale większość sprzeczności pada na postach dosłownych (pilot 9 w `context/archive/2026-09-27-assessment-contract-harness/frame.md`; Jev nie wykrywa ironii); więcej rozumowania nie pomagało. Cel: powrót do progów ≥75 % / ≤3 %. Tor: większy organiczny polski wzorzec z osobnym zbiorem kontrolnym → kierunek → ironia → stanowisko dosłowne vs zamierzone → ablacje kontekstu i promptu → porównanie modeli → regresja kosztu. Źródła (niezweryfikowane): `context/foundation/llm-assessment/hardening-sources.md`.
- **Ochrona przed dryfem modelu (alarm na rosnący udział `schema_violation` / `fabricated_evidence`, cicha zmiana modelu u dostawcy)** — Why parked: z audytu M-2 (B4); poza zakresem M-2, bo wymaga punktu odniesienia jakości, który buduje dopiero „LLM hardening”; `RecordingsReplayTest` w CI łapie dziś dryf parsera, schematu i walidatora.
- **Izolacja źródeł i świeżość per źródło** — Why parked: z audytu M-2 (sekcja E): dziś awaria jednego źródła zawala krok pobrania wszystkich, a etykieta świeżości jest wspólna; przy jednym źródle (Wykop) to bez znaczenia. Pierwszy wycinek kamienia milowego z Redditem (FR-023).
- **Porządki granic pakietów: cykl `pipeline` ↔ `notifications`, dwa mechanizmy zapisu `posts` (JPA i SQL), konwertery zależne od `pipeline.run`, duplikaty reguł („najnowsza ocena `ok`”, predykat resztek)** — Why parked: z audytu M-2 (sekcja E, L); nie wpływają dziś na niezawodność. Część zniknie przy S-17; reguła „najnowsza ocena `ok`” idzie z pozycją o wydajności filtra.
- **Drobne ryzyka z audytu M-2 (L): przebieg `queued` przy zamkniętym executorze i koniec zakresu `23:59:59` (A8), brak limitów schematu dla OpenRouter i 3 płatne wywołania przy deterministycznym naruszeniu kontraktu (B8), dodatkowa próba po przerwaniu przy przejściu flex→standard (B10), offset w powtórzonej jesiennej godzinie (B11), okno ponowień Caddy 30 s (C14), fałszywy alarm crona po 90 min (C8)** — Why parked: niska waga; wracają, gdy dotknie ich wycinek z tego samego obszaru.
- **Ocena obrazków w postach (model multimodalny)** — Why parked: nice-to-have po MVP, do sprawdzenia, czy ma sens. PRD v2 i kontrakt V2 oceniają wyłącznie tekst (`prd.md:258`); w pilotażu #gielda ~44% wpisów ma zdjęcie, a część ma sens tylko w obrazku (`frame.md`, „Znane luki”). Zestaw wzorcowy F-02 (flaga `media.photo`, etykiety `unclear` z powodu obrazka, raport fazy 6) pokaże skalę; wdrożenie wymagałoby zmiany PRD, kontraktu (V3: cytat/dowód z obrazka nie jest podciągiem tekstu), wyboru modeli z wejściem obrazkowym i ponownego pomiaru, a koszt wejścia rośnie o tokeny obrazków.
- **Wydajność filtra feedu przy dużej liczbie postów (TODO na przyszłość)** — Why parked: dziś zbędne (~300 postów, zapytania filtra w milisekundach). Pomiar z impl-review S-04 (folder zmiany `filter-by-assessment`, `reviews/impl-review.md`, F6): przy 100k ocenionych postów zapytanie strony z rzadkim filtrem trwa 44 ms, ale zliczanie wyników 0,6–0,95 s (sekwencyjny skan `posts`, najnowsza ocena `ok` i `jsonb_array_elements` dla każdego wiersza); filtr bez trafień płaci ten sam skan. Przy ~270 postach dziennie to ok. rok. TODO: po ~20k postów zmierzyć `EXPLAIN ANALYZE` na produkcji; jeśli zwalnia, projekcja celów (kolumna `latest_ok_assessment_id` w `posts` albo tabela `post_targets`) z indeksem `pg_trgm` — usunie też drugą kopię reguły „najnowsza ocena `ok`” (`PostRepository.findMatching` i `AssessmentRepository.findLatestOk`). Osobna zmiana z pomiarem.
- **Drugi runner CI w VM w Proxmoxie (równoległe runy bez PC)** — Why parked: z `self-hosted-pr-runner` (#30, 2026-10-19). CI idzie na pulę self-hosted runnerów z etykietą `szczur-ci` (`tools/ci-runner/`): jeden runner w VM w Proxmoxie zawsze i VM-ki na PC w czasie developmentu; bez PC Proxmox wykonuje joby po kolei (run ok. 7 min zamiast ok. 2). Drugi runner w tej samej VM wymaga najpierw unikalnych portów i nazw kontenerów w smoke testach (`8080`, `8000`, `5432` na hoście, kontenery `web`/`api`); alternatywa bez zmian w `ci.yml`: druga VM w Proxmoxie z własnym Dockerem.
- **Losowe padanie budowy obrazów Dockera na self-hosted runnerach (cache budowania)** — Why parked: zauważone w S-08 `empty-run-signal` (PR #80, 2026-10-19), do zrobienia w kolejnej fali. Joby budujące obraz padają losowo na VM-kach `ci-pc-*` z `failed to prepare extraction snapshot "…": parent snapshot sha256:… …` przy rozpakowaniu obrazu do lokalnego magazynu Dockera (obraz zbudowany, warstwy `CACHED`): `api container smoke test` na `ci-pc-1-1` (run 39286873262) i `db backup test` na `ci-pc-2-1` (run 39295084707, podejście 1); ponowienie jobu przechodzi. Prawdopodobnie cache budowania wskazuje na usuniętą warstwę rodzica (np. po czyszczeniu Dockera na VM). Do sprawdzenia w `tools/ci-runner/`: czyszczenie cache budowania (`docker builder prune -af`) przed jobem albo budowa obrazów w CI bez cache; do tego czasu pomaga `gh run rerun <id> --failed`.
- **Komunikat, gdy nie udało się odczytać daty aktualizacji danych** — Why parked: nice-to-have po MVP (decyzja użytkownika 2026-10-13, S-05 `daily-scheduled-run-freshness`, faza 4). Dziś przy błędzie `GET /api/freshness` etykieta „Dane zaktualizowane: …” nad feedem po prostu znika, a lista działa (`FeedView.vue`; `context/archive/2026-10-12-daily-scheduled-run-freshness/ui/README.md`), więc użytkownik nie odróżni „nie udało się sprawdzić” od braku etykiety. Pomysł: osobny, neutralny komunikat treści w miejscu etykiety (nie czerwony alert, bo posty są w porządku), np. „Nie udało się sprawdzić daty aktualizacji.”, ewentualnie z ponowieniem.
- **Unieważnianie sesji wersją poświadczeń** — Why parked: po MVP, z przeglądu implementacji S-07 (`cli-user-onboarding`, F1). Logowanie, które sprawdziło hasło przed zatwierdzeniem `reset-password`, zapisuje sesję już po `DELETE` resetu, więc ta sesja działa aż do aktywacji (najwyżej `ACTIVATION_LINK_TTL`, domyślnie 12 h); od S-07 kończy ją aktywacja. Pełne zamknięcie: wersja poświadczeń konta (np. `password_changed_at`), zapisana w sesji przy logowaniu i sprawdzana przy każdym uwierzytelnionym żądaniu — migracja i sprawdzenie w łańcuchu filtrów.
- **Przybliżony czas zakończenia trwającego przebiegu** — Why parked: nice-to-have po MVP (decyzja użytkownika 2026-10-13, po pierwszym przebiegu crona na produkcji). Buduje na postępie przebiegu z S-06 (dopisek o monitorowaniu): bez liczników postępu nie ma z czego liczyć. Pomysł: w `/admin` przy przebiegu `running` szacowany koniec i odliczanie, np. średni czas oceny posta z ostatnich przebiegów (z `pipeline_run_steps` i `assessments`) × liczba postów do oceny + typowy czas pobrania, albo tempo bieżącego przebiegu, gdy ma już kilka ocen. Niepewność: tier flex, ponowienia i powroty do standardu mocno wydłużają pojedyncze oceny, więc to przybliżenie, nie obietnica.

## Milestone History

- **M-1: MVP — ocenione sygnały z Wykopu dla zaproszonych osób** (`mvp-wykop-signal-radar`) — closed 2026-10-17. Wszystkie konieczne FR i historyjki US-01, US-02 dostarczone na produkcji (F-01–F-03, S-01–S-07); domknięcie runbooków produkcji S-04 (#17) i S-06 (#19) przeniesione do Open Roadmap Questions M-2.

## Done

(Pusta przy pierwszym generowaniu. Wpisy dopisuje `/10x-archive` przy archiwizacji zmiany o pasującym `Change ID`.)

- **S-01: operator może utworzyć pierwsze konto administratora przez CLI; admin może zalogować się e-mailem i hasłem oraz wylogować; niezalogowany użytkownik trafia wyłącznie na ekran logowania.** — Archived 2026-09-29 → `context/archive/2026-09-27-admin-bootstrap-and-login/`. Lesson: —.
- **F-03: (foundation) `szczur-radar-api` działa na Javie (Spring Boot) i obsługuje funkcje S-01 przez dotychczasowe API dla SPA — logowanie i wylogowanie, ochrona każdej trasy (komplet testów autoryzacji), CLI do utworzenia pierwszego admina oraz sondy `/api/health` i `/api/ready`. Sesje w ciasteczku `sr_session` są przechowywane przez Spring Session JDBC, przeżywają restart API, wygasają po 30 dniach bezczynności z odnawianiem ciasteczka i są unieważniane przez usuwanie sesji użytkownika. Migracje Flyway działają według fix-forward. Embabel rejestruje szkieletowego agenta i wybrany model z prawdziwym kluczem, bez wywołań LLM. Całość zweryfikowana na produkcji po wyczyszczeniu bazy i ponownym utworzeniu admina; kod Pythona usunięty, dokumenty stacku i roadmapa zaktualizowane.** — Archived 2026-10-03 → `context/archive/2026-10-01-java-api-rewrite/`. Lesson: —.
- **F-02: (foundation) kontrakt oceny LLM V2 (trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z cytatem, flagi niepewności) jest źródłem prawdy; wejście i wyjście są odwzorowane jako zamknięte modele odrzucające obce pola; walidator sprawdza reguły kontraktu (cytaty jako fragmenty po normalizacji białych znaków, referencje, reguły stanowiska) z jawną klasyfikacją błędów; wpisy bez treści są odsiewane bez wywołania modelu; model domyślny wybrany pomiarem GPT-6 Luna przez OpenRouter, DeepSeek Flash bezpośrednio u dostawcy i Gemini 3.1 Flash-Lite przez Google AI Studio spełnia progi jakości na zestawie wzorcowym prawdziwych postów oznaczonych przez użytkownika.** — Archived 2026-10-06 → `context/archive/2026-09-27-assessment-contract-harness/`. Lesson: —.
- **S-02: zalogowany użytkownik widzi chronologiczną, stronicowaną listę postów z Wykopu (#gielda) z zadanego zakresu dat, bez duplikatów, każdy z oceną LLM zgodną z kontraktem V2 (trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z cytatem, flagi niepewności); przebieg jest uruchamiany ręcznie przez operatora.** — Archived 2026-10-09 → `context/archive/2026-10-06-first-assessed-wykop-feed/`. Lesson: Akcje Embabel domyślnie się ponawiają — akcje ze skutkami ubocznymi oznaczaj FIRE_ONCE. Obejmuje też FR-022 (ręczne uruchomienie przebiegu z `/admin`; stwierdzone 2026-10-13, wpis usunięty z `## Parked`).
- **F-01: (foundation) produkcja jest gotowa na zbieranie prawdziwych danych: plan Railway Hobby z limitami kosztów $5 (alert) / $10 (twardy); wolumen Postgresa 5 GB; codzienna kopia logiczna (`pg_dump` z serwisu cron `db-backup`, tylko na produkcji) sprawdzana pełnym odtworzeniem, w buckecie Railway (14 dziennych + 12 miesięcznych) i na NAS operatora (Synology Cloud Sync); restore drill przećwiczony do odpytywalnej bazy; limit $10/mies. na kluczu OpenRouter produkcji (wyczerpany limit zatrzymuje przebieg bez zużycia prób postów); sekret LLM produkcji zapieczętowany i niedostępny w środowiskach PR; klucze Wykopu zapieczętowane na produkcji, te same klucze aplikacji świadomie używane w PR env.** — Archived 2026-10-12 → `context/archive/2026-10-11-production-data-gate/`. Lesson: Railway Hobby nie ma kopii bazy — sprawdź tabelę funkcji planu i każdą kopię logiczną odtwarzaj w całości.
- **S-05: przepływ uruchamia się sam codziennie o ustalonej godzinie na produkcji, a użytkownik widzi nad listą datę i godzinę ostatniej UDANEJ aktualizacji danych.** — Archived 2026-10-13 → `context/archive/2026-10-12-daily-scheduled-run-freshness/`. Lesson: —.
- **S-07: admin może przez CLI utworzyć konto z rolą i dostać jednorazowy, wygasający link aktywacyjny; zaproszony może raz ustawić hasło i się zalogować; operator może wygenerować nowy link (unieważniając poprzedni) i odzyskać dostęp admina tym samym mechanizmem.** — Archived 2026-10-13 → `context/archive/2026-10-12-cli-user-onboarding/`. Lesson: —.
- **S-03: użytkownik może otworzyć post i zobaczyć czytelną, przetworzoną ocenę (trafność, cele ze stanowiskiem autora, tezy/opinie/fakty/pytania/działania z dosłownym cytatem, flagi niepewności), surowe dane źródłowe pod opcjonalnym przełącznikiem oraz link do oryginału na Wykopie.** — Archived 2026-10-13 → `context/archive/2026-10-12-post-detail-view/`. Lesson: —.
- **S-04: użytkownik może zawęzić listę postów po spółce/temacie (dopasowanie tekstowe do fragmentu wskazującego cel i rodzaju celu) oraz po stanowisku autora wobec tego celu (np. pozytywne / negatywne); brak wyników pokazuje czytelny stan pusty.** — Archived 2026-10-13 → `context/archive/2026-10-12-filter-by-assessment/`. Lesson: —.
- **S-06: admin widzi historię przebiegów (data/godzina, status, wynik) i jednoznaczny sygnał nieudanego lub częściowo nieudanego przebiegu; użytkownik z rolą `user` nie ma do tego dostępu.** — Archived 2026-10-17 → `context/archive/2026-10-15-pipeline-run-history-dashboard/`. Lesson: —.
- **S-14: merge do `main` nie wdroży commita z czerwonymi testami (IaC stosowane dopiero po testach), crony czekają na CI, po apply i deployu jest automatyczna kontrola zdrowia produkcji, a zmienna ustawiona ręcznie nie blokuje kolejnych wdrożeń; logika bramki ma testy.** — Archived 2026-10-19 → `context/archive/2026-10-18-safe-deploy-gate/`. Lesson: —.
- **S-15: codzienne sprawdzenie kopii bazy zna wszystkie tabele z migracji (także `activation_tokens` i `notifications`) i odrzuca kopię bazy, która jest podejrzanie pusta, zanim wyprze dobre kopie z retencji.** — Archived 2026-10-18 → `context/archive/2026-10-18-backup-check-completeness/`. Lesson: —.
- **S-10: operator dostaje czytelny błąd startu (serwer i `migrate`), gdy `LLM_TIMEOUT` nie ma jednostki albo jest absurdalnie krótki; wszystkie ustawienia czasu w API są parsowane jedną regułą, a enumy dostawcy nie żyją w klasie konfiguracji.** — Archived 2026-10-18 → `context/archive/2026-10-18-llm-config-fail-fast/`. Lesson: —.
- **S-08: operator dostaje sygnał (powiadomienie w aplikacji i niezerowe wyjście crona), gdy przebieg nic nie pobrał albo pominął elementy o nieoczekiwanym kształcie; etykieta „Dane zaktualizowane” nie przesuwa się na takim przebiegu.** — Archived 2026-10-19 → `context/archive/2026-10-18-empty-run-signal/`. Lesson: —.
- **S-19: operator czyta aktualne runbooki i stan: `deploy-plan.md` bez nieaktualnych statusów (S-02, S-04, S-06) i linków do przeniesionych zmian, prawdziwe pomiary JVM, `infrastructure.md` bez LangGraph; zbędne zmienne IaC (`RAILPACK_JDK_VERSION`, `DATABASE_URL`, `JWT_SECRET`) usunięte bez czerwonej bramki; ostrzeżenia Hikari (#36) rozstrzygnięte; nieaktualne issues (#51) zamknięte.** — Archived 2026-10-23 → `context/archive/2026-10-20-ops-docs-debt/`. Lesson: —.
- **S-09: awaria dostawcy LLM (seria 5xx/timeoutów, mieszane odmowy flex) zatrzymuje przebieg bez zużywania prób postów, flex przechodzi na tryb standardowy także przy mieszanych błędach, błąd naszego kodu w kliencie nie udaje odmowy modelu, a post z powtarzalnym błędem technicznym ma limit i trafia na listę nieudanych.** — Archived 2026-10-23 → `context/archive/2026-10-20-attempt-accounting/`. Lesson: —.
- **S-16: pobieranie z Wykopu wykrywa niekompletne stronicowanie (zamiast kończyć się „kompletne” przy brakach), a chwilowy błąd Wykopu (429, 5xx, transport) jest ponawiany, zamiast kończyć całą noc `failed`.** — Archived 2026-10-24 → `context/archive/2026-10-20-wykop-ingest-completeness/`. Lesson: —.
- **F-04: (foundation) jest jeden kanał alarmowy poza aplikacją, mieszczący się w limitach kosztów: healthchecks.io (usługa nadzoru cyklicznych zadań, darmowy plan) z alarmem w Telegramie przez @HealthchecksBot, $0. Oba crony meldują start i kod wyjścia z ostatnią linią logu, więc porażka, brak startu albo brak końca na czas (harmonogram + karencja) daje wiadomość; API ma nadawcę zdarzeń dla operatora (`alerts/OperatorAlerts`) i komendę `send-test-alert`, a działanie kanału jest raz sprawdzone na produkcji. Tylko minimalne podłączenie w API: jeden sygnał testowy; konkretne alarmy z API dodaje S-18, dni bez danych S-13.** — Archived 2026-10-24 → `context/archive/2026-10-20-out-of-app-alert-channel/`. Lesson: —.
