---
date: 2026-11-19T20:06:29+01:00
researcher: Claude (Sonnet 5.5)
git_commit: 0ed25375dfdf4e39dc55c0d3db7be142d211747e
branch: feature/ui-design-system
repository: szczur-radar
topic: "F-07 ui-design-system: co istniejący fundament, bramy testowe, wcześniejsze decyzje i dane narzucają projektowi UI/UX całej aplikacji"
tags: [research, codebase, ui, design-system, tokens, theme, visual-regression, e2e, mockups, F-07]
status: complete
last_updated: 2026-11-19
last_updated_by: Claude (Sonnet 5.5)
---

# Research: F-07 `ui-design-system` — grunt pod projekt UI/UX całej aplikacji

**Date**: 2026-11-19T20:06:29+01:00
**Researcher**: Claude (Sonnet 5.5); podagenci: lokalizowanie na Haiku 5.5 (2), analiza na Sonnet 5.5 (2)
**Git Commit**: 0ed25375dfdf4e39dc55c0d3db7be142d211747e (`docs(roadmap): v6.1 …`)
**Branch**: feature/ui-design-system
**Repository**: szczur-radar

## Research Question

`change.md` nie zawiera pytania, więc wynika ono z roadmapy: F-07 „Projekt UI/UX całej aplikacji” (`context/foundation/roadmap.md:193-206`, Change ID `ui-design-system`) ma dać w `context/foundation/ui-design.md` zasady, architekturę informacji, tokeny, komponenty i prototypy każdego ekranu M-5 (obie motywy, 375 px), z makiet `ui-ux-proposals.md` jako materiałem wejściowym. Badanie odpowiada na pytanie przygotowujące plan:

> Co w obecnym SPA, w bramach testowych (F-05/F-06/S-46), we wcześniejszych decyzjach i w danych API jest faktem, który projekt musi uwzględnić — jakie ograniczenia, jakie dane można uczciwie pokazać na prototypach i co zostaje nierozstrzygnięte?

Zakres: tylko odczyt; bez buildów, testów i uruchamiania aplikacji. Badanie nie wybiera wyglądu ani nie rozstrzyga decyzji właściciela.

## Summary

1. **Stan dzisiejszy** (inspekcja `src/`): 7 nazwanych tras + przekierowanie `*`; nagłówek `AppHeader` renderują osobno cztery widoki (Feed, PostDetail, Admin, RunDetail), a `/login`, `/activate` i `/kitchen-sink` go nie mają (`router/index.ts:34-55`, `FeedView.vue:165`, `PostDetailView.vue:138`, `AdminView.vue:412`, `RunDetailView.vue:154`). Brak biblioteki UI, ikon i czcionek (`package.json`); jedyna ikona w kodzie to inline SVG dzwonka (`NotificationMenu.vue:117-130`); jedyny `box-shadow` jest na sztywno (`NotificationMenu.vue:225`); w `src/` nie ma `transition`/`animation` (wg podagenta; patrz Luki). Krój to `system-ui` bez `@font-face` (`style.css:49`).
2. **Tokeny** (`style.css`, 198 linii): 12 ról kolorów w obu motywach (paleta A z S-46), skale typografii (5 rozmiarów), odstępów (`--space-0…5`, 2–24 px) i promieni (2); brak tokenów line-height, font-weight, cieni, szerokości i breakpointów. Role `--surface`, `--stance-neutral`, `--stance-mixed` nie mają konsumenta w widokach i komponentach (`REPOSITORY_GUIDELINES.md` mówi to samo; potwierdza grep podagenta).
3. **Bramy to twarde ograniczenia projektu** (sekcja „Ograniczenia”): kontrast ≥4,5:1 dla każdej roli tekstowej w `PAIRS` jako hex 3/6 cyfr w jednym `style.css`; zakaz `prefers-color-scheme` w CSS; skan literałów na 17 plikach z `CLEANED`; 56 baseline'ów przy tolerancji 0 pikseli renderowanych tylko w kontenerze Playwright z aliasem fontu; strażnik fontu wymaga `['Liberation Sans']`; E2E zakłada dokładnie jeden `banner` i `main` oraz konkretne etykiety.
4. **Krój pisma i paleta A to dziś decyzje bez właściciela w dokumentach zmian**: S-46 wyłączył typografię (`plan.md:41` archiwum S-46), a plan F-06 przypisał ją do S-46 (`visual-regression/plan.md:92`); roadmapa v6.1 przenosi je do F-07 (`roadmap.md:204`, decyzja: użytkownik).
5. **Dane**: strumień + inspektor i czytnik stoją na dzisiejszym API; katalog celów, proporcje stanowisk, przegląd dnia, filtr dat, wyszukiwanie w treści, sortowanie i ukrywanie nieinwestycyjnych wymagają nowych tras API (S-52…S-59) — brak ich wśród tras kontrolerów. Elementy makiet łamiące PRD/kontrakt: nazwy kanoniczne i tickery, scalanie odmian, jedna etykieta stanowiska na wpis, trend „↑”, „warte przeczytania”, „Zapisane”, status pipeline'u dla zwykłego użytkownika.
6. **Pytanie 7 roadmapy** (Analizy, Wątki, Ostatnie obserwacje, „Przejdź do źródła ↓”, „↑”) formalnie przeszło do F-07 i nie jest rozstrzygnięte; źródła dają dla każdego z nich tylko to, że nie mają FR lub łamią decyzję właściciela (szczegóły niżej).

