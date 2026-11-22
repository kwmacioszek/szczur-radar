# S-49: logowanie i aktywacja na wspólnym fundamencie — Implementation Plan

## Overview

Logowanie (`/login`) i aktywacja konta (`/activate#<token>`) przechodzą na tokeny i wspólne komponenty z F-08, według zaakceptowanych prototypów `login` i `activate` (`context/foundation/ui-design/prototypes/screens/`) i kontraktu E2E z `context/foundation/ui-design.md`. Dochodzą trzy wspólne klocki ekranów publicznych: `AppBrand` (znak radaru i nazwa), `FormField` (etykieta, podpowiedź i błąd przy polu z poprawnym `aria-describedby`) oraz `AuthLayout` (strona z marką, przełącznikiem motywu i kartą). `LoginView` trafia na listę `CLEANED`, a wyjątek `.login-form` znika z `style.css`. Backend, kontrakty API i logika sesji oraz aktywacji nie zmieniają się.

## Current State Analysis

- `LoginView.vue` ma własny scoped CSS w `px` (`LoginView.vue:84-131`: przycisk, pola, błąd, odstępy), własne `input` i `button` zamiast `AppButton`, błąd jako goły `<p role="alert">` bez ikony i nie jest na liście `CLEANED` (komentarz w `src/styleContract.test.ts:117-122` wskazuje S-49 jako spłatę długu).
- `style.css:359-422` wyłącza pola formularza `.login-form` z globalnego stylu pól (`:where(:not(.login-form *))` w pięciu regułach i w `::placeholder`), więc po migracji logowania ten wyjątek traci sens.
- `ActivateView.vue` jest już na tokenach, `AppButton` i `StatusMessage`, ale błędy hasła (`PROBLEM_MESSAGES`, `ActivateView.vue:25-31`) pokazuje jednym komunikatem pod formularzem, a prototyp i kontrakt E2E wymagają błędu przy polu z ikoną i fokusu na błędnym polu. Podpowiedź i etykieta pola mają lokalne style (`ActivateView.vue:190-204`).
- Style etykieta/podpowiedź/błąd są powielone w `ActivateView.vue`, `KitchenSinkView.vue:1021-1066` i `FeedFilter.vue:90-115`; wiązanie `aria-describedby`/`aria-invalid` jest ręczne w każdym widoku.
- Marki nie ma jako komponentu: sprite ma `i-brand`, ale oba widoki publiczne piszą tekst `Szczur Radar` jako `eyebrow` (`.eyebrow`, `.page`, `.card`, `.lead` w `style.css:270-310` służą wyłącznie tym dwóm widokom). Ekrany publiczne nie mają przełącznika motywu, choć prototypy go mają, a kontrakt E2E wymienia przycisk „Ciemny motyw” na ekranie logowania.
- Atrapa API ma gotowe tryby: `LOGIN` (`ok`/`invalid`/`limited`) i `ACTIVATION` (`valid`/`invalid`/`policy`/`checking`/`sending`/`error`) (`dev/mock-api.js:615-640`). Obecne E2E (`auth.spec.ts`, `activation.spec.ts`, `theme.spec.ts:90-97`) szukają elementów po etykietach, które się nie zmieniają.
- S-47 (`app-shell-navigation`) pracuje równolegle w osobnym worktree i też potrzebuje znaku marki; ma dziś tylko `change.md`, więc nie ma konfliktu plików.

## Desired End State

- `/login` i `/activate` wyglądają jak prototypy w obu motywach i przy 375 oraz 1280 px: marka u góry, przełącznik „Ciemny motyw” w prawym górnym rogu, karta `--surface` z nagłówkiem h1, polami, komunikatem i pełnoszerokim przyciskiem głównym.
- Błąd logowania i limit prób to `StatusMessage` z ikoną; błąd hasła w aktywacji stoi przy polu z ikoną, pole ma `aria-invalid="true"` i `aria-describedby`, a fokus ląduje na błędnym polu.
- `LoginView.vue` jest na liście `CLEANED` i skan literałów go przepuszcza; w `src/` nie ma `login-form`; martwe style `.page`, `.card`, `.eyebrow`, `.lead` zniknęły z `style.css`.
- Wzorce F-06: `view-login` i `view-activate` w czterech projektach nowe, dochodzą `view-login-error` i `view-activate-error`, sekcja katalogu „Marka” nowa, „Pola” bez zmiany pikseli (albo zmieniona z uzasadnieniem). axe przechodzi także na stanach błędu.
- Weryfikacja: `npm test`, `npm run build`, `npm run e2e`, `npm run visual:docker` i `node tools/design-check/design-check.mjs app` są zielone.

### Key Discoveries:

