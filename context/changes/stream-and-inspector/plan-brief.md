# Lista postów z oceną w panelu bocznym (S-51) — Plan Brief

> Full plan: `context/changes/stream-and-inspector/plan.md`

## What & Why

S-51 to gwiazda przewodnia M-5. Użytkownik przegląda zwartą listę postów (jeden post w wierszu), filtruje ją po celu i stanowisku w panelu „Filtry” ze znacznikami. Ocenę wybranego posta czyta w panelu wysuwanym z prawej, z listą widoczną w tle. Ekran zastępuje dzisiejszy feed z kartami i osobną stronę posta. To pierwszy przepływ, który sprawdza cały nowy fundament (tokeny, powłoka, wspólne stany, testy wizualne) na codziennej pracy użytkownika.

## Starting Point

- Lista działa pod `/` (`FeedView`), z kartami `PostCard`.
- Post jest osobną stroną `/posts/:id` (`PostDetailView`).
- `router/scroll.ts` przywraca pozycję listy po powrocie, bo lista jest odmontowywana.
- API daje już wszystko, co potrzebne: element listy ma pełną ocenę V2.
- Tokeny panelu (`--width-panel`, `--scrim`, `--z-scrim`, `--selected`) są w `style.css`, ale nieużywane.

## Desired End State

- `/posts` to lista z grupami dni („Dziś, 20 października”), a `/` przekierowuje na nią z parametrami.
- Klik wiersza otwiera `/posts/:id` jako modalny dialog nad przyciemnioną listą. W dialogu: metadane, „Zamknij post”, tytuł, „Otwórz oryginał”, treść, „Ocena” z celami i cytatami, „Surowe dane”.
- Esc, „Zamknij post” i „wstecz” wracają do listy z tym samym filtrem i stroną, a fokus wraca na wiersz.
- Poniżej 900 px panel zajmuje cały ekran.
- Filtr stoi pod przyciskiem „Filtry: N”, a nad listą są znaczniki „Usuń filtr: …”.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Adres listy | `/posts` teraz, `/` przekierowuje z zapytaniem | Adresy od razu docelowe; S-59 zmieni tylko przekierowanie `/` na przegląd dnia, bez drugiej przeróbki testów. |
| Własny adres posta (niewiadoma roadmapy) | tak, `/posts/:id?<zapytanie listy>` jako trasa dziecka listy | Działa link z innego ekranu, odświeżenie nie gubi wyboru, lista zostaje zamontowana i nie przeładowuje się. |
| Historia przeglądarki | otwarcie dodaje wpis; zamknięcie `back()`, a po wejściu z linku `replace` na listę | Jedno zachowanie na każdej szerokości, zgodne z projektem („wstecz” zamyka panel). |
| Dane panelu | post z bieżącej strony bez żądania, spoza niej `GET /api/posts/:id` ze stanami | Panel otwiera się natychmiast; link bezpośredni i logowanie z przekierowaniem nadal działają. |
| Panel jako komponent | ogólny `SidePanel` (dialog, `inert`, fokus, Esc, warstwy) + `PostInspector` (treść) + komponent trasy `PostPanel` (dane), rysowany przez `RouterView` w `FeedView` | S-58 użyje tego samego panelu dla karty celu; mechanika dialogu w jednym miejscu; trasa z komponentem nie ostrzega w vue-router, a `FeedView` nie rośnie. |
| Pole „Szukaj w postach” | w S-56, nie tutaj | Wymaga wyszukiwania w treści (FR-036, nowe API); dzisiejsze `q` to tekst celu i zostaje polem „Spółka lub temat”. |
| Stanowisko w wierszu | per cel (dwa pierwsze + „i N inne”) | Kontrakt nie ma sentymentu posta (`prd.md:133`, ryzyko w roadmapie). |
| Stany „czeka na ocenę” / „ocena nieudana” | pominięte | Lista ma tylko posty ocenione (`ASSESSED`), więc te stany nie występują. |

