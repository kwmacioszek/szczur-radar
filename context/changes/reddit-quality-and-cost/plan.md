# S-28 reddit-quality-and-cost — Obraz konfiguracji produkcyjnej na Reddicie przed włączeniem — Implementation Plan

## Overview

Nikt nie widział, co konfiguracja produkcyjna (`openai/gpt-6-luna`, prompt v2.5) robi z prawdziwym postem z Reddita, a decyzja o włączeniu potrzebuje zgrubnego obrazu tego zachowania, nie rozstrzygnięcia progów 70 % / 10 % (`frame.md`). Plan dokłada narzędzia wyłącznie testowe w `measurement/`, puszcza przez produkcyjny `Assessor` około 550 postów z 7 dni, dodaje godzinę oceny właściciela nakierowaną na pominięte cele i kończy się raportem oraz zapisaną decyzją o włączeniu. Kod produkcyjny, prompt, kontrakt, `models.json` i złota próbka nie zmieniają się.

Decyzje właściciela z wywiadu `/10x-plan` (2026-10-30): pominięty cel to spółka lub aktywo, o których autor coś twierdzi (sama lista tickerów i wzmianka w tle się nie liczą); arkusz z podpowiedzią modelu; mierzone subreddity to `stocks`, `ValueInvesting`, `investing`; agent sam wykonuje przebieg na `~/.config/szczur-radar/openrouter.env` i odczyt tylko do odczytu z produkcji.

## Current State Analysis

Z `frame.md` (hipotezy i sygnały właściciela):

- Luna nigdy nie oceniła posta z Reddita: nagrania `luna` to 120 razy `gielda`. Pilot 5 używał kontraktu V1, skróconego promptu, DeepSeeka i Gemini, a PR-132 `deepseek-v4-flash` w trybie JSON. Szczegóły błędów z PR-132 przepadły ze środowiskiem; zostały tylko liczby z `deploy-plan.md:2051`.
- Progu 70 % nie da się rozstrzygnąć w godzinę: 54/74 daje widełki 61,9–81,8 %, a pewny wynik wokół progu wymaga 180–290 postów.
- Odrzucenia rosną z długością posta zależnie od modelu (DeepSeek EN 3 / 15 / 38 %, Gemini 0 % dla limitów). Limity z §11 nie idą do dostawcy (`ProviderSchemas.java:27`), więc model zna je tylko z promptu.
- Język tez nie jest walidowany, a pilot dał ok. 64 % tez po polsku przy angielskich postach. Właściciel uważa tezy po polsku przy poście angielskim za problem.
- Megawątki nie są odfiltrowane (`RedditRepository.java:187`); pilot wyłączył ich 12 z 255.
- Oceny zostają na stałe: nie ma ukrywania, filtra po źródle ani ponownej oceny (`AssessmentSelection.java:64`, `PostFilter.java:18`). Właściciel przyjmuje to jako znośne nawet przy co czwartej błędnej ocenie.
- Koszt: szacunek łączny z Wykopem na `flex` to $2,6–7,2/mies., najgorszy wariant na `default` to $14,4 przy limicie $10 na kluczu. Rzeczywistych wydatków nie ma w repo.

## Desired End State

W folderze zmiany leży raport `report-<data>.md` z danymi źródłowymi (`sample/`), a właściciel zapisał na jego podstawie decyzję o włączeniu Reddita i budżet. Raport pokazuje: odsetek ocen i `dead`, odrzucenia według klasy i długości posta, liczbę celów, język tez, wpływ megawątków, koszt łączny z Wykopem oraz pominięte cele z godziny oceny. `deploy-plan.md` i roadmapa nie sprzeczają się z rolą wyniku, a CI jest zielone bez żadnej zmiany bramki.

Weryfikacja: wiersze `## Progress` na końcu tego planu.

### Key Discoveries:

