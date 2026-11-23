# Kontrola w PR env (4.6)

- **Data**: 2026-11-23, ok. 18:30 UTC
- **Środowisko**: `szczur-radar-pr-202`, https://szczur-radar-web-szczur-radar-pr-202.up.railway.app
- **Wdrożenia**: `szczur-radar-web` na `2712cb0` (`SLEEPING`, czyli serwuje; `f67f345` to same dokumenty, `SKIPPED`), `szczur-radar-api` na `fed862a` (`SUCCESS`; późniejsze commity nie zmieniają API). `/api/ready` → 200.
- **Konto**: jednorazowe `agent-shared-view-states-…@example.com`, `--role admin`, założone przez `railway ssh … create-user`, aktywowane losowym hasłem i zalogowane przez `curl` (procedura z `lessons.md`). Link, token, hasło i ciasteczko były tylko w zmiennych powłoki i w pliku w scratchpadzie, usuniętym po kontroli. Konto zostaje w bazie PR env.
- **Przeglądarka**: Chromium przez `playwright-cli` (sesja z ciasteczkiem `sr_session`), 1280×900, potem 375×812.

## Wyniki

| Krok | Sprawdzenie | Wynik |
|------|-------------|-------|
| 2 | `GET /api/posts?page=0&size=50` przez `curl` z ciasteczkiem | `total: 0`, `items: 0` (baza PR env nie ma postów) |
| 2 | `/` po wczytaniu: „Wczytywanie…” w `main`, `[aria-busy="true"]`, `alert` | 0, 0, 0 |
| 2 | `navigation` „Strony” (ma być wtedy i tylko wtedy, gdy `total > 50`) | brak, zgodnie z `total: 0`; klik „Starsze” nie dotyczy |
| 2 | nagłówki 2 w `main` | „Brak ocenionych postów” (pusty stan bez filtra) |
| 3 | `/?q=zzzzzz`: nagłówek 2 | „Brak postów dla tych filtrów”; linia wyniku `status`: „Żaden post nie pasuje do filtra.”; przycisk „Wyczyść filtry” w formularzu i w pustym stanie |
| 4 | `/admin`: `aria-busy` pięciu sekcji („Nowy przebieg”, „Dni bez danych”, „Jakość ocen (7 dni)”, „Ostatnie przebiegi”, „Nieudane posty”) | żadna nie ma atrybutu; `[aria-busy="true"]` na stronie: 0 |
| 4 | `/admin`: `alert` | 0 |
| 4 | `/admin`: przycisk „Odśwież” | jest, aktywny |
| 5 | 375 px, `/`: `innerWidth` / `scrollWidth` | 375 / 375 |
| 5 | 375 px, `/admin`: `innerWidth` / `scrollWidth` | 375 / 375 |

## Powtórka na danych (przegląd fazy 4, F2)

Pierwsza kontrola przeszła przy pustej bazie: przy `total: 0` sprawdzone było tylko to, że „Strony” się nie rysuje. Dlatego agent uruchomił w PR env jeden przebieg zakresu i powtórzył kroki 2, 4 i 5 na danych.

- **Kiedy i jak**: 2026-11-23, ok. 19:00 UTC, drugie jednorazowe konto admina według tej samej procedury, sekrety usunięte po kontroli.
- **Przebieg**: `0c006b23-…`, dzień 2026-11-21, źródła `wykop` i `reddit` (prawdziwe źródła i LLM).
- **Wynik przebiegu**: `partial`, bez powodów „wymaga uwagi”. Pobrano 325 postów (Wykop 219, Reddit 106, oba `succeeded`), 0 martwych i 0 pominiętych.

| Krok | Sprawdzenie | Wynik |
|------|-------------|-------|
| 2 | `GET /api/posts?page=0&size=50` | `total: 265`, 50 pozycji |
| 2 | `/` po wczytaniu | „Wczytywanie…” 0, 50 artykułów, linia wyniku „Ocenione posty: 265”, `navigation` „Strony” jest: „Nowsze · Strona 1 z 6 · Starsze” |
| 2 | klik „Starsze” | adres `/?page=1`, „Strona 2 z 6”, 50 artykułów, fokus na nagłówku 1, `scrollY` 0 |
| 4 | `/admin` z przebiegiem | `[aria-busy="true"]` 0, `alert` 0, „Odśwież” aktywne, w „Ostatnie przebiegi” 1 odnośnik przebiegu |
| 5 | 375 px, `innerWidth` / `scrollWidth` | `/`: 375 / 375; `/?page=1`: 375 / 375; `/admin`: 375 / 375 |

Konta i przebieg zostają w bazie PR env i znikają razem z nim.
