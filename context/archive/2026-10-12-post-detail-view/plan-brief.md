# Widok szczegółowy posta (S-03) — Plan Brief

> Full plan: `context/changes/post-detail-view/plan.md`
> Research: `context/changes/post-detail-view/research.md`

## What & Why

Użytkownik otwiera post z listy i widzi go w całości, z czytelną oceną LLM V2 (cele ze stanowiskiem autora, tezy z dosłownym cytatem), surowymi danymi źródła pod przełącznikiem „Dane dla nerdów” i linkiem do oryginału na Wykopie (PRD US-01, FR-009, FR-010; roadmapa S-03; issue #16). Lista pokazuje tylko skrót oceny, a kontrakt V2 zakłada, że stanowisko da się sprawdzić jednym spojrzeniem, bo surowy post stoi obok oceny (§7.1.0).

## Starting Point

- **API:** `GET /api/posts` już zwraca pełne `AssessmentOutputV2`, autora i `webUrl`, ale nie ma trasy dla jednego posta ani kodu 404. RAW leży za `RawPayloadStore` (1–2 kB na post) i nikt poza pipeline'em go nie czyta.
- **SPA po `feed-view-ui-hardening`:** karta posta jest na kontrakcie wyglądu, ale nie ma linku; typy mają `claims: unknown[]`; router nie ma `scrollBehavior`, więc powrót na listę zaczyna od góry.

## Desired End State

Nagłówek karty prowadzi do `/posts/:id`. Widok pokazuje pełny tekst posta, a pod nim ocenę: każdy cel ze stanowiskiem i tezami z cytatami, potem tezy ogólne, modyfikatory i flagi. Jest też link do oryginału (nowa karta) i zwinięty JSON źródła, pobierany dopiero po rozwinięciu.

„← Posty” i Wstecz wracają na tę samą stronę i to samo miejsce listy. Nieznany albo nieoceniony post daje czytelny komunikat z linkiem do listy.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Co widać domyślnie | Pełny tekst posta nad oceną, JSON źródła zwinięty; jedna kolumna 48rem | Godzi US-01 („surowy post obok przetworzonego”) z FR-009 („JSON nie domyślnie”) | Plan |
| Kształt API | `GET /api/posts/{id}` (ten sam `PostItem` co lista) + osobne `GET /api/posts/{id}/raw` na żądanie | Widok nie zależy od magazynu RAW, a RAW czytany jest tylko po rozwinięciu | Plan |
| Format RAW | `application/json` z wcięciami z Jacksona; SPA pokazuje tekst bez `JSON.parse` | Bezstratnie, także dla liczb powyżej 2^53; trasę można otworzyć wprost w przeglądarce | Plan |
| Układ oceny | Tezy pod swoimi celami, z dopiskiem „podstawa stanowiska”; „Tezy ogólne” osobno | Stanowisko stoi obok tez, na których się opiera (§7.1.0) | Plan |
| Które posty | Tylko `assessed`; inny etap i nieznane id dają 404 `post_not_found` | Spójne z listą i z decyzją S-02 (FAILED/DEAD widzi tylko admin) | Plan |
| Powrót z posta | Nagłówek karty to link; powrót przywraca stronę i pozycję listy | 50 postów na stronie: przeglądanie nie gubi miejsca | Plan |
| Kolejność wobec hardeningu | Najpierw `feed-view-ui-hardening`, potem S-03 | Nowy widok dziedziczy stanowisko w kolorze roli, flagi jako `StatusBadge` i skan skali | Research (follow-up) |
| Komponenty | `StanceText` (też w `PostCard`) i `PostAssessment` jako współdzielone, na kitchen sinku | Jedno miejsce dla stanowiska; każdy wariant oceny widoczny przed trasą | Plan |
| Błąd 404 | Nowy `ErrorCode.POST_NOT_FOUND`, wyjątek domenowy + lokalny `@ExceptionHandler` | Wzorzec `PipelineRunController`; `ErrorCode` to zamknięty kontrakt z SPA | Research |
| Poza zakresem | Podświetlanie cytatów, posty failed/dead, proweniencja modelu, etykiety rodziców | Niewymagane przez FR; w MVP nie ma postów nadrzędnych (§6.8) | Plan |

## Scope

**In scope:**
- Trasy `GET /api/posts/{id}` i `/raw`, kod `post_not_found`, testy IT (w tym bezstratność RAW)
- Typy V2 w SPA i polskie etykiety; `StanceText`, `PostAssessment` i ich warianty na kitchen sinku
- `PostDetailView` pod `/posts/:id`: stany, pełny tekst, link do oryginału, leniwy RAW, fokus; token `--mono`
- Link z karty, powrót z przywróceniem pozycji (`scrollBehavior` i sygnał z FeedView); mock trasy szczegółu
- Zrzuty 7 stanów, `REPOSITORY_GUIDELINES.md`, weryfikacja w Railway PR env

**Out of scope:**
- Widok dla `failed`/`dead` i link z `/admin`; RAW w odpowiedzi szczegółu
- Podświetlanie cytatów w treści, formatowanie Wykopu, media, graf argumentów, poprzedni/następny post
- Migracje, zmiana kształtu listy, podświetlenie „Posty” w nagłówku, `/10x-ui` dla nowego widoku

## Architecture / Approach

**API:** `PostController` dostaje dwie trasy. Każda bierze post przez `findById` z warunkiem `stage == ASSESSED`, a w przeciwnym razie rzuca `PostNotFoundException` (404). Szczegół składa `PostItem` z `findLatestOk`. RAW to `RawPayloadStore.load`, zapisany ponownie z wcięciami jako `byte[]` `application/json`.

**SPA:** `PostDetailView` woła `fetchPost`, a po rozwinięciu `<details>` także `fetchPostRaw` (nowe `apiFetchText`). Ocenę renderuje `PostAssessment`. `scrollBehavior` przywraca pozycję tylko przy powrocie `post` → `home`, po sygnale FeedView „strona wczytana”. Stronicowanie i fokus z hardeningu się nie zmieniają.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. API szczegółu i RAW | Dwie trasy, `post_not_found`, testy IT | Kodowanie i wcięcia RAW: `byte[]` zamiast `String`/`JsonNode` |
| 2. Ocena jako komponenty | Typy V2, etykiety, `StanceText`, `PostAssessment`, kitchen sink | Dane wzorcowe niezgodne z kontraktem (`claim_ids` ↔ `target_ids`) |
| 3. Widok i nawigacja | `/posts/:id`, leniwy RAW, link z karty, powrót na to samo miejsce, mock | `scrollBehavior` zmienia przywracanie przewinięcia w całej aplikacji |
| 4. Weryfikacja i PR env | Zrzuty 7 stanów, reguła repo, sprawdzenie na prawdziwych danych | PR env z wdrożeniem web ze starszego commitu (deploy-plan) |

**Prerequisites:** gałąź `feature/post-detail-view` na `origin/main` (już przewinięta do `002da67`); issue #16 i PR na starcie fazy 1; `npm ci` w worktree; `playwright-cli` z configiem głównego checkoutu.

**Estimated effort:** ~3–4 sesje w 4 fazach (backend + frontend, bez migracji).

## Open Risks & Assumptions

- **Kolor linku na karcie.** Nagłówek karty zmienia kolor z `--text-h` na `--accent`, zgodnie z zasadą repo, że akcent oznacza interakcję. Hierarchię karty z hardeningu trzeba ocenić na zrzutach.
- **Czas przywracania.** Przywracanie pozycji czeka na wczytanie listy najwyżej kilka sekund. Przy wolnym API użytkownik zostaje na górze strony.
- **Uszkodzony RAW.** Zła suma SHA-256 daje 500 tylko w przełączniku (komunikat i ponowienie), bez nowego kodu błędu.

## Success Criteria (Summary)

- Z listy jednym kliknięciem użytkownik widzi pełny post i ocenę z tezami pod celami, a jednym spojrzeniem sprawdza stanowisko wobec cytatu.
- „Dane dla nerdów” pokazują bezstratny JSON z Wykopu, a link prowadzi do oryginału.
- Powrót z posta ląduje na tej samej stronie i w tym samym miejscu listy, a nieznany post daje komunikat, nie błąd.