- `GoldRecorder` jest dobrym wzorcem pętli (`GoldRecorder.java:53-75`), ale `Recording.write` i `readAll` mają zaszyty katalog `fixtures/llm/recordings` (`Recording.java:51,95,108`), a `RecordingsReplayTest` czyta stałe pliki `gold/` (`RecordingsReplayTest.java:28,44`). Próbka Reddita musi żyć poza tymi ścieżkami.
- `ModelsConfig.matching` wymaga zgodnego trybu, a `luna` ma `serviceTier: default` (`ModelsConfig.java:79-83`), ale ma też ceny kanału `openai/flex`. Rejestrator nie musi dopasowywać konfiguracji: bierze ceny z `luna` i zapisuje rzeczywisty kanał i tryb w `Recording`.
- `Replay.judge` jest prywatny (`Replay.java:94`); raport potrzebuje tej samej oceny, więc wystarczy zmiana widoczności na pakietową.
- `Post.MAX_ASSESSMENT_ATTEMPTS = 3` (`Post.java:40`): post staje się `dead` po trzeciej nieudanej próbie, więc rejestrator robi do trzech prób w jednym przebiegu.
- `RedditRepository.postsCreatedBetween` przyjmuje granice włącznie, co do sekundy (`RedditRepository.java:104-112`). Publiczny konstruktor to `RedditRepository(RedditApiClient, List<String>)` (`:87`), a `RedditApiClient()` nie wymaga klucza (`RedditApiClient.java:69`).
- `RedditMapper` nie przekazuje autora ani adresu, ale tekst idzie bez zmian (`RedditMapper.java:24`), więc `u/nick` i URL-e z treści zostają. Repo jest publiczne, a `WykopGoldSampler.Anonymizer` umie zamienić URL-e, e-maile, `@nick` i nicki autorów (`WykopGoldSampler.java:165-243`).
- `labeling-gold.html` wczytuje `posts.json`, trzyma stan w `localStorage` i eksportuje `labels.json` w formacie `GoldLabels.Label`. Archiwum jest niezmienne, więc arkusz Reddita to kopia w folderze zmiany.
- Definicje miar i `AnchorMatcher` nie zależą od źródła (`AnchorMatcher.java:40,58,66`), ale `GoldMetrics.compute` rzuca przy pustym mianowniku (`GoldMetrics.java:260`), więc w małych przedziałach długości raport liczy pary sam, tymi samymi regułami.
- `LLM_SERVICE_TIER`, `LLM_MODEL` i `LLM_API_PROVIDER` są na produkcji zwykłymi ustawieniami, nie sekretami (`deploy-plan.md:264`). Wzorzec odczytu bazy to `railway ssh -s postgres -e production` (`deploy-plan.md:2062`). Ten worktree nie jest podlinkowany do Railway.

## What We're NOT Doing

- Zmian w `src/main`: prompcie, `PROMPT_VERSION`, kontrakcie, limitach, `ProviderSchemas`, prefiltrze i filtrach Reddita. Nowa wersja promptu unieważniłaby 480 nagrań przy zapasie 2 celów na Wykopie. Ewentualna naprawa to osobna zmiana po decyzji właściciela.
- Kolumny `posts.language` ani detekcji języka w produkcie: to osobna potrzeba właściciela; trafia do `## Parked` w roadmapie.
- Rozstrzygania progów 70 % / 10 % i bramki opartej na wyniku: progi pojawiają się w raporcie tylko jako punkt odniesienia.
- Włączenia Reddita na produkcji: runbook S-27 wykonuje operator po tej zmianie.
- Mierzenia innych subredditów, innych modeli ani trybów `reasoning`.
- Dokładania Reddita do złotej próbki, `models.json` i bramki CI (`RecordingsReplayTest`).
- Ponownej oceny lub usuwania postów na produkcji, filtra po źródle (S-29).
- Weryfikacji w PR env: zmiana nie ma kodu produkcyjnego, a PR env nie dostaje klucza produkcyjnego.
- Oznaczania na ślepo i oceny wielu osób: wynik ma być zgrubny.

## Implementation Approach

Mierzymy ścieżkę produkcyjną, nie skrypt: `RedditRepository`, `RedditMapper`, `Prefilter`, `Assessor`, walidatory i klient OpenRouter z `flex`. Skrypt w Pythonie wymagałby odtworzenia walidatorów, a PR env odpada, bo klucz produkcyjny tam nie trafia. Różnica względem produkcji jest jedna i świadoma: anonimizacja treści przed oceną, bo próbka trafia do publicznego repo.

