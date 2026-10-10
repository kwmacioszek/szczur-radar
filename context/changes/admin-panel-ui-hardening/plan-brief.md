# Hardening UI panelu admina — Plan Brief

> Full plan: `context/changes/admin-panel-ui-hardening/plan.md`
> Research: `context/changes/admin-panel-ui-hardening/research.md`

## What & Why

Panel `/admin` działa, ale jest zbudowany z literałów i stylów elementów: w ciemnym motywie statusy „błąd” i „częściowo” mają 3.56–3.85:1, główna akcja wygląda jak każdy inny przycisk, przy wejściu panel twierdzi „Jeszcze nie było przebiegu.”, a nieudany krok jest oznaczony tylko kolorem. Zmiana przenosi panel na kontrakt systemu projektowego (role w `:root`, wspólne komponenty, 7 stanów) i zostawia regułę oraz test, żeby kolejne widoki go dziedziczyły.

## Starting Point

`src/style.css` ma 4 kolory startera Vite (fioletowy akcent, 4.39:1 na białym), przycisk i pole daty istnieją tylko jako globalne selektory elementów, a `src/components/` ma dwa komponenty widoków (`AppHeader`, `PostCard`). `AdminView.vue`: 5 linii literałów kolorów, 18 linii `px`, brak stanu ładowania. Brak lintera i narzędzia zrzutów.

## Desired End State

`/admin` w obu motywach i przy 375 px jest zbudowany wyłącznie z ról i komponentów `AppButton`, `StatusMessage`, `StatusBadge`; kobalt zamiast fioletu, każda para tekst/tło ≥4.5:1. Panel pokazuje ładowanie zamiast fałszywego stanu pustego, odzyskuje się po błędzie źródeł i mówi, dlaczego „Uruchom” jest wyłączony. Feed i Login mają ten sam ton. Następny agent dostaje blok UI w `REPOSITORY_GUIDELINES.md`, kitchen sink w dev i test, który odrzuca literały.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Wariant kontraktu | Zwykłe zmienne CSS w `:root` + SFC w `src/components/`, bez zależności | Rozszerzamy istniejący system zamiast wnosić drugi | Research |
| Bramka wizualna | Kitchen sink tylko w dev (`/kitchen-sink`) + zrzuty `/admin` z `dev/mock-api.js` | Bez refaktoru widoku i bez kodu w obrazie produkcyjnym | Plan |
| Zasięg tokenów | Globalne role + mechaniczna podmiana 5 literałów w Feed/Login/PostCard | Ciemny motyw i przycisk logowania (2.64:1) naprawiają się razem | Plan |
| Akcent | Kobalt `#2a5fc4` / `#86aef0`, `--on-accent` `#fff` / `#16171d`, chłodne szarości | Daleko od zieleni, czerwieni i pomarańczu statusów; fiolet odrzucony | Plan (design-taste-frontend, reguły koloru) |
| Kontrola | Reguła w `REPOSITORY_GUIDELINES.md` + `styleContract.test.ts` (literały i kontrast) | Działa w CI przez `npm test`, bez lintera | Plan |
| Poboczne | `lang="pl"` i polskie etykiety `failureClass` (typ z 7 wartości API) | To, co admin czyta i słyszy w panelu | Plan |
| Spójność z widokami użytkownika | 3 gwarancje: globalny `button` = wariant pomocniczy, wariant główny = przycisk logowania, tony bez nazw z panelu | Późniejsza migracja Feed/Login jest mechaniczna | Plan |
| Granica z S-06 | Układ historii, agregowany sygnał awarii, auto-odświeżanie → S-06 | Nie budować dwa razy tego samego | Research |

## Scope

**In scope:**
- Role kolorów obu motywów, skala pisma/odstępów/promieni, globalne hover i `:focus-visible`
- `AppButton`, `StatusMessage`, `StatusBadge`; `AdminView` i `AppHeader` bez literałów
- Stany: ładowanie per sekcja, ponowienie źródeł, przyczyna wyłączenia, „w toku”, tekstowy status kroku
- Kitchen sink (dev), `dev/mock-api.js`, zrzuty `ui-after/`, test kontrastu i literałów, blok UI w regułach

**Out of scope:**
- Migracja Feed/Login/PostCard na komponenty → `feed-view-ui-hardening` (przed S-03/S-04), `login-view-ui-hardening`
- Układ i auto-odświeżanie listy przebiegów → S-06
- Nowe zależności (Tailwind, shadcn, stylelint, Playwright Test), przełącznik motywu, zmiany API

## Architecture / Approach

Wartości żyją tylko w `:root` (`style.css`), ciemny motyw przełącza je w jednym bloku `prefers-color-scheme`. Komponenty w `src/components/` czytają role, widoki składają komponenty. Kitchen sink pokazuje stany komponentów, a stany widoku dają tryby mocka na prawdziwym `/admin`; `styleContract.test.ts` pilnuje kontrastu tokenów i braku literałów w oczyszczonych plikach.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Środowisko i bramka | `lang="pl"`, `/kitchen-sink` tylko w dev, `dev/mock-api.js` | Kitchen sink przecieka do `dist/` |
| 2. Wartości tokenów | Role, kobalt, skala, hover/fokus; Feed/Login/PostCard na rolach | Zmiana wyglądu Feed/Login poza kolorami |
| 3. Panel na komponentach | 3 komponenty, panel bez literałów, test skanujący, etykiety `failureClass` | Rozjazd selektorów testów `AdminView` |
| 4. Stany widoku | Ładowanie, ponowienie źródeł, przyczyna wyłączenia, macierz 7 stanów | Skok układu przy dochodzeniu źródeł |
| 5. Utrwalenie i weryfikacja | Blok UI w regułach, lista kontrolna, PR env | Różnica między mockiem a prawdziwymi danymi |

**Prerequisites:** świeży `origin/main`; Node 24 z `playwright-cli` i Chromium (`.playwright/cli.config.json`); Railway PR env dla PR.
**Estimated effort:** ~2–3 sesje w 5 fazach, jeden PR.

## Open Risks & Assumptions

- Drzewo robocze ma niezacommitowane zmiany spoza tej zmiany — commity dodają tylko pliki tej zmiany.
- Kontrast liczony dla zwykłego tekstu na `--bg`; tekst na zabarwionym tle `StatusMessage` sprawdzany na zrzutach, nie w teście.
- Do czasu kolejnych zmian `/10x-ui` Feed i Login mają ten sam ton, ale własny kod przycisków i komunikatów.

## Success Criteria (Summary)

- Admin w ciemnym i jasnym motywie odczytuje każdy status i błąd (≥4.5:1), widzi główną akcję i nigdy nie dostaje fałszywego „brak danych”.
- Skan literałów w `AdminView.vue` i `AppHeader.vue`: 0 (było 5 i 1); 7 stanów pokazanych w `ui-after/`.
- Nowy kod UI łamiący kontrakt nie przechodzi `npm test` w CI.
