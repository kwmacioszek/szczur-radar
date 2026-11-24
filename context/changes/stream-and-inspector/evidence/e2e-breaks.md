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
