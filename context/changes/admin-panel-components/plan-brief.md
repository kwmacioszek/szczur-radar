# Panel admina w trzech częściach (S-50) — Plan Brief

> Full plan: `context/changes/admin-panel-components/plan.md`

## What & Why

Panel admina dziś to jedna długa strona z pięcioma sekcjami. Właściciel zgłosił go jako „nieczytelny”, a zaakceptowany projekt (`ui-design.md`) dzieli go na trzy części o jednym temacie każda, z własnymi adresami: „Przebiegi”, „Jakość ocen” i „Nieudane posty”. Wycinek S-50 (issue #182) wdraża ten podział według prototypów `admin-runs`, `admin-quality`, `admin-posts` i `run`.

## Starting Point

- `src/views/AdminView.vue` ma 964 linie i pięć sekcji, każda z własnym ładowaniem, błędem i ponowieniem z S-48. Sekcje są powiązane: start przebiegu i ponowienie postów odświeżają przebiegi, a „Wpisz do formularza” wypełnia formularz startu.
- Nieudanych postów widać tylko pierwsze 50.
- API ma prawie wszystko. Brakuje źródła i kanału nieudanego posta, a nieudanego posta nie da się otworzyć w aplikacji, bo API zwraca tylko posty ocenione.

## Desired End State

Admin przełącza części paskiem pod nagłówkiem „Panel admina”. Bieżąca część jest zaznaczona, a przy „Nieudanych postach” stoi ich liczba.

Przebiegi i nieudane posty są tabelami:
- nieudane posty mają kolumny Źródło, Stan, Próby i Ostatni błąd, stronicowanie w adresie, tytuł prowadzący do oryginału i komunikat po ponowieniu z odnośnikiem do nowego przebiegu;
- jakość ocen to karty źródeł, w których każda miara ma wartość, „N z M prób · próg P%”, stan słowami i pasek z kreską progu.

Strona przebiegu ma nagłówek „Przebieg z <data>” i układ prototypu. Każda część ładuje się, jest pusta albo ma błąd niezależnie od pozostałych.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Kolumna „Źródło” (G1) | Dodać `source` i `channel` do `FailedPost` w API, kontrakcie, typie TS i atrapie | Mała zmiana na wzór `PostItem`, bez migracji; zgodna z zaakceptowanym prototypem | Plan |
| Tytuł nieudanego posta (G2) | Odnośnik do oryginału (`webUrl`) w nowej karcie; bez `webUrl` tekst; zmiana zapisana w `ui-design.md` | API nie zwraca nieocenionego posta, a panel posta powstaje w S-51; oryginał to to, co admin sprawdza | Plan |
| Ponad 50 nieudanych postów | `PagerNav` („Poprzednie”/„Następne”), strona w adresie, zaznaczenie per strona | Wspólny komponent z S-48 z przetestowanym fokusem; nic nie jest ukryte | Plan |
| Informacja po „Ponów zaznaczone” | Komunikat powodzenia z odnośnikiem do strony nowego przebiegu | Tabela przebiegów jest teraz na innym adresie; jeden klik do postępu, zgodnie z kontraktem | Plan |
| Struktura tras | `/admin` jako rodzic z układem panelu; dzieci `admin`, `admin-quality`, `admin-posts`; `admin-run` obok | Nazwa `admin` zostaje, więc istniejące odnośniki działają; układ i licznik nie montują się na nowo | Plan |
| Licznik w pasku | `total` z `GET /api/admin/posts/failed?size=1` przy wejściu i zmianie części; brak licznika przy 0 i przy błędzie | Bez nowej trasy API; błąd licznika nie zasłania stanu części | Plan |
| Znacznik źródła w jakości | „ponad progiem”, gdy którakolwiek miara jest ponad progiem; inaczej stan nieudanych prób | Prototyp pokazuje źródło ponad progiem przy jednej mierze ponad progiem | Plan |
| „Pokaż starsze” w przebiegach | Zostaje | Prototyp `admin-runs` je ma | Plan |

## Scope

**In scope:**
- `source`/`channel` w `FailedPost` (API, kontrakt, typ, atrapa) i tryb atrapy `FAILED: many` (57 postów);
- komponenty `AdminPartsNav` i `DataTable` na kitchen sink;
- trasy i układ panelu, trzy widoki części, strona przebiegu według prototypu;
- testy jednostkowe, E2E, wzorce i axe;
- zapis G2 w `ui-design.md`, `REPOSITORY_GUIDELINES.md`, PR env i galeria dla właściciela.

**Out of scope:**
- panel boczny posta i otwieranie nieudanego posta w aplikacji (S-51);
- nowa trasa API do czytania dowolnego posta;
- zaznaczanie na wielu stronach;
- stronicowanie zamiast „Pokaż starsze”;
- miara „kanał jako cel” (S-60);
- osobne tryby atrapy dla pojedynczych sekcji „Przebiegów”.

## Architecture / Approach

Powłoka (`AppShell`) → trasa `/admin` z `AdminPanelView`, który zawiera `h1` „Panel admina”, `AdminPartsNav` z licznikiem i `<RouterView>`. W `<RouterView>` stoi `AdminRunsView`, `AdminQualityView` albo `AdminFailedPostsView`.

Każda część trzyma stany sekcji z S-48. Wspólny `sectionFocus` przechodzi do `src/views/admin/sectionState.ts`. Układ udostępnia częściom `refreshFailedCount()` i `focusTitle()` przez `provide`/`inject`. `RunDetailView` zostaje osobną trasą `admin-run`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Pochodzenie nieudanego posta w API | `source`/`channel` w odpowiedzi, kontrakcie, typie i atrapie; tryb `FAILED` | rozjazd pliku kontraktu z atrapą (pilnuje `mockApi.test`) |
| 2. Komponenty „Części panelu admina” i „Tabela” | `AdminPartsNav`, `DataTable` na kitchen sink, w `CLEANED` i `STATES` | nowa sekcja katalogu przesuwa wzorce innych sekcji (stoją na końcu) |
| 3. Rozbicie panelu na trzy adresy | trasy, układ, trzy części z dzisiejszymi sekcjami, testy przeniesione | utrata asercji przy dzieleniu 1309 linii testów |
| 4. Części według prototypów | tabele, karty jakości, stronicowanie, komunikat z odnośnikiem, odnośnik do oryginału | fokus i zaznaczenie przy zmianie strony i po akcjach |
| 5. Strona przebiegu według prototypu | „Przebieg z …”, metadane, liczniki, źródła, kroki | zmiana nagłówka łamie testy E2E i powłoki |
| 6. Testy przepływów, dokumentacja i weryfikacja | E2E nowych przepływów ze złamaniami, G2 w projekcie, PR env, galeria | PR env bez danych (agent startuje jeden przebieg) |

**Prerequisites:** S-48 w `main` (97992dd); Docker do `npm run visual:docker` i Testcontainers; dostęp do Railway PR env.
**Estimated effort:** ~3–4 sesje przez 6 faz; fazy 3 i 4 są największe.

## Open Risks & Assumptions

- Wzorce wizualne zmieniają się w trzech fazach (2, 3–4, 5). Opis PR musi wymienić grupy i powody, bo inaczej przegląd nie rozpozna niezamierzonej zmiany.
- Rozbicie usuwa dwa powiązania: ponowienie już nie odświeża tabeli przebiegów, a koniec przebiegu już nie odświeża jakości ocen. Zastępują je komunikat z odnośnikiem i świeży odczyt przy wejściu w część.
- G2 odchodzi od zaakceptowanego prototypu (tytuł → panel posta). Zmianę zatwierdził właściciel w planie, a faza 6 zapisuje ją w `ui-design.md`.

## Success Criteria (Summary)

- Admin pracuje w trzech częściach pod własnymi adresami z paskiem części, a każdy przepływ z kontraktu `ui-design.md:584-594` ma zielony test E2E.
- Wszystkie bramki są zielone: `./mvnw -B verify`, `npm test`, `npm run build`, `npm run e2e` i `npm run visual:docker` dwa razy z rzędu. Kontrola PR env przez agenta zgadza się z API.
- Właściciel akceptuje wygląd w galerii zrzutów obok prototypów.
