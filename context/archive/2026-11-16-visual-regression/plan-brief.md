# F-06: zrzuty porównawcze i test dostępności w CI — Plan Brief

> Full plan: `context/changes/visual-regression/plan.md`

## What & Why

W CI wchodzą zrzuty porównawcze (`toHaveScreenshot`) i automatyczny test dostępności (axe-core). Pilnują one każdego widoku z danymi w obu motywach przy 375 px i na desktopie, każdej sekcji katalogu komponentów w obu motywach przy 375 px oraz małego zestawu stanów hover i focus.

Przebudowa UI w S-46–S-59 zmienia wspólne tokeny, komponenty i router. Bez tej siatki regresję wyglądu łapałyby tylko ręczne zrzuty. Zmiana wyglądu ma aktualizować wzorce w tym samym PR, żeby weryfikacja wyglądu była przeglądem różnic obrazków (NFR `prd.md:328-330`, roadmapa F-06).

## Starting Point

F-05 dał Playwright 1.65.1 na buildzie produkcyjnym z atrapą API ustawianą z testu i zatrzymanym zegarem, a w CI job `web-e2e` w przypiętym obrazie Playwright. Katalog `/kitchen-sink` istnieje tylko na serwerze dev.

Próbny pomiar dał trzy wyniki:
- axe: 0 naruszeń WCAG A/AA na wszystkich 36 kombinacjach;
- zrzuty: identyczne bajt w bajt przy powtórzeniach;
- font: w obrazie `system-ui` trafia na chiński WenQuanYi (brak pogrubienia, obce polskie litery).

Mac jest na arm64, a CI na amd64.

## Desired End State

`npm run visual:docker` i `npm run visual:update` uruchamiają testy wyglądu w tym samym obrazie i na tej samej architekturze co CI. Wyniki:
- 54 wzorce: 24 widoki, 20 sekcji katalogu, 10 stanów;
- axe na każdym widoku i katalogu;
- strażnik fontu i strażnik, że każdy wzorzec ma cel i odwrotnie.

W CI zmiana wyglądu bez nowych wzorców albo naruszenie axe daje czerwony `web-e2e` i wstrzymane wdrożenie. Czerwony krok zostawia artefakt `visual-baselines` z gotowymi wzorcami. Wytyczne mówią następnym wycinkom, jak dodać cel i zaktualizować wzorce.

## Key Decisions Made

| Decyzja | Wybór | Dlaczego (1 zdanie) |
| --- | --- | --- |
| Font w zrzutach | Alias tylko w testach: `visual/fonts.conf` mapuje `system-ui`/`sans-serif` na Liberation Sans, przekazany przez `launchOptions.env` | Pogrubienie i polskie litery z jednego fontu bez przesądzania typografii, którą ma otwartą S-46. |
| Aktualizacja wzorców | Lokalny kontener amd64 (`visual:update`, OrbStack/Rosetta) + artefakt `visual-baselines` z czerwonego CI jako zapas | Szybka pętla bez pushowania; zapas działa, gdyby emulacja rozjechała się z CI. |
| Stany hover/focus | 5 stanów: hover i focus przycisku primary i secondary oraz focus pola wyszukiwania; zrzut regionu z asercją `:hover`/`:focus-visible` | Pokrywa regułę hover i pierścień `--ring` przy 10 małych wzorcach; axe tego nie sprawdza. |
| Podział katalogu | Zrzut na sekcję (nagłówek `h2` → region o jego nazwie, bez paneli dzwonka), nowa sekcja dostaje wzorzec sama | Różnica w PR wskazuje komponent, a historia gita rośnie o mały plik. |
| Szerokości | 375×812 i 1280×720; katalog tylko 375 | Desktop jak obecny projekt Playwright; PRD mówi o katalogu przy 375 px. |
| Widoki | 6 widoków w jednym stanie z danymi: login, aktywacja, feed (user), post (user), `/admin`, przebieg 3; feed przycięty do 6 pierwszych kart | Wersja odchudzona z roadmapy; 6 kart pokrywa wszystkie warianty, a całostronicowy feed to ~1,3 MB na plik. |
| Dostępność | `@axe-core/playwright` 4.14.0, tagi WCAG 2.0/2.1/2.2 A/AA, blokuje; bez best-practice i bez rejestru wyjątków | Dziś 0 naruszeń; wyjątek byłby zmianą kodu testu z powodem w PR. |
| Próg różnicy | Domyślny `threshold` 0.2, zero pikseli różnicy | Pomiar dał zrzuty identyczne bajt w bajt w tym samym środowisku. |
| Miejsce w CI | Nowy krok w jobie `web-e2e` (ten sam kontener i kontrola wersji), osobne katalogi raportu | Bez drugiego joba i drugiej kontroli obrazu; ~+1–1,5 min przy ~5,5 min zapasu. |
| Gdzie działa | Konfiguracja odmawia poza obrazem Playwright na linux/amd64 | Wzorce z innego systemu nie mogą powstać przypadkiem, a `npm run e2e` na Macu się nie zmienia. |

