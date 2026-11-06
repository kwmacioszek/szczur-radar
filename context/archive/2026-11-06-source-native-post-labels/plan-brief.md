# Typ posta słowami jego źródła (S-37) — Plan Brief

> Full plan: `context/changes/source-native-post-labels/plan.md`

## What & Why

Karta posta i strona szczegółów nazywają typ posta słowami Wykopu także przy poście Reddita („znalezisko” przy linku z r/stocks). Decyzja właściciela Q-13 (PRD v3, FR-023): typ pokazujemy słowami jego źródła, a słowa Wykopu nie wychodzą poza Wykop.

## Starting Point

`labels.itemType` w `szczur-radar-web/src/labels.ts` to jedna płaska mapa `post` → „wpis”, `link` → „znalezisko”, `comment` → „komentarz”. Używają jej `PostCard.vue` i `PostDetailView.vue` (meta i tytuł zastępczy). API od S-29 wysyła `source` przy każdym poście.

## Desired End State

Wykop: wpis / znalezisko / komentarz. Reddit: post / link / komentarz. Nieznane albo brakujące źródło pokazuje surowy typ. Test rejestru wymusza słowa dla każdej pary źródło × typ.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Słowa per źródło | Reddit: post → „post”, link → „link”; Wykop bez zmian | Rozstrzygnięte w PRD v3 (Q-13) | PRD |
| Miejsce słów | Tabela `ITEM_TYPE_WORDS` poza `labels`, jak `CHANNEL_PREFIXES` | `labels` odzwierciedla zbiory wartości, a to iloczyn źródło × typ | Plan |
| Nieznane/brak źródła | Surowy typ (`post`/`link`/`comment`) | Zachowuje regułę Q-13 i zwyczaj `label()` dla nieznanych wartości | Plan (user) |
| `comment` | „komentarz” dla obu źródeł | Komentarze Reddita nie są pobierane; brak potrzeby osobnego słowa | Plan |
| API i `rules.json` | Bez zmian | Zbiory wartości się nie zmieniają, zmiana tylko w SPA | Plan |
| Weryfikacja | Vitest + build + agent z `playwright-cli`, bez PR env | Zmiana tekstu; lekcja „stronę sprawdza agent” | Plan (user) |

## Scope

**In scope:** `labels.ts` (tabela, `postType`), `PostCard.vue`, `PostDetailView.vue` (meta i tytuł zastępczy), testy i test rejestru, kitchen sink (link Reddita), zdanie w `REPOSITORY_GUIDELINES.md`.

**Out of scope:** API, migracje, `rules.json`, osobne słowa dla komentarzy, filtr po źródle, neutralne polskie słowa dla nieznanego źródła, weryfikacja w PR env.

## Architecture / Approach

Funkcja `postType(source, itemType)` czyta małą stałą mapę i oddaje surowy typ, gdy wiersza brak. Faza 1 dokłada ją bez zmiany widoków; faza 2 przełącza widoki, usuwa płaski `labels.itemType` i zawęża test zbiorów `labels`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Etykiety zależne od źródła | Tabela, `postType`, testy funkcji i rejestru | Test rejestru musi łapać brakujący wiersz |
| 2. Widoki i weryfikacja | Karta i strona szczegółów na `postType`, usunięty `labels.itemType`, kitchen sink, wytyczne, kontrola agenta | Tytuł zastępczy strony szczegółów („Post użytkownika …”) |

**Prerequisites:** brak; zmiana tylko w SPA.
**Estimated effort:** ~1 sesja, 2 małe fazy.

## Open Risks & Assumptions

- Mieszane wersje (stare API bez `source`, nowy web) pokażą na chwilę surowy typ — przyjęte.
- Tytuł zastępczy „Post użytkownika x” dla Reddita: słowo „post” bywa też tytułem ładowania; akceptacja właściciela w PR.
- Roadmapa zakłada zmianę `rules.json`; plan jej nie robi, bo zbiory wartości się nie zmieniają.

## Success Criteria (Summary)

- Post Reddita nigdzie nie pokazuje „wpis” ani „znalezisko”; post Wykopu pokazuje je jak dotąd.
- Dodanie źródła lub typu bez słów czerwieni `npm test`.
- `npm test`, `npm run build` i `SpaRulesTest` zielone; agent potwierdził wygląd kart i strony szczegółów.
