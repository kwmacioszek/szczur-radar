# S-46: przełącznik motywu jasnego i ciemnego w nowej palecie — plan implementacji

## Overview

Użytkownik przełącza motyw aplikacji między jasnym i ciemnym przyciskiem w nagłówku. Wybór jest zapamiętany w przeglądarce, a bez wyboru obowiązuje motyw systemu, także po jego zmianie. Motywy dostają nową paletę: tła z makiet M-5 (papierowe jasne z „Czytnika”, grafitowe ciemne ze „Strumienia”), jeden akcent spoza zieleni i czerwieni stanowisk oraz nowe role kolorów, których potrzebują następne wycinki (`--surface`, `--highlight`, `--stance-neutral`, `--stance-mixed`). Każda para tekstu ma kontrast co najmniej 4.5:1 w obu motywach (FR-040, `prd.md:295-296`; roadmapa S-46).

## Current State Analysis

- Tokeny żyją w `:root` pliku `szczur-radar-web/src/style.css:8-56`: 8 ról koloru (`--bg`, `--text`, `--text-h`, `--accent`, `--on-accent`, `--success`, `--warning`, `--destructive`) i trzy pochodne (`--ring`, `--border`, `--border-strong`).
- Motyw ciemny to wyłącznie `@media (prefers-color-scheme: dark) { :root { … } }` (`style.css:58-69`). Nie ma `[data-theme]`, skryptu motywu ani użycia `localStorage` w SPA. `color-scheme: light dark` (`style.css:45`) oddaje natywne kontrolki (lista `select`, kalendarz `input[type=date]`) systemowi.
- Test kontrastu `src/styleContract.test.ts` czyta pierwszy `:root` jako motyw jasny, a ciemny wyłuskuje dosłownie po tekście `@media (prefers-color-scheme: dark)` (`:61-64`). Pary to tylko role tekstu na `--bg` i `--on-accent` na `--accent` (`:28-36`).
- Testy wyglądu F-06 (`szczur-radar-web/visual/`) przełączają motyw przez `colorScheme` Playwrighta w 4 projektach (`visual/targets.ts:10-15`). Archiwalny plan F-06 zakładał wprost, że motyw to tylko media query (`context/archive/2026-11-16-visual-regression/plan.md:38`). Wzorców jest 54: 6 widoków × 4 projekty, 10 sekcji katalogu × 2 i 5 stanów × 2.
- `AppHeader.vue` stawiają sami 4 widoki: feed, post, `/admin` i przebieg (`FeedView.vue:165`, `PostDetailView.vue:138`, `AdminView.vue:412`, `RunDetailView.vue:154`). Logowanie, aktywacja i `/kitchen-sink` nie mają nagłówka. Stały shell dopiero w S-47, `LoginView` przebudowuje S-49.
- `AppButton.vue` przekazuje atrybuty do swojego `<button>` (jeden korzeń), więc `aria-pressed` przechodzi bez nowego propsa.
- `KitchenSinkView.vue:31-51` ma listę `colourRoles` z próbkami w sekcji „Tokeny”. Nowa sekcja katalogu (nagłówek poziomu 2) dostaje wzorzec sama (`visual/targets.ts` `catalogSections`).
- Caddy (`szczur-radar-web/Caddyfile`) nie ustawia CSP, więc klasyczny skrypt inline w `index.html` jest dozwolony.
- Makiety (`docs/visuals/ui-ux-proposals/`): „Czytnik” jest jasny papierowy z rdzawym akcentem, „Strumień” ciemny grafitowo-morski z limonkowym akcentem. Pokazują stanowiska „Neutralnie” i „Mieszanie” oraz podświetlony cytat. `ui-ux-proposals.md:3` mówi, że kolory ustala M-5.

## Desired End State

