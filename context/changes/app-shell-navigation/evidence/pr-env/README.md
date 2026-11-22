# S-47: pasek aplikacji w PR env (krok 3.5)

Tylko wyniki pomiarów, bez sekretów. Link aktywacyjny, hasło i ciasteczko sesji były wyłącznie w zmiennych powłoki i w plikach scratchpada, usuniętych po kontroli.

- Data: 2026-11-22
- Środowisko: `szczur-radar-pr-201`, web `https://szczur-radar-web-szczur-radar-pr-201.up.railway.app`
- Sprawdzany commit: `0980f3f` (CI na PR #201: 13 z 13 zielonych)
- Przeglądarka: Chromium przez `playwright-cli`, sesja załadowana z ciasteczka (`state-load`)
- Konto: jednorazowy admin `agent-app-shell-navigation-2@example.com` założony przez `railway ssh … create-user --role admin` (za zgodą właściciela), aktywowany przez `/api/auth/activation`, zalogowany przez `/api/auth/login`. Konta `agent-app-shell-navigation[-2]@example.com` zostają w bazie PR env i znikają razem z nim.
- Uwaga operacyjna: usługa API w PR env zasypia. `railway ssh` bez instancji nie łączy się z kontenerem (wraca ogólny opis konta z `railway.new`), więc API trzeba najpierw obudzić zapytaniem HTTP (`/api/ready`), a dopiero potem wywołać `create-user`.

## Dane z API

`GET /api/freshness` z sesją admina zwrócił `{"sources":[]}`: świeża baza PR env nie ma żadnego udanego przebiegu. Oczekiwany tekst to więc „Dane nie były jeszcze aktualizowane.”. Wariant z datami per źródło („Wykop 13.10.2026, 05:12”) nie był sprawdzany na prawdziwych danych (nie uruchamiałem przebiegu z prawdziwymi kluczami Wykopu i LLM); pokrywają go `e2e/shell.spec.ts` na atrapie i testy jednostkowe `AppShell`.

## Wyniki (commit `0980f3f`)

| Krok | Adres, szerokość | Wynik |
| --- | --- | --- |
| 3 | `/`, 1280 px | jeden `header`, jeden `main`, `h1` „Posty”; `navigation` „Główna”: „Posty” (`aria-current="page"`), „Panel admina” bez; przyciski „Powiadomienia, 0 nieprzeczytanych”, „Ciemny motyw”, „Wyloguj”; e-mail konta widoczny; napis „Dane nie były jeszcze aktualizowane.” widoczny, `scrollWidth` 1280 |
| 4 | `/admin`, 1280 px | jeden `header`, jeden `main`, `h1` „Panel admina”; „Panel admina” z `aria-current="page"`, „Posty” bez; `scrollWidth` 1280 |
| 4 | strona przebiegu | pominięta: baza PR env nie ma żadnego przebiegu (krok warunkowy „jeśli przebieg istnieje”); pokrywa ją `e2e/shell.spec.ts` na atrapie |
| 5 | `/`, 375 px | napis o danych widoczny, e-mail ukryty, „Wyloguj” widoczne, `scrollWidth` 375 |
| 5 | `/`, 320 px | napis o danych widoczny, e-mail ukryty, `scrollWidth` 320 |
| 6 | „Wyloguj” | adres `/login`, brak `navigation` „Główna” i brak `header` |

## Błąd znaleziony tą kontrolą i naprawiony

Pierwsze wdrożenie (commit `3469327`, potem `8774b1b`) przy 375 px miało `scrollWidth` 387 (poziome przewijanie o 12 px). Przyczyna: poniżej 900 px `.freshness` w `AppBar.vue` ma `flex: 0 0 100%` i `padding`, a bez `box-sizing: border-box` padding dodawał się do szerokości paska. Dotyczyło każdego wariantu daty. Atrapa tego nie pokazała: żaden test nie sprawdzał `scrollWidth`, a stare wzorce `view-*-375` uwieczniły szerokość 387 px.

Poprawka w `0980f3f`:

- `box-sizing: border-box` na `.freshness` poniżej 900 px,
- dwa testy w `e2e/shell.spec.ts` (data danych i napis o braku aktualizacji nie poszerzają strony przy 375 px), czerwone bez poprawki,
- osiem wzorców `view-{feed,post,admin,run}` w `light-375` i `dark-375`: szerokość 375 px zamiast 387 px, wiersz daty zawija się o linię.
