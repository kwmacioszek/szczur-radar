---
project: "Szczur Radar"
version: 4
status: draft # draft | active | locked
created: 2026-09-27
updated: 2026-10-31
prd_version: 2
main_goal: quality
top_blocker: decisions # 7 pytań do właściciela o Reddita i oceny (destylacja domeny, Q-01–Q-07): subreddity i dostęp, oznaczanie źródła, świeżość per źródło, budżet LLM, jakość po angielsku, mapowanie url+selftext
milestone_id: second-source-on-hardened-core
milestone_seq: 3
milestone_status: open
---

# Roadmap: Szczur Radar

> Derived from (M-3) `context/foundation/prd.md` (v2, kolejna transza) + audyt `context/changes/post-m2-audit/research.md` + destylacja `context/domain/domain-distillation.md` + mapa projektu `context/map/repo-map.md` (2026-10-27). M-2 derived from the user's milestone description + `context/map/repo-map.md` (mapa projektu, 2026-10-17) + `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` (audyt diagnostyczny pipeline'u i operacji, HEAD `5b6eabb`) + PRD v2 NFRs + auto-researched codebase baseline (z mapy i audytu). M-1 derived from `context/foundation/prd.md` (v2) + `context/foundation/llm-assessment/` + `shape-notes.md` + `tech-stack-api.md` / `tech-stack-web.md` / `infrastructure.md` / `deploy-plan.md`.
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

> v3.3 (2026-10-24): S-12 (`reliable-run-notifications`) **zaparkowany** (decyzja użytkownika po `/10x-frame`, `context/archive/2026-10-24-reliable-run-notifications/frame.md`): A3, A5, A6 znane tylko z audytu, bez incydentu; zguba powiadomienia wymaga awarii bazy w oknie milisekund po commicie, przebiegi idą prawie tylko z crona (kod wyjścia, Railway, healthchecks), a S-18 zrobi z nieudanego powiadomienia alarm. A5 (nieudany zapis licznika, dziś tylko WARN) przeszedł do zakresu S-18; A6 i reszta S-12 w `## Parked` z warunkiem powrotu. Status `parked` nie blokuje zamknięcia M-2.