- `<html data-theme="light|dark">` jest ustawione, zanim przeglądarka pomaluje pierwszą klatkę, na każdej stronie: logowanie, aktywacja, widoki i katalog, w buildzie produkcyjnym i na `vite`.
- Rozstrzygnięcie motywu: poprawny zapamiętany wybór (`light`/`dark`) wygrywa. W przeciwnym razie, także przy nieznanej wartości albo błędzie dostępu do `localStorage`, obowiązuje motyw systemu. Bez zapamiętanego wyboru zmiana motywu systemu przełącza stronę na żywo, z wyborem nie.
- Przycisk „Ciemny motyw” (`aria-pressed`) w nagłówku zalogowanego przełącza motyw i zapisuje wybór. Strony bez nagłówka stosują zapamiętany wybór.
- `style.css` ma jeden blok motywu jasnego (`:root`) i jeden ciemny (`:root[data-theme='dark']`), bez media query motywu. Nowa paleta (wariant wybrany przez właściciela) i role `--surface`, `--highlight`, `--stance-neutral`, `--stance-mixed` są w obu blokach, z próbkami w katalogu.
- Test kontrastu sprawdza 19 par w obu motywach. Test E2E pilnuje przełączenia, zapamiętania, pierwszeństwa wyboru i śledzenia systemu. Wzorce F-06 pokazują nową paletę.

Weryfikacja: `npm test`, `npm run build`, `npm run e2e` i `npm run visual:docker` są zielone, CI `web-e2e` jest zielony w PR, a właściciel zaakceptował różnice obrazków w PR.

### Key Discoveries:

- Test kontrastu jest przywiązany do tekstu media query (`styleContract.test.ts:63`), więc zmiana struktury CSS zmienia jego parser w tej samej fazie.
- `colorScheme` Playwrighta zmienia wynik `matchMedia('(prefers-color-scheme: dark)')`. Skrypt, który bez zapamiętanego wyboru czyta system, zostawia więc mechanikę projektów F-06 bez zmian.
- Ton `neutral` w `StatusBadge` to `--text`, a `info` to `--accent` (`StatusBadge.vue:35-40`). `StanceText` koloruje tylko stanowiska wzrostowe (`--success`) i spadkowe (`--destructive`). Nowe role stanowisk nie zmieniają tych komponentów w tym wycinku.
- Pierwsze `npm run visual:update` z nowym celem (sekcja „Motyw”) jest raz czerwone, bo strażnik `baselines.spec.ts` biegnie równolegle. Drugie przejście jest zielone (`REPOSITORY_GUIDELINES.md`, F-06).

## What We're NOT Doing

- Zapamiętanie wyboru na koncie (API, migracja, kontrakt). Wybór żyje w przeglądarce i nie wędruje między urządzeniami.
- Trzeciej opcji „jak system” w przełączniku: po pierwszym kliknięciu motyw jest stały, dopóki użytkownik nie kliknie znowu albo nie wyczyści danych strony.
- Przełącznika na `/login` i `/activate`, które tylko stosują wybór. Te ekrany przebudowuje S-49, a stały shell S-47.
- Typografii (szeryf, rozmiary poniżej 14 px), skali odstępów powyżej 24 px, cieni, siatki i breakpointów. To należy do S-47 i S-53.
- Użycia nowych ról w widokach i komponentach (`StanceText` dla neutral/mixed, panele, podświetlenie cytatu). W tym wycinku pokazuje je tylko katalog, a używają ich S-47, S-51 i S-53.
- Zmian w `StatusBadge`, w atrapie API i w backendzie.
- Kontroli w PR env: build produkcyjny ze skryptem w `index.html` sprawdzają E2E i testy wyglądu na `vite preview`, a Caddy tylko serwuje te same pliki.

## Implementation Approach

Źródłem motywu jest jeden atrybut `data-theme` na `<html>`. Ustawia go klasyczny skrypt inline w `<head>` pliku `index.html` przed pierwszym malowaniem, bez mignięcia złego motywu. Potem przejmuje go moduł `src/theme.ts`: reaktywny stan, zapis wyboru i śledzenie systemu bez wyboru. CSS ma dwa bloki ról, więc wartości ciemne istnieją raz. Bez JS strona jest jasna, a SPA i tak bez niego nie działa.

Fazy oddzielają mechanizm od palety. Faza 1 zmienia tylko strukturę i dodaje przełącznik przy dzisiejszych kolorach, więc różnice obrazków w PR pokazują wyłącznie nowy przycisk. Faza 2 dodaje nowe role (na razie w dzisiejszej palecie) i przygotowuje dwa warianty nowej palety jako pliki w folderze zmiany, ze zrzutami. Kończy się wyborem właściciela, zanim cokolwiek przerysuje wszystkie wzorce. Faza 3 wpisuje wybrany wariant i przerysowuje wzorce.

## Critical Implementation Details

