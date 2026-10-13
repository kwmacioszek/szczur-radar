# Widok posta (S-03): zrzuty i macierz stanów

Zrzuty zrobiono 2026-10-13 z mockiem `szczur-radar-web/dev/mock-api.js` i `playwright-cli` (Chromium), według procedury z nagłówka mocka. Kod to gałąź `feature/post-detail-view` w commicie `16fe2de`. Rozmiary okna: 1280×900 i 375×812. Motywy jasny i ciemny ustawia `emulateMedia({ colorScheme })`. Zrzuty stanów default, error, empty i loading widoku posta oraz kitchen sinka obejmują całą stronę. Zrzuty hover i fokus oraz `feed-card-links-*` obejmują tylko okno.

Każdy zrzut sprawdzał też przewijanie w poziomie przy 375 px (`scrollWidth > innerWidth`). Nie było go w żadnym zrzucie, także z rozwiniętym JSON.

## Macierz 7 stanów

| Stan | Widok posta `/posts/:id` | Karta na liście (link w nagłówku) |
| --- | --- | --- |
| default | `post-data-1280-light.png`, `post-data-1280-dark.png`, `post-data-375-light.png`, `post-data-375-dark.png` (JSON rozwinięty); `post-collapsed-1280-light.png` (JSON zwinięty, komentarz z dwoma celami i flagami) | `feed-card-links-1280-light.png`, `feed-card-links-1280-dark.png`, `feed-card-links-375-light.png` |
| hover | `post-back-hover-1280-{light,dark}.png` („← Posty”), `post-original-hover-1280-{light,dark}.png` (link do oryginału), `post-summary-hover-1280-{light,dark}.png` (przełącznik „Dane dla nerdów”); przy 375 px N/A, bo ekran dotykowy nie ma hover | `feed-card-link-hover-1280-{light,dark}.png` (podkreślenie); przy 375 px N/A z tego samego powodu |
| focus-visible | `post-back-focus-1280-{light,dark}.png`, `post-original-focus-1280-{light,dark}.png`, `post-summary-focus-1280-{light,dark}.png` (fokus z klawiatury, Tab) | `feed-card-link-focus-1280-{light,dark}.png` |
| disabled | N/A: widok nie ma kontrolek, które da się wyłączyć. „Spróbuj ponownie” znika w trakcie wczytywania, nie szarzeje | N/A: link nie ma stanu wyłączonego |
| error | `post-error-1280-light.png`, `post-error-375-dark.png` (błąd posta z „Spróbuj ponownie”); `raw-error-1280-light.png`, `raw-error-375-dark.png` (błąd JSON w przełączniku) | bez zmian względem `feed-view-ui-hardening` |
| empty | `post-not-found-1280-light.png`, `post-not-found-375-dark.png` (nieznany post); `post-prefilter-1280-light.png`, `post-prefilter-375-dark.png` (post bez treści, prefiltr). Ocena modelu bez celów i tez: kitchen sink, sekcja „Ocena posta” (`kitchen-sink-1280-light.png`, `kitchen-sink-375-dark.png`) | bez zmian |
| loading | `post-loading-1280-light.png`, `post-loading-375-dark.png` (post); `raw-loading-1280-light.png`, `raw-loading-375-dark.png` (JSON w przełączniku) | bez zmian |

Hover i fokus karty były w `feed-view-ui-hardening` oznaczone „N/A do S-03” (`context/archive/2026-10-12-feed-view-ui-hardening/ui-after/README.md:19`). Teraz mają zrzuty.

## Nawigacja (Playwright z mockiem, 1280 px)

| Krok | Wynik |
| --- | --- |
| `/?page=1`, przewinięcie do `scrollY 1605`, klik w nagłówek karty | `/posts/feed-62`, `scrollY 0`, fokus na `h1` |
| przed rozwinięciem „Dane dla nerdów” | brak `<pre>` (JSON niepobrany) |
| „← Posty” | `href="/?page=1"`, powrót na `/?page=1`, `scrollY 1605` |
| Wstecz przeglądarki z posta | `/?page=1`, `scrollY 1605` |
| `/posts/brak` | „Nie ma takiego posta albo nie został jeszcze oceniony.” i „Przejdź do listy postów” |

## PR env

- **Data:** 2026-10-13.
- **Adres:** `https://szczur-radar-web-szczur-radar-pr-53.up.railway.app`.
- **Commity:** web `16fe2de`, API `25c388b` (API nie zmieniło się po fazie 1).
  - Pierwsze wdrożenie web po pushu `16fe2de` znów wzięło starszy commit (`25c388b`). Pomogło `railway redeploy --from-source -s szczur-radar-web -e szczur-radar-pr-53` (deploy-plan, „PR-env verification: the web deployment's commit”).
- **Dane:** świeża baza PR env. Operator utworzył admina (`create-admin`) i uruchomił przebieg na 2026-10-12: z Wykopu przyszło 212 postów, 206 zostało ocenionych.
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