> v3.4 (2026-10-25): S-13 (`missed-night-detection`) zakończone: dni bez danych widoczne w `/admin`, zakres wpisywany do formularza ręcznego przebiegu (PR #106, squash `d0b099c`, weryfikacja produkcji w issue #72). Niewiadoma S-13 rozstrzygnięta: tylko pokazywać, bez automatycznego uzupełniania i bez nowego alarmu. Do `## Parked` dopisane dwie odłożone poprawki z przeglądu S-13 (F2, F5) i nadzór synchronizacji NAS → Google Drive, który S-13 zostawił poza zakresem.

> v3.5 (2026-10-25): S-18 (`error-visibility`) zakończone: przebieg wymagający uwagi i awarie ścieżek sygnału (powiadomienie, odzyskiwanie, zamknięcie przebiegu, watchdog, sprzątanie sierot, liczniki A5) dają operatorowi wiadomość w Telegramie przez kanał F-04; klucze dostawców poza logowanymi przyczynami (B7); przyczyna 503 z `/api/ready` w logu (C10) (PR #107, squash `6dc24ee`, weryfikacja produkcji w issue #77). Niewiadoma S-18 rozstrzygnięta: bez error trackera i bez alarmu z każdej linii ERROR, tylko jawne zdarzenia `AlertEvent` przez kanał F-04, $0. Do `## Parked` dopisane dwie odłożone poprawki z przeglądu S-18 (F4, F5).

> v3.6 (2026-10-27): S-17 (`pipeline-without-embabel`) zakończone: przebieg to sekwencja kroków w Javie na wątku kolejki, bez Embabel w całym API (Spring AI 2.0.1 wprost), przerwanie dociera do kroku (watchdog `cancel(true)`, `shutdownNow()`), krok `assess` zatrzymany przez dyspozytor kończy się `failed` (A6), checkpointy w `store`/`fetch` (A7), watchdog odporny na błąd alertu (F5 z S-18), bezpiecznik na trzy niesklasyfikowane błędy klienta LLM (F7 z S-09), testy silnika skupione i w losowej kolejności (MS-03); po przeglądach zatrzymany przebieg nie daje fałszywego ERROR ani alertu `RUN_CLOSE_FAILED`. PR #110, squash `e869a07`, weryfikacja produkcji 2026-10-27 (issue #76): start 5,6 s bez linii Embabel, pierwsze obudzenie 5,98 s (przed: 6,9–7,9 s), RETRY i RANGE na nowym silniku bez ERROR; pierwsza noc crona pod nadzorem F-04. Weryfikacja w PR env `szczur-radar-pr-110` w `deploy-plan.md`, S-17. Do Parked: skrócenie obudzenia (Spring AOT, flagi JVM, AOT cache JDK 25).

> v3.7 (2026-10-27): M-2 zamknięty (`/10x-roadmap`): F-04, S-08–S-11 i S-13–S-19 `done`, S-12 `parked` (nie blokuje, v3.3); wpis w `## Milestone History`. M-3 jeszcze nieotwarty — decyzja użytkownika: najpierw odświeżona mapa projektu (`/10x-repo-map --replace`) i audyt zawężony pod kolejny kamień (drugie źródło, silnik po S-17, ustalenia S-14 z issue #73), potem `/10x-roadmap` z PRD v2 (kolejna transza) i raportem audytu jako źródłami.

> v4 (2026-10-27): otwarty M-3 „Drugie źródło na utwardzonym rdzeniu” (`/10x-roadmap`) z PRD v2 (FR-023), audytu `post-m2-audit` i destylacji domeny; ramy od użytkownika: cel `quality`, gwiazda przewodnia S-20 (izolacja źródeł), główne ryzyko `decisions`. Wycinki S-20–S-29; bez fundamentów. Poprawki z audytu w treści roadmapy: S-17 zamknęło A6 (wpis Parked S-12 poprawiony), a A7 i F5 tylko częściowo (nota v3.6 zawyża: A7 zostaje dla przebiegu przerwanego przez odzyskiwanie nowszej instancji, F5 dla linii ERROR przed alarmem watchdoga). Ustalenia niskiej wagi z audytu w `## Parked`. Z `## Parked` usunięte pozycje, które weszły do M-3: FR-023 (→ S-27) i izolacja źródeł ze świeżością per źródło (→ S-20, S-29). Kolejny kamień M-4 (kandydat): nowe UI i UX — wpis w `## Parked` i propozycje układu w `context/foundation/m4-ui-ux-proposals.md`.

> v4.1 (2026-10-27): kolejność kamieni po M-3 zmieniona decyzją użytkownika: M-4 (kandydat) to weryfikacja i regresja (`context/foundation/verification-scope.md`), a nowe UI/UX przesuwa się na M-5 (kandydat) razem z testami E2E na Playwright (`context/foundation/ui-ux-proposals.md`, dawniej `m4-ui-ux-proposals.md`; makiety w `docs/visuals/ui-ux-proposals/`). Dwa wpisy w `## Parked`; M-3 bez zmian.
- **S-29: użytkownik widzi przy każdym poście, z którego źródła pochodzi, a etykieta „Dane zaktualizowane” nie przesuwa się po przebiegu, który nie objął danego źródła — zgodnie z decyzją, czy świeżość jest wspólna, czy per źródło.** — Archived 2026-10-31 → `context/archive/2026-10-30-source-aware-feed/`. Lesson: —.

## Milestone

**M-3: Drugie źródło na utwardzonym rdzeniu** — Status: open

- **Intent:** Awaria albo cisza jednego źródła nie psuje drugiego, ślepe punkty z audytu po M-2 są domknięte (bramka wdrożeń, reguły w kilku miejscach, niestabilne testy silnika), a posty z wybranych subredditów trafiają do tego samego feedu i tej samej oceny co Wykop — z jakością i kosztem zmierzonymi, zanim pójdą na produkcję.
- **Source materials:** `context/foundation/prd.md` (v2, kolejna transza: FR-023, kryterium „secondary” `prd.md:42`, NFR `prd.md:268`, guardrails `prd.md:48-49`); audyt `context/changes/post-m2-audit/research.md` (ustalenia S1–S8, E1–E6, P1–P9, K1–K6); destylacja domeny `context/domain/domain-distillation.md` (R-05, R-15–R-17, R-22, R-24, R-27, D-01, D-04, D-08, pytania Q-01–Q-12).
- **Done when:** every F-NN and S-NN below is `done`.
- **Scope anchors:** FR-023, NFR `prd.md:268` (nowe źródło = pozyskanie + mapowanie), NFR `prd.md:259` (sygnał awarii), guardrails `prd.md:48-49` (jakość i koszt LLM); ustalenia audytu `post-m2-audit` i rozbieżności destylacji wymienione przy wycinkach.

## Vision recap

Na Wykopie (#gielda) i Reddicie codziennie pojawiają się setki postów o inwestowaniu; wartościowe informacje giną w szumie, a indywidualny inwestor nie ma czasu przeglądać obu platform ręcznie. Szczur Radar pobiera posty, a LLM według zamkniętego kontraktu opisuje, czego dotyczą, jakie stanowisko autor zajmuje wobec każdej spółki lub tematu i jakie tezy przedstawia — każdą z dosłownym cytatem. M-1 dostarczył to dla Wykopu, M-2 sprawił, że przebiegi nie milczą i nie gubią danych; M-3 dokłada drugie źródło, którego brakowało od początku wizji, na rdzeniu, który po audycie nie ma już znanych pułapek dla drugiego źródła.

## North star

**S-20: Awaria albo cisza jednego źródła nie psuje przebiegu pozostałych** — to rozbieżność #1 destylacji (R-05/D-01) i jedyne ustalenie audytu, które bezpośrednio blokuje FR-023; przy celu `quality` dowodzi, że NFR „nowe źródło zmienia tylko pozyskanie i mapowanie” jest prawdziwy nie tylko w strukturze kodu, ale i w zachowaniu silnika.

> „Gwiazda przewodnia” (north star) oznacza tu najmniejszy wycinek od początku do końca, którego udane dostarczenie potwierdza cel kamienia milowego — dlatego stoi tak wcześnie, jak pozwalają jego warunki wstępne.

## At a glance

| ID   | Change ID                       | Outcome (user can …)                                                                                                           | Prerequisites | PRD refs                                                                 | Status   |
| ---- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ------------- | ------------------------------------------------------------------------ | -------- |
| S-20 | source-isolation                | awaria albo cisza jednego źródła nie psuje przebiegu pozostałych; czas pobrania wielu źródeł mieści się w progu strażnika; liczniki widać per źródło | —             | NFR `prd.md:268`, FR-023; audyt S1, S2, S5; destylacja R-05, D-01        | done        |
| S-21 | deploy-gate-no-deploy-changes   | zmiana IaC, która nie wywołuje deployu, nie robi CI czerwonego ani nie blokuje deployu kodu; dokumenty S-14 i #73 mówią prawdę  | —             | audyt P1, P2, P3; Open Roadmap Question 2                                 | done |
| S-22 | run-attention-single-rule       | admin widzi powody „wymaga uwagi” liczone tą samą regułą co powiadomienia i alerty, bez pustego tekstu przy nowym powodzie      | —             | NFR `prd.md:259`; audyt K1; destylacja R-22, D-04                         | done |
| S-23 | closed-sets-guard               | nowa wartość zamkniętego zbioru albo nowa kolumna posta bez migracji lub bez zapisu jest wykrywana w CI, nie w nocy na produkcji | —             | audyt E4; destylacja R-17, R-19                                           | done |
| S-24 | api-contract-guard              | rozjazd odpowiedzi API i typów SPA (pełne ciała, kształt błędu 500, kopie reguł) jest wykrywany testem, nie ręczną dyscypliną   | —             | audyt K2, K3, K4; destylacja Q-05                                         | done        |
| S-25 | alert-ping-single-rule          | adres check-in alarmów ma jedną regułę we wszystkich miejscach, a rozjazd kopii jest wykrywany testem                           | —             | NFR `prd.md:259`; audyt P4, P5; destylacja R-27                           | done |
| S-26 | engine-test-stability           | testy silnika nie zależą od czasu zegara na współdzielonym runnerze, a znane okna fałszywych alarmów (F3) są przypięte testami | —             | audyt E1, E2, E6                                                          | done |
| S-27 | reddit-source                   | użytkownik widzi w feedzie posty z wybranych subredditów obok Wykopu, ocenione tym samym kontraktem                             | S-20          | FR-023, NFR `prd.md:268`, `prd.md:42`; audyt S3, S6, S7, S8; destylacja Q-01, Q-07 | done        |
| S-28 | reddit-quality-and-cost         | operator wie przed włączeniem Reddita na produkcji, że ocena postów po angielsku spełnia progi, a koszt mieści się w budżecie    | S-27          | guardrails `prd.md:48-49`; audyt S7; destylacja R-15, R-16, Q-03, Q-04    | proposed |
| S-29 | source-aware-feed               | użytkownik odróżnia źródło posta w feedzie i szczegółach, a data aktualizacji nie kłamie przy dwóch źródłach                    | S-27          | FR-023, FR-008; audyt S5, S6; destylacja D-08, R-24, Q-02                 | done        |

## Streams

Navigation aid — groups items that share a Prerequisites chain. Canonical ordering still lives in the dependency graph below; this table is the proposed reading order across parallel tracks.

| Stream | Theme                      | Chain                                   | Note                                                                                                  |
| ------ | -------------------------- | --------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| A      | Drugie źródło              | `S-20` → `S-27` → `S-28` · `S-29`       | Gwiazda przewodnia otwiera strumień; `S-28` i `S-29` są równoległe po `S-27`.                         |
| B      | Wdrożenia i alarmy         | `S-21` · `S-25`                         | Niezależne; `S-21` warto wcześnie, bo każda zmiana IaC w M-3 (klucze Reddita) przechodzi przez bramkę. |
| C      | Jedna reguła, jeden strażnik | `S-22` · `S-23` · `S-24`              | Przy celu `quality` reguły powtórzone w kilku miejscach dostają strażnika przed nowym źródłem.        |
| D      | Silnik                     | `S-26`                                  | Stabilne testy silnika przed zmianą szwu źródeł w `S-20` i `S-27`.                                    |

## Baseline

What's already in place in the codebase as of `2026-10-27` (`main` `d7c6113`, kod = `a783195`, po S-17; z sondy stanu kodu, mapy projektu i audytu `post-m2-audit`).
Foundations below assume these are present and do NOT re-scaffold them.

- **Frontend:** present — SPA Vue: feed, widok posta, `/admin`, strona przebiegu, dzwonek, aktywacja (`szczur-radar-web/src/views/`, `src/router/index.ts`); tylko `vue` i `vue-router`.
- **Backend / API:** present — przebieg jako sekwencja kroków w Javie (`api/pipeline/flow/RunSequence.java`), kolejka, strażnik, odzyskiwanie, sprzątanie sierot; 24 trasy HTTP; port źródła `SourceFetcher` z jednym źródłem `WYKOP` (`api/sources/Source.java`).
- **Data:** present — Postgres, Flyway V1–V11; `posts`, `assessments`, `raw_payloads` osobno, `pipeline_runs`, `pipeline_run_steps`, `notifications`, `activation_tokens`.
- **Auth:** present — Spring Session JDBC (`sr_session`), role `user`/`admin`, deny-by-default z `AuthorizationCompletenessIT`, token crona dla `/internal/**`; konta tylko przez CLI.
- **Deploy / infra:** present z luką — IaC `.railway/railway.ts`, CI z `needs:`, Wait for CI na czterech usługach, bramka wdrożeń; luka: kontrola po apply oczekuje deployu po każdej zmianie (audyt P1).
- **Observability:** present — alarmy healthchecks.io → Telegram (`api/alerts/`, check-iny cronów), powiadomienia w aplikacji, „Dni bez danych”; bez error trackera i metryk (decyzja S-18).

## Foundations

Brak — każda praca przekrojowa M-3 ma widoczny skutek dla użytkownika albo operatora i jest wycinkiem.

## Slices

### S-20: Awaria albo cisza jednego źródła nie psuje drugiego

- **Outcome:** przebieg z kilkoma źródłami zapisuje i ocenia posty zdrowych źródeł, gdy jedno z nich rzuca błąd albo nie daje żadnego posta; łączny czas pobrania kilku zdrowych źródeł nie zatrzymuje przebiegu jako „bez postępu”; admin widzi pobrane i pominięte elementy osobno dla każdego źródła, a dzień jest pokryty dla źródła, które dało posty.
- **Change ID:** source-isolation
- **PRD refs:** NFR `prd.md:268`, FR-023; audyt S1, S2, S5; destylacja R-05, D-01
- **Prerequisites:** —
- **Parallel with:** S-21, S-22, S-23, S-24, S-25, S-26
- **Blockers:** —
- **Unknowns:**
  - Jaki status ma przebieg, w którym jedno źródło padło albo było puste, a drugie dało posty (`partial`? `succeeded` z powiadomieniem?), i czy taki przebieg przesuwa datę aktualizacji? — Owner: user. Block: no (rozstrzyga wywiad `/10x-plan`; wiąże się z S-29).
- **Risk:** zmienia szew `SourceIngest` i regułę postępu kroku `fetch`, które działają dziś na produkcji z jednym źródłem; da się to zweryfikować fałszywym drugim źródłem w testach, bez API Reddita (audyt, sekcja 1, „Testowalność drugiego źródła”).
- **Status:** done

### S-21: Zmiana IaC bez deployu nie blokuje wdrożeń

- **Outcome:** kontrola po apply w `railway-config` przechodzi, gdy zastosowana zmiana nie wywołuje deployu (np. `source.checkSuites`) albo gdy w trakcie pojawi się deployment z innego pusha; nie przepuszcza przy tym deployu, który naprawdę się nie udał; runbook S-14 w `deploy-plan.md` i issue #73 opisują to, co faktycznie stało się z `2abc64c`.
- **Change ID:** deploy-gate-no-deploy-changes
- **PRD refs:** audyt P1, P2, P3; Open Roadmap Question 2
- **Prerequisites:** —
- **Parallel with:** S-20, S-22–S-26
- **Blockers:** —
- **Unknowns:**
  - Jak wygląda `meta` (`reason`, `commitHash`) deploymentu wywołanego przez apply zmiany, która deployuje, i czy taki redeploy czeka na Wait for CI? — Owner: operator (żywy Railway). Block: no (plan wybiera regułę z R1–R6 audytu i przyjmuje ostrożne założenie).
- **Risk:** zmienia bramkę, która chroni każdy deploy; dziś błąd jest uśpiony (od 2026-10-19 nie było apply ze zmianą), ale następny apply ze zmianą bez deployu zrobi CI na `main` czerwonym, a Wait for CI pominie wtedy deploy kodu — stąd wcześnie, przed zmianami IaC dla Reddita.
- **Status:** done

### S-22: „Wymaga uwagi” liczone jedną regułą

- **Outcome:** powody „wymaga uwagi” na stronie przebiegu, w `/admin` i w dzwonku pochodzą z tej samej reguły co powiadomienia i alert `RUN_NEEDS_ATTENTION`; nowy powód po stronie API nie daje pustego tekstu w SPA, a ton przebiegu przerwanego przez wdrożenie zgadza się z tym, czy wymaga uwagi.
- **Change ID:** run-attention-single-rule
- **PRD refs:** NFR `prd.md:259`; audyt K1; destylacja R-22, D-04
- **Prerequisites:** —
- **Parallel with:** S-20, S-21, S-23–S-26
- **Blockers:** —
- **Unknowns:**
  - Czy SPA ma dostawać powody z API, czy dalej liczyć je z liczników przy wspólnych przypadkach testowych (destylacja Q-05)? — Owner: user. Block: no (rozstrzyga wywiad `/10x-plan`).
- **Risk:** dotyka reguły nośnej dla powiadomień, alertów i trzech widoków; dziś obie tabele są równe, więc ryzyko jest w zmianie, nie w stanie — wymaga testów po obu stronach na tych samych przypadkach.
- **Status:** done

### S-23: Zamknięte zbiory i kolumny posta pilnowane w CI

- **Outcome:** każda wartość enumu zapisywanego w bazie (`PostStage`, `RunStatus`, `StepStatus`, `InterruptionCause`, `FailureClass`, `ItemType`, `Source` i pozostałe) jest przyjmowana przez `CHECK` swojej kolumny, a kolumna dodana do posta bez obsługi w warunkowych zapisach stanu jest wykrywana testem — przed nocnym przebiegiem, nie w nim.
- **Change ID:** closed-sets-guard
- **PRD refs:** audyt E4; destylacja R-17, R-19
- **Prerequisites:** —
- **Parallel with:** S-20–S-22, S-24–S-26
- **Blockers:** —
- **Unknowns:** —
- **Risk:** mały i czysto testowy; warto przed S-27, bo Reddit dodaje stałą `Source` i migrację `CHECK` — pierwsze miejsce, gdzie strażnik zadziała.
- **Status:** done

### S-24: Kontrakt SPA ↔ API pilnowany testem

- **Outcome:** duże odpowiedzi API (przebieg z krokami, post z oceną, nieudany post) mają w testach zapisany pełny kształt; odpowiedź na nieoczekiwany błąd serwera ma zdefiniowany kod, a atrapa i testy SPA używają tego samego; reguły powtórzone po obu stronach (białe znaki, limity, listy wartości) nie rozjadą się bez czerwonego testu.
- **Change ID:** api-contract-guard
- **PRD refs:** audyt K2, K3, K4; destylacja Q-05
- **Prerequisites:** —
- **Parallel with:** S-20–S-23, S-25, S-26
- **Blockers:** —
- **Unknowns:**
  - Jaki JSON API wysyła dziś na błąd 500 na produkcji (brak handlera i IT)? — Owner: team. Block: no (do sprawdzenia w `/10x-research` lokalnym testem).
- **Risk:** łatwo przerosnąć w generator klienta albo OpenAPI — cel to wykrycie rozjazdu, nie nowa warstwa; zakres ograniczają ustalenia K2–K4 (lekcja „Uzasadniaj zakres celem użytkownika”).
- **Status:** done

### S-25: Jedna reguła adresu check-in alarmów

- **Outcome:** adres check-in healthchecks ma jedną regułę walidacji w API, w obu cronach i w narzędziu checków (wielkość liter schematu, host, brak zapytania i fragmentu), kod wyjścia po limicie czasu jest zgłaszany tak samo przez oba crony, a rozjazd kopii jest wykrywany testem.
- **Change ID:** alert-ping-single-rule
- **PRD refs:** NFR `prd.md:259`; audyt P4, P5; destylacja R-27
- **Prerequisites:** —
- **Parallel with:** S-20–S-24, S-26
- **Blockers:** —
- **Unknowns:**
  - Jeden dom reguły (np. walidacja tylko w jednym miejscu) czy dwie kopie z testem zgodności (destylacja Q-09)? — Owner: operator. Block: no (rozstrzyga wywiad `/10x-plan`).
- **Risk:** dotyka check-inów obu cronów, które dziś działają; zły wzorzec po cichu wyłącza check-in (warn-and-continue), więc test negatywny jest częścią wycinka.
- **Status:** done

### S-26: Testy silnika bez zależności od zegara

- **Outcome:** test anulowania dyspozytora i pokrewne testy silnika nie padają losowo na współdzielonym runnerze (synchronizacja bez limitów zegarowych jako warunku poprawności, zrzut wątków przy przekroczeniu czasu), a znane okna fałszywych alarmów po S-17 (fałszywa linia ERROR i `NOTIFICATION_FAILED` przy spóźnionym przerwaniu) są przypięte testami; notatki silnika `pipeline/CLAUDE.md` zgadzają się z kodem.
- **Change ID:** engine-test-stability
- **PRD refs:** audyt E1, E2, E6
- **Prerequisites:** —
- **Parallel with:** S-20–S-25
- **Blockers:** —
- **Unknowns:**
  - Co spowodowało Timeout w runie 39133095273? — Owner: team. Block: no (wycinek dodaje zrzut wątków, który odpowie przy następnym wystąpieniu).
- **Risk:** dotyka testów przepisanego rdzenia, nie samego kodu silnika; warto przed S-20 i S-27, które zmieniają ten sam obszar i potrzebują wiarygodnego CI.
- **Status:** done

### S-27: Reddit jako drugie źródło

- **Outcome:** posty z wybranych subredditów są pobierane w przebiegu zakresu obok Wykopu, mapowane do wspólnego modelu, oceniane tym samym kontraktem i widoczne w feedzie; nieskonfigurowany Reddit nie zmienia niczego w zachowaniu Wykopu, a jego klucze nie zatrzymują wdrożeń.
- **Change ID:** reddit-source
- **PRD refs:** FR-023, NFR `prd.md:268`, `prd.md:42`; audyt S3, S6, S7, S8; destylacja Q-01, Q-07
- **Prerequisites:** S-20
- **Obowiązkowe przejęcie z S-20 (plan-review F1, 2026-10-29):** w testach integracyjnych rzeczywistego Reddita zastąpić testowy adapter `RawInputMapper(REDDIT)` delegujący do `WykopPosts` oraz RAW Wykopu produkcyjnym mapperem Reddita i jego rzeczywistym, zanonimizowanym fixture. Dostosować `ScriptedSourceConfiguration`, żeby nie rejestrowała dodatkowych beanów `SourceFetcher` / `RawInputMapper` pod `Source.REDDIT` obok produkcyjnych. Bramka S-27: przy awarii Wykopu post Reddita przechodzi przez zapis RAW, produkcyjny mapper i ocenę; zapisane wejście oceny zgadza się z fixture Reddita. Ująć ten krok w planie S-27 i zweryfikować przed zamknięciem wycinka; adapter z S-20 dowodzi tylko izolacji silnika, nie obsługi formatu Reddita.
- **Parallel with:** S-21–S-26
- **Blockers:** —
- **Unknowns:**
  - Które subreddity i jaki sposób dostępu do Reddita (aplikacja z kluczem, publiczny odczyt, archiwum Arctic Shift z `contract-v2.md:19`) — także warunki przechowywania treści? — Owner: user + operator. Block: yes (bez listy kanałów i dostępu nie da się zaplanować pobrania).
  - Post Reddita z odnośnikiem i treścią tekstową: `post` czy `link` (`contract-v2.md:70`, destylacja Q-07)? — Owner: user (właściciel kontraktu). Block: no (rozstrzyga wywiad `/10x-plan` albo ścieżka zmiany kontraktu).
  - Czy Reddit ma wejść do nocnego przebiegu od pierwszej konfiguracji (audyt S4)? — Owner: user. Block: no.
- **Risk:** pierwszy test NFR „nowe źródło = pozyskanie + mapowanie” na prawdziwym drugim źródle; model stronicowania Reddita (kursory, bez filtra dat) może nie pasować do reguły spójności z `lessons.md` — po S-20, żeby awaria nowego źródła nie psuła nocy Wykopu.
- **Status:** done

### S-28: Jakość i koszt Reddita zmierzone przed produkcją

- **Outcome:** operator ma pomiar oceny postów z Reddita (progi kierunku celów i sprzeczności na próbce angielskiej przy obecnym prompcie) oraz szacunek dziennego kosztu LLM dla wybranych subredditów, zanim klucze Reddita trafią na produkcję.
- **Change ID:** reddit-quality-and-cost
- **PRD refs:** guardrails `prd.md:48-49`; audyt S7; destylacja R-15, R-16, Q-03, Q-04
- **Prerequisites:** S-27
- **Parallel with:** S-29
- **Blockers:** —
- **Unknowns:**
  - Jaki jest budżet kosztu LLM na dzień albo przebieg poza limitem $10/mies. na kluczu (Open Roadmap Question 4, destylacja Q-03)? — Owner: user. Block: no (pomiar najpierw, decyzja o włączeniu po nim).
  - Czy wynik pomiaru jest warunkiem włączenia Reddita na produkcji (destylacja Q-04)? — Owner: user. Block: no.
  - Język posta w bazie (decyzja właściciela 2026-10-29 przy planie S-27: ma być zapisany): pole `language` w wyjściu modelu (ścieżka §16 kontraktu, nowa `PROMPT_VERSION`) czy detekcja w backendzie bez LLM, i kolumna `posts.language` z backfillem? — Owner: user (właściciel kontraktu). Block: no (S-27 daje `posts.channel`, z którego język da się zgadnąć; S-28 i tak dotyka promptu przy pomiarze po angielsku).
- **Risk:** zestaw wzorcowy i nagrania są dziś wyłącznie wykopowe; próbka angielska wymaga oznaczenia przez człowieka, a progi zostały już raz złagodzone (≥ 70 % / ≤ 10 %).
- **Status:** proposed

### S-29: Źródło widoczne w feedzie, świeżość bez kłamstwa

- **Outcome:** użytkownik widzi przy każdym poście, z którego źródła pochodzi, a etykieta „Dane zaktualizowane” nie przesuwa się po przebiegu, który nie objął danego źródła — zgodnie z decyzją, czy świeżość jest wspólna, czy per źródło.
- **Change ID:** source-aware-feed
- **PRD refs:** FR-023, FR-008; audyt S5, S6; destylacja D-08, R-24, Q-02
- **Prerequisites:** S-27
- **Parallel with:** S-28
- **Blockers:** —
- **Unknowns:**
  - Czy lista i szczegóły mają oznaczać źródło, czy potrzebny jest filtr albo sortowanie po źródle (FR-008, nice-to-have), i czy świeżość ma być per źródło (destylacja Q-02)? — Owner: user. Rozstrzygnięte w planie `source-aware-feed` z 2026-10-30: lista i szczegóły oznaczają źródło, bez filtra i sortowania (FR-008 zostaje kandydatem do M-5); świeżość per źródło, zawsze rozpisana.
  - Czy obok źródła feed i szczegóły pokazują kanał (`posts.channel` z S-27: `#gielda`, `r/<subreddit>`) i język posta (jeśli S-28 go zapisze)? — Owner: user. Rozstrzygnięte w planie `source-aware-feed` z 2026-10-30: kanał tak (`Wykop · #gielda`, `Reddit · r/<subreddit>`); język posta nie, decyzja zostaje przy S-28.
- **Risk:** zmienia kształt odpowiedzi feedu i etykiety świeżości, które widzi każdy użytkownik; po S-27, bo bez drugiego źródła nie ma czego oznaczać.
- **Status:** done

## Backlog Handoff

| Roadmap ID | Change ID                     | Suggested issue title                                                 | Ready for `/10x-plan` | Notes                                                        |
| ---------- | ----------------------------- | --------------------------------------------------------------------- | --------------------- | ------------------------------------------------------------ |
| S-20       | source-isolation              | S-20: awaria albo cisza jednego źródła nie psuje drugiego (gwiazda przewodnia M-3) | yes                   | Run `/10x-plan source-isolation`                              |
| S-21       | deploy-gate-no-deploy-changes | S-21: zmiana IaC bez deployu nie blokuje wdrożeń                       | yes                   | Run `/10x-plan deploy-gate-no-deploy-changes`; warto wcześnie |
| S-22       | run-attention-single-rule     | S-22: „wymaga uwagi” liczone jedną regułą                              | yes                   | Run `/10x-plan run-attention-single-rule`                     |
| S-23       | closed-sets-guard             | S-23: zamknięte zbiory i kolumny posta pilnowane w CI                  | yes                   | Run `/10x-plan closed-sets-guard`                             |
| S-24       | api-contract-guard            | S-24: kontrakt SPA ↔ API pilnowany testem                              | yes                   | Run `/10x-plan api-contract-guard`                            |
| S-25       | alert-ping-single-rule        | S-25: jedna reguła adresu check-in alarmów                             | yes                   | Run `/10x-plan alert-ping-single-rule`                        |
| S-26       | engine-test-stability         | S-26: testy silnika bez zależności od zegara                           | yes                   | Run `/10x-plan engine-test-stability`                         |
| S-27       | reddit-source                 | S-27: Reddit jako drugie źródło                                        | no                    | Po S-20 i decyzji: subreddity + sposób dostępu (destylacja Q-01) |
| S-28       | reddit-quality-and-cost       | S-28: jakość i koszt Reddita zmierzone przed produkcją                 | no                    | Po S-27                                                       |
| S-29       | source-aware-feed             | S-29: źródło widoczne w feedzie, świeżość bez kłamstwa                 | no                    | Po S-27 i decyzji o oznaczeniu źródła (destylacja Q-02)       |

## Open Roadmap Questions

1. **Weryfikacja produkcji z M-1:** lokalizacja bazy dla filtra S-04 (runbook z issue #17, krok 2: `C`/`POSIX` po cichu psuje `KĘTY`→`Kęty`) i pierwsza noc S-06 (runbook z issue #19). Owner: operator. Block: — (przeniesione z M-2; wynik `C`/`POSIX` dałby nowy wycinek).
2. **Ustalenia z żywego Railway** (audyt M-2, Open Questions 2–7): czy redeploy po `config apply` zmiany, która deployuje, czeka na CI (S-21); czy reguła „deployment crashed” obejmuje crash loop API; okno SIGTERM→SIGKILL; przyczyna przebiegu „trwającego” 22 h z 2026-10-09. Owner: operator. Block: —. Rozstrzygnięte przez audyt `post-m2-audit` (P2): merge samej dokumentacji daje `SKIPPED` na wszystkich usługach; apply `source.checkSuites` nie wywołuje żadnego deploymentu; deployment API z czerwonym CI jest pomijany przez Wait for CI.
3. **Kanał i próg dla FR-025 (powiadomienia o obiecującym trendzie)** — nie ustalono, czym jest „obiecujący trend” liczbowo ani gdzie ma trafiać powiadomienie. Owner: użytkownik. Block: — (FR-025 odroczone).
4. **Budżet kosztów LLM na przebieg** — guardrail PRD każe minimalizować koszt zapytań, ale nie podaje pułapu (na dzień ani na post); twardy pułap to $10/mies. na kluczu OpenRouter produkcji. Drugie źródło zwiększy liczbę ocen. Owner: użytkownik. Block: S-28 (nie blokuje planowania; decyduje o włączeniu Reddita).
5. **PRD ma status `draft` i ostatnią zmianę 2026-10-13** — nowsze decyzje żyją w blokach „Doprecyzowanie” i w roadmapie (destylacja, sekcja „Stale documents”: FR-029 krótka sesja vs 30 dni, „zakres dat/godzin”, FR-022 warunkowe). Czy odświeżyć PRD do v3 przed planowaniem Reddita? Owner: użytkownik. Block: — (destylacja D-02, D-03, D-10).

## Parked

- **M-4 (kandydat): weryfikacja i regresja** — Why parked: decyzja użytkownika 2026-10-27 — kolejny kamień milowy po M-3: dziś nie wiadomo, czy testy backendu łapią błędy, nie ma planu testowania, a weryfikacja wycinków w PR env jest klikana ręcznie. Cel: większość weryfikacji i regresja działają same w CI, ręcznie zostaje krótka lista (Telegram, deploy na Railway, prawdziwe API źródeł i LLM, harmonogram cronów). Kandydaci na zakres: plan testów (`/10x-test-plan`), testy mutacyjne rdzenia backendu, scenariusze API na atrapach źródeł i LLM w miejsce ręcznej weryfikacji PR env, smoke w przeglądarce na `dev/mock-api.js`, regresja w CI — opis w `context/foundation/verification-scope.md`. S-23 i S-26 zostają w M-3; S-24 do decyzji przy otwieraniu M-4.
- **M-5 (kandydat): nowe UI i UX aplikacji webowej z testami E2E (Playwright)** — Why parked: decyzja użytkownika 2026-10-27 (najpierw jako M-4, przesunięte za weryfikację i regresję, żeby przebudowa miała gotową siatkę regresji); projektować raz, gdy feed ma już dwa źródła i oznaczenie źródła (S-27, S-29). Cztery propozycje układu od użytkownika (czytnik z adnotacjami, karta podmiotu, strumień z inspektorem wpisu, przegląd „Co mówi dziś rynek?”) opisane w `context/foundation/ui-ux-proposals.md`; wygląd do ustalenia w M-5, a każdy nowy widok dostaje test E2E, rozbudowujący smoke z M-4. Kandydaci do tego samego kamienia: FR-008/FR-011b, FR-031, FR-020 oraz dwie pozycje UX niżej (komunikat przy nieudanym odczycie daty, przybliżony koniec przebiegu). Widoki agregujące wokół spółki dotykają `prd.md:314` (rozpoznawanie spółek poza zakresem) i FR-024/FR-025.
- **FR-008 / FR-011b — sortowanie po dacie, źródle i polach oceny** — Why parked: nice-to-have; chronologia wystarcza na start (cel `speed`).
- **FR-016 / FR-017 / FR-019 — panel kont w aplikacji + wysyłka maila z zaproszeniem** — Why parked: nice-to-have; CLI (S-07) daje ten sam efekt; PRD §Non-Goals wyklucza wymaganą wysyłkę e-maili.
- **FR-020 — własna nazwa wyświetlana** — Why parked: nice-to-have; e-mail wystarcza w MVP.
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
- **Porządki granic pakietów: cykl `pipeline` ↔ `notifications`, dwa mechanizmy zapisu `posts` (JPA i SQL), konwertery zależne od `pipeline.run`, duplikaty reguł („najnowsza ocena `ok`”, predykat resztek)** — Why parked: z audytu M-2 (sekcja E, L); nie wpływają dziś na niezawodność. Część zniknie przy S-17; reguła „najnowsza ocena `ok`” idzie z pozycją o wydajności filtra.
- **Drobne ryzyka z audytu M-2 (L): przebieg `queued` przy zamkniętym executorze i koniec zakresu `23:59:59` (A8), brak limitów schematu dla OpenRouter i 3 płatne wywołania przy deterministycznym naruszeniu kontraktu (B8), dodatkowa próba po przerwaniu przy przejściu flex→standard (B10), offset w powtórzonej jesiennej godzinie (B11), okno ponowień Caddy 30 s (C14), fałszywy alarm crona po 90 min (C8)** — Why parked: niska waga; wracają, gdy dotknie ich wycinek z tego samego obszaru.
- **Ocena obrazków w postach (model multimodalny)** — Why parked: nice-to-have po MVP, do sprawdzenia, czy ma sens. PRD v2 i kontrakt V2 oceniają wyłącznie tekst (`prd.md:258`); w pilotażu #gielda ~44% wpisów ma zdjęcie, a część ma sens tylko w obrazku (`frame.md`, „Znane luki”). Zestaw wzorcowy F-02 (flaga `media.photo`, etykiety `unclear` z powodu obrazka, raport fazy 6) pokaże skalę; wdrożenie wymagałoby zmiany PRD, kontraktu (V3: cytat/dowód z obrazka nie jest podciągiem tekstu), wyboru modeli z wejściem obrazkowym i ponownego pomiaru, a koszt wejścia rośnie o tokeny obrazków.
- **Wydajność filtra feedu przy dużej liczbie postów (TODO na przyszłość)** — Why parked: dziś zbędne (~300 postów, zapytania filtra w milisekundach). Pomiar z impl-review S-04 (folder zmiany `filter-by-assessment`, `reviews/impl-review.md`, F6): przy 100k ocenionych postów zapytanie strony z rzadkim filtrem trwa 44 ms, ale zliczanie wyników 0,6–0,95 s (sekwencyjny skan `posts`, najnowsza ocena `ok` i `jsonb_array_elements` dla każdego wiersza); filtr bez trafień płaci ten sam skan. Przy ~270 postach dziennie to ok. rok. TODO: po ~20k postów zmierzyć `EXPLAIN ANALYZE` na produkcji; jeśli zwalnia, projekcja celów (kolumna `latest_ok_assessment_id` w `posts` albo tabela `post_targets`) z indeksem `pg_trgm` — usunie też drugą kopię reguły „najnowsza ocena `ok`” (`PostRepository.findMatching` i `AssessmentRepository.findLatestOk`). Osobna zmiana z pomiarem.
- **Drugi runner CI w VM w Proxmoxie (równoległe runy bez PC)** — Why parked: z `self-hosted-pr-runner` (#30, 2026-10-19). CI idzie na pulę self-hosted runnerów z etykietą `szczur-ci` (`tools/ci-runner/`): jeden runner w VM w Proxmoxie zawsze i VM-ki na PC w czasie developmentu; bez PC Proxmox wykonuje joby po kolei (run ok. 7 min zamiast ok. 2). Drugi runner w tej samej VM wymaga najpierw unikalnych portów i nazw kontenerów w smoke testach (`8080`, `8000`, `5432` na hoście, kontenery `web`/`api`); alternatywa bez zmian w `ci.yml`: druga VM w Proxmoxie z własnym Dockerem.
- **Losowe padanie budowy obrazów Dockera na self-hosted runnerach (cache budowania)** — Why parked: zauważone w S-08 `empty-run-signal` (PR #80, 2026-10-19), do zrobienia w kolejnej fali. Joby budujące obraz padają losowo na VM-kach `ci-pc-*` z `failed to prepare extraction snapshot "…": parent snapshot sha256:… …` przy rozpakowaniu obrazu do lokalnego magazynu Dockera (obraz zbudowany, warstwy `CACHED`): `api container smoke test` na `ci-pc-1-1` (run 39286873262) i `db backup test` na `ci-pc-2-1` (run 39295084707, podejście 1); ponowienie jobu przechodzi. Prawdopodobnie cache budowania wskazuje na usuniętą warstwę rodzica (np. po czyszczeniu Dockera na VM). Do sprawdzenia w `tools/ci-runner/`: czyszczenie cache budowania (`docker builder prune -af`) przed jobem albo budowa obrazów w CI bez cache; do tego czasu pomaga `gh run rerun <id> --failed`.
- **Komunikat, gdy nie udało się odczytać daty aktualizacji danych** — Why parked: nice-to-have po MVP (decyzja użytkownika 2026-10-13, S-05 `daily-scheduled-run-freshness`, faza 4). Dziś przy błędzie `GET /api/freshness` etykieta „Dane zaktualizowane: …” nad feedem po prostu znika, a lista działa (`FeedView.vue`; `context/archive/2026-10-12-daily-scheduled-run-freshness/ui/README.md`), więc użytkownik nie odróżni „nie udało się sprawdzić” od braku etykiety. Pomysł: osobny, neutralny komunikat treści w miejscu etykiety (nie czerwony alert, bo posty są w porządku), np. „Nie udało się sprawdzić daty aktualizacji.”, ewentualnie z ponowieniem.
- **Unieważnianie sesji wersją poświadczeń** — Why parked: po MVP, z przeglądu implementacji S-07 (`cli-user-onboarding`, F1). Logowanie, które sprawdziło hasło przed zatwierdzeniem `reset-password`, zapisuje sesję już po `DELETE` resetu, więc ta sesja działa aż do aktywacji (najwyżej `ACTIVATION_LINK_TTL`, domyślnie 12 h); od S-07 kończy ją aktywacja. Pełne zamknięcie: wersja poświadczeń konta (np. `password_changed_at`), zapisana w sesji przy logowaniu i sprawdzana przy każdym uwierzytelnionym żądaniu — migracja i sprawdzenie w łańcuchu filtrów.
- **S-12 `reliable-run-notifications` — powiadomienia o przebiegach nie giną (A3), krok `assess` nie jest `succeeded` w przebiegu `failed` (A6)** — Why parked: decyzja użytkownika 2026-10-24 po `/10x-frame` (`context/archive/2026-10-24-reliable-run-notifications/frame.md`). Scenariusze tylko z audytu, bez incydentu; A3 wymaga awarii bazy w oknie milisekund po commicie i po S-18 przestaje być cichy; A6 to niespójny obraz kroków w `/admin` (powiadomienie powstaje). **Aktualizacja 2026-10-27:** A6 zamknięte przez S-17 (`RunSequence.assessed` zamyka krok `assess` jako `failed`, audyt `post-m2-audit` E3); zostaje A3. A5 przeszedł do S-18. Powrót: pierwszy incydent zgubionego powiadomienia albo przebiegi admina uruchamiane bez patrzenia; A6 przy najbliższej pracy w `pipeline/flow`.
- **Przybliżony czas zakończenia trwającego przebiegu** — Why parked: nice-to-have po MVP (decyzja użytkownika 2026-10-13, po pierwszym przebiegu crona na produkcji). Buduje na postępie przebiegu z S-06 (dopisek o monitorowaniu): bez liczników postępu nie ma z czego liczyć. Pomysł: w `/admin` przy przebiegu `running` szacowany koniec i odliczanie, np. średni czas oceny posta z ostatnich przebiegów (z `pipeline_run_steps` i `assessments`) × liczba postów do oceny + typowy czas pobrania, albo tempo bieżącego przebiegu, gdy ma już kilka ocen. Niepewność: tier flex, ponowienia i powroty do standardu mocno wydłużają pojedyncze oceny, więc to przybliżenie, nie obietnica.
- **Dni bez danych przy drugim źródle (S-13, przegląd F2)** — Why parked: dziś jest jedno źródło (Wykop), więc przypadku „dwa źródła z różnymi lukami” w `MissingDaysTest` nie da się napisać; kod liczy pokrycie osobno dla każdego źródła (`pipeline/run/MissingDays`). Dopisać w wycinku z Redditem (FR-023): dzień, którego brakuje tylko jednemu źródłu, i `sources` dnia w kolejności `Source` (`context/archive/2026-10-24-missed-night-detection/follow-ups/review-fixes.md`).
- **Odczyt dni bez danych poza kontrolerem (S-13, przegląd F5)** — Why parked: jedynym odbiorcą listy jest sekcja w `/admin`. `PipelineRunController.missingDays()` składa ją z dwóch odczytów bez wspólnej transakcji (chwilowa niespójność możliwa tylko przy pierwszym w historii przebiegu źródła). Przy drugim odbiorcy (np. alarm o dziurze z crona) przenieść składanie do usługi odczytu w `pipeline/`, z jednym odczytem `readOnly` i jednym zapytaniem grupującym po źródłach.
- **Nadzór synchronizacji NAS → Google Drive** — Why parked: S-13 (audyt C2) obejmował go „tylko wtedy, gdy kanał to umożliwia”, a plan `missed-night-detection` zostawił go poza zakresem. Dziś nic nie sprawdza obu zadań Synology Cloud Sync (`deploy-plan.md`, sekcja F-01). Możliwa droga: osobny check healthchecks.io w `tools/healthchecks/checks.sh` i zadanie DSM na NAS-ie, które go pinguje — praca operatora poza Railway i kolejny sekret (ping URL).
- **Klucze z powtórzeniami w kolejce alarmów (S-18, przegląd F4)** — Why parked: `OperatorAlertQueue.forgetExpired` trzyma klucz (zdarzenie, przebieg) z powtórzeniami, dopóki ten sam klucz nie wróci; klucze z id przebiegu zwykle nie wracają, więc wpis zostaje do restartu, a dopisek „N repeat(s) … were not sent” nie wychodzi. Wzrost to kilka wpisów na dobę, bez realnego wycieku. **Powrót**, gdy operator zacznie polegać na liczbie powtórzeń albo mapa urośnie zauważalnie: zapominać klucz z powtórzeniami po 2 × okno (`context/archive/2026-10-24-error-visibility/reviews/impl-review.md`, F4).
- **Alarm watchdoga budowany w `catch (Throwable)` bez osobnej ochrony (S-18, przegląd F5)** — Why parked: `RunWatchdog.check` buduje tekst `WATCHDOG_FAILED` w bloku, który ma nie wypuścić niczego do `scheduleWithFixedDelay`; przy `OutOfMemoryError` składanie tekstu może rzucić drugi raz i zatrzymać kolejne sprawdzenia. Linia ERROR przed nim ma to samo ryzyko, a przypadek jest skrajny. **Powrót** przy najbliższej zmianie `RunWatchdog`: owinąć `alerts.raise(...)` w osobny `try`/`catch (Throwable)` (`…/reviews/impl-review.md`, F5).
- **Krótszy start API po obudzeniu (Spring AOT, flagi JVM, AOT cache JDK 25)** — Why parked: nice-to-have po S-17; JVM startuje w ok. 6 s (produkcja po S-17: 5,6–6,0 s), a klient widzi 8–10 s razem ze startem kontenera Railway. Kolejność prób: `JAVA_TOOL_OPTIONS` (`-XX:TieredStopAtLevel=1`, `-XX:+UseSerialGC`) zmierzone w PR env; `spring-boot-maven-plugin` `process-aot` + `-Dspring.aot.enabled=true` jako mała zmiana; AOT cache JDK 25 (trening z bazą albo cache na wolumenie) tylko gdy poprzednie nie zejdą poniżej ok. 4 s; bez `spring.main.lazy-initialization` (kłóci się z fail-fast S-10) i bez wyłączania usypiania (decyzja F-03). Pomiary: `deploy-plan.md`, S-17 „Startup time”.
- **Ustalenia niskiej wagi z audytu po M-2 (`post-m2-audit`)** — Why parked: cel M-3 `quality` bierze do wycinków ustalenia wysokiej i średniej wagi; te zostają do najbliższej pracy w tym samym obszarze. A7 dla przebiegu przerwanego przez odzyskiwanie nowszej instancji (zombie zapisuje posty do SIGTERM; nic nie ginie), A8 cz. 1 (`submit` przy zamykanym executorze → 500 i `queued` do sprzątania), A4 i B10 bez zmian (E3); harmonogramy i karencje checków w trzech źródłach bez testu krzyżowego (P6); po `TRIGGER_RUN_TIMEOUT` fałszywy alarm idzie dwoma kanałami (P8, rozszerza C8); statusy GitHub Deployment `success` dla commita z deploymentem `SKIPPED` — kandydat na lekcję (P9); atrapa `dev/mock-api.js` poza testami, id nie-UUID, brak logowania (K5); limiter logowań w pamięci jednej repliki (destylacja R-33, Q-10); kaskady `ON DELETE` bez ścieżki usuwania konta (R-29, Q-11); pola `content_hash`, `slug`, `requested_by` bez czytelnika (destylacja #10, Q-12). Szczegóły i kotwice: `context/changes/post-m2-audit/research.md`, `context/domain/domain-distillation.md`.

## Milestone History

- **M-1: MVP — ocenione sygnały z Wykopu dla zaproszonych osób** (`mvp-wykop-signal-radar`) — closed 2026-10-17. Wszystkie konieczne FR i historyjki US-01, US-02 dostarczone na produkcji (F-01–F-03, S-01–S-07); domknięcie runbooków produkcji S-04 (#17) i S-06 (#19) przeniesione do Open Roadmap Questions M-2.
- **M-2: MVP Quality & Reliability Hardening** (`mvp-quality-reliability-hardening`) — closed 2026-10-27. Istniejące MVP nie milczy i nie gubi danych: kanał alarmowy poza aplikacją (F-04), sygnały pustej i brakującej nocy, awarie dostawców bez utraty prób, samonaprawa utkniętych przebiegów, bezpieczna bramka wdrożeń, pełna kontrola kopii bazy, kompletne pobieranie z Wykopu, przebieg bez Embabel i błędy wymagające reakcji w Telegramie (F-04, S-08–S-11, S-13–S-19, produkcja zweryfikowana); S-12 zaparkowany decyzją użytkownika (v3.3). Pierwsza noc crona na silniku S-17 (2026-10-28) pod nadzorem F-04; otwarte ustalenie S-14 z issue #73: fałszywa kontrola po apply w `verify-apply.sh` (zmiana bez deployu, `SKIPPED` z równoległego pusha); drugie ustalenie z #73 („deploy `2abc64c` mimo czerwonego CI”) okazało się błędną diagnozą — deployment API został `SKIPPED` przez Wait for CI (audyt `post-m2-audit`).

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
- **S-11: przebieg `running` po instancji, która zniknęła bez zamknięcia, oraz post przejęty w wyścigu z odzyskiwaniem wracają do obiegu bez restartu API, więc nocny cron nie dostaje godzinami `409 pipeline_busy`.** — Archived 2026-10-24 → `context/archive/2026-10-23-stuck-run-self-heals/`. Lesson: —.
- **S-13: operator dostaje alarm przez kanał z F-04, gdy nocny przebieg albo backup nie wystartował lub nie zgłosił końca na czas, a dni bez pobranych danych są widoczne (lub uzupełniane).** — Archived 2026-10-24 → `context/archive/2026-10-24-missed-night-detection/`. Merged as squash `d0b099c` (PR #106; SHA w Progress archiwum to commity gałęzi), produkcja zweryfikowana 2026-10-25 (issue #72). Lesson: —.
- **S-18: błędy wymagające reakcji — linie ERROR (odzyskiwanie, watchdog, zamykanie przebiegu, nieudane powiadomienie), nieudany zapis licznika przebiegu, nieudane `/api/ready` z przyczyną w logu — docierają do operatora przez kanał z F-04, a łańcuch przyczyn wyjątków dostawców nie wynosi kluczy do logów.** — Archived 2026-10-25 → `context/archive/2026-10-24-error-visibility/`. Merged as squash `6dc24ee` (PR #107; SHA w Progress archiwum to commity gałęzi), produkcja zweryfikowana 2026-10-25 (issue #77): start z linią `Operator alerts: sent to …`, przebieg admina za 2004-01-01 i poprzedzający go RETRY (`partial`, 1 martwy post) dały po wiadomości `RUN_NEEDS_ATTENTION` w Telegramie (`DELIVERED`). Lesson: —.
- **S-17: przebieg pipeline'u działa jako zwykła sekwencja kroków bez Embabel, przerwanie i watchdog docierają do oceny bez „zombie” akcji, a testy silnika są skupione (zapisy stanu na bazie, logika bez Springa) i niezależne od kolejności.** — Archived 2026-10-27 → `context/archive/2026-10-25-pipeline-without-embabel/`. Merged `e869a07` (PR #110), production verified 2026-10-27 (issue #76). Lesson: Railway redeploy bez cache buduje obraz ~11 min, więc test przerwania przez deploy wymaga długiego `assess`; obudzenie zbiegające się z deployem lub kliknięciem nie jest pomiarem.

- **S-21: kontrola po apply w `railway-config` przechodzi, gdy zastosowana zmiana nie wywołuje deployu (np. `source.checkSuites`) albo gdy w trakcie pojawi się deployment z innego pusha; nie przepuszcza przy tym deployu, który naprawdę się nie udał; runbook S-14 w `deploy-plan.md` i issue #73 opisują to, co faktycznie stało się z `2abc64c`.** — Archived 2026-10-28 → `context/archive/2026-10-27-deploy-gate-no-deploy-changes/`. Lesson: kryterium, które agent sprawdzi poleceniem, zapisuj jako Automated — Manual tylko dla decyzji człowieka (`lessons.md`).
- **S-23: każda wartość enumu zapisywanego w bazie (`PostStage`, `RunStatus`, `StepStatus`, `InterruptionCause`, `FailureClass`, `ItemType`, `Source` i pozostałe) jest przyjmowana przez `CHECK` swojej kolumny, a kolumna dodana do posta bez obsługi w warunkowych zapisach stanu jest wykrywana testem — przed nocnym przebiegiem, nie w nim.** — Archived 2026-10-28 → `context/archive/2026-10-27-closed-sets-guard/`. Lesson: —.
- **S-26: test anulowania dyspozytora i pokrewne testy silnika nie padają losowo na współdzielonym runnerze (synchronizacja bez limitów zegarowych jako warunku poprawności, zrzut wątków przy przekroczeniu czasu), a znane okna fałszywych alarmów po S-17 (fałszywa linia ERROR i `NOTIFICATION_FAILED` przy spóźnionym przerwaniu) są przypięte testami; notatki silnika `pipeline/CLAUDE.md` zgadzają się z kodem.** — Archived 2026-10-28 → `context/archive/2026-10-27-engine-test-stability/`. Lesson: —.
- **S-22: powody „wymaga uwagi” na stronie przebiegu, w `/admin` i w dzwonku pochodzą z tej samej reguły co powiadomienia i alert `RUN_NEEDS_ATTENTION`; nowy powód po stronie API nie daje pustego tekstu w SPA, a ton przebiegu przerwanego przez wdrożenie zgadza się z tym, czy wymaga uwagi.** — Archived 2026-10-29 → `context/archive/2026-10-27-run-attention-single-rule/`. Lesson: —.
- **S-25: adres check-in healthchecks ma jedną regułę walidacji w API, w obu cronach i w narzędziu checków (wielkość liter schematu, host, brak zapytania i fragmentu), kod wyjścia po limicie czasu jest zgłaszany tak samo przez oba crony, a rozjazd kopii jest wykrywany testem.** — Archived 2026-10-29 → `context/archive/2026-10-27-alert-ping-single-rule/`. Lesson: —.
- **S-20: przebieg z kilkoma źródłami zapisuje i ocenia posty zdrowych źródeł, gdy jedno z nich rzuca błąd albo nie daje żadnego posta; łączny czas pobrania kilku zdrowych źródeł nie zatrzymuje przebiegu jako „bez postępu”; admin widzi pobrane i pominięte elementy osobno dla każdego źródła, a dzień jest pokryty dla źródła, które dało posty.** — Archived 2026-10-29 → `context/archive/2026-10-29-source-isolation/`. Lesson: —.
- **S-24: duże odpowiedzi API (przebieg z krokami, post z oceną, nieudany post) mają w testach zapisany pełny kształt; odpowiedź na nieoczekiwany błąd serwera ma zdefiniowany kod, a atrapa i testy SPA używają tego samego; reguły powtórzone po obu stronach (białe znaki, limity, listy wartości) nie rozjadą się bez czerwonego testu.** — Archived 2026-10-30 → `context/archive/2026-10-29-api-contract-guard/`. Lesson: —.
- **S-27: posty z wybranych subredditów są pobierane w przebiegu zakresu obok Wykopu, mapowane do wspólnego modelu, oceniane tym samym kontraktem i widoczne w feedzie; nieskonfigurowany Reddit nie zmienia niczego w zachowaniu Wykopu, a jego klucze nie zatrzymują wdrożeń.** — Archived 2026-10-30 → `context/archive/2026-10-29-reddit-source/`. Lesson: —.