**Timing & lifecycle:** skrypt w `index.html` musi być klasyczny (bez `type="module"`, który jest odroczony i dałby mignięcie) i stać w `<head>`. Ustawia atrybut na `document.documentElement`, który istnieje już w czasie parsowania `<head>`. `src/theme.ts` nie rozstrzyga motywu drugi raz przy starcie: czyta atrybut ustawiony przez skrypt i dopiero od tej chwili obsługuje kliknięcia i zmianę systemu.

**Jedna reguła w dwóch miejscach:** rozstrzygnięcie (wybór, potem system) istnieje w skrypcie inline i w `theme.ts`, bo skrypt nie może importować modułu. Test Vitest uruchamia skrypt wyjęty z `index.html` w jsdom dla tej samej tabeli przypadków co `resolveTheme`, w tym przy kluczu zapisu `THEME_STORAGE_KEY`, więc rozjazd jest czerwony.

## Phase 1: Mechanizm motywu i przełącznik

### Overview

Struktura `data-theme`, skrypt przed startem, moduł `theme.ts`, przycisk w nagłówku i w katalogu, test kontrastu na nowym bloku oraz test E2E. Kolory zostają dzisiejsze.

### Changes Required:

#### 1. Skrypt motywu przed startem

**File**: `szczur-radar-web/index.html`

**Intent**: Ustawić `data-theme` przed pierwszym malowaniem: poprawny zapamiętany wybór, w przeciwnym razie motyw systemu. Błąd dostępu do `localStorage` oznacza motyw systemu.

**Contract**: Klasyczny `<script>` inline w `<head>`, bez importów. Czyta `localStorage[THEME_STORAGE_KEY]` (`'szczur-radar-theme'`) w `try/catch`, akceptuje tylko `light`/`dark` i ustawia `document.documentElement.dataset.theme`.

#### 2. Moduł motywu

**File**: `szczur-radar-web/src/theme.ts` (nowy), `szczur-radar-web/src/main.ts`

**Intent**: Jedno miejsce stanu motywu w SPA, w stylu `auth/session.ts`: reaktywny bieżący motyw, przełączenie z zapisem wyboru i śledzenie systemu, dopóki nie ma wyboru.

**Contract**:
- `export type Theme = 'light' | 'dark'` i `export const THEME_STORAGE_KEY = 'szczur-radar-theme'`.
- `export function resolveTheme(stored: string | null, systemDark: boolean): Theme` to ta sama reguła co skrypt.
- `export const currentTheme: Readonly<Ref<Theme>>`.
- `export function setTheme(theme: Theme): void` ustawia atrybut i zapis (błąd zapisu połknięty: motyw działa do końca sesji strony) oraz wyłącza śledzenie systemu.
- `export function toggleTheme(): void`.
- `export function initTheme(): void` czyta atrybut, a bez zapamiętanego wyboru słucha `matchMedia('(prefers-color-scheme: dark)')`, `change`.
- `main.ts` woła `initTheme()` przed `mount`.

#### 3. Tokeny

**File**: `szczur-radar-web/src/style.css`

**Intent**: Zastąpić media query blokiem `:root[data-theme='dark']` z tymi samymi wartościami. `color-scheme` idzie za motywem, żeby natywne kontrolki się przełączały. Komentarz nagłówka opisuje nowe źródło motywu.

**Contract**: `:root { … color-scheme: light; }` i `:root[data-theme='dark'] { <8 ról>; color-scheme: dark; }`. Brak `prefers-color-scheme` w pliku.

#### 4. Przełącznik

**File**: `szczur-radar-web/src/components/ThemeToggle.vue` (nowy), `src/components/AppHeader.vue`, `src/views/KitchenSinkView.vue`

**Intent**: Wspólny komponent przycisku motywu w nagłówku (obok „Wyloguj”) i w nowej sekcji katalogu „Motyw”. Komponent idzie do katalogu zgodnie z regułą wspólnych komponentów. W katalogu pokazuje stan bieżącego motywu, więc projekty `light-375` i `dark-375` dają oba stany.

**Contract**: `<AppButton :aria-pressed="…">Ciemny motyw</AppButton>`: stała nazwa dostępna „Ciemny motyw”, `aria-pressed="true"` w motywie ciemnym, kliknięcie woła `toggleTheme()`. Bez literałów stylu (plik na liście `CLEANED` w `styleContract.test.ts`). Sekcja katalogu `<section aria-labelledby>` z `<h2>Motyw</h2>`.

