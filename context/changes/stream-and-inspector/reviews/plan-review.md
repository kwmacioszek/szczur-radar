<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Lista postów z oceną w panelu bocznym (S-51)

- **Plan**: context/changes/stream-and-inspector/plan.md
- **Mode**: Deep
- **Date**: 2026-11-24
- **Verdict**: REVISE → SOUND (after triage)
- **Findings**: 0 critical, 5 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
19/19 paths ✓, symbols ✓ (watcher `FeedView.vue:219-246`, `NotificationMenu.vue:78-95`, tokens, `formatDateTime`), brief↔plan ✓, Progress↔Fazy ✓ (7/6/5/6 wierszy). vue-router 5.3.1: `FeedView` zostaje tą samą instancją przy `/posts` ↔ `/posts/:id` (brak `:key` na `RouterView`); watcher `[page, filter]` odpala, ale wychodzi na `sameFilter` (`FeedView.vue:229`).

## Findings

### F1 — Faza 1 pomija pliki, które przekierowanie `/` zepsuje

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1, #1 i #4
- **Detail**: `e2e/auth.spec.ts` oczekuje `'/login?redirect=/'` (:60), `'/login?redirect=/?page=1'` (:71), `'/?page=1'` (:76), `'/'` (:92); `AppShell.test.ts` mockuje `PostDetailView` (:21) i oczekuje rekordu/nazwy `home` (:112, :206); `AppBar.test.ts:12,18` używa `name: 'home'`. Żaden z tych plików nie jest w planie.
- **Fix**: Dopisać `e2e/auth.spec.ts`, `AppShell.test.ts`, `AppBar.test.ts` do plików fazy 1 (`/` → `/posts` w asercjach, `home` → `posts`); mock `PostDetailView` usunąć w fazie 2.
- **Decision**: FIXED (Fix in plan)

### F2 — Atrapa nie ma kilku dni na pierwszej stronie

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Faza 1, #4
- **Detail**: `publishedAt` = 2026-10-12 20:00Z − n·17 min (`dev/mock-api.js:448`), `FIXED_NOW` = 2026-10-20. Strona 0 to jeden dzień warszawski (12 października); dwa dni dopiero na `?page=1`; „Dziś”/„Wczoraj” nigdy w E2E ani zrzutach. Przycięcie „nad N-tym regionem dnia” niemożliwe na stronie 0.
- **Fix A ⭐ Recommended**: Test dni na `?page=1`, zrzut przycięty nad stronicowaniem, „Dziś”/„Wczoraj” tylko w Vitest (`groupByDay`)
  - Strength: Atrapa i `mockApi.test.ts` bez zmian, zgodnie z zakresem.
  - Tradeoff: Zrzut `view-posts` pokaże jeden dzień, inaczej niż prototyp.
  - Confidence: HIGH — daty wyliczone z kodu atrapy.
  - Blind spot: Wysokość zrzutu przy 50 wierszach niezmierzona.
- **Fix B**: Przesunąć daty atrapy bliżej `FIXED_NOW`
  - Strength: E2E i zrzut jak prototyp.
  - Tradeoff: Zmiana atrapy poza zakresem; kaskada wzorców i asercji z datami.
  - Confidence: MED — skala zależności niesprawdzona.
  - Blind spot: Wpływ na `mockApi.test.ts` i istniejące wzorce.
- **Decision**: FIXED (Fix A)

### F3 — Nierozstrzygnięty sposób rysowania trasy-dziecka

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Faza 2, #3
- **Detail**: Plan zostawia „przez `RouterView` albo bezpośrednio”. Rekord bez `component` w vue-router 5.3.1 daje ostrzeżenie `VUE_ROUTER_R0026` przy każdej nawigacji w dev i Vitest; wariant „bezpośrednio” dokłada logikę danych panelu do `FeedView`.
- **Fix A ⭐ Recommended**: Rekord `post` z komponentem `PostPanel` (SidePanel + PostInspector + dane), `FeedView` ma `<RouterView :items=… @closed=… />`
  - Strength: Idiomatyczne, bez ostrzeżeń, logika danych w osobnym pliku z testem.
  - Tradeoff: Jeden plik więcej; props/zdarzenia przez `RouterView`.
  - Confidence: HIGH — standardowy wzorzec vue-router.
  - Blind spot: Typowanie zdarzenia przez `RouterView` nie jest sprawdzane przez `vue-tsc`.
- **Fix B**: `FeedView` rysuje panel po `route.name === 'post'`, rekord z `components: {}`
  - Strength: Wszystko w jednym widoku.
  - Tradeoff: Obejście walidatora; `FeedView` puchnie.
  - Confidence: MED — działa według źródła, nieoczywiste.
  - Blind spot: Przyszłe wersje vue-router.
- **Decision**: FIXED (Fix A)

### F4 — Przeniesienie `/` → `/posts` odchodzi od `ui-design.md` bez zapisu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 4, #1
- **Detail**: „Istniejące testy, które zmiana łamie” w `ui-design.md` przypisuje zmianę adresu listy z `/` na `/posts` do S-59; plan robi to w S-51, a faza 4 zmienia `ui-design.md` tylko przy różnicy nazw.
- **Fix**: W fazie 4 #1 jawna edycja tego wiersza (S-51, `/` przekierowuje) i wzmianka w opisie PR do akceptacji właściciela.
- **Decision**: FIXED (Fix in plan)

### F5 — Trzy przyciski „Wyczyść filtry” naraz

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 3, #2 i #3
- **Detail**: „Wyczyść filtry” mają lista znaczników, pusty stan (`state-empty.html:149,155`) i otwarty panel; ścisły lokator w `main` padnie.
- **Fix**: W kontrakcie testu szukać „Wyczyść filtry” w liście „Włączone filtry” albo w regionie pustego stanu; Vitest `FeedView` sprawdza, że oba czyszczą ten sam filtr.
- **Decision**: FIXED (Fix in plan)

### F6 — Każde otwarcie i zamknięcie panelu pobiera powiadomienia admina

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Performance Considerations
- **Detail**: `NotificationBell.vue:79-81` pobiera powiadomienia przy każdym `afterEach`; panel to nawigacja, więc u admina każde otwarcie/zamknięcie wysyła `GET` — wbrew zdaniu „panel z bieżącej strony nie wysyła żądania”.
- **Fix**: Pomijać pobranie, gdy `from` i `to` należą do ekranu postów, albo przyjąć koszt i poprawić zdanie w planie.
- **Decision**: FIXED (Fix in plan)

### F7 — Panel po odświeżeniu: nie wiadomo, kiedy zapada wybór „z listy czy z API”

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 2, #3
- **Detail**: Po odświeżeniu `/posts/:id?page=1` panel montuje się przy wczytującej się liście, więc rusza `fetchPost`, a chwilę później przychodzi lista z tym postem; plan nie mówi, czy wybór reaguje na listę.
- **Fix**: Wybór zapada raz przy otwarciu (albo zmianie `id`); lista w ładowaniu → panel wczytuje post sam i ignoruje późniejszą listę.
- **Decision**: FIXED (Fix in plan)

### F8 — Roadmapa zmieniona bez odświeżenia SVG

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 (pierwszy commit)
- **Detail**: `roadmap.md` ustawia S-51 na `planning`, a `tools/render_roadmap_svg.py:240` ma `proposed`; reguła repo wymaga aktualizacji skryptu i SVG.
- **Fix**: W pierwszym commicie fazy 1 zaktualizować status w `render_roadmap_svg.py` i uruchomić `python3 tools/render_roadmap_svg.py`.
- **Decision**: FIXED (Fix in plan)