- Wyjątek `.login-form` jest w `:where()`, więc usunięcie `:not(.login-form *)` nie zmienia specyficzności reguł pól (`style.css:373-422`).
- Router przekazuje e-mail po aktywacji w `history.state.activatedEmail`, nie w adresie (`LoginView.vue:13-16`, `ActivateView.vue:104`); token żyje tylko w fragmencie i jest usuwany z adresu w `onMounted` (`ActivateView.vue:107-114`). Tych dwóch mechanizmów nie ruszamy.
- `ThemeToggle` i `AppButton` same nie zależą od `AppHeader`, więc `AuthLayout` może użyć `ThemeToggle` bez powłoki S-47; stan motywu trzyma `src/theme.ts`.
- Kontrakt E2E ekranu `login`: przycisk „Ciemny motyw”, komunikat `alert`, „Nieprawidłowy e-mail lub hasło.”, „Zbyt wiele prób. …”; ekranu `activate`: za krótkie hasło daje błąd przy polu i fokus na polu, nieważny link daje komunikat i brak formularza (`ui-design.md`, sekcja „Kontrakt dla testów E2E”).
- Komponenty wspólne trafiają na `/kitchen-sink` i dostają wzorzec `kitchen-sink-<slug>.png` w projektach 375 px, a strażnik `baselines.spec.ts` wymaga zgodności plików z celami w obie strony (`REPOSITORY_GUIDELINES.md`, sekcja Screenshot comparison).

## What We're NOT Doing

- Backend, kontrakty API (`src/contract/`), `src/api/*`, logika sesji (`src/auth/session.ts`), router i jego strażnik.
- Wspólne stany widoków z S-48 (ładowanie szkieletem, pusta lista, błąd z ponowieniem): stany „sprawdzanie”, „link nieważny” i „nie udało się sprawdzić linku” zostają na `StatusMessage` i `AppButton`.
- Pasek aplikacji, nawigacja i `AppHeader` (S-47). `AppBrand` powstaje tu, S-47 go użyje; ewentualne opakowanie w odnośnik robi S-47.
- Migracja `FeedFilter` i `PostCard` na `FormField` (S-51 i dalej).
- Zmiany tekstów, nazw pól i przycisków („E-mail”, „Hasło”, „Zaloguj się”, „Nowe hasło”, „Powtórz hasło”, „Ustaw hasło”) oraz komunikatów błędów.
- Zmiany w `context/foundation/ui-design.md` i prototypach (nazwy komponentów są decyzją planu; projekt wizualny jest zaakceptowany).
- Nowe stany hover i fokus w `visual/targets.ts` (`STATES`).
- Rejestracja publiczna lub e-mail: prototyp logowania ma tylko notkę „Konto zakłada administrator.”.

## Implementation Approach

Fundament przed widokami: najpierw wspólne klocki z testami i sekcjami katalogu (faza 1), potem logowanie (faza 2) i aktywacja (faza 3), na końcu stany błędu w zrzutach, dokumentacja i pełne bramki (faza 4). Każdą fazę zamyka commit zielony na CI, więc wzorce F-06 zmieniające się w danej fazie aktualizujemy w tej samej fazie (`npm run visual:update`, tylko dotknięte pliki), a nie na końcu. `AuthLayout` jest układem strony (jak powłoka S-47), nie komponentem katalogu, więc nie trafia na `/kitchen-sink`; pilnują go zrzuty widoków. Logika widoków (wywołania API, przekierowania, historia, fokus) zostaje bez zmian; zmienia się szablon, styl i sposób pokazywania błędu pola.

## Critical Implementation Details

- **Kolejność zmiany stylu pól.** Wyjątek `.login-form` w `style.css` usuwamy w tym samym commicie, w którym `LoginView` przestaje mieć klasę `login-form` i własny styl pól (faza 2). Rozdzielenie zostawia formularz logowania z podwójnym stylem albo bez żadnego.
- **Fokus przy błędzie.** Fokus przechodzi na błędne pole po aktualizacji DOM (`nextTick`): `too_short`, `too_long`, `password_policy` na „Nowe hasło”, `mismatch` na „Powtórz hasło”; `unavailable` jest błędem formularza (`StatusMessage`, `role="alert"`) i fokusu nie przenosi.
- **Ogłoszenie błędu pola.** Region `aria-live="polite"` w `FormField` ogłasza błąd niezależnie od fokusu. Żeby ten sam błąd przy ponownym wysłaniu ogłosił się jeszcze raz, `submit` w `ActivateView` najpierw czyści `problem` i czeka na `nextTick`, a dopiero potem ustawia nowy.

## Faza 1: Wspólne klocki ekranów publicznych

### Overview

Trzy komponenty w `src/components/` z testami jednostkowymi, dwie zmiany w katalogu `/kitchen-sink` i wpis nowych plików na listę `CLEANED`. Widoki jeszcze ich nie używają.

### Changes Required:

#### 1. AppBrand

**File**: `szczur-radar-web/src/components/AppBrand.vue`, `AppBrand.test.ts`

**Intent**: Znak marki z projektu: tarcza radaru (`i-brand`) w kółku z gradientem stożkowym i nazwa „Szczur Radar” krojem `--font-display`, według `.brand` / `.brand-mark` z `prototype.css:689-710`. Jeden komponent dla ekranów publicznych teraz i paska aplikacji w S-47.