#### 5. Test kontrastu

**File**: `szczur-radar-web/src/styleContract.test.ts`

**Intent**: Ciemny motyw czytany z bloku `:root[data-theme='dark']`. Test jest czerwony, gdy plik ma jeszcze media query motywu (dwa źródła prawdy). Dodać `ThemeToggle.vue` do `CLEANED`.

**Contract**: `ROLES.dark` z `block(source, ":root[data-theme='dark']")`. Nowy przypadek: `source` nie zawiera `prefers-color-scheme`.

#### 6. Testy jednostkowe

**File**: `szczur-radar-web/src/theme.test.ts` (nowy), `src/components/ThemeToggle.test.ts` (nowy), `src/components/AppHeader.test.ts`

**Intent**:
- **`theme.test.ts`:** tabela `resolveTheme` (brak, `light`, `dark`, nieznana wartość × system jasny/ciemny); skrypt z `index.html` (`../index.html?raw`, wyjęty i wykonany w jsdom) daje dla tej tabeli ten sam wynik; `setTheme` zapisuje i ustawia atrybut; zmiana systemu przełącza tylko bez wyboru; rzucający `localStorage` (odczyt i zapis) nie psuje przełączenia.
- **`ThemeToggle.test.ts`:** `aria-pressed` odpowiada motywowi, a klik przełącza.
- **`AppHeader.test.ts`:** nagłówek ma przycisk „Ciemny motyw”.

**Contract**: wyłącznie testy (Vitest, jawne importy z `'vitest'`).

#### 7. Test E2E

**File**: `szczur-radar-web/e2e/theme.spec.ts` (nowy)

**Intent**: Pilnować przepływu motywu w przeglądarce na buildzie produkcyjnym. Każdy test nazwany od ryzyka:
- wybór motywu przetrwa przeładowanie strony;
- zapamiętany wybór wygrywa z motywem systemu, także po zmianie systemu;
- bez wyboru motyw idzie za systemem, także na żywo;
- logowanie stosuje zapamiętany motyw.

**Contract**:
- `test` i `expect` z `./fixtures.ts`, tryby `AUTH: 'user'` albo `'anonymous'`; system przez `page.emulateMedia({ colorScheme })`.
- Przycisk po roli i nazwie w `banner` (`exact: true`).
- Motyw odczytany przez `page.evaluate` (atrybut `data-theme` i obliczony `color-scheme` na `<html>`), nie lokatorem CSS. Bez `waitForTimeout`.
- Zapis przed wejściem na `/login` przez `page.addInitScript`.

#### 8. Wzorce i dokumentacja fazy

**File**: `szczur-radar-web/visual/__screenshots__/**`, `context/foundation/roadmap.md`, `tools/render_roadmap_svg.py`, `docs/visuals/roadmap.svg`

**Intent**:
- **Wzorce:** przerysować te z nagłówkiem (feed, post, `/admin`, przebieg × 4 projekty) i dodać `kitchen-sink-motyw.png` w obu projektach 375 przez `npm run visual:update`. Pierwsze przejście jest raz czerwone przez strażnika, drugie zielone.
- **Roadmapa:** S-46 przechodzi na `in-progress` (robi to `/10x-implement`), a dane w `tools/render_roadmap_svg.py` dostają ten sam status z wygenerowanym na nowo SVG (reguła z `REPOSITORY_GUIDELINES.md`).

**Contract**: zmienione PNG tylko w grupach „widoki z nagłówkiem” i „nowa sekcja Motyw”. Pozostałe wzorce bajtowo bez zmian.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe zielone, w tym `theme.test.ts`, `ThemeToggle.test.ts` i test kontrastu na nowym bloku: `cd szczur-radar-web && npm test`
- Typecheck i build produkcyjny przechodzą: `cd szczur-radar-web && npm run build`
- `style.css` nie ma już media query motywu: `! grep -n "prefers-color-scheme" szczur-radar-web/src/style.css`
- Testy E2E zielone, w tym 4 testy `e2e/theme.spec.ts`: `cd szczur-radar-web && npm run e2e`
- Testy wyglądu zielone po przerysowaniu. Zmienione wzorce to tylko 4 widoki z nagłówkiem w 4 projektach i nowe `kitchen-sink-motyw.png` (2): `cd szczur-radar-web && npm run visual:docker` oraz `git status --porcelain szczur-radar-web/visual/__screenshots__` pokazuje 18 plików
- Mignięcie wykluczone: skrypt w `index.html` jest klasycznym skryptem w `<head>` przed modułem aplikacji (`theme.test.ts` sprawdza to na `index.html?raw`)

