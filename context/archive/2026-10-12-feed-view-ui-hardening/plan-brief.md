# Hardening UI feedu — Plan Brief

> Full plan: `context/changes/feed-view-ui-hardening/plan.md`
> Research: `context/changes/feed-view-ui-hardening/research.md`
> Prototyp układu karty: `context/changes/feed-view-ui-hardening/ui-proto/` (wybrany `c-boxed-final-*.png`)

## What & Why

Feed `/` to ekran, na który zalogowany użytkownik trafia najczęściej, a jest zbudowany sprzed kontraktu UI z `admin-panel-ui-hardening`: pismo i odstępy z literałów, ręczne odpowiedniki komponentów, a stronicowanie miesza numer strony z adresu ze starą listą. Zmiana przenosi feed na kontrakt i naprawia stany, zanim S-03 (widok posta) i S-04 (filtry) zaczną na nim budować.

## Starting Point

Kolory feedu są już rolami, ale `FeedView` i `PostCard` mają 23 linie `px` w piśmie i odstępach (karta: 13/14/15/18 px), 0 importów `AppButton`/`StatusMessage`/`StatusBadge`, a test `no literals` przepuszcza to, bo sprawdza tylko kolory. Po „Starsze” widać „Strona 2 z 3” nad postami strony 1, potem widok zostaje na dole nowej strony z fokusem na `body`; `/?page=9` pokazuje pusty pasek „Strona 10 z 3”; błąd nie ma ponowienia.

## Desired End State

Każdy post to karta w obwódce: na desktopie metadane (data i godzina w jednej linii, typ, trafność jako znacznik) w wąskiej kolumnie obok treści, na telefonie w jednym wierszu nad nią. Karta czyta się hierarchią wagi i koloru (nagłówek 16 px 600 `--text-h`, cele w siatce „spółka | stanowisko” 16 px, metadane i znaczniki 14 px), typ posta nie udaje linku, flagi i trafność są `StatusBadge`. Zmiana strony od razu przewija na górę, pokazuje „Wczytywanie strony N…”, a po wczytaniu stawia fokus na „Posty”; stary link poza zakresem prowadzi do ostatniej strony; błąd ma „Spróbuj ponownie”. Test i reguła pilnują skali dla wszystkich oczyszczonych plików.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Układ karty | C2: karty dwukolumnowe (metadane 9rem \| treść od 40rem, jedna kolumna niżej); data i godzina w 1 linii; trafność jako `StatusBadge neutral` | Sama skala była „strasznie prosta”; C2 wybrany z 3 kierunków i 2 prototypów na zrzutach | Plan (prototyp `ui-proto/`) |
| Skala karty | Dwa kroki (`md`/`sm`) + waga 600 i `--text-h` dla nagłówka | Hierarchia wagą i kolorem zamiast czterech rozmiarów; ocena rośnie do 16, flagi do 14 px | Plan (design-taste 9.B) |
| Stanowisko | Tekst w kolorze roli, nazwa celu 600 `--text-h`; flagi jako `StatusBadge neutral` | Treść oceny i zastrzeżenia wyglądają różnie, bez 6 obwódek na kartę | Plan |
| Zmiana strony | Od razu góra strony, „Wczytywanie strony N…” z `aria-busy`, fokus na `h1` po wczytaniu | Nigdy stare posty pod nowym numerem; wzorzec z `AdminView` | Plan |
| Strona poza zakresem | Komunikat + `AppButton` do ostatniej strony | Użytkownik wie, co się stało, adres nie zmienia się po cichu | Plan |
| Kontrola | Jedna lista `CLEANED`, skan także `px`/`rem` w piśmie, odstępach, promieniach; `LoginView` zdjęty do swojej zmiany | Lista znaczy jedno: „oczyszczony z kolorów i skali” | Plan |
| Treść | Typ posta jako zwykła metadana; stan pusty „…po najbliższej aktualizacji danych.” | Akcent tylko dla interakcji; bez żargonu admina dla roli `user` | Plan |
| Nowe tokeny | Brak — faza „wartości tokenów” N/A | Istniejące role, kroki skali, `--border` i `--radius` wystarczają także dla kart C2 | Plan |
| Mock i kitchen sink | `/api/posts` w `dev/mock-api.js`; sekcja „Posty” w kitchen sink | Reguła repo wskazuje jeden mock; `PostCard` jest współdzielony | Research |
| Granice | Link do oryginału/widok posta → S-03; filtry i pasek u góry → S-04 | Jeden widok na zmianę, bez wyprzedzania slice'ów | Research |

