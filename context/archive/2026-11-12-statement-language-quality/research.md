---
date: 2026-11-12T09:28:23+01:00
researcher: Claude Opus 5.5 (/10x-research)
git_commit: af3046705fcb1c6eb4bc40274f00b2a77c18d389
branch: feature/statement-language-quality
repository: szczur-radar
topic: "S-36: jak mierzyć per źródło udział tez (statement) w innym języku niż post i dać sygnał kanałem S-35"
tags: [research, s-36, fr-033, assessment-quality, language, night-summary, alerts]
status: complete
last_updated: 2026-11-12
last_updated_by: Claude Opus 5.5 (/10x-research)
---

# Research: S-36 — tezy w innym języku niż post, per źródło

**Date**: 2026-11-12T09:28:23+01:00
**Researcher**: Claude Opus 5.5 (/10x-research)
**Git Commit**: af3046705fcb1c6eb4bc40274f00b2a77c18d389
**Branch**: feature/statement-language-quality
**Repository**: szczur-radar

## Research Question

Wycinek S-36 (`context/foundation/roadmap.md:236-248`): operator widzi osobno dla każdego źródła udział tez (`statement`) w innym języku niż post, a przekroczenie progu daje sygnał tym samym kanałem co S-35. Pytania badania:

1. Co już ustalono (reguła, kanał, okno) i co zostaje do decyzji `/10x-plan`?
2. Skąd dziś da się wziąć język tezy i język posta — i jakim kosztem każdy wariant z roadmapy (pole w wyjściu modelu, wykrywanie w backendzie, `posts.language`)?
3. Jak jest zbudowany kanał S-35 i gdzie S-36 się do niego podłącza?
4. Na jakich danych ustalić próg?

## Summary

- **Reguła i kanał są ustalone, metoda wykrywania i próg — nie.** Reguła „`statement` w języku posta” obowiązuje bez wyjątków, także na Reddicie, i nie jest sprawdzana walidatorem (`context/foundation/llm-assessment/contract-v2.md:261-262`, Q-16, `prd.md:268-269`). S-35 zostawił S-36 to samo okno (7 dni kalendarza warszawskiego `[D−6, D]`) i ten sam kanał: linię `quality 7d` w podsumowaniu nocy oraz alarm `ASSESSMENT_QUALITY` przy przejściu przez próg (`prd.md:270`, `context/archive/2026-11-11-per-source-failure-quality/plan.md:7`).
- **Dane do miary już są w bazie, bez migracji.** Tezy udanych prób leżą w `assessments.output` (JSONB, niepuste dokładnie przy `status = 'ok'`, `V3__create_pipeline_and_posts.sql:139,165-167`), pod ścieżką `output->'claims'->i->>'statement'`. Tekst posta leży w `posts.title`/`posts.body` (`V3:90-91`, `Post.java:72-74`). Każdy wariant liczony w backendzie da się więc policzyć wstecz na danych produkcyjnych, także do ustalenia progu.
- **Języka nie ma dziś nigdzie w produkcie.** Nie ma go w schemacie wyjścia, w `posts`, w `Source` ani w `pom.xml` (żadnej biblioteki do wykrywania języka). Jedyna reguła języka to kod pomiarowy S-28 w `src/test` (`measurement/RedditMeasures.java:51-60,115-151`), który nie trafia do jara.
- **Język tezy: reguła S-28 działa dobrze.** Teza jest polska, gdy ma polską literę albo co najmniej 2 polskie słowa funkcyjne, w przeciwnym razie angielska. Ręczne sprawdzenie dało 40 zgodności na 40 tez (`report-2026-10-31.md:106-108`). Słabość: krótka polska teza bez diakrytyków.
- **Język posta: ta sama reguła na tekście posta jest stronnicza.** Jedna polska litera w długim angielskim poście robi z niego post polski, a krótki wpis z Wykopu bez ogonków albo z samym angielskim tytułem linku wychodzi jako angielski. Roadmapa i S-28 wskazują prostszy zamiennik: język z `source`/`channel` (`roadmap.md:422`).
- **Pole `language` w wyjściu modelu to najdroższy wariant.** Wymaga ścieżki §16 kontraktu, nowej `PROMPT_VERSION` (dziś `v2.5`, `AssessmentPrompt.java:39`) i unieważnia nagrania (S-28: 480 nagrań, `S-28 frame.md:72`). Nie obejmuje też ocen sprzed zmiany, więc progu nie da się ustalić na historii.
- **Punkt odniesienia dla progu dotyczy tylko Reddita.** S-28 zmierzył 22,1 % tez po polsku (484/2191) na próbce Reddita przy tym samym prompcie `v2.5` (`report-2026-10-31.md:3,60`). Udziału tez nie po polsku na Wykopie i udziału na Reddicie z produkcji nikt jeszcze nie zmierzył. S-35 ustalił swoje progi odczytem produkcji tylko do odczytu, za zgodą właściciela, na etapie `/10x-plan` (`evidence/README.md:3-27`).
- **Pułapka kanału.** `OperatorAlertQueue` odrzuca to samo zdarzenie dla tego samego przebiegu w ciągu 10 min (`OperatorAlertQueue.java:56,180-191`). Drugi `ASSESSMENT_QUALITY` dla tego samego `alertRunId` w tym samym żądaniu przepadnie. Przejście przez próg językowy trzeba więc dołączyć do treści jednego alarmu albo dać mu osobne zdarzenie.

