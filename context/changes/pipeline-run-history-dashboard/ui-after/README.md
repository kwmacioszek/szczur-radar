# Zrzuty „po” — historia przebiegów, strona przebiegu i dzwoneczek

Dowód bramki wizualnej zmiany `pipeline-run-history-dashboard` (plan, faza 6 pkt 1).

- **Mock.** `npx vite --port 5173 --strictPort` i `playwright-cli run-code` (Chromium, `--config` z głównego checkoutu, bo worktree go nie ma). Atrapa `szczur-radar-web/dev/mock-api.js` w trybach `MODE` `data`, `loading` i `error` oraz `NOTIFICATIONS` `unread`, `none` i `error`. Każdy inny tryb to tymczasowa kopia pliku poza repo z podmienioną stałą; kopia zdejmuje trasy poprzedniego trybu (`page.unrouteAll`).
- **Widoki.** `/admin` (historia), `/admin/runs/:id` dla przebiegów atrapy `r-running`, `r-deploy`, `r-stalled` i nieznanego id, dzwoneczek w nagłówku oraz sekcja „Dzwoneczek” na `/kitchen-sink` (zrzut elementu).
- **Rozmiary i motywy.** 1280×900 i 375×812, motyw przez `page.emulateMedia({ colorScheme })`. Historia i strony przebiegu to zrzuty całej strony, reszta to sam viewport.
- **Hover i fokus.** Wymuszone w przeglądarce, nie klasami CSS: hover przez `locator.hover()`, fokus klawiszem Tab od początku strony.
- **Nazwy plików:** `p6-<widok>-<stan>-<szerokość>-<motyw>.png`.

## Macierz 7 stanów

| Stan | 1280 jasny | 1280 ciemny | 375 jasny | 375 ciemny |
| --- | --- | --- | --- | --- |
| default | `p6-admin-history-1280-light.png` (historia, dzwoneczek zamknięty z licznikiem 2), `p6-run-running-1280-light.png` (trwający z postępem), `p6-run-deploy-1280-light.png` (przerwany przez deploy), `p6-run-stalled-1280-light.png` (zatrzymany przez strażnika), `p6-bell-open-1280-light.png` (otwarty z listą), `p6-kitchen-bell-1280-light.png` | `p6-{admin-history,run-running,run-deploy,run-stalled,bell-open,kitchen-bell}-1280-dark.png` | `p6-{admin-history,run-running,run-deploy,run-stalled,bell-open,kitchen-bell}-375-light.png` | `p6-{admin-history,run-running,run-deploy,run-stalled,bell-open,kitchen-bell}-375-dark.png` |
| hover | `p6-hover-bell-1280-light.png` (dzwoneczek), `p6-hover-bell-item-1280-light.png` (pozycja listy), `p6-hover-details-1280-light.png` („Szczegóły”), `p6-hover-older-1280-light.png` („Pokaż starsze”) | `p6-hover-{bell,bell-item,details,older}-1280-dark.png` | N/A: ekran dotykowy nie ma hover | N/A: jak obok |
| focus-visible | `p6-focus-bell-1280-light.png`, `p6-focus-bell-item-1280-light.png`, `p6-focus-details-1280-light.png`, `p6-focus-older-1280-light.png` | `p6-focus-{bell,bell-item,details,older}-1280-dark.png` | `p6-focus-{bell,bell-item,details,older}-375-light.png` | `p6-focus-{bell,bell-item,details,older}-375-dark.png` |
| disabled | `p6-older-busy-1280-light.png` („Pokaż starsze” w trakcie wczytywania: wyłączony, `aria-busy`, „Wczytywanie…”) | `p6-older-busy-1280-dark.png` | `p6-older-busy-375-light.png` | `p6-older-busy-375-dark.png` |
| error | `p6-run-error-1280-light.png` (strona przebiegu, 500), `p6-bell-error-1280-light.png` (lista powiadomień, 500) | `p6-{run-error,bell-error}-1280-dark.png` | `p6-{run-error,bell-error}-375-light.png` | `p6-{run-error,bell-error}-375-dark.png` |
| empty | `p6-bell-empty-1280-light.png` („Brak powiadomień.”, dzwoneczek bez licznika), `p6-run-404-1280-light.png` („Nie ma takiego przebiegu.”) | `p6-{bell-empty,run-404}-1280-dark.png` | `p6-{bell-empty,run-404}-375-light.png` | `p6-{bell-empty,run-404}-375-dark.png` |
| loading | `p6-run-loading-1280-light.png` („Wczytywanie przebiegu…”), `p6-bell-loading-1280-light.png` (lista „Wczytywanie…”, `aria-busy`) | `p6-{run-loading,bell-loading}-1280-dark.png` | `p6-{run-loading,bell-loading}-375-light.png` | `p6-{run-loading,bell-loading}-375-dark.png` |

