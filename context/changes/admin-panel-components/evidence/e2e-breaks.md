# Testy E2E nowych przepływów: pokrycie i celowe złamania (faza 6, §1)

Kryterium 6.2: każdy nowy test E2E był raz czerwony na celowym złamaniu w kodzie aplikacji (`szczur-radar-web/src/`, nigdy w teście), a złamanie zostało cofnięte (`git checkout -- <plik>`, potem `git diff --quiet <plik>`).

Przebieg: `E2E_PORT=4391 npx playwright test e2e/admin.spec.ts -g "<nazwa>"`. Lokalnie `webServer` przy każdym uruchomieniu buduje aplikację od nowa (`npm run build && npm run preview`), bo na porcie 4391 nie działał żaden serwer. Każdy czerwony wynik pochodzi więc z buildu ze złamaniem. Data: 2026-11-24.

## Pokrycie punktów planu

| Punkt planu (faza 6, §1) | Test | Plik | Nowy? |
| --- | --- | --- | --- |
| klik części w pasku zmienia adres i `aria-current`, fokus zostaje na klikniętym odnośniku | „panel admina wczytuje każdą część z paska części” | `e2e/admin.spec.ts` | nie (faza 3), nie łamany |
| „Wpisz do formularza: …” wpisuje zakres do „Od” i „Do” i stawia fokus na „Od” | „dzień bez danych wpisuje swój zakres do formularza i stawia fokus na „Od”” | `e2e/admin.spec.ts` | tak |
| „Pokaż starsze” wydłuża tabelę | „„Pokaż starsze” dokłada starsze przebiegi do tabeli” | `e2e/admin.spec.ts` | tak |
| każda miara jakości ma wartość, próg i stan słowami | „każda miara jakości ma wartość, próg i stan słowami (data)” i „(few)” | `e2e/admin.spec.ts` | tak |
| zaznaczenie postów zwiększa osobno „Ponów zaznaczone (N)” i „Przywróć martwe (N)” | „zaznaczenie postów liczy osobno posty do ponowienia i martwe” | `e2e/admin.spec.ts` | tak |
| „Ponów zaznaczone” daje komunikat z odnośnikiem do strony nowego przebiegu | „ponowienie zaznaczonych daje odnośnik do strony nowego przebiegu” | `e2e/admin.spec.ts` | tak |
| „Przywróć martwe” usuwa posty z tabeli i zmniejsza licznik w pasku | „przywrócenie martwego posta usuwa go z tabeli i zmniejsza licznik w pasku” | `e2e/admin.spec.ts` | tak |
| tytuł nieudanego posta prowadzi do oryginału w nowej karcie (`href`, `target`) | „tytuł nieudanego posta prowadzi do oryginału w nowej karcie” | `e2e/admin.spec.ts` | tak |
| `MODE: 'empty'` na `/admin/posts`: pusty stan bez tabeli, odnośnik „Nieudane posty” bez licznika, „Pokaż przebiegi” → `/admin` | „pusty stan nieudanych postów nie ma tabeli ani licznika, a prowadzi do przebiegów” | `e2e/admin.spec.ts` | tak (obok starszego „brak nieudanych postów to pusty stan bez listy” w `e2e/states.spec.ts`, który sprawdza tylko tekst i brak pól wyboru) |
| `FAILED: 'many'`: „Następne” daje stronę 2 i `page=1` w adresie, odświeżenie zostaje na niej | „druga strona nieudanych postów jest w adresie i zostaje po odświeżeniu” | `e2e/admin.spec.ts` | tak |
| „Panel admina” na stronie przebiegu wraca do części „Przebiegi” | „„Panel admina” na stronie przebiegu wraca do części „Przebiegi”” | `e2e/admin.spec.ts` | tak |
| wejście na `/admin/posts` bez roli admina odbija na `/` | „część panelu bez roli admina odbija na listę postów” | `e2e/admin.spec.ts` | tak (starszy „zwykły użytkownik nie widzi panelu admina” w `e2e/auth.spec.ts` sprawdza tylko `/admin`) |

Fokus przy wejściu do panelu (Critical Implementation Details) pokrywa istniejący test „wejście do panelu stawia fokus na jego nagłówku” z fazy 3. Nie ma go na liście planu, więc nie był łamany.

## Złamania

Każde złamanie zostało cofnięte przez `git checkout -- <plik>`. Po każdym `git diff --quiet <plik>` dało czysty wynik („RESTORED clean”).

