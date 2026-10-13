# Widok posta (S-03): zrzuty i macierz stanów

**Zrzuty z mocka.** Zrobiono je 2026-10-13 z mockiem `szczur-radar-web/dev/mock-api.js` i `playwright-cli` (Chromium), według procedury z nagłówka mocka.

- Kod to commit `e6d7879`, czyli po poprawkach z przeglądu (`reviews/impl-review.md`). Pierwsza wersja zrzutów pochodziła z `16fe2de` i została zastąpiona.
- Rozmiary okna: 1280×900 i 375×812. Motyw jasny lub ciemny ustawia `emulateMedia({ colorScheme })`.
- Stany default, error, empty i loading mają wszystkie cztery kombinacje: `-1280-light`, `-1280-dark`, `-375-light`, `-375-dark`.
- Zasięg:
  - zrzuty stanów widoku posta i kitchen sinka obejmują całą stronę;
  - zrzuty hover i fokus oraz `feed-card-links-*` obejmują tylko okno.

**Kontrola przewijania w poziomie.** Przy 375 px sprawdzono `scrollWidth > innerWidth` na każdym zrzucie oraz osobno na `/posts/feed-3`, `/posts/feed-4`, `/posts/feed-5` (z rozwiniętym JSON), `/posts/brak`, `/` i `/kitchen-sink`. Przewijania w poziomie nie było nigdzie.

**Uwaga o porcie.** Serwer Vite tej zmiany przy drugiej serii zrzutów działał na porcie 5174. Port 5173 zajmował wtedy serwer innego worktree.

## Macierz 7 stanów

| Stan | Widok posta `/posts/:id` | Karta na liście (link w nagłówku) |
| --- | --- | --- |
| default | `post-data-{1280,375}-{light,dark}.png` (JSON rozwinięty), `post-collapsed-{1280,375}-{light,dark}.png` (JSON zwinięty, komentarz z dwoma celami i flagami) | `feed-card-links-{1280,375}-{light,dark}.png` |
| hover | `post-back-hover-1280-{light,dark}.png` („← Posty”), `post-original-hover-1280-{light,dark}.png` (link do oryginału), `post-summary-hover-1280-{light,dark}.png` (przełącznik „Dane dla nerdów”). Przy 375 px N/A: ekran dotykowy nie ma hover | `feed-card-link-hover-1280-{light,dark}.png` (podkreślenie). Przy 375 px N/A z tego samego powodu |
| focus-visible | `post-back-focus-1280-{light,dark}.png`, `post-original-focus-1280-{light,dark}.png`, `post-summary-focus-1280-{light,dark}.png` (fokus z klawiatury, Tab) | `feed-card-link-focus-1280-{light,dark}.png` |
| disabled | N/A: widok nie ma kontrolek, które da się wyłączyć. „Spróbuj ponownie” znika w trakcie wczytywania, nie szarzeje | N/A: link nie ma stanu wyłączonego |
| error | `post-error-{1280,375}-{light,dark}.png` (błąd posta z „Spróbuj ponownie”); `raw-error-{1280,375}-{light,dark}.png` (błąd JSON w przełączniku) | bez zmian względem `feed-view-ui-hardening` |
| empty | `post-not-found-{1280,375}-{light,dark}.png` (nieznany post); `post-prefilter-{1280,375}-{light,dark}.png` (post bez treści, prefiltr). Ocena modelu bez celów i tez: kitchen sink, sekcja „Ocena posta” (`kitchen-sink-{1280,375}-{light,dark}.png`) | bez zmian |
| loading | `post-loading-{1280,375}-{light,dark}.png` (post); `raw-loading-{1280,375}-{light,dark}.png` (JSON w przełączniku) | bez zmian |

Hover i fokus karty były w `feed-view-ui-hardening` oznaczone „N/A do S-03” (`context/archive/2026-10-12-feed-view-ui-hardening/ui-after/README.md:19`). Teraz mają zrzuty.

## Nawigacja (Playwright z mockiem, 1280 px)

| Krok | Wynik |
| --- | --- |
| `/?page=1`, przewinięcie do `scrollY 1605`, klik w nagłówek karty | `/posts/feed-62`, `scrollY 0`, fokus na `h1` |
| przed rozwinięciem „Dane dla nerdów” | brak `<pre>` (JSON niepobrany) |
| „← Posty” | `href="/?page=1"`, powrót na `/?page=1`, `scrollY 1605` |
| Wstecz przeglądarki z posta | `/?page=1`, `scrollY 1605` |
| `/posts/brak` | „Nie ma takiego posta albo nie został jeszcze oceniony.” i „Przejdź do listy postów” |

Ten przebieg zrobiono na `16fe2de`. Poprawka F5 z przeglądu dodała do tego scenariusza fokus na linku posta, z którego wrócono. Pokrywa go `router/scroll.test.ts`.

## PR env

- **Data:** 2026-10-13.
- **Adres:** `https://szczur-radar-web-szczur-radar-pr-53.up.railway.app`.
- **Commity podczas weryfikacji kroków 1.2 i 4.6:** web `16fe2de`, API `25c388b`, bo API nie zmieniło się po fazie 1.
  - Pierwsze wdrożenie web po pushu `16fe2de` znów wzięło starszy commit (`25c388b`).
  - Pomogło `railway redeploy --from-source -s szczur-radar-web -e szczur-radar-pr-53` (deploy-plan, „PR-env verification: the web deployment's commit”).
- **Dane:** świeża baza PR env.
  - Operator utworzył admina (`create-admin`) i uruchomił przebieg na 2026-10-12.
  - Z Wykopu przyszło 212 postów, 206 zostało ocenionych.
- **Wynik (potwierdzony przez operatora):**
  - Krok 1.2:
    - `/api/posts/<id>/raw` to sformatowany JSON z polskimi znakami;
    - losowy UUID daje 404 `post_not_found`.
  - Krok 4.6:
    - wejście w post z listy i powrót na to samo miejsce;
    - JSON źródła po rozwinięciu;
    - link do oryginału na Wykopie;
    - anonimowy głęboki link przez logowanie z powrotem do posta;
    - komunikat dla nieznanego id.
- **Zrzuty z PR env** (prawdziwe dane, web i API na `e6d7879`, sesja zalogowana przez operatora w oknie Playwright):
  - `prenv-feed-1280-light.png`: lista;
  - `prenv-post-1280-light.png` i `prenv-post-375-dark.png`: wpis `87914237` z celem „szczur” („spadkowo (ostrożnie)”, teza „informacja · autor · podstawa stanowiska”) i rozwiniętym JSON z Wykopu.

  Adres e-mail zalogowanego konta w nagłówku zastąpiono przed zrzutem napisem „admin (e-mail ukryty)”. Przewijania w poziomie nie było ani przy 1280, ani przy 375 px.
