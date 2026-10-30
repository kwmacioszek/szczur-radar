<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-27 reddit-source — Reddit jako drugie źródło przez Arctic Shift

- **Plan**: context/changes/reddit-source/plan.md
- **Scope**: Full plan — completed phases only (1–4 of 5)
- **Reviewed phases**: 1, 2, 3, 4
- **Date**: 2026-10-30
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 2 observations
- **Triage**: COMPLETE — F1, F2, F3 FIXED; 0 pending

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Nie znaleziono krytycznych problemów ani brakującej funkcjonalności faz 1–4. Pierwotny werdykt NEEDS ATTENTION i FAIL w Success Criteria dotyczyły niedziałającego polecenia wyszukującego asercje (F1), a nie porażki testu zachowania aplikacji. Po naprawieniu F1–F3 podczas triage werdykt zmieniono na APPROVED dla niezmienionego zakresu faz 1–4. Szczegóły ustaleń poniżej opisują stan sprzed napraw; pola Decision i podsumowanie triage dokumentują rozwiązania.

## Findings

### F1 — Odhaczona kontrola 4.5 nie znajduje istniejących asercji

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/reddit-source/plan.md:496
- **Detail**: Dosłowne wykonanie polecenia z kryterium 4.5 kończy się kodem 1 i pustym wyjściem: wzorzec regex zawiera nadmiarowe backslashe. Progress oznacza kontrolę jako wykonaną, a opis zapowiada odnalezienie obu plików. Asercje rzeczywiście istnieją w PipelineRunControllerIT.java:159 i PipelineRunWithoutSourceIT.java:80; wyszukanie tego samego tekstu przez `rg -F` znajduje oba miejsca. Jest to błąd odtwarzalności dowodu weryfikacji, nie brak rejestracji Reddita.
- **Fix**: Popraw polecenie w opisowych kryteriach fazy 4 na wyszukiwanie literalne i dopisz sprostowanie wyniku 4.5 w Implementation Note, zachowując niezmienny historyczny tytuł Progress.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw teraz”. Poprawiono opisowe kryterium fazy 4 na wyszukiwanie literalne i dopisano Implementation Note prostującą wynik 4.5. Ponowne wykonanie polecenia: exit 0, oba pliki znalezione. Sekcja Progress identyczna z wersją sprzed poprawki; `git diff --check` bez błędów. Zapis wykonania: follow-ups/review-fixes.md.

Poprawione polecenie:

```bash
rg -n -F '\"source\":\"reddit\",\"configured\":false' szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunControllerIT.java szczur-radar-api/src/test/java/pl/szczurradar/api/pipeline/api/PipelineRunWithoutSourceIT.java
```

### F2 — Opis fixture’a przeczy udokumentowanej zmianie selftext

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/test/resources/fixtures/reddit/README.md:55
- **Detail**: Faza 2 wymagała zachowania treści posta. README:31–33 uczciwie dokumentuje ręczne zastąpienie wzmianki o profilu w selftext posta 1xh5lkk aliasem uzytkownik15. Jednocześnie README:42–43, 52–57 oraz komentarz skryptu twierdzą, że selftext pozostał identyczny z nagraniem dla każdego posta. Podane polecenie jq nie odtwarza tej dodatkowej operacji. Anonimizacja nie osłabia testowanego mapowania, ale twierdzenia o pochodzeniu fixture’a są wzajemnie sprzeczne.
- **Fix**: Wymień tę jedną zmianę jako wyjątek od zachowania selftext i opisz odtwarzalny dodatkowy krok anonimizacji; pozostaw zanonimizowany fixture.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw teraz”. README wskazuje wyjątek selftext posta 1xh5lkk, prostuje deklaracje identyczności i zawiera dodatkowy filtr anonymize-mention.jq odtwarzający ręczną anonimizację. Uruchomienie obu opisanych filtrów zachowuje oba istniejące fixture’y bez zmian. Syntetyczna wzmianka o profilu zostaje zastąpiona w etykiecie i URL-u; wszystkie pozostałe pola pozostają identyczne. `git diff --check` bez błędów. Zapis wykonania: follow-ups/review-fixes.md.

