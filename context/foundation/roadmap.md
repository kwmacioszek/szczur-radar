---
project: "Szczur Radar"
version: 5
status: draft # draft | active | locked
created: 2026-09-27
updated: 2026-11-06
prd_version: 3
main_goal: quality
top_blocker: capacity # 16 wycinków, jeden recenzent i osoba mergująca; darmowe runnery GitHuba muszą zmieścić scenariusze i PIT
milestone_id: verified-core-and-regression
milestone_seq: 4
milestone_status: open
---

# Roadmap: Szczur Radar

> Derived from (M-4) `context/foundation/prd.md` (v3) + `context/foundation/verification-scope.md` + audyt `context/changes/post-m3-verification-audit/research.md` + destylacja `context/domain/domain-distillation.md` (ranking, 2026-11-01). M-3 derived from `context/foundation/prd.md` (v2, kolejna transza) + audyt `context/changes/post-m2-audit/research.md` + destylacja `context/domain/domain-distillation.md` + mapa projektu `context/map/repo-map.md` (2026-10-27). M-2 derived from the user's milestone description + `context/map/repo-map.md` (mapa projektu, 2026-10-17) + `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` (audyt diagnostyczny pipeline'u i operacji, HEAD `5b6eabb`) + PRD v2 NFRs + auto-researched codebase baseline (z mapy i audytu). M-1 derived from `context/foundation/prd.md` (v2) + `context/foundation/llm-assessment/` + `shape-notes.md` + `tech-stack-api.md` / `tech-stack-web.md` / `infrastructure.md` / `deploy-plan.md`.
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
> v4.2 (2026-10-31): S-28 zamyka pomiar: nowy Outcome (obraz konfiguracji produkcyjnej zamiast spełnienia progów), Q-03 i Q-04 oraz pytanie 4 rozstrzygnięte, decyzja o włączeniu Reddita bez napraw. Do Parked trafiają `posts.language`, naprawy oceny Reddita i porównanie modeli; S-29 bez warunku o języku.