## Scope

**In scope:**
- `PostCard.vue` jako karta C2, `FeedView.vue` na skali, rolach i komponentach; nowe teksty stanu pustego, poza zakresem i błędu
- Jedno źródło stanu strony, odporne na spóźnione odpowiedzi; przewinięcie, fokus, `aria-busy`
- `dev/mock-api.js` z `/api/posts`; kitchen sink „Posty”; `styleContract.test.ts`; `REPOSITORY_GUIDELINES.md`
- Testy `PostCard` i `FeedView`; zrzuty `ui-after/`; weryfikacja w Railway PR env

**Out of scope:**
- S-03, S-04, S-05; `AppHeader`; `LoginView` (poza zdjęciem z listy); nowe tokeny, szkielety, animacje, zależności; backend

## Architecture / Approach

`FeedView` trzyma ostatnią pokazaną parę (żądana strona, odpowiedź) i stan wczytywania z numerem żądania; etykieta i przyciski liczą się z pokazanej pary, odpowiedzi nieaktualnych żądań są odrzucane. Zmiana `?page` przewija na górę i zastępuje listę stanem ładowania; po odpowiedzi lista, stan pusty, stan poza zakresem albo błąd z ponowieniem. `PostCard` to karta w siatce dwóch kolumn (metadane | treść) z `StatusBadge` dla trafności i flag, pod klasą inną niż globalne `.card` z `style.css`; `FeedView` korzysta z `AppButton` i `StatusMessage`; wszystkie wartości z `:root` w `style.css`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Środowisko i bramka | Mock `/api/posts` z trybami feedu, `PostCard` w kitchen sink; wygląd bez zmian | Mock rozjedzie się z kontraktem `PostController` |
| 2. Karta i widok na kontrakcie | Karty C2, skala, role, komponenty, nowa hierarchia karty, ostrzejszy skan | Wzorzec skanu za szeroki albo za wąski; kolizja z globalnym `.card` |
| 3. Stany i stronicowanie | Jedno źródło stanu, ładowanie, przewinięcie, fokus, poza zakresem, ponowienie; macierz 7 stanów | Wyścig odpowiedzi i fokus/przewinięcie w jsdom |
| 4. Utrwalenie i weryfikacja | Reguła, checklista `/10x-ui`, PR env na prawdziwych danych | PR env tylko z web może nie mieć API i bazy |

**Prerequisites:** gałąź `feature/feed-view-ui-hardening` na aktualnym `origin/main`; issue `feed-view-ui-hardening: …` i PR na starcie fazy 1; `npm ci` w worktree; `playwright-cli` z configiem głównego checkoutu.
**Estimated effort:** ~2–3 sesje w 4 fazach (frontend + dokumenty, bez backendu).

## Open Risks & Assumptions

- Nagłówek posta maleje z 18 do 16 px; na prototypie C2 waga 600 i `--text-h` utrzymały go jako najsilniejszy element karty — do potwierdzenia na zrzutach fazy 2.
- Karty C2 wydłużają stronę w pionie (obwódka, wcięcia, odstępy); przy 50 postach na stronę pasek stronicowania jest dalej niż dziś (D3 → S-04).
- `LoginView` traci kontrolę kolorów do `login-view-ui-hardening` (świadoma decyzja: jedna lista).
- PR env może wymagać ręcznego `railway redeploy --from-source` API i Postgresa oraz przebiegu w `/admin`, żeby mieć dane.

## Success Criteria (Summary)

- Po „Starsze” użytkownik jest na górze nowej strony, widzi, że się wczytuje, i nigdy nie czyta starych postów pod nowym numerem; klawiatura ląduje na „Posty”.
- Karta posta czyta się od nagłówka do oceny bez pisma mniejszego niż 14 px; stary link i błąd API mają wyjście.
- `npm test` odrzuca literał koloru albo `px` w piśmie/odstępach w każdym oczyszczonym pliku; reguła repo mówi to samo.