### F3 — Skrajny niepoprawny czas omija obsługę niemapowalnego elementu

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/sources/reddit/RedditMapper.java:66
- **Detail**: Repozytorium akceptuje nieujemny Long jako created_utc, ale Instant.ofEpochSecond / OffsetDateTime.ofInstant mogą zgłosić DateTimeException. Fetcher przechwytuje tylko UnmappableItemException. W sondzie zmieniono wyłącznie ostatni timestamp prawdziwego 14-postowego fixture’a na Long.MAX_VALUE: repozytorium zwróciło 14 elementów i skipped=0, po czym fetcher zakończył się DateTimeException zamiast zwrócić 13 postów i skipped=1. Całe pobranie Reddita zostaje wtedy odrzucone. To syntetyczny przypadek odporności na błędne dane; nie występuje w fixture’ach i nie zaobserwowano go na żywym archiwum. Prawidłowe znaczniki czasu, braki, typy podstawowe i wartości ujemne nie wykazały tego problemu.
- **Fix**: Zamień DateTimeException przy konwersji czasu na UnmappableItemException i dodaj wąski przypadek regresyjny potwierdzający zachowanie pozostałych postów.
- **Decision**: FIXED — 2026-10-30, na polecenie „napraw teraz”. RedditMapper.publishedAt zamienia DateTimeException z konwersji Instant/OffsetDateTime na UnmappableItemException. Parametryzowany test fetchera sprawdza Instant.MAX.getEpochSecond() i Long.MAX_VALUE: fetched=14, skipped=1, dokładnie 13 pozostałych id. Test przed poprawką: 2 błędy DateTimeException; po poprawce cały zestaw Reddit*Test: 70 testów, 0 failures, 0 errors, 0 skipped. `git diff --check` bez błędów. Zapis wykonania: follow-ups/review-fixes.md.

## Scope and evidence

- Kanoniczny Progress: 33/34 kroków ukończonych; bieżąca faza 5. Zgodnie ze skillem zakres obejmuje tylko fazy w pełni ukończone: 1, 2, 3, 4.
- Faza 5 nie jest pokryta tym raportem: 5.9 pozostaje nieodhaczoną oceną PRD/runbooka i decyzją operatora. Nie oznaczono jej jako pozornego odhaczenia. Skan CI/IaC/dokumentów z tej fazy był pomocniczy i nie stanowi pełnego przeglądu ani ponownej weryfikacji PR env. Ten raport nie daje pokrycia wszystkich pięciu faz dla archiwizacji.
- Zakres Git: `5a013ca..e874021` na `feature/reddit-source`, osiem commitów zmiany; stan roboczy przed przeglądem czysty. Zamiast daty nieobecnej w nazwie planu użyto jednoznacznych commitów reddit-source i ich rodzica. Cały diff: 78 plików, 8840 dodanych i 564 usuniętych linii, w dużej części fixture’y i dokumentacja.
- Dwa niezależne przeglądy: zgodność z Changes Required faz 1–4 oraz bezpieczeństwo/jakość/wzorce dla zmienionych plików. Porównano istniejący stos Wykopu i strażników schematu z nowym stosem Reddita. Priorytety lessons.md: granice źródeł, RAW osobno, jedna ograniczona pętla ponowień i termin poniżej watchdoga, brak nieuzasadnionego rozszerzania zakresu.
- Oczekiwane zmiany: HttpRetry i adapter Wykopu; wspólny wyjątek; V13 i kanał w modelu/zapisie; Arctic Shift, stronicowanie, mapowanie i konfiguracja; LocalReddit i testy silnika. Nie znaleziono MISSING ani istotnego EXTRA.
- Dodatkowe ścieżki mają uzasadnienie: Source.java aktualizuje javadoc, ClosedSetsIT dopuszcza CHECK otwartego zbioru kanałów, V13BackfillIT izoluje historyczny backfill, WykopGoldSampler aktualizuje import przeniesionego wyjątku, pozostałe konstruktory testowe przekazują nowy kanał. Pliki CI/IaC, roadmapy i dokumentów są zaplanowane przez fazę 5 / start fazy 1, więc nie są rozrostem zakresu.
- Bramka F1: PipelineRunIT:779–827 sprawdza ocenę każdego posta Reddita względem produkcyjnego RedditMapper i rzeczywistego fixture’a. Scenariusz :834 sprawdza wyłączenie zdjętych postów z liczników, zapisu i powodu SKIPPED. Scenariusz :311 powtarza zakres obu źródeł bez nowych postów. Kanały obu źródeł mają osobne asercje.

### Uzasadnione różnice wykonania