## Detailed Findings

### 1. Obecne SPA — inwentarz (stan na `0ed2537`)

- **Trasy** (`src/router/index.ts:34-55`, meta `:25-32`, strażnik `:60-72`): `/login` i `/activate` publiczne; `/`, `/posts/:id` z sesją; `/admin`, `/admin/runs/:id` z rolą admin; `/kitchen-sink` publiczna tylko w `import.meta.env.DEV` (`:42-53`); `*` → `/`. `App.vue` to samo `<RouterView />`.
- **Widoki** (linie ≈): `AdminView` 790, `KitchenSinkView` 829, `RunDetailView` 442, `PostDetailView` 286, `FeedView` 261, `ActivateView` 223, `LoginView` 132. `AdminView` ma sekcje h2: „Nowy przebieg”, „Dni bez danych”, „Jakość ocen (7 dni)”, „Ostatnie przebiegi”, „Nieudane posty”.
- **Komponenty** (11 w `src/components/`): `AppButton`, `AppHeader`, `FeedFilter`, `NotificationBell`, `NotificationMenu`, `PostAssessment`, `PostCard`, `StanceText`, `StatusBadge`, `StatusMessage`, `ThemeToggle`. `AppHeader` zawiera nawigację („Posty”, dla admina „Panel admina”), e-mail, dzwonek (tylko admin), przełącznik motywu i „Wyloguj” (`AppHeader.vue:33-47`).
- **Tokeny** (`style.css`): jasny `:root` `:11-66`, ciemny `:root[data-theme='dark']` `:68-82` (tylko role kolorów + `color-scheme`). Skala typografii 14/16/20/32/44 px; baza `font: 18px/145%`, poniżej 1024 px root 16 px (`:63-65`); odstępy 2/4/8/12/16/24 px (`:39-44`); promienie 8 i 6 px; `--sans`/`--mono` systemowe (`:49,51`). `font-synthesis: none` (`:58`).
- **Niespójność istniejąca w kodzie**: komentarz w `style.css:164-169` mówi, że pola tekstowe dzielą styl globalny, ale selektor obejmuje tylko `date`/`search`/`select` (`:170-182`); pola tekstowe mają własne `input {}` w `LoginView.vue:99`, `ActivateView.vue:202`, `FeedFilter.vue:100` (podagent; nie sprawdzano osobno).
- **Ikony/logo**: favicon `public/favicon.svg` (fiolet) jako jedyny plik w `public/`; brak komponentu logo (tekst „Szczur Radar” jako `.eyebrow`); strzałka `←` jako znak tekstowy (`RunDetailView.vue:156`, `PostDetailView.vue:140`).
- **Kitchen sink**: 11 sekcji h2 (Tokeny, Przyciski, Komunikaty, Znaczniki, Dzwoneczek, Pola, Filtr, Posty, Stanowisko, Ocena posta, Motyw) (`KitchenSinkView.vue:596-711`); nie ma w nim wspólnych stanów ładowania/pustej listy/błędu (S-48 dopiero je tworzy).

