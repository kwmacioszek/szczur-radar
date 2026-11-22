<!-- IMPL-REVIEW-REPORT -->
# Implementation Review — F-08 · Faza 3: widoki w nowym wyglądzie i wzorce F-06 od nowa

- **Plan**: [plan.md](../plan.md)
- **Scope**: Phase 3 of 4
- **Reviewed phases**: 3
- **Date**: 2026-11-21
- **Revision**: `e16cfd4..367a0fd` (commity fazy: `003fd78`, `367a0fd`), stan sprawdzony na `81f666b`
- **Verdict**: REJECTED
- **Findings**: 1 CRITICAL, 1 WARNING, 4 OBSERVATION

Stan planu: 26/26 punktów odhaczonych, 3.8 zaakceptowany przez właściciela 2026-11-21. Gałąź PR #199 ma na górze `11e4b36`. Lokalny `81f666b` (odhaczenie 3.8) nie jest wypchnięty.

Kryteria automatyczne fazy 3 sprawdzone w tym przeglądzie:

| Kryterium | Wynik |
| --- | --- |
| 3.1 `npm test` | zielone, 33 pliki, 1061 testów |
| 3.2 `npm run build` | zielone |
| 3.3 `npm run e2e` | zielone, 22 testy |
| 3.4 pomiary w Chromium | zielone. Przy 1024 i 1280 px: karta 544/612 px, feed, post i przebieg 768/864 px, panel admina 896/1008 px, pierwsza kolumna karty 144/162 px, panel powiadomień 384/432 px. Pole „Nowe hasło” z `aria-invalid="true"` ma krawędź i cień `--destructive` przed najechaniem i pod kursorem. Odstęp alertu od „Spróbuj ponownie” wynosi 12 px w feedzie i w panelu powiadomień. |
| 3.4/3.5 `npm run visual:docker` | **czerwone** lokalnie: 7 błędów, 49 przechodzi. Błędy dotyczą dokładnie 7 wzorców wziętych z artefaktu CI w `367a0fd`. |
| 3.6 `gh pr checks 199` | **czerwone** na `11e4b36`: krok wizualny `web-e2e`, `light-1280/view-activate`, 1 piksel. Reszta zielona, `mutation` w toku. |
| 3.7 środowisko PR | anonimowa część powtórzona: `/login` rysuje „Newsreader 16pt 16pt” i Hanken Grotesk przez CDP, a trzy pliki `woff2` (w tym latin-ext dla „Hasło”) przychodzą jako `200 font/woff2`. Części zalogowanej nie powtórzono. |

## Verdicts

| Dimension | Verdict |
| --- | --- |
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | FAIL |

## Findings

### F1 — Wzorce F-06 zależą od sprzętu runnera, krok wizualny jest czerwony

