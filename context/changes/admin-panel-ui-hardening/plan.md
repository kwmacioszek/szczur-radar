# Hardening UI panelu admina — plan implementacji

## Overview

Panel `/admin` przechodzi z literałów i stylów elementów na kontrakt systemu projektowego tego repozytorium: role w `:root` (`src/style.css`), trzy współdzielone komponenty w `src/components/` i komplet 7 stanów. Akcent zmienia się z fioletu startera Vite na kobalt w obu motywach. Bramka wizualna to strona kitchen sink dostępna tylko w trybie dev plus zrzuty prawdziwego `/admin` z mockiem API; zabezpieczeniem dla kolejnego agenta jest blok UI w `REPOSITORY_GUIDELINES.md` i test Vitest odrzucający literały. Zakres: jeden widok (`AdminView` + wspólny `AppHeader`) plus globalne tokeny; Feed, Login i PostCard dostają tylko mechaniczną podmianę literałów na te same role.

## Current State Analysis

Źródło: `research.md` (zarzuty C1–C4, deferred D1–D2, macierz 7 stanów „przed”, zrzuty `ui-before/`).

- `:root` ma 4 kolory i `--sans` (`style.css:1-31`); jasne szarości są fioletowo podbarwione (`--text` `#6b6375`, `--text-h` `#08060d`), akcent `#aa3bff` ma 4.39:1 na białym. Ról stanu, obramowania, pierścienia i skali nie ma.
- `AdminView.vue` ma 5 linii z literałami kolorów i 18 linii z `px`; czyta tokeny 3 razy, importuje 1 komponent. Kolory stanów nie zmieniają się w ciemnym motywie (3.56–3.85:1).
- Przycisk i pole daty istnieją tylko jako globalne selektory elementów (`style.css:81-106`), bez hover i własnego fokusu; `LoginView.vue:89-111` ma drugi wariant. Komunikaty `role="status|alert"` są pisane ręcznie 9 razy w 4 plikach.
- Brak stanu ładowania: przed odpowiedzią API panel pokazuje stany puste (`AdminView.vue:48`, `:72`, `:161`, `:187`); po błędzie źródeł formularz jest martwy (`:112-121`).
- Nieudany krok przebiegu różni się tylko kolorem (`:174-178`, `:307-309`).
- Wejścia przez router są poprawne (`router/index.ts:34-60`). Brak lintera i narzędzia testów zrzutów; testy Vitest z jsdom (`vitest.config.ts`).

## Desired End State

- `/admin` w obu motywach i przy 375 px jest zbudowany wyłącznie z ról `:root` i komponentów `AppButton`, `StatusMessage`, `StatusBadge`; skan literałów w `AdminView.vue` i `AppHeader.vue` zwraca 0 linii (było 5 i 1).
- Każda para tekst/tło z tabeli tokenów ma ≥4.5:1 w obu motywach — pilnuje tego test Vitest.
- Panel nie twierdzi „Jeszcze nie było przebiegu.” ani „Brak nieudanych postów.” przed odpowiedzią API; po błędzie źródeł da się je wczytać ponownie bez przeładowania strony; „Uruchom” mówi, dlaczego jest wyłączony i kiedy trwa start.
- Nieudany krok i status przebiegu mają tekst, nie tylko kolor; „trwa” i „w kolejce” mają znacznik.
- Każda z 7 komórek macierzy stanów jest pokazana na zrzucie w `ui-after/` (kitchen sink lub `/admin` z mockiem) albo oznaczona N/A z powodem.
- Feed i Login mają ten sam ton (kobalt, chłodne szarości, statusy, hover i fokus z globalnych tokenów) bez zmian układu.
- `REPOSITORY_GUIDELINES.md` mówi następnemu agentowi, gdzie są tokeny i komponenty, że literały w widokach są zakazane i jak zrobić zrzuty; test skanujący pilnuje tego w CI.

### Key Discoveries:

- Obraz produkcyjny budowany jest przez `npm run build` (`szczur-radar-web/Dockerfile`), więc `import.meta.env.DEV` jest tam `false`, a gałąź z dynamicznym importem kitchen sink wypada z `dist/`.
- `tsconfig.app.json` ma `types: ["vite/client"]` — test może importować pliki przez `?raw` bez nowej zależności.
- `router/index.test.ts` sprawdza guard konkretnymi ścieżkami, nie listą tras; guard wpuszcza trasy `meta.public` także gdy `fetchMe` się nie powiedzie (`router/index.ts:37-41`, `auth/session.ts`).
- `AdminView.test.ts` używa selektorów `.run`, `.status`, `.alarm`, `.run-error`, `.failed-posts`, `form button`, `[role="status"]`, `[role="alert"]` i tekstu „Ponów zaznaczone” (`:93-163`).
- `FailureClass` w API ma 7 wartości (`agents/assessor/validation/FailureClass.java`): `provider_error`, `timeout`, `invalid_structured_output`, `schema_violation`, `invalid_reference`, `fabricated_evidence`, `domain_invariant_violation`; `mock-api.js` z researchu używa dwóch zmyślonych.
- Globalny `h1` 44px (`style.css:37-46`) w praktyce dotyczy tylko karty logowania (`.card`); widoki po zalogowaniu nadpisują go na 32px.

