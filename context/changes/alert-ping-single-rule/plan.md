# S-25: jedna reguła adresu check-in alarmów — Implementation Plan

## Overview

Adres check-in healthchecks.io (`HEALTHCHECK_PING_URL`) dostaje jedną regułę: jeden literał wzorca, użyty dosłownie w API (`AlertSettings`), w obu cronach (`backup.sh`, `trigger.sh`) i w narzędziu checków (`checks.sh`). Jedna tabela przypadków jest przepuszczana przez Javę i przez blok bashowy. Test w `healthchecks-test` wykrywa każdy rozjazd kopii. Oba crony zgłaszają przekroczony limit czasu tym samym kodem `1`. Roadmapa: `context/foundation/roadmap.md` (S-25), issue #119. Źródła: NFR `prd.md:259`, audyt po M-2 P4 i P5 (`context/changes/post-m2-audit/research.md:105-106`), destylacja R-27, Q-09 i ranking #7 (`context/domain/domain-distillation.md:182,237,256`).

Konwencja ścieżek: `api/…` = `szczur-radar-api/src/main/java/pl/szczurradar/api/…`, `test/…` = `szczur-radar-api/src/test/java/pl/szczurradar/api/…`.

## Current State Analysis

- **Java** (`api/alerts/AlertSettings.java:47-61`): `java.net.URI`. Schemat `https` bez względu na wielkość liter, wymagany host, bez zapytania i fragmentu. Przy złym adresie rzuca `InvalidSettingsException` (serwer nie startuje, `send-test-alert` kończy się kodem 2), a komunikat nigdy nie powtarza wartości. Jedna końcowa `/` jest odcinana. Przy okazji przepuszcza dane logowania (`https://user@host/…`), adres IP i pusty port (`host:/…`) i odrzuca host z `_`. To skutki semantyki `URI`, nie zapisane reguły. Testy: `test/alerts/AlertSettingsTest.java:21-56` (9 przypadków odmowy wpisanych w kod).
- **Oba crony** (`tools/db-backup/backup.sh:48-62` ≡ `tools/pipeline-trigger/trigger.sh:52-66`, identyczne): wzorzec `^https://[A-Za-z0-9._~:/@!$&()*+,;=%-]+$`. Odrzuca `HTTPS://`, przepuszcza `https:///uuid` bez hosta i host z `_`. Przy złym adresie skrypt tylko ostrzega `HEALTHCHECK_PING_URL is not an https:// address without ? or #; no check-in` i nie wysyła meldunków (warn-and-continue, decyzja F-04: zły adres alarmów nie może zepsuć kopii ani nocnego przebiegu). Zgodności pilnuje tylko komentarz „change both” (`backup.sh:48`, `trigger.sh:52`).
- **`checks.sh:51`**: `ping_pattern` przepuszcza `?` i `#`. Służy do sprawdzenia `ping_url` z odpowiedzi Management API (`:219-220,238-239`), zanim narzędzie uzbroi check i wydrukuje adres operatorowi. Mógłby więc wydrukować adres, który odrzuci zarówno API, jak i crony.
- **Limit czasu**: `backup.sh:96-101` melduje i kończy się kodem `timeout` (`124`, po dobiciu `137`). `trigger.sh:101-106` zamienia oba na `1`, zgodnie ze swoim zamkniętym zbiorem kodów 0/1/2 (`trigger.sh:3-6`, `tools/pipeline-trigger/README.md:23`). Oba README opisują swoje zachowanie, a `context/deployment/deploy-plan.md:1204` (F-04) opisuje tę różnicę.
- **Testy cronów**: trigger ma `ping-not-https` i `ping-query` (`tools/pipeline-trigger/test.sh:445-457`). db-backup nie ma żadnego przypadku walidacji (P5), a `run4` oczekuje `start 124` (`tools/db-backup/test.sh:460-467`). Nic nie porównuje kopii.
- **Budowanie**: każdy cron buduje się z własnego katalogu (`.railway/railway.ts:228,275`, `rootDirectory`), więc wspólnego pliku shell nie da się `COPY` bez zmiany `source.rootDirectory`. Takiej zmiany bramka S-14 nie przepuszcza (`tools/deploy-gate/gate.jq`: ze zmian źródła tylko `source.checkSuites → true`). Obraz API nie uruchamia testów (`szczur-radar-api/Dockerfile:11-12`), a `./mvnw -B verify` biegnie w CI na pełnym checkoucie.