Dane i raport żyją w folderze zmiany (`sample/`), żeby pomiar został dowodem po archiwizacji; utrata szczegółów z PR-132 pokazała, że same agregaty nie wystarczają. Maven działa w `szczur-radar-api/`, więc ścieżki idą przez `-Dreddit.dir=../context/changes/reddit-quality-and-cost/sample`.

Terminy: **dzień** to dzień kalendarzowy czasu warszawskiego; **próba** to jedno wywołanie `Assessor.assess` = jedna linia `recordings.jsonl` (ponowienia transportu wewnątrz tego wywołania to pole `attempts` tej linii, nie próba); **`dead`** to post bez poprawnej oceny po trzech próbach — na produkcji rozłożonych na kolejne noce (RETRY przed RANGE), w pomiarze wykonanych od razu, co raport nazywa w sekcji „oceny” jako przybliżenie; **koszt miesięczny** to 30 × średnia dzienna liczba postów do oceny × średni koszt posta wraz z ponowieniami; **przedziały długości** to `body` < 1000, 1000–2999 i 3000+ znaków; **megawątek** to tytuł pasujący do wzorca pilota `(daily|weekly).*(discussion|thread)|thread on meme stocks` (bez rozróżniania wielkości liter).

Zmianę wykonuje `/10x-implement`, faza po fazie. `/10x-goal-implement` nie pasuje, bo fazy 3 i 4 czekają na człowieka.

## Critical Implementation Details

- **Kolejność w fazie 2:** najpierw odczyt ustawień z produkcji, potem przebieg. Po załadowaniu `openrouter.env` wszystkie trzy opcjonalne ustawienia — `LLM_SERVICE_TIER`, `LLM_REASONING_EFFORT`, `LLM_TIMEOUT` — przyjmują wartości z `production-llm.json` (nieustawione na produkcji = nieustawione w przebiegu), inaczej zmierzymy nie to, co działa: tryb zmienia cenę i powrót z `flex`, `reasoning` zmienia wyjście i tokeny, `timeout` — odsetek niepowodzeń transportu.
- **Sekrety:** `openrouter.env` ładuje się wyłącznie w podpowłoce polecenia (`( source … && ./mvnw … )`), tak jak w `measurement/README.md:207`. Klucz nigdy nie trafia do logu, pliku ani argumentu.
- **Długi przebieg:** rejestrator działa w tle i musi wznawiać się po przerwaniu, bo pojedyncze wywołanie narzędzia ma limit 10 minut, a flex bywa wolny.

## Phase 1: Narzędzia pomiaru

### Overview

Trzy klasy pomiarowe i wspólne obliczenia, testy czystej logiki, dokumentacja. Żadnego wywołania modelu w CI.

### Changes Required:

#### 1. Otwarcie pracy

**File**: `context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: Pierwszy commit niesie folder zmiany. `/10x-implement` przesuwa S-28 na `in-progress`, a dane rysunku idą za roadmapą (reguła repo). Issue #122 dostaje komentarz z change id, gałęzią i linkiem do PR.

**Contract**: Gałąź `feature/reddit-quality-and-cost` sprawdzona względem `origin/main`; PR z `Refs #122`; status w `render_roadmap_svg.py:188` zgodny z roadmapą.

#### 2. Wspólne obliczenia

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/RedditMeasures.java`, `RedditMeasuresTest.java`

**Intent**: Czysta logika, od której zależą liczby w raporcie, oddzielona od wejścia i wyjścia, żeby dało się ją sprawdzić testem.

**Contract**: Funkcje statyczne: przedział Wilsona (z = 1,96), przedział długości, wzorzec megawątku, klasyfikacja języka tezy (polska, gdy ma polski znak diakrytyczny albo co najmniej dwa słowa z krótkiej listy polskich słów funkcyjnych bez angielskiego homografu — np. `że`, `się`, `nie`, `jest`, `oraz`, `ale`, `czy`, `będzie`, `które`, `jednak`; nigdy `a`, `i`, `to`, `on`, `no`, `by`, `ma`; inaczej angielska). `RedditMeasuresTest` jest zwykłym `*Test` w CI i przypina: Wilson 54/74 = 61,9–81,8 %, granice 999/1000 i 2999/3000, przykłady megawątków z pilota, po dwa zdania polskie i angielskie oraz angielską tezę z `a`, `to`, `on`, `no`, `i` (np. „AAPL is going to rally on earnings, no doubt”), która musi wyjść jako angielska.

#### 3. Próbka

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/RedditSampler.java`, `RedditAnonymizerTest.java`