> v4.3 (2026-11-01): M-3 zamknięty (`/10x-roadmap`): S-20–S-29 `done`, Reddit (r/stocks, r/ValueInvesting, r/investing) włączony na produkcji 2026-11-01; wpis w `## Milestone History`. Decyzja użytkownika: otwarta weryfikacja produkcji S-27 (issue #121: noce z Redditem 2.11 i 4.11, porównanie `dead` z raportem S-28, feed i strona przebiegu w aplikacji) nie blokuje zamknięcia; ewentualne poprawki pójdą jako wydanie poprawkowe v0.3.1. Wpis S-29, który `/10x-archive` dopisał między notkami wersji, przeniesiony do `## Done`. M-4 jeszcze nieotwarty (decyzja użytkownika); kandydaci M-4 i M-5 w `## Parked`.
> v5 (2026-11-04): otwarty M-4 „Weryfikacja i regresja” (`/10x-roadmap`) z PRD v3, `verification-scope.md`, audytu `post-m3-verification-audit` i rankingu destylacji; ramy od użytkownika: cel `quality`, gwiazda przewodnia S-31 (scenariusze PR env w CI), główne ryzyko `capacity`. Wycinki S-30–S-45, bez fundamentów; S-30 (niestabilne testy integracyjne z runu 41784416932) pierwszy z decyzji użytkownika. Decyzja użytkownika: cały Playwright, także smoke na obecnych widokach, idzie do M-5 razem z nowym UI; M-4 przygotowuje atrapę API zgodną z kontraktem (S-43). Plan testów (`/10x-test-plan`) w `## Parked`. Z `## Parked` usunięte: kandydat M-4 (otwarty) i drugi runner CI w Proxmoxie (od 2026-10-24 CI idzie na darmowych runnerach GitHuba, repo publiczne; pula `szczur-ci` to rezerwa). Open Roadmap Question 5 rozstrzygnięte przez PRD v3.

## Milestone

**M-4: Weryfikacja i regresja** — Status: open

- **Intent:** Testy łapią błędy, a nie tylko przechodzą: ręczne weryfikacje wycinków w PR env chodzą jako scenariusze w CI, strażnicy reguł wyprowadzają zbiory z kodu zamiast z ręcznych list, a mutanty rdzenia są zabite albo świadomie przyjęte. Obok tego wymagania PRD v3 z nocy z Redditem: seria odmów dostawcy nie zabija ocen bez drogi powrotu, martwe posty da się przywrócić i nie alarmują co noc, a operator widzi jakość ocen osobno dla każdego źródła.
- **Source materials:** `context/foundation/prd.md` (v3: FR-033, FR-034, doprecyzowanie FR-023 Q-13, doprecyzowanie PRD v3 NFR o sygnale `prd.md:275`); `context/foundation/verification-scope.md` (zakres i „done when” M-4); audyt `context/changes/post-m3-verification-audit/research.md` (eksperymenty X1–X6, sekcje 1–6, „Materiał dla M-4”); destylacja domeny `context/domain/domain-distillation.md` (ranking #1–#6).
- **Done when:** every S-NN below is `done`; ręczne kroki weryfikacji PR env z wycinków S-13, S-17, S-18, S-20 i S-27 chodzą jako scenariusze w CI; mutanty, które przeżyły na rdzeniu, są zabite testem albo przyjęte z powodem; ręczna lista po deployu zawiera tylko pozycje z `verification-scope.md`, sekcja „Co zostanie ręczne”.
- **Scope anchors:** FR-033, FR-034, FR-023 (doprecyzowanie Q-13), FR-005, FR-006, FR-007, FR-011a, FR-014, FR-015, NFR `prd.md:275` (sygnał dla operatora, doprecyzowanie PRD v3), NFR `prd.md:289` (nowe źródło = pozyskanie + mapowanie), NFR `prd.md:293` (kompletny zestaw testów autoryzacji); `verification-scope.md` (kandydaci na zakres, „done when”); ustalenia audytu i rozbieżności destylacji wymienione przy wycinkach.

## Vision recap

Na Wykopie (#gielda) i Reddicie codziennie pojawiają się setki postów o inwestowaniu; Szczur Radar pobiera je, a LLM według zamkniętego kontraktu opisuje, czego dotyczą, jakie stanowisko autor zajmuje wobec każdej spółki lub tematu i jakie tezy przedstawia — każdą z dosłownym cytatem. M-1–M-3 zbudowały to dla dwóch źródeł na rdzeniu, który nie milczy i nie gubi danych. M-4 sprawdza, czy testy tego rdzenia naprawdę łapią błędy (audyt po M-3: trzy zepsucia SPA przeszły przez wszystkie testy), i domyka to, czego nauczyły pierwsze noce z Redditem.

## North star

**S-31: Ręczne weryfikacje PR env chodzą jako scenariusze w CI** — to pierwsze kryterium „done when” z `verification-scope.md` i najbardziej bezpośredni dowód celu `quality`: weryfikacja wycinka przestaje być klikaniem w PR env, a regresja chodzi przy każdym PR.

> „Gwiazda przewodnia” (north star) oznacza tu najmniejszy wycinek od początku do końca, którego udane dostarczenie potwierdza cel kamienia milowego — dlatego stoi tak wcześnie, jak pozwalają jego warunki wstępne (zaraz po S-30, bez którego CI bywa czerwone bez powodu).

## At a glance

| ID   | Change ID                         | Outcome (user can …)                                                                                                               | Prerequisites            | PRD refs                                                                                   | Status   |
| ---- | --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------ | ------------------------------------------------------------------------------------------ | -------- |
| S-30 | it-test-isolation                 | wynik CI nie zależy od kolejności klas testów integracyjnych; test nie widzi danych zostawionych przez inny test                   | —                        | `verification-scope.md` „Regresja w CI”; run CI 41784416932                               | done |
| S-31 | pr-env-scenarios-in-ci            | ręczne weryfikacje PR env z S-13, S-17, S-18, S-20, S-27 chodzą w CI jako scenariusze na pełnym API z atrapami źródeł i LLM         | S-30                     | `verification-scope.md` „Scenariusze API”, „done when”; NFR `prd.md:275`                  | proposed |
| S-32 | provider-refusal-breaker          | seria odmów dostawcy zatrzymuje przebieg, zanim zużyje próby pozostałych postów, i daje sygnał jak każdy `failed`                  | —                        | NFR `prd.md:275` (doprecyzowanie PRD v3); audyt §6 R-41; destylacja #3                     | ready    |
| S-33 | restore-dead-posts                | admin przywraca zaznaczone martwe posty do oceny, a ich próby są zerowane                                                          | —                        | FR-034, FR-015; destylacja Q-18                                                            | ready    |
| S-34 | dead-posts-daily-digest           | operator dostaje raz dziennie podsumowanie martwych postów zamiast alarmu przy każdym przebiegu z martwym postem                    | —                        | NFR `prd.md:275` (doprecyzowanie PRD v3); destylacja #5, R-42, Q-14                        | ready    |
| S-35 | per-source-failure-quality        | operator widzi udział nieudanych prób według klas błędu osobno dla każdego źródła i dostaje sygnał po przekroczeniu progu           | —                        | FR-033; destylacja #1, R-15                                                                | ready    |
| S-36 | statement-language-quality        | operator widzi udział tez w innym języku niż post osobno dla każdego źródła, z sygnałem po przekroczeniu progu                     | S-35                     | FR-033; `contract-v2.md` §8 (Q-16); destylacja #1, D-14, R-38                              | proposed |
| S-37 | source-native-post-labels         | użytkownik widzi typ posta słowami jego źródła: „wpis”/„znalezisko” na Wykopie, „post”/„link” na Reddicie                          | —                        | FR-023 (doprecyzowanie Q-13); destylacja #4, D-12, R-45                                    | done     |
| S-38 | post-store-loss-visible           | post odrzucony przy zapisie przez inne ograniczenie niż duplikat jest policzony i widoczny, a nie znika po cichu                   | —                        | FR-005, NFR `prd.md:275`; audyt §6 D-15; destylacja #2, D-15                               | ready    |
| S-39 | derived-persistence-guards        | konwerter zapisujący inną wartość niż enum, złożony `CHECK` z literałem i nowy koniec przebiegu bez powiadomienia czerwienią CI     | —                        | NFR `prd.md:289`; audyt §1 R-17, X1, X2, §5 R-23; destylacja #6                            | ready    |
| S-40 | derived-route-guards              | każda trasa `/api/admin/**` daje 403 zwykłemu użytkownikowi, a każda `/internal/**` 401 bez tokenu — bez ręcznej listy tras        | —                        | NFR `prd.md:293`, FR-014, FR-015; audyt §1 R-30                                            | ready    |
| S-41 | api-contract-shapes-round-two     | każdy kształt odpowiedzi, który czyta SPA, ma plik kontraktu z wariantami `null`, a pole zawsze wysyłane przez API jest wymagane w TS | —                        | FR-006; audyt §2, X3; `verification-scope.md`                                              | ready    |
| S-42 | spa-rules-registry-round-two      | porównanie kodu błędu, limit rozmiaru strony i enumy spoza rejestru są pilnowane testem albo kompilatorem                          | —                        | FR-006; audyt §1 R-22, R-43, X5                                                            | ready    |
| S-43 | contract-bound-mocks              | atrapa API w SPA i atrapy narzędzi odpowiadają tak jak prawdziwe API, a rozjazd czerwieni test                                     | S-41                     | audyt §3, X4, „Materiał dla M-4” p. 4; `verification-scope.md` „Granice z M-3 i M-5”      | proposed |
| S-44 | feed-regression-and-paging-bounds | feed ma testy regresji znanych ścieżek, a ogromny numer strony i NUL w linku dają 400 zamiast błędu serwera albo zawieszonego feedu | —                        | FR-006, FR-007, FR-011a; audyt §4, X6                                                      | done |
| S-45 | core-mutation-testing             | wiadomo, które testy rdzenia łapią błędy: mutanty, które przeżyły, są zabite testem albo przyjęte z powodem                         | S-30, S-32, S-33, S-38   | `verification-scope.md` „Poprawność testów backendu”, „done when”                         | proposed |

## Streams

Navigation aid — groups items that share a Prerequisites chain. Canonical ordering still lives in the dependency graph below; this table is the proposed reading order across parallel tracks.

| Stream | Theme                               | Chain                                     | Note                                                                                                                    |
| ------ | ----------------------------------- | ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| A      | Weryfikacja w CI                    | `S-30` → `S-31` · `S-45`                  | Gwiazda przewodnia zaraz po stabilnym CI; `S-45` dołącza też po `S-32`, `S-33`, `S-38` ze strumienia B.                 |
| B      | Przebieg nie gubi i nie milczy      | `S-32` · `S-33` · `S-34` · `S-38`         | Wymagania PRD v3 z nocy z Redditem i cicha utrata posta; niezależne, ale dotykają tych samych pakietów przebiegu.       |
| C      | Ocena per źródło                    | `S-35` → `S-36` · `S-37`                  | FR-033 w dwóch krokach (klasy błędów, potem język tez) i słowa źródła przy typie posta.                                 |
| D      | Strażnicy wyprowadzani automatycznie | `S-39` · `S-40`                          | Przy celu `quality` strażnik ma liczyć zbiór z kodu; eksperymenty X1 i X2 przeszły przez dzisiejsze ręczne listy.       |
| E      | Kontrakt i atrapy                   | `S-41` → `S-43` · `S-42` · `S-44`         | Druga runda kontraktu SPA ↔ API; atrapa zgodna z kontraktem to podłoga pod testy E2E w M-5.                             |

## Baseline

What's already in place in the codebase as of `2026-11-04` (`main` `244ef6c`; z audytu `post-m3-verification-audit` i roadmapy M-3, potwierdzone przez użytkownika).
Wycinki niżej zakładają te warstwy i ich nie budują od nowa.

- **Frontend:** present — SPA Vue (feed z filtrem i etykietą pochodzenia, widok posta, `/admin`, strona przebiegu, dzwonek, aktywacja); 513 testów Vitest; kontrakt z API w `szczur-radar-web/src/contract/` (S-24); bez Playwrighta.
- **Backend / API:** present — przebieg jako sekwencja kroków w Javie z izolacją źródeł (S-20), dwa źródła: Wykop i Reddit przez Arctic Shift (S-27); kontrakt oceny V2 egzekwowany kodem.
- **Data:** present — Postgres, Flyway V1–V13 (`posts.channel` od V13); `ClosedSetsIT` z ręcznym rejestrem 24 kolumn.
- **Auth:** present — Spring Session JDBC, role `user`/`admin`, deny-by-default z `AuthorizationCompletenessIT` (trasy publiczne wyliczane automatycznie, trasy admina z ręcznej listy 10 tras), token crona dla `/internal/**`.
- **Deploy / infra:** present z luką — CI na darmowych runnerach GitHuba od 2026-10-24 (repo publiczne), pula `szczur-ci` jako rezerwa; luka: wynik testów integracyjnych zależy od kolejności klas (`PostRepositoryIT` po `MigrationsIT`, run 41784416932 na `main` 2026-11-04).
- **Observability:** present z luką — alarmy healthchecks.io → Telegram, powiadomienia w aplikacji, „Dni bez danych”; luka: brak miary jakości ocen na produkcji (destylacja #1) i brak testów mutacyjnych.

## Foundations

Brak — każda praca przekrojowa M-4 ma widoczny skutek dla użytkownika, operatora albo CI i jest wycinkiem. Plan testów (`/10x-test-plan`) nie jest fundamentem: zakres M-4 ustalają `verification-scope.md` i audyt (wpis w `## Parked`).

## Slices

### S-30: Wynik CI nie zależy od kolejności testów

- **Outcome:** wynik CI nie zależy od kolejności, w jakiej uruchamiają się klasy testów integracyjnych: test sprawdza tylko dane, które sam zasiał, a test, który zapisuje dane poza transakcją, sprząta po sobie; kolejność, która dziś czerwieni `main`, jest sprawdzana wprost.
- **Change ID:** it-test-isolation
- **PRD refs:** `verification-scope.md` „Regresja w CI”; run CI 41784416932 (`main`, 2026-11-04: `PostRepositoryIT` zobaczył 6 postów zostawionych przez `MigrationsIT`)
- **Prerequisites:** —
- **Parallel with:** każdy wycinek poza S-31 i S-45 (zależą od S-30)
- **Blockers:** —
- **Unknowns:** —
- **Risk:** pierwszy, bo do jego końca każdy merge może dać czerwony `main` bez błędu w kodzie (ponawiać run, nigdy nie omijać bramki); inne klasy z tym samym wzorcem (zapis poza transakcją, zapytanie po całej bazie) mogą się ujawnić dopiero przy innej kolejności.
- **Status:** ready

### S-31: Ręczne weryfikacje PR env chodzą jako scenariusze w CI

- **Outcome:** ręczne kroki weryfikacji PR env z wycinków S-13, S-17, S-18, S-20 i S-27 chodzą w CI jako scenariusze na pełnym API z Postgresem, z atrapami źródeł i LLM z prawdziwych payloadów; to, czego CI nie zobaczy, jest na krótkiej, nazwanej liście ręcznej.
- **Change ID:** pr-env-scenarios-in-ci
- **PRD refs:** `verification-scope.md` „Scenariusze API zamiast ręcznego klikania”, „Kandydat na done when”; NFR `prd.md:275`
- **Prerequisites:** S-30
- **Parallel with:** każdy wycinek poza S-30
- **Blockers:** —
- **Unknowns:**
  - Które kroki z sekcji S-13, S-17, S-18, S-20 i S-27 w `deploy-plan.md` da się odtworzyć w CI, a które zostają na liście ręcznej (Telegram, deploy na Railway, prawdziwe API, harmonogram cronów)? — Owner: team (`/10x-plan`). Block: no.
  - Ile czasu CI na darmowych runnerach mogą zająć scenariusze? — Owner: user. Block: no.
- **Risk:** gwiazda przewodnia; scenariusze sprawdzają zachowanie przez API, więc łapią to, co strażnicy przepuścili (X1, X2), ale wydłużają job `backend` — kontrola czasu CI jest częścią wycinka.
- **Status:** proposed

### S-32: Seria odmów dostawcy zatrzymuje przebieg

- **Outcome:** seria odmów dostawcy na kolejnych postach (np. moderacja albo odrzucane żądanie dla każdego posta) zatrzymuje przebieg, zanim zużyje próby pozostałych postów; przebieg kończy się `failed` i daje sygnał jak każdy `failed`, a posty, których nie zdążył ocenić, wracają bez straty próby.
- **Change ID:** provider-refusal-breaker
- **PRD refs:** NFR `prd.md:275` (doprecyzowanie PRD v3, „Wyłącznik serii odmów dostawcy”); audyt §6 R-41; destylacja #3, R-41, R-04, Q-18
- **Prerequisites:** —
- **Parallel with:** każdy wycinek poza S-45 (zależy od S-32)
- **Blockers:** —
- **Unknowns:**
  - Ile odmów z rzędu to seria i czy pojedyncza udana ocena ją zeruje, jak dziś zeruje licznik awarii? — Owner: user (`/10x-plan`). Block: no.
- **Risk:** dziś seria jest cicha przez dwie noce, a w trzeciej posty są nieodwracalnie `dead` (`ConcurrentAssessmentDispatcher.java:298-306`); zmienia regułę dyspozytora, którą od S-09 i S-17 pilnują testy, więc stoi przed S-45.
- **Status:** ready

### S-33: Admin przywraca martwe posty

- **Outcome:** admin zaznacza martwe posty na liście w `/admin` i przywraca je do oceny: post traci martwy stan, a jego próby oceny są zerowane, więc kolejny przebieg ocenia go od nowa.
- **Change ID:** restore-dead-posts
- **PRD refs:** FR-034, FR-015 (doprecyzowanie PRD v3, grupa (a)); `prd.md` § Access Control; destylacja Q-18, R-04
- **Prerequisites:** —
- **Parallel with:** każdy wycinek poza S-45 (zależy od S-33)
- **Blockers:** —
- **Unknowns:** —
- **Risk:** nowa trasa admina i nowe przejście stanu posta; obie mają już strażników (lista tras admina, `PostColumnsIT` dla przejść), więc wycinek dopisuje do nich wpisy, a S-40 później usunie ręczną listę.
- **Status:** ready

### S-34: Dzienne podsumowanie martwych postów zamiast alarmu co przebieg

- **Outcome:** przebieg `partial` z martwym postem nie daje już wiadomości `RUN_NEEDS_ATTENTION` w Telegramie; operator dostaje raz dziennie podsumowanie martwych postów z ostatniej doby, a pozostałe powody wiadomości zostają bez zmian.
- **Change ID:** dead-posts-daily-digest
- **PRD refs:** NFR `prd.md:275` (doprecyzowanie PRD v3, „Martwe posty bez alarmu na każdy przebieg”); destylacja #5, R-42, D-13, Q-14
- **Prerequisites:** —
- **Parallel with:** każdy wycinek poza S-45
- **Blockers:** —
- **Unknowns:**
  - Kto i kiedy wysyła podsumowanie (cron, API po nocnym przebiegu, healthchecks.io) i czy dzwonek w aplikacji dalej pokazuje martwe posty? — Owner: user (`/10x-plan`; PRD v3 zostawia to planowi). Block: no.
- **Risk:** zmienia regułę „wymaga uwagi” wspólną dla dzwonka, strony przebiegu i alarmu (S-22); reguła ma zostać jedna, więc różnica dotyczy tylko kanału, nie powodu.
- **Status:** ready

### S-35: Jakość ocen per źródło — nieudane próby według klas

- **Outcome:** operator widzi osobno dla każdego źródła udział nieudanych prób oceny według klas błędu, liczony z danych produkcyjnych, a przekroczenie progu daje mu sygnał bez czytania logów.
- **Change ID:** per-source-failure-quality
- **PRD refs:** FR-033 (nieudane próby według klas); `contract-v2.md` §9 (znaczenie klas błędów); destylacja #1, R-15
- **Prerequisites:** —
- **Parallel with:** każdy wycinek poza S-36 (zależy od S-35)
- **Blockers:** —
- **Unknowns:**
  - Jakie progi i w jakim oknie (np. ostatnie 7 nocy)? Punkt odniesienia: S-28 zmierzył 17,3 % nieudanych prób na Reddicie wobec 2,3 % na Wykopie, a noce z #121 dały 16,6 % na pierwszej próbie. — Owner: user. Block: no (rozstrzyga wywiad `/10x-plan`).
- **Risk:** przejmuje część wpisu Parked „Ochrona przed dryfem modelu” (alarm na rosnący udział klas błędu); próg zbyt niski da alarm co noc, jak martwe posty przed S-34.
- **Status:** ready

### S-36: Jakość ocen per źródło — tezy w innym języku niż post

- **Outcome:** operator widzi osobno dla każdego źródła udział tez (`statement`) w innym języku niż post, a przekroczenie progu daje sygnał tym samym kanałem co S-35.
- **Change ID:** statement-language-quality
- **PRD refs:** FR-033 (tezy w innym języku niż post); `contract-v2.md` §8 (doprecyzowanie Q-16: reguła bez wyjątków, pilnowana pomiarem, nie walidatorem); destylacja #1, D-14, R-38
- **Prerequisites:** S-35
- **Parallel with:** każdy wycinek poza S-35
- **Blockers:** —
- **Unknowns:**
  - Skąd wiadomo, w jakim języku jest post i teza: pole w wyjściu modelu (nowa `PROMPT_VERSION`, ścieżka §16 kontraktu) czy wykrywanie w backendzie bez LLM; i czy przy okazji powstaje `posts.language` (wpis w `## Parked`)? — Owner: user + właściciel kontraktu. Block: no (rozstrzyga `/10x-plan`).
  - Jaki próg? S-28 zmierzył 22,1 % tez w innym języku niż post na Reddicie. — Owner: user. Block: no.
- **Risk:** wybór wykrywania języka może zmienić prompt albo schemat, więc stoi po S-35, który ustala kanał sygnału i okno pomiaru.
- **Status:** proposed

### S-37: Typ posta słowami jego źródła

- **Outcome:** użytkownik widzi przy poście Reddita „post” albo „link”, a przy poście Wykopu „wpis” albo „znalezisko” — na karcie w feedzie i na stronie szczegółów; słowa Wykopu nie pojawiają się przy postach Reddita.
- **Change ID:** source-native-post-labels
- **PRD refs:** FR-023 (doprecyzowanie PRD v3, decyzja Q-13); destylacja #4, D-12, R-45, B-11
- **Prerequisites:** —
- **Parallel with:** każdy wycinek
- **Blockers:** —
- **Unknowns:** —
- **Risk:** mały wycinek SPA; słowa zależą od źródła, więc nie mieszczą się w płaskim `labels` — tabela źródło × typ (`ITEM_TYPE_WORDS`) z własnym testem w `rules.test.ts`; `rules.json` i API bez zmian.
- **Status:** done

### S-38: Post odrzucony przy zapisie nie znika po cichu

- **Outcome:** post, którego zapis odrzuca inne ograniczenie bazy niż klucz unikalny (np. `CHECK` albo `NOT NULL`), jest policzony w przebiegu (pominięty albo błąd) i daje powód „wymaga uwagi”; dla każdego przebiegu pobrane = nowe posty + duplikaty + pominięte.
- **Change ID:** post-store-loss-visible
- **PRD refs:** FR-005, NFR `prd.md:275`; audyt §6 D-15; destylacja #2, D-15, R-06, R-01
- **Prerequisites:** —
- **Parallel with:** każdy wycinek poza S-45 (zależy od S-38)
- **Blockers:** —
- **Unknowns:** —
- **Risk:** `PostStore.java:68-79` połyka dziś każde naruszenie jako „zapisany przez inny przebieg”, a `PostStoreTest:84-92` to utrwala; realna droga na dzisiejszych danych jest mało prawdopodobna, ryzyko leży w przyszłych ograniczeniach i nowych źródłach.
- **Status:** ready

### S-39: Strażnicy zapisu i końca przebiegu liczą zbiory z kodu

- **Outcome:** CI czerwienieje, gdy konwerter enuma zapisuje inną wartość, niż dopuszcza `CHECK`, gdy złożony `CHECK` ma literał spoza enuma i gdy nowa ścieżka kończąca przebieg nie wywołuje powiadomienia (R-23).
- **Change ID:** derived-persistence-guards
- **PRD refs:** NFR `prd.md:289` (rejestracja źródła w zbiorach `CHECK`); audyt §1 R-17, eksperymenty X1, X2; audyt §5 R-23; destylacja #6, R-17, R-23
- **Prerequisites:** —
- **Parallel with:** każdy wycinek
- **Blockers:** —
- **Unknowns:** —
- **Risk:** X1 i X2 przeszły przez `ClosedSetsIT` i złapały je tylko testy zachowania; strażnik R-23 ma wzór w `PostColumnsIT:178-192`. Strażnik zbyt ścisły zablokuje uprawnione zmiany, więc wycinek nazywa wyjątki jawnie.
- **Status:** ready

### S-40: Strażnik tras admina i `/internal` bez ręcznej listy

- **Outcome:** każda zmapowana trasa `/api/admin/**` daje 403 zalogowanemu zwykłemu użytkownikowi, a każda trasa `/internal/**` daje 401 bez tokenu crona; zbiory tras są wyliczane z mapowań, a nie z ręcznej listy.
- **Change ID:** derived-route-guards
- **PRD refs:** NFR `prd.md:293` (kompletny zestaw testów autoryzacji), FR-014, FR-015; audyt §1 R-30
- **Prerequisites:** —
- **Parallel with:** każdy wycinek
- **Blockers:** —
- **Unknowns:** —
- **Risk:** dziś 403 sprawdza ręczna lista 10 tras, a `/internal/**` tylko dwa zakodowane adresy; nowa trasa admina z S-33 jest pierwszą, która by tę lukę pokazała.
- **Status:** ready

### S-41: Kontrakt SPA ↔ API, druga runda — kształty

- **Outcome:** każdy kształt odpowiedzi, który czyta SPA (konto, aktywacja, powiadomienia z `run: null`, wynik źródła, dni bez danych, koperty stron), ma plik kontraktu sprawdzany po obu stronach, warianty `null` są w kontrakcie, a pole, które API zawsze wysyła, jest wymagane w typie TS.
- **Change ID:** api-contract-shapes-round-two
- **PRD refs:** FR-006; audyt §2 (tabela kształtów bez powiązania z TS), eksperyment X3; `verification-scope.md`
- **Prerequisites:** —
- **Parallel with:** każdy wycinek poza S-43 (zależy od S-41)
- **Blockers:** —
- **Unknowns:** —
- **Risk:** X3 (usunięcie `attentionReasons` z pliku i próbki) przeszło przez 513 testów i `vue-tsc`, bo pole jest opcjonalne w TS; wycinek zmienia typy SPA w wielu plikach naraz.
- **Status:** ready

### S-42: Rejestr reguł SPA, druga runda

- **Outcome:** porównanie kodu błędu w SPA (`err.detail === '<kod>'`) jest pilnowane kompilatorem albo skanem, limit rozmiaru strony i enumy dziś spoza `rules.json` są w rejestrze, a SPA nie liczy sama warunku powodu „wymaga uwagi”.
- **Change ID:** spa-rules-registry-round-two
- **PRD refs:** FR-006; audyt §1 R-22 (kopia warunku `skipped` w `runs.ts`), R-43 (kopie limitu strony, enumy spoza rejestru), eksperyment X5
- **Prerequisites:** —
- **Parallel with:** każdy wycinek
- **Blockers:** —
- **Unknowns:** —
- **Risk:** X5 przeszedł przez skan kodów błędów i `vue-tsc`; typy SPA sprawdza w CI tylko budowa obrazu web, co wycinek bierze pod uwagę przy wyborze miejsca strażnika.
- **Status:** ready

### S-43: Atrapy odpowiadają jak prawdziwe API

- **Outcome:** `dev/mock-api.js` odpowiada z plików kontraktu albo test porównuje jego odpowiedzi z kontraktem (dziś 7 rozbieżności, m.in. brak Reddita, brak logowania, start przebiegu zawsze 409), a atrapy narzędzi (`pipeline-trigger`, stuby `deploy-gate`) czytają statusy i kształty z rejestru.
- **Change ID:** contract-bound-mocks
- **PRD refs:** audyt §3, eksperyment X4, „Materiał dla M-4” p. 4; `verification-scope.md` „Granice z M-3 i M-5” (atrapa jako podłoga pod testy E2E)
- **Prerequisites:** S-41
- **Parallel with:** każdy wycinek poza S-41
- **Blockers:** —
- **Unknowns:** —
- **Risk:** M-5 postawi testy E2E na tej atrapie (decyzja 2026-11-04: cały Playwright w M-5), więc bez tego wycinka E2E sprawdzałyby atrapę, nie API.
- **Status:** proposed

### S-44: Feed z testami regresji i granicą numeru strony

- **Outcome:** feed ma testy regresji znanych ścieżek (remisy daty z filtrem, `kind` i `direction` na jednym celu, najnowsza ocena `ok` przy późniejszej `failed`, strona za końcem, stanowisko ostrożne), a ogromny numer strony na listach postów, nieudanych postów i przebiegów oraz NUL w linku feedu dają 400 zamiast błędu serwera albo zawieszonego feedu.
- **Change ID:** feed-regression-and-paging-bounds
- **PRD refs:** FR-006, FR-007, FR-011a; audyt §4, eksperyment X6
- **Prerequisites:** —
- **Parallel with:** każdy wycinek
- **Blockers:** —
- **Unknowns:** —
- **Risk:** X6 to potwierdzony defekt (`Page offset exceeds Integer.MAX_VALUE` jako nieobsłużony wyjątek); ta sama wada w `FailedPostController` i `PipelineRunController` nie była uruchamiana, więc wycinek ją najpierw sprawdza.
- **Status:** done

### S-45: Testy mutacyjne rdzenia

- **Outcome:** testy mutacyjne (PIT) mierzą rdzeń — przebieg i warunkowe zapisy stanu, walidator kontraktu V2, rozliczanie prób oceny — a każdy mutant, który przeżył, jest zabity testem albo przyjęty z zapisanym powodem; pomiar da się powtórzyć w CI.
- **Change ID:** core-mutation-testing
- **PRD refs:** `verification-scope.md` „Poprawność testów backendu”, „Kandydat na done when”
- **Prerequisites:** S-30, S-32, S-33, S-38
- **Parallel with:** S-31, S-34, S-35, S-36, S-37, S-39, S-40, S-41, S-42, S-43, S-44
- **Blockers:** —
- **Unknowns:**
  - Czy PIT chodzi przy każdym PR, czy tylko na żądanie albo na `main` (czas CI; `ci.yml` jest jedynym workflow i nie ma filtrów ścieżek)? — Owner: user (`/10x-plan`). Block: no.
- **Risk:** ostatni, bo S-32, S-33 i S-38 zmieniają rdzeń, który mierzy; zmierzony wcześniej dałby wynik do powtórki. Testy integracyjne (Testcontainers) są wolne pod PIT, więc zakres pomiaru wybiera plan.
- **Status:** proposed

## Backlog Handoff

| Roadmap ID | Change ID                         | Suggested issue title                                                     | Ready for `/10x-plan` | Notes                                                        |
| ---------- | --------------------------------- | ------------------------------------------------------------------------- | --------------------- | ------------------------------------------------------------ |
| S-30       | it-test-isolation                 | S-30: wynik CI nie zależy od kolejności testów integracyjnych             | yes                   | Run `/10x-plan it-test-isolation`; pierwszy (decyzja 2026-11-04) |
| S-31       | pr-env-scenarios-in-ci            | S-31: ręczne weryfikacje PR env jako scenariusze w CI (gwiazda przewodnia M-4) | no                | Po S-30                                                      |
| S-32       | provider-refusal-breaker          | S-32: seria odmów dostawcy zatrzymuje przebieg                            | yes                   | Run `/10x-plan provider-refusal-breaker`                     |
| S-33       | restore-dead-posts                | S-33: admin przywraca martwe posty                                        | yes                   | Run `/10x-plan restore-dead-posts`                           |
| S-34       | dead-posts-daily-digest           | S-34: dzienne podsumowanie martwych postów zamiast alarmu co przebieg     | yes                   | Run `/10x-plan dead-posts-daily-digest`                      |
| S-35       | per-source-failure-quality        | S-35: jakość ocen per źródło — nieudane próby według klas                 | yes                   | Run `/10x-plan per-source-failure-quality`                   |
| S-36       | statement-language-quality        | S-36: jakość ocen per źródło — tezy w innym języku niż post               | no                    | Po S-35                                                      |
| S-37       | source-native-post-labels         | S-37: typ posta słowami jego źródła                                       | yes                   | Run `/10x-plan source-native-post-labels`                    |
| S-38       | post-store-loss-visible           | S-38: post odrzucony przy zapisie nie znika po cichu                      | yes                   | Run `/10x-plan post-store-loss-visible`                      |
| S-39       | derived-persistence-guards        | S-39: strażnicy zapisu i końca przebiegu liczą zbiory z kodu              | yes                   | Run `/10x-plan derived-persistence-guards`                   |
| S-40       | derived-route-guards              | S-40: strażnik tras admina i `/internal` bez ręcznej listy                | yes                   | Run `/10x-plan derived-route-guards`                         |
| S-41       | api-contract-shapes-round-two     | S-41: kontrakt SPA ↔ API, druga runda — kształty                          | yes                   | Run `/10x-plan api-contract-shapes-round-two`                |
| S-42       | spa-rules-registry-round-two      | S-42: rejestr reguł SPA, druga runda                                      | yes                   | Run `/10x-plan spa-rules-registry-round-two`                 |
| S-43       | contract-bound-mocks              | S-43: atrapy odpowiadają jak prawdziwe API                                | no                    | Po S-41                                                      |
| S-44       | feed-regression-and-paging-bounds | S-44: feed z testami regresji i granicą numeru strony                     | yes                   | Run `/10x-plan feed-regression-and-paging-bounds`            |
| S-45       | core-mutation-testing             | S-45: testy mutacyjne rdzenia                                             | no                    | Po S-30, S-32, S-33, S-38                                    |

## Open Roadmap Questions

1. **Weryfikacja produkcji z M-1:** lokalizacja bazy dla filtra S-04 (runbook z issue #17, krok 2: `C`/`POSIX` po cichu psuje `KĘTY`→`Kęty`) i pierwsza noc S-06 (runbook z issue #19). Owner: operator. Block: — (przeniesione z M-2; wynik `C`/`POSIX` dałby nowy wycinek).
2. **Ustalenia z żywego Railway** (audyt M-2, Open Questions 2–7): czy redeploy po `config apply` zmiany, która deployuje, czeka na CI (S-21); czy reguła „deployment crashed” obejmuje crash loop API; okno SIGTERM→SIGKILL; przyczyna przebiegu „trwającego” 22 h z 2026-10-09. Owner: operator. Block: —. Rozstrzygnięte przez audyt `post-m2-audit` (P2): merge samej dokumentacji daje `SKIPPED` na wszystkich usługach; apply `source.checkSuites` nie wywołuje żadnego deploymentu; deployment API z czerwonym CI jest pomijany przez Wait for CI.
3. **Kanał i próg dla FR-025 (powiadomienia o obiecującym trendzie)** — nie ustalono, czym jest „obiecujący trend” liczbowo ani gdzie ma trafiać powiadomienie. Owner: użytkownik. Block: — (FR-025 odroczone).
4. **Budżet kosztów LLM na przebieg** — guardrail PRD każe minimalizować koszt zapytań, ale nie podaje pułapu (na dzień ani na post); twardy pułap to $10/mies. na kluczu OpenRouter produkcji. Drugie źródło zwiększy liczbę ocen. Owner: użytkownik. Rozstrzygnięte 2026-10-31 w S-28: limit $10/mies. zostaje, bez osobnego pułapu; koszt łączny z Wykopem to $3,21/mies. na `flex` (górna granica $4,67), Reddit włączany.
5. **PRD ma status `draft` i ostatnią zmianę 2026-10-13** — nowsze decyzje żyją w blokach „Doprecyzowanie” i w roadmapie (destylacja, sekcja „Stale documents”: FR-029 krótka sesja vs 30 dni, „zakres dat/godzin”, FR-022 warunkowe). Czy odświeżyć PRD do v3 przed planowaniem Reddita? Owner: użytkownik. Block: — (destylacja D-02, D-03, D-10). Rozstrzygnięte 2026-11-04: PRD v3 (`prd.md` `version: 3`) zamyka D-02, D-03, D-10 i D-16 oraz zapisuje decyzje Q-13, Q-14, Q-16 i Q-18.
6. **Pytania z destylacji domeny bez odpowiedzi** (`domain-distillation.md`, sekcja Questions): Q-08 (kto woła `/api/admin/llm/check`), Q-11 (usuwanie lub deaktywacja konta), Q-12 (`content_hash`, `slug`, `tags` bez czytelnika), Q-15 (czy feed domyślnie pokazuje posty `not_investment_related`; do M-5). Owner: właściciel produktu. Block: — (żaden wycinek M-4 od nich nie zależy).

## Parked

- **M-5 (kandydat): nowe UI i UX aplikacji webowej z testami E2E (Playwright)** — Why parked: decyzja użytkownika 2026-10-27 (najpierw jako M-4, przesunięte za weryfikację i regresję, żeby przebudowa miała gotową siatkę regresji); projektować raz, gdy feed ma już dwa źródła i oznaczenie źródła (S-27, S-29). Cztery propozycje układu od użytkownika (czytnik z adnotacjami, karta podmiotu, strumień z inspektorem wpisu, przegląd „Co mówi dziś rynek?”) opisane w `context/foundation/ui-ux-proposals.md`; wygląd do ustalenia w M-5, a każdy nowy widok dostaje test E2E. Decyzja użytkownika 2026-11-04: cały Playwright, także konfiguracja i smoke, jest w M-5 (M-4 nie pisze testów obecnych widoków, które M-5 wymieni); testy E2E stoją na atrapie API zgodnej z kontraktem z S-43 (M-4). Kandydaci do tego samego kamienia: FR-008/FR-011b, FR-031, FR-020 oraz dwie pozycje UX niżej (komunikat przy nieudanym odczycie daty, przybliżony koniec przebiegu). Widoki agregujące wokół spółki dotykają `prd.md:314` (rozpoznawanie spółek poza zakresem) i FR-024/FR-025.
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
- **Ochrona przed dryfem modelu (alarm na rosnący udział `schema_violation` / `fabricated_evidence`, cicha zmiana modelu u dostawcy)** — Why parked: z audytu M-2 (B4); poza zakresem M-2, bo wymaga punktu odniesienia jakości, który buduje dopiero „LLM hardening”; `RecordingsReplayTest` w CI łapie dziś dryf parsera, schematu i walidatora. Od M-4 alarm na rosnący udział klas błędu per źródło robi S-35 (FR-033); zostaje cicha zmiana modelu u dostawcy.
- **Porządki granic pakietów: cykl `pipeline` ↔ `notifications`, dwa mechanizmy zapisu `posts` (JPA i SQL), konwertery zależne od `pipeline.run`, duplikaty reguł („najnowsza ocena `ok`”, predykat resztek)** — Why parked: z audytu M-2 (sekcja E, L); nie wpływają dziś na niezawodność. Część zniknie przy S-17; reguła „najnowsza ocena `ok`” idzie z pozycją o wydajności filtra.
- **Drobne ryzyka z audytu M-2 (L): przebieg `queued` przy zamkniętym executorze i koniec zakresu `23:59:59` (A8), brak limitów schematu dla OpenRouter i 3 płatne wywołania przy deterministycznym naruszeniu kontraktu (B8), dodatkowa próba po przerwaniu przy przejściu flex→standard (B10), offset w powtórzonej jesiennej godzinie (B11), okno ponowień Caddy 30 s (C14), fałszywy alarm crona po 90 min (C8)** — Why parked: niska waga; wracają, gdy dotknie ich wycinek z tego samego obszaru.
- **Ocena obrazków w postach (model multimodalny)** — Why parked: nice-to-have po MVP, do sprawdzenia, czy ma sens. PRD v2 i kontrakt V2 oceniają wyłącznie tekst (`prd.md:258`); w pilotażu #gielda ~44% wpisów ma zdjęcie, a część ma sens tylko w obrazku (`frame.md`, „Znane luki”). Zestaw wzorcowy F-02 (flaga `media.photo`, etykiety `unclear` z powodu obrazka, raport fazy 6) pokaże skalę; wdrożenie wymagałoby zmiany PRD, kontraktu (V3: cytat/dowód z obrazka nie jest podciągiem tekstu), wyboru modeli z wejściem obrazkowym i ponownego pomiaru, a koszt wejścia rośnie o tokeny obrazków.
- **Wydajność filtra feedu przy dużej liczbie postów (TODO na przyszłość)** — Why parked: dziś zbędne (~300 postów, zapytania filtra w milisekundach). Pomiar z impl-review S-04 (folder zmiany `filter-by-assessment`, `reviews/impl-review.md`, F6): przy 100k ocenionych postów zapytanie strony z rzadkim filtrem trwa 44 ms, ale zliczanie wyników 0,6–0,95 s (sekwencyjny skan `posts`, najnowsza ocena `ok` i `jsonb_array_elements` dla każdego wiersza); filtr bez trafień płaci ten sam skan. Przy ~270 postach dziennie to ok. rok. TODO: po ~20k postów zmierzyć `EXPLAIN ANALYZE` na produkcji; jeśli zwalnia, projekcja celów (kolumna `latest_ok_assessment_id` w `posts` albo tabela `post_targets`) z indeksem `pg_trgm` — usunie też drugą kopię reguły „najnowsza ocena `ok`” (`PostRepository.findMatching` i `AssessmentRepository.findLatestOk`). Osobna zmiana z pomiarem.
- **Losowe padanie budowy obrazów Dockera na self-hosted runnerach (cache budowania)** — Why parked: zauważone w S-08 `empty-run-signal` (PR #80, 2026-10-19), do zrobienia w kolejnej fali. Joby budujące obraz padają losowo na VM-kach `ci-pc-*` z `failed to prepare extraction snapshot "…": parent snapshot sha256:… …` przy rozpakowaniu obrazu do lokalnego magazynu Dockera (obraz zbudowany, warstwy `CACHED`): `api container smoke test` na `ci-pc-1-1` (run 39286873262) i `db backup test` na `ci-pc-2-1` (run 39295084707, podejście 1); ponowienie jobu przechodzi. Prawdopodobnie cache budowania wskazuje na usuniętą warstwę rodzica (np. po czyszczeniu Dockera na VM). Do sprawdzenia w `tools/ci-runner/`: czyszczenie cache budowania (`docker builder prune -af`) przed jobem albo budowa obrazów w CI bez cache; do tego czasu pomaga `gh run rerun <id> --failed`. Od 2026-10-24 dotyczy tylko rezerwowej puli `szczur-ci` (CI idzie na darmowych runnerach GitHuba).
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
- **Język posta w bazie (`posts.language`)** — Why parked: plan S-28 z 2026-10-30 wyłączył to z pomiaru (prompt i kontrakt bez zmian), a stanowisko właściciela z 2026-10-29 („ma być zapisany”) pozostaje. Warianty: pole `language` w wyjściu modelu (ścieżka §16 kontraktu, nowa `PROMPT_VERSION`) albo detekcja w backendzie bez LLM, w obu kolumna `posts.language` z backfillem. Do tego czasu język da się przybliżyć z `posts.channel`. Wraca, gdy właściciel wróci do tej potrzeby. Od M-4 wybór wykrywania języka rozstrzyga S-36 (FR-033) i może przywrócić tę pozycję.
- **Naprawy oceny Reddita (`claim_ids`, dopasowanie cytatów `D6`, tezy po polsku, filtr megawątków)** — Why parked: decyzja użytkownika 2026-10-31 o włączeniu bez napraw, bo Arctic Shift może przestać dostarczać dane między styczniem a marcem 2027. Wraca, gdyby Reddit został z nami dłużej.
- **Porównanie modeli na postach z Reddita (DeepSeek, Haiku 5.5)** — Why parked: pomysł użytkownika z 2026-10-31, poza S-28. Bez niezależnych etykiet pokaże odrzucenia, koszt i liczbę celów, a nie to, który model ocenia lepiej; Haiku 5.5 nie ma w `models.json`. Zarys: osobna zmiana, około 50 długich postów w osobnym katalogu.
- **Plan testów (`/10x-test-plan`, `context/foundation/test-plan.md`)** — Why parked: decyzja przy otwieraniu M-4 (2026-11-04): zakres weryfikacji M-4 ustalają `verification-scope.md` i audyt `post-m3-verification-audit` (eksperymenty X1–X6, mapa strażników), a S-31 i S-45 wybierają scenariusze i zakres pomiaru w swoich planach. Wraca przed M-5, gdy dojdą ryzyka testów E2E nowego UI.
- **Polityki z destylacji bez incydentu (ranking #7–#10)** — Why parked: cel M-4 `quality` bierze pozycje #1–#6; te są dziś pilnowane albo bez ekspozycji danych: przejścia stanu przebiegu tylko w aplikacji (R-18, R-19, R-21), limiter logowań zerowany przy uśpieniu API (R-33), kaskady bez ścieżki usuwania (R-29, Q-11), pola bez czytelnika (`tags`, `slug`, `content_hash`, Q-12). Każda wraca z decyzją właściciela albo z najbliższą pracą w tym obszarze.

## Milestone History

- **M-1: MVP — ocenione sygnały z Wykopu dla zaproszonych osób** (`mvp-wykop-signal-radar`) — closed 2026-10-17. Wszystkie konieczne FR i historyjki US-01, US-02 dostarczone na produkcji (F-01–F-03, S-01–S-07); domknięcie runbooków produkcji S-04 (#17) i S-06 (#19) przeniesione do Open Roadmap Questions M-2.
- **M-2: MVP Quality & Reliability Hardening** (`mvp-quality-reliability-hardening`) — closed 2026-10-27. Istniejące MVP nie milczy i nie gubi danych: kanał alarmowy poza aplikacją (F-04), sygnały pustej i brakującej nocy, awarie dostawców bez utraty prób, samonaprawa utkniętych przebiegów, bezpieczna bramka wdrożeń, pełna kontrola kopii bazy, kompletne pobieranie z Wykopu, przebieg bez Embabel i błędy wymagające reakcji w Telegramie (F-04, S-08–S-11, S-13–S-19, produkcja zweryfikowana); S-12 zaparkowany decyzją użytkownika (v3.3). Pierwsza noc crona na silniku S-17 (2026-10-28) pod nadzorem F-04; otwarte ustalenie S-14 z issue #73: fałszywa kontrola po apply w `verify-apply.sh` (zmiana bez deployu, `SKIPPED` z równoległego pusha); drugie ustalenie z #73 („deploy `2abc64c` mimo czerwonego CI”) okazało się błędną diagnozą — deployment API został `SKIPPED` przez Wait for CI (audyt `post-m2-audit`).
- **M-3: Drugie źródło na utwardzonym rdzeniu** (`second-source-on-hardened-core`) — closed 2026-11-01. Reddit jest drugim źródłem na produkcji (od 2026-11-01, trzy subreddity), ocenianym tym samym kontraktem co Wykop, na rdzeniu, w którym awaria albo cisza jednego źródła nie psuje przebiegu pozostałych (S-20, gwiazda przewodnia); źródło widoczne w feedzie i świeżość bez kłamstwa przy dwóch źródłach (S-29); obraz jakości i kosztu Reddita przed włączeniem i decyzja o włączeniu bez napraw (S-28); domknięte ślepe punkty z audytu po M-2: bramka wdrożeń przy zmianie bez deployu (S-21), reguły w jednym miejscu (S-22, S-25), zamknięte zbiory i kontrakt SPA ↔ API pilnowane w CI (S-23, S-24), testy silnika bez zależności od zegara (S-26). Weryfikacja nocy z Redditem otwarta w issue #121 — nie blokuje zamknięcia (decyzja użytkownika, v4.3).

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
- **S-28: operator zna obraz konfiguracji produkcyjnej (`openai/gpt-6-luna`, prompt v2.5) na 396 postach z Reddita i koszt łączny z Wykopem oraz zapisał decyzję o włączeniu; pomiar nie rozstrzyga progów kierunku celów i sprzeczności.** — Archived 2026-10-31 → `context/archive/2026-10-30-reddit-quality-and-cost/`. Lesson: —.
- **S-29: użytkownik widzi przy każdym poście, z którego źródła pochodzi, a etykieta „Dane zaktualizowane” nie przesuwa się po przebiegu, który nie objął danego źródła — zgodnie z decyzją, czy świeżość jest wspólna, czy per źródło.** — Archived 2026-10-31 → `context/archive/2026-10-30-source-aware-feed/`. Lesson: —.
- **S-30: wynik CI nie zależy od kolejności, w jakiej uruchamiają się klasy testów integracyjnych: test sprawdza tylko dane, które sam zasiał, a test, który zapisuje dane poza transakcją, sprząta po sobie; kolejność, która dziś czerwieni `main`, jest sprawdzana wprost.** — Archived 2026-11-05 → `context/archive/2026-11-05-it-test-isolation/`. Lesson: —.
- **S-37: użytkownik widzi przy poście Reddita „post” albo „link”, a przy poście Wykopu „wpis” albo „znalezisko” — na karcie w feedzie i na stronie szczegółów; słowa Wykopu nie pojawiają się przy postach Reddita.** — Archived 2026-11-06 → `context/archive/2026-11-06-source-native-post-labels/`. Lesson: —.
- **S-44: feed ma testy regresji znanych ścieżek (remisy daty z filtrem, `kind` i `direction` na jednym celu, najnowsza ocena `ok` przy późniejszej `failed`, strona za końcem, stanowisko ostrożne), a ogromny numer strony na listach postów, nieudanych postów i przebiegów oraz NUL w linku feedu dają 400 zamiast błędu serwera albo zawieszonego feedu.** — Archived 2026-11-06 → `context/archive/2026-11-06-feed-regression-and-paging-bounds/`. Lesson: —.