## Scope

**In scope:**
- trasy `/posts` i `/posts/:id`, przekierowanie `/`;
- `PostRow` i grupy dni;
- `SidePanel` i `PostInspector` (ocena i surowe dane);
- panel „Filtry” i `FilterChips`;
- usunięcie `PostDetailView`, `PostCard` i logiki przywracania przewijania;
- E2E, wzorce F-06 i katalog;
- dokumentacja;
- kontrola w PR env i galeria zrzutów.

**Out of scope:**
- przełącznik „Inwestycyjne / Wszystkie” (S-52);
- podświetlenia, „Poprzedni / Następny post” i „4 z 128” (S-53);
- kolejność (S-54), daty (S-55), wyszukiwanie w treści (S-56);
- zmiany API i kontraktu;
- karta celu (S-58);
- skróty klawiatury.

## Architecture / Approach

`FeedView` zostaje widokiem listy pod trasą `posts`. Post to trasa dziecka `post`: Vue Router nie odmontowuje rodzica, więc lista zostaje na ekranie, a panel rysuje się nad nią przez `Teleport` do `body` z `#app` jako `inert`.

Odnośnik wiersza niesie zapytanie listy, a `scrollBehavior` nie przewija w obrębie ekranu postów. Treść panelu to logika przeniesiona z `PostDetailView` i `PostAssessment` w układzie „Cel w ocenie”. Filtr to `FeedFilter` z przyciskiem i wyskakującym panelem (wzorzec `NotificationMenu`) oraz `FilterChips` nad listą.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Adres i zwarta lista | `/posts`, przekierowanie `/`, `PostRow`, grupy dni, testy i wzorce listy | Przemianowanie wzorców `view-feed*` → `view-posts*` i nowe przycinanie zrzutu bez `article` |
| 2. Panel posta | `SidePanel`, `PostInspector`, trasa dziecka, historia, dane z listy lub API, usunięcie starej strony i logiki przewijania | Modalność (`inert`, krążenie Tab, powrót fokusu) i „wstecz” — sprawdza je tylko przeglądarka |
| 3. Panel „Filtry” | przycisk „Filtry: N” z panelem, znaczniki „Usuń filtr: …”, Esc | Fokus po zdjęciu znacznika i zamknięciu panelu |
| 4. Domknięcie | dokumentacja, kontrola w PR env przez agenta, galeria zrzutów | Ocena wyglądu przez właściciela może wymagać poprawek |

**Prerequisites:** S-48 na `main` (jest); issue #183; gałąź `feature/stream-and-inspector` z świeżego `origin/main`.
**Estimated effort:** ~3–4 sesje w 4 fazach (frontend; każda faza z `npm run visual:docker`, 2–5 min pod emulacją).

## Open Risks & Assumptions

- Wzorce F-06 i E2E nie mogą łapać panelu w trakcie animacji; zrzut powstaje po jej końcu albo przy wyłączonym ruchu.
- Lista stronicowana offsetem: między otwarciem a odświeżeniem nowe posty mogą przesunąć stronę; panel i tak pokazuje swój post (z API, gdy wypadł ze strony).
- Nazwy z kontraktu E2E w `ui-design.md` są wiążące; świadome odejście wymaga akceptacji właściciela.

## Success Criteria (Summary)

- Użytkownik czyta ocenę posta w panelu bez opuszczania listy, a zamknięcie wraca dokładnie tam, gdzie był (filtr, strona, fokus na wierszu).
- Link do posta, odświeżenie i logowanie z przekierowaniem otwierają ten sam post w panelu; na telefonie panel jest pełnym ekranem zamykanym „wstecz”.
- Wszystkie bramki (Vitest, E2E, F-06 z axe, CI) są zielone, kontrola w PR env przechodzi na prawdziwych danych, a właściciel akceptuje wygląd na zrzutach.