Uwagi do macierzy:

- **disabled dzwoneczka: N/A.** Przycisk dzwoneczka nigdy nie jest wyłączony, także w trakcie wczytywania i po błędzie. Wyłączony bywa tylko przycisk zajęty pracą (`AppButton` `busy`): „Pokaż starsze” na zrzutach i „Oznacz wszystkie jako przeczytane” w trakcie swojego `POST`, w tym samym wyglądzie. Zrzut „Pokaż starsze” powstał z dodatkową trasą, która wstrzymuje drugą stronę listy.
- **hover przy 375 px: N/A.** Ekran dotykowy nie ma hover.
- **Pozycja listy na hover** dostaje tło z `--accent` (12%). „Szczegóły” na hover dostaje podkreślenie, a dzwoneczek i „Pokaż starsze” mają globalny hover przycisku.
- **Stany błędów bez zrzutu** pokrywają testy Vitest, bo atrapa ich nie odtwarza:
  - nieudane odświeżenie trwającego przebiegu („Nie udało się odświeżyć przebiegu. Kolejna próba za 10 s.”): `RunDetailView.test.ts`;
  - nieudane „Pokaż starsze”: `AdminView.test.ts`;
  - nieudane „Oznacz wszystkie jako przeczytane”: `NotificationBell.test.ts`.
- **Pusta historia** („Jeszcze nie było przebiegu.”, tryb `empty`) się nie zmieniła i nie ma nowego zrzutu.
- **Przebieg w błędzie a błąd wczytania.** `p6-run-stalled-*` pokazuje przebieg `failed` (stan danych z powodem „Wymaga uwagi”), a `p6-run-error-*` błąd wczytania strony z „Spróbuj ponownie”.
- **Fokus na nagłówku strony przebiegu.** Po wczytaniu strona przenosi fokus na `h1` (`tabindex="-1"`), więc zrzuty stron przebiegu mają obwódkę `--ring` wokół tytułu. To ten sam wzorzec co strona posta (`PostDetailView`, S-03): czytnik ekranu zaczyna od tytułu.

## Pomiary w przeglądarce (mock)

| Pomiar | Wynik |
| --- | --- |
| Poziome przewijanie przy 375 px, każdy zrzut | brak: `scrollWidth` = `clientWidth` = 375 |
| Okienko dzwoneczka przy 375 px | x 16, szerokość 343, prawa krawędź 359 (mieści się w 375 z marginesem `--space-4`) |
| Okienko dzwoneczka przy 1280 px | x 832, szerokość 432 (`24rem`), prawa krawędź 1264 |
| Tab od początku `/admin` do dzwoneczka | 3. Tab („Posty”, „Panel admina”, dzwoneczek), `:focus-visible` |
| Enter na dzwoneczku, potem Tab do pierwszej pozycji | 2. Tab (pierwszy to „Oznacz wszystkie jako przeczytane”), `:focus-visible` |
| Escape przy otwartej liście | lista zamknięta, fokus wraca na dzwoneczek |
| Tab od początku `/admin` do pierwszego „Szczegóły” / „Pokaż starsze” | 16. / 36. Tab, `:focus-visible` |
| `aria-label` dzwoneczka | „Powiadomienia, 0 nieprzeczytanych” przy pustej liście, „Powiadomienia” przed pierwszą odpowiedzią i po błędzie (z licznikiem: test `NotificationBell.test.ts`) |
| `aria-busy` okienka w trakcie wczytywania | `true` |