## Detailed Findings

### 1. Co ustalono przed S-36

- **Reguła:** `contract-v2.md:261` — „`statement` jest w języku posta (post po polsku → po polsku, post po angielsku → po angielsku)”. `:262` (Q-16, 2026-11-04): bez wyjątków, także dla Reddita; walidator jej nie sprawdza; pilnuje jej prompt, a udział mierzy się per źródło na danych produkcyjnych (FR-033). `contract-v2.md:178` (tabela pól §3) mówi to samo.
- **Prompt:** `szczur-radar-api/src/main/resources/assessor/system-prompt.md:52` — „`statement`: krótko (≤240 znaków) w języku posta; …”. Cały prompt jest po polsku. Plik zmienił się ostatnio 2026-10-06 (`663c687`), a S-28 mierzył na tej samej wersji `v2.5`.
- **Przyczyna zjawiska (kontekst, nie zakres):** w pilocie F-02 polski prompt dał 65 % tez po polsku na postach angielskich mimo instrukcji, a prompt angielski 0 % (`context/archive/2026-09-27-assessment-contract-harness/frame.md:272`). Ten prompt angielski zmieniał też inne rzeczy, więc nie był czystym testem A/B (`:265`). Naprawa przyczyny to osobna decyzja §16, bo nowa `PROMPT_VERSION` unieważnia nagrania (`context/archive/2026-10-30-reddit-quality-and-cost/frame.md:72`). Właściciel 2026-10-31: „Tezy po polsku: nie są warunkiem” włączenia Reddita (`…/reddit-quality-and-cost/change.md:19`).
- **Kanał i okno z S-35:** `prd.md:270` — „Część o języku tez robi S-36 tym samym kanałem i w tym samym oknie”. Okno 7 dni `[D−6, D]` i minimum 100 prób (`AssessmentQuality.java:45,48`). Próg jest stałą przy źródle, a jego zmiana to PR (`Source.java:31-37`). Alarm idzie tylko przy przejściu przez próg, bez zapisanego stanu (`AssessmentQuality.java:79-87,141-156`).
- **Domena:** R-38 „język tez”, status *declared* (`context/domain/domain-distillation.md:217`); D-14 „naprawa świadomie odłożona” (`:286`); kandydat #1 jest gotowy, gdy istnieje miara z udziałem tez w innym języku niż post i sygnał bez logów (`:321-323`).

### 2. Skąd wziąć język tezy i posta

**Stan dziś (sprawdzony zakres: `src/main/java/**`, migracje V1–V15, `pom.xml`, schemat wyjścia):**
- schemat wyjścia nie ma pola `language`; `statement` to tekst o długości 1–240 (`assessor/assessment-output-v2.schema.json:91,106`);
- kontrakt §2: język „nie trafia do modelu jako osobne pole; zna go backend (§15)” (`contract-v2.md:75`), ale lista proweniencji w §15 (`:402-441`) języka nie zawiera;
- `Source` ma tylko `failureThresholdPercent` (`Source.java:15,22,26-28`); Wykop czyta jeden tag `gielda` (`sources/wykop/WykopPosts.java:24`), Reddit — subreddity z `REDDIT_SUBREDDITS`;
- `pom.xml` nie ma lingua, langdetect, Tiki ani Optimaize.

