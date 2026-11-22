# Weryfikacja w środowisku PR (szczur-radar-pr-199, 2026-11-22, kryterium 3.7)

Środowisko `szczur-radar-pr-199`, web wdrożony z `e0737de` (następny commit `4647f9b` zmienia tylko dokumentację, więc Railway pominął wdrożenie). CI 44994050961 na `e0737de` jest zielone, łącznie z `railway config`. Sekrety (link aktywacyjny, hasło, ciasteczko sesji) zostały w zmiennych powłoki i w pliku w katalogu tymczasowym zadania, usuniętym po kontroli. Żaden z nich nie trafił do tego folderu.

## Kroki

1. Jednorazowe konto `agent-design-tokens-and-components@example.com` (rola `user`), zgodnie z lekcją „W PR env agent sam zakłada jednorazowe konto admina…”. `create-user` przez `railway ssh` odmówił, bo konto zostało po wcześniejszej weryfikacji. Link dał `reset-password`, przechwycony do zmiennej. Hasło z `openssl rand`. `POST /api/auth/activation` → 204, `POST /api/auth/login` z nagłówkiem `Origin` → 200 i ciasteczko `sr_session`.
2. Chromium (Playwright z `szczur-radar-web`), po `document.fonts.ready`, z rodziną odczytaną przez CDP `CSS.getPlatformFontsForNode`, jak w `visual/font.spec.ts`:

| Przejście | Strona | Nagłówek 1 | Etykieta | Pliki `woff2` |
| --- | --- | --- | --- | --- |
| anonimowe | `/login` | „Logowanie”: `Newsreader 16pt 16pt` | „Hasło”: `Hanken Grotesk` | `200 font/woff2`: `newsreader-latin-wght-normal`, `hanken-grotesk-latin-wght-normal`, `hanken-grotesk-latin-ext-wght-normal` |
| z sesją | `/` | „Posty”: `Newsreader 16pt 16pt` | „Spółka lub temat”: `Hanken Grotesk` | te same trzy pliki, `200 font/woff2` |

Obie etykiety mają znak z podzbioru latin-ext („ł”, „ó”), więc plik `hanken-grotesk-latin-ext-wght-normal.woff2` jest pobierany i rysuje glify. Żaden węzeł nie ma glifów z kroju zastępczego.