**Intent**: Wzorzec `WykopGoldSampler`, ale bez losowania: zbiera wszystkie posty do oceny z zamkniętych dni, bo wolumen i megawątki są częścią pomiaru. Zapisuje też liczby wejściowe do kosztu.

**Contract**: `@Tag("measurement")`, parametry `-Dreddit.days=<d1>,…` (dni zamknięte), `-Dreddit.dir=<ścieżka>`, `-Dreddit.subreddits` (domyślnie `stocks,ValueInvesting,investing`). Na każdy dzień i subreddit osobny `postsCreatedBetween` od początku dnia warszawskiego do sekundy przed następnym. Anonimizacja: nicki autorów z `RedditItem.author` (bez `[deleted]` i `AutoModerator`), `u/nick` i `/u/nick` sprowadzone do wzmianek `@nick` i zamienione przez `WykopGoldSampler.Anonymizer`, URL-e i e-maile jak tam. Kolejność jak w `WykopGoldSampler.java:101`, bo `Anonymizer.apply` jest wykopowy: `Anonymizer.text` na `title` i `selftext` → kopia `RedditItem` z tymi polami → `RedditMapper.map` (nigdy ręcznie składany `Item`), żeby wejście było polem w pole produkcyjne (`channels = [subreddit]`, `item_type` z `is_self`, `title` i `body` osobno, offset `Z`). Filtr `Prefilter.isAssessable`. Wyjście: `posts.json` przez `GoldLabels.writePosts` (`post_id` = `reddit-<id>`, `Media.NONE`) oraz `volume.json` (dzień, subreddit, pobrane, zdjęte z `RedditSearchResult.removed`, pominięte z `skippedElements`, do oceny). `RedditAnonymizerTest` to zwykły `*Test` na wzór `WykopAnonymizerTest`; przypina też, że `channels` i `item_type` zanonimizowanego wejścia są równe wynikowi `RedditMapper.map` na oryginale.

#### 4. Rejestrator

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/RedditRecorder.java`

**Intent**: Każdy post z `posts.json` przechodzi przez produkcyjny `Assessor` do trzech prób, a każda próba zostaje zapisana, żeby nie stracić szczegółów błędów.

**Contract**: `@Tag("measurement")`, parametry `-Dreddit.dir`, `-Dreddit.concurrency` (domyślnie 4, własny `ExecutorService`), `-Dreddit.limit` (pierwsze N postów), `-Dreddit.maxUsd` (domyślnie 4; oczekiwany koszt całości to ok. $1, a $2 zostaje w raporcie jako wartość odniesienia). Odmawia, gdy dostawca lub model z `LLM_*` to nie `openrouter` i `openai/gpt-6-luna`. Dopisuje po jednej linii `Recording` (`configId` = `luna-<tryb>`) do `recordings.jsonl`, synchronizując zapis: `ContractJson.MAPPER.writeValueAsString(Recording.of(…))` (bez wcięć, jedna linia) i `Files.writeString(…, APPEND)`, bo `Recording.write` jest przyspawane do `fixtures/llm/recordings`. Wznawianie z tego pliku: liczy linie na `post_id` i pomija posty z linią `ok` albo z trzema liniami (nie patrzy na pole `attempts`). `LlmUnavailableException` obsługuje jak `GoldRecorder.assess`. Bezpiecznik: po przekroczeniu `maxUsd` (koszt z tokenów, ceny `luna` dla kanału nagrania) kończy nowe zadania i test pada z komunikatem.

#### 5. Raport

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/RedditReport.java`, `Replay.java`

**Intent**: Raport bez wywołań modelu, czytany przez właściciela. Pada, gdy próbka jest niekompletna, zamiast liczyć z dziur.