### 2. Ograniczenia narzucone przez bramy (twarde dla F-07 → F-08)

- **Kontrast** (`styleContract.test.ts:26-49,157-162`): 19 par × 2 motywy = 38 przypadków, próg 4,5:1; pary dla `--text`, `--text-h`, `--accent`, `--success`, `--warning`, `--destructive`, `--stance-neutral`, `--stance-mixed` na `--bg` i `--surface`, `--on-accent` na `--accent`, `--text-h`/`--text` na `--highlight` (archiwum S-46 `palette.md:24-49` mówi o 19 parach). `role()` rzuca, gdy wartość nie jest hexem 3/6 cyfr (`:79-84`): role w `PAIRS` nie mogą być `oklch()`, `color-mix()`, `var()` ani hexem z alfą. Test czyta wyłącznie `./style.css?raw` (`:13`), jasny motyw z pierwszego `:root` (`:75`), ciemny z dokładnie `:root[data-theme='dark']` (`:76`).
- **Czego kontrast nie obejmuje**: `--border`, `--border-strong`, `--ring` nie są w `PAIRS`; kontrast nietekstowy (WCAG 1.4.11) nie ma testu (podagent; potwierdza lista `PAIRS`). axe sprawdza `color-contrast` tylko dla stanu domyślnego (wniosek podagenta z `visual/axe.ts`).
- **Zakaz `prefers-color-scheme` w CSS** (`styleContract.test.ts:164-168`); jedyne wystąpienie w kodzie to skrypt `index.html:21` (zweryfikowane grepem: brak w `style.css`).
- **Skan literałów**: dotyczy tylko 17 plików z `CLEANED` (`styleContract.test.ts:108-126`, policzone); `LoginView.vue` i `style.css` poza listą (komentarz `:104-107`). `LITERAL` odrzuca hex, `rgb(a)`, `hsl(a)`, `oklch(`, wartości arbitralne i klasy palety (`:129-130`); `OFF_SCALE` odrzuca `px`/`rem` w `font-size`, `margin*`, `padding*`, `gap`, `letter-spacing`, `border-radius` (`:137-138`). Nowy plik trzeba dopisać do `CLEANED` ręcznie.
- **Motyw**: dwa motywy (`Theme = 'light' | 'dark'`, `theme.ts:7`); skrypt w `index.html:13-26` ma być klasycznym `<script>` przed modułem (`theme.test.ts:81-90`); przycisk „Ciemny motyw” z `aria-pressed` w `banner` jest pinowany przez `e2e/theme.spec.ts:25,40,73` i `AppHeader.test.ts:49,55`. Trzeci motyw (np. wysoki kontrast) lub zmiana nazwy atrybutu/klucza wymagają zmiany mechanizmu i testów.
- **Zrzuty** (`visual/`): 56 baseline'ów — `light-375` 22, `dark-375` 22, `light-1280` 6, `dark-1280` 6 (policzone `ls`); 6 widoków × 4 projekty + 11 sekcji katalogu i 5 stanów × 2 projekty 375 px (`targets.ts:20,58-142,155-165,218-254`). `threshold: 0`, `maxDiffPixels: 0` (`playwright.visual.config.ts:53-54`); config odmawia innej platformy niż linux/amd64 (`:9-14`); w CI brakujący baseline jest czerwony. Nazwa sekcji h2 w katalogu = nazwa pliku baseline'u, a `baselines.spec.ts:16-51` wymaga zgodności w obie strony.
- **Font w bramie**: `visual/fonts.conf` mapuje tylko `system-ui`/`sans-serif` → Liberation Sans i `ui-monospace`/`monospace` → Liberation Mono; `font.spec.ts:31-43` wymaga, żeby nagłówek „Logowanie” i etykieta „Hasło” miały dokładnie `['Liberation Sans']`. Wnioski (nie zaobserwowane uruchomieniem): (a) lokalnie hostowany webfont zostanie wyrenderowany w kontenerze, ale złamie ten strażnik, więc F-08 musi go zmienić świadomie; (b) nazwany krój bez webfontu (np. `'Inter', system-ui`) zrenderuje się w bramie jako Liberation Sans i zostanie zielony, choć użytkownik zobaczy inny krój; (c) `serif` w `h1` na `/login` zrobi strażnika czerwonym. Dodatkowo `font-synthesis: none` (`style.css:58`) wymaga plików dla używanych wag (400 i 600: `style.css:89`, `AppHeader.vue:74`), a polskie znaki wymagają subsetu latin-ext.
- **axe** (`visual/axe.ts:7`): tagi `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`, `wcag22aa`, bez reguł best-practice, bez wyjątków; na 6 widokach w 4 projektach i na katalogu w 2. Przycisk z samą ikoną wymaga nazwy dostępnej (reguła `button-name`).
- **E2E** (`e2e/*.spec.ts`, `e2e/fixtures.ts`): testy zakładają dokładnie jeden `banner` na stronach zalogowanych i jeden `main`; w `banner`: „Wyloguj”, e-mail, „Panel admina”, dzwonek „Powiadomienia, N nieprzeczytanych”, „Ciemny motyw”. `post.spec.ts:23` klika link „Posty” w `main` — link o tej samej nazwie jest też w `banner`, więc przeniesienie nawigacji do `main` lub do paska bocznego wewnątrz `main` rozmnoży dopasowanie. Zmiana etykiet (np. „Posty”, „Filtruj”, nagłówki h2 admina) dotyka E2E, testów jednostkowych, `visual/targets.ts` (`ready`) i `dev/mock-api.js`.
- **Ukryte sprzężenie**: wysokość pól `calc(1.45rem + 14px)` (`style.css:170-175`) zakłada `line-height: 145%`, padding przycisku `6px 14px` i border 1 px — zmiana bazy, interlinii lub paddingu przycisku wymaga zmiany formuły, inaczej pola przestają być równe przyciskom.
- **Hosting**: `Caddyfile` nie ma żadnej dyrektywy `header` (grep: brak trafień dla `header`, `Content-Security-Policy`); fallback SPA `try_files {path} /index.html` (`Caddyfile:~38`) zwraca `index.html` z 200 także dla brakującego pliku fontu, więc błędna ścieżka fontu nie zgłosi się jako 404. W `src/`, `index.html`, `dev/` nie ma odwołań do zewnętrznych zasobów fontów (podagent). Zewnętrzny CDN fontów odpada przez determinizm zrzutów (tolerancja 0 pikseli), nie przez CSP. Gdyby F-07 zaproponował CSP, inline-skrypt motywu w `index.html:13-26` wymaga hasha lub nonce.