## Desired End State

- Reguła istnieje jako jeden literał (wzorzec niżej). W `AlertSettings` jest stałą `PING_PATTERN`, w obu cronach i w `checks.sh` linią `readonly ping_pattern='…'`, a tekst wzorca jest wszędzie bajt w bajt ten sam.
- `tools/healthchecks/fixtures/ping-urls.txt` zawiera przypadki `accept`/`refuse`. Przechodzą przez nie `AlertSettingsTest` i blok bashowy wycięty z `backup.sh` w kroku 9 `tools/healthchecks/test.sh`, z tym samym wynikiem.
- Krok 9 `tools/healthchecks/test.sh` robi się czerwony, gdy (a) bloki między znacznikami w `backup.sh` i `trigger.sh` różnią się choćby bajtem, (b) literał w `checks.sh` albo w `AlertSettings.java` różni się od literału cronów, (c) blok bashowy rozstrzyga którykolwiek przypadek tabeli inaczej niż ona.
- Oba crony po przekroczeniu limitu czasu meldują `/1` z linią `FAILED: the run exceeded …` i kończą się kodem `1`.
- Adres w kształcie produkcyjnym (`https://hc-ping.com/<uuid>`, z `checks.sh`) przechodzi w API, w obu obrazach cronów i w `checks.sh`. Produkcja nie wymaga żadnej zmiany zmiennych.
- README trzech narzędzi, Javadoc `AlertSettings`, `REPOSITORY_GUIDELINES.md` (wpis `tools/healthchecks/`) i `deploy-plan.md` (nowa sekcja S-25 i dopisek przy F-04) opisują nową regułę i kod `1`.

### Key Discoveries:

- Wzorzec bez ukośników wstecznych (kropka jako `[.]`) jest tym samym tekstem w literale Javy i w bashu, więc porównanie literałów to zwykłe porównanie linii.
- Sprawdzone lokalnie (bash i `java` na 21 przypadkach): dla każdego adresu, który przepuszcza wzorzec, `java.net.URI` daje host, brak zapytania i brak fragmentu. Java nie ma więc dodatkowych odmów, a `URI` zostaje tylko do zbudowania obiektu. Stara reguła Javy przepuszczała `user@host`, adres IP i `host:/…`, a nowa je odrzuca. To jedyne zawężenie w API.
- Blok walidacji w cronach biegnie przed procesem z limitem czasu, a przy złym adresie przebieg idzie dalej. Przypadki odmowy w db-backup kończą się więc tanio, na `PGHOST is not set` (`backup.sh:104-106`), bez kopii.
- Testy cronów biegną w prawdziwych obrazach (Debian z glibc i Alpine z musl), co jest jedynym miejscem, gdzie wzorzec sprawdza bash w obrazie, który naprawdę działa na Railway.

## What We're NOT Doing

- Nie zmieniamy zachowania przy złym adresie. API dalej nie startuje (fail-fast), a crony dalej ostrzegają i nie meldują się (warn-and-continue, F-04).
- Nie ruszamy obcinania białych znaków. Java używa `String.strip()` (białe znaki Unicode), shell `[:space:]`. Tabela przypadków nie zawiera białych znaków na brzegach; obcinanie zostaje w dotychczasowych testach każdej strony.
- Bez wspólnego pliku shell w obrazach, generowania skryptów i nowego zadania CI. Pierwsze wymaga zmiany `rootDirectory`, której bramka S-14 nie przepuszcza; nowe zadanie CI nie jest potrzebne, skoro `healthchecks-test` już istnieje.
- Bez reguły dla innych adresów `checks.sh` (`url_pattern` dla `HEALTHCHECKS_API_URL`). S-25 dotyczy tylko adresu check-in.
- Kod `137` (dobicie po `--kill-after`) nie dostaje osobnego kodu w meldunku. Różnicę widać tylko w logu.
- Bez zmian w repo monitora `szczur-radar-monitor`, który też pinguje `production/deploy-health`: to inne repo i jego adres nie przechodzi przez żadną z czterech kopii.
- Destylacja (`context/domain/`) i audyt nie są edytowane. Następny przebieg destylacji porówna się z kodem.

## Implementation Approach

