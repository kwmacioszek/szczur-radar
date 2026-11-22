<!-- IMPL-REVIEW-REPORT -->
# Implementation Review — F-08 · Tokeny, kroje i komponenty bazowe

- **Plan**: [plan.md](../plan.md)
- **Scope**: Ukończone fazy pełnego planu
- **Reviewed phases**: 1, 2, 4
- **Date**: 2026-11-21
- **Revision**: `2597dfe..f410735`
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 CRITICAL, 3 WARNING, 2 OBSERVATION

Punkty automatyczne faz 1, 2 i 4 sprawdzono na `f410735`. Zakres commitów tych faz obejmuje `2813926`, `e16cfd4` i `3733e79`. Faza 3 czeka na akceptację wyglądu w punkcie 3.8. Stan planu w chwili przeglądu to 25/26.

## Verdicts

| Dimension | Verdict |
| --- | --- |
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — `design-check app` przepuszcza rozjazdy tokenów

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — zmiana parsera wymaga sprawdzenia obu trybów narzędzia.
- **Dimension**: Plan Adherence
- **Location**: [tools/design-check/design-check.mjs:115](../../../../tools/design-check/design-check.mjs#L115), `tokenBlocks` i `checkApp`
- **Detail**: Odczyt wymaga średnika po każdej deklaracji i zbiera deklaracje z zagnieżdżonych reguł do tej samej mapy. W kopii aplikacji dodatkowy ostatni token `--review-extra: 1` przed `}` dał kod 0. Druga próba zmieniła właściwe `--space-9` na `5rem` i dodała `4rem` wewnątrz zagnieżdżonego `@media`. Ona także dała kod 0, mimo rozjazdu wartości bazowej. Obie próby naruszają kontrakt porównania tokenów w obie strony.
- **Fix**: Odczytać deklaracje we właściwym bloku i na właściwym poziomie zagnieżdżenia, obsłużyć ostatnią deklarację bez średnika i dodać obie próbki do selftestu.
  - Strength: Wykrywa oba odtworzone rozjazdy bez zmiany kontraktu CLI.
  - Tradeoff: Trzeba utrzymać zgodność parsowania z testem kontrastu aplikacji.
  - Confidence: HIGH — oba przypadki odtworzono na tymczasowej kopii aplikacji.
  - Blind spot: Docelowy zakres obsługiwanej składni CSS wymaga zapisania w testach.
- **Decision**: FIXED — `design-check` i test kontrastu czytają bezpośrednie deklaracje, także ostatnią bez średnika. Zagnieżdżona reguła nie zastępuje wartości bazowej.
- **Weryfikacja poprawki**: Oba przypadki zwracają kod 1 i wskazują właściwy token. `design-check app`, `contrast` i `selftest` przeszły, podobnie jak build i 1060 testów jednostkowych.

### F2 — Hover usuwa wygląd aktywnego przycisku motywu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — wystarczy zmienić pierwszeństwo reguł CSS.
- **Dimension**: Plan Adherence
- **Location**: [szczur-radar-web/src/components/AppButton.vue:95](../../../../szczur-radar-web/src/components/AppButton.vue#L95)
- **Detail**: `.icon-only:hover:not(:disabled)` ma wyższą specyficzność od reguł `aria-pressed` i `aria-expanded`. Ciemny przełącznik nadal sygnalizuje `aria-pressed="true"`, a jego tło pod kursorem zmienia się z `--selected` na `--surface-2`. Wzorzec dark hover zawiera 1352 piksele `--surface-2` i 0 pikseli `--selected`. Wzorzec fokusu zawiera 1352 piksele `--selected`. Zielony test zrzutu utrwala obecne pierwszeństwo.
- **Fix**: Zastosować `:where(:hover:not(:disabled))` w regule ikonowej, sprawdzić kolory aktywnego przycisku pod kursorem i odświeżyć dotknięte wzorce.
- **Decision**: FIXED — hover ustępuje `aria-pressed` i `aria-expanded`. Reguły hover wariantu głównego i dużego pomijają tryb ikonowy.
- **Weryfikacja poprawki**: 1060 testów jednostkowych i 14 kontroli Chromium przeszło. Sprawdzono kolory, fokus i stan wyłączony w ciemnym i jasnym motywie przy 375 i 1280 px. Jedyny zmieniony wzorzec to `dark-375/state-motyw-hover.png`. Zmiana pikseli mieści się w przycisku.

### F3 — SVG roadmapy pokazuje nieaktualny status F-08

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — synchronizacja danych i ponowne wygenerowanie pliku.
- **Dimension**: Pattern Consistency
- **Location**: [tools/render_roadmap_svg.py:235](../../../../tools/render_roadmap_svg.py#L235), [docs/visuals/roadmap.svg:1362](../../../../docs/visuals/roadmap.svg#L1362)
- **Detail**: Markdown ma `in-progress` i nową pozycję w `Parked`. Dane generatora i SVG nadal pokazują F-08 jako `ready`. Reguła w `REPOSITORY_GUIDELINES.md:26` wymaga synchronizacji przy zmianach roadmapy.
- **Fix**: Zaktualizować dane i liczniki generatora zgodnie z Markdownem, a następnie uruchomić `python3 tools/render_roadmap_svg.py`.
- **Decision**: FIXED — F-08 ma w danych generatora status `progress`, a nowa pozycja Parked „Test zgodności projektu w CI” (etykieta `F-08`) jest w grupie „Operacje i skala”. SVG wygenerowano od nowa.
- **Weryfikacja poprawki**: `docs/visuals/roadmap.svg` ma `data-status="progress"` dla F-08, M-5 pokazuje „1 w toku” i „0 gotowych do planowania”, Backlog ma 26 pozycji.

### F4 — Ikonowy przycisk dopuszcza pustą nazwę dostępną

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — zmiana dotyczy kontraktu jednego komponentu.
- **Dimension**: Safety & Quality
- **Location**: [szczur-radar-web/src/components/AppButton.vue:27](../../../../szczur-radar-web/src/components/AppButton.vue#L27)
- **Detail**: Kontrola nazwy działa jako jednorazowe ostrzeżenie developerskie. `aria-label` złożone z samych spacji przechodzi sprawdzenie. Aktualne wywołania podają prawidłowe nazwy, a nadchodzące widoki będą ponownie używać tego wariantu.
- **Fix**: Wymagać nazwy w API trybu ikonowego i dodać test, który odrzuca pusty lub biały ciąg znaków.
- **Decision**: FIXED — ostrzeżenie traktuje `aria-label` i `aria-labelledby` z samych białych znaków jak brak nazwy. Nowy test w `AppButton.test.ts` sprawdza `aria-label="  "`.
- **Weryfikacja poprawki**: `AppButton.test.ts` i `ThemeToggle.test.ts` przeszły, 14/14.

### F5 — Diagnostyka pomija brakujące pliki aplikacji, gdy brakuje też wzorca

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — dwa sprawdzenia ścieżek mogą działać niezależnie.
- **Dimension**: Safety & Quality
- **Location**: [tools/design-check/design-check.mjs:286](../../../../tools/design-check/design-check.mjs#L286), [porównanie kopii](../../../../tools/design-check/design-check.mjs#L308)
- **Detail**: Na dwóch pustych katalogach opcje `--design` i `--web` dały poprawny kod 1. Dla CSS, par kontrastu i sprite’a wskazane zostały wyłącznie braki po stronie projektu. Ścieżki aplikacji są pomijane przez skróconą ewaluację operatorów logicznych.
- **Fix**: Sprawdzić istnienie obu ścieżek przed warunkiem pomijającym porównanie danych.
- **Decision**: FIXED — helper `bothExist` w `checkApp` sprawdza obie ścieżki przed warunkiem dla tokenów, par kontrastu i sprite’a. Selftest ma przypadek `app/empty`: dwa puste katalogi zgłaszają brak każdego z trzech plików po obu stronach. README opisuje ten przypadek.
- **Weryfikacja poprawki**: Na dwóch pustych katalogach jest 6 braków (3 + 3) i kod 1. `design-check app` dał kod 0, a `selftest` kod 0. Na kopii ze starą logiką przerywania `app/empty` był czerwony.

## Weryfikacja

| Kryteria | Polecenie lub próba | Wynik |
| --- | --- | --- |
| 1.1, 2.1, 4.6 | `npm test` | 1058/1058, 33 pliki |
| 1.2, 2.2, 4.6 | `npm run build` | Kod 0 na Node 24.21.0, typecheck i build przeszły |
| 1.3, 2.3 | `npm run e2e` | 22/22, diff w `szczur-radar-web/e2e/` pusty |
| 1.4 | Skan nazw `--border-strong`, `--sans`, `--mono` w `src/` | Brak trafień |
| 1.5 | `node tools/design-check/design-check.mjs contrast szczur-radar-web/src/style.css szczur-radar-web/src/contrastPairs.json` | Kod 0, 73 pary w obu motywach |
| 1.6 | `npm run visual:docker -- visual/font.spec.ts` | 1 zaliczony, 3 celowo pominięte |
| 1.7, 1.8 | `docker build -t szczur-radar-web:f08-review ./szczur-radar-web` i sprawdzenie MIME w tymczasowych kontenerach | Plik obecny: `font/woff2`. Plik usunięty: HTTP 200, `text/html; charset=utf-8`, co odrzuca warunek smoke |
| 1.8 | Test kroju w Dockerze na kopii bez łacińskiego Hanken Grotesk i po przywróceniu pliku | Kod 1 na asercji „Hasło”, pojawił się Liberation Sans. Po przywróceniu kod 0 |
| 2.4 | `npm test -- src/components/AppIcon.test.ts` na kopii bez `i-bell` i po przywróceniu symbolu | 1 oczekiwana porażka. Po przywróceniu 29/29 |
| 2, katalog | Wybrane testy `visual/kitchen-sink.spec.ts` na obu projektach 375 px z `--update-snapshots=none` | 6/6, w tym axe oraz hover i fokus motywu |
| 4.1 | `node tools/design-check/design-check.mjs app` | Kod 0 na aktualnym drzewie. Dodatkowe przypadki opisano w F1 |
| 4.2 | `node tools/design-check/design-check.mjs selftest` | Kod 0, próbki `app` przechodzą |
| 4.3 | Zmiana `--space-9` na `5rem` w kopii aplikacji, następnie przywrócenie | Kod 1, `VIOLATION [app]` z nazwą i wartościami roli. Po przywróceniu kod 0 |
| 4.4 | `node tools/design-check/design-check.mjs doc --stage 4` | Kod 0 |
| 4.5 | Skan `app-pairs` w `tools` i `REPOSITORY_GUIDELINES.md` oraz sprawdzenie pliku | Brak odwołań i pliku |

Pierwsze uruchomienia builda i Vitest wybrały lokalne Node 18. Powtórzono je z Node 24. Sandbox blokował start Chromium i lokalny port, więc selftest i E2E powtórzono poza sandboxem. Kontrole katalogu zakończyły się sukcesem na izolowanej kopii po błędzie startu serwera w pierwszym przebiegu.