**Implementation Note**: po zielonej weryfikacji automatycznej przejdź do fazy 2. Faza nie ma kryteriów ręcznych: różnice obrazków ocenia się raz w PR po fazie 3.

---

## Phase 2: Nowe role i dwa warianty palety

### Overview

Nowe role kolorów wchodzą do `style.css` w dzisiejszej palecie, z parami kontrastu i próbkami w katalogu. Dwa warianty nowej palety powstają jako pliki w folderze zmiany, ze sprawdzonym kontrastem i zrzutami, a właściciel wybiera jeden.

### Changes Required:

#### 1. Nowe role

**File**: `szczur-radar-web/src/style.css`, `src/styleContract.test.ts`, `src/views/KitchenSinkView.vue`

**Intent**: Dodać role, których potrzebują S-47, S-51 i S-53. Wartości tymczasowe pasują do dzisiejszej palety i spełniają kontrast. Każda rola ma próbkę w sekcji „Tokeny”.

**Contract**:
- Nowe role w obu blokach: `--surface` (tło paneli i kart), `--highlight` (tło podświetlonego cytatu), `--stance-neutral` i `--stance-mixed` (kolor tekstu stanowiska).
- `PAIRS` = każda rola tekstu (`--text`, `--text-h`, `--accent`, `--success`, `--warning`, `--destructive`, `--stance-neutral`, `--stance-mixed`) na `--bg` i na `--surface`, plus `--on-accent` na `--accent`, `--text-h` i `--text` na `--highlight`. Razem 19 par × 2 motywy.
- `colourRoles` w katalogu z 4 nowymi rolami. `--highlight` jest pokazane jako tekst na tle (jak `--on-accent`).

#### 2. Warianty palety

**File**: `context/changes/light-dark-theme-switch/palette/palette.md`, `palette/variant-a.css`, `palette/variant-b.css`, `palette/*.png` (nowe)

**Intent**: Dwa kompletne zestawy wartości wszystkich ról koloru w obu motywach, do wyboru przez właściciela na zrzutach.

**Contract**:
- Tła z makiet: jasne ciepłe, papierowe („Czytnik”, rejon `#f7f4ee`), ciemne grafitowo-morskie („Strumień”, rejon `#101a20`), `--surface` o krok od tła. Akcent jeden w obu motywach, wariant A niebieski, wariant B morski. Odcień akcentu jest wyraźnie oddalony od odcieni `--success` i `--destructive`. `--success`, `--warning` i `--destructive` są dostrojone do nowych teł.
- Każdy plik `variant-*.css` to dwa bloki (`:root`, `:root[data-theme='dark']`) w formacie `style.css`. Kontrast potwierdza prawdziwy test kontrastu na tymczasowej podmianie bloków w `style.css`, niezacommitowanej i cofniętej po teście.
- `palette.md` zawiera tabelę wartości i współczynników kontrastu obu wariantów oraz miejsce na decyzję.
- Zrzuty na `npm run dev` z atrapą przez `playwright-cli`, z wariantem wstrzykniętym jako styl strony. Na każdy wariant: `/kitchen-sink` przy 375 px w obu motywach i feed `/` (`AUTH: 'user'`) przy 1280 px w obu motywach, czyli 8 PNG. Wysłane właścicielowi.

### Success Criteria:

#### Automated Verification:

- Test kontrastu ma 38 przypadków par (19 × 2 motywy) i jest zielony: `cd szczur-radar-web && npx vitest run src/styleContract.test.ts`
- Oba warianty przechodzą ten sam test na tymczasowej podmianie, a `style.css` po kontroli wraca do zacommitowanej treści: `git diff --quiet szczur-radar-web/src/style.css` po cofnięciu; wynik zapisany w `palette.md`
- 8 zrzutów i `palette.md` z obiema tabelami w folderze zmiany: `ls context/changes/light-dark-theme-switch/palette/*.png | wc -l` daje 8
- Build i całe testy jednostkowe zielone: `cd szczur-radar-web && npm test && npm run build`
- Testy wyglądu zielone. Zmieniona jest tylko sekcja „Tokeny” w obu projektach 375 (`kitchen-sink-tokeny.png`): `cd szczur-radar-web && npm run visual:docker`

