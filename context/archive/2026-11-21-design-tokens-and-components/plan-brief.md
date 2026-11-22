# F-08: tokeny, kroje i komponenty bazowe z projektu F-07 — Plan Brief

> Full plan: `context/changes/design-tokens-and-components/plan.md`

## What & Why

F-08 przenosi do aplikacji język wizualny zaakceptowanego projektu F-07 (`context/foundation/ui-design.md`): tokeny, trzy kroje, ikony i komponenty bazowe. Obecne widoki dostają nowy wygląd w starym układzie. Tokeny są wspólne dla każdego widoku, więc F-08 musi być przed powłoką S-47 i logowaniem S-49, które budują ekrany już na nich.

## Starting Point

- `style.css` ma paletę A z S-46, pismo systemowe i pięć rozmiarów w px.
- `--border` znaczy dziś linię ozdobną, a `--border-strong` krawędź pola.
- Nie ma krojów, ikon ani komponentu ikony.
- Bramki:
  - test kontrastu sprawdza 19 par z jednym progiem;
  - test kroju oczekuje Liberation Sans z aliasu;
  - 56 wzorców F-06 opisuje stary wygląd.
- Caddy dla brakującego pliku oddaje HTML z kodem 200.

## Desired End State

- **Tokeny i kroje.** `style.css` ma tokeny projektu w obu motywach. Kroje Newsreader, Hanken Grotesk i JetBrains Mono są w `public/fonts/` z preloadem dwóch plików łacińskich.
- **Komponenty bazowe** mają wygląd i stany z arkusza komponentów: przycisk, komunikat, znacznik stanu, stanowisko, cel ze stanowiskiem, motyw, pola, odnośnik i ikona. Pokazuje je `/kitchen-sink`.
- **Bramki.** Test kontrastu sprawdza 73 pary projektu z progiem dla każdej pary. Test kroju pilnuje krojów aplikacji. Smoke kontenera łapie brakujący krój.
- **Wzorce F-06** są nowe i zaakceptowane przez właściciela na obrazkach.
- **Strażnik.** `design-check app` wykrywa rozjazd projektu i aplikacji.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Zakres przepięcia | wszystkie widoki na nowych tokenach, układ bez zmian | układ zmieniają wycinki ekranów S-47–S-59 | Roadmapa / projekt |
| Warianty w widokach | tylko to, co zmienia się samo w komponencie; ikony w przyciskach widoków nie | widoki i tak przebudują wycinki, więc mniejszy diff i jedna zmiana wzorców | Plan (właściciel) |
| Strażnik zgodności | ręczne `design-check app`; test w CI jako TODO w `## Parked` przed zamknięciem M-5 | bez nowej zależności modułu web od `context/`; wrócić, gdy będzie widać, ile zmian projektu rozjeżdża kopie | Plan (właściciel) |
| Zmiana nazw `--border` | `--border`→`--line` i `--border-strong`→`--border` w jednym commicie, razem ze skalą pisma | żaden widok nie dostaje przez pomyłkę grubszej linii ani mniejszego nagłówka | Projekt (`ui-design.md:658`) |
| Miejsce krojów | `public/fonts/`, stałe nazwy, `font-display: swap`, preload dwóch plików łacińskich | preload i `@font-face` wskazują ten sam adres bez hasha | Plan |
| Ikony | sprite wpisany raz w `App.vue`, `AppIcon` z zamkniętym zbiorem nazw pilnowanym testem | bez żądania sieciowego, więc zrzuty są stabilne; zbiór nazw jest kontraktem | Plan |
| Pary kontrastu | kopia `pairs.json` jako `src/contrastPairs.json`, próg z każdej pary | obraz Dockera buduje testy tylko z modułu web | Projekt + Plan |
| Stabilność zrzutów | zrzut po `document.fonts.ready`; dwa kolejne przebiegi zielone | przy `swap` zrzut mógłby złapać krój zastępczy | Plan |
| Tony i słowa | `src/runs.ts` i `src/labels.ts` bez zmian, także „wymaga uwagi” | projekt mówi, że słowa i tony się nie zmieniają | Projekt (`ui-design.md:450`) |
| „(ostrożnie)” | dosłownie w DOM, nie nawiasy z CSS jak w arkuszu | testy i projekt używają tego tekstu | Plan |
| `LoginView.vue` | poza `CLEANED` do S-49 | projekt przypisuje go S-49 | Projekt (`ui-design.md:701`) |

## Scope