### 3. Wcześniejsze decyzje (kontekst planu)

- **Paleta**: S-46 wybrał wariant A (akcent niebieski; jasny `#1d4fa8`, ciemny `#7fb0ff`) decyzją właściciela 2026-11-17, bez poprawek ról (`context/archive/2026-11-17-light-dark-theme-switch/palette/palette.md:58-60`). Zakres ról w S-46: paleta + `--surface`, `--highlight`, `--stance-neutral`, `--stance-mixed`; wprost bez typografii i odstępów (`plan-brief.md:22`); typografia, odstępy >24 px, cienie i siatka „do S-47 i S-53” (`plan.md:41`) — dziś wskazanie nieaktualne wobec roadmapy v6.1.
- **Brak decyzji o kroju pisma** w `light-dark-theme-switch/` (przeszukane przez podagenta) i w audycie `post-m4-ui-readiness-audit/research.md` (typografia jest tam tylko na liście braków). Plan F-06 pisze: „Typografia należy do S-46” (`visual-regression/plan.md:92`) — sprzeczne z `light-dark-theme-switch/plan.md:41`; roadmapa v6.1 rozstrzyga organizacyjnie: F-07 (`roadmap.md:204`).
- **Brak standardów projektowych w `context/`**: `context/foundation/ui-design.md` nie istnieje (sprawdzone `ls`); grep po `Nielsen`, `design system`, `heurystyk` nie dał trafień poza zapowiedzią F-07; WCAG występuje wyłącznie jako kryterium kontrastu/axe (m.in. `archive/2026-10-10-admin-panel-ui-hardening/plan.md:180`).
- **Decyzje właściciela z 2026-11-15** (`post-m4-ui-readiness-audit/research.md:347-361`): wszystkie cztery układy w M-5; agregacja po znormalizowanym tekście anchora, odmiany osobno, bez rozpoznawania spółek; filtr po dacie i wyszukiwanie w treści: tak; „Zapisane” i status przebiegów dla usera: nie; „Warto przeczytać”/„Wątek dnia”/„Aktywny wątek” tylko jawnie liczbowe; trend i jedna etykieta stanowiska na wpis odpadają; nieinwestycyjne domyślnie ukryte; z nice-to-have tylko sortowanie.
- **Roadmapa v6.1** (`roadmap.md:92`): F-07 zmienia sposób pracy wycinków — każdy wycinek UI wdraża swój widok według `ui-design.md` na tokenach z F-08, a opis PR pokazuje zrzut F-06 obok prototypu (`:224`). F-07 jest „dokumentacją i prototypami bez kodu aplikacji, więc wzorce F-06 się nie zmieniają”; zmieniają się dopiero w F-08 (`:208-219`).
- **Kontekst F-05/F-06**: zegar E2E stały na `FIXED_NOW`; kontener `mcr.microsoft.com/playwright:v1.65.1-noble` (`e2e-harness/plan-brief.md:28`, wersja = `@playwright/test`); zrzuty tylko w nim (`REPOSITORY_GUIDELINES.md`, sekcja UI i Deployment).