**Contract**: Bez propsów; renderuje element liniowy (`span`) z `AppIcon name="i-brand"` i tekstem „Szczur Radar”. Rozmiar tekstu `--font-size-3xl`, poniżej 900 px `--font-size-2xl` (zapytanie `max-width: 899px` jest dozwolone w skanie). Same tokeny, bez literałów koloru i `px` w odstępach, rozmiarach pisma i promieniach. Dostępna nazwa to widoczny tekst; ikona ukryta (`AppIcon`).

#### 2. FormField

**File**: `szczur-radar-web/src/components/FormField.vue`, `FormField.test.ts`

**Intent**: Pole formularza według sekcji „Pola” projektu: etykieta nad polem, pod polem podpowiedź albo błąd z ikoną, a powiązania `for`, `aria-describedby` i `aria-invalid` robi komponent. Zastępuje ręczne wiązanie w widokach.

**Contract**: Props `label: string`, `hint?: string`, `error?: string | null`. Identyfikatory z `useId()`. Scoped slot domyślny dostaje `fieldProps: { id: string; 'aria-describedby': string | undefined; 'aria-invalid': 'true' | undefined }`, który widok wiąże z `<input v-bind="fieldProps">`. `aria-describedby` wymienia id podpowiedzi i/lub błędu, tylko tych, które są. Błąd to `<p>` z `AppIcon name="i-circle-alert"` i tekstem, bez `role="alert"`, wewnątrz kontenera z `aria-live="polite"`, który komponent renderuje zawsze (także bez błędu, pusty), żeby błąd był ogłoszony również wtedy, gdy fokus już stoi na polu (Enter w „Nowe hasło”, błąd serwera po Enter) i `focus()` nic nie zmienia. `aria-describedby` wskazuje id samego `<p>` błędu, tylko gdy błąd jest. Pusty kontener nie zajmuje miejsca ani nie dodaje odstępu siatki (odstęp niesie jego treść, nie `gap` korzenia), więc pole bez błędu wygląda jak dziś. Styl etykiety, podpowiedzi i błędu przejmuje wartości z `KitchenSinkView.vue:1021-1066` (`--font-size-sm`/`xs`, `--text-muted`, `--destructive`, `--weight-medium`, ikona 0,9375 rem przez `--icon-size`). Komponent ma też cały układ pola z `KitchenSinkView.vue:1021-1043`: korzeń to siatka z odstępem `--space-1`, `min-width: 0`, `align-content: start`, a pole w slocie ma pełną szerokość (`:deep(input:not([type='checkbox']), select) { width: 100% }`; reguła globalna pól w `style.css` szerokości nie ustawia). Slot może owinąć pole w opakowanie (np. `.input-wrap` z ikoną wyszukiwania), a `fieldProps` trafia wtedy na wewnętrzny `input`. Klasy położenia podane przez rodzica (np. `wide`) przechodzą na korzeń (jeden element główny, domyślne dziedziczenie atrybutów). Dla pól `text`, `email`, `password`, `search`, `date` i `select`; pole wyboru zostaje poza zakresem.

#### 3. AuthLayout

**File**: `szczur-radar-web/src/components/AuthLayout.vue`, `AuthLayout.test.ts`

**Intent**: Układ ekranu publicznego z `.auth-page` i `.auth` prototypu: wyśrodkowana kolumna z `AppBrand`, kartą (`--surface`, `--line`, `--radius-2xl`, wnętrze `--space-6`, `--space-5` poniżej 900 px, `max-width: 26rem`) i `ThemeToggle` w prawym górnym rogu. Zastępuje `.page`, `.card`, `.eyebrow` w `style.css`.

**Contract**: Prop `title: string` (tekst h1), opcjonalny `busy?: boolean`. Root to `div` na całą wysokość okna (`min-height: 100svh`) zawierający `ThemeToggle` (poza `main`, jak w prototypie) i jeden `<main>` z `AppBrand` i `<section aria-labelledby>` (id z `useId()`); sekcja zawiera `<h1>` z `title` i slot domyślny, ma `aria-busy="true"` przy `busy`. Karta jest siatką z odstępem `--space-4`, więc bezpośrednie dzieci slotu (komunikat, akapit, formularz) rozstawia ona; formularz w slocie też jest siatką `--space-4`, a jego przycisk wysyłający zajmuje całą szerokość przez `:deep(form > button[type='submit'])` (korzeń `AppButton` to `<button class="primary">` bez klasy `btn`, więc `.auth .btn` z prototypu tu nie trafia), żeby widoki tego nie powtarzały. Nagłówek h1 ma `--font-size-5xl` (reguła globalna `h1` w `style.css:240-247` już daje krój).

#### 4. Katalog komponentów

**File**: `szczur-radar-web/src/views/KitchenSinkView.vue`, `szczur-radar-web/visual/__screenshots__/{light,dark}-375/kitchen-sink-marka.png`

**Intent**: Nowa sekcja „Marka” (`AppBrand` na szerokim i wąskim ekranie) i przepięcie sekcji „Pola” na `FormField`, z usunięciem lokalnych stylów pola z katalogu (`KitchenSinkView.vue:1021-1066`, poza `.field.wide`), które teraz mieszkają w komponencie.

