---
date: 2026-11-15T14:30:57+0100
researcher: kwmacioszek (Claude Opus 5.5)
git_commit: ef56ddc
branch: docs/post-m4-ui-readiness-audit
repository: kwmacioszek/szczur-radar
topic: "Audyt gotowości po M-4: na czym stanie nowe UI i Playwright w M-5"
tags: [research, audit, m-5, ui, spa, design-tokens, components, api-gaps, prd, e2e, playwright, mock-api, targets]
status: complete
last_updated: 2026-11-15
last_updated_by: kwmacioszek (Claude Opus 5.5)
last_updated_note: "Dopisane decyzje właściciela z 2026-11-15 (odpowiedzi na Open Questions 1–8)"
---

# Research: audyt gotowości po M-4 pod M-5

**Date**: 2026-11-15T14:30:57+0100
**Researcher**: kwmacioszek (Claude Opus 5.5)
**Git Commit**: ef56ddc (`main` po zamknięciu M-4, PR #173; kod = `ffbdf6e`, S-45)
**Branch**: docs/post-m4-ui-readiness-audit
**Repository**: kwmacioszek/szczur-radar

Ścieżki: `api/` = `szczur-radar-api/src/main/java/pl/szczurradar/api/`, `web/` = `szczur-radar-web/src/`, `fx/` = `szczur-radar-api/src/test/resources/fixtures/`. Mapa projektu i destylacja domeny z PR #174 (jeszcze nie na `main`) są cytowane z gałęzi `docs/pre-m5-discovery`.

## Research Question

Na czym stanie nowe UI/UX i testy E2E na Playwright w M-5: co jest gotowe, a czego brakuje? Cztery obszary:

1. **Fundament SPA.** Tokeny `style.css`, wspólne komponenty, kitchen sink, lista `CLEANED`, `AdminView.vue`. Czy wystarczą pod układy z `context/foundation/ui-ux-proposals.md` i gdzie widoki je omijają?
2. **Luki API i PRD wobec makiet.** Czytnik, Karta podmiotu, Strumień + Inspektor, Przegląd: które dane, trasy i pojęcia są, których nie ma, co wymaga zmiany PRD, a co jest sprzeczne z Non-Goals?
3. **Podłoga E2E.** Czy `dev/mock-api.js` (S-43) wystarczy pod Playwright: logowanie i sesja, tryby dla tras czytanych przez SPA, 7 stanów UI? Czego brakuje w infrastrukturze?
4. **Dane pod agregację po celu.** R-39 (kanał jako cel) i Q-15 (feed a `not_investment_related`) z destylacji: co wynika z kodu i czy R-39 da się zmierzyć bez produkcji?

Wynik ma być materiałem do decyzji właściciela, PRD v4 i wycinków M-5, a nie planem.

## Summary

**Odpowiedź: układy 1 i 3 (Czytnik, Strumień + Inspektor) stoją na dzisiejszym API, ale nie na dzisiejszym fundamencie SPA. Układy 2 i 4 (Karta podmiotu, Przegląd) wymagają zmiany PRD, a w części wprost łamią Non-Goals. Podłoga E2E istnieje jako atrapa, ale bez żadnej infrastruktury Playwrighta.**

- **Fundament SPA.** Wystarcza na karty i listy w jednej kolumnie, czyli na to, co SPA robi dziś. Brakuje:
  - **warstwy układu:** `web/App.vue` to sam `<RouterView />`, a każdy widok sam stawia `AppHeader` i własny kontener;
  - **tokenów:** drugiej powierzchni, podświetlenia, szeryfu, trzeciego motywu, ręcznego przełącznika motywu, siatki i breakpointów;
  - **komponentów:** nawigacji, paneli, paska proporcji, kafelków liczb, katalogu celów, kolejki czytania, a także wspólnych stanów ładowania, pustej listy i „błąd + ponów”.

  Widoki omijają fundament w trzech miejscach: kontener strony kopiowany w 5 widokach, wzorzec stanów kopiowany w 4 widokach i sekcje `AdminView` wpisane inline (5 sekcji, ok. 168 linii stylu lokalnego). `LoginView` jest poza listą `CLEANED` i nie używa `AppButton` ani `StatusMessage`.
- **API.** Feed i post niosą wszystko, czego potrzebuje widok jednego wpisu: autora, czas, typ, źródło, kanał, link do oryginału, cele z anchorem, stanowiska per cel oraz tezy z cytatem i polem.
  - Podświetlenie cytatów jest wykonalne bez nowych danych, bo zapisany tekst to dokładnie wejście modelu. Cytat nie ma jednak offsetu, a przy powtórzeniach nie wiadomo, które wystąpienie podświetlić.
  - Do PRD v4 jest 6 kandydatów na nowe lub rozszerzone wymaganie, wymienionych w szczegółach.
  - Sprzeczne z `prd.md:344` są: karta podmiotu jako encja (ticker, scalanie Orlen = PKN), wybór „warto przeczytać” lub „wątek dnia”, trend i jedna etykieta stanowiska na wpis.
- **E2E.** Atrapa obsługuje wszystkie 20 tras czytanych przez SPA, ze stanami dane, pusto, 500, ładowanie (60 s), 401 i 403, oraz logowanie, wylogowanie i aktywację. Brakuje:
  - `@playwright/test`, konfiguracji, katalogu testów, skryptu i joba CI z przeglądarką;
  - atrapy jako modułu z trybami przełączanymi w teście i resetem stanu;
  - kilku wariantów: wygaśnięcia sesji w trakcie pracy, błędu i ładowania dla `/auth/me`, logowania i świeżości, niezależnego zawieszenia sekcji admina.
- **Dane pod agregację.**
  - **R-39:** na nagraniach (506 ocen, 674 cele) produkcyjny model (`gpt-6-luna`, prompt v2.5) ma **0** celów równych kanałowi posta. Wszystkie 6 naruszeń zrobiły tańsze modele na Wykopie. Reddit to tylko 20 postów i tylko model `luna`. Ryzyko z destylacji #1 jest więc realne przy zmianie modelu, a nie dziś. Nic go nie mierzy na produkcji.
  - **Q-15:** feed bez filtra pokazuje każdy post `assessed`, także `not_investment_related`, `unclear` i wynik odsiewu, a SPA je oznacza. Przy filtrze po celu posty bez celów znikają. Filtr `q` to ILIKE „zawiera” po `anchor.quote`, więc agregacja po tekście anchora rozbije jeden podmiot na odmiany („Orlen”, „Orlenu”, „#orlen”). Scalenie ich to już rozpoznawanie spółek.

## Detailed Findings

### 1. Fundament SPA

**Tokeny (`web/style.css`)**

- **Co jest:**
  - role kolorów `--bg`, `--text`, `--text-h`, `--accent`, `--on-accent`, `--success`, `--warning`, `--destructive`, `--ring`, `--border`, `--border-strong` (`web/style.css:9-21`);
  - skala fontów 14/16/20/32/44 px (`:23-27`), odstępów 2–24 px (`:29-34`), dwa promienie (`:36-37`);
  - fonty `--sans` i `--mono` (`:39-41`).
- **Motyw ciemny:** tylko `@media (prefers-color-scheme: dark)`, który redefiniuje 8 ról (`:58-69`). Nie ma `[data-theme]` ani ręcznego wyboru. Test kontrastu zna dokładnie dwa motywy i pary tylko na `--bg` oraz `--on-accent`/`--accent` (`web/styleContract.test.ts:22,28-36,61-64`).
- **Czego brakuje wobec makiet (stan faktyczny, nie projekt):**
  - druga powierzchnia (panel, karta, pasek boczny);
  - rola podświetlenia cytatu;
  - kolory dla stanowisk `neutral` i `mixed`;
  - motyw „papierowy”, font szeryfowy, rozmiar poniżej 14 px na etykiety wersalikami;
  - odstępy powyżej 24 px, cienie;
  - tokeny szerokości kontenera i breakpointów. Widoki wpisują `48rem`/`56rem` lokalnie: `FeedView.vue:217`, `PostDetailView.vue:194`, `RunDetailView.vue:292`, `AdminView.vue:624`, `KitchenSinkView.vue:709`.
- **Siatka:** żadnej siatki wielokolumnowej poza wnętrzem `PostCard` i `RunDetailView`.

**Komponenty (`web/components/`, 10 plików)**

- **Lista:** `AppButton`, `StatusMessage`, `StatusBadge`, `StanceText`, `PostCard`, `PostAssessment`, `FeedFilter`, `NotificationMenu`, `NotificationBell`, `AppHeader`.
- **Odpowiedniki elementów makiet:**
  - karta celu ze stanowiskiem, tezą i cytatem ≈ grupa w `PostAssessment` (`PostAssessment.vue:51-66`). Różnica: pokazuje wszystkie tezy, a makieta jedną;
  - wiersz strumienia ≈ `PostCard`. Cele są w nim jako `dl` (`PostCard.vue:34-39`), a karta nie ma stanu „wybrany” ani zdarzenia wyboru; jedyne działanie to link (`:31-32`);
  - wyszukiwanie ze stanowiskiem ≈ `FeedFilter`, z `select` zamiast przełącznika.
- **Uwagi:**
  - `StanceText` koloruje tylko `bullish` i `bearish` (`StanceText.vue:22-28`);
  - `AppHeader` ma dwa linki: „Posty” i „Panel admina” (`AppHeader.vue:34-37`).

**Kitchen sink (`web/views/KitchenSinkView.vue`)**

- Jest tylko w trybie dev (`web/router/index.ts:44-53`).
- **Pokazuje:** próbki kolorów, przyciski, komunikaty, znaczniki, 6 stanów `NotificationMenu`, pola, filtr, 7 wariantów `PostCard`, 6 kierunków stanowiska i 6 próbek oceny.
- **Nie pokazuje:**
  - `--border-strong` ani skal fontów, odstępów i promieni;
  - `AppHeader` ani `NotificationBell`;
  - żadnego układu strony;
  - stanów hover i focus-visible (te są tylko interaktywnie).

**Lista `CLEANED` i skan literałów (`web/styleContract.test.ts`)**

- Lista (`:95-112`) obejmuje 6 widoków i 10 komponentów.
- Poza nią są:
  - `style.css`: celowo, jako źródło tokenów (`:90-91`);
  - `App.vue`: nie ma stylu;
  - `LoginView.vue`: komentarz `:92-93` odsyła do zmiany `login-view-ui-hardening`, której nie ma w `context/changes/`.
- **Literały w `LoginView.vue`:** `gap: 16px` (`:87`), `gap: 6px` (`:94`), `font-size: 16px` (`:96`), `padding` i `border-radius` w px (`:101-112`). Kotwica w komentarzu („`LoginView.vue:77-102`”) jest nieaktualna.
- **`LoginView` nie używa wspólnych komponentów:**
  - surowy `<button>` zamiast `AppButton` (`:75-77`);
  - `<p class="error" role="alert">` zamiast `StatusMessage` (`:74`);
  - pola e-mail i hasła stylowane lokalnie (`:99-106`), bo globalna reguła pól nie obejmuje tych typów.

**Widoki**

- **`AdminView.vue`** (790 linii, szablon `:411-620`, styl lokalny `:622-790`):
  - pięć sekcji: nowy przebieg, dni bez danych, jakość ocen, ostatnie przebiegi, nieudane posty;
  - każdy wiersz listy, nagłówek sekcji z akcją (`:667-671`) i wzorzec ładowanie / błąd / pusto są wpisane inline, czterokrotnie;
  - ze wspólnych komponentów używa tylko `AppButton`, `StatusBadge` i `StatusMessage`.
- **`FeedView`, `PostDetailView`, `RunDetailView`:**
  - mają lokalne kontenery, `.loading`/`.empty` i parę `StatusMessage` + „Spróbuj ponownie”;
  - `.back` i `.part` są zduplikowane między `PostDetailView` (`:209,263`) a `RunDetailView` (`:307,360`);
  - kafelki liczb istnieją tylko jako `ul.figures` inline w `RunDetailView` (`:211-227`).

**Shell i router**

- `web/App.vue` to sam `<RouterView />`.
- Trasy są płaskie: `/login`, `/activate`, `/`, `/posts/:id`, `/admin`, `/admin/runs/:id`, `/kitchen-sink` (dev) i catch-all (`web/router/index.ts:34-55`). `RouteMeta` zna tylko `public` i `admin` (`:26-31`).
- Każdy zalogowany widok renderuje własny `AppHeader` (`FeedView.vue:165`, `PostDetailView.vue:138`, `AdminView.vue:412`, `RunDetailView.vue:154`). `NotificationBell` trzyma listę w store właśnie dlatego (`NotificationBell.vue:2-4`).

**Element makiety → odpowiednik dziś (skrót):**

| Element | Odpowiednik | Uwagi |
|---|---|---|
| wiersz strumienia | `PostCard` | jest |
| karta celu | `PostAssessment` | jest |
| filtr ze stanowiskiem | `FeedFilter` | jest |
| surowy JSON | `<details>` w `PostDetailView` (`:178-186`) | jest |
| link do oryginału | `PostDetailView.vue:160-169` | jest |
| górna nawigacja sekcji | `AppHeader` | częściowo |
| licznik wpisów i źródła | linia świeżości w `FeedView` (`:169-184`) | częściowo |
| stan „Pipeline gotowy” | `runs.ts`, tylko admin | częściowo |
| nawigacja boczna, układ paneli, okruszki, kolejka czytania, poprzedni / następny wpis | — | brak |
| chip celu, pasek proporcji, kafelki, katalog celów, lista celów z paskiem | — | brak |
| podświetlenie cytatu, sortowanie, wybór dnia poza `input[type=date]` | — | brak |
| motyw papierowy i szeryf | — | brak |

### 2. Luki API i PRD wobec makiet

**Co dziś daje API**

- **Lista `GET /api/posts`:**
  - parametry tylko `page`, `size` (1–100), `q`, `kind`, `direction` (`api/posts/api/PostController.java:87-92`);
  - stała kolejność `publishedAt DESC, id DESC` (`api/posts/PostRepository.java:25`);
  - brak parametru daty, źródła, sortowania i przeszukiwania treści.
- **Pola posta** (`PostItem`, `PostController.java:146-148`; `web/contract/post.json`):
  - są: `author`, `publishedAt`, `itemType`, `source`, `channel`, `webUrl`, `targets[].{kind, anchor{document, field, quote}, stance, reference}`, `claims[].{kind, statement, evidence{…}}`, `assessment.resultSource`;
  - brak: offsetu cytatu (`web/api/posts.ts:50-54` — `Quote` = `{document, field, quote}`), id źródłowego, tagów i języka (są w encji `api/posts/Post.java`, nie w odpowiedzi).
- **Podświetlenie cytatów bez nowych danych:**
  - Zapisany `title`/`body` to dokładnie wejście modelu: ten sam mapper w `WykopPosts.java:49-51`, `RedditPosts.java:49-53` i `AssessmentInputs.java:32-38`. Cytat jest podciągiem pola po normalizacji białych znaków (`contract-v2.md:289-305`).
  - Otwarte zostaje, które wystąpienie podświetlić przy powtórzeniach (§6.7, `contract-v2.md:225`), nakładanie anchora i cytatu oraz mapowanie pozycji po normalizacji na surowy tekst.

**Układy:**

| Układ | Ocena |
|---|---|
| **1. Czytnik** | Kolejka, treść, cele nad tytułem, autor · czas · źródło, karty celów i link do źródła mieszczą się w FR-007, FR-009 i FR-010 (`prd.md:109,119,123`). Brak: „wydania dnia” (filtr daty), „Zapisanych” i „Wątków” (pojęcia nie ma; jeśli to wątki komentarzy — Non-Goal `prd.md:340`). Etykieta „01 · ORLEN” jako nazwa kanoniczna to rozpoznawanie spółek; jako tekst anchora mieści się. |
| **2. Karta podmiotu** | Brak trasy katalogu celów i agregacji stanowisk; `total` liczy posty, nie stanowiska. „Powiązane wypowiedzi” mieszczą się w FR-011a (`/api/posts?q=`). Karta jako encja (nazwa kanoniczna, „Polskie spółki”, scalanie) łamie `prd.md:344`. Jedna „teza z wypowiedzi” na podmiot to wybór „wartościowego” (`prd.md:311,344`). |
| **3. Strumień + Inspektor** | Strumień, przełącznik stanowiska, licznik, wiersz z celami, inspektor z kartami celów i JSON mieszczą się w FR-006/007/009/011a. Brak: przeszukiwania „po treści” (FR-011a dopasowuje tylko anchor, `prd.md:127`), wyboru sortowania (FR-008/FR-011b, nice-to-have), „Zapisanych”, id źródłowego w odpowiedzi i statusu pipeline'u dla zwykłego użytkownika (FR-030 zostawia status adminowi, `prd.md:226-229`). Jedna etykieta stanowiska na wpis jest sprzeczna z kontraktem bez globalnego sentymentu posta (`prd.md:128`). |
| **4. Przegląd** | Wybór dnia, kafelki dnia, lista celów z paskiem — brak danych (data, agregacja). „Aktywny wątek… warto przejrzeć”, „Wątek dnia”, „Warto przeczytać” łamią `prd.md:344` i `prd.md:311`. Ticker łamie kontrakt §14 pkt 5–6 (`contract-v2.md:391-392`). Strzałka trendu łamie `prd.md:344` i FR-024/FR-025 (nice-to-have). Kafel „Źródła” mieści się w FR-030 (`prd.md:232`). |

**Kandydaci do PRD v4 (wymagają zmiany PRD; klasyfikacja, nie decyzja):**

1. Zawężenie listy do dnia lub zakresu dat publikacji (wybór dnia, archiwum, liczniki dnia). FR-011a filtruje tylko po polach oceny (`prd.md:127`), a FR-001 dotyczy zakresu przebiegu (`prd.md:83-84`).
2. Wyszukiwanie w treści posta. FR-011a zawęża dopasowanie do fragmentu wskazującego cel (`prd.md:127`).
3. Katalog celów jako lista tekstów anchorów, „bez rozpoznawania, o którą konkretnie spółkę chodzi” (`prd.md:127`). „CDR” i „CD Projekt” mogą być różne (`prd.md:129`).
4. Migawkowa agregacja stanowisk per cel (paski %), po tekście i bez czasu. Business Logic przypisuje agregację „w czasie per spółka” do FR-024/FR-025 poza MVP (`prd.md:313`).
5. „Zapisane”: zapisy per użytkownik. Brak FR; NFR o danych kontowych (`prd.md:300`) to dopuszcza.
6. Status pipeline'u dla zwykłego użytkownika. Zmienia rozstrzygnięcie przy FR-030 (`prd.md:226-229`).

Obok tego pole bez nowego FR: id źródłowe w odpowiedzi posta (mieści się w FR-009).

**Mieszczą się w istniejących FR (nice-to-have, gdy M-5 je weźmie):**
- sortowanie: FR-008 (`prd.md:114`), FR-011b (`:134`);
- nazwa wyświetlana: FR-020 (`:181`);
- eksport: FR-031 (`:235`), w makietach go nie ma.

**Sprzeczne z Non-Goals (`prd.md:344`) albo z doprecyzowaniami:**
- karta podmiotu jako encja: ticker, nazwa kanoniczna, scalanie Orlen = PKN;
- automatyczny wybór „warto przeczytać”, „wątek dnia” i jednej tezy na podmiot;
- trend;
- jedna etykieta stanowiska na wpis (`prd.md:128`);
- „Wątki”, jeśli znaczą komentarze (`prd.md:340`).

Wybór czysto liczbowy, np. „najwięcej wpisów”, byłby kandydatem do zmiany PRD pod inną nazwą.

**Filtr po celu a agregacja**

- Mechanika:
  - `q` to `anchor.quote ILIKE '%q%'` z escapowaniem (`api/posts/PostFilter.java:47-55`);
  - działa na najnowszej ocenie `ok`, a wszystkie warunki muszą dotyczyć jednego elementu `targets[]` (`PostRepository.java:115-121,137-143`);
  - nie ma `unaccent`: „zabka” nie znajduje „ŻABKA” (`PostsApiIT.java:406-414`);
  - post bez celów nigdy nie pasuje (`PostRepository.java:121`).
- Anchor jest dosłowny, z odmianą, slangiem i hashtagiem (`contract-v2.md:219`). Klucz „anchor” rozbija więc jeden podmiot na warianty, a „zawiera” łapie fałszywe trafienia przy krótkich zapytaniach.
- Proporcje per cel w samym SPA wymagałyby przejścia wszystkich stron po 100 postów i powtórzenia ILIKE w JS.

### 3. Podłoga E2E

**Atrapa (`web/dev/mock-api.js`, 701 linii)**

- Forma: funkcja `async page => {…}` dla `playwright-cli run-code`, bez eksportu (`:127`). Jeden `page.route` na `/api/` po `page.unrouteAll()` (`:467-468`). Nieobsłużona ścieżka daje 404 `not_found`.
- Tryby to stałe w tekście (ok. `:427-435`), zmieniane podmianą linii w kopii pliku:
  - `MODE`: data, loading (zawieszenie 60 s), error, empty, `*-next`, `raw-*`;
  - `AUTH`: admin, user, anonymous;
  - `LOGIN`: ok, invalid, limited;
  - `START`: accepted, busy, not_configured;
  - `ACTIVATION`: 6 wariantów;
  - `FRESHNESS`: 5 wariantów;
  - `MISSING`: 3 warianty;
  - `QUALITY`: 5 wariantów;
  - `NOTIFICATIONS`: 3 warianty.
- **Pokrycie tras:** wszystkie 20 tras czytanych przez SPA (z `web/api/*.ts`) są obsłużone. Nie każdy stan jest jednak osiągalny dla każdej trasy:
  - `/api/auth/me` nie ma błędu ani ładowania;
  - logowanie nie ma 500 ani zawieszenia;
  - `/api/freshness` nie podlega `MODE` i nie ma ładowania;
  - sekcje admina zawieszają się tylko razem (`MODE=loading`);
  - `/sources` daje zawsze `configured: true`.
- **Sesja:**
  - logowanie działa: `admin@szczur-radar.pl` = admin, każdy inny e-mail = user;
  - wylogowanie czyści sesję;
  - user dostaje 403 na `/api/admin/*`;
  - aktywacja przechodzi do `/login`;
  - **nie ma trybu wygaśnięcia sesji w trakcie pracy** (401 tylko przy `session === null`).
- **Stan:** sesja, przeczytane powiadomienia, uruchomione przebiegi i mutacje listy nieudanych żyją w jednej instalacji; reset tylko przez ponowną instalację.
- **Strażnik:** `web/contract/mockApi.ts` wykonuje tekst atrapy bez przeglądarki (`?raw` + eval).
  - `mockApi.test.ts` sprawdza zgodność z plikami kontraktu (klucze, typy, enumy), tabelę zachowań `ROWS`, bramkę sesji i zawieszenia na sztucznych zegarach.
  - Nie sprawdza: zachowania w prawdziwej przeglądarce, pełnej macierzy stała × trasa ani tego, że SPA pokazuje stan ładowania.
  - Średnia pewność: test czytany fragmentami.

**Infrastruktura**

- **Brakuje:**
  - `@playwright/test` w `web/package.json` (0 wystąpień);
  - `playwright.config`, katalogu `e2e/` i plików `*.spec.*` (`git ls-files`);
  - skryptu `e2e` (jest `dev`, `build` = `vue-tsc -b && vite build`, `preview`, `test`).
- **CI:** job `web-unit` (`.github/workflows/ci.yml:109-125`) robi `npm ci && npm test` bez przeglądarki; `web-container-smoke` buduje obraz. Joba E2E nie ma.
- **Testy widoków Vitest:** montują `createAppRouter` z `createMemoryHistory` i mockują `../api/*` przez `vi.mock`. Do E2E przechodzą tylko dane kontraktu (`web/contract/samples.ts`, pliki JSON).
- **Kitchen sink** jest tylko w dev, więc E2E na `vite preview` go nie zobaczy.
- **Rozbieżność:** nagłówek atrapy (`:116-117`) mówi, że `.playwright/cli.config.json` nie jest w repozytorium, a jest śledzony.

**Archiwum S-43** (`context/archive/2026-11-11-contract-bound-mocks/plan-brief.md:22,48-57`)

- Atrapa była celem „podłogi pod E2E”.
- Przepisanie na ESM lub middleware Vite oraz Playwright świadomie odłożono do M-5.
- Z przeglądu zostaje: pierwszy start przebiegu przy `running` nadal przyjęty (F2). Przegląd nazywa to stanem, którego produkcja nie wytworzy.

### 4. Dane pod agregację po celu

**R-39 (kanał jako cel)** — pomiar na nagraniach

- **Skrypt:** `context/changes/post-m4-ui-readiness-audit/evidence/channel_as_target.py`, uruchomiony z argumentem `fx/`.
- **Normalizacja:** trim, małe litery, bez wiodącego `#` i `r/`.
- **Wejścia nagrań:**
  - Wykop: `fx/llm/recordings/*` wiązane po `post_id` z `fx/assessor/gold/posts.json`;
  - Reddit: `fx/llm/scenario-days/reddit-*` wiązane po id z `fx/reddit/search-posts-*.json`.
- **Wszystkie nagrania mają prompt v2.5.**

| Zbiór | Oceny | Cele | Cel = kanał posta |
|---|---:|---:|---:|
| Wykop, deepseek-flash | 115 (+5 failed pominięte) | 126 | 2 |
| Wykop, gemini-flash-lite | 120 | 149 | 4 |
| Wykop, luna (`openai/gpt-6-luna`) | 120 | 161 | 0 |
| Wykop, luna-low | 118 (+2 failed pominięte) | 142 | 0 |
| scenariusze Wykop, luna | 13 | 20 | 0 |
| scenariusze Reddit, luna (14 r/inwestowanie, 6 r/stocks) | 20 | 76 | 0 |
| **razem** | **506** | **674** | **6** |

- **Naruszenia:** wszystkie 6 to `#gielda` albo `gielda` na kanale `gielda`, np. `fx/llm/recordings/deepseek-flash/gielda-2026-10-01-054.json`.
- **Ograniczenia:**
  - Reddit był oceniany tylko modelem `luna`;
  - r/ValueInvesting i r/investing nie występują w żadnym nagraniu;
  - próbka Reddita to 20 postów.
- **Wniosek:** na modelu produkcyjnym w tej próbce reguła jest przestrzegana. Ryzyko pojawia się przy zmianie modelu, którego nic na produkcji nie mierzy (R-15 i R-38 mierzą klasy błędów i język tez, nie cele).

**Q-15 (feed a `not_investment_related`)**

- **Feed bez filtra:** `GET /api/posts` → `PostRepository.findAssessedPage` (`PostRepository.java:123-131`). Dla pustego filtra bierze wszystkie posty `assessed` (`:94-96`), bez warunku na `relevance` ani na źródło wyniku. Javadoc: „Without a condition the feed is every assessed post” (`PostController.java:74-86`).
- **Wynik odsiewu:** wpis bez treści dostaje `AssessmentResult.ok` z `Relevance.UNCLEAR`, pustymi celami i flagą `INSUFFICIENT_CONTENT` (`api/agents/assessor/Prefilter.java:41-44`, `Assessor.java:71-75`). Feed pokazuje go z `resultSource = prefilter` (`PostController.java:161-167`).
- **SPA:**
  - plakietka trafności „nie inwestycyjny” / „niejasny” (`web/components/PostCard.vue:27`, `web/labels.ts:3-7`);
  - notka „bez treści, bez oceny modelu” dla odsiewu (`PostCard.vue:28`).
- **Przy filtrze po celu** posty bez celów znikają (`PostRepository.java:121`). Filtr nie sprawdza `relevance`.

## Code References

| Plik | Co tam jest |
|---|---|
| `szczur-radar-web/src/App.vue:1-3` | sam `<RouterView />`, bez warstwy układu |
| `szczur-radar-web/src/style.css:9-41,58-69` | tokeny i motyw ciemny tylko przez `prefers-color-scheme` |
| `szczur-radar-web/src/styleContract.test.ts:90-112` | lista `CLEANED` i powód wyłączenia `LoginView` |
| `szczur-radar-web/src/views/LoginView.vue:74-77,87-112` | surowy przycisk i komunikat; literały px |
| `szczur-radar-web/src/views/AdminView.vue:411-790` | 5 sekcji inline, ok. 168 linii stylu lokalnego |
| `szczur-radar-web/src/router/index.ts:26-55` | płaskie trasy; `RouteMeta` = `public`, `admin` |
| `szczur-radar-web/src/api/posts.ts:50-54` | `Quote` bez offsetu |
| `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/api/PostController.java:87-92` | parametry feedu |
| `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/PostRepository.java:25,94-96,115-143` | kolejność, feed bez filtra, dopasowanie celu |
| `szczur-radar-api/src/main/java/pl/szczurradar/api/posts/PostFilter.java:47-55` | ILIKE „zawiera” z escapowaniem |
| `szczur-radar-web/dev/mock-api.js:127,467-468` | forma atrapy (`run-code`), jeden `page.route` |
| `.github/workflows/ci.yml:109-161` | `web-unit` bez przeglądarki, smoke obrazu |
| `context/changes/post-m4-ui-readiness-audit/evidence/channel_as_target.py` | pomiar R-39 na nagraniach |

## Architecture Insights

- **SPA jest zbudowane wokół jednej kolumny i jednego widoku na trasę.** Wspólne są komponenty treści (post, ocena, stanowisko, status), ale nie układu (shell, nawigacja, panele) ani stanów (ładowanie, pusto, błąd z ponowieniem). Każda z czterech makiet zakłada trwały układ z nawigacją i panelami, więc pierwszą brakującą warstwą jest shell, a nie komponenty treści.
- **Kontrakt oceny wspiera widok jednego wpisu w całości.** Wszystko, czego potrzebują Czytnik i Inspektor, łącznie z podświetleniem, wynika z kontraktu V2 i dzisiejszej odpowiedzi posta. Luki API dotyczą przekrojów: daty, treści, katalogu i agregacji.
- **Granica PRD przebiega między „po tekście anchora” a „po spółce”.** Wszystko, co da się policzyć po dosłownym tekście celu, jest zmianą PRD. Wszystko, co wymaga tożsamości spółki (scalanie, ticker, trend) albo wyboru wartości („warto przeczytać”), jest sprzeczne z Non-Goals i z kontraktem §14–§15.
- **Atrapa jest strzeżona kontraktem, ale skrojona pod `playwright-cli`, nie pod runner testów.** Stałe w tekście i stan globalny wystarczają do ręcznych zrzutów, nie do izolowanych testów.

## Historical Context (from prior changes)

- `context/archive/2026-11-11-contract-bound-mocks/plan-brief.md:22,48-57`: atrapa jako podłoga pod E2E; ESM, middleware i Playwright świadomie odłożone do M-5.
- `context/foundation/roadmap.md:85` (v5): decyzja użytkownika z 2026-11-04, że cały Playwright, także smoke na obecnych widokach, idzie do M-5.
- `context/foundation/ui-ux-proposals.md:44-51`: wnioski z 2026-10-27 potwierdzone (układy 1 i 3 na dzisiejszych danych, 2 i 4 dotykają granic PRD). Kotwice `prd.md:314` i `prd.md:126` są nieaktualne; dziś to `prd.md:344` i `prd.md:129`.
- Destylacja domeny z PR #174 (`context/domain/domain-distillation.md`, gałąź `docs/pre-m5-discovery`): ranking #1 R-39 i pytania Q-15, Q-20. Ten audyt dodaje pomiar R-39 na nagraniach.

## Related Research

- `context/changes/post-m3-verification-audit/research.md`: audyt przed M-4 (strażnicy, kontrakt SPA ↔ API, atrapy).
- Wcześniejszego audytu UI nie ma; propozycje układu żyją tylko w `context/foundation/ui-ux-proposals.md`.

## Open Questions

Dla właściciela produktu, przed PRD v4 i `/10x-roadmap`:

1. **Które układy wchodzą do M-5?**
   - Czytnik i Strumień + Inspektor wymagają tylko pracy w SPA i drobnych pól.
   - Karta podmiotu i Przegląd wymagają nowych FR: katalogu celów, agregacji po tekście, filtra daty.
2. **Czy agregacja po dosłownym tekście anchora wystarczy** (z odmianami „Orlen” / „Orlenu” jako osobnymi pozycjami)? Czy M-5 ma przesunąć granicę „rozpoznawanie spółek” (`prd.md:344`)?
3. **Czy wchodzą nowe pojęcia?**
   - „Zapisane” (zapisy per użytkownik);
   - status pipeline'u dla zwykłego użytkownika (zmiana rozstrzygnięcia FR-030);
   - wyszukiwanie w treści.
4. **Elementy sprzeczne z Non-Goals** („Warto przeczytać”, „Wątek dnia”, trend, jedna etykieta na wpis): odrzucić, przemianować na czysto liczbowe czy zmienić Non-Goals?
5. **Motyw:** czy M-5 wybiera jeden kierunek wizualny (papierowy / ciemny / systemowy), czy wprowadza ręczny wybór motywu? Test kontrastu zna dziś dokładnie dwa motywy.
6. **Q-15:** czy feed domyślnie pokazuje posty `not_investment_related` i wynik odsiewu? To wpływa na każdy nowy widok listy.
7. **Q-20 / R-39:** czy mierzyć „kanał jako cel” na produkcji (tak jak język tez), skoro dziś ryzyko pojawia się tylko przy zmianie modelu?
8. **E2E:** czy atrapa ma zostać skryptem `run-code`, czy stać się modułem z trybami ustawianymi z testu? (S-43 odłożyło to do M-5.)

## Decyzje właściciela (2026-11-15)

Odpowiedzi na *Open Questions* powyżej, zebrane po audycie. Są wejściem do PRD v4 i do otwarcia M-5; badanie ich nie weryfikuje.

| Pytanie | Decyzja |
|---|---|
| 1. Które układy w M-5 | wszystkie cztery, w „uczciwie dużym” M-5. Kolejność: siatka E2E, potem kierunek wizualny i fundament (shell, wspólne stany, `LoginView`, rozbicie `AdminView`), potem Czytnik i Strumień + Inspektor, na końcu Karta podmiotu i Przegląd. Termin oddania projektu kursowego to styczeń 2027, a nie `hard_deadline: 2026-12-06` z `prd.md:15` |
| 2. Agregacja po celu | po znormalizowanym tekście anchora (wielkość liter, wiodący `#`); odmiany zostają osobno; rozpoznawanie spółek, trend i automatyczny wybór „wartych uwagi” zostają poza M-5 (`prd.md:344`) |
| 3. Nowe pojęcia | filtr po dacie publikacji i wyszukiwanie w tytule i treści: **tak**; „Zapisane”: nie; status przebiegów dla zwykłego użytkownika: nie (FR-030 bez zmian, `prd.md:226-229`) |
| 4. Elementy sprzeczne z Non-Goals | „Warto przeczytać”, „Wątek dnia” i „Aktywny wątek” stają się jawnie liczbowe (np. najczęściej omawiane cele dnia i najnowsze wpisy o nich); trend i jedna etykieta stanowiska na wpis odpadają |
| 5. Motyw | jasny i ciemny z ręcznym przełącznikiem, nowa paleta pod układy paneli; test kontrastu zostaje dla dwóch motywów |
| 6. Q-15 | w nowych widokach listy posty `not_investment_related` i wynik odsiewu są domyślnie ukryte, z przełącznikiem „pokaż wszystkie” |
| 7. Q-20 / R-39 | mierzyć „kanał jako cel” na produkcji jak język tez: okno 7 dni, podsumowanie nocy, `/admin`, alarm przy przejściu progu |
| 8. Atrapa pod E2E | decyzja techniczna dla `/10x-plan` wycinka E2E; nie rozstrzygana tutaj |
| Nice-to-have | tylko sortowanie (FR-008, FR-011b); bez „Zapisanych”, FR-020 i FR-031 |