**Reguła S-28 (`szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/RedditMeasures.java`):**
- `POLISH_LETTER = [ąćęłńóśźż]` z `CASE_INSENSITIVE | UNICODE_CASE`, wystarczy jedno `find()` (`:51-52`);
- tokeny `\p{L}+` po `toLowerCase(Locale.ROOT)` (`:54,125`);
- `POLISH_WORDS` — 14 słów: `że, się, nie, jest, oraz, ale, czy, będzie, które, jednak, dla, przez, jako, lub` (`:56-58`). Celowo bez słów, które są też słowami angielskimi (`a, i, to, on, no, by, ma`; plan-review S-28 F5, `…/reddit-quality-and-cost/reviews/plan-review.md:77-84`);
- `MIN_POLISH_WORDS = 2`, każde wystąpienie się liczy (`:60,128`);
- wynik ma dwie wartości, `POLISH` albo `ENGLISH`, bez „nieznany” (`:116-133`); `ThesisMix` daje mieszankę na post (`:136-151`); testy: `RedditMeasuresTest.java:79-98`;
- reguła żyje w `measurement/`, którego `src/main` nie może importować (`measurement/CLAUDE.md`). Produkcyjna wersja to nowy kod w `main` (przeniesienie albo nowa implementacja).

**Jak reguła sprawdza się na tezach i na postach:**
- **Tezy:** 40 zgodności na 40 przy ręcznym sprawdzeniu (7 PL, 33 EN; jedna teza to same liczby i skróty). Sprawdzała jedna osoba na małej liczbie polskich tez, więc sprawdzenie może przeoczyć krótkie polskie tezy bez diakrytyków (`report-2026-10-31.md:106-108`). Model pisze polskie tezy z diakrytykami (wniosek z tej próbki, nie mierzony osobno).
- **Posty (wniosek z reguły, niezmierzony):**
  - długi angielski post z jedną polską literą (np. nazwa „Łódź”) wychodzi jako polski;
  - wpis z Wykopu bez ogonków (`ze`, `sie` wypadają ze zbioru słów) albo z samym angielskim tytułem linku, tickerem czy emoji wychodzi jako angielski — i jego polskie tezy byłyby wtedy liczone jako „w innym języku”, choć naruszenia nie ma;
  - ten sam błąd w drugą stronę (reguła uzna polski post za angielski) dotyka tez polskich i wypacza miarę Wykopu.
- **Zamiennik języka posta:** `roadmap.md:422` — „Do tego czasu język da się przybliżyć z `posts.channel`”. Wykop #gielda to w praktyce polski, a skonfigurowane subreddity — angielski, ale w kodzie nic tego nie zapisuje.

**Warianty z roadmapy (`roadmap.md:245`), porównane na faktach:**

| Wariant | Co zmienia | Historia (próg z produkcji) | Koszt / ryzyko |
|---|---|---|---|
| (a) pole `language` w wyjściu modelu | schemat, prompt, nowa `PROMPT_VERSION`, kontrakt §16 (`contract-v2.md:444-454`) | nie — tylko oceny po zmianie | unieważnia nagrania (S-28: 480); model sam ocenia język, który pisze źle |
| (b) wykrywanie w backendzie na tekście (reguła S-28 albo biblioteka) | nowy kod w `main`; opcjonalnie `posts.language` z backfillem | tak — `assessments.output` i `posts.title/body` | stronniczość na postach (wyżej); biblioteka = nowa zależność |
| (c) język posta z `source`/`channel`, język tezy regułą S-28 | stała przy źródle (jak próg) albo mapa kanałów | tak | anglojęzyczny wpis na Wykopie albo polski subreddit liczy się źle; dziś nie ma takiego kanału w konfiguracji (wniosek z `WykopPosts.java:24` i `REDDIT_SUBREDDITS`) |

**`posts.language`:** właściciel 2026-10-29: „ma być zapisany” (roadmapa z commita `78c57ca`, linia 263). Pozycję odłożono dwa razy jako osobną potrzebę (S-27 `…/2026-10-29-reddit-source/plan.md:7,45`; S-28 `frame.md:14,70`, `plan.md:42`), ale żadnego wariantu nie odrzucono. Do miary FR-033 nie jest potrzebna: miarę da się liczyć w zapytaniu albo w Javie bez kolumny. `prd.md:248` (S-29): języka posta nie pokazujemy, bo nie jest zapisany.

### 3. Kanał S-35 i punkty podłączenia

**Miara (`szczur-radar-api/src/main/java/pl/szczurradar/api/posts/`):**
- `AssessmentQuality` (`@Component`, tylko odczyt):
  - `window(to, zone)` (`:60-64`);
  - `percent` — jedna cyfra po przecinku, zaokrąglenie połówki w górę, kropka (`:93-101`);
  - `status` — mniej niż 100 prób daje `NOT_ENOUGH_ATTEMPTS`, `failed*100 > threshold*attempts` daje `ABOVE_THRESHOLD` (`:104-109`);
  - `sources` (`:112-135`), `crossed` (`:79-87`, `:141-156`).