**Contract**: Sekcje to nagłówki h2 w `main`; strażnik wzorców wymaga pliku `kitchen-sink-marka.png` w dwóch projektach 375 px. Po przepięciu „Pola” wzorzec `kitchen-sink-pola.png` ma zostać bez zmiany pikseli; jeśli różni się o cień czy odstęp, popraw komponent do stanu sprzed, a nie wzorzec. Na `FormField` przechodzą wszystkie pola siatki `.fields` z etykietą: trzy „E-mail”, oba „Hasło”/„Powtórz hasło”, oba pola wyszukiwania (slot zawiera `.input-wrap` z ikoną, `fieldProps` na wewnętrznym `input`), „Dzień” i oba `select`. Bez `FormField` zostają tylko wiersz pól wyboru (`.check`) i duże pole wyszukiwania (`.input-wrap.input-lg`, z `aria-label`). Z katalogu znikają `.field`, `.field label`, `.field input/select`, `.field-hint`, `.field-error`; zostaje `.field.wide` jako klasa położenia (cały wiersz siatki) nadana korzeniowi `FormField` i style `.input-wrap`. Stany `state-pola-email-hover`, `state-pola-email-focus` i `state-pola-search-focus` leżą w tej sekcji i też mają zostać bez zmiany pikseli (kryterium 1.5).

#### 5. Lista CLEANED

**File**: `szczur-radar-web/src/styleContract.test.ts`

**Intent**: Każdy nowy komponent dochodzi do `CLEANED` w chwili powstania (reguła projektu).

**Contract**: Wpisy dla `AppBrand.vue`, `FormField.vue`, `AuthLayout.vue` (import `?raw` jak przy pozostałych); komentarz nad listą o `LoginView` zostaje do fazy 2.

### Success Criteria:

#### Automated Verification:

- Testy nowych komponentów przechodzą: `cd szczur-radar-web && npx vitest run src/components/AppBrand.test.ts src/components/FormField.test.ts src/components/AuthLayout.test.ts`
- Pełne testy jednostkowe przechodzą, w tym skan literałów dla nowych plików: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Kopie projektu zgodne z tokenami i kontrast: `node tools/design-check/design-check.mjs app`
- Wzorce katalogu: nowy `kitchen-sink-marka.png` w dwóch projektach 375 px, `kitchen-sink-pola.png` bez zmian: `cd szczur-radar-web && npm run visual:update -- visual/kitchen-sink.spec.ts && git status --porcelain visual/__screenshots__` pokazuje tylko dwa nowe pliki `kitchen-sink-marka.png`
- Testy wizualne i axe zielone: `cd szczur-radar-web && npm run visual:docker`

**Implementation Note**: Po fazie 1 zielone automaty wystarczają do przejścia dalej; ocena zrzutów przez właściciela jest raz, w fazie 4.

---

## Faza 2: Logowanie na wspólnych komponentach

### Overview

`LoginView` według prototypu `login` na `AuthLayout`, `FormField`, `AppButton` i `StatusMessage`; koniec wyjątku `.login-form`; widok na liście `CLEANED`.

### Changes Required:

#### 1. LoginView

**File**: `szczur-radar-web/src/views/LoginView.vue`

**Intent**: Szablon na wspólnych klockach, cała logika bez zmian (`submit`, `errorMessage`, `activatedEmail` z `history.state`, `safeRedirect`). Usunięty scoped CSS w `px` i klasa `login-form`.

**Contract**: `AuthLayout title="Logowanie"`; w środku `<form novalidate @submit.prevent="submit">`: `StatusMessage tone="ok"` „Hasło ustawione. Zaloguj się.” (gdy `activated`), `StatusMessage tone="error"` z komunikatem błędu (gdy `error`; `role="alert"`), dwa `FormField` („E-mail” `type="email" autocomplete="username"`, „Hasło” `type="password" autocomplete="current-password"`), `AppButton type="submit" variant="primary" :busy="submitting" busy-label="Logowanie…"` z tekstem „Zaloguj się” i akapit `--text-muted` „Konto zakłada administrator.”. Komunikaty błędu 401, 429 (z minutami) i ogólny zostają tymi samymi zdaniami. Komunikaty stoją nad polami, jak w prototypie. Nie dodajemy własnych reguł dla `input`, `button`, `label`.

#### 2. Globalny styl pól

**File**: `szczur-radar-web/src/style.css`

**Intent**: Usunąć wyjątek `.login-form`: pola logowania biorą jedyny globalny styl pól.

**Contract**: Z pięciu reguł pól i reguły `::placeholder` (`style.css:359-422`) znika `:where(:not(.login-form *))`; komentarz nad nimi traci zdanie o wyjątku. Komentarz nad regułą `button` (`style.css:310-315`) traci przykład „the filled login button” (np. na „a scoped style always wins”, bez widoku). W `src/` nie zostaje ciąg `login-form`.

#### 3. Skan literałów i testy widoku