#### Manual Verification:

- Właściciel wybiera wariant A albo B (albo zleca poprawki konkretnych ról) na zrzutach z `palette/`, a wybór jest zapisany w `palette.md`

**Implementation Note**: tu bieg bez nadzoru (`/goal`) się zatrzymuje. Faza 3 startuje dopiero po zapisanym wyborze w `palette.md`.

---

## Phase 3: Wybrana paleta i wzorce

### Overview

Wybrany wariant trafia do `style.css`, wszystkie wzorce F-06 są przerysowane, a wytyczne UI opisują nowe źródło motywu i nowe role.

### Changes Required:

#### 1. Paleta

**File**: `szczur-radar-web/src/style.css`

**Intent**: Wartości ról z wybranego wariantu z `palette.md`, z poprawkami właściciela, w obu blokach. Komentarz nagłówka wskazuje `palette.md` jako źródło wartości, w miejsce tabeli z `admin-panel-ui-hardening`.

**Contract**: wartości ról równe tabeli wybranego wariantu w `palette.md`. Pochodne `--ring`, `--border` i `--border-strong` dalej wyprowadzone z ról.

#### 2. Wzorce F-06

**File**: `szczur-radar-web/visual/__screenshots__/**`

**Intent**: Przerysować wszystkie wzorce w nowej palecie (`npm run visual:update`). axe nie może zgłosić naruszenia kontrastu w żadnym widoku ani w katalogu.

**Contract**: 56 wzorców (54 + 2 sekcji „Motyw”). Opis PR wymienia zmienione grupy (wszystkie widoki i sekcje, nowa paleta) i powód.

#### 3. Wytyczne

**File**: `REPOSITORY_GUIDELINES.md` (sekcja UI, punkt „Tokens”)

**Intent**: Opisać, że motyw to `data-theme` ustawiane przed startem przez skrypt w `index.html` i prowadzone przez `src/theme.ts`, ciemny blok to `:root[data-theme='dark']`, wybór żyje w `localStorage` pod `THEME_STORAGE_KEY`, przełącznik to `ThemeToggle`. Wymienić nowe role i dla każdej, gdzie jej pary kontrastu.

**Contract**: tylko punkt „Tokens” sekcji UI. Zdanie o `prefers-color-scheme` zastąpione.

### Success Criteria:

#### Automated Verification:

- Wartości ról w `style.css` równe tabeli wybranego wariantu w `palette.md`, a test kontrastu (38 przypadków) zielony: `cd szczur-radar-web && npm test`
- Build przechodzi: `cd szczur-radar-web && npm run build`
- Testy E2E zielone: `cd szczur-radar-web && npm run e2e`
- Testy wyglądu zielone na przerysowanych wzorcach (w tym axe na każdym widoku i katalogu): `cd szczur-radar-web && npm run visual:docker`
- `REPOSITORY_GUIDELINES.md` nie opisuje już motywu przez `prefers-color-scheme`: `! grep -n "prefers-color-scheme" REPOSITORY_GUIDELINES.md`
- CI w PR zielone, w tym `web-e2e` (E2E i testy wyglądu): `gh pr checks <PR> --watch` kończy się bez błędu

#### Manual Verification:

- Właściciel akceptuje różnice obrazków w PR („Files changed”): nowa paleta w obu motywach, przycisk motywu w nagłówku, sekcja „Motyw” w katalogu

**Implementation Note**: po akceptacji przechodzą `/10x-impl-review` (z jawnym `phase 3`) i `/10x-archive` na tej samej gałęzi. Merge robi człowiek.

---

## Testing Strategy

### Unit Tests:

- `resolveTheme`: tabela wybór × system, z nieznaną wartością.
- Skrypt z `index.html` w jsdom daje ten sam wynik co `resolveTheme`, ma ten sam klucz zapisu i jest klasyczny w `<head>`.
- `setTheme` / `toggleTheme`: atrybut, zapis, wyłączenie śledzenia systemu.
- Rzucający `localStorage` przy odczycie i zapisie: motyw systemu, przełączenie dalej działa w sesji.
- `ThemeToggle`: `aria-pressed` i klik. `AppHeader`: przycisk obecny.
- Test kontrastu: 19 par × 2 motywy i brak media query motywu.