### 4. Dane i API wobec makiet i ekranów M-5

- **Post w feedzie i szczegółach ma jeden kształt** (`src/api/posts.ts:81-98`, `src/contract/post.json`): źródło, kanał, typ, czas, tytuł/treść/autor/link (nullable), ocena V2 (`output`: trafność, modyfikatory, flagi, cele ze stanowiskiem i `assertiveness`, tezy z cytatem i atrybucją) (`posts.ts:36-69`). Cytat to `{document, field, quote}` bez offsetu (`posts.ts:50-54`); kontrakt wymaga, by cytat po normalizacji białych znaków był ciągłym podciągiem pola (`context/foundation/llm-assessment/contract-v2.md` §10, wg podagenta — nie czytano osobno). Odpowiedź nie ma: id źródłowego (jest tylko w `/raw`), języka, jednej etykiety stanowiska na wpis, nazwy kanonicznej celu/tickera, nazwy wyświetlanej autora.
- **Lista**: `GET /api/posts?page&size&q&kind&direction`, `size` 1–100, stała kolejność `publishedAt DESC, id DESC` (`PostController.java:87-92`, `PostRepository.java:25`); `q` dotyczy tekstu anchora celu (ILIKE, bez `unaccent`), nie tytułu/treści (`PostFilter.java:47-55`); bez filtra feed zwraca każdy post `assessed`, także nieinwestycyjne i odsiew (javadoc `PostController.java:74-86`). Spośród tras kontrolerów podagent nie znalazł trasy katalogu celów, agregacji stanowisk, przeglądu dnia ani sąsiadów (poprzedni/następny).
- **Tabela ekranów** (`dziś` = tylko praca w SPA; `nowe API` = wymaga wycinka):
  - Strumień + inspektor (S-51): dziś — źródło/kanał/czas/tytuł, cele i stanowiska (z „ostrożnie”), tezy z cytatami, link, JSON, filtry, paginacja, `total`; nowe API — ukrywanie nieinwestycyjnych (S-52), sortowanie (S-54), filtr dat (S-55), wyszukiwanie w treści (S-56).
  - Czytnik z podświetleniem (S-53): dziś — treść i cytaty do dopasowania; brak offsetów, wybór wystąpienia ustala plan S-53; „poprzedni/następny” liczy SPA po bieżącej liście.
  - Katalog + karta celu (S-57, S-58): surowiec jest w `targets[].anchor.quote`; liczby i proporcje (wzrostowe/spadkowe/pozostałe) liczy nowe API.
  - Przegląd dnia (S-59): zależy od S-55 i S-58; PRD wymaga jawnie liczbowych wyborów (FR-039).
  - Admin, przebieg, logowanie/aktywacja, powiadomienia (S-49, S-50): dziś — pełne kontrakty (`run.json`, `runs-page.json`, `assessment-quality*.json`, `missing-days.json`, `failed-posts-page.json`, `notifications*.json`, `user.json`, `activation-lookup.json`).