**Contract**: `@Tag("measurement")`, parametry `-Dreddit.dir`, `-Dreddit.price=luna`, `-Dreport.out=<ścieżka>`. `Replay.judge` staje się pakietowy, a raport sam czyta linie `recordings.jsonl` (`ContractJson.MAPPER`) i woła `judge` na każdej; `Replay.of` zostaje nietknięte, bo czyta stały katalog nagrań. Sekcje po polsku: próbka i wolumen; oceny (pierwsza próba, po trzech, `dead`, z przedziałem Wilsona); klasy niepowodzeń z rozbiciem po `Violation.rule`; przedziały długości (posty, wynik, mediana celów, tokeny); megawątki z nimi i bez nich; język tez (posty tylko po angielsku, mieszane, tylko po polsku); koszt (tokeny, cena `flex` i `default`, udział powrotu z flex, koszt miesięczny Reddita, Wykopa z `wykop-usage.json` i łączny wobec $10); próg z Wykopu tylko jako odniesienie. Bez ikon zaliczenia. Gdy jest `labels.json`, dochodzi część z fazy 3.

#### 6. Dokumentacja pomiaru

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/README.md`, `CLAUDE.md`

**Intent**: Następny pomiar po zmianie promptu da się powtórzyć tymi samymi poleceniami.

**Contract**: README: sekcja `## Reddit sample` z poleceniami, plikami i zastrzeżeniem o anonimizacji. `CLAUDE.md`: akapit, że pomiar Reddita nie wchodzi do bramki CI.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe zielone, w tym nowe i bramka nagrań: `cd szczur-radar-api && ./mvnw -B test`
- Zmiana nie dotyka produkcji ani danych bramki: `git diff --name-only origin/main...HEAD -- szczur-radar-api/src/main szczur-radar-api/src/test/resources szczur-radar-web .railway .github` jest puste
- Próbny zapis jednego zamkniętego dnia do katalogu tymczasowego przechodzi: `./mvnw -Pmeasurement test -Dtest=RedditSampler -Dreddit.days=<dzień> -Dreddit.dir=<scratch>`, a `posts.json` nie ma adresów ani wzmianek: `grep -cE 'https?://|www\.|(^|[^[:alnum:]])/?u/[A-Za-z0-9_-]{3,}'` zwraca 0
- CI na PR zielone: `gh pr checks`

**Implementation Note**: Po tej fazie agent przechodzi dalej bez czekania na człowieka.

---

## Phase 2: Przebieg na żywo

### Overview

Odczyt produkcji, 7 dni próbki, przebieg na Lunie, automatyczna część raportu. Cały koszt modelu przypada na tę fazę.

### Changes Required:

#### 1. Odczyt z produkcji

**File**: `context/changes/reddit-quality-and-cost/sample/production-llm.json`, `sample/wykop-usage.json`

**Intent**: Tryb do pomiaru i rzeczywisty koszt Wykopa zamiast szacunku. Tylko odczyt i tylko agregaty.

**Contract**: Po `railway link` (projekt `szczur-radar`) pięć zwykłych ustawień, filtrowanych przed wypisaniem:

```bash
railway variables -s szczur-radar-api -e production --json \
  | jq '{LLM_API_PROVIDER, LLM_MODEL, LLM_SERVICE_TIER, LLM_REASONING_EFFORT, LLM_TIMEOUT}'
```

Jeden `SELECT` w transakcji tylko do odczytu, 14 ostatnich zamkniętych dni: dzień, liczba ocen `ok` i nieudanych, sumy `input_tokens`, `cached_tokens`, `output_tokens`, `service_tier`, `flex_fallback`, liczba postów Wykopa z dnia. Polecenie według wzorca `deploy-plan.md:2062`, z `PGOPTIONS='-c default_transaction_read_only=on'`. Żadnych treści postów.

#### 2. Próbka i przebieg

**File**: `context/changes/reddit-quality-and-cost/sample/posts.json`, `sample/volume.json`, `sample/recordings.jsonl`

**Intent**: Siedem zamkniętych dni kończących się na D-2, trzy subreddity, najpierw 5 postów na sucho, potem całość w tle.

**Contract**:

```bash
( source ~/.config/szczur-radar/openrouter.env && cd szczur-radar-api && \
  ./mvnw -Pmeasurement test -Dtest=RedditRecorder \
    -Dreddit.dir=../context/changes/reddit-quality-and-cost/sample )
```

#### 3. Raport automatyczny

**File**: `context/changes/reddit-quality-and-cost/report-<data>.md`

**Intent**: Pierwsza część raportu. Agent czyta 40 losowych tez z klasyfikatorem i zapisuje w raporcie, ile ocenił tak samo.

