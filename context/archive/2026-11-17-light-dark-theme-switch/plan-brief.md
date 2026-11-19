# S-46: przełącznik motywu jasnego i ciemnego w nowej palecie — Plan Brief

> Full plan: `context/changes/light-dark-theme-switch/plan.md`

## What & Why

Użytkownik przełącza motyw między jasnym i ciemnym, a wybór jest zapamiętany. Domyślnie obowiązuje motyw systemu (FR-040, must-have). Motywy dostają nową paletę pod układy paneli M-5, a każda para tekstu ma kontrast co najmniej 4.5:1. S-46 idzie przed shellem (S-47) i widokami, bo tokeny są wspólne dla każdego widoku i przemalowanie ich później przerysowałoby wszystko drugi raz.

## Starting Point

Motyw ciemny to dziś tylko `@media (prefers-color-scheme: dark)` w `src/style.css:58-69`, z 8 rolami koloru. Nie ma ręcznego wyboru ani `data-theme`. Test kontrastu czyta media query dosłownie po tekście, a testy wyglądu F-06 przełączają motyw emulacją systemu w Playwright. Nagłówek z przyciskami mają tylko widoki zalogowanego.

## Desired End State

Na każdej stronie motyw jest ustawiony przed pierwszą klatką, bez mignięcia. Przycisk „Ciemny motyw” w nagłówku przełącza go i zapamiętuje wybór w przeglądarce, a logowanie i aktywacja ten wybór stosują. Bez wyboru strona idzie za systemem, także na żywo. Paleta ma tła z makiet (papierowe jasne, grafitowe ciemne), jeden akcent i nowe role pod kolejne wycinki. Testy jednostkowe, E2E i wzorce F-06 tego pilnują.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Kierunek palety | tła z makiet („Czytnik” jasny, „Strumień” ciemny), jeden akcent w obu motywach spoza zieleni i czerwieni | akcent makiet (rdza, limonka) koliduje z kolorami stanowisk spadkowo/wzrostowo, a jeden akcent trzyma markę po przełączeniu | Plan |
| Zakres ról | paleta + `--surface`, `--highlight`, `--stance-neutral`, `--stance-mixed`; bez typografii i odstępów | paleta oceniana raz jako całość, S-47/S-51/S-53 nie przemalowują wzorców; szeryf i odstępy należą do wycinków, które ich używają | Plan |
| Zapamiętanie | `localStorage` w przeglądarce, klucz `szczur-radar-theme` | bez API i migracji, działa przed zalogowaniem i od pierwszej klatki | Plan |
| Przełącznik | jeden przycisk jasny↔ciemny w `AppHeader`, bez opcji „jak system”, bez przycisku na `/login` | dosłownie FR-040, najmniej UI; logowanie przebuduje S-49, a stały shell da S-47 | Plan |
| Akceptacja palety | dwa warianty akcentu (niebieski, morski) na zrzutach, wybór właściciela przed przerysowaniem wzorców | decyzja wizualna zapada na tanich zrzutach, zanim 56 wzorców pójdzie do PR | Plan |
| Źródło motywu | `data-theme` na `<html>` ze skryptu inline w `index.html`, potem `src/theme.ts` | brak mignięcia, wartości ciemne raz w CSS, a emulacja systemu w F-06 działa dalej bez zmian | Plan |
| Kierunek M-5 | jasny i ciemny z ręcznym przełącznikiem, nowa paleta pod układy paneli | decyzja nr 5 właściciela z 2026-11-15 | Research (audyt `post-m4-ui-readiness-audit`) |

## Scope

**In scope:**
- Skrypt motywu w `index.html`, moduł `src/theme.ts`, komponent `ThemeToggle` w nagłówku i w katalogu.
- `style.css` z blokiem `:root[data-theme='dark']`, nowa paleta i 4 nowe role.
- Test kontrastu (19 par × 2 motywy), testy jednostkowe, `e2e/theme.spec.ts`.
- Przerysowane wzorce F-06 i punkt „Tokens” w `REPOSITORY_GUIDELINES.md`.

**Out of scope:**
- Zapis na koncie, opcja „jak system” i przełącznik na `/login`.
- Typografia, odstępy, cienie i siatka.
- Użycie nowych ról w widokach (S-47, S-51, S-53).
- Zmiany w `StatusBadge`, atrapie API i backendzie.
- Kontrola w PR env.

## Architecture / Approach

Klasyczny skrypt w `<head>` rozstrzyga motyw (poprawny wybór z `localStorage`, inaczej `prefers-color-scheme`) i ustawia `data-theme`, zanim strona się pomaluje. `src/theme.ts` przejmuje stan (reaktywny `currentTheme`, `toggleTheme`, zapis, śledzenie systemu bez wyboru). CSS ma dwa bloki ról, a `color-scheme` idzie za motywem. Regułę z dwóch miejsc (skrypt i moduł) spina test, który wykonuje skrypt z `index.html` w jsdom dla tej samej tabeli przypadków.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Mechanizm motywu i przełącznik | `data-theme`, skrypt, `theme.ts`, `ThemeToggle`, testy jednostkowe i E2E; kolory bez zmian, 18 wzorców (nagłówek, sekcja „Motyw”) | mignięcie złego motywu albo rozjazd reguły skryptu i modułu |
| 2. Nowe role i dwa warianty palety | 4 role z parami kontrastu i próbkami; warianty A/B w `palette/` z 8 zrzutami; wybór właściciela | wariant, który przechodzi kontrast, a nie pasuje do makiet (wtedy poprawki przed fazą 3) |
| 3. Wybrana paleta i wzorce | paleta w `style.css`, 56 wzorców przerysowanych, wytyczne UI | duża różnica obrazków w PR do przejrzenia naraz |

**Prerequisites:** F-06 zrobione (zrzuty i axe w CI); Docker do `npm run visual:docker`; `playwright-cli` do zrzutów wariantów.
**Estimated effort:** ~2–3 sesje w 3 fazach. Bieg bez nadzoru zatrzymuje się po fazie 2 na wyborze palety.

## Open Risks & Assumptions

- Zakładam, że żaden użytkownik nie potrzebuje powrotu do „jak system” po kliknięciu. Jeśli tak, opcja trzecia to osobna mała zmiana.
- Kolory `--success`, `--warning` i `--destructive` będą dostrojone do nowych teł, więc znaczniki i stanowiska w widokach też zmienią wygląd. To jest zamierzone i widoczne w różnicach.
- Zrzuty wariantów z `playwright-cli` na macOS różnią się fontem od wzorców (Liberation Sans w obrazie Playwright). Do wyboru palety to bez znaczenia.

## Success Criteria (Summary)

- Użytkownik przełącza motyw w nagłówku, wybór przetrwa przeładowanie i obowiązuje też na logowaniu, a bez wyboru strona idzie za systemem.
- Nowa paleta, wybrana przez właściciela, spełnia kontrast co najmniej 4.5:1 dla 19 par w obu motywach, a axe nie zgłasza naruszeń.
- CI `web-e2e` jest zielony na nowych wzorcach, a właściciel zaakceptował różnice obrazków w PR.
