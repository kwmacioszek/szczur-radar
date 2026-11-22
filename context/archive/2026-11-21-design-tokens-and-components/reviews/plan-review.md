<!-- PLAN-REVIEW-REPORT -->
# Plan Review — F-08 · Tokeny, kroje i komponenty bazowe z projektu F-07

- **Plan**: [plan.md](../plan.md)
- **Mode**: Deep
- **Date**: 2026-11-21
- **Verdict**: REVISE → SOUND po triage
- **Findings**: 0 CRITICAL, 6 WARNING, 3 OBSERVATION
- **Triage**: 9/9 FIXED

## Verdicts

| Dimension | Przed triage | Po triage |
| --- | --- | --- |
| End-State Alignment | WARNING | PASS |
| Lean Execution | PASS | PASS |
| Architectural Fitness | WARNING | PASS |
| Blind Spots | WARNING | PASS |
| Plan Completeness | WARNING | PASS |

## Grounding

Grounding: 24/24 paths ✓, 5/5 symbols ✓, brief↔plan ✓, Progress↔Phase 26/26 ✓.

`design-check doc --stage 4` i kontrola 73 par kontrastu w obu motywach przeszły. Lokalna próba odczytu CDP zatrzymała się na starcie Chromium. Po triage ponownie sprawdzono spójność 26 kryteriów z `Progress`, odnośniki i 25 unikalnych identyfikatorów w spricie.

## Findings

### F1 — Zmiana bazy `rem` zmienia szerokości widoków

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — decyzja dotyczy zakresu F-08.
- **Dimension**: End-State Alignment
- **Location**: Fazy 1 i 3
- **Detail**: Obecna baza 18 px daje feedowi limit 864 px na desktopie przy `48rem`. Po zmianie bazy na 16 px ten sam limit miałby 768 px.
- **Fix A ⭐ Recommended**: Ustawić `html` na `100%`, przenieść typografię na `body` i zachować wskazane szerokości.
  - Strength: Zachowuje dotychczasowy zakres zmiany.
  - Tradeoff: Wymaga przeliczenia reguł układu zależnych od bazy `rem`.
  - Confidence: HIGH — różnica wynika z obecnych deklaracji CSS.
  - Blind spot: Pomiary szerokości w przeglądarce są częścią implementacji.
- **Fix B**: Dopuścić proporcjonalną zmianę szerokości wraz z bazą `rem`.
  - Strength: Ogranicza liczbę reguł przejściowych.
  - Tradeoff: Rozszerza zakres akceptacji zrzutów o zmianę szerokości.
  - Confidence: HIGH — mechanizm wynika z jednostek CSS.
  - Blind spot: Efekt wizualny wymagałby akceptacji właściciela.
- **Decision**: FIXED — Fix A. Plan zawiera tabelę zachowywanych wymiarów i pomiar przy 1024 oraz 1280 px.

### F2 — Pomiar kadru feedu może wyprzedzić załadowanie krojów

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — poprawka dotyczy kolejności operacji.
- **Dimension**: Blind Spots
- **Location**: Faza 1, bramka kroju i zrzuty
- **Detail**: `views.spec.ts` mierzy położenie siódmej karty przed zrzutem. Zmiana kroju między pomiarem a zrzutem może przesunąć granicę kadru.
- **Fix**: Poczekać na kroje przed pomiarem, ustawieniem stanu kontrolki i odczytem CDP.
- **Decision**: FIXED — plan wskazuje wywołania wspólnego `waitForFonts(page)` w trzech specach.

### F3 — Kryterium 3.7 nie obejmuje uwierzytelnienia do feedu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — repozytorium ma procedurę konta testowego.
- **Dimension**: Plan Completeness
- **Location**: Faza 3, kryterium 3.7
- **Detail**: Router przekierowuje anonimowego użytkownika na `/login`. Samo `document.fonts` potwierdza ładowanie rodzin, a kryterium obejmuje krój faktycznie użyty do narysowania tekstu.
- **Fix**: Użyć konta jednorazowego w PR env, potwierdzić wejście na feed i odczytać faktycznie użyte kroje przez CDP.
- **Decision**: FIXED — kryterium opisuje konto z rolą `user`, sesję, odczyt CDP i kontrolę MIME użytych plików.