**Contract**: `RedditReport` z `-Dreport.out`. Sprawdzenie klasyfikatora to krótki akapit w raporcie z liczbą zgodnych.

### Success Criteria:

#### Automated Verification:

- `production-llm.json` ma dostawcę `openrouter` i model `openai/gpt-6-luna`, a `wykop-usage.json` ma 14 dni i żadnego pola z treścią
- `posts.json` obejmuje 7 dni i 3 subreddity, a `grep` z fazy 1 zwraca na nim 0
- Każdy post ma ocenę `ok` albo trzy próby: `RedditReport` kończy się sukcesem
- Wszystkie próby w `recordings.jsonl` mają `promptVersion` v2.5, `contractVersion` V2, model Luny oraz `serviceTier` i `reasoningEffort` z `production-llm.json`: `jq -s` na pliku zwraca jedną kombinację
- Koszt przebiegu w raporcie nie przekracza $4 (oczekiwany ok. $1; $2 to wartość odniesienia w raporcie)
- Raport zawiera wszystkie sekcje z fazy 1 i akapit o klasyfikatorze: `grep -c` nagłówków

**Implementation Note**: Po tej fazie agent przedstawia operatorowi najważniejsze liczby. Jeśli `dead` przekracza 25 % albo koszt łączny przekracza $10, mówi to wprost przed przejściem dalej.

---

## Phase 3: Godzina oceny

### Overview

Arkusz z podpowiedzią, kolejka z większym udziałem długich postów, godzina właściciela, część raportu o pominiętych celach.

### Changes Required:

#### 1. Kolejka

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/RedditSheet.java`, `context/changes/reddit-quality-and-cost/sample/sheet.json`

**Intent**: Zamrożona kolejka 40 postów, dobrana tak, żeby przerwanie po godzinie zostawiało sensowny skład.

**Contract**: `@Tag("measurement")`, parametry `-Dreddit.dir`, `-Dreddit.seed` (domyślnie 20261030). Tylko posty z poprawną oceną, bez megawątków (oba zjawiska mierzy faza 2). Skład 10 / 15 / 15 (3000+ / 1000–2999 / < 1000) albo tyle, ile jest, ułożony przeplotem, zaczynając od najdłuższych. Rekord: pola `GoldLabels.Post` plus `model` (`relevance` i cele z `anchor`, `direction`, `hedged`).

#### 2. Arkusz

**File**: `context/changes/reddit-quality-and-cost/labeling-reddit.html`

**Intent**: Kopia `labeling-gold.html` z archiwum. Różnice: wczytuje `sheet.json`, wypełnia cele i trafność z modelu, podświetla anchory modelu w tekście, ma przycisk „Zatwierdź bez zmian”, a eksport obejmuje tylko posty przejrzane.

**Contract**: Własny klucz `localStorage`. Eksport `labels.json` w dokładnym formacie `GoldLabels.Label`, bo mapper jest ścisły. Na górze instrukcja: przeczytaj cały post; popraw kierunek albo usuń cel, który nie jest podmiotem; dopisz spółkę lub aktywo, o których autor coś twierdzi, a których brakuje; samej listy tickerów i wzmianek w tle nie dopisuj; po godzinie eksportuj.

#### 3. Część raportu z oceny

**File**: `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/RedditReport.java`

**Intent**: Liczby odpowiadające obawie właściciela, ogółem i w przedziałach długości.

**Contract**: Pary przez `AnchorMatcher.matchNames` z aliasami pustymi. Posty oznaczone „trudne” poza mianownikiem. Sekcja podaje: odsetek postów z co najmniej jednym pominiętym celem i liczbę braków na post; cele modelu usunięte przez właściciela; odwrotny kierunek; stanowisko modelu tam, gdzie właściciel go nie widzi. Wszystko z przedziałem Wilsona i uwagą o podpowiedzi, która może zawyżać wynik.

### Success Criteria:

#### Automated Verification:

- `sheet.json` ma do 40 postów, wszystkie z oceną `ok`, bez megawątków, pierwsze trzy to kolejne przedziały od najdłuższego
- Scenariusz `playwright-cli` z zapisanym wynikiem: wczytanie `sheet.json`, widoczne podświetlenia, dopisanie celu, zatwierdzenie bez zmian, eksport
- Eksport z tego scenariusza przechodzi `GoldLabels.readLabels` i `RedditReport` w katalogu tymczasowym
- Po eksporcie właściciela raport zawiera sekcję o pominiętych celach z liczbą przejrzanych postów

#### Manual Verification:

- Operator oznacza posty przez około godzinę i zapisuje `sample/labels.json`

**Implementation Note**: Ostatnie kryterium automatyczne wykonuje agent po zapisaniu `labels.json`. Faza kończy się po nim.

---

## Phase 4: Decyzja i dokumenty

### Overview

Właściciel zapisuje decyzję, a dokumenty przestają się sprzeczać z rolą wyniku.

### Changes Required:

#### 1. Decyzja właściciela

**File**: `context/changes/reddit-quality-and-cost/change.md`

**Intent**: Decyzja i budżet z datą, po przeczytaniu raportu. Decyzja brzmi: włączyć teraz, włączyć po osobnej naprawie albo nie włączać. To tu właściciel rozstrzyga też, czy tezy po polsku są warunkiem.

**Contract**: Wpis w `## Notes`: wariant, budżet (Q-03), powód, lista subredditów. Następstwa (naprawa promptu, filtr megawątków) trafiają jako nowe pozycje roadmapy, nie do tego PR.

