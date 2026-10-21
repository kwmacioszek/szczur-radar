# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame, /10x-research, /10x-plan, /10x-plan-review, /10x-implement, /10x-impl-review.

## Nie myl wyboru startera z pełnym projektem stacku technologicznego

- **Context**: Praca greenfield z `/10x-tech-stack-selector`, gdy projekt ma więcej zależności i elementów niż sam framework startowy.
- **Problem**: `tech-stack.md` może wyglądać zbyt skromnie, jeśli oczekuje się od niego pełnej specyfikacji technologicznej projektu. Skill jest jednak zaprojektowany przede wszystkim jako kontrakt przekazania do `/10x-bootstrapper`: wybiera agent-friendly starter, zapisuje sygnały projektu i uzasadnienie wyboru. Nie musi zawierać kompletnej listy bibliotek i decyzji implementacyjnych.
- **Rule**: Najpierw sprawdź kontrakt artefaktu i rolę skilla w całym workflow. Nie rozszerzaj `tech-stack.md` na siłę o ORM, auth, biblioteki domenowe, test tooling i inne dependencies, jeśli handoff schema tego nie wymaga — szczegółowy stack materializuje się podczas bootstrapu i późniejszego planowania implementacji. Na ścieżce custom warto zakwestionować domyślną rekomendację startera, jeśli nie pasuje do charakteru aplikacji, ale po decyzji wróć do kontraktu skilla zamiast zamieniać selector w pełny proces projektowania architektury.
- **Applies to**: plan

## Oddziel RAW od danych operacyjnych i zachowaj możliwość kompresji

- **Context**: Pipeline ingestujący niezmienne payloady z zewnętrznych źródeł (Wykop, Reddit i przyszłe źródła), szczególnie przy bezterminowej retencji i storage rozliczanym według zajętej przestrzeni.
- **Problem**: Trzymanie pełnego RAW JSON inline w głównym rekordzie posta niepotrzebnie powiększa relacyjną bazę i indeksowany/queryable storage, wiąże archiwalny payload z modelem domenowym oraz utrudnia późniejszą kompresję lub migrację do tańszego storage. Historyczny Szczur Radar osiągnął ok. 46 MB przy 11 767 wpisach z 29 dni, więc wzrost jest mierzalny i kumuluje się latami.
- **Rule**: RAW JSON z zewnętrznych źródeł zawsze przechowuj oddzielnie od znormalizowanych rekordów aplikacyjnych. Zachowaj RAW bezstratnie i projektuj jego storage tak, aby można go było transparentnie kompresować oraz później przenieść do innego backendu bez zmiany modelu domenowego, API ani utraty widoku surowych danych.
- **Applies to**: plan, plan-review, implement, impl-review

## Railway trial: limity kosztów za paywallem, ale PITR działa

- **Context**: Wdrożenie MVP na Railway na planie **trial** (bez aktywnej subskrypcji).
- **Problem**: Na trialu `railway usage limit set` zwraca „Usage limits require an active subscription" — nie da się ustawić soft/hard limitu kosztów, więc egzekwowalnej bramki wydatków nie ma do czasu subskrypcji. PITR jest natomiast dostępne na trialu: `railway postgres pitr enable` tworzy base backup + ciągłe archiwizowanie WAL. Ale automatyczny harmonogram backupów (`railway postgres pitr schedule set`) jest gated („You do not have access to this resource"). Dodatkowo `pitr progress --watch` dotyczy tylko klastrów HA — na pojedynczej instancji zwraca mylący komunikat, który łatwo wziąć za brak PITR.
- **Rule**: Na trialu Railway planuj kontrolę kosztów wokół budżetu trialu (brak egzekwowalnego twardego limitu) i ustaw limity ($ alert + hard limit) natychmiast po wykupieniu subskrypcji. Do odtwarzalności na trialu polegaj na PITR (ciągłe archiwizowanie + base backup z `pitr enable`); cykliczny harmonogram backupów traktuj jako pozycję po-subskrypcyjną. Zdrowie backupów sprawdzaj przez `railway postgres pitr status --json` (`backupSetCount`, `archiverHealthy`, `maxRestoreTime`), a nie przez `pitr progress` na usłudze nie-HA.
- **Applies to**: infra, deploy, plan

## Jedna gałąź + jeden PR na całość pracy; testuj w Railway PR Env, nie merguj ciągle do main