| # | Test | Zmiana w kodzie aplikacji | Czerwony wynik | Cofnięcie |
| --- | --- | --- | --- | --- |
| A | dzień bez danych wpisuje swój zakres do formularza i stawia fokus na „Od” | `src/views/admin/AdminRunsView.vue`, `fillForm`: usunięty wiersz `from.value = range.from` | `toHaveValue` dla „Od”: oczekiwano `2026-10-11`, jest `2026-10-19` | `git checkout`, czysto |
| B | „Pokaż starsze” dokłada starsze przebiegi do tabeli | `src/views/admin/AdminRunsView.vue`, `loadOlder`: `runs.value = [...runs.value, ...added]` → `runs.value = [...runs.value]` | `toHaveCount`: oczekiwano 32 odnośników „Szczegóły przebiegu z …”, jest 20 | `git checkout`, czysto |
| C | każda miara jakości ma wartość, próg i stan słowami (data), (few) | `src/views/admin/AdminQualityView.vue`: usunięty `StatusBadge` stanu miary (`label('qualityStatus', shown.status)`) | oba przypadki czerwone na `toHaveText` stanów karty: brakuje 2 stanów miar (`- Expected - 2 / + Received + 0`) | `git checkout`, czysto |
| D | zaznaczenie postów liczy osobno posty do ponowienia i martwe | `src/views/admin/AdminFailedPostsView.vue`, `selectedRetryable`: bez warunku `stageOf.value.get(id) !== 'dead'`, więc martwy post liczy się też do ponowienia | po zaznaczeniu martwego posta (`counts(1, 1)`, wiersz 240) brak przycisku „Ponów zaznaczone (1)”, bo przycisk ma (2) | `git checkout`, czysto |
| E | ponowienie zaznaczonych daje odnośnik do strony nowego przebiegu | `src/views/admin/AdminFailedPostsView.vue`: odnośnik komunikatu `:to="{ name: 'admin-run', params: { id: retryRuns.run.id } }"` → `:to="{ name: 'admin' }"` | `toHaveURL`: oczekiwano `/admin/runs/00000000-0000-4000-8000-000100001000`, jest `/admin` | `git checkout`, czysto |
| F | przywrócenie martwego posta usuwa go z tabeli i zmniejsza licznik w pasku | `src/views/admin/AdminFailedPostsView.vue`, `restore`: usunięte `panel.refreshFailedCount()` | odnośnik paska „Nieudane posty 3” nie znaleziony (wiersz 274), bo pasek zostaje przy 4 | `git checkout`, czysto |
| G | tytuł nieudanego posta prowadzi do oryginału w nowej karcie | `src/views/admin/AdminFailedPostsView.vue`: usunięte `target="_blank"` z odnośnika tytułu | `toHaveAttribute('target')`: oczekiwano `_blank`, jest `""` | `git checkout`, czysto |
| H | pusty stan nieudanych postów nie ma tabeli ani licznika, a prowadzi do przebiegów | `src/components/AdminPartsNav.vue`: `v-if="item.count != null && item.count > 0"` → `v-if="item.count != null"`, więc licznik 0 też ma znacznik | `toHaveAccessibleName`: oczekiwano „Nieudane posty”, jest „Nieudane posty 0” | `git checkout`, czysto |
| I | druga strona nieudanych postów jest w adresie i zostaje po odświeżeniu | `src/views/admin/AdminFailedPostsView.vue`, `onMounted`: `await loadFailed()` → `await loadFailed({}, 0)`, więc wejście zawsze czyta pierwszą stronę | po `page.reload()` (wiersz 334) brak „Strona 2 z 2”, choć adres ma `?page=1` | `git checkout`, czysto |
| J | „Panel admina” na stronie przebiegu wraca do części „Przebiegi” | `src/views/RunDetailView.vue`: odnośnik powrotu `:to="{ name: 'admin' }"` → `:to="{ name: 'admin-quality' }"` | `toHaveURL`: oczekiwano `/admin`, jest `/admin/quality` | `git checkout`, czysto |
| K | część panelu bez roli admina odbija na listę postów | `src/router/index.ts`: `admin: true` przeniesione z `meta` rodzica `/admin` na samo dziecko `admin` (Przebiegi), więc `/admin/posts` nie ma straży roli | `toHaveURL`: oczekiwano `/`, jest `/admin/posts` | `git checkout`, czysto |

Po złamaniach `git status` nie pokazuje zmian w `szczur-radar-web/src/`. Cały zestaw jest zielony: `npm run e2e` i `npm test` (wyniki w raporcie fazy).
