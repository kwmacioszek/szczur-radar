# S-47: powłoka aplikacji z nawigacją u góry — brief planu

> Full plan: `context/changes/app-shell-navigation/plan.md`

## What & Why

Każdy zalogowany ekran dostaje jeden stały pasek u góry:
- znak i nazwa aplikacji;
- nawigacja „Główna” według roli;
- data ostatniej udanej aktualizacji danych per źródło z dniem i godziną;
- dzwonek admina, „Ciemny motyw”, e-mail i „Wyloguj”.

Treść stoi pod paskiem w jednej kolumnie. Tak chce projekt F-07 („Kanał”, decyzja właściciela z 2026-11-20) i doprecyzowanie FR-030 z 2026-11-21: data danych ma być w pasku na każdym ekranie, a nie nad listą postów. To pierwszy wycinek ekranów M-5. Kolejne (S-48–S-59) budują swoje widoki już w tej powłoce.

## Starting Point

Dziś `App.vue` to sam `<RouterView/>`. Cztery widoki (lista, post, panel admina, przebieg) same stawiają `AppHeader` i własny `<main>`. W `nav` nie ma nazwy, a bieżąca pozycja jest zaznaczona tylko przy dokładnym adresie. Data danych stoi tylko nad listą postów, a przywracanie przewijania czeka na nią. `GET /api/freshness` daje już per źródło pełną chwilę ostatniej udanej aktualizacji, więc API się nie zmienia.

## Desired End State

Na każdej zalogowanej stronie jest jeden `banner` z paskiem z prototypu.
- **Nawigacja.** Zwykły użytkownik ma „Posty”, a admin „Posty” i „Panel admina”. Zaznaczona jest sekcja bieżącej strony, także na stronie posta i przebiegu.
- **Data danych.** Pasek pokazuje „Dane:” i po wierszu na źródło, np. „Wykop 13.10.2026, 05:12”. Data odświeża się przy nawigacji, najwyżej raz na minutę. Poniżej 900 px zostaje widoczna w trzecim wierszu paska.
- **Treść.** Widoki renderują tylko treść w `main` powłoki, z tytułem z nowego `PageHeader`.
- **Logowanie i aktywacja.** Zostają bez paska.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Adres listy `/posts` | lista zostaje pod `/` do S-51 | S-51 i tak przebudowuje ekran postów; S-47 zmienia tylko powłokę, bez ruszania adresów, przewijania i ścieżek testów |
| Odczyt daty danych | przy starcie, po zmianie strony i po powrocie do karty, pominięty, gdy poprzedni jest młodszy niż 60 s | stała powłoka montuje się raz, a data nie może się zestarzeć w długiej sesji |
| Szerokość treści | kolumna do 70rem, obecne widoki zachowują swoją szerokość (48/54rem, admin 56/63rem) | karty i tekst posta nie rozciągają się ponad szerokość czytania, dopóki S-50/S-51 ich nie przebudują |
| Zakres | pasek i nagłówek strony (`PageHeader`) w czterech widokach | projekt przypisuje oba do S-47; jedno miejsce na `h1` i fokus |
| Architektura | trasa-rodzic z `AppShell` (dane) i `AppBar` (prezentacja, w kitchen sink) | rodzic nie montuje się na nowo między dziećmi; podział jak `NotificationBell`/`NotificationMenu`, bo kitchen sink nie ma sesji |
| Bieżąca pozycja | `meta.section` (`posts`/`admin`) na trasach, `aria-current` z sekcji | prototypy zaznaczają sekcję, a nie dokładny adres |
| `notificationsStore` | zostaje, zmieniają się komentarze | nadal czyści listę przy końcu sesji; przebudowa bez potrzeby użytkownika |

## Scope

**In scope:**
- `AppBar`, `AppShell` i `PageHeader`, z kitchen sink, listą `CLEANED` i testami.
- Trasa-rodzic z `meta.section`.
- Data danych w pasku: usunięta z `FeedView`, a z nią oczekiwanie przy przewijaniu.
- Usunięcie `AppHeader`.
- Wzorce F-06 widoków i nowych sekcji katalogu.
- `e2e/shell.spec.ts`.
- Wiersze `FRESHNESS` w strażniku atrapy.
- `REPOSITORY_GUIDELINES.md`.
- Kontrola w PR env i galeria zrzutów.

**Out of scope:**
- Adres `/posts` (S-51).
- Pozycje „Przegląd” i „Cele” (S-59, S-57).
- Przebudowa treści widoków (S-50–S-59) i wspólne stany (S-48).
- Logowanie i aktywacja (S-49).
- Zmiany API i formatu dat.
- Nowy tryb atrapy.

## Architecture / Approach

Router dostaje rekord `/` z komponentem `AppShell` i dziećmi `home`, `post`, `admin` i `admin-run`. Nazwy i adresy tras się nie zmieniają.

`AppShell` czyta sesję, `route.meta.section` i `/api/freshness` i przekazuje je do `AppBar`, a w slocie paska stawia `NotificationBell` dla admina. Pod paskiem renderuje `<main>` z kolumną `--width-content` i `<RouterView/>`.

Widoki tracą pasek i `main`, a tytuł biorą z `PageHeader`, który daje `focus()`. Kiedy przenieść fokus, nadal decydują widoki.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Komponenty paska i nagłówka strony | `AppBar` i `PageHeader` w kitchen sink z wzorcami F-06 i testami | układ wąskiego ekranu (3 wiersze, 320 px bez przewijania w poziomie) |
| 2. Powłoka w routerze i widoki w niej | trasa-rodzic z `AppShell`, data danych w pasku, 4 widoki bez `AppHeader`, nowe wzorce widoków | przywracanie przewijania po wyjęciu daty z listy; powtarzalność zrzutów (gotowość na datę w pasku) |
| 3. Testy E2E powłoki i weryfikacja | `shell.spec.ts`, strażnik `FRESHNESS`, dokumentacja, kontrola w PR env, akceptacja zrzutów | daty z prawdziwego API w strefie warszawskiej w kontroli PR env |

**Prerequisites:** F-08 zarchiwizowany (jest na `main`), gałąź `feature/app-shell-navigation` z `origin/main`, Docker do `npm run visual:docker`, issue #179.
**Estimated effort:** ~2–3 sesje w 3 fazach; najdłużej trwają przebiegi `visual:docker` (2–5 min każdy, kilka razy).

## Open Risks & Assumptions

- **Zrzuty i data danych.** Wzorce F-06 są powtarzalne tylko wtedy, gdy każdy zalogowany widok czeka na datę w `banner`.
- **Odświeżanie daty.** Ponowny odczyt przy nawigacji nie może zdejmować starej wartości na czas żądania, bo na wąskim ekranie zmieniłoby to wysokość paska w chwili przywracania pozycji.
- **Szerokość na przejście.** Na szerokim ekranie treść obecnych widoków jest węższa niż pasek (świadomie, do S-50/S-51). Właściciel ocenia to na zrzutach.

## Success Criteria (Summary)

- Na każdej zalogowanej stronie jest jeden pasek z nawigacją według roli i sekcji, datą danych per źródło z dniem i godziną (także na telefonie), dzwonkiem admina, motywem, e-mailem i „Wyloguj”.
- Widoki działają w powłoce bez własnego paska; dotychczasowe E2E przechodzą bez zmian lokatorów, a nowy `shell.spec.ts` pilnuje przepływów powłoki.
- Agent potwierdza pasek w PR env na prawdziwych datach, a właściciel akceptuje wygląd na zrzutach obok prototypów.