Najpierw reguła i tabela w API (faza 1): `AlertSettingsTest` na tabeli daje szybką pętlę `./mvnw test`, a literał jest gotowy do skopiowania. Potem kopie w shellu ze strażnikiem (faza 2), który od razu porównuje się z literałem Javy z fazy 1. Na koniec kod limitu czasu i dokumenty operatora (faza 3), osobno, bo to zmiana zachowania jednego crona z własnym testem w Dockerze.

## Critical Implementation Details

- **Literał musi zostać w jednej linii** w każdym z czterech plików: `static final String PING_PATTERN = "…";` w Javie (bez łamania przez `+`, także przy reformatowaniu w IntelliJ) i `readonly ping_pattern='…'` w shellu. Krok 9 wyciąga go `sed`em z tej linii.
- **Stary test triggera `ping-ok` używa `$ping_url/`**, czyli z końcową `/`. Nowy przypadek z wielkimi literami ma być osobnym wywołaniem albo zmianą `ping-ok` na `HTTPS://…/`. Nie zmieniaj `$ping_url` globalnie, bo `pings_were` dopasowuje ścieżkę po kluczu, nie po schemacie.

## Phase 1: Reguła i tabela przypadków w API

### Overview

Reguła staje się jawnym wzorcem w `AlertSettings`, a przypadki wpisane w test przenoszą się do wspólnej tabeli, z której korzysta też shell w fazie 2.

### Changes Required:

#### 1. Tabela przypadków

**File**: `tools/healthchecks/fixtures/ping-urls.txt` (nowy), `tools/healthchecks/fixtures/README.md`

**Intent**: Jedno źródło przypadków reguły dla Javy i bashu. Ma zawierać co najmniej: kształt produkcyjny `https://hc-ping.com/<uuid>`, `HTTPS://…`, host z portem i ścieżką (kształt atrapy testów cronów `https://hc-ping.test:8443/ping/<klucz>/run1`), `https://localhost`, `%2F` w ścieżce, końcową `/` (przyjęta) oraz odmowy: `http://`, `ftp://`, brak schematu, `https:///uuid`, `?`, `#`, `_` w hoście, etykieta zaczynająca się od `-`, `user@host`, adres IP, ostatnia etykieta z cyfrą na początku, pusty port, `%zz`, spacja, `"`, `\`.

**Contract**: Linie `accept <adres>` albo `refuse <adres>`. Werdykt to pierwszy token, a adres to reszta linii po jednej spacji, więc może zawierać spacje. Linie puste i zaczynające się od `#` są pomijane. Białych znaków na brzegach adresu nie ma. Dla przypadków `accept` adresy są fikcyjne (UUID w stylu `00000000-…`), nie sekrety. W `fixtures/README.md` dopisz, że ten plik nie jest nagraniem odpowiedzi API, tylko tabelą reguły S-25, i kto go czyta.

#### 2. Wzorzec w `AlertSettings`

**File**: `api/alerts/AlertSettings.java`

**Intent**: Walidacja to dopasowanie całego adresu do `PING_PATTERN`. `URI` jest tworzony dopiero po dopasowaniu i tylko jako obiekt (nadal w `parse` z `null` przy błędzie, bo komunikat `URISyntaxException` cytuje wartość). Komunikat `RULE` i Javadoc mówią to samo co wzorzec: `https` w dowolnej wielkości liter, nazwa hosta (bez danych logowania i adresu IP), opcjonalny port, ścieżka bez `?` i `#`, `%` tylko jako `%XX`. Javadoc wskazuje kopie (`backup.sh`, `trigger.sh`, `checks.sh`) i krok 9 `tools/healthchecks/test.sh` jako strażnika.

**Contract**: `static final String PING_PATTERN` (package-private, czyta go test) w jednej linii, skompilowany raz do prywatnego `Pattern`. Sygnatura `from`, wyjątki, `configured()`, `pingUrl()`, `toString()` i odcinanie jednej końcowej `/` bez zmian. Wzorzec (sprawdzony lokalnie w bashu i w Javie):

```
^[Hh][Tt][Tt][Pp][Ss]://([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?[.])*[A-Za-z]([A-Za-z0-9-]*[A-Za-z0-9])?(:[0-9]+)?(/([A-Za-z0-9._~:/@!$&()*+,;=-]|%[0-9A-Fa-f]{2})*)?$
```

#### 3. Test na tabeli

**File**: `test/alerts/AlertSettingsTest.java`