### F4 — Globalne style pól zmienią formularz logowania

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — wystarczy doprecyzować selektory.
- **Dimension**: Architectural Fitness
- **Location**: Faza 2, pola tekstowe
- **Detail**: Globalna wysokość objęłaby pola logowania z lokalnym stylem. Specyficzność selektora hover z prototypu może nadpisać czerwoną krawędź błędnego pola.
- **Fix**: Wyłączyć formularz logowania z nowych reguł pól do S-49 i nadać stanowi `aria-invalid` pierwszeństwo nad hover.
- **Decision**: FIXED — plan określa wyjątek `login-form`, użycie `:where()` i pomiar krawędzi oraz cienia błędu.

### F5 — Katalog pomija stany pól i przełącznika motywu

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — trzeba wskazać próbki i cele zrzutów.
- **Dimension**: End-State Alignment
- **Location**: Faza 2, katalog i `STATES`
- **Detail**: Dla pól plan wskazywał tylko fokus wyszukiwania. Brakowało próbki błędu oraz celów hover i fokusu dla motywu.
- **Fix**: Wypisać próbki pól, powiązać błąd przez `aria-describedby` i dodać cele hover oraz fokusu.
  - Strength: Cele pokrywają stany obiecane w arkuszu.
  - Tradeoff: Cztery dodatkowe cele zrzutów w obu motywach.
  - Confidence: HIGH — zakres wynika z arkusza i `STATES`.
  - Blind spot: Kadry przy 375 px wymagają weryfikacji w implementacji.
- **Decision**: FIXED — plan uwzględnia próbki z arkusza, cztery cele dla pola e-mail i motywu oraz obie wartości `aria-pressed`.

### F6 — Sam `gap` nie zastąpi marginesu komunikatu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — zmiana dotyczy konkretnych odbiorców.
- **Dimension**: Architectural Fitness
- **Location**: Fazy 2 i 3, `StatusMessage` i jego odbiorcy
- **Detail**: Gałęzie błędu w układzie blokowym polegają na marginesie komunikatu. Nowy akapit w komponencie ma też domyślny margines.
- **Fix**: Wyzerować margines akapitu i przypisać odstępy odbiorcom.
- **Decision**: FIXED — plan wskazuje lokalne odstępy oraz pomiar od komunikatu do przycisku ponowienia w feedzie i powiadomieniach.

### F7 — Próbki ostrożności wymagają ograniczenia do trzech kierunków

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — wystarczy doprecyzować przykłady.
- **Dimension**: Blind Spots
- **Location**: Faza 2, katalog
- **Detail**: Kontrakt §7.2 przewiduje `hedged` dla `bullish`, `bearish` i `neutral`.
- **Fix**: Ograniczyć próbki z dopiskiem do kierunków dopuszczonych przez kontrakt.
- **Decision**: FIXED — plan określa sześć przykładów podstawowych i trzy z dopiskiem.

### F8 — `AppButton` ma zależności poza własnym testem

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — wystarczy zapisać istniejący kontrakt.
- **Dimension**: Architectural Fitness
- **Location**: Faza 2, przycisk
- **Detail**: Test dzwonka wymaga `secondary`, menu przywraca fokus przez korzeń komponentu, a „Wyloguj” polega na treści slotu podczas `busy`.
- **Fix**: Zachować natywny korzeń, klasy wariantów i treść slotu przy `busy` bez `busyLabel`.
- **Decision**: FIXED — plan zapisuje te zależności i wskazuje odpowiednie asercje.

### F9 — Katalog i Vitest potrzebują listy nazw ikon podczas wykonania

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — wystarczy jedna deklaracja.
- **Dimension**: Architectural Fitness
- **Location**: Faza 2, ikony
- **Detail**: Unia `IconName` znika po kompilacji. Galeria i test zgodności potrzebują listy identyfikatorów.
- **Fix**: Wyprowadzić typ z krotki `ICON_NAMES` i użyć krotki w galerii oraz w teście.
- **Decision**: FIXED — plan wskazuje wspólny moduł `iconNames.ts` oraz porównanie list z identyfikatorami w spricie.