## What We're NOT Doing

- Migracja `FeedView`, `LoginView`, `PostCard` na nowe komponenty — następne zmiany `/10x-ui`: **`feed-view-ui-hardening`** (FeedView + PostCard, przed S-03 i S-04, żeby nowe widoki dziedziczyły kontrakt) i **`login-view-ui-hardening`**. Tu tylko mechaniczna podmiana 5 literałów kolorów na role.
- Układ listy przebiegów, agregowany sygnał awarii i automatyczne odświeżanie przebiegu w toku — S-06 (`pipeline-run-history-dashboard`), deferred D1 z researchu.
- Rozbijanie `AdminView` na komponenty sekcji z propsami.
- Tailwind, shadcn, stylelint, Playwright Test, Storybook — żadnych nowych zależności.
- Ręczny przełącznik motywu — motyw dalej z `prefers-color-scheme`.
- Zmiany w API i backendzie.

## Implementation Approach

Kolejność z `/10x-ui`: środowisko → wartości tokenów → jeden widok → stany, potem utrwalenie. Faza 1 nie zmienia wyglądu (poza `lang`), tylko daje miejsce na bramkę. Faza 2 zmienia wartości globalnie i jest jedyną fazą, która dotyka Feed/Login/PostCard. Faza 3 przenosi panel na komponenty i włącza test skanujący, faza 4 domyka stany, faza 5 zostawia regułę i weryfikuje w Railway PR env.

Trzy gwarancje spójności z widokami zwykłego użytkownika (decyzja planowania):

1. Globalny `button` w `style.css` wygląda jak wariant pomocniczy `AppButton` — zwykłe przyciski Feed już teraz mają wygląd komponentu.
2. Wariant główny `AppButton` ma kolory i promień obecnego przycisku logowania (tło `--accent`, tekst `--on-accent`, `--radius`) — migracja Login to podmiana elementu.
3. Komponenty nie mają nazw ani tonów z panelu admina (tony `success` / `warning` / `destructive` / `neutral` / `info`), żeby PostCard mógł użyć `StatusBadge` dla „wzrostowo” / „spadkowo”.

Proces (reguły repo, nie wiersze Progress): na starcie fazy 1 `/10x-implement` tworzy gałąź `feature/admin-panel-ui-hardening` ze świeżo pobranego `origin/main`, issue `admin-panel-ui-hardening: …` i PR z `Refs #<issue>`; folder zmiany idzie w pierwszym commicie. Implementacja, weryfikacja w PR env, `/10x-impl-review` z poprawkami i `/10x-archive` na tej samej gałęzi; merge robi człowiek.

## Critical Implementation Details