**File**: `szczur-radar-web/src/styleContract.test.ts`, `szczur-radar-web/src/views/LoginView.test.ts`

**Intent**: `LoginView.vue` dochodzi do `CLEANED`; komentarz nad listą traci zdanie o S-49. Testy widoku sprawdzają nową strukturę, zachowując obecne przypadki zachowania.

**Contract**: Testy `LoginView` zostają (błędne dane, limit z minutami, zaokrąglenie do minuty, ogólny błąd, powrót na `redirect`, notka po aktywacji, odrzucenie obcego `redirect`); dochodzą: obecność przycisku „Ciemny motyw” i marki „Szczur Radar”, błąd jako `role="alert"` z ikoną w `StatusMessage`, `autocomplete` obu pól, brak elementu `.login-form`.

#### 4. E2E

**File**: `szczur-radar-web/e2e/auth.spec.ts`, `szczur-radar-web/e2e/theme.spec.ts`

**Intent**: Dwa przepływy kontraktu `login`, których dotąd nie ma: limit prób i przełącznik motywu na ekranie publicznym.

**Contract**: W `auth.spec.ts` w trybie `LOGIN: 'limited'` komunikat `alert` zaczyna się od „Zbyt wiele prób.”, adres zostaje `/login`. W `theme.spec.ts` na `/login` przycisk „Ciemny motyw” (rola `button`, `exact`, poza `main` i `banner`) przestawia `aria-pressed` i `data-theme` na `dark` i wybór przetrwa przeładowanie. Lokatory po roli i etykiecie, bez CSS i `waitForTimeout`.

### Success Criteria:

#### Automated Verification:

- Testy widoku przechodzą: `cd szczur-radar-web && npx vitest run src/views/LoginView.test.ts`
- Pełne testy jednostkowe przechodzą (LoginView na liście `CLEANED`): `cd szczur-radar-web && npm test`
- Wyjątek usunięty: `! grep -rn "login-form" szczur-radar-web/src`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Testy w przeglądarce zielone, w tym limit prób i motyw na logowaniu: `cd szczur-radar-web && npm run e2e`
- Zgodność z projektem i kontrast: `node tools/design-check/design-check.mjs app`
- Wzorce `view-login` w czterech projektach zaktualizowane, nic innego: `cd szczur-radar-web && npm run visual:update -- visual/views.spec.ts --grep view-login && git status --porcelain visual/__screenshots__` pokazuje cztery zmienione pliki `view-login.png`
- Testy wizualne i axe zielone: `cd szczur-radar-web && npm run visual:docker`

**Implementation Note**: Jeśli Docker niedostępny, wzorce bierze się z artefaktu `visual-baselines` czerwonego CI (`gh run download <run-id> -n visual-baselines -D szczur-radar-web/visual/__screenshots__`) po przeglądzie obrazów.

---

## Faza 3: Aktywacja z błędem przy polu

### Overview

`ActivateView` według prototypu `activate`: `AuthLayout` i `FormField`, błąd hasła przy polu z fokusem, usunięcie martwych stylów globalnych.

### Changes Required:

#### 1. ActivateView

**File**: `szczur-radar-web/src/views/ActivateView.vue`

**Intent**: Szablon na `AuthLayout` i `FormField`; błędy hasła przy polu zamiast jednego komunikatu pod formularzem. Logika sprawdzania linku, aktywacji, kończenia obcej sesji, usuwania tokenu z adresu i przejścia na `/login` z `history.state` zostaje bez zmian.

**Contract**: `AuthLayout title="Ustawienie hasła" :busy="phase === 'checking'"`. Fazy bez formularza: `checking` (akapit „Sprawdzanie linku…”), `invalid` (`StatusMessage tone="error"`, „Link jest nieważny lub wygasł. Poproś administratora o nowy.”), `lookup_failed` (`StatusMessage` z „Nie udało się sprawdzić linku. Spróbuj ponownie.” i `AppButton` „Spróbuj ponownie”). Faza `form`: akapit „Ustaw hasło dla <strong>e-mail</strong>.”, ukryte pole `type="email" autocomplete="username"` dla menedżera haseł (jak dziś), `FormField` „Nowe hasło” (`hint` „Od N do M znaków.”, `error` dla `too_short`/`too_long`/`password_policy`, `autocomplete="new-password"`), `FormField` „Powtórz hasło” (`error` dla `mismatch`), `StatusMessage tone="error"` tylko dla `unavailable`, `AppButton type="submit" variant="primary" :busy busy-label="Zapisywanie…"` „Ustaw hasło”. Treści komunikatów (`PROBLEM_MESSAGES`) bez zmian. Po ustawieniu problemu fokus idzie na błędne pole zgodnie z „Critical Implementation Details”. Własne style `.field`, `label`, `.hint` znikają.

#### 2. Martwe style globalne

**File**: `szczur-radar-web/src/style.css`

**Intent**: Po migracji obu widoków `.page`, `.card`, `.eyebrow`, `.card h1` i `.lead` nie ma kto używać.

