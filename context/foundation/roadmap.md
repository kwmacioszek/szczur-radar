---
project: "Szczur Radar"
version: 6
status: draft # draft | active | locked
created: 2026-09-27
updated: 2026-11-22
prd_version: 4
main_goal: quality
top_blocker: capacity # 19 pozycji, jeden recenzent i osoba mergująca; fundament UI dotyka wspólnych plików (tokeny, komponenty, router), więc pierwsze fale są sekwencyjne; darmowe runnery GitHuba muszą zmieścić testy w przeglądarce i zrzuty
milestone_id: new-ui-on-tested-foundation
milestone_seq: 5
milestone_status: open
---

# Roadmap: Szczur Radar

> Derived from (M-5) `context/foundation/prd.md` (v4) + `context/foundation/ui-ux-proposals.md` + audyt `context/changes/post-m4-ui-readiness-audit/research.md` (z decyzjami właściciela z 2026-11-15) + mapa projektu `context/map/repo-map.md` i destylacja `context/domain/domain-distillation.md` (2026-11-15). M-4 derived from `context/foundation/prd.md` (v3) + `context/foundation/verification-scope.md` + audyt `context/changes/post-m3-verification-audit/research.md` + destylacja `context/domain/domain-distillation.md` (ranking, 2026-11-01). M-3 derived from `context/foundation/prd.md` (v2, kolejna transza) + audyt `context/changes/post-m2-audit/research.md` + destylacja `context/domain/domain-distillation.md` + mapa projektu `context/map/repo-map.md` (2026-10-27). M-2 derived from the user's milestone description + `context/map/repo-map.md` (mapa projektu, 2026-10-17) + `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md` (audyt diagnostyczny pipeline'u i operacji, HEAD `5b6eabb`) + PRD v2 NFRs + auto-researched codebase baseline (z mapy i audytu). M-1 derived from `context/foundation/prd.md` (v2) + `context/foundation/llm-assessment/` + `shape-notes.md` + `tech-stack-api.md` / `tech-stack-web.md` / `infrastructure.md` / `deploy-plan.md`.
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
> v5.1 (2026-11-09): S-34 (`dead-posts-daily-digest`) w realizacji (PR #162, issue #144) — niewiadoma rozstrzygnięta: podsumowanie nocy składa API na żądanie crona (`POST /internal/pipeline-runs/nightly-summary` po przebiegach nocy) i wysyła je samo; dzwonek pokazuje martwe posty bez zmian. Decyzja właściciela (2026-11-08/09): kanał zdarzeń API przechodzi z checka healthchecks.io `api-events` na ntfy (`NTFY_URL`, token `NTFY_TOKEN`), healthchecks.io → Telegram zostaje dla cronów i `deploy-health`; podsumowanie zawsze, także przy 0 martwych, z tokenami bez kosztu w $. Outcome S-34 przepisany na to, co zbudowano; doprecyzowanie NFR o sygnale w PRD.

> v5.2 (2026-11-15): M-4 zamknięty (`/10x-roadmap`): S-30–S-45 `done`, wszystkie issues M-4 (#139, #141–#155) zamknięte; wpis w `## Milestone History`. Blok S-30 dostaje `Status: done` (tabela „At a glance” miała go od archiwizacji 2026-11-05, blok został na `ready`). M-5 jeszcze nieotwarty; kandydat w `## Parked` (nowe UI/UX z całym Playwrightem na atrapie z S-43).

> v6 (2026-11-15): otwarty M-5 „Nowe UI na sprawdzonym fundamencie” (`/10x-roadmap`) z PRD v4, `ui-ux-proposals.md` i audytu `post-m4-ui-readiness-audit`; ramy od użytkownika: cel `quality`, gwiazda przewodnia S-51 (strumień z inspektorem oceny), główne ryzyko `capacity`. Decyzje właściciela z 2026-11-15: M-5 „uczciwie duży” — najpierw testy w przeglądarce (F-05) i zrzuty porównawcze z testem dostępności w wersji odchudzonej (F-06), potem motyw, shell, wspólne stany, logowanie i panel admina, potem widoki; agregacja po tekście celu bez rozpoznawania spółek; z nice-to-have tylko sortowanie. Fundamenty F-05, F-06 i wycinki S-46–S-60. Z `## Parked` usunięte: kandydat M-5 (otwarty), FR-008 / FR-011b (wchodzą jako S-54) i język posta w bazie (zrobione w S-36, `posts.language` od V16); dopisane pozycje odłożone poza M-5 i kandydaci na M-6.

> v6.1 (2026-11-19): decyzja właściciela po S-46 — aplikacja ma wyglądać jak dopracowany produkt, a nie surowy panel; makiety z `ui-ux-proposals.md` dawały plan tylko układu. Nowy fundament F-07 (`ui-design-system`): projekt UI/UX całej aplikacji od nowa według standardów i wzorców projektowych (Fable), z makietami jako materiałem wejściowym, akceptowany przez właściciela na prototypach; F-08 (`design-tokens-and-components`) wdraża go w tokenach i komponentach przed S-47 i S-49. S-46 domknięty bez zmian (mechanizm motywu i paleta A), więc wzorce F-06 zmienią się drugi raz w F-08. Wycinki UI wdrażają projekt F-07; otwarte pytanie 7 przechodzi do F-07. Jeśli projekt zmieni architekturę informacji, `/10x-roadmap` przetnie S-47–S-59 na nowo (v6.2).

> v6.2 (2026-11-21): F-07 zarchiwizowany, projekt UI/UX zaakceptowany przez właściciela 2026-11-20 (`context/foundation/ui-design.md`). Projekt zmienił architekturę informacji, więc `/10x-roadmap` przecina F-08 i S-47–S-60 według jego sekcji „Różnice wobec roadmapy”: te same ID, Change ID, issue i zależności, nowe opisy. Najważniejsze zmiany: pasek nawigacji u góry i jedna kolumna zamiast paneli stałych (S-47); panel admina w trzech częściach z własnymi adresami (S-50); zwarta lista postów z oceną w panelu wysuwanym z prawej (S-51); podświetlenie cytatów jako część tego panelu, a nie osobny ekran (S-53); filtry, daty i kolejność w jednym panelu filtrów (S-54–S-56); katalog i karta celu na jednym ekranie (S-57, S-58); przegląd dnia jako ekran startowy (S-59). Słowo „post” zastępuje „wpis”. F-08 jest `ready`. Otwarte pytanie 7 rozstrzygnięte; pytanie 8 (doprecyzowania FR-007, FR-030 i FR-039) zapisane w PRD tego samego dnia. Change ID `stream-and-inspector` i `reader-quote-highlighting` zostają jako stabilne identyfikatory mimo nowych nazw.

## Milestone

**M-5: Nowe UI na sprawdzonym fundamencie** — Status: open

- **Intent:** Aplikacja webowa dostaje nowy, spójny interfejs według zaakceptowanego projektu UI/UX — zwartą listę postów z oceną w panelu bocznym i cytatami podświetlonymi w treści, katalog celów z proporcjami stanowisk i przegląd dnia — zbudowany na wspólnym fundamencie (motywy jasny i ciemny, nawigacja u góry, wspólne stany widoków), a każdy przepływ i wygląd pilnują w CI testy w przeglądarce, więc zmiany UI nie wymagają ręcznego klikania.
- **Source materials:** `context/foundation/prd.md` (v4: FR-035–FR-040, doprecyzowania v4 przy FR-007, FR-008, FR-009, FR-011a, FR-011b, FR-033 i Non-Goals, NFR o testach w przeglądarce `prd.md:328`); `context/foundation/ui-ux-proposals.md` (cztery układy: Czytnik, Karta podmiotu, Strumień + Inspektor, Przegląd; makiety w `docs/visuals/ui-ux-proposals/`); audyt `context/changes/post-m4-ui-readiness-audit/research.md` (fundament SPA, luki API wobec makiet, podłoga E2E, pomiar „kanał jako cel”, sekcja „Decyzje właściciela (2026-11-15)”); mapa projektu `context/map/repo-map.md` i destylacja `context/domain/domain-distillation.md` z 2026-11-15; od v6.1 projekt UI/UX `context/foundation/ui-design.md` (F-07).
- **Done when:** every F-NN and S-NN below is `done`; każdy przepływ użytkownika z NFR `prd.md:328` ma test w przeglądarce w CI; wygląd katalogu komponentów i każdego widoku jest pilnowany zrzutami porównawczymi w obu motywach i przy 375 px, a ręczna weryfikacja wycinka UI to ocena różnic obrazków w PR; każdy widok wdraża zaakceptowany projekt UI/UX z F-07 (opis PR wycinka UI pokazuje zrzut obok prototypu).
- **Scope anchors:** FR-035, FR-036, FR-037, FR-038, FR-039, FR-040 (nowe w PRD v4); FR-007 (doprecyzowanie v4, Q-15), FR-008 i FR-011b (sortowanie wybrane do M-5), FR-009 (doprecyzowanie v4: podświetlenie cytatów, poprzedni i następny wpis), FR-010, FR-011a (doprecyzowanie v4: agregacja po tekście celu), FR-033 (doprecyzowanie v4: „kanał jako cel”, Q-20), FR-006, FR-014, FR-015, FR-021, FR-026–FR-028 (widoki istniejące, przenoszone na nowy fundament); NFR `prd.md:328` (testy w przeglądarce, wizualne i dostępności); Non-Goals `prd.md:369` z doprecyzowaniem v4 (granica: bez rozpoznawania spółek, trendu i automatycznego „warte uwagi”).

## Vision recap

Na Wykopie (#gielda) i Reddicie codziennie pojawiają się setki postów o inwestowaniu; Szczur Radar pobiera je, a LLM według zamkniętego kontraktu opisuje, czego dotyczą, jakie stanowisko autor zajmuje wobec każdej spółki lub tematu i jakie tezy przedstawia — każdą z dosłownym cytatem. M-1–M-4 zbudowały i utwardziły ten rdzeń dla dwóch źródeł oraz siatkę regresji w CI. M-5 zmienia to, jak użytkownik czyta te oceny: z jednej kolumny kart w układ, w którym widać źródło oceny w treści, posty wokół jednego celu i obraz dnia — bez rozpoznawania spółek i bez decyzji za użytkownika, co jest warte uwagi.

## North star

**S-51: Użytkownik przegląda zwartą listę postów i czyta ocenę wybranego posta w panelu bocznym** — to najmniejszy przepływ, który sprawdza cały nowy fundament (motyw, nawigacja, wspólne stany, testy wizualne) na codziennej pracy użytkownika i zastępuje dzisiejszy feed oraz stronę posta; przy celu `quality` stoi zaraz po fundamencie, a nie po nowych funkcjach.

> „Gwiazda przewodnia” (north star) oznacza tu najmniejszy wycinek od początku do końca, którego udane dostarczenie potwierdza cel kamienia milowego — dlatego stoi tak wcześnie, jak pozwalają jego warunki wstępne.

## At a glance

| ID   | Change ID                     | Outcome (user can …)                                                                                                     | Prerequisites | PRD refs                                   | Status   |
| ---- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------ | ------------- | ------------------------------------------ | -------- |
| F-05 | e2e-harness                   | (foundation) testy w przeglądarce w repo i w CI na atrapie API ustawianej z testu; smoke obecnych przepływów            | —             | NFR `prd.md:328`                           | done     |
| F-06 | visual-regression             | (foundation) zrzuty porównawcze katalogu komponentów i widoków w obu motywach i przy 375 px oraz test dostępności w CI   | F-05          | NFR `prd.md:328` (doprecyzowanie v4)       | done |
| F-07 | ui-design-system              | (foundation) zaakceptowany projekt UI/UX całej aplikacji według standardów: architektura informacji, tokeny, komponenty, prototypy | —             | FR-040, NFR `prd.md:328`                   | done |
| F-08 | design-tokens-and-components  | (foundation) tokeny, kroje i komponenty bazowe z projektu F-07 w aplikacji; obecne widoki w nowym wyglądzie, jeszcze w starym układzie | F-07, S-46    | FR-040, NFR `prd.md:328` | done |
| S-46 | light-dark-theme-switch       | użytkownik przełącza motyw jasny i ciemny w nowej palecie, a wybór jest zapamiętany                                      | F-06          | FR-040                                     | done |
| S-47 | app-shell-navigation          | użytkownik porusza się po aplikacji przez pasek u góry (nawigacja według roli, data danych per źródło, motyw, wylogowanie), z treścią w jednej kolumnie | F-08          | FR-007, FR-013, FR-015, FR-021, FR-030 | proposed |
| S-48 | shared-view-states            | użytkownik widzi w każdym widoku to samo ładowanie, pustą listę, błąd z ponowieniem i stronicowanie | S-47          | FR-007, FR-009, NFR `prd.md:328` | proposed |
| S-49 | login-on-shared-foundation    | użytkownik loguje się i aktywuje konto na ekranach zbudowanych ze wspólnych komponentów i tokenów | F-08          | FR-026, FR-027, FR-028 | proposed |
| S-50 | admin-panel-components        | admin pracuje w panelu z trzema częściami pod własnymi adresami (Przebiegi, Jakość ocen, Nieudane posty); strona przebiegu osobno | S-48          | FR-014, FR-015, FR-021, FR-022, FR-033, FR-034 | proposed |
| S-51 | stream-and-inspector          | użytkownik przegląda zwartą listę postów i czyta ocenę wybranego posta w panelu wysuwanym z prawej | S-48          | FR-006, FR-007, FR-009, FR-010, FR-011a | proposed |
| S-52 | investment-posts-by-default   | użytkownik widzi domyślnie tylko posty inwestycyjne, a przełącznik „Inwestycyjne N / Wszystkie M” pokazuje resztę | S-51          | FR-007 (doprecyzowanie v4) | proposed |
| S-53 | reader-quote-highlighting     | użytkownik widzi w panelu posta podświetlone fragmenty, na których oparto ocenę, i przechodzi do poprzedniego i następnego posta | S-51          | FR-009 (doprecyzowanie v4) | proposed |
| S-54 | post-list-sorting             | użytkownik ustawia kolejność listy postów w panelu filtrów (data, źródło, pola oceny) | S-51          | FR-008, FR-011b | proposed |
| S-55 | publication-date-filter       | użytkownik zawęża listę do dnia albo zakresu dat publikacji polami „Od” i „Do” w panelu filtrów | S-51          | FR-035 | proposed |
| S-56 | post-content-search           | użytkownik wyszukuje posty po słowach w tytule i treści w polu „Szukaj w postach” nad listą | S-51          | FR-036 | proposed |
| S-57 | target-catalog                | użytkownik widzi katalog celów z liczbą postów i udziałem stanowisk i przechodzi do postów wybranego celu | S-52          | FR-037, FR-038, FR-011a (doprecyzowanie v4) | proposed |
| S-58 | target-card-stance-shares     | użytkownik otwiera kartę celu w panelu na ekranie katalogu: proporcje stanowisk autorów i wypowiedzi o tym celu | S-57          | FR-038 | proposed |
| S-59 | daily-overview                | użytkownik widzi po zalogowaniu przegląd dnia: pięć liczb dnia, trzy najczęściej omawiane cele i tabelę celów dnia | S-55, S-58    | FR-039 | proposed |
| S-60 | channel-as-target-quality     | operator widzi udział celów równych kanałowi posta per źródło (noc i część „Jakość ocen”) i dostaje sygnał po przekroczeniu progu | S-50          | FR-033 (doprecyzowanie v4) | proposed |

## Streams

Navigation aid — groups items that share a Prerequisites chain. Canonical ordering still lives in the dependency graph below; this table is the proposed reading order across parallel tracks.

| Stream | Theme                    | Chain                                                      | Note                                                                                                                         |
| ------ | ------------------------ | ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| A      | Siatka i fundament UI    | `F-05` → `F-06` → `S-46` · `F-07` → `F-08` → `S-47` → `S-48` → `S-50` · `S-49` | Przy celu `quality` testy działania i wyglądu idą przed przebudową; projekt UI/UX (F-07, zaakceptowany) i jego tokeny (F-08) przed powłoką; wspólne pliki (tokeny, komponenty, router) — fala sekwencyjna. |
| B      | Posty: lista i panel     | `S-51` → `S-52` · `S-53` · `S-54` · `S-55` · `S-56`        | Gwiazda przewodnia zaraz po fundamencie; potem przekroje listy. Od v6.2 S-54, S-55 i S-56 dzielą jeden panel filtrów, a S-53 panel posta — równolegle tylko z ustaloną kolejnością merge. |
| C      | Wokół celu               | `S-57` → `S-58` → `S-59`                                   | Agregacja po tekście celu (FR-011a v4); katalog i karta celu to jeden ekran; dołącza do B przy `S-52` (zakres postów) i `S-55` (dzień dla przeglądu); S-59 zmienia ekran startowy. |
| D      | Jakość ocen              | `S-60`                                                     | Miara „kanał jako cel” chroni widoki C przy zmianie modelu; dołącza do A przy `S-50` (część „Jakość ocen” panelu admina).     |

## Baseline

What's already in place in the codebase as of `2026-11-15` (`main` `ef56ddc`; z mapy projektu i audytu `post-m4-ui-readiness-audit`, potwierdzone przez użytkownika).
Wycinki niżej zakładają te warstwy i ich nie budują od nowa.

- **Frontend:** partial — SPA Vue 3 + TypeScript: feed z filtrem po celu i stanowisku, widok posta z oceną i surowymi danymi, `/admin`, strona przebiegu, dzwonek, logowanie i aktywacja; wspólne komponenty treści (karta posta, ocena, stanowisko, status, przycisk, filtr) i tokeny jednego motywu z `prefers-color-scheme`; brak warstwy układu (`App.vue` to sam `RouterView`), wspólnych stanów, katalogu celów i podświetlenia cytatów; `LoginView` poza listą `CLEANED`, `AdminView` (790 linii) z sekcjami wpisanymi inline.
- **Backend / API:** present — feed `/api/posts` (strona, rozmiar, cel tekstowo, rodzaj celu, stanowisko), post z oceną i `/raw`, trasy admina; brak filtra daty publikacji, wyszukiwania w treści, katalogu i agregacji po celu.
- **Data:** present — Postgres 18 + Flyway V1–V16; ocena w `assessments.output` (JSONB z celami, stanowiskami i tezami z cytatem); `posts.language` od V16.
- **Auth:** present — Spring Session JDBC, role `user`/`admin`, deny-by-default z trasami wyliczanymi z mapowań (S-40), token crona dla `/internal/**`.
- **Deploy / infra:** present z luką — CI na darmowych runnerach GitHuba z bramką wdrożeń, scenariuszami PR env (S-31) i PIT (S-45); luka: brak testów w przeglądarce i joba z przeglądarką.
- **Observability:** present z luką — alarmy ntfy (API) i healthchecks.io → Telegram (crony), podsumowanie nocy, jakość ocen per źródło (S-35, S-36); luka: brak miary „kanał jako cel”.
- **Weryfikacja:** present z luką — kontrakt SPA ↔ API strzeżony po obu stronach (S-41–S-43), atrapa `dev/mock-api.js` obsługuje wszystkie 20 tras czytanych przez SPA; luka: atrapa to skrypt `playwright-cli` z trybami wpisanymi w tekst, bez ustawiania z testu i bez resetu stanu.

## Foundations

### F-05: Testy w przeglądarce w CI na atrapie API

- **Outcome:** (foundation) testy w przeglądarce leżą w repo i chodzą w CI przy każdym PR na atrapie API zgodnej z kontraktem, której tryby (sesja, rola, dane, pusto, błąd, ładowanie) ustawia test; smoke obejmuje obecne przepływy: logowanie i wylogowanie, feed z filtrem, widok posta, panel admina.
- **Change ID:** e2e-harness
- **PRD refs:** NFR `prd.md:328`
- **Unlocks:** F-06; ścieżka weryfikacji „test w przeglądarce w CI” dla S-46–S-59 (każdy wycinek UI kończy się testem swojego przepływu); smoke obecnych widoków jako siatka regresji przebudowy w S-46–S-51.
- **Prerequisites:** —
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:**
  - Czy atrapa zostaje jednym skryptem z trybami podmienianymi przez test, czy staje się modułem z trybami jako parametrami i resetem stanu (S-43 świadomie odłożyło to do M-5)? — Owner: team (`/10x-plan`). Block: no.
  - Brakujące warianty atrapy (wygaśnięcie sesji w trakcie pracy, błąd i ładowanie `/auth/me`, logowania i świeżości, osobne zawieszenie sekcji admina) — które dopisać w F-05, a które w wycinkach, które ich potrzebują? — Owner: team. Block: no.
- **Risk:** pierwszy, bo bez niego przebudowa w S-46–S-51 nie ma czym sprawdzić, że obecne przepływy nadal działają; testy widoków w Vitest mockują API, więc przepięcia układu i routera mogą przez nie przejść.
- **Status:** done

### F-06: Zrzuty porównawcze i test dostępności w CI

- **Outcome:** (foundation) wygląd jest pilnowany zrzutami porównawczymi w CI — katalog komponentów we wszystkich stanach w obu motywach i przy 375 px oraz po jednym zrzucie każdego widoku z danymi w obu motywach, przy 375 px i na desktopie — a każdy z tych widoków przechodzi automatyczny test dostępności; zmiana wyglądu aktualizuje wzorce w tym samym PR.
- **Change ID:** visual-regression
- **PRD refs:** NFR `prd.md:328` (doprecyzowanie v4)
- **Unlocks:** ścieżka weryfikacji „przegląd różnic obrazków w PR” zamiast ręcznych zrzutów dla S-46–S-59; S-46 (nowa paleta zmienia wszystkie wzorce raz, jako świadoma akceptacja).
- **Prerequisites:** F-05
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:**
  - Jak zapewnić identyczne renderowanie zrzutów lokalnie i w CI (kontener z przypiętą przeglądarką i czcionkami) i jaki próg różnicy? — Owner: team (`/10x-plan`). Block: no.
  - Ile czasu CI na darmowych runnerach mogą zająć zrzuty w dwóch motywach i dwóch szerokościach? — Owner: user. Block: no.
- **Risk:** wersja odchudzona z wyboru (decyzja 2026-11-15): stany widoków sprawdza F-05 funkcjonalnie, a zrzuty pilnują katalogu komponentów i jednego stanu widoku — przy kilkudziesięciu wzorcach zmienianych w każdym wycinku przebudowy łatwo o odruchowe zatwierdzanie.
- **Status:** done

### F-07: Projekt UI/UX całej aplikacji

- **Outcome:** (foundation) aplikacja ma zaakceptowany przez właściciela projekt UI/UX opracowany od nowa według standardów (WCAG 2.2 AA, heurystyki Nielsena, wzorce dojrzałych systemów projektowych): zasady, architekturę informacji i nawigację, język wizualny jako tokeny (typografia, siatka i odstępy, role kolorów, promienie, ikony), komponenty z ich stanami oraz prototypy każdego ekranu M-5 w obu motywach i przy 375 px, w `context/foundation/ui-design.md`; makiety z `ui-ux-proposals.md` są materiałem wejściowym, nie wzorem do skopiowania.
- **Change ID:** ui-design-system
- **PRD refs:** FR-040, NFR `prd.md:328`; widoki z FR-006–FR-039 (PRD v4)
- **Unlocks:** F-08; kryterium akceptacji wyglądu i układu dla S-47–S-59; rozstrzyga otwarte pytanie 7 (elementy makiet bez znaczenia w PRD).
- **Prerequisites:** — (korzysta z mechanizmu motywu z S-46 i kontenera zrzutów z F-06)
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:**
  - Czy projekt zmienia architekturę informacji (inne ekrany, inny podział panelu admina, katalog i karta celu w jednym widoku)? Jeśli tak, `/10x-roadmap` przecina S-47–S-59 na nowo (v6.2), z tymi samymi ID i issue. — Owner: user (akceptacja projektu). Block: no.
  - Krój pisma (polskie znaki, licencja, hostowany u nas) i czy paleta A z S-46 zostaje. — Owner: team (`/10x-plan`), decyzja: user. Block: no.
- **Risk:** bez wspólnego projektu plan każdego wycinka bierze z makiet tylko układ, a wygląd zostaje surowy (decyzja właściciela 2026-11-19); dokumentacja i prototypy bez kodu aplikacji, więc wzorce F-06 się nie zmieniają.
- **Status:** done

### F-08: Tokeny, kroje i komponenty bazowe z projektu

- **Outcome:** (foundation) język wizualny z F-07 jest w aplikacji: tokeny projektu w `style.css` w obu motywach na mechanizmie z S-46, trzy kroje hostowane w aplikacji, ikony oraz komponenty bazowe (przycisk, komunikat, znacznik stanu, stanowisko, przełącznik motywu, pola, odnośnik) w katalogu komponentów we wszystkich stanach; obecne widoki działają na nowych tokenach, jeszcze w starym układzie, a wzorce F-06 zmieniają się raz, jako akceptacja wyglądu.
- **Change ID:** design-tokens-and-components
- **PRD refs:** FR-040, NFR `prd.md:328`
- **Prototypy:** arkusz komponentów `ui-design/prototypes/components.html`; zakres w `ui-design.md`, sekcja „Wdrożenie” (co skąd brać, mapa tokenów, mapa komponentów, zmiany bramek).
- **Unlocks:** S-47, S-49 (a przez nie resztę strumieni A i B); bramki wyglądu dla S-47–S-59: pary kontrastu z projektu z progiem osobnym dla tekstu i elementów interfejsu, test kroju, lista plików bez literałów stylu.
- **Prerequisites:** F-07, S-46
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:** — (zakres przepięcia rozstrzygnął projekt: F-08 przenosi wszystkie obecne widoki na nowe tokeny bez zmiany układu; układ zmieniają wycinki ekranów)
- **Risk:** zmienia wszystkie wzorce F-06 drugi raz po S-46 (zamierzone) i świadomie zmienia bramki opisane w projekcie (test kroju, pary kontrastu, nazwy sekcji katalogu komponentów); nazwa `--border` zmienia znaczenie z linii ozdobnej na krawędź kontrolki, więc obie nazwy zmieniają się w jednym commicie; tokeny są wspólne dla każdego widoku, więc idzie przed powłoką.
- **Status:** done

## Slices

> Od v6.1 (2026-11-19): każdy wycinek UI (S-47–S-59) wdraża swój widok według zaakceptowanego projektu F-07 (`context/foundation/ui-design.md`) na tokenach i komponentach z F-08, a opis PR pokazuje zrzut F-06 obok prototypu ekranu. Każdy wycinek S-47–S-59 kończy się testem E2E swoich przepływów według sekcji „Kontrakt dla testów E2E” w `context/foundation/ui-design.md` (od 2026-11-20). Od v6.2 (2026-11-21) bloki S-47–S-60 opisują ekrany tak, jak je pokazuje projekt; pole „Prototypy” wskazuje ekrany z `ui-design/screens.json`, które są kryterium akceptacji wycinka.

### S-46: Motyw jasny i ciemny z przełącznikiem

- **Outcome:** użytkownik przełącza motyw aplikacji między jasnym i ciemnym w nowej palecie pod układy paneli, wybór jest zapamiętany, a domyślnie obowiązuje ustawienie systemu; każda para tekstu spełnia kontrast co najmniej 4.5:1 w obu motywach.
- **Change ID:** light-dark-theme-switch
- **PRD refs:** FR-040
- **Prerequisites:** F-06
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:**
  - Paleta, druga powierzchnia, rola podświetlenia cytatu, kolory stanowisk `neutral` i `mixed`, typografia i skala odstępów pod panele — kto i na czym zatwierdza wygląd (właściciel na zrzutach F-06)? — Owner: user. Block: no.
- **Risk:** zmienia wszystkie wzorce F-06 naraz (zamierzone); tokeny są wspólne dla każdego widoku, więc idzie przed shellem i widokami, żeby nie przebudowywać ich drugi raz.
- **Status:** done

### S-47: Powłoka aplikacji z nawigacją u góry

- **Outcome:** użytkownik porusza się po aplikacji przez pasek u góry strony: nawigacja według roli (po tym wycinku „Posty” i „Panel admina” dla admina; „Cele” i „Przegląd” dochodzą ze swoimi ekranami w S-57 i S-59), data ostatniej aktualizacji danych per źródło z dniem i godziną, powiadomienia admina, przełącznik motywu, e-mail zalogowanego i „Wyloguj”; treść jest w jednej kolumnie, bez kolumny bocznej i paneli stałych, a obecne widoki działają w powłoce zamiast stawiać własny nagłówek.
- **Change ID:** app-shell-navigation
- **PRD refs:** FR-007, FR-013, FR-015, FR-021, FR-030
- **Prototypy:** pasek aplikacji na każdym ekranie zalogowanym; `notifications`.
- **Prerequisites:** F-08
- **Parallel with:** S-49
- **Blockers:** —
- **Unknowns:**
  - Czy adres `/posts` powstaje już tutaj (ekran startowy `/` pokazuje listę postów do czasu S-59), czy dopiero z ekranem postów w S-51? — Owner: team (`/10x-plan`). Block: no.
- **Risk:** przepina router i nagłówek każdego widoku (dziś każdy widok renderuje własny nagłówek, a dzwonek trzyma stan w store z tego powodu); data danych przechodzi znad listy postów do paska (doprecyzowanie FR-030 w PRD z 2026-11-21); smoke F-05 i zrzuty F-06 łapią regresje przepięcia.
- **Status:** proposed

### S-48: Wspólne stany widoków i stronicowanie

- **Outcome:** użytkownik widzi w każdym widoku te same cztery wspólne elementy: ładowanie jako szkielet, pustą listę, błąd z ponowieniem i stronicowanie, pokazane w katalogu komponentów we wszystkich wariantach; widoki przestają kopiować kontener strony i wzorzec stanów.
- **Change ID:** shared-view-states
- **PRD refs:** FR-007, FR-009, NFR `prd.md:328`
- **Prototypy:** `state-loading`, `state-empty`, `state-error` (lista postów); `admin-loading`, `admin-empty`, `admin-error` (panel admina).
- **Prerequisites:** S-47
- **Parallel with:** S-49
- **Blockers:** —
- **Unknowns:** —
- **Risk:** dziś ten wzorzec jest skopiowany w czterech widokach, a kontener strony w pięciu (audyt, sekcja 1); bez tego każdy nowy widok skopiowałby go po raz kolejny.
- **Status:** proposed

### S-49: Logowanie i aktywacja na wspólnym fundamencie

- **Outcome:** użytkownik loguje się i aktywuje konto na ekranach zbudowanych ze wspólnych komponentów i tokenów w obu motywach; ekran logowania jest na liście plików wolnych od literałów stylu.
- **Change ID:** login-on-shared-foundation
- **PRD refs:** FR-026, FR-027, FR-028
- **Prototypy:** `login`, `activate`.
- **Prerequisites:** F-08
- **Parallel with:** S-47, S-48, S-50, S-51
- **Blockers:** —
- **Unknowns:** —
- **Risk:** mały i oddzielny od powłoki (ekrany publiczne nie mają nawigacji); domyka zaległy dług `login-view-ui-hardening`, na który wskazuje komentarz w teście stylu.
- **Status:** proposed

### S-50: Panel admina w trzech częściach

- **Outcome:** admin pracuje w panelu podzielonym na trzy części z własnymi adresami, przełączane paskiem pod nagłówkiem: „Przebiegi” (nowy przebieg, dni bez danych z uzupełnieniem, ostatnie przebiegi jako tabela), „Jakość ocen” (miary z 7 dni per źródło wobec progów) i „Nieudane posty” (tabela ze stanem posta, liczbą prób i ostatnim błędem; ponowienie zaznaczonych i przywrócenie martwych); strona przebiegu zostaje osobnym ekranem złożonym z tych samych elementów.
- **Change ID:** admin-panel-components
- **PRD refs:** FR-014, FR-015, FR-021, FR-022, FR-033, FR-034
- **Prototypy:** `admin-runs`, `admin-quality`, `admin-posts`, `run`.
- **Prerequisites:** S-48
- **Parallel with:** S-49, S-51, S-52–S-56
- **Blockers:** —
- **Unknowns:** —
- **Risk:** największy widok (790 linii, w M-4 +261) i najwięcej stylu lokalnego; dziś jedna strona, po wycinku trzy adresy, więc zmieniają się testy E2E i wzorce panelu admina; zrzuty F-06 i scenariusze admina w F-05 pilnują, że rozbicie nie zmienia działania.
- **Status:** proposed

### S-51: Lista postów z oceną w panelu bocznym

- **Outcome:** użytkownik przegląda zwartą listę postów (jeden post w jednej linii: źródło, czas, tytuł, cele ze stanowiskiem), filtruje ją po celu i stanowisku w panelu „Filtry” z widocznymi znacznikami aktywnych filtrów i czyta ocenę wybranego posta w panelu wysuwanym z prawej, z listą widoczną w tle: cele ze stanowiskiem, tezy z cytatem, „Otwórz oryginał” i surowe dane; na wąskim ekranie panel zajmuje cały ekran; zastępuje dzisiejszy feed i stronę posta.
- **Change ID:** stream-and-inspector
- **PRD refs:** FR-006, FR-007, FR-009, FR-010, FR-011a
- **Prototypy:** `posts`, `post`.
- **Prerequisites:** S-48
- **Parallel with:** S-49, S-50
- **Blockers:** —
- **Unknowns:**
  - Czy wybrany post ma własny adres? Projekt rekomenduje: tak (działa link z powiadomienia i z innego ekranu, „wstecz” wraca do listy z filtrem, odświeżenie nie gubi wyboru). — Owner: team (`/10x-plan`). Block: no.
- **Risk:** gwiazda przewodnia; jedna etykieta stanowiska na cały post (makieta) jest sprzeczna z kontraktem bez globalnego sentymentu posta (`prd.md:132`), więc wiersz pokazuje stanowisko per cel; panel jest oknem nad listą, więc fokus klawiatury i „wstecz” przeglądarki wymagają testu w przeglądarce.
- **Status:** proposed

### S-52: Domyślnie tylko posty inwestycyjne

- **Outcome:** użytkownik widzi w listach domyślnie tylko posty inwestycyjne, bez postów `not_investment_related` i wyniku odsiewu (post bez treści); przełącznik dwóch stanów z liczbami „Inwestycyjne N / Wszystkie M” na ekranie postów pokazuje resztę i przełącza cały widok (listę, liczniki, proporcje); katalog celów, karta celu i przegląd dnia dostają ten sam przełącznik w S-57, S-58 i S-59.
- **Change ID:** investment-posts-by-default
- **PRD refs:** FR-007 (doprecyzowanie v4)
- **Prototypy:** `posts`, `posts-all`; ten sam przełącznik na `targets`, `target`, `overview`.
- **Prerequisites:** S-51
- **Parallel with:** S-53, S-54, S-55, S-56, S-50
- **Blockers:** —
- **Unknowns:** —
- **Risk:** zmienia domyślną odpowiedź feedu, którą dziś pilnują testy integracyjne i kontrakt („Without a condition the feed is every assessed post”); przełącznik pokazuje liczbę postów bieżącego wyniku w obu zakresach, więc odpowiedź listy dostaje nowe liczby; przed katalogiem celów, żeby liczby w C od początku liczyły się po tej regule.
- **Status:** proposed

### S-53: Podświetlone cytaty w panelu posta

- **Outcome:** użytkownik widzi w panelu posta podświetlone w treści fragmenty, na których oparto ocenę (anchory celów i cytaty tez; także w tytule, gdy cytat z niego pochodzi, oraz miejsce celu bez twierdzeń), może podświetlenie wyłączyć i przechodzi do poprzedniego i następnego posta bieżącej listy; to część panelu z S-51, a nie osobny ekran.
- **Change ID:** reader-quote-highlighting
- **PRD refs:** FR-009 (doprecyzowanie v4)
- **Prototypy:** `post` (podświetlenia, wyłącznik, „poprzedni” i „następny”).
- **Prerequisites:** S-51
- **Parallel with:** S-52, S-54, S-55, S-56, S-50
- **Blockers:** —
- **Unknowns:**
  - Które wystąpienie podświetlić, gdy cytat powtarza się w treści, i jak pokazać nachodzące na siebie anchor i cytat? Cytat nie ma pozycji, tylko pole i tekst (audyt, sekcja 2). — Owner: team (`/10x-plan`). Block: no.
- **Risk:** zapisany tytuł i treść to dokładnie wejście modelu, więc podświetlenie nie wymaga nowych danych; ryzyko leży w mapowaniu pozycji po normalizacji białych znaków (`contract-v2.md` §10) z powrotem na tekst.
- **Status:** proposed

### S-54: Sortowanie listy

- **Outcome:** użytkownik ustawia kolejność listy postów polem „Kolejność” w panelu filtrów: po dacie i źródle oraz po wybranych polach oceny; domyślnym porządkiem zostaje chronologia od najnowszych.
- **Change ID:** post-list-sorting
- **PRD refs:** FR-008, FR-011b
- **Prototypy:** `posts-filters` (pole „Kolejność”; prototyp pokazuje tylko „od najnowszych” i „od najstarszych”).
- **Prerequisites:** S-51
- **Parallel with:** S-52, S-53, S-55, S-56, S-50
- **Blockers:** —
- **Unknowns:**
  - Po których polach oceny sortować (np. liczba celów, liczba tez, trafność), skoro kontrakt nie ma miary „ważności” (FR-007, Socrates)? Projekt nie rozstrzyga; pokazuje tylko kolejność po dacie. — Owner: user. Block: no.
- **Risk:** jedyny nice-to-have w M-5 (wybór właściciela); sortowanie po polach JSONB oceny może wymagać innego zapytania niż dzisiejsza stała kolejność; dzieli panel filtrów z S-55 i S-56.
- **Status:** proposed

### S-55: Filtr po dacie publikacji

- **Outcome:** użytkownik zawęża listę do dnia albo zakresu dat publikacji (kalendarz warszawski) polami „Od” i „Do” w panelu filtrów, także razem z pozostałymi filtrami; wybrany zakres widać jako znacznik nad listą, a lista nie ma ukrytego domyślnego okresu.
- **Change ID:** publication-date-filter
- **PRD refs:** FR-035
- **Prototypy:** `posts-filters` (pola „Od” i „Do”, znacznik zakresu).
- **Prerequisites:** S-51
- **Parallel with:** S-52, S-53, S-54, S-56, S-50
- **Blockers:** —
- **Unknowns:** —
- **Risk:** nowy parametr w kontrakcie listy po obu stronach (plik kontraktu, typ TS, atrapa); przegląd dnia (S-59) stoi na nim; dzieli panel filtrów z S-54 i S-56.
- **Status:** proposed

### S-56: Wyszukiwanie w treści

- **Outcome:** użytkownik wyszukuje posty po słowach w tytule i treści w dużym polu „Szukaj w postach” nad listą; filtr po celu („Spółka lub temat”) zostaje osobnym polem w panelu filtrów, więc jedno pole nie znaczy dwóch rzeczy.
- **Change ID:** post-content-search
- **PRD refs:** FR-036
- **Prototypy:** `posts-filters` (pole „Szukaj w postach”, pole „Spółka lub temat”).
- **Prerequisites:** S-51
- **Parallel with:** S-52, S-53, S-54, S-55, S-50
- **Blockers:** —
- **Unknowns:**
  - Dopasowanie „zawiera” jak dziś dla celu czy wyszukiwanie pełnotekstowe; polskie znaki bez `unaccent` („zabka” nie znajduje „ŻABKA”, audyt, sekcja 2)? — Owner: team (`/10x-plan`). Block: no.
- **Risk:** pełne skanowanie treści przy rosnącej bazie (wpis Parked o wydajności filtra feedu); dziś ~10 tys. postów; dzieli panel filtrów z S-54 i S-55.
- **Status:** proposed

### S-57: Katalog celów

- **Outcome:** użytkownik widzi katalog celów z postów z wybranego okresu jako tabelę: nazwa celu, liczba postów i udział stanowisk (wzrostowe, spadkowe, pozostałe); filtruje go po nazwie, zmienia okres i zakres postów i przechodzi do postów wybranego celu; cele są grupowane po znormalizowanym tekście `anchor`, bez rozpoznawania spółek; w nawigacji pojawia się „Cele”.
- **Change ID:** target-catalog
- **PRD refs:** FR-037, FR-038, FR-011a (doprecyzowanie v4)
- **Prototypy:** `targets`.
- **Prerequisites:** S-52
- **Parallel with:** S-53, S-54, S-55, S-56, S-50
- **Blockers:** —
- **Unknowns:**
  - Jaki jest domyślny okres katalogu i od ilu postów cel trafia do katalogu? Projekt proponuje okres „Dziś / 7 dni / 30 dni” z domyślnym „7 dni” i brak progu (cel z jednym postem jest w katalogu). — Owner: user. Block: no.
- **Risk:** odmiany („Orlen”, „Orlenu”, „#orlen”) zostają osobnymi pozycjami z wyboru (agregacja po tekście, Non-Goals `prd.md:369`); cel równy kanałowi posta zanieczyściłby katalog przy zmianie modelu (S-60); udział stanowisk w tabeli wymaga tej samej agregacji co karta celu, więc liczenie proporcji w API powstaje tutaj, a S-58 z niego korzysta.
- **Status:** proposed

### S-58: Karta celu z proporcjami stanowisk

- **Outcome:** użytkownik otwiera kartę celu w panelu z prawej na ekranie katalogu, pod własnym adresem celu i w tym samym wzorcu co panel posta: proporcje stanowisk autorów (wzrostowe, spadkowe, pozostałe) liczone ze stanowisk wobec tego celu w postach z wybranego okresu, jako migawka bez trendu, przełącznik zakresu postów oraz wypowiedzi o tym celu ze źródłem, czasem i stanowiskiem, z przejściem do posta.
- **Change ID:** target-card-stance-shares
- **PRD refs:** FR-038
- **Prototypy:** `target`.
- **Prerequisites:** S-57
- **Parallel with:** S-53, S-54, S-55, S-56, S-50
- **Blockers:** —
- **Unknowns:** —
- **Risk:** dzieli ekran z katalogiem (S-57) i wzorzec panelu z postem (S-51), więc idzie po nich; proporcje liczy API, nie SPA; „teza z wypowiedzi” z makiety nie jest wybierana automatycznie (Non-Goals).
- **Status:** proposed

### S-59: Przegląd dnia

- **Outcome:** użytkownik widzi po zalogowaniu przegląd wybranego dnia: pięć klocków liczb (posty ze źródłami, cele ze stanowiskiem oraz udział stanowisk wzrostowych, spadkowych i pozostałych w tym dniu), trzy najczęściej omawiane cele z proporcjami i najnowszymi postami oraz tabelę wszystkich celów dnia, z wyborem dnia i przełącznikiem zakresu postów; wszystkie wybory są jawnie liczbowe; przegląd staje się ekranem startowym, a w nawigacji pojawia się „Przegląd”.
- **Change ID:** daily-overview
- **PRD refs:** FR-039
- **Prototypy:** `overview`.
- **Prerequisites:** S-55, S-58
- **Parallel with:** S-53, S-54, S-56, S-60
- **Blockers:** —
- **Unknowns:** —
- **Risk:** zamyka M-5, bo składa się z filtra dnia (S-55) i proporcji (S-58); klocki udziału stanowisk to migawka jednego dnia, nie trend (prośba właściciela z 2026-11-20, doprecyzowanie FR-039 w PRD z 2026-11-21); zmiana ekranu startowego przepina testy wejścia do aplikacji; „Warto przeczytać” i „Wątek dnia” z makiety nie wchodzą, więc nie wolno przemycić oceny wartości (`prd.md:311`).
- **Status:** proposed

### S-60: Miara „kanał jako cel”

- **Outcome:** operator widzi osobno dla każdego źródła udział celów, których anchor równa się kanałowi posta, w oknie 7 dni w podsumowaniu nocy i w części „Jakość ocen” panelu admina, w tym samym wzorze miary co pozostałe, i dostaje sygnał przy przejściu progu tym samym kanałem co pozostałe miary jakości.
- **Change ID:** channel-as-target-quality
- **PRD refs:** FR-033 (doprecyzowanie v4)
- **Prototypy:** `admin-quality` (wzór miary; tej miary prototyp nie rysuje, bo próg i mianownik ustali wycinek).
- **Prerequisites:** S-50
- **Parallel with:** S-51–S-59
- **Blockers:** —
- **Unknowns:**
  - Jakie progi i jaki mianownik? Na nagraniach z promptem v2.5 model produkcyjny ma 0 takich celów na 674, tańsze modele 6 (audyt, sekcja 4); na produkcji nikt tego nie mierzył. — Owner: user (`/10x-plan`). Block: no.
- **Risk:** reguła kontraktu `contract-v2.md` §6.4 jest dziś przestrzegana, ale strzeże jej tylko prompt; miara chroni widoki po celu przy zmianie modelu i dopisuje trzecią linię do podsumowania nocy (`NightSummary`, strefa „watch” mapy projektu).
- **Status:** proposed

## Backlog Handoff

| Roadmap ID | Change ID                    | Suggested issue title                                                           | Ready for `/10x-plan` | Notes                          |
| ---------- | ---------------------------- | ------------------------------------------------------------------------------- | --------------------- | ------------------------------ |
| F-05       | e2e-harness                  | F-05: testy w przeglądarce w CI na atrapie API                                  | yes                   | Run `/10x-plan e2e-harness`    |
| F-06       | visual-regression            | F-06: zrzuty porównawcze i test dostępności w CI                                | no                    | Po F-05                        |
| F-07       | ui-design-system             | F-07: projekt UI/UX całej aplikacji                                             | yes                   | Run `/10x-research ui-design-system`, potem `/10x-plan` |
| F-08       | design-tokens-and-components | F-08: tokeny, kroje i komponenty bazowe z projektu                              | yes                   | Run `/10x-plan design-tokens-and-components` |
| S-46       | light-dark-theme-switch      | S-46: motyw jasny i ciemny z przełącznikiem                                     | no                    | Po F-06                        |
| S-47       | app-shell-navigation         | S-47: powłoka aplikacji z nawigacją u góry                                      | no                    | Po F-08                        |
| S-48       | shared-view-states           | S-48: wspólne stany widoków i stronicowanie                                     | no                    | Po S-47                        |
| S-49       | login-on-shared-foundation   | S-49: logowanie i aktywacja na wspólnym fundamencie                             | no                    | Po F-08                        |
| S-50       | admin-panel-components       | S-50: panel admina w trzech częściach                                           | no                    | Po S-48                        |
| S-51       | stream-and-inspector         | S-51: lista postów z oceną w panelu bocznym (gwiazda przewodnia M-5)            | no                    | Po S-48                        |
| S-52       | investment-posts-by-default  | S-52: domyślnie tylko posty inwestycyjne                                        | no                    | Po S-51                        |
| S-53       | reader-quote-highlighting    | S-53: podświetlone cytaty w panelu posta                                        | no                    | Po S-51                        |
| S-54       | post-list-sorting            | S-54: sortowanie listy                                                          | no                    | Po S-51                        |
| S-55       | publication-date-filter      | S-55: filtr po dacie publikacji                                                 | no                    | Po S-51                        |
| S-56       | post-content-search          | S-56: wyszukiwanie w treści                                                     | no                    | Po S-51                        |
| S-57       | target-catalog               | S-57: katalog celów                                                             | no                    | Po S-52                        |
| S-58       | target-card-stance-shares    | S-58: karta celu z proporcjami stanowisk                                        | no                    | Po S-57                        |
| S-59       | daily-overview               | S-59: przegląd dnia                                                             | no                    | Po S-55, S-58                  |
| S-60       | channel-as-target-quality    | S-60: miara „kanał jako cel”                                                    | no                    | Po S-50                        |

## Open Roadmap Questions

1. **Weryfikacja produkcji z M-1:** lokalizacja bazy dla filtra S-04 (runbook z issue #17, krok 2: `C`/`POSIX` po cichu psuje `KĘTY`→`Kęty`) i pierwsza noc S-06 (runbook z issue #19). Owner: operator. Block: — (przeniesione z M-2; wynik `C`/`POSIX` dałby nowy wycinek; dotyczy też dopasowania tekstu w S-56 i S-57).
2. **Ustalenia z żywego Railway** (audyt M-2, Open Questions 2–7): czy redeploy po `config apply` zmiany, która deployuje, czeka na CI (S-21); czy reguła „deployment crashed” obejmuje crash loop API; okno SIGTERM→SIGKILL; przyczyna przebiegu „trwającego” 22 h z 2026-10-09. Owner: operator. Block: —. Rozstrzygnięte przez audyt `post-m2-audit` (P2): merge samej dokumentacji daje `SKIPPED` na wszystkich usługach; apply `source.checkSuites` nie wywołuje żadnego deploymentu; deployment API z czerwonym CI jest pomijany przez Wait for CI.
3. **Kanał i próg dla FR-025 (powiadomienia o obiecującym trendzie)** — nie ustalono, czym jest „obiecujący trend” liczbowo ani gdzie ma trafiać powiadomienie. Owner: użytkownik. Block: — (FR-025 odroczone).
4. **Budżet kosztów LLM na przebieg** — guardrail PRD każe minimalizować koszt zapytań, ale nie podaje pułapu (na dzień ani na post); twardy pułap to $10/mies. na kluczu OpenRouter produkcji. Drugie źródło zwiększy liczbę ocen. Owner: użytkownik. Rozstrzygnięte 2026-10-31 w S-28: limit $10/mies. zostaje, bez osobnego pułapu; koszt łączny z Wykopem to $3,21/mies. na `flex` (górna granica $4,67), Reddit włączany.
5. **PRD ma status `draft` i ostatnią zmianę 2026-10-13** — nowsze decyzje żyją w blokach „Doprecyzowanie” i w roadmapie (destylacja, sekcja „Stale documents”: FR-029 krótka sesja vs 30 dni, „zakres dat/godzin”, FR-022 warunkowe). Czy odświeżyć PRD do v3 przed planowaniem Reddita? Owner: użytkownik. Block: — (destylacja D-02, D-03, D-10). Rozstrzygnięte 2026-11-04: PRD v3 (`prd.md` `version: 3`) zamyka D-02, D-03, D-10 i D-16 oraz zapisuje decyzje Q-13, Q-14, Q-16 i Q-18; PRD v4 z 2026-11-15 zapisuje decyzje przed M-5.
6. **Pytania z destylacji domeny bez odpowiedzi** (`domain-distillation.md`, sekcja Questions): Q-08 (kto woła `/api/admin/llm/check`), Q-11 (usuwanie lub deaktywacja konta i sprzątanie wygasłych linków), Q-12 (`content_hash`, `slug`, `tags` bez czytelnika), Q-19 (czy 30 % tez w innym języku na Reddicie to poziom przyjęty na stałe). Owner: właściciel produktu. Block: — (żaden wycinek M-5 od nich nie zależy). Q-15 i Q-20 rozstrzygnięte przez PRD v4 (FR-007 i FR-033, doprecyzowania v4).
7. **Elementy makiet bez znaczenia w PRD:** „Analizy”, „Wątki”, „Ostatnie obserwacje”, „Przejdź do źródła ↓” i strzałka „↑” przy odsetku (`ui-ux-proposals.md`, audyt `post-m4-ui-readiness-audit`, sekcja 2). Owner: użytkownik. Block: — (wycinki M-5 ich nie budują; „Wątki” jako wątki komentarzy byłyby sprzeczne z Non-Goals). Rozstrzygnięte 2026-11-20 przez projekt F-07 (`ui-design.md`, „Różnice wobec roadmapy”): żaden z tych elementów nie wchodzi do projektu — „Wątki” byłyby sprzeczne z Non-Goals, strzałka przy odsetku sugeruje trend, pozostałe nie mają danych w kontrakcie; odnośnik do źródła to „Otwórz oryginał” w panelu posta.
8. **Doprecyzowania PRD po projekcie UI/UX (v6.2):** (a) FR-039 — klocki „Wzrostowe”, „Spadkowe”, „Pozostałe” na przeglądzie dnia to udział stanowisk w wybranym dniu, migawka bez trendu (prośba właściciela z 2026-11-20); (b) FR-030 — data danych jest w pasku aplikacji na każdym ekranie po zalogowaniu, a nie nad listą postów; (c) FR-007 — przełącznik zakresu postów jest także w katalogu celów, na karcie celu i na przeglądzie dnia. Owner: użytkownik. Block: —. Rozstrzygnięte 2026-11-21: właściciel potwierdził, a PRD ma trzy bloki „Doprecyzowanie” z tą datą przy FR-007, FR-030 i FR-039.

## Parked

- **FR-016 / FR-017 / FR-019 — panel kont w aplikacji + wysyłka maila z zaproszeniem** — Why parked: nice-to-have; CLI (S-07) daje ten sam efekt; PRD §Non-Goals wyklucza wymaganą wysyłkę e-maili.
- **FR-020 — własna nazwa wyświetlana** — Why parked: nice-to-have; e-mail wystarcza w MVP. Poza M-5 decyzją właściciela z 2026-11-15 (z nice-to-have do M-5 weszło tylko sortowanie); kandydat na M-6.
- **FR-024 / FR-025 — wykres giełdowy z trendem sygnałów i powiadomienia o trendzie** — Why parked: nice-to-have, poza MVP; wymaga integracji danych giełdowych, rozpoznawania spółek i rozstrzygnięcia Open Question 4.
- **FR-029 — automatyczne wygasanie krótkich sesji** — Why parked: nice-to-have; krótki timeout uciążliwy przy czytaniu. F-03 stosuje 30 dni bezczynności z odnawianiem ciasteczka; krótsze limity pozostają odroczone.
- **FR-031 — eksport widocznej listy do CSV/JSON** — Why parked: nice-to-have, nie blokuje głównego przepływu. Poza M-5 decyzją właściciela z 2026-11-15; kandydat na M-6.
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
- **Naprawy oceny Reddita (`claim_ids`, dopasowanie cytatów `D6`, tezy po polsku, filtr megawątków)** — Why parked: decyzja użytkownika 2026-10-31 o włączeniu bez napraw, bo Arctic Shift może przestać dostarczać dane między styczniem a marcem 2027. Wraca, gdyby Reddit został z nami dłużej.
- **Porównanie modeli na postach z Reddita (DeepSeek, Haiku 5.5)** — Why parked: pomysł użytkownika z 2026-10-31, poza S-28. Bez niezależnych etykiet pokaże odrzucenia, koszt i liczbę celów, a nie to, który model ocenia lepiej; Haiku 5.5 nie ma w `models.json`. Zarys: osobna zmiana, około 50 długich postów w osobnym katalogu.
- **Plan testów (`/10x-test-plan`, `context/foundation/test-plan.md`)** — Why parked: decyzja przy otwieraniu M-4 (2026-11-04): zakres weryfikacji M-4 ustalają `verification-scope.md` i audyt `post-m3-verification-audit` (eksperymenty X1–X6, mapa strażników), a S-31 i S-45 wybierają scenariusze i zakres pomiaru w swoich planach. Wraca przed M-5, gdy dojdą ryzyka testów E2E nowego UI.
- **Polityki z destylacji bez incydentu (ranking #7–#10)** — Why parked: cel M-4 `quality` bierze pozycje #1–#6; te są dziś pilnowane albo bez ekspozycji danych: przejścia stanu przebiegu tylko w aplikacji (R-18, R-19, R-21), limiter logowań zerowany przy uśpieniu API (R-33), kaskady bez ścieżki usuwania (R-29, Q-11), pola bez czytelnika (`tags`, `slug`, `content_hash`, Q-12). Każda wraca z decyzją właściciela albo z najbliższą pracą w tym obszarze.
- **Rozpoznawanie spółek, trend i wykres jako kierunek M-6 (kandydat)** — Why parked: decyzja właściciela z 2026-11-15: M-5 agreguje po tekście celu (FR-037–FR-039), a scalanie odmian i nazw jednej spółki, tickery, trend (FR-024, FR-025) i automatyczny wybór „wartych uwagi” zostają poza zakresem PRD v4 (Non-Goals `prd.md:369`); pełna karta podmiotu i przegląd dają pełną wartość dopiero z nimi.
- **„Zapisane” (zapisy wpisów per użytkownik)** — Why parked: w nawigacji makiet 1 i 3 (`ui-ux-proposals.md`), ale decyzją właściciela z 2026-11-15 poza M-5; brak FR w PRD v4 (NFR o danych kontowych by na to pozwalał); kandydat na M-6.
- **Status przebiegów dla zwykłego użytkownika („Pipeline gotowy”)** — Why parked: decyzja właściciela z 2026-11-15: zostaje jak w PRD (FR-030: użytkownik widzi datę aktualizacji per źródło, status przebiegów tylko admin, `prd.md:226-229`).
- **Wyłączenie Reddita przy wygaśnięciu Arctic Shift** — Why parked: dostęp do danych Reddita może wygasnąć między styczniem a marcem 2027; plan operatora (2026-11-15): wtedy usunąć `REDDIT_SUBREDDITS` na produkcji — Reddit staje się źródłem nieskonfigurowanym, nocny przebieg i „Dni bez danych” go pomijają, kod bez zmian, posty zostają w bazie. Drugie źródło w miejsce Reddita (oficjalne API albo inne forum) to kandydat na M-6.
- **Propozycje z projektu UI/UX poza PRD v4** — Why parked: `ui-design.md`, sekcja „Propozycje poza PRD”: skróty klawiatury w panelu posta, zapamiętanie ostatniego zakresu i okresu, podpowiedź odmian celu na karcie celu, znak „nowe od ostatniej wizyty”, powiadomienia dla zwykłego użytkownika. Żadna nie ma prototypu ani FR; każda wymaga decyzji właściciela i wpisu w PRD; kandydaci na M-6.
- **Test zgodności projektu i aplikacji w CI (TODO przed zamknięciem M-5)** — Why parked: decyzja właściciela (F-08, `design-tokens-and-components`): dziś `design-check app` (`tools/design-check/`) to polecenie ręczne, które porównuje `ui-design/tokens.css`, `pairs.json`, sprite ikon i pliki krojów z kopiami w `szczur-radar-web/`, uruchamiane przy każdej zmianie projektu; zadania CI na to nie ma. Warunek powrotu: przed zamknięciem M-5 ocenić, ile zmian projektu w S-47–S-60 rozjechało te kopie, i dopiero wtedy zdecydować, czy test w CI jest wart swojego kosztu. Pytanie otwarte: jak test w CI ma czytać `context/`, skoro obraz web buduje się z samego `szczur-radar-web/`.

## Milestone History

- **M-1: MVP — ocenione sygnały z Wykopu dla zaproszonych osób** (`mvp-wykop-signal-radar`) — closed 2026-10-17. Wszystkie konieczne FR i historyjki US-01, US-02 dostarczone na produkcji (F-01–F-03, S-01–S-07); domknięcie runbooków produkcji S-04 (#17) i S-06 (#19) przeniesione do Open Roadmap Questions M-2.
- **M-2: MVP Quality & Reliability Hardening** (`mvp-quality-reliability-hardening`) — closed 2026-10-27. Istniejące MVP nie milczy i nie gubi danych: kanał alarmowy poza aplikacją (F-04), sygnały pustej i brakującej nocy, awarie dostawców bez utraty prób, samonaprawa utkniętych przebiegów, bezpieczna bramka wdrożeń, pełna kontrola kopii bazy, kompletne pobieranie z Wykopu, przebieg bez Embabel i błędy wymagające reakcji w Telegramie (F-04, S-08–S-11, S-13–S-19, produkcja zweryfikowana); S-12 zaparkowany decyzją użytkownika (v3.3). Pierwsza noc crona na silniku S-17 (2026-10-28) pod nadzorem F-04; otwarte ustalenie S-14 z issue #73: fałszywa kontrola po apply w `verify-apply.sh` (zmiana bez deployu, `SKIPPED` z równoległego pusha); drugie ustalenie z #73 („deploy `2abc64c` mimo czerwonego CI”) okazało się błędną diagnozą — deployment API został `SKIPPED` przez Wait for CI (audyt `post-m2-audit`).
- **M-3: Drugie źródło na utwardzonym rdzeniu** (`second-source-on-hardened-core`) — closed 2026-11-01. Reddit jest drugim źródłem na produkcji (od 2026-11-01, trzy subreddity), ocenianym tym samym kontraktem co Wykop, na rdzeniu, w którym awaria albo cisza jednego źródła nie psuje przebiegu pozostałych (S-20, gwiazda przewodnia); źródło widoczne w feedzie i świeżość bez kłamstwa przy dwóch źródłach (S-29); obraz jakości i kosztu Reddita przed włączeniem i decyzja o włączeniu bez napraw (S-28); domknięte ślepe punkty z audytu po M-2: bramka wdrożeń przy zmianie bez deployu (S-21), reguły w jednym miejscu (S-22, S-25), zamknięte zbiory i kontrakt SPA ↔ API pilnowane w CI (S-23, S-24), testy silnika bez zależności od zegara (S-26). Weryfikacja nocy z Redditem otwarta w issue #121 — nie blokuje zamknięcia (decyzja użytkownika, v4.3).
- **M-4: Weryfikacja i regresja** (`verified-core-and-regression`) — closed 2026-11-15. Testy łapią błędy, a nie tylko przechodzą: ręczne weryfikacje PR env z S-13, S-17, S-18, S-20 i S-27 chodzą w CI jako scenariusze na pełnym API z atrapami źródeł i LLM (S-31, gwiazda przewodnia), wynik CI nie zależy od kolejności testów (S-30), a PIT na rdzeniu w jobie `mutation` przy każdym PR zostawia 44 z 1133 mutantów, każdy z zapisanym powodem (S-45); strażnicy liczą zbiory z kodu zamiast z ręcznych list (S-39, S-40), kontrakt SPA ↔ API, rejestr reguł i atrapy odpowiadają jak prawdziwe API (S-41–S-43), feed ma testy regresji i granicę numeru strony (S-44). Wymagania PRD v3 z nocy z Redditem: seria odmów dostawcy zatrzymuje przebieg (S-32), admin przywraca martwe posty (S-33), zamiast alarmu o martwym poście co noc podsumowanie w ntfy — kanał zdarzeń API przeniesiony z healthchecks.io na ntfy (S-34), post odrzucony przy zapisie jest policzony (S-38), jakość ocen per źródło z sygnałem progu (S-35, S-36), typ posta słowami źródła (S-37).

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
- **S-39: CI czerwienieje, gdy konwerter enuma zapisuje inną wartość, niż dopuszcza `CHECK`, gdy złożony `CHECK` ma literał spoza enuma i gdy nowa ścieżka kończąca przebieg nie wywołuje powiadomienia (R-23).** — Archived 2026-11-06 → `context/archive/2026-11-06-derived-persistence-guards/`. Lesson: —.
- **S-33: admin zaznacza martwe posty na liście w `/admin` i przywraca je do oceny: post traci martwy stan, a jego próby oceny są zerowane, więc kolejny przebieg ocenia go od nowa.** — Archived 2026-11-10 → `context/archive/2026-11-08-restore-dead-posts/`. Lesson: —.
- **S-32: seria odmów dostawcy na kolejnych postach (np. moderacja albo odrzucane żądanie dla każdego posta) zatrzymuje przebieg, zanim zużyje próby pozostałych postów; przebieg kończy się `failed` i daje sygnał jak każdy `failed`, a posty, których nie zdążył ocenić, wracają bez straty próby.** — Archived 2026-11-10 → `context/archive/2026-11-08-provider-refusal-breaker/`. Lesson: W PR env agent sam zakłada jednorazowe konto admina — link i hasło tylko w zmiennych powłoki (`lessons.md`).
- **S-41: każdy kształt odpowiedzi, który czyta SPA (konto, aktywacja, powiadomienia z `run: null`, wynik źródła, dni bez danych, koperty stron), ma plik kontraktu sprawdzany po obu stronach, warianty `null` są w kontrakcie, a pole, które API zawsze wysyła, jest wymagane w typie TS.** — Archived 2026-11-10 → `context/archive/2026-11-08-api-contract-shapes-round-two/`. Lesson: —.
- **S-34: przebieg, którego jedynym powodem „wymaga uwagi” jest martwy post, nie daje już operatorowi wiadomości `RUN_NEEDS_ATTENTION` (martwy post obok innego powodu nadal ją daje); zamiast tego operator dostaje co noc, także przy 0 martwych, podsumowanie nocy: linia nocy (zakres, status każdego przebiegu, błędy techniczne, pominięte), linia na każde źródło (pobrane, pominięte, nowe, resztki, ocenione, nieudane, martwe, tokeny wejścia/cache/wyjścia — bez kosztu w pieniądzu) i martwe posty z przebiegów admina od poprzedniej nocy. Wszystkie zdarzenia API (`RUN_NEEDS_ATTENTION`, awarie ścieżek sygnału, `send-test-alert`, podsumowanie `NIGHT_SUMMARY`) przychodzą przez ntfy (`NTFY_URL`, opcjonalny `NTFY_TOKEN`); healthchecks.io → Telegram zostaje dla cronów i `deploy-health`, check `api-events` znika. Dzwonek i strona przebiegu pokazują martwe posty bez zmian.** — Archived 2026-11-11 → `context/archive/2026-11-08-dead-posts-daily-digest/`. Lesson: Nową zmienną z railway.ts ustawiaj na produkcji tuż przed merge'em — inny merge utknie na variable.delete.
- **S-40: każda zmapowana trasa `/api/admin/**` daje 403 zalogowanemu zwykłemu użytkownikowi, a każda trasa `/internal/**` daje 401 bez tokenu crona; zbiory tras są wyliczane z mapowań, a nie z ręcznej listy.** — Archived 2026-11-11 → `context/archive/2026-11-11-derived-route-guards/`. Lesson: —.
- **S-35: operator widzi osobno dla każdego źródła udział nieudanych prób oceny według klas błędu, liczony z danych produkcyjnych, a przekroczenie progu daje mu sygnał bez czytania logów.** — Archived 2026-11-11 → `context/archive/2026-11-11-per-source-failure-quality/`. Lesson: —.
- **S-38: post, którego zapis odrzuca inne ograniczenie bazy niż klucz unikalny (np. `CHECK` albo `NOT NULL`), jest policzony w przebiegu (pominięty albo błąd) i daje powód „wymaga uwagi”; dla każdego przebiegu pobrane = nowe posty + duplikaty + pominięte.** — Archived 2026-11-11 → `context/archive/2026-11-11-post-store-loss-visible/`. Lesson: —.
- **S-43: `dev/mock-api.js` odpowiada z plików kontraktu albo test porównuje jego odpowiedzi z kontraktem (dziś 7 rozbieżności, m.in. brak Reddita, brak logowania, start przebiegu zawsze 409), a atrapy narzędzi (`pipeline-trigger`, stuby `deploy-gate`) czytają statusy i kształty z rejestru.** — Archived 2026-11-12 → `context/archive/2026-11-11-contract-bound-mocks/`. Lesson: —.
- **S-31: ręczne kroki weryfikacji PR env z wycinków S-13, S-17, S-18, S-20 i S-27 chodzą w CI jako scenariusze na pełnym API z Postgresem, z atrapami źródeł i LLM z prawdziwych payloadów; to, czego CI nie zobaczy, jest na krótkiej, nazwanej liście ręcznej.** — Archived 2026-11-13 → `context/archive/2026-11-12-pr-env-scenarios-in-ci/`. Lesson: —.
- **S-42: porównanie kodu błędu w SPA (`err.detail === '<kod>'`) jest pilnowane kompilatorem albo skanem, limit rozmiaru strony i enumy dziś spoza `rules.json` są w rejestrze, a SPA nie liczy sama warunku powodu „wymaga uwagi”.** — Archived 2026-11-13 → `context/archive/2026-11-12-spa-rules-registry-round-two/`. Lesson: —.
- **S-36: operator widzi osobno dla każdego źródła udział tez (`statement`) w innym języku niż post, a przekroczenie progu daje sygnał tym samym kanałem co S-35.** — Archived 2026-11-13 → `context/archive/2026-11-12-statement-language-quality/`. Lesson: —.
- **S-45: testy mutacyjne (PIT) mierzą rdzeń — przebieg i warunkowe zapisy stanu, walidator kontraktu V2, rozliczanie prób oceny — a każdy mutant, który przeżył, jest zabity testem albo przyjęty z zapisanym powodem; pomiar da się powtórzyć w CI.** — Archived 2026-11-14 → `context/archive/2026-11-13-core-mutation-testing/`. Lesson: —.
- **F-05: (foundation) testy w przeglądarce leżą w repo i chodzą w CI przy każdym PR na atrapie API zgodnej z kontraktem, której tryby (sesja, rola, dane, pusto, błąd, ładowanie) ustawia test; smoke obejmuje obecne przepływy: logowanie i wylogowanie, feed z filtrem, widok posta, panel admina.** — Archived 2026-11-16 → `context/archive/2026-11-16-e2e-harness/`. Lesson: —.
- **F-06: (foundation) wygląd jest pilnowany zrzutami porównawczymi w CI — katalog komponentów we wszystkich stanach w obu motywach i przy 375 px oraz po jednym zrzucie każdego widoku z danymi w obu motywach, przy 375 px i na desktopie — a każdy z tych widoków przechodzi automatyczny test dostępności; zmiana wyglądu aktualizuje wzorce w tym samym PR.** — Archived 2026-11-17 → `context/archive/2026-11-16-visual-regression/`. Lesson: —.
- **S-46: użytkownik przełącza motyw aplikacji między jasnym i ciemnym w nowej palecie pod układy paneli, wybór jest zapamiętany, a domyślnie obowiązuje ustawienie systemu; każda para tekstu spełnia kontrast co najmniej 4.5:1 w obu motywach.** — Archived 2026-11-19 → `context/archive/2026-11-17-light-dark-theme-switch/`. Lesson: —.
- **F-07: (foundation) aplikacja ma zaakceptowany przez właściciela projekt UI/UX opracowany od nowa według standardów (WCAG 2.2 AA, heurystyki Nielsena, wzorce dojrzałych systemów projektowych): zasady, architekturę informacji i nawigację, język wizualny jako tokeny (typografia, siatka i odstępy, role kolorów, promienie, ikony), komponenty z ich stanami oraz prototypy każdego ekranu M-5 w obu motywach i przy 375 px, w `context/foundation/ui-design.md`; makiety z `ui-ux-proposals.md` są materiałem wejściowym, nie wzorem do skopiowania.** — Archived 2026-11-21 → `context/archive/2026-11-19-ui-design-system/`. Lesson: —.
- **F-08: (foundation) język wizualny z F-07 jest w aplikacji: tokeny projektu w `style.css` w obu motywach na mechanizmie z S-46, trzy kroje hostowane w aplikacji, ikony oraz komponenty bazowe (przycisk, komunikat, znacznik stanu, stanowisko, przełącznik motywu, pola, odnośnik) w katalogu komponentów we wszystkich stanach; obecne widoki działają na nowych tokenach, jeszcze w starym układzie, a wzorce F-06 zmieniają się raz, jako akceptacja wyglądu.** — Archived 2026-11-22 → `context/archive/2026-11-21-design-tokens-and-components/`. Lesson: —.
