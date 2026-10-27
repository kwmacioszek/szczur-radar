# M-4 (kandydat): weryfikacja i regresja — zakres

Zapis decyzji użytkownika z 2026-10-27: po M-3 (drugie źródło i izolacja źródeł) przychodzi **M-4: weryfikacja i regresja**, a dopiero po nim **M-5: nowe UI/UX z testami E2E na Playwright** (`context/foundation/ui-ux-proposals.md`). To nie jest plan ani podział na wycinki — zakres potwierdzi `/10x-test-plan`, a wycinki `/10x-roadmap` przy otwieraniu M-4.

## Co dziś uwiera

- Nie wiadomo, czy testy backendu są poprawne: zielony test mówi, że kod robi to, co test sprawdza, a nie że test łapie błąd.
- Nie ma planu testowania: testy powstają przy kolejnych wycinkach, bez mapy ryzyk.
- Weryfikacja wycinków w PR env jest klikana ręcznie (przez użytkownika albo agenta).
- Nic poza pojedynczymi testami nie chroni przed powrotem starych błędów.

## Cel

Większość weryfikacji wycinka i regresja działają same w CI przy każdym PR; ręcznie zostaje krótka, nazwana lista rzeczy, których CI nie widzi.

## Kandydaci na zakres (do potwierdzenia)

- **Plan testów:** `/10x-test-plan` → `context/foundation/test-plan.md` (ryzyka, przegląd obecnych testów, fazy). To sam dokument, więc może powstać wcześniej, np. pod koniec M-3.
- **Poprawność testów backendu:** testy mutacyjne (np. PIT) na rdzeniu — przebieg i warunkowe zapisy stanu, walidator kontraktu V2, rozliczanie prób oceny — oraz przegląd jakości testów (asercje na zachowanie zamiast na mocki, pełne ciała odpowiedzi, brak zależności od zegara).
- **Scenariusze API zamiast ręcznego klikania:** pełne API z Postgresem (Testcontainers), atrapy źródeł i LLM z prawdziwych payloadów w `szczur-radar-api/src/test/resources/fixtures/`; scenariusze odtwarzają ręczne weryfikacje PR env z ostatnich wycinków (np. S-13, S-17, S-18, S-20, S-27).
- **Smoke w przeglądarce na atrapie API:** konfiguracja Playwright (`/10x-e2e-setup`) i kilka przepływów na `szczur-radar-web/dev/mock-api.js` — logowanie i strażnik sesji, feed z filtrem, post z oceną, strona przebiegu. M-5 rozbudowuje je pod nowe widoki.
- **Regresja w CI:** wszystko powyżej chodzi przy każdym PR w `ci.yml`, z kontrolą czasu CI na self-hosted runnerach.

## Kandydat na „done when”

- Ręczne kroki weryfikacji PR env ostatnich wycinków chodzą jako scenariusze w CI.
- Mutanty, które przeżyły na rdzeniu, są zabite testem albo świadomie zaakceptowane z powodem.
- Ręczna lista po deployu zawiera tylko pozycje z sekcji niżej.

## Co zostanie ręczne (CI tego nie zobaczy)

- Dostarczenie alertu do Telegrama (healthchecks.io).
- Przerwanie przebiegu przez deploy na Railway i zachowanie Wait for CI.
- Prawdziwe API Wykopu, Reddita i dostawcy LLM.
- Harmonogram cronów.

## Granice z M-3 i M-5

- M-3: S-23 i S-26 zostają w M-3, bo chronią S-20 i S-27. S-24 (kontrakt SPA ↔ API) pasuje też do M-4 — do decyzji przy otwieraniu M-4, jeśli nie będzie jeszcze `done`.
- M-5: testy E2E w przeglądarce na nowym UI; M-4 robi tylko smoke na obecnych widokach.
