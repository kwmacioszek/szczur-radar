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
