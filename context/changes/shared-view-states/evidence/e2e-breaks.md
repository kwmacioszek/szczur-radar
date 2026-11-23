# S-48: złamania testów E2E stanów (kryterium 4.4)

Każdy test z `szczur-radar-web/e2e/states.spec.ts` złamany raz w kodzie produkcyjnym (2026-11-23, lokalnie: `npx playwright test e2e/states.spec.ts -g "<nazwa>"` na buildzie produkcyjnym z atrapą). Zmiana tylko w drzewie roboczym, po każdym przebiegu cofnięta (`git checkout -- <plik>`); bez złamań cały plik przechodzi (8 passed, `npm run e2e`: 49 passed).

| Test | Plik | Złamanie | Diff | Wynik |
| --- | --- | --- | --- | --- |
| kolejna strona wczytuje się z listą na ekranie | `src/views/FeedView.vue` | zmiana strony pokazuje szkielet zamiast zostawionej listy (showSkeleton bez warunku kind) | 1+/1- |  1 failed |
| błąd kolejnej strony pozwala ją ponowić | `src/views/FeedView.vue` | po błędzie zmiany strony fokus nie idzie na h1 (usunięte header.value?.focus() w gałęzi catch) | 0+/1- |  1 failed |
| błąd listy ponawia ten sam filtr | `src/views/FeedView.vue` | retry() gubi filtr (pusty filtr w load) | 1+/1- |  1 failed |
| lista mieszcząca się na jednej stronie nie ma stronicowania | `src/components/PagerNav.vue` | PagerNav rysuje się przy jednej stronie | 1+/1- |  1 failed |
| każda sekcja panelu pokazuje szkielet do czasu swoich danych | `src/views/AdminView.vue` | „Odśwież” aktywne przy pierwszym wczytaniu przebiegów | 1+/1- |  1 failed |
| błąd jednej sekcji nie psuje pozostałych, a jej ponowienie wczytuje tylko ją | `src/views/AdminView.vue` | ponowienie sekcji jakości wywołuje refresh() (wszystkie sekcje) | 1+/1- |  1 failed |
| błąd całego panelu daje ponowienie w każdej sekcji | `src/views/AdminView.vue` | sekcja „Dni bez danych” nie pokazuje ErrorState przy błędzie | 1+/1- |  1 failed |
| brak nieudanych postów to pusty stan bez listy | `src/views/AdminView.vue` | pusty stan nieudanych postów bez tytułu „Brak nieudanych postów” | 1+/1- |  1 failed |