**Intent**: Przypadki wpisane w `anythingButAnAbsoluteHttpsUrlIsRefusedWithoutRepeatingTheValue` zastępuje test sparametryzowany czytający `../tools/healthchecks/fixtures/ping-urls.txt` (ścieżka względem katalogu modułu, w którym Surefire uruchamia testy). `accept` daje `configured()` = true. `refuse` daje `InvalidSettingsException`, którego komunikat zawiera nazwę zmiennej, a nie zawiera adresu ani jego części: hosta, ścieżki ani 8 pierwszych znaków ostatniego segmentu. Testy obcinania białych znaków, końcowej `/`, `toString` i `readsOnlyItsOwnVariable` zostają. Dodaj sprawdzenie, że tabela ma co najmniej po jednym przypadku każdego rodzaju i że brak pliku daje czytelny błąd, a nie zero przypadków.

**Contract**: JUnit 5 `@ParameterizedTest` z `@MethodSource` (albo `@TestFactory`), wzorem `test/settings/DurationSettingTest.java`. Bez Springa i bez Dockera (`*Test`).

### Success Criteria:

#### Automated Verification:

- `cd szczur-radar-api && ./mvnw -B test -Dtest=AlertSettingsTest` przechodzi, a raport pokazuje po jednym wywołaniu na wiersz tabeli
- `cd szczur-radar-api && ./mvnw -B test` przechodzi
- `grep -c 'PING_PATTERN = "' szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/AlertSettings.java` = 1, a wzorzec jest w jednej linii
- Kontrolne zepsucie: zmiana `[Hh]` na `h` w `PING_PATTERN` daje czerwony `AlertSettingsTest` na wierszu `HTTPS://…` (zmiana cofnięta)

**Implementation Note**: Po zielonej weryfikacji automatycznej przejdź do fazy 2. Ta faza nie ma kontroli ręcznych.

---

## Phase 2: Kopie w shellu i strażnik zgodności

### Overview

Oba crony i `checks.sh` dostają literał z fazy 1. Krok testu w `healthchecks-test` pilnuje, żeby żadna z czterech kopii się nie rozjechała, a testy cronów sprawdzają regułę w prawdziwych obrazach.

### Changes Required:

#### 1. Blok w obu cronach

**File**: `tools/db-backup/backup.sh`, `tools/pipeline-trigger/trigger.sh`

**Intent**: Kod od `readonly ping_pattern=` do `readonly ping_url` trafia między znaczniki `# ping-url rule (S-25): begin` i `# ping-url rule (S-25): end`, bajt w bajt taki sam w obu plikach, z nowym literałem. Ostrzeżenie mówi, czego brakuje, zgodnie z regułą (np. `HEALTHCHECK_PING_URL is not an https:// address with a host name and without ? or #; no check-in`) i nadal nie zawiera wartości. Komentarz nad blokiem zamiast „change both” wskazuje strażnika: krok 9 `tools/healthchecks/test.sh` porównuje ten blok z drugim cronem, a wzorzec z `AlertSettings.PING_PATTERN` i `checks.sh`.

**Contract**: Kolejność w bloku bez zmian (obcięcie białych znaków, odcięcie jednej `/`, `unset HEALTHCHECK_PING_URL`, dopasowanie, `readonly ping_url`). Blok używa tylko `log` spoza siebie, a krok 9 tego wymaga. `check_in`, `last_line` i reszta skryptów bez zmian.

#### 2. Wzorzec w `checks.sh`

**File**: `tools/healthchecks/checks.sh`

**Intent**: `ping_pattern` dostaje ten sam literał, więc `apply` odmawia (`fail`, kod 1) uzbrojenia i wydrukowania adresu, którego API i crony by nie przyjęły. Komentarz przy stałej wskazuje regułę i strażnika.

**Contract**: Linia `readonly ping_pattern='…'`. `url_pattern` i `uuid_pattern` bez zmian. Fikstury `post-*-created.json` i `get-checks.json` mają adresy `https://hc-ping.com/<uuid>`, więc przypadki 1–8 nie powinny się zmienić.

#### 3. Krok 9 w `tools/healthchecks/test.sh`

**File**: `tools/healthchecks/test.sh` (i lista przypadków w jego nagłówku)