- WykopApiClientTest jest bajtowo niezmieniony w commicie kończącym fazę 1 (`git diff --stat 5a013ca 11aa2cd -- <plik>` daje puste wyjście). W fazie 2 wydzielenie LocalApiServer zmieniło wyłącznie dwa importy Request/Reply, bez zmiany testów i asercji. Dlatego pierwotne polecenie 1.2 na aktualnym HEAD wypisuje `2 insertions(+), 2 deletions(-)`. Nie jest to osłabienie pinu zachowania refaktoru.
- Kryterium 3.2 na HEAD zwraca 1, ponieważ faza 4 zgodnie z planem dodaje @Component. `git grep -n '@Component' afe6103 -- <sources/reddit>` nie znajduje adnotacji w commicie kończącym fazę 3. To oczekiwana kolejność faz, a nie usterka.
- LocalReddit używa testowego timeoutu 60 s zamiast planowanych 5 s; komentarz wyjaśnia, że test zatrzymania ma być rozstrzygany przez przerwanie pracy, nie przez timeout klienta. Produkcyjny timeout pozostaje 30 s.

## Verification

Wspólne polecenia powtarzające się w kilku fazach uruchomiono jednokrotnie dla aktualnego HEAD i przypisano do wszystkich odpowiadających im kryteriów. Faza 5 nie była ponownie wykonywana.

### Testy i komendy

| Kryteria | Polecenie / kontrola | Rzeczywisty wynik |
|----------|---------------------|------------------|
| 1.1 | `cd szczur-radar-api && ./mvnw -B test -Dtest='WykopApiClientTest,WykopRepositoryTest,WykopFetcherTest,WykopMapperTest,WykopConfigurationTest,HttpRetryTest'` | exit 0; 75 tests, 0 failures, 0 errors, 0 skipped |
| 1.2 | `git diff --stat origin/main -- szczur-radar-api/src/test/java/pl/szczurradar/api/sources/wykop/WykopApiClientTest.java` | HEAD: 2 insertions, 2 deletions — tylko importy; pin fazy 1 sprawdzony także dla `5a013ca..11aa2cd`: pusty diff |
| 1.3 | `rg -n 'for \(int attempt' szczur-radar-api/src/main/java/pl/szczurradar/api/sources/` | Jedyny wynik: transport/HttpRetry.java:115 |
| 1.4 | `test -f` wspólnego wyjątku i `! test -e` starej ścieżki | Plik istnieje tylko w sources/ |
| 1.5, 2.5, 3.4 | `cd szczur-radar-api && ./mvnw -B test` | exit 0; BUILD SUCCESS; 980 tests, 0 failures, 0 errors, 0 skipped |
| 1.6 | `rg -n 'channel'` dla V13, SourcePost, Post i PostColumnsIT | Znajduje wszystkie cztery pliki |
| 1.7, 4.1 | `cd szczur-radar-api && ./mvnw -B verify`, z lokalnym llm.env | exit 0; BUILD SUCCESS; 980 unit + 311 integration tests, 0 failures, 0 errors, 0 skipped; 02:29 min |
| 1.8 | `tools/db-backup/test.sh` | exit 0; All checks passed; 13 migracji/Flyway V13, dump/restore, odrzucenie uszkodzonej kopii i brak sekretów w logach |
| 2.1 | `cd szczur-radar-api && ./mvnw -B test -Dtest='RedditApiClientTest,RedditRepositoryTest,HttpRetryTest,Wykop*Test'` | exit 0; 109 tests, 0 failures, 0 errors, 0 skipped |
| 2.2 | Pętla jq i test autorów z planu dla search-posts-*.json | exit 0; obie odpowiedzi mają ≥10 postów; author pasuje do aliasu albo [deleted] |
| 2.3 | Dwa `jq -e` sprawdzające removed_by_category w stocks | `true`, `true`; exit 0 |
| 2.4 | `rg -n 'aFetchEndsBeforeTheWatchdogsShortestDefault'` | RedditRepositoryTest.java:304 |
| 3.1 | `cd szczur-radar-api && ./mvnw -B test -Dtest='Reddit*Test'` | exit 0; 68 tests, 0 failures, 0 errors, 0 skipped |
| 3.2 | `! rg -n '@Component'` dla sources/reddit/ | HEAD: exit 1, zgodnie z późniejszą fazą 4; na afe6103 brak adnotacji — zob. wyjaśnienie powyżej |
| 3.3 | `cd szczur-radar-api && ./mvnw -B test -Dtest='RedditPostsTest'` | exit 0; 4 tests, 0 failures, 0 errors, 0 skipped |
| 3.5 | `rg` dla subreddit() i channel w RedditPosts/Test | RedditPosts:50; test:68,78, z równością channel/channels[0]/subreddit |
| 4.2 | `! rg -n 'ScriptedSourceConfiguration' szczur-radar-api/src REPOSITORY_GUIDELINES.md` | exit 0, bez wyników |
| 4.3 | `rg` dla bramki F1 i RedditMapper.map | PipelineRunIT:779,821 |
| 4.4 | `rg` dla removedRedditPostsAreNeitherFetchedNorSkipped | PipelineRunIT:834 |
| 4.5 | Dosłowne polecenie regex z planu | Pierwotnie exit 1, puste wyjście — F1; po naprawie w triage polecenie z opisowego kryterium używa `rg -n -F`, znajduje oba pliki i kończy się exit 0 |
| 4.6 | `rg -n 'LocalReddit'` w obu regułach pakietów | Znajduje sources/CLAUDE.md i pipeline/CLAUDE.md |
| 4.7 | `rg -n 'channel'` w PipelineRunIT | Asercje gielda:750–751 oraz inwestowanie:808–823 |