- Rekordy: `QualityWindow(from, to, sources)`, `SourceQuality(source, attempts, failed, classes, thresholdPercent, status)` (`SourceQuality.java:21`), `QualityCrossing(tonight, @Nullable before)`, `QualityStatus` (`ok`/`above_threshold`/`not_enough_attempts`, nigdzie nie zapisywany).
- Zapytanie: `AssessmentRepository.countModelAttemptsBySourceAndClass` (`:71-78`), JPQL po `Assessment join Post`, `resultSource = MODEL`, okno `[from, until)` po `assessedAt`, indeks `ix_assessments_assessed_at` (`V15:9`). Treść `output` nie jest czytana.
- „Poprzednie podsumowanie” jest przybliżone bez stanu: drugie zapytanie za okno kończące się w `min(startOf(night), now − 24h)` (`AssessmentQuality.java:79-87`; testy `AssessmentQualityTest:110,132`, `AssessmentQualityIT:119`).

**Podsumowanie nocy i alarm:**
- `pipeline/NightSummary`:
  - jakość czytana jest we własnej transakcji tylko do odczytu (`:148-149,159-167`); `quality(date, now)` przy wyjątku daje linię `unavailable (<Class>)` (`:174-187,311-321`);
  - linia per źródło: `qualityLine` (`:361-389`), np. `wykop quality 7d (2026-11-05…2026-11-11): failed 2.6% of 3134 attempts (…); threshold 5%` z dopiskiem `; ABOVE THRESHOLD`;
  - treść alarmu: `qualityAlert()` (`:349-356`) i `crossingText` (`:392-419`).
- `pipeline/api/InternalPipelineRunController.java:123-124` podnosi `ASSESSMENT_QUALITY` po `NIGHT_SUMMARY` dla `night.alertRunId()`.
- `alerts/AlertEvent.java:28-29`: `ASSESSMENT_QUALITY` ma priorytet 4, a jego opis mówi tylko o „share of failed assessment attempts”.
- `OperatorAlertQueue`:
  - klucz deduplikacji to `(event, runId)` w oknie 10 min (`:56,81,180-191`);
  - najwyżej 2 zdarzenia na minutę, kolejka 20 (wyniki pracownika; okno 10 min sprawdzone w kodzie).

**`/admin`:**
- API: `posts/api/AssessmentQualityController` → `GET /api/admin/assessment-quality` (`:36-40`), widoki `QualityView`, `SourceQualityView`, `ClassCountView` (`:51,66,82`).
- Pliki kontraktu: `szczur-radar-web/src/contract/assessment-quality.json` i `assessment-quality-few-attempts.json`, w rejestrze `ContractRegistry.java:59-60,98-99`.
- Próbki i typy: `samples.ts:867,900,934-935`; typy w `src/api/quality.ts`.
- Widok: `AdminView.vue` — ładowanie `:126-157`, formatowanie `:160-178`, sekcja „Jakość ocen (7 dni)” `:461-478`; test `AdminView.test.ts:709-760`.
- Etykiety: `labels.ts:73` (`qualityStatus`); `rules.json` `values.qualityStatus`/`values.failureClass` (`:31,35`).
- Atrapa: `dev/mock-api.js` (tryb `QUALITY` `:29-33,433`, dane `:448-464`, trasa `:573-574`); `mockApi.test.ts:188-205,610,816-831`.

**Testy, które przypinają format S-35:** `NightSummaryTest` (`:48,127,213,239`), `NightSummaryIT:112`, `InternalPipelineRunIT:389-458` (kolejność: podsumowanie przed alarmem), `NtfyAlertsTest:74-81`, `SourceTest:11`. Fixture `tools/pipeline-trigger/fixtures/api/nightly-summary.json` nie ma linii jakości (noc z 0 prób); przykładowe linie są w `tools/pipeline-trigger/README.md:34,67-68,74`. Runbook S-35: `context/deployment/deploy-plan.md:2373-2418`.