- **Severity**: ❌ CRITICAL
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Success Criteria
- **Location**: [szczur-radar-web/playwright.visual.config.ts:62](../../../../szczur-radar-web/playwright.visual.config.ts#L62), `visual/__screenshots__/light-1280/view-activate.png`
- **Detail**: Przebieg CI 44953717465 na `11e4b36` ma czerwony krok wizualny: `light-1280/view-activate` różni się o 1 piksel, także w ponowieniu. Porównanie z artefaktem `visual-baselines` daje 8 pikseli z różnicą ±1 w jednym kanale, w rogach zaokrąglonej ramki (x 340–342 i 938–939, y 488–489). Geometria jest identyczna, więc to antyaliasing, a nie zmiana CSS w `11e4b36`. Ten przebieg trafił na runner w westus2. Zielone przebiegi `367a0fd`, `3733e79` i `f410735` biegły w southcentralus, centralus i northcentralus, a wzorce pochodzą z przebiegu w centralus. Obraz runnera jest ten sam (ubuntu-26.04 20261116.202.1). Ten sam objaw widać lokalnie: `npm run visual:docker` na Macu (Rosetta) jest czerwony na 7 wzorcach z artefaktu. Kryterium 3.5 („zrzuty powtarzalne”) sprawdzono dwoma przebiegami na jednej maszynie, więc nie wykrył zależności od sprzętu. Skutki: `railway-config` ma `needs:` na `web-e2e`, więc środowisko PR się nie wdraża. Po merge'u losowo czerwony przebieg na `main` zatrzyma wdrożenie produkcji przez Wait for CI. Zdanie w `REPOSITORY_GUIDELINES.md:68`, że wzorce z `visual:update` pod emulacją przechodzą na runnerach GitHuba, od `367a0fd` jest nieprawdziwe.
- **Fix A ⭐ Recommended**: Wymusić w Chromium ścieżkę rysowania niezależną od procesora: dodać `--disable-skia-runtime-opts` do `launchOptions.args` w `playwright.visual.config.ts`, a jeśli to nie wystarczy, także `--disable-gpu`. Potem jednorazowo wygenerować wzorce od nowa i ponawiać CI, aż przebiegi trafią do kilku regionów.
  - Strength: Usuwa przyczynę i zachowuje regułę `threshold: 0`, `maxDiffPixels: 0` z F-06. Chromium używa tej flagi we własnych testach pikselowych właśnie dlatego, że Skia wybiera kod SIMD według procesora. Może też przywrócić zgodność lokalnego `visual:update` z CI.
  - Tradeoff: Wszystkie 78 wzorców trzeba wygenerować jeszcze raz. Dowód zajmie kilka przebiegów CI.
  - Confidence: MED — objaw (±1 w rogach, zależny od regionu) pasuje do różnic SIMD w Skia, ale flagi nie sprawdzono w tym obrazie.
  - Blind spot: Nie wiadomo, czy rogi rysuje rasteryzacja CPU, czy SwiftShader. Od tego zależy, która flaga działa.
- **Fix B**: Dopuścić różnice antyaliasingu małym progiem (np. `threshold` około 0,05) i zmienić regułę F-06 w `REPOSITORY_GUIDELINES.md`.
  - Strength: Na pewno zatrzymuje różnice ±1 bez badania Chromium.
  - Tradeoff: Osłabia bramkę, którą F-06 celowo zaostrzył: domyślne 0,2 przepuściło przyciemniony hover. Trzeba powtórzyć dowód z `visual-breaks.md`, a regułę musi zmienić właściciel.
  - Confidence: HIGH — progi YIQ w Playwright pomijają różnice tej wielkości.
  - Blind spot: Nie sprawdzono, czy próg 0,05 dalej łapie przyciemniony hover z F-06.
- **Decision**: FIXED (Fix A, rozszerzony) — Chromium w testach wizualnych ma dwie flagi:
  - `--disable-skia-runtime-opts` (`c589529`): z nią CI w westus3 dało te same piksele co lokalna emulacja dla 6 z 7 wzorców zależnych od procesora.
  - `--disable-partial-raster` (`e0737de`): została jedna niestabilność, w obrębie jednego przebiegu. Rogi pola „Powtórz hasło” w `view-activate` (promień 10 px, dolna krawędź na y = 489,625 px) wychodziły w dwóch wariantach. Wariant zależał od tego, czy kafel był dorysowany częściowo po późnych zmianach strony (formularz zastępuje „sprawdzanie”, fokus, kroje). Z pełnym rysowaniem lokalny render jest bajt w bajt równy dawnemu wzorcowi z CI. Ta sama przyczyna tłumaczy rozjazd `light-375/kitchen-sink-pola` między lokalnymi przebiegami 2026-11-21, a nie HMR.
  - Nowe wzorce: `view-activate` (light-375 i light-1280), `kitchen-sink-pola`, `state-pola-email-hover` i `state-pola-search-focus` (oba projekty 375 px).
  - `REPOSITORY_GUIDELINES.md` opisuje obie flagi (`4647f9b`).
- **Weryfikacja poprawki**:
  - Lokalnie dwa pełne `npm run visual:docker` są zielone (56 testów) i nie zmieniają żadnego wzorca.
  - W CI krok wizualny jest zielony bez niestabilnych testów na runnerach w trzech regionach: us-east-1 (`e0737de`, przebieg 44994050961), southcentralus (`4647f9b`, 44994686833) i eastus3 (ponowione `web-e2e` tego przebiegu).
  - `gh pr checks 199` daje 11/11, łącznie z `railway config`.

### F2 — Siatka celów w karcie posta zmieniona bez zapisu, przy 320 px nazwa celu ma 40 px

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: [szczur-radar-web/src/components/PostCard.vue:109](../../../../szczur-radar-web/src/components/PostCard.vue#L109)
- **Detail**: Kontrakt fazy 3 mówi „Układ (siatki…) bez zmian”. `003fd78` zmienia `.targets` z `fit-content(50%) minmax(0, 1fr)` na `minmax(0, max-content) max-content` z `align-items: start`. Powód jest realny: znacznik stanowiska z fazy 2 ma `white-space: nowrap`, a stara kolumna `1fr` nie mieściła go w wąskiej karcie. Zmianę opisuje tylko komunikat commita, nie `plan.md` ani opis PR. Pomiar w Chromium przy 320 px: `dl` ma 238 px, kolumna nazwy celu 40 px obok znacznika 182 px, a „CDR · spółka” łamie się na 3 linie w środku słowa. Strona nie przewija się w poziomie. Przy 375 px kolumna ma 93–99 px i jedną linię. Wzorce F-06 mają tylko 375 i 1280 px, więc stanu 320 px nikt nie oglądał. Na `/kitchen-sink` przy 320 px znacznik z celem i rodzajem wystaje o 8 px, z tej samej przyczyny (`nowrap`, faza 2, strona tylko dev).
- **Fix A ⭐ Recommended**: Zapisać zmianę w `plan.md` i w opisie PR jako świadome odstępstwo, pokazać właścicielowi zrzut karty przy 320 px i przekazać wąski wiersz posta do S-51+, który przebudowuje wiersz posta.
  - Strength: Nie zmienia układu w F-08, zgodnie z „What We're NOT Doing”. Decyzję o wyglądzie podejmuje właściciel na obrazku (pamięć `ui-decisions-by-pictures`).
  - Tradeoff: Do S-51 karta przy 320 px łamie nazwę celu w środku słowa.
  - Confidence: HIGH — dowody pochodzą z pomiaru, a S-51+ ma wiersz posta w zakresie.
  - Blind spot: Nie wiadomo, jak często prawdziwi użytkownicy mają 320 px.
- **Fix B**: Dać kolumnie nazwy dolną granicę (np. `minmax(min(40%, max-content), max-content)`) i pozwolić tekstowi znacznika zawijać się w wąskiej karcie.
  - Strength: Naprawia czytelność od razu, także na `/kitchen-sink`.
  - Tradeoff: Kolejna zmiana układu w F-08 i nowe wzorce `view-feed` oraz `kitchen-sink-*`. Zawijanie znacznika zmienia też komponent z fazy 2.
  - Confidence: MED — zachowanie zawijanego znacznika przy 320 px nie jest sprawdzone.
  - Blind spot: Wpływ na `StanceText` w `PostAssessment` i na wzorce 375 px.
- **Decision**: FIXED (Fix A) — odstępstwo zapisane w `plan.md` (faza 3, „Odstępstwo: siatka celów w karcie posta”) i w opisie PR. Właściciel obejrzał zrzuty karty przy 320 px 2026-11-22: nazwa celu się rozjeżdża, ale akceptuje to do S-51+, który naprawi wąski wiersz posta.

### F3 — Gałąź i opis PR nie mówią o stanie po akceptacji

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: PR #199 (opis), commit `81f666b`
- **Detail**: `81f666b` (odhaczenie 3.8) nie jest wypchnięty, więc PR kończy się na `11e4b36`. Opis PR nadal mówi „Do akceptacji właściciela”, choć właściciel zaakceptował wygląd 2026-11-21. Opis nie wspomina zmiany siatki z F2 ani poprawek F1–F5 z pierwszego przeglądu (nowy wzorzec `dark-375/state-motyw-hover.png`). `81f666b` ma `Refs: #199` (PR) zamiast issue `#197`.
- **Fix**: Wypchnąć gałąź i poprawić opis PR (akceptacja, odstępstwo siatki, poprawki z przeglądu), z `Refs #197`.
- **Decision**: FIXED — `81f666b` był już na `origin`, kolejne commity są wypchnięte. Opis PR #199 podaje akceptację właściciela, odstępstwo siatki, restyl panelu powiadomień, obie flagi Chromium, przebiegi CI, dowód ze środowiska PR i oba raporty przeglądu (`Refs #197`). Historii commitów nie przepisano.

### F4 — Dowód kryterium 3.7 to tylko jedno zdanie

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: [plan.md:499](../plan.md#L499), PR #199, issue #197
- **Detail**: Plan zapisuje wyniki pomiarów w środowisku PR jako dowód (wzór: weryfikacja S-36, `evidence/pr-env/README.md`). Folder zmiany nie ma takiego pliku. Opis PR i komentarz w issue mają po jednym zdaniu, bez rodzin z CDP, statusów i typów plików, także dla feedu po zalogowaniu. Ten przegląd powtórzył tylko część anonimową, z wynikiem zielonym.
- **Fix**: Dopisać krótki plik `evidence/pr-env.md` z wynikami obu przejść (rodziny z CDP, statusy i `Content-Type` plików `woff2`) albo dodać je w komentarzu PR przy następnej weryfikacji.
- **Decision**: FIXED — [evidence/pr-env.md](../evidence/pr-env.md) ma wyniki obu przejść na webie z `e0737de`: anonimowego `/login` i feedu z sesją jednorazowego konta. Nagłówki rysuje Newsreader, etykiety Hanken Grotesk, a trzy pliki `woff2`, w tym latin-ext, przychodzą jako `200 font/woff2`. Sekrety zostały w zmiennych i w usuniętym pliku tymczasowym.

### F5 — Martwe klasy po usuniętych regułach

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: [szczur-radar-web/src/views/ActivateView.vue:164](../../../../szczur-radar-web/src/views/ActivateView.vue#L164), [szczur-radar-web/src/views/AdminView.vue:425](../../../../szczur-radar-web/src/views/AdminView.vue#L425)
- **Detail**: `003fd78` usunął reguły `.form .problem` i `.sources .sources-error`, ale klasy `problem` i `sources-error` zostały w szablonach. Nic w `src/`, `e2e/` ani `visual/` ich nie używa.
- **Fix**: Usunąć oba atrybuty `class` z szablonów.
- **Decision**: FIXED — oba atrybuty `class` usunięte. Żaden test, styl ani lokator ich nie używał.

### F6 — Panel powiadomień ma nowy wygląd spoza kontraktu i `z-index: 1`

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: [szczur-radar-web/src/components/NotificationMenu.vue:198](../../../../szczur-radar-web/src/components/NotificationMenu.vue#L198)
- **Detail**: Panel dostał `--surface`, krawędź `--line` i `--shadow-overlay`, jak `.popover` w `prototype.css:1623-1630`. Kontrakt fazy 3 tego nie wymienia, ale to mieści się w jego intencji. Panel zachował `z-index: 1`, choć `style.css` ma dla tej warstwy `--z-overlay: 20`.
- **Fix**: Ustawić `z-index: var(--z-overlay)` i dopisać restyl panelu do opisu PR.
- **Decision**: FIXED — panel ma `z-index: var(--z-overlay)`. To jedyny `z-index` w aplikacji, więc piksele się nie zmieniają. Restyl panelu jest opisany w PR.

## Triage

Triage zakończony 2026-11-22. Wszystkie ustalenia są naprawione (F1 rozszerzonym Fix A, F2 przez Fix A, F3–F6). Żadne nie zostało pominięte, zaakceptowane jako ryzyko ani zapisane jako lekcja. Po poprawkach kryteria 3.4–3.7 przechodzą: lokalny `visual:docker`, CI na PR (11/11) i środowisko PR.