**Intent**: Nowy krok „9. the ping URL rule: one pattern, two identical cron blocks, one case table”, który:
(a) wycina blok między znacznikami z `backup.sh` i `trigger.sh` i porównuje je bajt w bajt (przy różnicy pokazuje `diff`);
(b) wyciąga literał z linii `readonly ping_pattern=` w bloku, w `checks.sh` i z linii `PING_PATTERN = "` w `AlertSettings.java` i wymaga, żeby były równe;
(c) dla każdego wiersza `ping-urls.txt` uruchamia wycięty blok w podpowłoce z `HEALTHCHECK_PING_URL=<adres>` i atrapą `log`, a wynik (`ping_url` pusty = odmowa) porównuje z werdyktem; przy niezgodności nazywa wiersz;
(d) sprawdza, że znaczniki występują dokładnie raz w każdym cronie, a tabela nie jest pusta.

**Contract**: Ścieżki względem `$here/../..` (korzeń repo), bez Dockera i bez sieci, wzorem `step`/`pass`/`die` z tego pliku. Podpowłoka dostaje blok przez `source <(…)` albo `eval` na wyciętym tekście. W README narzędzia lista testów dostaje punkt 9.

#### 4. Przypadki w obrazach cronów

**File**: `tools/db-backup/test.sh`, `tools/pipeline-trigger/test.sh` (i listy testów w ich README)

**Intent**: Reguła sprawdzona w bashu obrazów, które naprawdę biegną na Railway (Debian z glibc i Alpine z musl). db-backup (P5): przypadki `ping-not-https`, `ping-query` i `ping-no-host` (`https:///<klucz>`), każdy bez `PGHOST`, kończą się `PGHOST is not set` z ostrzeżeniem o adresie i bez meldunku. `run1` melduje się pod adresem z `HTTPS://`, co pokazuje przyjęcie wielkich liter. Trigger: `ping-ok` (albo nowy przypadek) z `HTTPS://`, nowy `ping-no-host`, a oczekiwany tekst ostrzeżenia w `ping-not-https` i `ping-query` zmienia się na nowy.

**Contract**: Istniejące helpery (`run_backup`, `pings_were`, `check_ping_case`, `log_says`). Żadnych nazwanych kontenerów zostawionych po teście (runnery self-hosted).

### Success Criteria:

#### Automated Verification:

- `tools/healthchecks/test.sh` przechodzi, a krok 9 wypisuje liczbę sprawdzonych wierszy tabeli
- `tools/pipeline-trigger/test.sh` przechodzi (Docker)
- `tools/db-backup/test.sh` przechodzi (Docker)
- Kontrolne zepsucie: (a) jeden znak zmieniony w bloku `trigger.sh`, (b) inny literał w `checks.sh`, (c) inny literał w `AlertSettings.java`: każde osobno daje czerwony krok 9 z nazwą rozjazdu (zmiany cofnięte)
- `grep -rn 'change both' tools/db-backup/backup.sh tools/pipeline-trigger/trigger.sh` nie znajduje nic

**Implementation Note**: Po zielonej weryfikacji automatycznej przejdź do fazy 3. Ta faza nie ma kontroli ręcznych.

---

## Phase 3: Limit czasu jako `1` i dokumenty operatora

### Overview

db-backup zgłasza przekroczony limit czasu tak jak trigger. Dokumenty operatora opisują regułę i kod, a PR przechodzi CI.

### Changes Required:

#### 1. Kod po limicie czasu w `backup.sh`

**File**: `tools/db-backup/backup.sh`, `tools/db-backup/test.sh`

**Intent**: Po `124` albo `137` z `timeout` proces pilnujący dopisuje linię limitu (jak dziś) i ustawia `status=1`, więc meldunek i kod wyjścia to `1`, tak jak w `trigger.sh:101-106`. `run4` oczekuje `start 1` i niezerowego kodu wyjścia. Komentarz w nagłówku testu (`test.sh:29`) mówi `/1`.

**Contract**: Kontrakt crona: każda porażka kończy się kodem niezerowym (bez zmian), a limit czasu kodem `1`. Railway traktuje każdy kod niezerowy tak samo (`CRASHED`).

#### 2. README narzędzi

**File**: `tools/db-backup/README.md`, `tools/pipeline-trigger/README.md`, `tools/healthchecks/README.md`