**Prawdopodobne miejsca zmian S-36** (wniosek z powyższego, nie projekt):
- drugi próg na `Source` (nowy argument konstruktora + `SourceTest`);
- nowe zapytanie po udanych próbach z `output` w oknie (i tekst posta albo jego źródło) oraz reguła języka w `main`;
- rozszerzenie `SourceQuality`/`QualityWindow` albo równoległy rekord;
- druga linia albo dopisek w `qualityLine`;
- przejście przez próg w tej samej logice dwóch okien;
- treść jednego `ASSESSMENT_QUALITY` albo nowe zdarzenie (wtedy też `NtfyAlertsTest`);
- widoki API, pliki kontraktu i próbki, `quality.ts`, `AdminView.vue`, atrapa i jej testy.

Nowe pole `@Nullable` albo lista potrzebuje wariantu w pliku kontraktu albo wpisu `KNOWN_GAPS` (dziś brak wpisów dla jakości, `ContractVariantsTest.java:54-63`). Migracja jest potrzebna tylko przy `posts.language`, a wtedy w tym samym PR backfill, `JpaSchemaIT` i — jeśli kolumna przechowuje enum — `CHECK` z wpisem w `ClosedSetsIT`.

### 4. Mianownik i dane do progu

- **Mianownik S-35 to próby**, w tym nieudane (`AssessmentRepository.java:75-78`). Miara języka liczy się na tezach udanych prób, więc ma inny mianownik: tezy albo posty z tezami. S-28 podał oba (`report-2026-10-31.md:60-66`: 484/2191 tez, mieszanka 75,1 % / 5,2 % / 16,8 % / 2,8 % na 386 postach).
- **Ile udanych ocen ma jeden post:** na obejrzanych ścieżkach co najwyżej jedną. `contentHash` czyta tylko `posts/Post.java`, więc zmieniony post nie wraca do oceny. `PostTransitions.restore` przywraca tylko posty `dead` (`PostTransitions.java:30,190`), które udanej oceny nie miały. W bazie nie ma jednak ograniczenia unikalności na `(post_id, status='ok')` (`V3:130-170`), więc to wniosek, nie gwarancja.
- **Próbki prefiltra:** udana próba z `result_source = 'prefilter'` ma wyjście bez tez (wniosek z nazwy „no-content prefilter” w `agents/assessor`; nie sprawdzone wierszem). Filtr `result_source = 'model'` jak w S-35 je pomija.
- **Dane produkcyjne:** S-35 wziął je jednym odczytem tylko do odczytu, za zgodą właściciela, w `/10x-plan`: `railway ssh -s postgres -e production -- psql … -c "SET default_transaction_read_only = on" -c "<SQL>"`. Wynik zawierał same liczby, bez treści postów (`evidence/README.md:3-27`). Ten sam odczyt dla S-36 może grupować tezy per dzień warszawski i źródło, a regułę S-28 da się odtworzyć w SQL (`regexp_split_to_table`, klasa diakrytyków z wielkimi literami podanymi wprost). `[:alpha:]` i `lower()` zależą od `lc_ctype` bazy, więc najpierw `SHOW lc_ctype`. Szkic SQL pracownika nie był nigdzie uruchomiony.

## Code References

