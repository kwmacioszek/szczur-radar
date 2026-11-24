# Nowe testy E2E czerwone na swoim złamaniu (S-51)

Każde złamanie było edycją tylko w drzewie roboczym, po stagingu fazy. Test uruchomiono pojedynczo (`npx playwright test e2e/<spec> -g "<nazwa>"`, który buduje aplikację od nowa). Po każdym złamaniu plik przywrócono do wersji ze stagingu przez `git checkout -- <plik>`.

## Faza 1

| Test | Złamanie | Wynik |
| --- | --- | --- |
| `feed.spec.ts` „stary adres listy z filtrem prowadzi na /posts z tym samym filtrem” | `router/index.ts`: przekierowanie `''` → `{ name: 'posts', query: {} }` | czerwony: oczekiwano `/posts?q=orlen&direction=bullish`, jest `/posts` |
| `feed.spec.ts` „wiersze stoją pod nagłówkiem swojego dnia” | `postDays.ts`: `if (last) last.items.push(item)` (każdy post trafia do pierwszego dnia) | czerwony: `toHaveCount` regionów dni |
| `feed.spec.ts` „lista przy 320 px nie przewija się w poziomie” | `FeedView.vue`: ramka dnia `.rows` z `min-width: 30rem` | czerwony: `scrollWidth` 494 > 320 |

Dwie pierwsze próby niczego nie zepsuły, więc test został zielony. Za drugim razem złamanie było prawdziwe:

- **Przekierowanie bez `query`** (`redirect: () => ({ name: 'posts' })`): vue-router sam dokleja pierwotne `query` i `hash` do celu, który nie ma własnych. To złamanie nie zmienia więc zachowania.
- **Siatka wiersza `grid-template-columns: 30rem`** w `PostRow.vue`: ramka dnia ma `overflow: hidden` dla zaokrąglonych rogów i obcina wiersz. Nadmiar nie dociera do strony. Przewijanie w poziomie może wywołać tylko element ponad ramką albo sama ramka.

## Faza 2

Wszystkie złamania sprawdzono na całym `e2e/post.spec.ts`. Kolumna „Czerwone testy” wymienia testy, które padły.

| Złamanie | Czerwone testy |
| --- | --- |
| odnośnik wiersza bez zapytania (`FeedView.vue`: `:to` bez `query: route.query`) | „„Zamknij post” zamyka panel…”, „Esc zamyka panel…” (adres bez filtra i strony), „przy 375 px panel zajmuje całe okno…” (fokus) |
| zamknięcie bez powrotu fokusu (`FeedView.vue`: bez `row.focus()` / `header.focus()`) | „Esc zamyka panel…”, „„Zamknij post” zamyka panel…”, „link do posta spoza strony listy…” (`toBeFocused`) |
| brak `inert` (`SidePanel.vue`: bez `setAttribute('inert')`) | „klik wiersza otwiera post w panelu…” (`#app` bez `inert`) |
| Esc nie zamyka (`SidePanel.vue`: bez `emit('close')` na Escape) | „Esc zamyka panel…” (dialog zostaje) |
| `scrollBehavior` przewija przy otwarciu (`scroll.ts`: warunek ekranu postów wyłączony) | „otwarcie i zamknięcie panelu nie przewija listy na górę” (`scrollY` 0 zamiast 1892) |

Ostatniego złamania początkowo nie łapał żaden test: wszystkie 10 testów `post.spec.ts` było zielonych. Dopisałem więc test „otwarcie i zamknięcie panelu nie przewija listy na górę”. Pierwsza próba usunęła import `withinPostsScreen`, przez co `vue-tsc` przerywał build i testy nie ruszały. Druga próba zostawiła import w użyciu (`&& false`).

## Faza 3

Każde złamanie sprawdzono na `e2e/feed.spec.ts` i `e2e/states.spec.ts` razem.

| Złamanie | Czerwone testy |
| --- | --- |
| znacznik zdejmuje wszystkie filtry (`feedFilter.ts`: `withoutFilterKey` zwraca `{}`) | „znacznik zdejmuje tylko swój filtr z adresu i z listy znaczników” (adres `/posts` zamiast `/posts?q=orlen&direction=bullish`) |
| Esc nie oddaje fokusu (`FeedFilter.vue`: Escape tylko zamyka panel, bez `focusToggle`) | „Esc zamyka panel filtrów bez stosowania i oddaje fokus przyciskowi „Filtry”” (`toBeFocused`: inactive) |
| liczba w przycisku liczy puste klucze (`feedFilter.ts`: `activeFilterKeys` zwraca każdy klucz) | „stary adres listy z filtrem prowadzi na /posts z tym samym filtrem” („Filtry: 3” zamiast 2), „filtr po celu i stanowisku trafia do adresu i zawęża listę” |
| „Wyczyść filtry” w znacznikach nic nie robi (`FilterChips.vue`: bez `emit('clear')`) | „„Wyczyść filtry” w znacznikach wraca do pełnej listy” |
