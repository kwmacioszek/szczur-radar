# Review fixes — feed-view-ui-hardening

Źródło: `context/changes/feed-view-ui-hardening/reviews/impl-review.md`, fazy 1, 2, 3, 4.

## F1 — Wielowierszowy CSS omija skan skali

- [x] Naprawa wybrana przez użytkownika: „napraw teraz” (2026-10-12).
- [x] `szczur-radar-web/src/styleContract.test.ts`: dopasowywanie na pełnym źródle, numery linii właściwości i zachowanie jednej pozycji na linię.
- [x] Cztery testy regresyjne: wielowierszowe odstępy/pismo/promień, lokalizacje, kolory/klasy palety, dozwolone wartości tokenów i układu.
- [x] Przed poprawką dwa nowe testy wykazały błąd; po poprawce `npm test` — 122/122 PASS, `npm run build` — PASS, `git diff --check` — PASS.

Status: **FIXED**. Progress planu pozostał bez zmian.

## F2 — Brak dowodu „Starsze” na prawdziwej drugiej stronie

- [x] Użytkownik wybrał „napraw teraz” (2026-10-12). Autoryzacja pozostaje aktualna; wznowienie nie wymaga ponownego wyboru naprawy.
- [x] Railway MCP potwierdził PR env `szczur-radar-pr-48` (`918ff4ef-b827-4e31-b6ca-edc2e57371ab`) w projekcie `decf00e4-f5a4-43df-b338-6698be87c5a1`; web deployment `fb3b49bc-575c-48f9-99aa-1a5b98957e1b`, commit `df6213a602a4d49fe1fdc53b2362f0135bd4848b`, po wejściu w aplikację **SUCCESS**.
- [x] Przygotowano i dostosowano do runtime CLI `verify-pr-env-paging.js`: 1280/375 px, oba motywy, odpowiedź API, karty, etykieta, przewinięcie, fokus, PNG; zwracany JSON zapisany jako artefakt pomiarów.
- [x] Po odblokowaniu uprawnień uzyskano zalogowaną sesję Playwright w PR #48; API potwierdziło rolę admina.
- [x] Sprawdzono rzeczywiste `total`: **34**, pierwsza strona 34, brak aktywnych przebiegów, Wykop skonfigurowany.
- [x] Przez formularz `/admin` uruchomiono jeden dodatkowy dzień **2026-10-10**. Run `e92a403a-73d2-4476-b67f-2fa66ffb04af`: partial, 39 pobranych, 37 ocenionych, 2 nieudane. Automatyczny retry `85edf5a0-5614-493d-84ee-3d9033a9b3d9`: succeeded, 1 oceniony. Poczekano na zakończenie obu; końcowe **total 72**. PAGE_SIZE 50 bez zmian, dane rzeczywiste.
- [x] Wykonano `playwright-cli run-code --filename=context/changes/feed-view-ui-hardening/follow-ups/verify-pr-env-paging.js --raw`: cztery warianty PASS, 22 posty na stronie 2 z 2, poprawne rzeczywiste API i karty, scrollY 0, fokus H1#feed-title, brak poziomego przepełnienia.
- [x] Obejrzano wszystkie cztery PNG, zapisano JSON `ui-after/f2-pr48-starsze-2026-10-12T20-09-14-963Z.json`, pomiary i commit web w README; F2 oznaczone **FIXED**.

Status wykonania: **FIXED — weryfikacja rzeczywistego UI PASS**. F1 i F2 rozstrzygnięte, triage zakończone. Progress planu zachowano.

Historia: wcześniej CUA i Node MCP wymagały zgody niedostępnej przy polityce `never`; terminal nie rozwiązywał DNS, a Playwright nie miał dostępu do cache. Po zmianie uprawnień użyto eskalowanych połączeń i Playwright CLI. Zdalnie zmieniono wyłącznie dane PR #48 przez autoryzowany przebieg; konfiguracja usług pozostała bez zmian.