- **Niezwiązane lokalne zmiany.** Drzewo robocze ma niezacommitowane zmiany spoza tej zmiany (`.agents/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, `.gitignore`, `skills-lock.json`, `context/changes/openrouter-in-body-errors/`). Commity tej zmiany dodają tylko jej pliki (`git add <ścieżki>`), nigdy `git add -A`.
- **Jeden token, dwie role.** `--accent` jest kolorem tekstu na tle i tłem pod tekstem; tekst na akcencie zawsze przez `--on-accent` (biały w jasnym, `--bg` w ciemnym), nigdy przez stały kolor.
- **Mock nie może łapać modułów Vite.** Dopasowanie po ścieżce zaczynającej się od `/api/`; wzorzec `**/api/**` łapie też `/src/api/*.ts` i daje pustą stronę. `playwright-cli` czyta `.playwright/cli.config.json` z bieżącego katalogu — uruchamiać z katalogu głównego repo albo z `--config`.

## Phase 1: Środowisko i bramka

### Overview

Miejsce na bramkę wizualną bez zmiany wyglądu: trasa kitchen sink tylko w trybie dev, utrzymywany mock API w repo, poprawny język dokumentu.

### Changes Required:

#### 1. Język dokumentu

**File**: `szczur-radar-web/index.html`

**Intent**: Interfejs jest po polsku, a `lang="en"` każe czytnikowi ekranu czytać go z angielską wymową.

**Contract**: `<html lang="pl">`.

#### 2. Trasa kitchen sink tylko w dev

**File**: `szczur-radar-web/src/router/index.ts`, `szczur-radar-web/src/views/KitchenSinkView.vue` (nowy)

**Intent**: Stałe miejsce, które następny agent może otworzyć, z każdym komponentem w każdym stanie; w tej fazie szkielet z sekcjami (tokeny, przyciski, komunikaty, znaczniki, pola), wypełniany w fazach 2–4.

**Contract**: Trasa `{ path: '/kitchen-sink', name: 'kitchen-sink', meta: { public: true } }` z komponentem ładowanym dynamicznym importem, dodawana do `routes` wyłącznie gdy `import.meta.env.DEV`; w buildzie produkcyjnym gałąź wypada razem z napisem trasy. Widok renderuje stałe dane, nie woła API. Nie dodaje linku w `AppHeader`.

#### 3. Mock API w repo

**File**: `szczur-radar-web/dev/mock-api.js` (nowy)

**Intent**: Utrzymywane narzędzie do zrzutów `/admin` bez API, także dla kolejnych zmian `/10x-ui`; kopia `context/changes/admin-panel-ui-hardening/mock-api.js` zostaje w folderze zmiany jako dowód zrzutów „przed”.

**Contract**: Jedna funkcja `async page => {…}` dla `playwright-cli run-code --filename=…`, tryby `data` / `loading` / `error` / `empty`, dopasowanie po `pathname.startsWith('/api/')`. Wartości `failureClass` wyłącznie z `FailureClass.java`. Komentarz nagłówka z procedurą (dev server, `playwright-cli open`, `run-code`, `goto`, `set-color-scheme`, `screenshot`).

#### 4. Test trasy

**File**: `szczur-radar-web/src/router/index.test.ts`

**Intent**: Trasa kitchen sink jest osiągalna bez sesji w trybie testów (dev), a reszta guarda bez zmian.

**Contract**: Nowy przypadek: anonimowy użytkownik na `/kitchen-sink` zostaje na `/kitchen-sink`.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą, w tym nowy przypadek `/kitchen-sink`: `cd szczur-radar-web && npm test`
- Build produkcyjny przechodzi i nie zawiera kitchen sink: `cd szczur-radar-web && npm run build && ! grep -ri kitchen dist/`
- `index.html` ma `lang="pl"`: `grep -c 'lang="pl"' szczur-radar-web/index.html` zwraca 1
- `dev/mock-api.js` parsuje się jako funkcja i nie zawiera `transport_error` ani `quote_not_fragment`

#### Manual Verification:

- `npm run dev`: `/kitchen-sink` otwiera się bez logowania; `/admin` z `dev/mock-api.js` (tryb `data`) wygląda jak `ui-before/desktop-light.png`

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 2: Wartości tokenów

### Overview

Kontrakt wartości: role kolorów dla obu motywów, kobalt, chłodne szarości, skala pisma, odstępów i promieni; globalne style elementów na tokenach z hover i fokusem. Jedyna faza, która dotyka Feed, Login i PostCard.

### Changes Required:

#### 1. Role i skala w `:root`

**File**: `szczur-radar-web/src/style.css`

**Intent**: Jedno źródło wartości z nazwami ról; ciemny motyw przełącza się wyłącznie na warstwie tokenów. Nad blokiem komentarz: źródło wartości = ten plan (tabela poniżej, kontrasty liczone wzorem WCAG 2.x).

**Contract**: Wartości (jasny / ciemny w istniejącym bloku `@media (prefers-color-scheme: dark)`):

| Token | Jasny | Ciemny | Para i kontrast |
| --- | --- | --- | --- |
| `--bg` | `#fff` | `#16171d` | — |
| `--text` | `#5f6672` | `#9ca3af` | na `--bg`: 5.78 / 7.04 |
| `--text-h` | `#111318` | `#f3f4f6` | na `--bg`: 18.58 / 16.25 |
| `--accent` | `#2a5fc4` | `#86aef0` | na `--bg`: 5.94 / 7.94 |
| `--on-accent` | `#fff` | `#16171d` | na `--accent`: 5.94 / 7.94 |
| `--success` | `#188038` | `#4ade80` | na `--bg`: 5.02 / 10.26 |
| `--warning` | `#b06000` | `#fbbf24` | na `--bg`: 4.65 / 10.71 |
| `--destructive` | `#d93025` | `#f87171` | na `--bg`: 4.77 / 6.46 |
| `--ring` | `var(--accent)` | (dziedziczy) | — |
| `--border` | `color-mix(in srgb, var(--text) 25%, transparent)` | (dziedziczy) | linie podziału, dekoracyjne |
| `--border-strong` | `var(--text)` | (dziedziczy) | obramowanie pól formularza |

Skala: `--font-size-sm: 14px`, `--font-size-md: 16px`, `--font-size-lg: 20px`, `--font-size-xl: 32px`, `--font-size-display: 44px`; `--space-0: 2px`, `--space-1: 4px`, `--space-2: 8px`, `--space-3: 12px`, `--space-4: 16px`, `--space-5: 24px`; `--radius: 8px` (przyciski, pola, grupy pól), `--radius-sm: 6px` (małe znaczniki i chipy) — reguła promieni zapisana w komentarzu.

#### 2. Globalne style elementów na tokenach

**File**: `szczur-radar-web/src/style.css`

**Intent**: Bazowe elementy, z których korzystają wszystkie widoki, dostają stany z tokenów; globalny `button` = wariant pomocniczy `AppButton` (gwarancja 1).

**Contract**:
- `button`: obrys i tekst `--accent`, przezroczyste tło, `--radius`; widoczny hover sterowany tokenem (np. `color-mix` z `--accent`), tylko dla `:not(:disabled)`; `:disabled` jak dziś (`opacity`, `cursor`).
- `:focus-visible` dla przycisków, linków, pól i checkboxów: obrys w `--ring` z odstępem; bez usuwania fokusu dla myszy w sposób, który ukrywa go klawiaturze.
- `input[type='date']`: `--border-strong`, `--radius`, `--text-h`.
- `accent-color: var(--accent)` dla checkboxów.
- `h1` globalnie `--font-size-xl`; reguła 44px / 32px przy ≤1024 px przenosi się do `.card h1` (karta logowania bez zmiany wyglądu). Komentarz „placeholder home” przy `.page` poprawiony.

#### 3. Mechaniczna podmiana literałów w innych widokach

**File**: `szczur-radar-web/src/views/FeedView.vue:81`, `szczur-radar-web/src/views/LoginView.vue:104`, `:115`, `szczur-radar-web/src/components/PostCard.vue:98`, `:102`

**Intent**: Te same role w miejscach o tym samym znaczeniu — ciemny motyw i przycisk logowania (dziś 2.64:1) naprawiają się razem z panelem. Nic poza tymi liniami.

**Contract**: `#d93025` → `var(--destructive)`, `#188038` → `var(--success)`, `#fff` na przycisku logowania → `var(--on-accent)`.

#### 4. Test kontrastu tokenów

**File**: `szczur-radar-web/src/styleContract.test.ts` (nowy)

**Intent**: Zmiana wartości tokenu, która zepsuje kontrast, nie przejdzie CI.

**Contract**: Test czyta `style.css?raw`, wyciąga wartości hex ról z `:root` i z bloku ciemnego, liczy kontrast WCAG 2.x i wymaga ≥4.5:1 dla par z tabeli (`--text`, `--text-h`, `--accent`, `--success`, `--warning`, `--destructive` na `--bg`; `--on-accent` na `--accent`) w obu motywach. Brak tokenu = błąd testu.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą, w tym test kontrastu tokenów w obu motywach: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Skan literałów z `/10x-ui` na `FeedView.vue`, `LoginView.vue`, `PostCard.vue` zwraca 0 linii

#### Manual Verification:

- Zrzuty `/` i `/login` w obu motywach (`ui-after/`): ten sam ton co panel, przycisk logowania czytelny w ciemnym motywie, układ bez zmian
- Tab przez `/admin` i `/` w obu motywach: pierścień fokusu w kolorze akcentu na każdej kontrolce, hover widoczny na przyciskach

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 3: Panel na komponentach

### Overview

Trzy współdzielone komponenty i `AdminView` + `AppHeader` zbudowane wyłącznie z nich i z tokenów (C1, C2, C4 w części komponentu). Włącza się test skanujący — kontrola z `/10x-ui`.

### Changes Required:

#### 1. `AppButton`

**File**: `szczur-radar-web/src/components/AppButton.vue` (nowy)

**Intent**: Jeden przycisk z wariantem głównym i pomocniczym oraz stanem „w toku”, żeby główna akcja odróżniała się od reszty (C2), a stan startu był widoczny.

**Contract**: Renderuje natywny `<button>` (atrybuty `disabled`, nasłuchy przechodzą); `type` domyślnie `button` — `submit` tylko jawnie, bo przycisk wewnątrz formularza (np. „Spróbuj ponownie” w fieldsecie źródeł) inaczej wysłałby formularz. Propsy: `variant: 'primary' | 'secondary'` (domyślnie `secondary` = wygląd globalnego `button`), `busy?: boolean` (wyłącza przycisk, ustawia `aria-busy="true"`), `busyLabel?: string` (tekst w trakcie). Wariant `primary`: tło `--accent`, tekst `--on-accent`, `--radius`, hover z tokenów (gwarancja 2). Fokus z globalnego `:focus-visible`.

#### 2. `StatusMessage`

**File**: `szczur-radar-web/src/components/StatusMessage.vue` (nowy)

**Intent**: Jeden komunikat wyniku zamiast ręcznych `<p class="ok|error">`, wyraźniejszy niż sam kolorowy tekst (C2).

**Contract**: Prop `tone: 'ok' | 'error'`; `ok` → `<p role="status">`, `error` → `<p role="alert">`; treść przez slot. Wygląd: tło zabarwione tokenem tonu (`--success` / `--destructive` przez `color-mix`), lewa krawędź w kolorze tonu, tekst `--text-h` (kontrast z zabarwionym tłem ≥4.5:1), `--radius-sm`, odstępy ze skali.

#### 3. `StatusBadge`

**File**: `szczur-radar-web/src/components/StatusBadge.vue` (nowy)

**Intent**: Znacznik stanu, który zawsze niesie tekst, a kolor tylko go wzmacnia (C4); tony bez nazw z panelu (gwarancja 3).

**Contract**: Prop `tone: 'success' | 'warning' | 'destructive' | 'neutral' | 'info'` (`info` = `--accent`, `neutral` = `--text`), tekst przez slot (wymagany), `<span>` z `data-tone`; klasa z rodzica przechodzi na korzeń (selektor `.status` w testach). Obramowanie i tekst w kolorze tonu, `--radius-sm`, `--font-size-sm`.

#### 4. `AdminView` i `AppHeader` na tokenach i komponentach

**File**: `szczur-radar-web/src/views/AdminView.vue`, `szczur-radar-web/src/components/AppHeader.vue`

**Intent**: Usunąć wszystkie literały kolorów i zastąpić `px` skalą; „Uruchom” jako wariant główny, „Odśwież”, „Ponów zaznaczone” i „Wyloguj” jako pomocnicze; komunikaty przez `StatusMessage`; status przebiegu i kroki przez `StatusBadge`.

**Contract**:
- Mapowanie statusu przebiegu na ton: `succeeded` → `success`, `partial` / `interrupted` → `warning`, `failed` → `destructive`, `running` → `info`, `queued` → `neutral`.
- Krok przebiegu: udany jak dziś `etykieta (itemsOut)`; nieudany i w toku jako `StatusBadge` z tekstem statusu (`label('runStatus', step.status)`, np. „ocena (błąd)”).
- Zachowane selektory testów: `.run`, `.status` (na znaczniku statusu przebiegu), `.alarm`, `.run-error`, `.failed-posts`, `form button`, role `status` / `alert`.
- Obramowania przez `--border`, fieldset źródeł `--radius`.

#### 5. Etykiety `failureClass`

**File**: `szczur-radar-web/src/labels.ts`, `szczur-radar-web/src/api/posts.ts`, `szczur-radar-web/src/views/AdminView.vue`

**Intent**: Admin czyta polskie nazwy klas porażek zamiast surowych kodów; zamknięty zbiór jako typ (lekcja o enumach).

**Contract**: `type FailureClass` w `posts.ts` z 7 wartościami z `FailureClass.java`, użyty w `FailedPost.lastAttempt.failureClass` i `violations[].failureClass`. Zbiór `failureClass` w `labels.ts`: `provider_error` „błąd dostawcy”, `timeout` „przekroczony czas”, `invalid_structured_output` „niepoprawna struktura odpowiedzi”, `schema_violation` „niezgodność ze schematem”, `invalid_reference` „błędne odwołanie”, `fabricated_evidence` „zmyślony cytat”, `domain_invariant_violation` „naruszenie reguł kontraktu”. Nieznana wartość dalej pokazuje się bez zmian (`label()`).

#### 6. Test skanujący literały

**File**: `szczur-radar-web/src/styleContract.test.ts`

**Intent**: Literał koloru, klasa palety albo wartość arbitralna w oczyszczonym pliku nie przejdzie CI — kontrola z `/10x-ui`.

**Contract**: Lista plików importowanych przez `?raw`: `views/AdminView.vue`, `components/AppHeader.vue`, `components/AppButton.vue`, `components/StatusMessage.vue`, `components/StatusBadge.vue`, `views/KitchenSinkView.vue`, `views/FeedView.vue`, `views/LoginView.vue`, `components/PostCard.vue`; regex ze skanu `/10x-ui` (hex, `rgb(a)`, `hsl(a)`, `oklch`, `-[Npx|rem]`, klasy palety). Błąd wypisuje `plik:linia`. `style.css` (źródło tokenów) jest poza listą.

#### 7. Testy komponentów i widoku

**File**: `szczur-radar-web/src/components/AppButton.test.ts`, `StatusMessage.test.ts`, `StatusBadge.test.ts` (nowe), `szczur-radar-web/src/views/AdminView.test.ts`

**Intent**: Zachowanie komponentów i mapowanie stanów w panelu.

**Contract**: `AppButton`: wariant → klasa, domyślny `type="button"`, `busy` → `disabled` + `aria-busy` + `busyLabel`. `StatusMessage`: ton → rola. `StatusBadge`: ton → `data-tone`, tekst ze slotu. `AdminView`: istniejące przypadki bez zmian selektorów; nieudany krok ma tekst „błąd”; `failureClass` pokazuje polską etykietę.

#### 8. Kitchen sink: komponenty

**File**: `szczur-radar-web/src/views/KitchenSinkView.vue`

**Intent**: Stany komponentów w jednym miejscu.

**Contract**: Próbki ról kolorów; `AppButton` główny i pomocniczy × domyślny / wyłączony / w toku; `StatusMessage` ok i błąd; `StatusBadge` we wszystkich 5 tonach; pole daty i checkbox (zaznaczony, wyłączony). Hover i fokus na zrzutach wymuszane przez `playwright-cli` (`hover`, `press Tab`).

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą (komponenty, `AdminView`, test skanujący, kontrast): `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Skan literałów z `/10x-ui` na `AdminView.vue` i `AppHeader.vue` zwraca 0 linii (było 5 i 1)

#### Manual Verification:

- Kitchen sink w obu motywach: każdy komponent w każdym stanie, hover i fokus wymuszone; zrzuty w `ui-after/`
- `/admin` z mockiem (tryb `data`) w obu motywach i przy 375 px: „Uruchom” wyróżniony, statusy jako znaczniki z tekstem, `failureClass` po polsku

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 4: Stany widoku

### Overview

Domknięcie C3 i macierzy 7 stanów na poziomie widoku: ładowanie bez fałszywego stanu pustego, odzyskanie po błędzie źródeł, przyczyna wyłączenia i stan „w toku”.

### Changes Required:

#### 1. Ładowanie per sekcja

**File**: `szczur-radar-web/src/views/AdminView.vue`

**Intent**: Przed pierwszą odpowiedzią sekcja mówi, że się wczytuje, zamiast twierdzić, że nie ma danych (wzorzec z `FeedView.vue:14`, `:48`).

**Contract**: Osobny stan ładowania dla źródeł, przebiegów i nieudanych postów (prawda do pierwszej odpowiedzi); w trakcie „Wczytywanie…” i `aria-busy="true"` na sekcji; stan pusty tylko po udanej odpowiedzi z pustą listą; przy ponownym odświeżaniu istniejąca lista zostaje na ekranie.

#### 2. Źródła: miejsce, błąd i ponowienie

**File**: `szczur-radar-web/src/views/AdminView.vue`

**Intent**: Formularz nie skacze, gdy źródła dochodzą, i nie zostaje martwy po błędzie.

**Contract**: Wczytanie źródeł wydzielone do funkcji; domyślny wybór skonfigurowanych źródeł ustawiany tylko przy pierwszym udanym wczytaniu. Fieldset ma zarezerwowaną wysokość jednego wiersza także w trakcie ładowania i po błędzie. Błąd źródeł jako `StatusMessage` z przyciskiem „Spróbuj ponownie”; `refresh()` („Odśwież”) wczytuje ponownie także źródła, gdy wcześniej się nie udało.

#### 3. Przyczyna wyłączenia i stan „w toku”

**File**: `szczur-radar-web/src/views/AdminView.vue`

**Intent**: Admin wie, dlaczego nie może uruchomić przebiegu i że start trwa (C3, stan disabled).

**Contract**: Pod formularzem tekst pomocniczy powiązany z „Uruchom” przez `aria-describedby`: „Wybierz co najmniej jedno źródło.” (nic nie zaznaczono) albo „Żadne źródło nie jest skonfigurowane na serwerze.” (brak skonfigurowanych). „Uruchom” `busy` z `busyLabel` „Uruchamianie…”, „Ponów zaznaczone” z „Ponawianie…”. Stany puste po wczytaniu: „Jeszcze nie było przebiegu.” i „Brak nieudanych postów.” bez zmian treści.

#### 4. Testy stanów

**File**: `szczur-radar-web/src/views/AdminView.test.ts`

**Intent**: Regresja C3 nie przejdzie.

**Contract**: Przy nierozstrzygniętych żądaniach nie ma tekstów stanu pustego, jest „Wczytywanie…”; po błędzie źródeł „Spróbuj ponownie” wczytuje je i odblokowuje formularz; tekst przyczyny wyłączenia przy braku zaznaczenia; etykieta „Uruchamianie…” w trakcie startu.

#### 5. Macierz 7 stanów na zrzutach

**File**: `context/changes/admin-panel-ui-hardening/ui-after/` (zrzuty + `README.md`)

**Intent**: Dowód bramki wizualnej do przeglądu.

**Contract**: Zrzuty 1280×900 i 375×812, jasny i ciemny: `/admin` w trybach `data`, `loading`, `error`, `empty` (`dev/mock-api.js`) oraz kitchen sink. `README.md`: tabela 7 stanów → plik zrzutu albo N/A z powodem, plus porównanie z `ui-before/` i liczniki skanu przed/po.

### Success Criteria:

#### Automated Verification:

- Testy web przechodzą, w tym nowe testy stanów `AdminView`: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`

#### Manual Verification:

- `ui-after/README.md` pokazuje każdą z 7 komórek macierzy (default, hover, focus-visible, disabled, error, empty, loading) na zrzucie albo jako N/A z powodem, w obu motywach i przy 375 px
- Przy 1280×900 nagłówek „Ostatnie przebiegi” jest na tej samej wysokości w trybie `loading` i `data` (bez skoku układu)

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka.

---

## Phase 5: Utrwalenie i weryfikacja

### Overview

Reguła dla następnego agenta, lista kontrolna `/10x-ui` i sprawdzenie na prawdziwych danych w Railway PR env.

### Changes Required:

#### 1. Blok UI w regułach repo

**File**: `REPOSITORY_GUIDELINES.md` (dołączany przez `CLAUDE.md` / `AGENTS.md`, poza blokiem `@przeprogramowani/10x-cli`)

**Intent**: Następny agent wie, gdzie szukać, zanim napisze własny przycisk albo kolor.

**Contract**: Krótki blok w „Coding Style & Testing” (albo osobna sekcja UI): tokeny w `szczur-radar-web/src/style.css` `:root` (role i skala; ciemny motyw tylko na warstwie tokenów); komponenty w `src/components/` (`AppButton`, `StatusMessage`, `StatusBadge`) — sprawdź przed stworzeniem nowego, dodawaj tam; zakaz literałów kolorów, klas palety i wartości arbitralnych w widokach i komponentach; kitchen sink `/kitchen-sink` w `npm run dev`; `src/styleContract.test.ts` — dopisz plik po jego oczyszczeniu; zrzuty przez `szczur-radar-web/dev/mock-api.js` + `playwright-cli`. Do UI istniejącego widoku `/10x-ui`.

#### 2. Lista kontrolna i porównanie

**File**: `context/changes/admin-panel-ui-hardening/ui-after/README.md`

**Intent**: Wynik `ui-quality-checklist` przy zmianie, do przeglądu.

**Contract**: Każdy punkt listy z `.claude/skills/10x-ui/references/ui-quality-checklist.md` odhaczony albo N/A z powodem; wskazanie deferred (D1 → S-06, następne zmiany `feed-view-ui-hardening`, `login-view-ui-hardening`).

### Success Criteria:

#### Automated Verification:

- `REPOSITORY_GUIDELINES.md` zawiera blok UI z nazwami `src/style.css`, `src/components/`, `styleContract.test.ts` i `/kitchen-sink`
- Testy i build web przechodzą: `cd szczur-radar-web && npm test && npm run build`
- CI na PR jest zielone: `gh pr checks <PR>`

#### Manual Verification:

- `ui-after/README.md` zawiera odhaczoną listę `ui-quality-checklist` (lub N/A z powodem)
- `/admin` w Railway PR env (prawdziwe dane, zalogowany admin) w obu motywach i przy 375 px wygląda jak zrzuty z mocka; `/` i `/login` w tym samym tonie

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na potwierdzenie weryfikacji ręcznej przez człowieka. Potem `/10x-impl-review` i `/10x-archive` na tej samej gałęzi; merge robi człowiek.

---

## Testing Strategy

### Unit Tests:

- `styleContract.test.ts`: kontrast par tokenów w obu motywach (faza 2), brak literałów w oczyszczonych plikach (faza 3).
- Komponenty: `AppButton` (wariant, `busy`), `StatusMessage` (ton → rola), `StatusBadge` (ton, tekst).
- `AdminView`: dotychczasowe przypadki z tymi samymi selektorami; tekst nieudanego kroku; polska etykieta `failureClass`; stany ładowania, ponowienie źródeł, przyczyna wyłączenia, „Uruchamianie…”.
- Router: `/kitchen-sink` bez sesji w dev.

### Integration Tests:

- Build produkcyjny bez kitchen sink (`grep` na `dist/`); CI z prawdziwym Dockerfile web (`web-container-smoke`).

### Manual Testing Steps:

1. `cd szczur-radar-web && npm run dev`; z katalogu głównego repo `playwright-cli open`, `run-code --filename=szczur-radar-web/dev/mock-api.js`, `goto http://localhost:5173/admin`.
2. Dla trybów `data`, `loading`, `error`, `empty` (podmiana `MODE`): zrzut 1280×900 i 375×812, `set-color-scheme light` i `dark`.
3. Kitchen sink: zrzuty w obu motywach, `hover` na przycisku, `press Tab` do fokusu.
4. Tab przez cały panel: kolejność i widoczny fokus na każdej kontrolce.
5. PR env: `/admin`, `/`, `/login` na prawdziwych danych.

## Performance Considerations

Brak istotnych: kilka zmiennych CSS i trzy małe komponenty; kitchen sink nie trafia do bundla produkcyjnego.

## Migration Notes

Brak danych do migracji. Wycofanie = revert commitów tej gałęzi; tokeny są w jednym pliku.

## References

- Research: `context/changes/admin-panel-ui-hardening/research.md` (C1–C4, D1–D2, macierz „przed”)
- Zrzuty „przed”: `context/changes/admin-panel-ui-hardening/ui-before/`
- Kontrakt UI: `.claude/skills/10x-ui/SKILL.md`, `.claude/skills/10x-ui/references/ui-quality-checklist.md`
- Wzorzec ładowania: `szczur-radar-web/src/views/FeedView.vue:14`, `:48`
- Zbiór `FailureClass`: `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/validation/FailureClass.java`
- Granica z S-06: `context/foundation/roadmap.md` (S-06)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Środowisko i bramka

#### Automated

- [x] 1.1 Testy web przechodzą, w tym nowy przypadek `/kitchen-sink` — 75bd1a6
- [x] 1.2 Build produkcyjny przechodzi i nie zawiera kitchen sink — 75bd1a6
- [x] 1.3 `index.html` ma `lang="pl"` — 75bd1a6
- [x] 1.4 `dev/mock-api.js` parsuje się jako funkcja i nie zawiera `transport_error` ani `quote_not_fragment` — 75bd1a6

#### Manual

- [x] 1.5 `/kitchen-sink` otwiera się bez logowania; `/admin` z `dev/mock-api.js` wygląda jak `ui-before/desktop-light.png` — 75bd1a6

### Phase 2: Wartości tokenów

#### Automated

- [x] 2.1 Testy web przechodzą, w tym test kontrastu tokenów w obu motywach — 243c0e1
- [x] 2.2 Typecheck i build przechodzą — 243c0e1
- [x] 2.3 Skan literałów na `FeedView.vue`, `LoginView.vue`, `PostCard.vue` zwraca 0 linii — 243c0e1

#### Manual

- [x] 2.4 Zrzuty `/` i `/login` w obu motywach: ten sam ton, przycisk logowania czytelny w ciemnym, układ bez zmian — 243c0e1
- [x] 2.5 Tab przez `/admin` i `/` w obu motywach: pierścień fokusu w kolorze akcentu, hover widoczny — 243c0e1

### Phase 3: Panel na komponentach

#### Automated

- [x] 3.1 Testy web przechodzą (komponenty, `AdminView`, test skanujący, kontrast) — 44b468f
- [x] 3.2 Typecheck i build przechodzą — 44b468f
- [x] 3.3 Skan literałów na `AdminView.vue` i `AppHeader.vue` zwraca 0 linii — 44b468f

#### Manual

- [x] 3.4 Kitchen sink w obu motywach: każdy komponent w każdym stanie; zrzuty w `ui-after/` — 44b468f
- [x] 3.5 `/admin` z mockiem w obu motywach i przy 375 px: „Uruchom” wyróżniony, statusy z tekstem, `failureClass` po polsku — 44b468f

### Phase 4: Stany widoku

#### Automated

- [x] 4.1 Testy web przechodzą, w tym nowe testy stanów `AdminView` — a65ae7e
- [x] 4.2 Typecheck i build przechodzą — a65ae7e

#### Manual

- [x] 4.3 `ui-after/README.md` pokazuje 7 komórek macierzy w obu motywach i przy 375 px — a65ae7e
- [x] 4.4 Brak skoku układu między trybem `loading` i `data` przy 1280×900 — a65ae7e

### Phase 5: Utrwalenie i weryfikacja

#### Automated

- [x] 5.1 `REPOSITORY_GUIDELINES.md` zawiera blok UI
- [x] 5.2 Testy i build web przechodzą
- [x] 5.3 CI na PR jest zielone

#### Manual

- [x] 5.4 `ui-after/README.md` zawiera odhaczoną listę `ui-quality-checklist`
- [x] 5.5 `/admin` w Railway PR env w obu motywach i przy 375 px; `/` i `/login` w tym samym tonie
