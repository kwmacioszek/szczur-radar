# S-25: jedna reguła adresu check-in alarmów — Plan Brief

> Full plan: `context/changes/alert-ping-single-rule/plan.md`
> Research: `context/changes/post-m2-audit/research.md` (audyt po M-2, P4 i P5; ta zmiana nie ma własnego `research.md`)

## What & Why

Adres check-in healthchecks.io (`HEALTHCHECK_PING_URL`) jest sprawdzany w czterech miejscach na trzy różne sposoby, a zgodności pilnuje tylko komentarz „change both”. Rozjazdy już istnieją: crony odrzucają `HTTPS://` i przepuszczają adres bez hosta, `checks.sh` przepuszcza `?` i `#`. Zły wzorzec po cichu wyłącza meldunki crona, a API przy złym adresie nie startuje. Do tego oba crony różnie zgłaszają limit czasu (`124` i `1`). NFR `prd.md:259` wymaga jednoznacznego sygnału awarii (R-27).

## Starting Point

- `AlertSettings` w API sprawdza adres przez `java.net.URI` (fail-fast).
- `backup.sh` i `trigger.sh` mają identyczny blok z własnym regexem (warn-and-continue).
- `checks.sh` ma luźniejszy regex.
- Testy: trigger ma dwa przypadki odmowy, db-backup żadnego, nic nie porównuje kopii.
- Wspólnego pliku shell nie da się wnieść do obrazów, bo każdy cron buduje się z własnego `rootDirectory`, a bramka S-14 nie przepuszcza zmiany tego pola.

## Desired End State

- Jeden literał wzorca, bajt w bajt ten sam w `AlertSettings.PING_PATTERN`, obu cronach i `checks.sh`.
- Jedna tabela przypadków (`tools/healthchecks/fixtures/ping-urls.txt`), przez którą przechodzą Java i blok bashowy.
- Krok 9 `tools/healthchecks/test.sh` robi się czerwony przy każdym rozjeździe bloku, literału albo werdyktu.
- Oba crony po limicie czasu meldują `/1` i kończą się kodem `1`.
- Produkcja nie wymaga zmian: jej adresy `https://hc-ping.com/<uuid>` są w tabeli jako `accept`.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Dom reguły (Q-09) | Jeden literał regex we wszystkich czterech miejscach; `URI` w Javie tylko buduje obiekt | „Ta sama reguła” staje się porównaniem bajtów, nie argumentem | Plan |
| Wspólny plik shell / generowanie z Javy | Odrzucone | Wymaga zmiany `rootDirectory`, której bramka S-14 nie przepuszcza; obrazy cronów nie mają Javy | Plan (kod) |
| Kod po limicie czasu | Oba `1` (db-backup mapuje `124`/`137`) | Zachowuje zamknięty zbiór 0/1/2 triggera i zasadę „meldunek = kod wyjścia”; przyczynę mówi linia logu | Plan |
| Miejsce strażnika | Krok 9 `tools/healthchecks/test.sh` + `AlertSettingsTest` na tej samej tabeli | Bez nowego zadania CI; każdy język testowany w swoim runnerze | Plan |
| Zachowanie przy złym adresie | Bez zmian: API fail-fast, crony warn-and-continue | Decyzja F-04: zły adres alarmów nie psuje kopii ani nocnego przebiegu | Audyt / F-04 |
| Postać wzorca | `https` w dowolnej wielkości liter, nazwa hosta (bez `user@`, bez IP), opcjonalny port, ścieżka bez `?#`, `%` tylko jako `%XX`, bez `\` | Literał bez ukośników wstecznych jest tym samym tekstem w Javie i bashu; sprawdzony na 21 przypadkach | Plan |

## Scope

**In scope:**
- Wzorzec i tabela w API.
- Blok ze znacznikami w obu cronach i literał w `checks.sh`.
- Krok 9 testu healthchecks.
- Przypadki w testach obu obrazów cronów (P5).
- Kod `1` po limicie czasu w db-backup.
- README trzech narzędzi, Javadoc, `REPOSITORY_GUIDELINES.md`, sekcja S-25 w `deploy-plan.md`.

**Out of scope:**
- Zmiana fail-fast i warn-and-continue.
- Obcinanie białych znaków (Unicode w Javie, ASCII w shellu).
- Nowe zadanie CI i wspólny plik w obrazach.
- `url_pattern` w `checks.sh`.
- Osobny kod dla `137`.
- Repo monitora.
- Edycje destylacji i audytu.

## Architecture / Approach

Literał żyje w czterech plikach, a pilnuje go jeden test. Krok 9 wycina blok między znacznikami `# ping-url rule (S-25): begin/end` z obu cronów i porównuje go bajt w bajt. Potem wyciąga linię wzorca z bloku, z `checks.sh` i z `AlertSettings.java` i sprawdza, że są równe. Na koniec uruchamia blok w podpowłoce dla każdego wiersza tabeli. `AlertSettingsTest` czyta tę samą tabelę. Testy cronów w Dockerze potwierdzają regułę w bashu obrazów, które działają na Railway (glibc i musl).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Reguła i tabela przypadków w API | `PING_PATTERN`, `ping-urls.txt`, `AlertSettingsTest` na tabeli | Zawężenie w API (`user@`, IP, pusty port) — produkcja ich nie używa |
| 2. Kopie w shellu i strażnik zgodności | Bloki ze znacznikami, literał w `checks.sh`, krok 9, przypadki w obrazach | Różnica regexów glibc i musl — łapią ją testy obrazów |
| 3. Limit czasu jako `1` i dokumenty operatora | db-backup `124`/`137` → `1`, README, runbook S-25, zielone CI | Adres produkcyjny poza regułą zatrzymałby start API (kształt jest w tabeli) |

**Prerequisites:** brak (S-25 bez zależności); Docker lokalnie dla testów cronów; issue #119.
**Estimated effort:** ~1–2 sesje w 3 fazach.

## Open Risks & Assumptions

- Zakładamy, że zapieczętowane wartości produkcyjne mają kształt z `checks.sh apply` (`https://hc-ping.com/<uuid>`). Agent nie może ich odczytać, więc potwierdzi to dopiero zielony run monitora po deployu. Ścieżka naprawy jest w runbooku S-25.
- `AlertSettingsTest` czyta plik spoza modułu (`../tools/…`). Działa, bo testy biegną tylko w CI na pełnym checkoucie, a obraz API ich nie uruchamia.

## Success Criteria (Summary)

- Zmiana jednej kopii reguły bez pozostałych robi CI czerwone z nazwą rozjazdu.
- `HTTPS://…` i adres produkcyjny działają wszędzie; adres bez hosta, z `?` albo `#` jest odrzucany wszędzie.
- Przekroczony limit czasu wygląda w Telegramie tak samo dla obu cronów (`1` z linią `FAILED: the run exceeded …`).