## Scope

**In scope:** `playwright.visual.config.ts` i `visual/` (cele, fonty, specy, wzorce); `scripts/visual-docker.sh` i skrypty npm; krok CI z artefaktem; wpisy w `.gitignore`, `.dockerignore`, Vitest i `tsconfig.e2e.json`; celowe złamania z dowodem; `REPOSITORY_GUIDELINES.md` i `verification-scope.md`; status F-06 w roadmapie i jej SVG.

**Out of scope:** font webowy i typografia (S-46); stany widoków inne niż dane; katalog na desktopie; więcej stanów hover/focus; klasy wymuszające stan; inne przeglądarki; best-practice axe i rejestr wyjątków; bot commitujący wzorce i Git LFS; `test-stack.md`; PR env i pula self-hosted.

## Architecture / Approach

Osobna konfiguracja Playwright z 4 projektami (motyw × szerokość) i dwoma serwerami: `vite preview` dla widoków i `vite` dev dla katalogu. Używa fixture F-05 (atrapa i zegar), przekazuje przeglądarce `FONTCONFIG_FILE` i nie zapisuje wzorców w CI.

Cele to dane w `visual/targets.ts` (widoki, stany) plus regiony katalogu wyliczane z przeglądarki. Z tych samych danych korzysta strażnik wzorców.

Lokalnie skrypt uruchamia przypięty obraz na linux/amd64 z `node_modules` w wolumenie. W CI to krok joba `web-e2e`, a jego czerwony wynik zostawia `visual-baselines`.

## Phases at a Glance

| Faza | Co dostarcza | Główne ryzyko |
| --- | --- | --- |
| 1. Harness zrzutów i widoki | konfiguracja, font i jego strażnik, skrypt dockerowy, 24 wzorce widoków z axe, krok CI z artefaktem | wzorce z emulowanego amd64 na Macu nie przejdą na runnerze CI; zapas to wzorce z artefaktu |
| 2. Katalog komponentów | 20 wzorców sekcji, 10 stanów hover/focus, axe katalogu, strażnik wzorców | `:focus-visible` po fokusie z kodu może się nie zapalić; asercja przed zrzutem to łapie |
| 3. Celowe złamania i dokumentacja | 7 złamań z dowodem, sprawdzony zapas z artefaktu CI, wytyczne, zakres weryfikacji, akceptacja właściciela | odruchowe zatwierdzanie wzorców w kolejnych wycinkach; regułę „opis PR mówi, co i dlaczego” wpisują wytyczne |

**Prerequisites:** F-05 (zrobione); Docker na hoście (OrbStack) z emulacją amd64; `gh` do kryteriów CI; issue #177.
**Estimated effort:** ~2–3 sesje w 3 fazach.

## Open Risks & Assumptions

- Zakładam, że Chromium pod Rosettą renderuje identycznie jak na runnerze amd64. Kryterium 1.10 to sprawdza, a zapas z artefaktu jest gotowy i sprawdzony w fazie 3.
- Zakładam, że Chromium respektuje `FONTCONFIG_FILE` z `launchOptions.env`. Strażnik fontu to sprawdza (CDP `getPlatformFontsForNode`).
- Zrzuty pokazują Liberation Sans, nie krój systemu użytkownika: pilnują układu i kolorów, nie kroju.
- 54 wzorce to dużo do przejrzenia przy S-46, które celowo zmienia wszystkie naraz. Podział na sekcje ogranicza każdą późniejszą zmianę do plików jej komponentu.
- Kontener na puli self-hosted nie był sprawdzony (jak przy F-05).

## Success Criteria (Summary)

- PR, który zmienia wygląd widoku lub komponentu bez nowych wzorców, albo łamie dostępność (kontrast, role, etykiety), ma czerwone CI i nie wdraża się.
- Agent aktualizuje wzorce jednym poleceniem lokalnie, a właściciel ocenia zmianę wyglądu na różnicach obrazków w PR zamiast na ręcznych zrzutach.
- Każda bramka (zrzut, axe, font, strażnik wzorców) czerwienieje na swoim celowym złamaniu.