- `context/foundation/llm-assessment/contract-v2.md:75,178,259-264,444-454` — język poza wejściem, pole `statement`, §8 z Q-16, §16 reguła zmiany
- `szczur-radar-api/src/main/resources/assessor/system-prompt.md:52` — „w języku posta”
- `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/prompt/AssessmentPrompt.java:39` — `PROMPT_VERSION = "v2.5"`
- `szczur-radar-api/src/main/resources/db/migration/V3__create_pipeline_and_posts.sql:84-114,130-170` — `posts.title/body`, `assessments.output` i `ck_assessments_outcome`
- `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/RedditMeasures.java:51-60,115-151` — reguła języka tez S-28
- `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/Source.java:15-37` — próg per źródło jako stała
- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/AssessmentQuality.java:45-156` — okno, status, przejście przez próg
- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/AssessmentRepository.java:71-78` — zapytanie jakości
- `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/NightSummary.java:174-187,349-419` — linia jakości i treść alarmu
- `szczur-radar-api/src/main/java/pl/szczurradar/api/alerts/AlertEvent.java:28-29`, `OperatorAlertQueue.java:56,180-191` — zdarzenie i deduplikacja
- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/AssessmentQualityController.java:36-82` — `/api/admin/assessment-quality`
- `szczur-radar-web/src/views/AdminView.vue:126-178,461-478` — sekcja jakości w `/admin`

## Architecture Insights

- Miara jakości to odczyt z danych bez zapisanego stanu: okno i przejście liczone są od nowa co noc (`AssessmentQuality.java:66-87`). Wariant liczony w backendzie na już zapisanych `output` pasuje do tego wzorca. Wariant (a) też, ale dopiero od zmiany promptu.
- `posts/` nie importuje `pipeline/` (strefa przekazywana przez wywołującego, `AssessmentQuality.java:28-31`). Kod pomiarowy nie trafia do `main` (`measurement/CLAUDE.md`). Reguła języka w `main` potrzebuje więc własnego miejsca, z którego `posts/` może korzystać (np. obok `text/Whitespace`, który już jest współdzielony; to propozycja, nie ustalenie).
- Progi to stałe przy `Source` (zmiana progu to PR), a nowe źródło nie kompiluje się bez progu (`Source.java:9-11`). Drugi próg wpisuje się w ten sam wzorzec. Reguła repozytorium o nowym źródle (`REPOSITORY_GUIDELINES.md`, Hard Rules) wymienia dziś tylko `failureThresholdPercent`, więc drugi próg zmienia też ten opis.
- `QualityStatus` nie jest zapisywany, więc nowy status albo nowa miara nie potrzebuje `CHECK`. Wartość z etykietą idzie jednak przez `rules.json` i obie strony (`labels.ts:73`).

## Historical Context (from prior changes)

- `context/archive/2026-09-27-assessment-contract-harness/frame.md:259,272` — pilot: prompt PL dał 65 % tez po polsku, prompt EN 0 %; właściciel wtedy: tezy po angielsku są akceptowalne. **Aktualne** jako przyczyna; decyzję o regule zastąpiło Q-16 (bez wyjątków).
- `context/archive/2026-10-29-reddit-source/plan.md:7,45`, `plan-brief.md:36` — język posta odłożony do S-28/S-29. **Zastąpione:** S-28 też go odłożył, roadmapa przekazała decyzję S-36 (`roadmap.md:422`).
- `context/archive/2026-10-30-reddit-quality-and-cost/frame.md:14,30,39,70,72`, `plan.md:16,42,89`, `report-2026-10-31.md:60-66,106-108,132`, `change.md:19` — pomiar 22,1 %, reguła klasyfikatora, `posts.language` poza zakresem, nowa `PROMPT_VERSION` kosztuje nagrania, tezy po polsku nie blokują Reddita. **Aktualne.** Liczba 22,1 % pochodzi z próbki (386 postów z oceną), nie z produkcji.
- `context/archive/2026-11-11-per-source-failure-quality/plan.md:7`, `plan-brief.md:23-27`, `evidence/README.md:3-42` — kanał „gotowy dla S-36”, okno, procedura odczytu produkcji, odtworzenie progów na 35 dniach. **Aktualne.**

## Related Research

Nie dotyczy — w `context/changes/**` i `context/archive/**` nie ma osobnego `research.md` o języku tez. Najbliższe są frame/plan/report S-28 i plan S-35 wymienione wyżej.

## Open Questions

Decyzje dla `/10x-plan` (właściciel; żadna nie blokuje):

1. **Język posta:** z `source`/`channel` (stała, jak próg), regułą na tekście posta (stronniczość opisana w §2) czy polem modelu (a)? I czy przy okazji powstaje `posts.language` z backfillem (stanowisko 2026-10-29 „ma być zapisany”)? Badanie nie znalazło niczego, co do miary FR-033 wymagałoby kolumny.
2. **Język tezy:** reguła S-28 przeniesiona do `main` (dwie wartości, pl/en) czy biblioteka? Co z tezą bez rozstrzygnięcia (same liczby i tickery)? Dziś reguła zalicza ją do angielskich.
3. **Mianownik i minimum:** tezy czy posty z tezami; tylko `result_source = 'model'`; jakie minimum zamiast 100 prób (np. N tez albo N postów)?
4. **Próg per źródło:** na podstawie odczytu produkcji tylko do odczytu (procedura S-35, za zgodą właściciela) z odtworzeniem przejść na 35 dniach. Brakuje liczb dla Wykopu i dla produkcyjnego Reddita.
5. **Kształt sygnału:** druga linia `language 7d` czy dopisek w `quality 7d`; jedno `ASSESSMENT_QUALITY` z obiema miarami czy nowe zdarzenie. Jedno z tych dwóch jest konieczne, bo deduplikacja `(event, runId)` w `OperatorAlertQueue` odrzuci drugie takie samo zdarzenie.
6. **Poza zakresem do potwierdzenia:** naprawa przyczyny (prompt EN albo dwujęzyczny, §16) — zgodnie z S-28 osobna decyzja po pomiarze.