#### 2. Deploy-plan

**File**: `context/deployment/deploy-plan.md`

**Intent**: Runbook S-27 nie może wymagać spełnienia progów, skoro wynik jest materiałem do decyzji.

**Contract**: Linia `:2073`: włączenie po przeczytaniu raportu i zapisaniu decyzji operatora w issue #121, bez wymogu progów. Linia `:2094`: porównanie z odsetkiem `dead` z raportu zamiast „what S-28 accepted”. Nowa sekcja `## S-28: Reddit measurement (reddit-quality-and-cost)` z linią `**Status:** delivered by PR #<N>; closure in issue #122.`, wynikiem w kilku zdaniach, decyzją i odnośnikiem do raportu; tekst prawdziwy także po archiwizacji.

#### 3. Roadmapa

**File**: `context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**: S-28 opisuje to, co zmierzono, a język posta wychodzi z tego slice'a.

**Contract**: W S-28 i wierszu `## At a glance` (`:114`) nowy Outcome: obraz konfiguracji produkcyjnej zamiast spełnienia progów; niewiadome Q-03 i Q-04 rozstrzygnięte z datą; wpis o języku usunięty z S-28. S-29 (`:277`) bez warunku „jeśli S-28 go zapisze”. W `## Parked` wpis o `posts.language` z powodem. Pytanie 4 (`:301`) zamknięte decyzją. Następny wpis `> v4.x` w historii wersji. Dane w `render_roadmap_svg.py:188` i regeneracja `python3 tools/render_roadmap_svg.py`.

#### 4. Issue

**File**: brak (GitHub)

**Intent**: Stan w issue zgodny z rzeczywistością.

**Contract**: Komentarze na #122 i #121 z wynikiem, decyzją i linkiem do raportu. Zamknięcie #122 to krok runbooku po merge'u i weryfikacji produkcji (komentarz w issue, poza Progress tego planu); #121 zostaje otwarte do włączenia na produkcji.

### Success Criteria:

#### Automated Verification:

- `deploy-plan.md` nie zawiera już `Only once S-28 has shown` ani `what S-28 accepted`: `grep -c` zwraca 0
- Sekcja S-28 w roadmapie nie wspomina `posts.language`, a `## Parked` tak
- Rysunek jest aktualny: po `python3 tools/render_roadmap_svg.py` `git diff --exit-code docs/visuals/roadmap.svg`
- Komentarze są w issue: `gh issue view 122 --comments` i `gh issue view 121 --comments` zawierają link do raportu
- CI na PR zielone: `gh pr checks`

#### Manual Verification:

- Operator czyta raport i zapisuje decyzję o włączeniu Reddita, budżet i listę subredditów

**Implementation Note**: Dokumenty pisze agent po decyzji operatora. Merge wykonuje wyłącznie człowiek, po `/10x-impl-review` i `/10x-archive` (stała reguła repo, nie kryterium tej zmiany).

---

## Testing Strategy

### Unit Tests:

