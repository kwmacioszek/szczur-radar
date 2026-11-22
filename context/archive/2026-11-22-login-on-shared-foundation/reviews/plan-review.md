<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-49: logowanie i aktywacja na wspólnym fundamencie

- **Plan**: context/changes/login-on-shared-foundation/plan.md
- **Mode**: Deep
- **Date**: 2026-11-22
- **Verdict**: REVISE → SOUND (po poprawkach)
- **Findings**: 0 critical, 4 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | WARNING |
| Plan Completeness | WARNING |

## Grounding
14/14 paths ✓, 7/8 symbols ✓ (`.btn` ✗), brief↔plan ✓, Progress↔Fazy ✓ (6/8/8/10+1)

## Findings

### F1 — `.auth .btn` nie trafi w AppButton

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Plan Completeness
- **Location**: Faza 1 — AuthLayout, Contract
- **Detail**: Selektor `.auth .btn` pochodzi z prototype.css:2138, a AppButton renderuje `<button class="primary">` bez `btn` (AppButton.vue:37-40). Przycisk zostałby wąski, a `visual:update` utrwaliłby to we wzorcach.
- **Fix**: Selektor `:deep(form > button[type='submit'])` w kontrakcie AuthLayout, asercja w AuthLayout.test.ts.
- **Decision**: FIXED

### F2 — FormField bez szerokości pola, sprzeczność przy usuwaniu stylów katalogu

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zastanów się
- **Dimension**: Architectural Fitness
- **Location**: Faza 1 — FormField (2) i Katalog komponentów (4)
- **Detail**: Plan usuwał cały blok `.field-*` (KitchenSinkView.vue:1021-1066), a jednocześnie zostawiał pola wyszukiwania `.input-wrap` bez FormField, choć te pola z tego bloku korzystają. Kontrakt FormField pomijał `width: 100%`, a globalna reguła pól (style.css:366-383) szerokości nie ustawia. Modyfikator `.field.wide` nie miał właściciela.
- **Fix**: FormField przejmuje cały układ pola (siatka `--space-1`, etykieta, `:deep(...) { width: 100% }`). Pola wyszukiwania przechodzą na FormField (w slocie `.input-wrap`). `.wide` zostaje w katalogu jako klasa położenia. Stany `state-pola-*` mają zostać bez zmiany pikseli.
  - Strength: Jedno źródło stylu pola; kryterium 1.5 zostaje osiągalne.
  - Tradeoff: Więcej pól katalogu do przepięcia w fazie 1.
  - Confidence: HIGH — stan katalogu i reguł globalnych sprawdzony w kodzie.
  - Blind spot: Stany `state-pola-*` pilnuje kryterium 1.5.
- **Decision**: FIXED

### F3 — Błąd przy polu niesłyszalny, gdy fokus już jest na polu

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — prawdziwy kompromis; zastanów się
- **Dimension**: Blind Spots
- **Location**: Faza 1 — FormField Contract; Critical Implementation Details
- **Detail**: Plan opierał ogłoszenie błędu na przeniesieniu fokusu. Gdy fokus już stoi na polu (Enter w „Nowe hasło”, błąd serwera po Enter, ten sam błąd po raz drugi), czytnik ekranu nic nie ogłasza. Dziś ActivateView ogłasza każdy błąd (`role="alert"`), więc to byłby regres.
- **Fix**: Stały kontener `aria-live="polite"` w FormField, który bez błędu nie zajmuje miejsca. ActivateView czyści `problem` i czeka na `nextTick` przed ustawieniem nowego. Test jednostkowy regionu.
  - Strength: Ogłoszenie nie zależy od fokusu; wygląd i kontrakt E2E bez zmian.
  - Tradeoff: Przy przeniesieniu fokusu czytnik może przeczytać błąd dwa razy.
  - Confidence: MED — znany wzorzec, niesprawdzony w NVDA/VoiceOver.
  - Blind spot: axe tego nie wykryje; pilnuje tylko test jednostkowy.
- **Decision**: FIXED

### F4 — „Obecne przypadki zostają”, a cztery testy aktywacji muszą się zmienić

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Plan Completeness
- **Location**: Faza 3 — Testy widoku i E2E
- **Detail**: ActivateView.test.ts:118, :127, :140 i :149 szukają błędu hasła jako `[role="alert"]`. Przypadku `too_long` nie było na liście nowych przypadków.
- **Fix**: Kontrakt wymienia te cztery testy jako przepisane na błąd przy polu i dodaje `too_long`.
- **Decision**: FIXED

### F5 — Kliknięcie w `ready` utrwala hover przycisku we wzorcach błędu

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Blind Spots
- **Location**: Faza 4 — Widoki błędu w zrzutach
- **Detail**: Po kliknięciu kursor zostaje nad przyciskiem głównym, więc wzorzec pokazałby jego stan hover.
- **Fix**: Wysyłanie formularza klawiszem Enter w polu hasła.
- **Decision**: FIXED

### F6 — Komentarze nieaktualne po usunięciu `.card` i stylu logowania

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Blind Spots
- **Location**: Fazy 2–3 — style.css
- **Detail**: `PostCard.vue:49` („the centred login card”) i `style.css:314` („the filled login button”) przestałyby być prawdziwe.
- **Fix**: Poprawienie obu komentarzy dopisane do kontraktów faz 2 i 3.
- **Decision**: FIXED
