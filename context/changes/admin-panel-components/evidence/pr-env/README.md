# Kontrola w PR env (faza 6, kryterium 6.7)

- **Środowisko:** `szczur-radar-pr-203`, `https://szczur-radar-web-szczur-radar-pr-203.up.railway.app`, web wdrożony z commitu fazy 5 (`c428ec8`); faza 6 nie zmienia kodu aplikacji, tylko testy E2E i dokumentację.
- **Data:** 2026-11-24.
- **Konto:** jednorazowe konto admina `agent-admin-panel-components@example.com` założone przez agenta według `lessons.md` („W PR env agent sam zakłada jednorazowe konto admina”): `create-user` przez `railway ssh`, link i hasło (`openssl rand`) tylko w zmiennych powłoki, aktywacja i logowanie przez `curl` z nagłówkiem `Origin` strony, ciasteczko w katalogu w scratchpadzie usuniętym po kontroli. Żaden sekret nie trafił na wyjście ani do pliku repozytorium. Konto zostaje w bazie PR env i znika razem z nim.
- **Dane:** PR env nie miał przebiegu, więc agent uruchomił jeden przebieg jednego dnia (`2026-11-22`, źródło `wykop`) przez `POST /api/admin/pipeline-runs` (202). Pierwsza kontrola biegła w trakcie przebiegu, a liczby nieudanych postów rosły między odczytami. Kontrola niżej jest powtórzona po końcu przebiegu (`/api/deploy-safety` → `safe: true`).

## Wynik (375 px, ta sama sesja dla API i strony)

| Część | API | Strona | Zgodne |
| --- | --- | --- | --- |
| `/admin` | `GET /api/admin/pipeline-runs?page=0&size=20`: `total` 1, 1 element | `h1` „Panel admina”, pasek „Przebiegi” (bieżący), „Jakość ocen”, „Nieudane posty 24”; tabela: 1 wiersz (stan „częściowo”) | tak |
| `/admin/quality` | `GET /api/admin/assessment-quality`: źródła `["wykop"]` | jedna karta, `h3` „Wykop” | tak |
| `/admin/posts` | `GET /api/admin/posts/failed?page=0&size=50`: `total` 24, 24 elementy, pochodzenie `wykop/gielda` | tabela: 24 wiersze, kolumna „Źródło” wypełniona („Wykop · #gielda”), licznik w pasku 24 | tak |
| strona przebiegu (odnośnik z tabeli) | — | `h1` „Przebieg z 24.11.2026, 18:04”, `h2` „Liczniki”, „Wynik per źródło”, „Kroki” | tak |

`document.documentElement.scrollWidth` przy 375 px: `/admin` 375, `/admin/quality` 375, `/admin/posts` 375, strona przebiegu 375.

## Zrzuty

`pr-env-admin-375.png`, `pr-env-quality-375.png`, `pr-env-posts-375.png`, `pr-env-run-375.png` (cała strona, `playwright-cli`). Zrzuty pochodzą z pierwszej kontroli (przebieg jeszcze trwał) albo z drugiej, zależnie od pliku: skrypt nadpisuje je przy każdym biegu, więc obecne są z drugiej kontroli, po końcu przebiegu.

## Sieć i sekrety

Narzędzia: `railway ssh` (konto operatora z CLI), `curl`, `playwright-cli`. `railway ssh` przy uśpionym API odpowiadał ogólnym serwerem Railway zamiast kontenera. Skrypt budzi API zapytaniem `GET /api/ready` przed każdym wywołaniem.