### Integration Tests:

- E2E `e2e/theme.spec.ts`: 4 ryzyka (przeładowanie, wybór wygrywa z systemem, śledzenie systemu bez wyboru, logowanie stosuje wybór).
- F-06: projekty `dark-*` przez system i skrypt, sekcja katalogu „Motyw” w obu stanach, axe na nowej palecie.

### Manual Testing Steps:

1. Faza 2: wybór wariantu palety na 8 zrzutach w `palette/`.
2. Faza 3: ocena różnic obrazków w PR.

## Performance Considerations

Skrypt inline to kilka linii synchronicznych w `<head>`, bez żądań sieciowych. Słuchacz `matchMedia` jest jeden.

## Migration Notes

Brak danych do migracji. Użytkownik bez zapamiętanego wyboru widzi po wdrożeniu nową paletę w motywie systemu, jak dotąd. Wycofanie to revert commitów, a zapamiętany klucz w `localStorage` stary kod ignoruje.

## References

- Roadmapa: `context/foundation/roadmap.md`, S-46
- PRD: `context/foundation/prd.md:295-296` (FR-040)
- Decyzja nr 5 właściciela: `context/changes/post-m4-ui-readiness-audit/research.md` (sekcja „Decyzje właściciela (2026-11-15)”)
- Makiety: `context/foundation/ui-ux-proposals.md`, `docs/visuals/ui-ux-proposals/`
- F-06: `context/archive/2026-11-16-visual-regression/plan.md`, `szczur-radar-web/visual/targets.ts`
- Tokeny i test kontrastu: `szczur-radar-web/src/style.css`, `szczur-radar-web/src/styleContract.test.ts`
- Wzorzec stanu modułu: `szczur-radar-web/src/auth/session.ts`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Mechanizm motywu i przełącznik

#### Automated

- [x] 1.1 Testy jednostkowe zielone, w tym `theme.test.ts`, `ThemeToggle.test.ts` i test kontrastu na nowym bloku — 16016a4
- [x] 1.2 Typecheck i build produkcyjny przechodzą — 16016a4
- [x] 1.3 `style.css` nie ma już media query motywu — 16016a4
- [x] 1.4 Testy E2E zielone, w tym 4 testy `e2e/theme.spec.ts` — 16016a4
- [x] 1.5 Testy wyglądu zielone po przerysowaniu; zmienione tylko 4 widoki z nagłówkiem w 4 projektach i nowe `kitchen-sink-motyw.png` (18 plików) — 16016a4
- [x] 1.6 Skrypt w `index.html` klasyczny w `<head>` przed modułem aplikacji — 16016a4

### Phase 2: Nowe role i dwa warianty palety

#### Automated

- [x] 2.1 Test kontrastu ma 38 przypadków par (19 × 2 motywy) i jest zielony — 8d88557
- [x] 2.2 Oba warianty przechodzą ten sam test na tymczasowej podmianie, `style.css` wraca do zacommitowanej treści, wynik w `palette.md` — 8d88557
- [x] 2.3 8 zrzutów i `palette.md` z obiema tabelami w folderze zmiany — 8d88557
- [x] 2.4 Build i całe testy jednostkowe zielone — 8d88557
- [x] 2.5 Testy wyglądu zielone; zmieniona tylko sekcja „Tokeny” w obu projektach 375 — 8d88557

#### Manual

- [ ] 2.6 Właściciel wybiera wariant A albo B (albo zleca poprawki) na zrzutach z `palette/`, wybór zapisany w `palette.md`

### Phase 3: Wybrana paleta i wzorce

#### Automated

- [x] 3.1 Wartości ról w `style.css` równe tabeli wybranego wariantu w `palette.md`, test kontrastu zielony — e85114d
- [x] 3.2 Build przechodzi — e85114d
- [x] 3.3 Testy E2E zielone — e85114d
- [x] 3.4 Testy wyglądu zielone na przerysowanych wzorcach, w tym axe — e85114d
- [x] 3.5 `REPOSITORY_GUIDELINES.md` nie opisuje już motywu przez `prefers-color-scheme` — e85114d
- [ ] 3.6 CI w PR zielone, w tym `web-e2e`

#### Manual

- [ ] 3.7 Właściciel akceptuje różnice obrazków w PR