- **Co z makiet łamie PRD lub kontrakt** (FR-011a `prd.md:132/139`, Non-Goals `prd.md:369-370`, decyzje 2026-11-15, kontrakt V2 §14): nazwy kanoniczne i tickery („PKN · spółka”, „SPX · indeks”), scalanie odmian („Orlen” = „PKN”), hierarchia „Universe / Polskie spółki”, jedna etykieta stanowiska na wpis, trend „↑”, „teza z wypowiedzi”/„wątek dnia” wybrane jako „wartościowe”, „Zapisane”, „Pipeline gotowy” dla zwykłego użytkownika, „Wątki” jako komentarze (`prd.md:365`).
- **Pytanie 7 (`roadmap.md:445`)** — co o każdym wiadomo ze źródeł: „Analizy” — brak pojęcia i FR (najbliższe: katalog celów FR-037); „Wątki” — Non-Goal, jeśli to komentarze, lub FR-039, jeśli „wątek dnia” (wybór liczbowy); „Ostatnie obserwacje” — brak FR i danych (najbliższe inne pojęcie: świeżość per źródło `/api/freshness`); „Przejdź do źródła ↓” — na karcie celu nie ma jednego adresu, dla wpisu odpowiada FR-010 (`webUrl`); „↑” przy odsetku — sprzeczne z „migawką bez trendu” (FR-038) i decyzją właściciela.
- **Warianty danych na prototypach**: istnieją — wiele celów i stanowisk w jednej ocenie (`dev/mock-api.js:411-433`), wszystkie kierunki (sekcja „Stanowisko” katalogu), „ostrożnie” (`assertiveness=hedged`), post bez oceny z odsiewu (mock wariant 4, `posts-page.json`), dwa źródła i słowa typu posta (`labels.ts:92-95`, `ITEM_TYPE_WORDS`; `rules.test.ts:152-166` pilnuje, by słów Wykopu nie użyć dla Reddita), `MODE`-y listy (data/loading/error/empty/…), 10 wariantów przebiegów. Nie istnieją w atrapie (wg podagenta): ocena `not_investment_related` (jest tylko w katalogu), feed z wieloma dniami i zróżnicowanymi proporcjami (mock powtarza ten sam 6-elementowy zestaw ze stałymi stanowiskami), odmiany tego samego celu, powtórzony cytat w treści, cel równy kanałowi (R-39), długie tytuły, duża liczba celów. Atrapa zwraca `configured: true` dla wszystkich źródeł (`mock-api.js:651`), a `src/contract/sources.json` ma Reddita `false` (`:8`).

## Code References

