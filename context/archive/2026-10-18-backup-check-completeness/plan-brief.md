# S-15: kopia bazy sprawdzana w całości — Plan Brief

> Full plan: `context/changes/backup-check-completeness/plan.md`
> Research: `context/changes/backup-check-completeness/research.md`

## What & Why

Nocna kopia bazy (`tools/db-backup/`, F-01) to jedyna kopia danych na Railway Hobby. Ma dwie luki (audyt C9):
- jej kontrola nie zna tabel z V7 (`activation_tokens`) i V9 (`notifications`);
- przepuszcza kopię pustej bazy, która w ciągu 14 dni wypiera dobre kopie z bucketu i z lustra NAS.

Zmiana domyka obie luki, zanim zła kopia trafi do retencji.

## Starting Point

- `restore-check.sh` wymaga stałej listy 9 tabel ze schematu V5.
- `backup.sh` liczy 5 tabel, a kopia przechodzi, gdy liczba wierszy po odtworzeniu mieści się między pomiarem przed dumpem a pomiarem po nim. Pusta baza daje przedział [0, 0], więc przechodzi.
- Nowa tabela z migracji przechodzi po cichu, bo `test.sh` stosuje migracje, ale nie porównuje tabel z listami.
- Kopie z V5 nadal są w retencji (kopia miesięczna 2026-10 zostaje 12 miesięcy).

## Desired End State

- **Kontrola tabel.**
  - Każda tabela ma klasyfikację w `tools/db-backup/tables.sh`.
  - `restore-check.sh` wymaga tabel zgodnie z wersją Flyway dumpa, więc stare dumpy przechodzą.
  - Kontrola odrzuca tabelę, której nie zna.
- **Kontrola wiarygodności przed dumpem.** `backup.sh` przerywa przebieg, bez dumpa i bez wysyłki, w dwóch przypadkach:
  - `posts` jest pusta;
  - rosnąca tabela ma mniej wierszy niż w najnowszym manifeście.
  
  Przebieg kończy się `CRASHED` i powiadomieniem.
- **Zgoda po odtworzeniu.** Po celowym odtworzeniu operator ustawia jednorazową zgodę `BACKUP_ACCEPT_SHRINK`, którą komunikat błędu podaje gotową.

## Key Decisions Made

| Decyzja | Wybór | Dlaczego | Źródło |
|---|---|---|---|
| Co to „podejrzanie pusta” | Brak spadku względem najnowszego manifestu w `daily/` dla 7 rosnących tabel oraz `posts` > 0 zawsze | Łapie i wyczyszczoną bazę, i częściową utratę. Przy bezterminowej retencji każdy spadek to błąd albo celowe odtworzenie | Plan |
| Wyjście awaryjne | `BACKUP_ACCEPT_SHRINK=<nazwa najnowszego manifestu>`, ważne tylko wobec niego | Zapomniana zmienna nie wyłącza kontroli na stałe: po jednej udanej kopii najnowszy jest już inny manifest | Plan |
| Źródło listy tabel | Stała klasyfikacja w `tables.sh` (wersja Flyway, `counted`/`present`); nieznana tabela daje błąd | Migracja z tabelą czerwieni `db-backup-test` w swoim PR; stare dumpy przechodzą | Plan |
| Które tabele liczyć | 5 dzisiejszych + `pipeline_run_steps` i `notifications`; `activation_tokens` i sesje tylko „obecne” | W tych 3 tabelach wiersze znikają (`ActivationLinks.java:168,196`, Spring Session) | Research |
| Gdzie sprawdzać spadek | `backup.sh`, przed `pg_dump` | Tylko ten skrypt widzi bucket; porażka przed dumpem nie rusza retencji | Research |
| Zakres IaC | `preserve()` tylko dla `BACKUP_IN_ANY_ENVIRONMENT` i `BACKUP_ACCEPT_SHRINK`; reszta C7 dla S-14 | Weryfikacja w PR env nie zablokuje bramki, a konflikt z S-14 jest minimalny | Plan |

## Scope