## Liczniki skanu

| Plik | Literały kolorów, klasy palety, wartości arbitralne | `px`/`rem` w piśmie, odstępach i promieniach | Na liście `CLEANED` |
| --- | --- | --- | --- |
| `src/components/NotificationBell.vue` | 0 | 0 | tak (nowy) |
| `src/components/NotificationMenu.vue` | 0 | 0 | tak (nowy) |
| `src/views/RunDetailView.vue` | 0 | 0 | tak (nowy) |
| `src/views/AdminView.vue` | 0 | 0 | tak |
| `src/components/AppHeader.vue` | 0 | 0 | tak |

`npx vitest run src/styleContract.test.ts`: 34 testy przechodzą (1 plik). Cały `npm test`: 24 pliki, 373 testy; `npm run build` przechodzi. Zmiana nie dodaje tokenów ani ról kolorów: licznik dzwoneczka to tekst `--on-accent` na `--accent`, z parą kontrastu już w `styleContract.test.ts`.

## Poprawka po zrzutach

Na stronie przebiegu z błędem (`r-deploy`, `r-stalled`) ostatni krok miał dolną linię, a sekcja „Błąd przebiegu” własną górną, więc między nimi stały dwie linie. Kroki mają teraz linię tylko między sobą (`.step + .step`, jak pozycje listy powiadomień). Zrzuty `p6-run-{running,deploy,stalled}-*` są zrobione po poprawce.

## Lista kontrolna `/10x-ui`

Według `.claude/skills/10x-ui/references/ui-quality-checklist.md`.

### Charges

- [x] Punkt wyjścia: to funkcja (nowa strona, nowy komponent, rozszerzona historia), nie naprawa istniejącego widoku. `research.md` i plan opisują ją jako decyzje, nie jako listę `## Charges`.
- [x] Brakujące tokeny: 0 literałów w nowych i zmienionych plikach (tabela wyżej).
- [x] Brakujący współdzielony komponent:
  - przyciski to `AppButton`, stany to `StatusBadge`, komunikaty to `StatusMessage`;
  - nowy `NotificationMenu` (rysuje dzwoneczek ze wszystkich stanów z propsów) leży w `src/components/` i jest na kitchen sink;
  - `NotificationBell` tylko go zasila z API;
  - nie powstał drugi przycisk ani drugi znacznik.
- [x] Wejścia:
  - bez sesji `/admin/runs/:id` prowadzi na `/login`, a rola `user` trafia na `/` (`src/router/index.test.ts`);
  - prosto z linku: każdy zrzut strony przebiegu to bezpośrednie wejście na `/admin/runs/<id>`;
  - nieznane id pokazuje „Nie ma takiego przebiegu.” z linkiem do panelu;
  - bez danych: pusta lista powiadomień i pusta historia.
- [x] Zarzuty poza planem jako deferred:
  - nagłówek przy 375 px zawija się do trzech wierszy (linki, e-mail z dzwoneczkiem, „Wyloguj”); to D4 z `feed-view-ui-hardening`, nadal poza zakresem;
  - otwarta lista przykrywa część strony pod nagłówkiem (zamierzone: okienko, nie przesunięcie treści).

### Contract