**In scope:**
- tokeny projektu w `style.css` i tło z poświatą;
- przepięcie nazw w każdym pliku `src/`;
- sześć plików krojów z licencjami, `@font-face` i preload;
- `AppIcon` i `IconSprite`;
- `AppButton`, `StatusMessage`, `StatusBadge`, `StanceText`, `ThemeToggle`, dzwonek z ikoną;
- globalne style pól i odnośnika;
- sekcje katalogu pod nazwami projektu i nowe stany najechania i fokusu;
- przegląd widoków: szeryf, tokeny grubości i interlinii, wskazanie;
- 73 pary, test kroju, czekanie na kroje, smoke `woff2`;
- wszystkie wzorce F-06 od nowa;
- `design-check app`, dokumentacja i zadanie w `## Parked`.

**Out of scope:**
- układ ekranów (S-47–S-59) i komponenty wycinków: nawigacja, stany widoków, tabela, wiersz posta, panel, filtry, przełącznik, pasek proporcji, klocki;
- nowe warianty w widokach;
- nowe nazwy sekcji katalogu dla komponentów, których F-08 nie przebudowuje;
- sprzątanie px w `LoginView`;
- test zgodności w CI;
- nagłówki cache i CSP w Caddy;
- API.

## Architecture / Approach

Projekt (`ui-design/`) jest wzorem, a aplikacja ma jego kopie: `style.css` dla tokenów, `contrastPairs.json` dla par, `src/assets/icons/sprite.svg` dla ikon i `public/fonts/` dla krojów. Test kontrastu, test kroju i wzorce F-06 pilnują aplikacji w CI. `design-check app` pilnuje zgodności kopii z projektem przy zmianie projektu.

Kolejność:
1. tokeny i kroje;
2. komponenty;
3. widoki i wzorce;
4. strażnik i dokumentacja.

Wzorce zmieniają się raz, w fazie 3.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Tokeny i kroje | tokeny projektu, przepięcie nazw w jednym commicie, kroje z preloadem, 73 pary, test kroju, czekanie na kroje, smoke `woff2` | przesunięta skala pisma i `--border` pod tymi samymi nazwami; nazwa rodziny w CDP |
| 2. Ikony i komponenty bazowe | `AppIcon` i sprite, siedem komponentów w nowym wyglądzie, pola i odnośnik, sekcje katalogu, nowe stany | testy jednostkowe zależne od `text()` i `tagName` |
| 3. Widoki i wzorce F-06 | widoki według reguł projektu bez zmiany układu, wszystkie wzorce od nowa, galeria do akceptacji, CI zielone | niepowtarzalne zrzuty z krojami; czerwony krok wizualny w CI do tej fazy |
| 4. Strażnik i dokumentacja | `design-check app` z selftestem, README, `REPOSITORY_GUIDELINES.md`, `ui-design.md`, TODO w `## Parked` | — |

**Prerequisites:** F-07 i S-46 zarchiwizowane (są). Docker z emulacją amd64 dla `npm run visual:docker`, Chromium Playwright dla `design-check`. Gałąź `feature/design-tokens-and-components`, PR i issue F-08 powstają na początku fazy 1.

**Estimated effort:** około 3–4 sesji. Faza 3 z dwoma przebiegami `visual:update` i dwoma `visual:docker` w emulacji (2–5 min każdy).

## Open Risks & Assumptions

- Od fazy 1 do fazy 3 krok wizualny w CI jest czerwony na starych wzorcach. Środowisko PR wdraża się dopiero po fazie 3, bo `railway-config` czeka na `web-e2e`.
- Zakładamy, że zrzuty z emulacji na Macu przejdą na runnerach amd64, jak w F-06. Zapas: artefakt `visual-baselines` z czerwonego przebiegu CI.
- Nazwa rodziny, którą podaje CDP, może się różnić od nazwy w `@font-face`. Faza 1 odczytuje ją przed zapisaniem asercji.
- Widoki w nowym wyglądzie i starym układzie nie mają prototypu 1:1. Właściciel ocenia je obok najbliższych ekranów projektu.

## Success Criteria (Summary)

- Aplikacja wygląda jak projekt „Salon w kolorach Radaru” w obu motywach (kolory, kroje, komponenty), a układ się nie zmienia.
- CI jest zielone z nowymi bramkami: 73 pary, kroje aplikacji, krój w obrazie i nowe wzorce. Właściciel akceptuje wygląd na obrazkach.
- S-47 i S-49 mogą budować ekrany na tokenach i komponentach F-08, a `design-check app` wykrywa rozjazd projektu i aplikacji.