**Contract**: Przed usunięciem `grep` po `src/` (widoki, komponenty, testy) potwierdza brak użyć tych klas; reguły znikają razem z komentarzem „Centered single-card page (login)”. Komentarz `PostCard.vue:49` („Not `.card`: style.css has a global one (the centred login card)…”) przestaje być prawdziwy: zostaje usunięty albo przepisany bez odwołania do globalnej `.card` (sama klasa komponentu się nie zmienia, wzorce postów bez zmian).

#### 3. Testy widoku i E2E

**File**: `szczur-radar-web/src/views/ActivateView.test.ts`, `szczur-radar-web/e2e/activation.spec.ts`

**Intent**: Pilnują nowego zachowania błędu przy polu i obecnych przepływów aktywacji.

**Contract**: Jednostkowe: obecne przypadki zostają poza czterema, które dziś szukają błędu hasła jako `[role="alert"]` i są przepisywane na błąd przy polu — `ActivateView.test.ts:118` (`too_short`, liczony w punktach kodowych), `:127` (`too_long`), `:140` (`mismatch`), `:149` (`password_policy` z serwera). Po zmianie: `too_short`, `too_long` i `password_policy` stoją przy polu „Nowe hasło” (`aria-invalid="true"`, opis w `aria-describedby`, fokus na polu), `mismatch` przy „Powtórz hasło” (to samo na tym polu); w żadnym z nich nie ma `role="alert"`. Na `role="alert"` zostają tylko `unavailable` (bez przenoszenia fokusu) i nieważny link. E2E: za krótkie hasło daje błąd przy polu i fokus na nim (`getByLabel('Nowe hasło', { exact: true })` ma `toBeFocused` i `aria-invalid`); w trybie `ACTIVATION: 'invalid'` jest komunikat o nieważnym linku i brak pól „Nowe hasło”; w trybie `policy` błąd serwera pojawia się przy polu hasła.

### Success Criteria:

#### Automated Verification:

- Testy widoku przechodzą: `cd szczur-radar-web && npx vitest run src/views/ActivateView.test.ts`
- Pełne testy jednostkowe przechodzą: `cd szczur-radar-web && npm test`
- Martwe style usunięte: `! grep -nE "^\.(page|card|eyebrow|lead)\b" szczur-radar-web/src/style.css`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Testy w przeglądarce zielone: `cd szczur-radar-web && npm run e2e`
- Zgodność z projektem i kontrast: `node tools/design-check/design-check.mjs app`
- Wzorce `view-activate` w czterech projektach zaktualizowane, nic innego: `cd szczur-radar-web && npm run visual:update -- visual/views.spec.ts --grep view-activate && git status --porcelain visual/__screenshots__` pokazuje cztery zmienione pliki `view-activate.png` (i `view-login.png` z fazy 2 już zatwierdzone)
- Testy wizualne i axe zielone: `cd szczur-radar-web && npm run visual:docker`

---

## Faza 4: Stany błędu w zrzutach, dokumentacja i pełne bramki

### Overview

Nowy wygląd błędu dostaje strażnika zrzutów i axe, dokumentacja repozytorium przestaje mówić o wyjątku logowania, a plan kończą pełne bramki, PR env i ocena obrazków przez właściciela.

### Changes Required:

#### 1. Widoki błędu w zrzutach

**File**: `szczur-radar-web/visual/targets.ts` (wpisy w `VIEWS`)

**Intent**: Wygląd komunikatu błędu logowania i błędu przy polu aktywacji ma wzorce w czterech projektach i przechodzi przez axe.

**Contract**: `view-login-error`: tryby `{ AUTH: 'anonymous', LOGIN: 'invalid' }`, `ready` wypełnia pola, wysyła formularz klawiszem Enter w polu „Hasło” i czeka na `alert` „Nieprawidłowy e-mail lub hasło.”. `view-activate-error`: tryby `{ AUTH: 'anonymous', ACTIVATION: 'valid' }`, `ready` czeka na formularz, wpisuje za krótkie hasło w oba pola, wysyła klawiszem Enter w „Powtórz hasło” i czeka na tekst błędu przy polu oraz fokus na „Nowe hasło”. Bez kliknięcia przycisku: kursor nad nim utrwaliłby we wzorcu stan hover przycisku głównego. Każdy wpis ma `risk` po polsku w formie reszty tablicy. Lokatory po roli i etykiecie, bez `waitForTimeout`.

#### 2. Dokumentacja repozytorium

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: Usunąć nieaktualne zdania o wyjątku logowania i dopisać nowe wspólne komponenty.

**Contract**: W akapicie „Form fields” znika zdanie o wyjątku `LoginView` (`login-form`, „until S-49 migrates them”); w akapicie „No colour literals” znika zdanie, że `LoginView.vue` zostaje poza `CLEANED` do `login-view-ui-hardening`; w akapicie „Shared components” dochodzą `AppBrand`, `FormField` i `AuthLayout` (co robią, że `AuthLayout` to układ strony, nie element katalogu, i że `FormField` wiąże etykietę, podpowiedź i błąd), z wzmianką, że pasek aplikacji S-47 używa `AppBrand`.