- **Context**: Praca nad spójną całością na Railway z autodeploy z `main` (scaffold deploy, feature, większa zmiana infra) — każdy merge do `main` = deploy na produkcję.
- **Problem**: Rozbicie jednej pracy na wiele małych gałęzi/PR-ów mergowanych po kolei do `main` daje branch/commit/PR churn, zaśmieca historię i — co ważniejsze — każdy taki merge idzie na produkcję. W tej sesji scaffold deploy rozjechał się na ~7 PR-ów do prod, część pod drobne aktualizacje docsów.
- **Rule**: Dla jednej całości pracy trzymaj JEDNĄ gałąź (`deploy/…` lub `feature/…`), dokładaj commity iteracyjnie, wystaw JEDEN PR i weryfikuj w izolowanym **Railway PR Environment** (własna baza, własne `/api/*`). Do `main` (=prod) merguj **raz**, gdy całość jest gotowa i sprawdzona — nie po każdym kroku. Aktualizacje dokumentacji (deploy-plan, lessons) dokładaj do tej samej gałęzi/PR, nie osobnymi PR-ami.
- **Applies to**: plan, implement, deploy, infra

## Railway IaC w CI: token workspace + ID środowiska, nigdy `railway link`; plan = 0 przed pierwszym apply

- **Context**: Automatyzacja Railway IaC (`.railway/railway.ts`) w GitHub Actions — `railway config plan/apply` dla production i PR environments.
- **Problem**: Token projektu obejmuje jedno środowisko i nie sięga do PR env; z tokenem workspace `railway link`/`whoami` dają „Unauthorized”, więc job CI pada. Dodatkowo `config apply` na pliku rozjechanym z produkcją wyzerowałby ustawienia z dashboardu (uśpienie) i skasował bucket Postgres-PITR z backupami (wykryte testem na PR #10, 2026-09-28).
- **Rule**: Do CI obejmującego prod i PR env używaj tokena workspace jako `RAILWAY_API_TOKEN` i wybieraj środowisko przez `RAILWAY_PROJECT_ID` + `RAILWAY_ENVIRONMENT_ID` (ID z `railway environment list --json`), nigdy przez `railway link`. Przed pierwszym automatycznym apply doprowadź `railway config plan --detailed-exit-code` do 0, a w CI blokuj zmiany `destructive`/`resource.delete` i nigdy nie używaj `--confirm-destructive`.
- **Applies to**: plan, plan-review, implement, infra, deploy

## Tylko człowiek merguje do main — agent nigdy

- **Context**: Każdy PR i każda zmiana trafiająca do `main`, który autodeployuje się na produkcję Railway.
- **Problem**: W `admin-bootstrap-and-login` (faza 4) agent zapytał o squash-merge PR #13 jako jedno z trzech pytań w jednym formularzu, warunkowo z wyprzedzeniem („gdy CI będzie zielone”), i po zielonym CI zmergował sam. Kod poszedł na produkcję bez świadomej decyzji człowieka w chwili merge'a.
- **Rule**: Agent nigdy nie merguje do `main` (ani nie wykonuje `gh pr merge` czy pusha na `main`) — merge zawsze wykonuje człowiek. Agent przygotowuje PR, doprowadza CI i weryfikację do zielonego stanu, a potem zatrzymuje się i przekazuje merge użytkownikowi.
- **Applies to**: all

## Pytanie to nie polecenie — odpowiedz i zaproponuj, nie wykonuj

- **Context**: Każda wymiana, w której użytkownik zadaje pytanie o stan pracy lub o decyzję agenta („czemu nie zrobiłeś X?”, „czy Y jest zrobione?”, „czy to ma sens?”), zwłaszcza w trakcie `/10x-implement` i prac wdrożeniowych.
- **Problem**: W `admin-bootstrap-and-login` użytkownik zapytał, czemu roadmapa nie została poprawiona zgodnie z regułą w AGENTS.md. Agent zamiast odpowiedzieć sam uruchomił `/10x-archive` wraz z propozycją przepięcia 25 SHA w Progress — działanie, o które nikt nie prosił; użytkownik musiał je anulować.
- **Rule**: Gdy użytkownik zadaje pytanie, odpowiedz na nie i — jeśli trzeba — zaproponuj konkretne działanie, a potem zatrzymaj się i poczekaj na wyraźną prośbę. Nigdy nie uruchamiaj skilli, poleceń ani zmian w plikach na podstawie samego pytania.
- **Applies to**: all

## Przed zamrożeniem kontraktu LLM zrób tani pilot skryptem na żywych danych

- **Context**: Każda zmiana, w której LLM ma oceniać lub ekstrahować treści według kontraktu (np. F-02 `assessment-contract-harness`) — zanim kontrakt zostanie uznany za „accepted”, a plan przejdzie przegląd.
- **Problem**: W `assessment-contract-harness` kontrakt V1 i plan (12 przypadków × 3 × macierz 4 dostawców) powstały na czystych, wymyślonych przykładach i przeszły plan-review jako SOUND, zanim jakikolwiek model zobaczył prawdziwy post. Dopiero jednorazowe skrypty na żywych danych (Wykop #gielda, Reddit; koszt kilku dolarów; `context/changes/assessment-contract-harness/pilot/`) pokazały, że: realny materiał różni się od przykładów (slang, memy, obrazki, twarde spacje, markdown); „przechodzi walidację kontraktu” (95–98%) nie znaczy „jest poprawne” (zgodność treści ocen między modelami ~50% miarą ostrą, 74–78% łagodną); dobór miary zmienia wniosek; wzorzec oznaczony przez człowieka bez instrukcji mierzy różnicę definicji, nie jakość modelu; ograniczenia dostawców (odrzucane słowa kluczowe schematu, gubione pola wymagane, wpływ paczkowania) wychodzą tylko empirycznie.
- **Rule**: Przy integracji LLM z produktem najpierw pobierz próbkę prawdziwych danych i przepuść ją jednorazowym skryptem przez 1–2 tanie modele — zanim zamrozisz kontrakt, zdefiniujesz „przechodzi” i zaplanujesz harness. Mierz zgodność z kontraktem ORAZ zgodność treści (między modelami, z powtórką i z człowiekiem oznaczającym według spisanej instrukcji), sprawdzaj więcej niż jedną miarę, a wyniki i skrypty zapisuj w folderze zmiany jako dowód dla `/10x-frame` i `/10x-plan`. Nie opieraj kryteriów akceptacji na wymyślonych przykładach ani na samej walidacji schematu.
- **Applies to**: research, frame, plan, plan-review

## Proaktywnie szukaj okazji do abstrakcji i porządkowania kodu, póki jest mały

- **Context**: Każda faza lub zmiana dokładająca kod — backend i frontend (szczególnie nowe moduły, integracje z zewnętrznymi usługami jak LLM, źródła danych, pipeline, nowe widoki i warstwy komunikacji z API), zwłaszcza na wczesnym etapie, gdy kod jest jeszcze mały.
- **Problem**: W `assessment-contract-harness` (fazy 1–3) kod był dokładany fazami zgodnie z planem, a wspólna infrastruktura LLM (port dostawcy, ponowienia, adaptery) wylądowała w środku jednego agenta (`app/assessment/providers`) i importowała jego kontrakt. „Rozlanie” zauważył dopiero użytkownik, porównując z innym projektem; im później, tym droższe przenosiny.
- **Rule**: Proaktywnie, przy każdej fazie — nie tylko na prośbę — szukaj okazji do abstrakcji i porządkowania: czy nowy kod nie miesza warstw (infrastruktura vs domena/agent), czy coś generycznego nie jest zaszyte w konkretnym module, czy nie powstaje duplikacja. Oceniaj też, czy obecnie napisany kod wytrzyma kolejne fazy i kroki roadmapy, czy już teraz potrzebuje abstrakcji, żeby dalej trzymać się wzorców projektowych i utrzymać kod w ryzach. Jeśli tak, zaproponuj porządkowanie wcześnie, póki kod jest mały: osobny commit refactor bez zmian zachowania, potwierdzony testami. Proponuj, nie przebudowuj bez zgody.
- **Applies to**: plan, plan-review, implement, impl-review

## Zamknięte zbiory wartości trzymaj w enumach, nie w wolnych stringach

- **Context**: Kod backendu i frontendu, w którym pojawia się zamknięty zbiór wartości: nazwy komend CLI, role, typy, kody błędów, dostawcy, statusy — szczególnie interfejsy i rejestry, które każda implementacja wypełnia własnym literałem.
- **Problem**: W `java-api-rewrite` interfejs `Command` miał metody `String name()` i `String description()`, więc każda komenda zwracała wolny literał (`"migrate"`, `"create-admin"`), a ta sama nazwa była powtórzona w komunikatach i testach. Literówka albo zmiana nazwy w jednym miejscu kompiluje się bez błędu i rozjeżdża się po cichu z resztą; kompilator i IDE nie pokazują pełnego zbioru wartości ani ich użyć.
- **Rule**: Tam, gdzie zbiór wartości jest zamknięty (typy, role, kody, nazwy komend, dostawcy), trzymaj go w enumie, a nie jako wolne stringi w kodzie.
- **Applies to**: implement, impl-review

## Implementacja, przegląd i archiwizacja na jednej gałęzi — merge do main dopiero na końcu

- **Context**: Każda zmiana z `/10x-implement` na Railway z autodeployem z `main` (doprecyzowanie lekcji „Jedna gałąź + jeden PR…”).
- **Problem**: W `java-api-rewrite` główny PR #26 zmergowano przed `/10x-impl-review` i `/10x-archive`. Przegląd znalazł obejście filtra Origin, które było już na produkcji, a poprawki, przegląd i archiwizacja wymagały trzech kolejnych PR (#27–#29). Do tego Progress zawierał kroki możliwe dopiero po merge (przełączenie i weryfikacja produkcji), co uniemożliwiało archiwizację przed merge.
- **Rule**: Na jednej gałęzi i w jednym PR wykonaj kolejno: `/10x-implement` (wszystkie fazy), weryfikację w Railway PR env, `/10x-impl-review` z poprawkami, `/10x-archive`. Człowiek merguje raz, na końcu. W planie nie umieszczaj w Progress kroków, które da się wykonać dopiero po merge (operacje i weryfikacja na produkcji): prowadź je jako runbook w `deploy-plan.md` i komentarze w issue, zamykane po weryfikacji produkcji, bez kolejnego PR.
- **Applies to**: plan, plan-review, implement, impl-review

## Akcje Embabel domyślnie się ponawiają — akcje ze skutkami ubocznymi oznaczaj FIRE_ONCE

- **Context**: Agenci Embabel (`@Agent`, `@Action`), szczególnie akcje, które zapisują stan, wołają zewnętrzne API lub LLM (pipeline S-02, przyszłe etapy ML, harmonogram S-05).
- **Problem**: `@Action` bez parametrów używa domyślnej polityki ponowień: po większości wyjątków (poza m.in. IllegalArgument/IllegalState) akcja jest wykonywana ponownie z rosnącą przerwą. W S-02 nieudany `fetch` z Wykopu szedł kilka razy co ≥10 s, przebieg nie kończył się w 60 s, a każdy ponowny krok zapisywałby się drugi raz. Wyszło dopiero przy teście z awarią źródła (przegląd końcowy `first-assessed-wykop-feed`, 2026-10-09).
- **Rule**: Każda akcja ze skutkami ubocznymi dostaje `@Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)`; ponowienia żyją tylko w warstwie transportu (`llm/transport/TransportRetry`). Dla każdego agenta napisz test, w którym akcja rzuca wyjątek, i sprawdź, że wykonała się raz.
- **Applies to**: plan, plan-review, implement, impl-review

## Railway Hobby nie ma kopii bazy — sprawdź tabelę funkcji planu i każdą kopię logiczną odtwarzaj w całości

- **Context**: Planowanie i wdrażanie ochrony danych Postgresa na Railway (F-01 `production-data-gate`, 2026-10-11), szczególnie po przejściu z trialu na płatny plan.
- **Problem**:
  - Plan z 2026-10-06 zakładał, że po subskrypcji wystarczy włączyć dzienną kopię wolumenu Railway. Tymczasem kopie wolumenu i PITR są tylko w planie Pro (tabela „Compare features” na `railway.com/pricing`), a projekt jest na Hobby.
  - Wolumen z trialu (500 MB, ~434 MB systemu plików) nie urósł sam przy zmianie planu. Domyślny `max_wal_size` 1 GB mógł go zapełnić samym WAL-em („No space left on device” zabiło też drill PITR z 2026-09-27).
  - `pg_dump --exclude-table-data` po cichu nic nie robi, gdy wzorzec nie pasuje. Dump, który się nie odtwarza, wygląda tak samo jak dobry.
- **Rule**: Przed planowaniem backupu sprawdź tabelę funkcji planu (`railway.com/pricing`), a nie samą dokumentację funkcji, i rozmiar wolumenu po zmianie planu (`df -h` w kontenerze, nie panel). Na Hobby kopią jest `pg_dump` z osobnego serwisu (cron), z kopią poza platformą. Kopię logiczną uznawaj za ważną dopiero wtedy, gdy przeszła pełne odtworzenie do tymczasowej bazy: kontrole tabel, wersji Flyway i liczby wierszy, a raz test z aplikacją. Zawartość dumpa (np. wykluczone dane) sprawdzaj przez `pg_restore --list`.
- **Applies to**: infra, plan, plan-review

## Nowy bucket w Railway IaC sprawdzaj na produkcji, a stałe diffy usuwaj z railway.ts

- **Context**: Zmiana w `.railway/railway.ts`, która dodaje nowy zasób projektu (np. bucket) albo deklaruje ustawienie usługi, oraz każde (ponowne) uruchomienie apply IaC na produkcji (F-01 `production-data-gate`, 2026-10-12).
- **Problem**: CI stosuje IaC najpierw do PR env, więc nowy bucket powstał w projekcie tylko z instancją w PR env; apply na produkcji po merge'u zalogował `✓ bucket.…`, ale go tam nie wdrożył — referencje `${{db-backups.*}}` były puste i pierwsza kopia padła. Ponowny apply ze stałym diffem (`sleepApplication null → false`) zrestartował produkcyjnego Postgresa, który zawisł na „Mounting volume” na 9 min.
- **Rule**: Zmianę dodającą bucket w IaC planuj z krokiem produkcyjnym: po pierwszym apply na produkcji sprawdź `railway bucket list -e production` i długości zmiennych z referencjami (nie sam log CI), a brakujący bucket utwórz tam pod tą samą nazwą. Nie trzymaj w `railway.ts` deklaracji dającej stały diff i przed każdym apply na produkcji sprawdź w planie, które usługi zrestartuje.
- **Applies to**: plan, plan-review, implement, impl-review, infra, deploy

## Pole w railway.ts, którego SDK nie zna, znika bez błędu — sprawdzaj żywą konfigurację usługi

- **Context**: Każda deklaracja ustawienia usługi w `.railway/railway.ts` (SDK `railway` 3.11), szczególnie nowe pole albo pole przepisane z dokumentacji lub panelu (2026-10-15, po pierwszym nocnym przebiegu S-05).
- **Problem**: `watchPatterns` stało na najwyższym poziomie `service(...)`, a SDK ma je tylko w `build.watchPatterns`. Pole zniknęło bez błędu: plan był pusty, produkcja nie miała ścieżek obserwowanych i każdy commit na `main`, także sama dokumentacja, przedeployował wszystkie usługi. Merge dokumentacji #55 zrestartował API i przerwał trwający przebieg pipeline'u. PR envy tego nie pokazały, bo tam Railway sam pomija usługi poza zmienionym katalogiem.
- **Rule**: Nowe pole w `railway.ts` sprawdź z typem `IntentServiceConfig` w `node_modules/railway/dist/*.d.ts`, a po apply — w żywej konfiguracji usługi na produkcji (MCP `get-service-config` albo panel), nie po pustym planie. Pusty plan po dodaniu pola znaczy, że pole nic nie robi.
- **Applies to**: plan, implement, impl-review, infra, deploy

## Przed /10x-implement nie modyfikuj plików kodu ani konfiguracji — także przy pilnym problemie

- **Context**: Każda faza przed `/10x-implement` (`/10x-new`, `/10x-frame`, `/10x-research`, `/10x-plan`, plan-review), szczególnie gdy w trakcie pojawia się pilny problem (np. wyczerpany limit GitHub Actions blokujący CI).
- **Problem**: W `self-hosted-pr-runner` (2026-10-18), w trakcie wywiadu `/10x-plan`, użytkownik zapytał „czy możemy zrobić obejście” po wyczerpaniu limitu Actions. Agent sam zmienił `.github/workflows/ci.yml` i utworzył `tools/ci-runner/` bez zgody i bez planu.
- **Rule**: Przed `/10x-implement` nigdy nie modyfikuj plików kodu ani konfiguracji, także przy pilnym problemie. Przedstaw opcje i poczekaj na wyraźne polecenie przejścia do implementacji.
- **Applies to**: frame, research, plan

## Uzasadniaj zakres celem użytkownika, zanim dodasz mechanizm

- **Context**: Planowanie, research i review zmian, szczególnie gdy analiza ujawnia kolejne ryzyka i przypadki brzegowe.
- **Problem**: Agent traktuje znalezione ryzyka jako nowe wymagania i rozbudowuje rozwiązanie poza cel użytkownika. Przy weryfikacji deployu paczki doproponował rozliczanie historii, ledger i codzienny monitoring produkcji, a po korekcie nadal szukał wariantów nadmiarowego rozwiązania.
- **Rule**: Przed dodaniem mechanizmu wskaż wymaganie użytkownika, którego bez niego nie da się spełnić; jeśli cel można osiągnąć bez tego mechanizmu, pozostaw go poza zakresem. Podczas review rozważ usunięcie zbędnej funkcji przed jej naprawianiem, a po korekcie zakresu przez użytkownika usuń rozszerzenie zamiast proponować jego kolejne warianty.
- **Applies to**: frame, research, plan, plan-review, implement, impl-review

## Planuj weryfikację agent-first, zachowując bramki człowieka

- **Context**: Planowanie i przegląd slice'ów z `/10x-plan` oraz `/10x-plan-review`, szczególnie weryfikacja UI, API, integracji, CI i Railway PR environments.
- **Problem**: Powtarzalne kontrole techniczne trafiają do Manual Verification, co wymaga od operatora ręcznego wykonywania testów i zatrzymuje pracę nad wieloma równoległymi slice'ami.
- **Rule**: Dla każdego kryterium sukcesu wybierz najtańszą wiarygodną metodę weryfikacji: najpierw deterministyczny test automatyczny, następnie wykonanie scenariusza przez agenta z obserwowalnymi dowodami, a dopiero potem czynność człowieka. Manual zachowuj dla ocen i decyzji wymagających człowieka, ryzykownych operacji i przypadków bez wiarygodnej automatyzacji. Nie rezygnuj z weryfikacji ani nie uznawaj samego raportu agenta za niezależny dowód poprawności.
- **Applies to**: plan, plan-review, implement, impl-review

## Ponowienia źródła żyją w jego kliencie, w terminie poniżej watchdoga; stronicowanie sprawdza spójność, nie tylko liczbę

- **Context**: Każde źródło danych w `sources/<source>/` (Wykop, później Reddit), szczególnie klient API i stronicowanie wyników (S-16 `wykop-ingest-completeness`, 2026-10-20). Uzupełnia regułę „Akcje Embabel domyślnie się ponawiają…”, która wymienia tylko `llm/transport/TransportRetry`.
- **Problem**: Do S-16 `WykopApiClient` celowo nie ponawiał niczego, a akcja `fetch` jest `FIRE_ONCE`, więc jeden chwilowy 429/5xx Wykopu kończył całą noc `failed` (audyt B6). Stronicowanie kończyło się „kompletne”, gdy licznik otrzymanych elementów (z duplikatami) doszedł do `pagination.total` albo przyszła pusta strona; usunięcie wpisu w trakcie przesuwa strony i gubi element na granicy, choć liczby się zgadzają (50 + 49 = 99 = nowy `total`) (audyt B5). Krok `fetch` nie raportuje postępu, więc pobieranie dłuższe niż próg watchdoga (najkrócej 15 min) zostałoby zatrzymane jako „bez postępu” z mylącą przyczyną.
- **Rule**: Ponowienia źródła żyją w jego kliencie (warstwie transportu źródła), nigdy w akcji ani w `SourceIngest`: tylko chwilowe błędy (429, 5xx, transport), ograniczona liczba prób, `Retry-After` z limitem, każde ponowienie jako WARN bez sekretów, i zawsze w terminie całego pobrania, który repozytorium wyznacza poniżej `PipelineStallSettings.MIN_DEFAULT` (z zapasem na time-out ostatniego żądania; test przypina tę relację). Stronicowanie po `total` liczy unikalne elementy i sprawdza spójność: pusta lub krótka strona przed `total` albo `total` zmieniony między stronami to niespójność — jedno ponowne czytanie, potem błąd źródła, nigdy wynik „kompletny” z dziurą. Testy dowodzą tego liczbą żądań.
- **Applies to**: plan, plan-review, implement, impl-review
