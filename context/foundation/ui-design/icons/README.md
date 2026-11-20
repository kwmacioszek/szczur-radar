# Ikony

Ikony projektu UI/UX Szczur Radar (F-07). Pliki Lucide są skopiowane bez zmian z paczki `lucide-static` 1.77.0 (licencja ISC, tekst w `LICENSE-lucide.txt`). Ikony własne (znak marki i znaki stanowiska) narysowano dla tego projektu. `sprite.svg` zbiera wszystkie ikony jako `<symbol>`; grubość kreski ustawia CSS, symbole nie mają `stroke-width`.

| Plik | Id w sprite | Źródło | Licencja | Użycie |
| --- | --- | --- | --- | --- |
| `bell.svg` | `i-bell` | Lucide 1.77.0 | ISC | dzwonek powiadomień |
| `moon.svg` | `i-moon` | Lucide 1.77.0 | ISC | przycisk „Ciemny motyw” |
| `search.svg` | `i-search` | Lucide 1.77.0 | ISC | pole wyszukiwania; pusta lista po filtrach |
| `list-filter.svg` | `i-list-filter` | Lucide 1.77.0 | ISC | przycisk „Filtry” |
| `chevron-left.svg` | `i-chevron-left` | Lucide 1.77.0 | ISC | poprzedni post, poprzedni dzień, „Nowsze” |
| `chevron-right.svg` | `i-chevron-right` | Lucide 1.77.0 | ISC | następny post, następny dzień, „Starsze” |
| `chevron-down.svg` | `i-chevron-down` | Lucide 1.77.0 | ISC | rozwijane części (kroki przebiegu) |
| `x.svg` | `i-x` | Lucide 1.77.0 | ISC | zamknięcie panelu bocznego i panelu filtrów |
| `external-link.svg` | `i-external-link` | Lucide 1.77.0 | ISC | odnośnik do oryginału poza aplikacją |
| `braces.svg` | `i-braces` | Lucide 1.77.0 | ISC | przycisk „Surowe dane” |
| `circle-alert.svg` | `i-circle-alert` | Lucide 1.77.0 | ISC | błąd: komunikat, pole, stan widoku, znacznik |
| `circle-check.svg` | `i-circle-check` | Lucide 1.77.0 | ISC | komunikat powodzenia |
| `triangle-alert.svg` | `i-triangle-alert` | Lucide 1.77.0 | ISC | komunikat uwagi, znacznik stanu wymagającego uwagi |
| `info.svg` | `i-info` | Lucide 1.77.0 | ISC | informacja: post czeka na ocenę |
| `refresh-cw.svg` | `i-refresh-cw` | Lucide 1.77.0 | ISC | „Spróbuj ponownie” |
| `play.svg` | `i-play` | Lucide 1.77.0 | ISC | „Uruchom” przebieg |
| `rotate-ccw.svg` | `i-rotate-ccw` | Lucide 1.77.0 | ISC | „Przywróć zaznaczone” nieudane posty |
| `inbox.svg` | `i-inbox` | Lucide 1.77.0 | ISC | pusta lista: dzień bez danych |
| `arrow-down-up.svg` | `i-arrow-down-up` | Lucide 1.77.0 | ISC | kolejność listy |
| `calendar.svg` | `i-calendar` | Lucide 1.77.0 | ISC | dzień w nagłówku listy bez pola daty |
| `brand-mark.svg` | `i-brand` | własna | własna, na licencji repozytorium | znak marki w pasku aplikacji |
| `stance-up.svg` | `s-up` | własna | własna, na licencji repozytorium | stanowisko autora: wzrost |
| `stance-down.svg` | `s-down` | własna | własna, na licencji repozytorium | stanowisko autora: spadek |
| `stance-flat.svg` | `s-flat` | własna | własna, na licencji repozytorium | stanowisko autora: bez zmiany |
| `stance-mixed.svg` | `s-mixed` | własna | własna, na licencji repozytorium | stanowisko autora: mieszane |
| `stance-none.svg` | `s-none` | własna | własna, na licencji repozytorium | brak stanowiska |
| `sprite.svg` | — | złożony z powyższych | ISC i własna | jeden plik z `<symbol>` dla wszystkich ikon |

Strony prototypu mają własną kopię `sprite.svg` między znacznikami `<!--sprite-->` i `<!--/sprite-->` (strona otwarta z pliku nie wczyta zewnętrznego `<use>`); po zmianie ikon odświeża ją `node tools/design-check/inline-sprite.mjs context/foundation/ui-design/prototypes`.