- `RedditMeasuresTest`: Wilson, granice przedziałów, megawątki, język tez.
- `RedditAnonymizerTest`: `u/nick`, `/u/nick`, adresy, e-maile, nick autora w treści, zwykłe słowa bez zmian.
- Istniejące `RecordingsReplayTest` i `GoldDataTest` bez zmian: dowód, że bramka nie drgnęła.

### Integration Tests:

Brak nowych `*IT`: kod jest tylko testowy. `./mvnw -B verify` i tak biegnie w CI.

### Manual Testing Steps:

1. Otwórz `context/changes/reddit-quality-and-cost/labeling-reddit.html` w przeglądarce i wczytaj `sample/sheet.json`.
2. Oznaczaj przez około godzinę, potem eksportuj do `sample/labels.json`.
3. Przeczytaj raport i zapisz decyzję.

## Performance Considerations

Przy 4 równoległych wywołaniach i wolnym `flex` przebieg może trwać ponad godzinę, dlatego działa w tle i jest wznawialny. Szacowany koszt to ok. $1 (z nagrań `luna`: 550 postów × do 3 prób ≈ $0,8–1,3 na kanale `openrouter`, o połowę mniej na `flex`), bezpiecznik zatrzymuje go przy $4. Klucz z `openrouter.env` może liczyć się do limitu $10 na produkcji.

## Migration Notes

Brak migracji i zmian konfiguracji. Merge przebudowuje obraz API tak jak każda zmiana w `szczur-radar-api/`, więc obowiązują zwykłe reguły: bez `RUNNING`, poza oknem nocnym.

## References

- Frame: `context/changes/reddit-quality-and-cost/frame.md`
- Wzorce: `measurement/GoldRecorder.java`, `WykopGoldSampler.java`, `GoldReport.java`, `measurement/README.md:117-222`
- Arkusz źródłowy: `context/archive/2026-09-27-assessment-contract-harness/pilot/labeling-gold.html`
- Runbook włączenia: `context/deployment/deploy-plan.md:2071-2095`
- Roadmapa: `context/foundation/roadmap.md:252-265,277,301`
- Lekcje: „Przed zamrożeniem kontraktu LLM zrób tani pilot…”, „Kryterium, które agent sprawdzi poleceniem…”, „Uzasadniaj zakres celem użytkownika…”

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Narzędzia pomiaru

#### Automated

- [x] 1.1 Testy jednostkowe zielone, w tym nowe i bramka nagrań — 55f427b
- [x] 1.2 Zmiana nie dotyka produkcji ani danych bramki — 55f427b
- [x] 1.3 Próbny zapis jednego dnia przechodzi, a posts.json nie ma adresów ani wzmianek — 55f427b
- [x] 1.4 CI na PR zielone — 55f427b

### Phase 2: Przebieg na żywo

#### Automated

- [x] 2.1 production-llm.json i wykop-usage.json poprawne, bez treści
- [x] 2.2 posts.json obejmuje 7 dni i 3 subreddity, bez adresów i wzmianek
- [x] 2.3 Każdy post ma ocenę ok albo trzy próby
- [x] 2.4 Wszystkie próby mają tę samą wersję, model, tryb i reasoning produkcji
- [x] 2.5 Koszt przebiegu nie przekracza $4
- [x] 2.6 Raport ma wszystkie sekcje i akapit o klasyfikatorze

### Phase 3: Godzina oceny

#### Automated

- [ ] 3.1 sheet.json ma poprawny skład i przeplot
- [ ] 3.2 Scenariusz playwright-cli przechodzi z zapisanym wynikiem
- [ ] 3.3 Eksport przechodzi GoldLabels.readLabels i RedditReport
- [ ] 3.4 Raport zawiera sekcję o pominiętych celach

#### Manual

- [ ] 3.5 Operator oznacza posty przez około godzinę i zapisuje labels.json

### Phase 4: Decyzja i dokumenty

#### Automated

- [ ] 4.1 deploy-plan nie wymaga już spełnienia progów
- [ ] 4.2 Sekcja S-28 w roadmapie bez posts.language, wpis w Parked
- [ ] 4.3 Rysunek roadmapy aktualny
- [ ] 4.4 Komentarze z raportem są w issue #122 i #121
- [ ] 4.5 CI na PR zielone

#### Manual

- [ ] 4.6 Operator czyta raport i zapisuje decyzję, budżet i listę subredditów