- `szczur-radar-web/src/style.css:11-82` — tokeny jasne i ciemne (paleta A); `:170-182` — wspólne pola formularzy
- `szczur-radar-web/src/styleContract.test.ts:26-49,79-84,108-138,164-168` — pary kontrastu, parser ról, `CLEANED`, `LITERAL`/`OFF_SCALE`, zakaz media query
- `szczur-radar-web/index.html:13-26` i `src/theme.ts:7,51-102` — mechanizm motywu przed pierwszym renderem
- `szczur-radar-web/visual/font.spec.ts:31-43`, `visual/fonts.conf` — strażnik fontu i alias
- `szczur-radar-web/playwright.visual.config.ts:9-14,53-54` — odmowa innej platformy, tolerancja 0
- `szczur-radar-web/visual/targets.ts:58-142,155-165,218-254` — widoki, sekcje, stany baseline'ów
- `szczur-radar-web/src/components/AppHeader.vue:33-47` i `src/router/index.ts:34-55` — nagłówek per widok, trasy
- `szczur-radar-web/src/api/posts.ts:36-98`, `src/contract/post.json` — kontrakt posta i oceny V2
- `szczur-radar-web/dev/mock-api.js:411-440,651` — warianty ocen, źródła atrapy
- `szczur-radar-web/Caddyfile` — brak dyrektyw `header`; fallback SPA
- `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:74-92`, `posts/PostFilter.java:47-55` — lista i filtr
- `context/foundation/roadmap.md:92,193-224,445` — F-07, F-08, pytanie 7
- `context/foundation/prd.md:132,139,285-295,328,369-370` — FR-011a, FR-035–040, NFR E2E, Non-Goals

## Architecture Insights

- Cały język wizualny dziś żyje w jednym pliku tokenów, który jest zarazem jedynym wejściem testu kontrastu; podział na pliki lub nowy format kolorów oznacza zmianę testu (`styleContract.test.ts:13,75-76`).
- Bramy są szyte pod stosunkowo wąski zestaw: hex w dwóch motywach, systemowy krój, inline SVG, brak animacji. Wszystko poza tym (webfont, trzeci motyw, kolory w `color-mix`, pozycje ikon) to świadoma zmiana bramy w F-08, nie obejście.
- Nagłówek jako część widoku (nie powłoki) jest założeniem E2E i `AppHeader.test.ts`; S-47 to zmieni, więc F-07 powinien opisać powłokę tak, by dało się zachować lub świadomie przenieść locatory `banner`/`main`.
- Zdolności prototypów są ograniczone danymi: wszystko, co S-52…S-59 doda przez nowe API, na prototypie jest „obietnicą wycinka”, nie stanem istniejącym.

## Historical Context (from prior changes)

- `context/archive/2026-11-17-light-dark-theme-switch/palette/palette.md:58-60` — wybór palety A (2026-11-17): zachowuje się; F-07 ma otwartą kwestię, czy zostaje (`roadmap.md:204`).
- `context/archive/2026-11-17-light-dark-theme-switch/plan.md:41` oraz `plan-brief.md:22` — typografia, siatka i cienie poza S-46 (wskazanie na S-47/S-53 jest dziś nieaktualne: roadmapa v6.1 → F-07/F-08).
- `context/archive/2026-11-16-visual-regression/plan.md:92` — „typografia należy do S-46” (sprzeczne z powyższym; ocena: sprzeczność dokumentów, rozstrzygnięta roadmapą v6.1).
- `context/archive/2026-11-16-visual-regression/reviews/impl-review.md:46` (F1) — domyślny próg 0,2 przepuszczał zepsuty hover; kod ma dziś 0 (`playwright.visual.config.ts:53-54`). Notatka w planie `plan.md:186` ma starą wartość (twierdzenie historyczne częściowo nieaktualne: próg; aktualne: 0 pikseli, 4 projekty, axe).
- `context/changes/post-m4-ui-readiness-audit/research.md:146-204,347-361` — luka makiet wobec API/PRD i decyzje właściciela; kotwice `prd.md:314`, `:126`, `:344`, `:340` cytowane w audycie, `ui-ux-proposals.md:50` i `roadmap.md` (S-59: `prd.md:311`) są nieaktualne (podagent; zweryfikowano, że Non-Goals to dziś `prd.md:369-370`).
- Reguły z `context/foundation/lessons.md`, które dotyczą tej zmiany: „Planuj weryfikację agent-first”, „Kryterium, które agent sprawdzi poleceniem, zapisuj jako Automated”, „Stronę sprawdza agent przez playwright-cli” oraz „Uzasadniaj zakres celem użytkownika, zanim dodasz mechanizm” (przy dodawaniu elementów makiet bez FR).