- [x] Tokeny: pismo, odstępy, promienie i kolory z `:root` w `szczur-radar-web/src/style.css`.
- [x] Warstwa komponentów repo (`src/components/`): `NotificationMenu` na kitchen sink w każdym stanie (zamknięty z licznikiem i bez, otwarty z listą, pusty, błąd, ładowanie); znaczniki „przerwany — nowe wdrożenie” i „wymaga uwagi” w sekcji „Znaczniki”.
- [x] Macierz 7 stanów: tabela wyżej, każda komórka ze zrzutem albo N/A z powodem.
- [x] Desktop i jedna szerokość mobilna: 1280×900 i 375×812.
- [x] Fokus i nazwy:
  - pierścień `--ring` na dzwoneczku, pozycjach listy, „Szczegóły” i „Pokaż starsze”;
  - dzwoneczek ma `aria-label` z liczbą nieprzeczytanych, `aria-expanded` i `aria-controls`;
  - okienko to `role="region"` z nazwą z tytułu „Powiadomienia” i `aria-busy` w trakcie wczytywania;
  - „Szczegóły” ma nazwę z datą przebiegu („Szczegóły przebiegu z …”);
  - pasek postępu ma etykietę „Ocena: N z M”.
- [x] Ciemny motyw tylko na warstwie tokenów, oba motywy na zrzutach.

### Guard

- [x] `REPOSITORY_GUIDELINES.md`, sekcja „UI”: `NotificationMenu` (na kitchen sink) i `NotificationBell` (kontener danych, tylko w nagłówku admina) na liście wspólnych komponentów.
- [x] Kontrola: `src/styleContract.test.ts` z `NotificationBell.vue`, `NotificationMenu.vue` i `RunDetailView.vue` na liście `CLEANED` (Vitest, job „web unit tests” w CI).

### Gate

- [x] Kitchen sink (sekcja „Dzwoneczek”) i zrzuty widoków z atrapą w obu motywach i przy 375 px. Baseline `toHaveScreenshot`: N/A, bo w repo nie ma tego narzędzia.
- [ ] `/10x-impl-review`: po fazie 6.
- [x] Zakres: historia w `/admin`, nowa strona przebiegu i dzwoneczek w nagłówku. Poza nimi tylko etykiety i reguły przebiegu (`labels.ts`, `runs.ts`), atrapa, kitchen sink i reguła w repo.

## PR env

`szczur-radar-pr-60`, 2026-10-16, commit `fa87f19` (szczegóły i logi: `context/deployment/deploy-plan.md`, sekcja S-06, „PR-env verification”).

| Krok | Wynik | Zrzut |
| --- | --- | --- |
| 6.7 postęp na żywo | przebieg RANGE z crona (864 posty): strona przebiegu 33/864 → 217/864, „Ostatnia aktywność: przed chwilą”; `/admin`: zakres, „uruchomił cron”, czas, liczniki; w logu API linia `assess: 152/864 (…)` | `pr-env-run-progress-1.png`, `pr-env-run-progress-2.png`, `pr-env-admin-running.png` |
| 6.8 heartbeat crona | linie co minutę (`TRIGGER_HEARTBEAT_INTERVAL=1m`): `assessed 108/864`, `281/864`, `444/864` ze `step assess` i `last activity` | — (log Railway) |
| 6.9 przerwanie redeployem | przebieg `interrupted`, przyczyna `deploy`, krok `assess` przerwany, bez kroku `summarize`; cron: `(cause deploy)`, exit 1; dzwoneczek 1 nieprzeczytane, kliknięcie → strona przebiegu „przerwany — nowe wdrożenie”, licznik 0 | `pr-env-bell-open.png`, `pr-env-run-interrupted-deploy.png` |
| 6.10 konto `user` | bez dzwoneczka i linku „Panel admina”; `/admin/runs/:id` → `/`; `GET /api/admin/notifications` i `GET /api/admin/pipeline-runs/:id` → 403 | `pr-env-user-feed.png` |

Po weryfikacji zmienne testowe crona są usunięte, harmonogram wrócił do `30 0 * * *`, a cron jest zredeployowany.