**Intent**: db-backup `:24` mówi, że limit czasu to `1` (zamiast `124`/`137`). Opis reguły adresu w db-backup `:25` i pipeline-trigger `:24` mówi to co komunikat `RULE` (https w dowolnej wielkości liter, nazwa hosta, opcjonalny port, bez `?` i `#`) i nowy tekst ostrzeżenia. Zamiast „zmiana dotyczy obu” wskazuje strażnika (krok 9 `tools/healthchecks/test.sh`). Listy testów (`db-backup :103`, `pipeline-trigger :126`) dostają nowe przypadki. healthchecks README: `apply` odmawia adresu spoza reguły, a sekcja testu ma punkt 9 i tabelę `fixtures/ping-urls.txt`.

**Contract**: Język i styl istniejących README (polski, bez nowych sekcji poza punktami list).

#### 3. Wytyczne repo i runbook

**File**: `REPOSITORY_GUIDELINES.md`, `context/deployment/deploy-plan.md`

**Intent**: Wpis `tools/healthchecks/` w `REPOSITORY_GUIDELINES.md` mówi, że jego `test.sh` pilnuje też jednej reguły adresu check-in (literał w `AlertSettings`, obu cronach i `checks.sh` oraz tabela `fixtures/ping-urls.txt`). W `deploy-plan.md` powstaje nowa sekcja `## S-25: jedna reguła adresu check-in alarmów (alert-ping-single-rule)`: reguła, kod `1` po limicie czasu, brak działań operatora przy wdrożeniu, co zrobić, gdy API nie wstaje z `Invalid HEALTHCHECK_PING_URL` (adres z `checks.sh apply <env>`), i status zmiany. Przy zdaniu F-04 o `124`/`137` (`deploy-plan.md:1204`) pojawia się dopisek `> Od S-25: …` wskazujący nową sekcję. Treści F-04 nie przepisujemy.

**Contract**: Sekcje `deploy-plan.md` dopisywane jak przy poprzednich slice'ach, a status zgodny z lekcją „Status zmiany w deploy-plan.md ma być prawdziwy także po merge'u”.

### Success Criteria:

#### Automated Verification:

- `tools/db-backup/test.sh` przechodzi, a `run4` melduje `start 1`
- `grep -rn '124' tools/db-backup/README.md` nie znajduje opisu kodu meldunku po limicie czasu
- `cd szczur-radar-api && ./mvnw -B verify` przechodzi
- CI na PR zielone (`gh pr checks`), w tym `healthchecks-test`, `db-backup-test`, `pipeline-trigger-test`, backend `verify` i `railway-config`

#### Manual Verification:

- Po merge'u (robi go człowiek): run monitora `szczur-radar-monitor` dla `szczur-radar-api` na produkcji jest zielony, czyli API wstało z zapieczętowanym `HEALTHCHECK_PING_URL`. Agent może zebrać dowód (`gh run list -R kwmacioszek/szczur-radar-monitor`) i logi startu (bez `Invalid HEALTHCHECK_PING_URL`).
- Po pierwszej nocy po merge'u: checki `production/pipeline-trigger` i `production/db-backup` są „up”, a logi obu cronów na Railway nie mają `HEALTHCHECK_PING_URL is not an https:// address`. Agent może zebrać logi, a operator potwierdza brak alarmu w Telegramie.

**Implementation Note**: Po zielonym CI zatrzymaj się i przekaż PR do merge'u. Kontrole ręczne są po merge'u; issue #119 zamykamy dopiero po nich.

---

## Testing Strategy

### Unit Tests:

- `AlertSettingsTest` na całej tabeli: przyjęcia, odmowy bez powtórzenia wartości, kształt produkcyjny.
- Krok 9 `tools/healthchecks/test.sh`: bajtowa równość bloków, równość czterech literałów, tabela przez blok bashowy.

### Integration Tests:

- Testy cronów w prawdziwych obrazach: wielkie litery przyjęte (`run1`, `ping-ok`), odmowy z ostrzeżeniem i bez meldunku (`ping-not-https`, `ping-query`, `ping-no-host`), limit czasu `start 1` w obu.

### Manual Testing Steps:

1. Po merge'u: zielony run monitora dla API na produkcji.
2. Po pierwszej nocy: oba checki cronów „up”, bez ostrzeżenia o adresie w logach.

## Performance Considerations

Brak: jedno dopasowanie wzorca przy starcie API i przy starcie crona.

## Migration Notes