#### 3. Opis PR

**File**: opis pull requesta (GitHub, nie plik repozytorium)

**Intent**: Wymóg projektu: opis PR wycinka UI pokazuje zrzut F-06 obok prototypu i mówi, które grupy wzorców się zmieniły i dlaczego; oraz dla S-47 informację o `AppBrand`.

**Contract**: Treść zawiera nazwy grup `view-login`, `view-activate`, `view-login-error`, `view-activate-error`, `kitchen-sink-marka`, `Refs #<issue S-49>` i jednozdaniową notkę o kolejności scalania względem S-47 (S-47 używa `AppBrand` z tego PR).

### Success Criteria:

#### Automated Verification:

- Nowe wzorce błędu w czterech projektach, nic innego: `cd szczur-radar-web && npm run visual:update -- visual/views.spec.ts --grep "view-(login|activate)-error" && git status --porcelain visual/__screenshots__` pokazuje osiem nowych plików
- Testy wizualne i axe zielone, także na stanach błędu: `cd szczur-radar-web && npm run visual:docker`
- Testy jednostkowe: `cd szczur-radar-web && npm test`
- Typecheck i build: `cd szczur-radar-web && npm run build`
- Testy w przeglądarce: `cd szczur-radar-web && npm run e2e`
- Zgodność z projektem, kontrast i strony: `node tools/design-check/design-check.mjs app`
- Dokumentacja zaktualizowana: `! grep -nE "login-form|until S-49|login-view-ui-hardening" REPOSITORY_GUIDELINES.md` oraz `grep -c "AppBrand" REPOSITORY_GUIDELINES.md` daje co najmniej 1
- CI na PR zielone: `gh pr checks --watch` (wszystkie zadania, w tym `web-e2e` z krokiem wizualnym)
- Opis PR zawiera grupy wzorców i `Refs`: `gh pr view --json body -q .body | grep -E "view-login-error|Refs #"`
- Strony publiczne w PR env wyglądają jak prototypy (agent, `playwright-cli`, bez logowania): `/login` pokazuje nagłówek „Logowanie”, pola „E-mail” i „Hasło”, przycisk „Zaloguj się”, przycisk „Ciemny motyw”; `/activate#bogus` pokazuje „Link jest nieważny lub wygasł. Poproś administratora o nowy.” bez formularza; zrzuty obu w jasnym i ciemnym motywie zapisane w katalogu dowodów zmiany

#### Manual Verification:

- Właściciel ogląda zrzuty `login`, `activate` i stanów błędu w obu motywach przy 1280 i 375 px obok prototypów (zestawienie w opisie PR) i akceptuje wygląd

**Implementation Note**: Po fazie 4 zatrzymaj się na ocenie obrazków przez właściciela przed `/10x-impl-review` i `/10x-archive`; merge robi człowiek, nigdy agent.

---

## Testing Strategy

### Unit Tests:

- `FormField`: etykieta powiązana z polem przez `for`/`id`; `aria-describedby` zawiera tylko obecne części (podpowiedź, błąd, obie, żadna); `aria-invalid` tylko przy błędzie; błąd ma ikonę i nie ma `role="alert"`; region `aria-live="polite"` istnieje już przed pojawieniem się błędu i po nim zawiera jego tekst; id są unikalne między dwoma polami.
- `AuthLayout`: jeden `main`, jeden h1 z `title`, sekcja nazwana przez h1, `ThemeToggle` obecny, `aria-busy` przy `busy`, slot renderowany; reguła pełnej szerokości obejmuje przycisk `type="submit"` formularza w slocie (selektor `:deep(form > button[type='submit'])` obecny w stylu komponentu, bo jsdom nie liczy układu).
- `AppBrand`: tekst „Szczur Radar”, ikona ukryta przed czytnikiem.
- `LoginView` i `ActivateView`: obecne przypadki zachowania zostają, dochodzą przypadki z fazy 2 i 3 (błąd przy polu, fokus, brak `login-form`).
- `styleContract`: nowe pliki i `LoginView` na `CLEANED`.

### Integration Tests:

- E2E na atrapie API: logowanie ze złym hasłem i z limitem prób, przełącznik motywu na logowaniu z trwałym wyborem, aktywacja z poprawnym i za krótkim hasłem, nieważny link i błąd serwera przy polu hasła.
- Wzorce F-06 w czterech projektach dla czterech widoków i dla sekcji „Marka”; axe na każdym z nich (WCAG A/AA, `target-size`, kontrast).

### Manual Testing Steps:

1. Ocena obrazków właściciela w fazie 4 (zestawienie w opisie PR).
2. Kontrola stron publicznych w PR env wykonuje agent (kryterium Automated fazy 4), bez udziału właściciela.

## Performance Considerations

Brak: dochodzą trzy małe komponenty i jedna ikona z istniejącego sprite'a; kroje i preload zostają jak w F-08.

## Migration Notes

