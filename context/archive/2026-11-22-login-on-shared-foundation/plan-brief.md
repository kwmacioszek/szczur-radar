# S-49: logowanie i aktywacja na wspólnym fundamencie — Plan Brief

> Full plan: `context/changes/login-on-shared-foundation/plan.md`

## What & Why

Ekrany logowania i aktywacji konta są ostatnimi widokami publicznymi na własnych stylach: `LoginView` ma CSS w `px`, własne pola i przycisk oraz wyjątek w `style.css`, `ActivateView` pokazuje błąd hasła jednym komunikatem pod formularzem. S-49 przenosi oba na tokeny i wspólne komponenty F-08 według zaakceptowanych prototypów `login` i `activate`, w obu motywach, i domyka dług wskazany w teście stylu.

## Starting Point

- `LoginView` nie jest na liście `CLEANED`; `style.css:359-422` wyłącza z globalnego stylu pól formularz `.login-form`.
- `ActivateView` jest na tokenach, ale błędy pól to jeden `StatusMessage`; podpowiedź i błąd pola są skopiowane w trzech plikach (`ActivateView`, `KitchenSinkView`, `FeedFilter`).
- Brak komponentu marki i układu ekranu publicznego; prototypy mają znak radaru, przełącznik motywu w rogu i kartę.
- Atrapa API ma już tryby `LOGIN` i `ACTIVATION`, a E2E logowania i aktywacji istnieją i szukają elementów po etykietach, które się nie zmieniają.

## Desired End State

`/login` i `/activate` wyglądają jak prototypy w jasnym i ciemnym motywie, przy 375 i 1280 px. Błąd logowania i limit prób to komunikat z ikoną, błąd hasła przy aktywacji stoi przy polu z fokusem na nim. `LoginView` jest na `CLEANED`, w `src/` nie ma `login-form`. Zrzuty F-06 pilnują także stanów błędu, a axe przechodzi na wszystkich.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Znak marki | Wspólny `AppBrand` budowany w S-49, S-47 go użyje | Jedno źródło marki dla ekranów publicznych i paska aplikacji, bez duplikatu. | Plan (pytanie 1) |
| Pole z błędem | Komponent `FormField` (etykieta, podpowiedź, błąd, `useId`, `aria-describedby`/`aria-invalid`) | Dostępność z WCAG 3.3.1 jest w jednym miejscu, a panel filtrów (S-54–S-56) użyje go bez kopiowania. | Plan (właściciel: „nie wiem”, decyzja agenta według standardów projektu) |
| Zakres zrzutów | Dwa nowe widoki błędu (`view-login-error`, `view-activate-error`) obok dwóch obecnych | Nowy styl błędu ma strażnika w CI i axe zamiast ręcznego sprawdzania. | Plan (pytanie 3) |
| Układ publiczny | `AuthLayout` jako układ strony, bez sekcji w katalogu | To powłoka strony, jak w S-47; pilnują jej zrzuty widoków. | Plan |
| Wzorce F-06 | Aktualizowane w tej fazie, w której się zmieniają | Każdy commit fazy jest zielony na CI; stan czerwony do końca byłby zbędnym długiem. | Plan |
| Stany „sprawdzanie”, „link nieważny” | Zostają na `StatusMessage` i `AppButton` | Wspólne stany to S-48, który idzie równolegle. | Plan |

## Scope

**In scope:** `AppBrand`, `FormField`, `AuthLayout` z testami i sekcją „Marka” w katalogu; `LoginView` i `ActivateView` na nich; usunięcie wyjątku `.login-form` i martwych stylów `.page`/`.card`/`.eyebrow`/`.lead`; błąd przy polu z fokusem; nowe E2E (limit prób, motyw na logowaniu, krótkie hasło, nieważny link); wzorce F-06; aktualizacja `REPOSITORY_GUIDELINES.md`.

**Out of scope:** backend i kontrakty API, logika sesji i aktywacji, pasek aplikacji (S-47), wspólne stany (S-48), migracja `FeedFilter` na `FormField`, zmiany tekstów i nazw pól, edycje `ui-design.md`.

## Architecture / Approach

Fundament przed widokami: trzy klocki w `src/components/` (`AppBrand`, `FormField`, `AuthLayout`), potem po jednym widoku na fazę. `AuthLayout` składa stronę (marka, `ThemeToggle` w rogu, karta `--surface` z h1 i slotem), `FormField` daje polu `fieldProps` do `v-bind`, widoki zachowują obecną logikę i zmieniają tylko szablon i obsługę błędu pola. Wyjątek w `style.css` znika w tym samym commicie co klasa `login-form` w widoku.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Wspólne klocki | `AppBrand`, `FormField`, `AuthLayout`, sekcje katalogu, `CLEANED` | `kitchen-sink-pola` ma zostać piksel w piksel po przepięciu na `FormField` |
| 2. Logowanie | `LoginView` na wspólnych komponentach, koniec `.login-form`, E2E limitu i motywu | Podwójny styl pól, jeśli wyjątek i klasa zmienią się w różnych commitach |
| 3. Aktywacja | `ActivateView` z błędem przy polu i fokusem, martwe style globalne usunięte | Fokus po `nextTick`; usunięcie `.card`/`.lead` bez ukrytych użyć |
| 4. Stany błędu i bramki | Dwa widoki błędu w zrzutach, `REPOSITORY_GUIDELINES.md`, PR env, pełne bramki | Wzorce pod emulacją amd64 i opis PR z grupami zmian |

**Prerequisites:** F-08 (done), Docker do `npm run visual:docker` (albo artefakt `visual-baselines` z CI), `npm ci` w `szczur-radar-web/`.
**Estimated effort:** ~2 sesje, 4 fazy (jedna większa: faza 1).

## Open Risks & Assumptions

- S-47 wchodzi równolegle i też potrzebuje marki; do scalenia nie ma wspólnych plików, a kolejność scalania opisuje PR.
- Zakładam, że API logowania i aktywacji nie zmienia się (`src/api/*`, kontrakty); PR env sprawdza tylko wygląd stron publicznych, bo logowanie kontem jest pokryte E2E na atrapie i testami backendu.
- Decyzja o `FormField` to wybór agenta (właściciel nie miał zdania); jeśli właściciel woli klasy globalne, zmienia się tylko faza 1 i szablony obu widoków.

## Success Criteria (Summary)

- Użytkownik loguje się i aktywuje konto na ekranach zgodnych z prototypami w obu motywach, z błędem przy polu i widocznym fokusem.
- `npm test`, `npm run build`, `npm run e2e`, `npm run visual:docker` i `design-check app` zielone, CI zielone.
- Właściciel akceptuje zrzuty obok prototypów w opisie PR.