Bez zmian zmiennych i IaC. Jedyne ryzyko wdrożenia to wartość produkcyjna, której nowa reguła by nie przyjęła. API by wtedy nie wstało, a crony przestałyby się meldować z ostrzeżeniem. Wartości pochodzą z `checks.sh apply`, czyli mają kształt `https://hc-ping.com/<uuid>`, który jest w tabeli. Jeśli API nie wstanie: `checks.sh apply production` drukuje adresy ponownie, a operator ustawia je przez `--stdin` (runbook F-04). Wycofanie to revert commita (fix-forward na `main`).

## References

- Audyt po M-2: `context/changes/post-m2-audit/research.md:105-106` (P4, P5), `:193` (pytanie 6)
- Destylacja: `context/domain/domain-distillation.md:182` (R-27), `:237` (#7), `:256` (Q-09)
- Kod: `api/alerts/AlertSettings.java:47-61`; `tools/db-backup/backup.sh:41-101`; `tools/pipeline-trigger/trigger.sh:45-107`; `tools/healthchecks/checks.sh:51,219-239`
- Testy: `test/alerts/AlertSettingsTest.java:21-56`; `tools/pipeline-trigger/test.sh:400-463`; `tools/db-backup/test.sh:274-278,460-467`; `tools/healthchecks/test.sh`
- Runbook: `context/deployment/deploy-plan.md` (F-04 `:1194`, `:1204`; S-18 `:1674`)
- Bramka: `.railway/railway.ts:228,275`; `tools/deploy-gate/gate.jq`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Reguła i tabela przypadków w API

#### Automated

- [x] 1.1 `cd szczur-radar-api && ./mvnw -B test -Dtest=AlertSettingsTest` przechodzi, a raport pokazuje po jednym wywołaniu na wiersz tabeli — f9d920c
- [x] 1.2 `cd szczur-radar-api && ./mvnw -B test` przechodzi — f9d920c
- [x] 1.3 `grep -c 'PING_PATTERN = "' szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/AlertSettings.java` = 1, a wzorzec jest w jednej linii — f9d920c
- [x] 1.4 Kontrolne zepsucie: zmiana `[Hh]` na `h` w `PING_PATTERN` daje czerwony `AlertSettingsTest` na wierszu `HTTPS://…` (zmiana cofnięta) — f9d920c

### Phase 2: Kopie w shellu i strażnik zgodności

#### Automated

- [x] 2.1 `tools/healthchecks/test.sh` przechodzi, a krok 9 wypisuje liczbę sprawdzonych wierszy tabeli — 704747b
- [x] 2.2 `tools/pipeline-trigger/test.sh` przechodzi (Docker) — 704747b
- [x] 2.3 `tools/db-backup/test.sh` przechodzi (Docker) — 704747b
- [x] 2.4 Kontrolne zepsucie: (a) jeden znak zmieniony w bloku `trigger.sh`, (b) inny literał w `checks.sh`, (c) inny literał w `AlertSettings.java`: każde osobno daje czerwony krok 9 z nazwą rozjazdu (zmiany cofnięte) — 704747b
- [x] 2.5 `grep -rn 'change both' tools/db-backup/backup.sh tools/pipeline-trigger/trigger.sh` nie znajduje nic — 704747b

### Phase 3: Limit czasu jako `1` i dokumenty operatora

#### Automated

- [x] 3.1 `tools/db-backup/test.sh` przechodzi, a `run4` melduje `start 1` — 7a3a68b
- [x] 3.2 `grep -rn '124' tools/db-backup/README.md` nie znajduje opisu kodu meldunku po limicie czasu — 7a3a68b
- [x] 3.3 `cd szczur-radar-api && ./mvnw -B verify` przechodzi — 7a3a68b
- [x] 3.4 CI na PR zielone (`gh pr checks`), w tym `healthchecks-test`, `db-backup-test`, `pipeline-trigger-test`, backend `verify` i `railway-config` — 7a3a68b

#### Manual

- [x] 3.5 Po merge'u (robi go człowiek): run monitora `szczur-radar-monitor` dla `szczur-radar-api` na produkcji jest zielony, czyli API wstało z zapieczętowanym `HEALTHCHECK_PING_URL` — 9e47c87
- [x] 3.6 Po pierwszej nocy po merge'u: checki `production/pipeline-trigger` i `production/db-backup` są „up”, a logi obu cronów na Railway nie mają `HEALTHCHECK_PING_URL is not an https:// address` — 9e47c87