**In scope:** `tables.sh`; `restore-check.sh` (wymagania zależne od wersji, nieznana tabela); `backup.sh` (liczenie tabel, minimum, brak spadku, zgoda, pola manifestu); `test.sh`; 2 deklaracje `preserve()`; README; `deploy-plan.md` (krok runbooka, rekord S-15); reguła w `REPOSITORY_GUIDELINES.md`.

**Out of scope:** pozostałe zmienne C7 i Wait for CI dla crona (S-14); próg procentowy; lista tabel w manifeście; retencja, NAS i Drive; dead-man's switch (F-04); automatyczne odtwarzanie; reszta długu dokumentacji (S-19).

## Architecture / Approach

`tables.sh` to jedno źródło wiedzy o schemacie, czytane przez oba skrypty.

`restore-check.sh` odtwarza dump i najpierw czyta wersję Flyway. Potem sprawdza:
- wymagane tabele przy tej wersji;
- nieznane tabele;
- puste sesje;
- liczby wierszy z manifestu.

`backup.sh` po pierwszym liczeniu wierszy:
1. czyta najnowszy manifest z `daily/`, pomijając daty z przyszłości;
2. sprawdza minimum i brak spadku;
3. dopiero potem robi dump, restore check, wysyłkę i retencję, jak dziś.

## Phases at a Glance

| Faza | Co daje | Główne ryzyko |
|---|---|---|
| 1. Klasyfikacja tabel i restore-check | Kontrola zna cały schemat, stare dumpy przechodzą, nowa tabela daje błąd | Test „missing table” musi przejść na `activation_tokens`, bo `pipeline_run_steps` będzie liczone |
| 2. Odrzucanie podejrzanie pustej kopii | Spadek albo pusta baza dają porażkę przed dumpem; działa jednorazowa zgoda | Kolejność przypadków w `test.sh`: pusta baza na końcu, a spadek przepuszczony zgodą zmienia punkt odniesienia |
| 3. IaC, dokumenty i weryfikacja w PR env | `preserve()`, runbook po odtworzeniu, reguła dla migracji, przebieg na żywym Railway | PR env bez bazy i bez postów (ręczny wolumen i dane przykładowe SQL-em); zmienna wymaga redeployu crona |

**Prerequisites:**
- Docker lokalnie;
- dostęp operatora do Railway (PR env, bucket produkcji do pobrania dumpów);
- issue S-15 i gałąź z `gh issue develop` (`lessons.md`).

**Estimated effort:** ~2 sesje. Fazy 1–2 to skrypty i test, faza 3 to dokumenty i weryfikacja w PR env.

## Open Risks & Assumptions

- **Założenie o rosnących tabelach.** Zakładamy, że 7 rosnących tabel nie ma ścieżki usuwania. Jeśli któraś ją dostanie, kontrola spadku da fałszywy alarm. Klasyfikację w `tables.sh` trzeba wtedy zmienić na `present`, co reguła w `REPOSITORY_GUIDELINES.md` przypomina.
- **Pierwsza kopia po wdrożeniu.** Porówna się z manifestem, który zna 5 tabel, więc 2 nowe tabele zostaną pominięte. To zamierzone.
- **Równoległa praca z S-14.** S-14 może równolegle edytować blok `db-backup` w `railway.ts`. Konflikt będzie mały (2 linie `preserve()`).
- **Brak alarmu przy padzie crona.** Porażka dociera do operatora tylko przez regułę konta „deployment crashed”, której nie ma w IaC (C2, F-04).

## Success Criteria (Summary)

- **Pusta lub okrojona baza.** Jej kopia nie trafia do bucketu, a operator dostaje powiadomienie z gotową instrukcją.
- **Nowa tabela w migracji.** Czerwieni `db-backup-test` w PR, dopóki nie trafi do `tables.sh`.
- **Starsze kopie.** Prawdziwe kopie z V5 i V9 przechodzą `restore-check.sh`.
- **PR env.** Przebieg w PR env potwierdza ciąg: OK, potem spadek `CRASHED`, potem zgoda OK. `railway-config` zostaje zielone.