Pierwsza próba Maven wewnątrz sandboxa zakończyła się błędami lokalnych gniazd HTTP/Mockito; pierwsza próba backupu nie miała dostępu do Dockera. Ponowiono poza sandboxem. Pierwsze niesandboxowane verify miało zielone 980 testów jednostkowych, ale Spring odrzucił brak LLM_API_PROVIDER/MODEL/KEY. Pełne verify ponowiono z istniejącym lokalnym plikiem ustawień (bez wypisywania sekretów) i zakończyło się sukcesem. Są to rozpoznane warunki środowiska; nie są zgłoszone jako regresje implementacji.

Logi tego przeglądu (lokalne, tymczasowe): `/private/tmp/reddit-source-impl-review-verify-configured.log`, `reddit-source-impl-review-unit.log`, `reddit-source-impl-review-p1.log`, `reddit-source-impl-review-p2.log`, `reddit-source-impl-review-p3.log`, `reddit-source-impl-review-p3-raw.log`, `reddit-source-impl-review-backup.log` (wszystkie w /private/tmp). Kluczowe wyniki zachowano w tabeli, aby raport nie zależał od trwałości tych logów.

### Dodatkowa sonda F3

Uruchomiona przed naprawą na skompilowanych klasach produkcyjnych, bez edycji repozytorium i bez połączenia z archiwum: fixture search-posts-inwestowanie.json → ostatni created_utc = Long.MAX_VALUE → RedditApiClient.get zastąpione zwrotem tej tablicy → produkcyjny RedditRepository i RedditFetcher. Wyjście:

```text
Repository elements=14, skipped=0
Fetcher failed: java.time.DateTimeException: Instant exceeds minimum or maximum instant
```

Źródło sondy: `/private/tmp/reddit-source-review/pl/szczurradar/api/sources/reddit/RedditMalformedProbe.java`. Nie jest to niepowodzenie bramki testowej ani dowód błędnych danych w obecnym archiwum.

### Manual

Fazy 1–4 nie mają wierszy Manual. Jedyny oczekujący wiersz 5.9 wymaga człowieka i pozostaje poza zakresem. Progress pozostał bez zmian. Na polecenie użytkownika triage zmieniło opisowe kryterium planu, README fixture’ów, RedditMapper i test fetchera.

## Triage complete

- **Fixed**: F1, F2, F3 (3)
- **Rule**: none (0)
- **Skipped**: none (0)
- **Accepted**: none (0)
- **Pending**: none (0)
- **Coverage**: nadal wyłącznie fazy 1, 2, 3, 4; faza 5 wymaga osobnego pokrycia po zakończeniu.

Weryfikacja poprawek: F1 — poprawione polecenie znajduje oba pliki, exit 0, sekcja Progress bez zmian; F2 — oba filtry z README wykonano na obu fixture’ach oraz na syntetycznej wzmiance, zgodność danych potwierdzona; F3 — nowy test przed poprawką odtworzył oba błędy DateTimeException, po poprawce `cd szczur-radar-api && ./mvnw -B test -Dtest='Reddit*Test'` kończy się BUILD SUCCESS (70 testów, bez błędów i pominięć). Logi F3: `/private/tmp/reddit-source-f3-red.log` i `/private/tmp/reddit-source-f3-green.log`. Pełne verify (980 unit + 311 IT) z tabeli było wykonane przed triage; po wąskiej poprawce mappera uruchomiono zestaw testów Reddita. `git diff --check` bez błędów.