Bez migracji danych i bez zmian API. Wzorce F-06 `view-login` i `view-activate` zmieniają się (8 plików), 8 plików stanów błędu i 2 pliki `kitchen-sink-marka.png` są nowe. Zadanie S-47 w osobnym worktree używa `AppBrand` po scaleniu tego PR albo po rebase na niego; do czasu scalenia nie ma wspólnych plików.

## References

- Projekt: `context/foundation/ui-design.md` (sekcje „Ekrany”, „Pola”, „Komponenty”, „Kontrakt dla testów E2E”, „Zmiany bramek”), prototypy `context/foundation/ui-design/prototypes/screens/login.html` i `activate.html`, style `prototype.css:689-710` (marka), `:1727-1740` (karta `.auth`), `:2114-2145` (układ strony publicznej), `:330-360` (pole, podpowiedź, błąd).
- Roadmapa: `context/foundation/roadmap.md`, blok S-49 (`Prototypy: login, activate`, `Parallel with: S-47`).
- Lekcje: `context/foundation/lessons.md` — „Planuj weryfikację agent-first…”, „Kryterium, które agent sprawdzi poleceniem, zapisuj jako Automated…”, „Stronę sprawdza agent przez playwright-cli…”, „Uzasadniaj zakres celem użytkownika…”, „Proaktywnie szukaj okazji do abstrakcji…”.
- Obecny kod: `szczur-radar-web/src/views/LoginView.vue`, `ActivateView.vue`, `src/style.css:359-422`, `src/styleContract.test.ts:117-143`, `src/views/KitchenSinkView.vue:707-785,1021-1066`, `visual/targets.ts:58-83`, `e2e/auth.spec.ts`, `e2e/activation.spec.ts`, `e2e/theme.spec.ts:90-97`, `dev/mock-api.js:615-640`.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Wspólne klocki ekranów publicznych

#### Automated

- [x] 1.1 Testy nowych komponentów przechodzą (AppBrand, FormField, AuthLayout) — c116b1a
- [x] 1.2 Pełne testy jednostkowe przechodzą, w tym skan literałów dla nowych plików — c116b1a
- [x] 1.3 Typecheck i build przechodzą — c116b1a
- [x] 1.4 Kopie projektu zgodne z tokenami i kontrast (design-check app) — c116b1a
- [x] 1.5 Wzorce katalogu: nowy kitchen-sink-marka w dwóch projektach, kitchen-sink-pola bez zmian — c116b1a
- [x] 1.6 Testy wizualne i axe zielone — c116b1a

### Phase 2: Logowanie na wspólnych komponentach

#### Automated

- [x] 2.1 Testy widoku logowania przechodzą — 38269d0
- [x] 2.2 Pełne testy jednostkowe przechodzą (LoginView na liście CLEANED) — 38269d0
- [x] 2.3 Wyjątek login-form usunięty z kodu — 38269d0
- [x] 2.4 Typecheck i build przechodzą — 38269d0
- [x] 2.5 Testy w przeglądarce zielone, w tym limit prób i motyw na logowaniu — 38269d0
- [x] 2.6 Zgodność z projektem i kontrast (design-check app) — 38269d0
- [x] 2.7 Wzorce view-login w czterech projektach zaktualizowane, nic innego — 38269d0
- [x] 2.8 Testy wizualne i axe zielone — 38269d0

### Phase 3: Aktywacja z błędem przy polu

#### Automated

- [x] 3.1 Testy widoku aktywacji przechodzą — 0572e8a
- [x] 3.2 Pełne testy jednostkowe przechodzą — 0572e8a
- [x] 3.3 Martwe style globalne usunięte — 0572e8a
- [x] 3.4 Typecheck i build przechodzą — 0572e8a
- [x] 3.5 Testy w przeglądarce zielone — 0572e8a
- [x] 3.6 Zgodność z projektem i kontrast (design-check app) — 0572e8a
- [x] 3.7 Wzorce view-activate w czterech projektach zaktualizowane, nic innego — 0572e8a
- [x] 3.8 Testy wizualne i axe zielone — 0572e8a

### Phase 4: Stany błędu w zrzutach, dokumentacja i pełne bramki

#### Automated

- [x] 4.1 Nowe wzorce błędu w czterech projektach, nic innego — 2123d07
- [x] 4.2 Testy wizualne i axe zielone, także na stanach błędu — 2123d07
- [x] 4.3 Testy jednostkowe przechodzą — 2123d07
- [x] 4.4 Typecheck i build przechodzą — 2123d07
- [x] 4.5 Testy w przeglądarce zielone — 2123d07
- [x] 4.6 Zgodność z projektem, kontrast i strony (design-check app) — 2123d07
- [x] 4.7 Dokumentacja zaktualizowana — 2123d07
- [x] 4.8 CI na PR zielone — 2123d07
- [x] 4.9 Opis PR zawiera grupy wzorców i Refs — 2123d07
- [x] 4.10 Strony publiczne w PR env wyglądają jak prototypy — 2123d07

#### Manual

- [x] 4.11 Właściciel ogląda zrzuty obok prototypów w obu motywach i akceptuje wygląd — zaakceptowane przez właściciela 2026-11-22