## Related Research

- `context/changes/post-m4-ui-readiness-audit/research.md` — audyt gotowości UI po M-4 (źródło decyzji z 2026-11-15)
- `context/archive/2026-11-17-light-dark-theme-switch/` — S-46 (mechanizm motywu, paleta)
- `context/archive/2026-11-16-visual-regression/`, `context/archive/2026-11-16-e2e-harness/` — bramy F-06 i F-05

## Open Questions

Nierozstrzygnięte, a wymagające decyzji w `/10x-plan` lub od właściciela (nie wynika z badania, jak je rozstrzygnąć):

1. **Czy projekt zmienia architekturę informacji** (ekrany, podział admina, katalog i karta celu w jednym widoku)? Jeśli tak, `/10x-roadmap` przecina S-47–S-59 (v6.2) (`roadmap.md:203`; owner: user).
2. **Krój pisma** (polskie znaki, licencja, hostowany lokalnie, wagi 400/600) i **czy paleta A zostaje** (`roadmap.md:204`). Skutek: zmiana `font.spec.ts` w F-08 i nowe 56 baseline'ów.
3. **Pytanie 7** (Analizy, Wątki, Ostatnie obserwacje, „Przejdź do źródła ↓”, „↑”) — rozstrzyga projekt F-07 z akceptacją właściciela.
4. **Forma i miejsce prototypów** (wymaganie: każdy ekran M-5, obie motywy, 375 px, w `context/foundation/ui-design.md`): roadmapa nie mówi, czy to HTML w repo, obrazy, czy renderowane w kontenerze Playwright; nie sprawdzono, jaka forma mieści się w regule „bez kodu aplikacji” (`roadmap.md:205`). Wiąże się z: czy prototypy mogą używać atrapy danych.
5. **Zakres danych na prototypach**: które warianty z listy „nie istnieją w atrapie” (wiele dni, zróżnicowane proporcje, odmiany celu, cel = kanał, `not_investment_related`) F-07 ma pokazać i skąd je wziąć.
6. **Niezrealizowane wybory z roadmapy wpływające na projekt**: domyślny okres i próg katalogu (S-57), pola sortowania (S-54), adres wpisu (S-51), wystąpienie cytatu do podświetlenia (S-53), semantyka „najczęściej omawiane” i „liczba celów ze stanowiskiem” (FR-039), czy `not_expressed`/`unclear` to „stanowisko”.
7. **Powłoka vs. locatory E2E** (S-47): jak zachować jeden `banner`/`main` i rozróżnialny link „Posty”; do ustalenia w planie F-08/S-47.

### Luki w pokryciu badania

- Nie uruchamiano aplikacji, testów, buildu ani `npm run visual:docker`; nie wiadomo, czy bramy są dziś zielone, ani jak wyglądają obecne zrzuty (nie otwierano PNG baseline'ów).
- Nie czytano w całości: widoków z wyjątkiem nagłówków/szablonów przez grep, `dev/mock-api.js` (780 linii), `rules.test.ts`, `mockApi.test.ts`, `ci.yml`. Liczby reguł CSS w widokach to przybliżenia.
- Niepotwierdzone: czy Playwright czeka na `document.fonts.ready` przed zrzutem; czy Vite wstawia małe fonty jako `data:` URI (dotyczy tylko ewentualnego `font-src`).
- Brak `transition`/`animation` w `src/` i oświadczenie, że `prefers-reduced-motion` nie jest obsłużone: z raportu podagenta, bez własnego grepa.
- Treść `contract-v2.md` (§6, §10, §14) cytowana za podagentem; pozostałe numery linii w `PostController.java` i `WykopPosts.java` za audytem.
- Zweryfikowano własnoręcznie: liczba baseline'ów, kotwice `prd.md:295,328,369-370`, brak dyrektywy `header`/CSP w `Caddyfile`, oczekiwanie strażnika fontu, lista `CLEANED` (17), brak `prefers-color-scheme` w `style.css`, brak `context/foundation/ui-design.md`.
