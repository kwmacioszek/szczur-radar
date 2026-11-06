# Typ posta słowami jego źródła (S-37) — plan implementacji

## Overview

Karta posta w feedzie i strona szczegółów pokazują dziś typ posta słowami Wykopu (`wpis` / `znalezisko`) także przy postach Reddita (rozbieżność D-12). Decyzja właściciela Q-13 (PRD v3, FR-023): typ posta pokazujemy słowami jego źródła — Wykop: `post` → „wpis”, `link` → „znalezisko”; Reddit: `post` → „post”, `link` → „link”; słowa Wykopu nie pojawiają się przy postach Reddita. Zmiana dotyczy wyłącznie SPA.

## Current State Analysis

- `szczur-radar-web/src/labels.ts:3` — `labels.itemType` to jedna płaska mapa `{ post: 'wpis', link: 'znalezisko', comment: 'komentarz' }`, bez wiedzy o źródle.
- Dwa miejsca użycia: `src/components/PostCard.vue:26` (`<span class="type">`) i `src/views/PostDetailView.vue:155` (meta posta) oraz `PostDetailView.vue:38` (tytuł zastępczy „{Typ} użytkownika {autor}”, gdy post nie ma tytułu).
- `PostItem.source` (string: `wykop` / `reddit`) i `channel` są w odpowiedzi API od S-29 (`src/api/posts.ts:73`); typ posta w API zostaje wspólnym `post` / `link` / `comment` (reguła repozytorium: posty nie przechowują nazw typów źródła).
- Rejestr reguł: `rules.test.ts:81-91` wymaga, by zbiory `labels` były dokładnie zbiorami `values` z `rules.json`, a klucze każdego zbioru dokładnie jego wartościami. Precedens słów zależnych od źródła poza `labels`: `CHANNEL_PREFIXES` (`labels.ts:80`) z własnym testem (`rules.test.ts:93-97`).
- `rules.json` (`values.itemType`, `values.source`) i `SpaRulesTest` się nie zmieniają: zbiory wartości zostają takie same. Roadmapa zakłada zmianę rejestru po obu stronach; w rzeczywistości zmienia się tylko SPA i jej test rejestru.
- Kitchen sink ma już post Reddita (`KitchenSinkView.vue:291`, typ `post`); `dev/mock-api.js` zwraca co trzeci post jako Reddit (kanały `stocks`, `ValueInvesting`, `investing`).

## Desired End State

- Post Wykopu: `post` → „wpis”, `link` → „znalezisko”, `comment` → „komentarz”. Post Reddita: `post` → „post”, `link` → „link”, `comment` → „komentarz”. Tak na karcie, w meta strony szczegółów i w jej tytule zastępczym (np. „Post użytkownika x”, „Link użytkownika x”).
- Źródło nieznane albo brakujące (starsze API wdrożone osobno od web, przyszłe źródło bez wiersza w tabeli) pokazuje surowy typ (`post` / `link` / `comment`), tak jak `label()` pokazuje każdą nieznaną wartość. Nigdy słów innego źródła.
- Test rejestru wymusza, by tabela miała dokładnie źródła z `rules.json` × typy z `rules.json`; dodanie źródła lub typu bez słów czerwieni CI.
- Weryfikacja: `npm test`, `npm run build`, a wygląd kart Wykopu i Reddita sprawdza agent przez `playwright-cli`.

### Key Discoveries:

- `szczur-radar-web/src/labels.ts:3` jest jedynym miejscem słów typu; `PostCard.vue:26` i `PostDetailView.vue:38,155` jedynymi wywołaniami `label('itemType', …)`.
- `rules.test.ts:89` wymaga kluczy `labels.itemType` równych wartościom enuma, więc mapa źródło × typ nie może być zbiorem `labels` — idzie obok, jak `CHANNEL_PREFIXES`.
- `PostCard.test.ts:92,154` i `PostDetailView.test.ts:176` zakładają „wpis” dla posta bez zmiany źródła: nadal prawdziwe dla Wykopu, ale testy muszą nazwać źródło.
- Tytuł zastępczy strony szczegółów używa typu jako słowa na początku zdania: `PostDetailView.test.ts:162-163` („Wpis użytkownika uzytkownik1”, „Komentarz”).

## What We're NOT Doing

- Zmian w API, `ItemType`, migracjach ani `rules.json` / `SpaRulesTest` (zbiory wartości się nie zmieniają).
- Własnych słów dla `comment` w zależności od źródła: „komentarz” dla obu (komentarze Reddita nie są dziś pobierane).
- Filtra ani sortowania po typie lub źródle (FR-008 zostaje nice-to-have).
- Zbioru neutralnych polskich słów dla nieznanego źródła (łamałby regułę Q-13 słowem Wykopu).
- Weryfikacji w Railway PR env na prawdziwych danych (zmiana to tekst; wystarcza mock i kitchen sink).

## Implementation Approach

Dwie fazy. Faza 1 dodaje do `labels.ts` tabelę słów i funkcję `postType(source, itemType)` (z surowym typem jako fallbackiem) wraz z testami, nie ruszając istniejących widoków ani `labels.itemType` — aplikacja i jej testy zostają zielone. Faza 2 przełącza oba widoki na nową funkcję, usuwa płaski `labels.itemType` (i wyłącza `itemType` z testu zbiorów `labels`, a dopisuje test tabeli), poprawia testy komponentów, dodaje przypadek linku Reddita na kitchen sink, zapisuje regułę w `REPOSITORY_GUIDELINES.md` i kończy kontrolą wyglądu przez agenta.

## Phase 1: Etykiety typu zależne od źródła

### Overview

Nowa tabela i funkcja w `labels.ts` z własnymi testami; nic jeszcze z niej nie korzysta.

### Changes Required:

#### 1. Tabela i funkcja

**File**: `szczur-radar-web/src/labels.ts`

**Intent**: Dodać słowa typu posta zależne od źródła, obok `CHANNEL_PREFIXES`, jako rejestr spoza `labels` (bo `labels` odzwierciedla zbiory wartości, a ta tabela jest iloczynem dwóch zbiorów). Funkcja zwraca słowo źródła, a dla nieznanego źródła lub typu — surowy typ.

**Contract**: `export const ITEM_TYPE_WORDS: Record<string, Record<string, string>>` (klucze: źródło → typ) z Wykopem `{ post: 'wpis', link: 'znalezisko', comment: 'komentarz' }` i Redditem `{ post: 'post', link: 'link', comment: 'komentarz' }`; `export function postType(source: string | undefined, itemType: string): string` — `ITEM_TYPE_WORDS[source]?.[itemType] ?? itemType`. Komentarz nad tabelą: słowa Wykopu nigdy dla Reddita (PRD FR-023, Q-13), nie jest zbiorem `labels`. Parametr `source` dopuszcza `undefined`, bo starsze API nie wysyła pola (jak w `PostCard.test.ts` „older API”).

#### 2. Testy funkcji

**File**: `szczur-radar-web/src/labels.test.ts`

**Intent**: Przypiąć tabelę słów przypadkami z PRD: `it.each` dla wszystkich sześciu par źródło × typ (w tym `reddit` + `link` → „link”, `reddit` + `post` → „post”, `wykop` + `link` → „znalezisko”), nieznane źródło (`x`) i brak źródła (`undefined`) → surowy typ, nieznany typ → surowy typ.

#### 3. Test rejestru tabeli

**File**: `szczur-radar-web/src/contract/rules.test.ts`

**Intent**: Nowy `describe('item type words')` obok `channel prefixes`: klucze `ITEM_TYPE_WORDS` to dokładnie `values.source`, a klucze każdego wiersza to dokładnie `values.itemType`; żadne słowo nie jest puste; żadne słowo Wykopu (`wpis`, `znalezisko`) nie występuje w wierszu Reddita.

### Success Criteria:

#### Automated Verification:

- Testy jednostkowe SPA przechodzą: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Test rejestru tabeli jest czerwony po usunięciu wiersza `reddit` z `ITEM_TYPE_WORDS` (próba psucia): `cd szczur-radar-web && npx vitest run src/contract/rules.test.ts`

---

## Phase 2: Widoki, testy komponentów i weryfikacja

### Overview

Karta i strona szczegółów używają `postType`; płaski `labels.itemType` znika; opis reguły w wytycznych; kontrola wyglądu.

### Changes Required:

#### 1. Karta posta

**File**: `szczur-radar-web/src/components/PostCard.vue`

**Intent**: Typ w `<span class="type">` pokazuje słowo źródła posta.

**Contract**: `{{ postType(post.source, post.itemType) }}` zamiast `label('itemType', post.itemType)`; import `postType` z `../labels`, a `label` tylko jeśli nadal używany.

#### 2. Strona szczegółów

**File**: `szczur-radar-web/src/views/PostDetailView.vue`

**Intent**: Meta posta (linia ~155) i tytuł zastępczy (linia ~38) używają słowa źródła, tak by Reddit dostawał „Post użytkownika x” / „Link użytkownika x”, a nie słowa Wykopu.

**Contract**: oba wywołania `label('itemType', …)` → `postType(item.source, item.itemType)`; tytuł nadal z wielką pierwszą literą i „Post” zostaje tytułem do czasu wczytania posta.

#### 3. Usunięcie płaskiego zbioru

**File**: `szczur-radar-web/src/labels.ts`, `szczur-radar-web/src/contract/rules.test.ts`

**Intent**: Usunąć `itemType` z `labels` (jedno źródło prawdy: `ITEM_TYPE_WORDS`) i w teście `labels` wyłączyć `itemType` z listy zbiorów, tak jak wyłączony jest `attentionReason` — z komentarzem, że jego słowa zależą od źródła i mają test w `item type words`. Zbiór `itemType` w `UNION_TUPLES` (typ TS `ItemType`) zostaje.

**Contract**: `Object.keys(labels)` == wartości `values` bez `attentionReason` i `itemType`; test zbiorów `labels` odpowiednio zawężony.

#### 4. Testy komponentów

**File**: `szczur-radar-web/src/components/PostCard.test.ts`, `szczur-radar-web/src/views/PostDetailView.test.ts`

**Intent**: Pokazać słowa obu źródeł w obu widokach: karta posta Reddita typu `post` → „post”, typu `link` → „link” (i nie „wpis” / „znalezisko”); Wykop `link` → „znalezisko”; brak `source` → surowy typ bez „undefined”; strona szczegółów: meta i tytuł zastępczy dla Reddita („Post użytkownika …”, „Link użytkownika …”) oraz dla Wykopu bez zmian („Wpis użytkownika uzytkownik1”).

**Contract**: istniejące asercje „wpis” zostają przy domyślnym poście Wykopu (źródło `wykop`); nowe przypadki obok.

#### 5. Kitchen sink

**File**: `szczur-radar-web/src/views/KitchenSinkView.vue`

**Intent**: Obok istniejącego posta Reddita (`post`) dodać post Reddita typu `link`, by kitchen sink pokazywał każdą parę źródło × typ, którą widzi użytkownik (Wykop `post` / `link` / `comment` już są).

**Contract**: nowy element listy przykładów posta z `source: 'reddit'`, `itemType: 'link'`, tytułem, `webUrl` na `reddit.com`, wzorowany na istniejącym przykładzie Reddita.

#### 6. Reguła w wytycznych

**File**: `REPOSITORY_GUIDELINES.md`

**Intent**: W akapicie o `PostCard` (sekcja UI) dopisać, że typ posta jest pokazywany słowami jego źródła z `ITEM_TYPE_WORDS` w `src/labels.ts` (`postType`), bez słów Wykopu przy Reddicie, a nowe źródło dodaje wiersz do tej tabeli (test w `rules.test.ts` tego wymaga).

**Contract**: jedno zdanie w istniejącym punkcie `Shared components`; bez zmian w `context/`.

### Success Criteria:

#### Automated Verification:

- Testy SPA przechodzą: `cd szczur-radar-web && npm test`
- Typecheck i build przechodzą: `cd szczur-radar-web && npm run build`
- Żaden widok nie woła już `label('itemType'`: `! grep -rn "label('itemType'" szczur-radar-web/src --include='*.vue' --include='*.ts'`
- Testy API dotyczące rejestru nie zmieniły się i przechodzą: `cd szczur-radar-api && ./mvnw -B -q test -Dtest=SpaRulesTest`
- Agent sprawdza karty przez `playwright-cli` na kitchen sink (`npm run dev`, `/kitchen-sink`) i w feedzie z `dev/mock-api.js`: karta Wykopu pokazuje „wpis” / „znalezisko” / „komentarz”, karta Reddita „post” / „link”, bez słów Wykopu; w jasnym i ciemnym motywie oraz przy 375 px (7 stanów z wytycznych: N/A z uzasadnieniem dla stanów, których zmiana tekstu nie dotyka)
- Agent sprawdza stronę szczegółów posta Reddita w mocku: meta „post” lub „link”, tytuł zastępczy „Post użytkownika …” (`playwright-cli` snapshot)

#### Manual Verification:

- Właściciel przegląda zmianę w PR (diff `labels.ts`, widoków i wytycznych) i akceptuje słowa tytułu zastępczego („Post użytkownika …”, „Link użytkownika …”)

---

## Testing Strategy

### Unit Tests:

- `postType`: wszystkie sześć par źródło × typ, nieznane źródło, brak źródła, nieznany typ.
- Rejestr: tabela ma dokładnie źródła i typy z `rules.json`; Reddit nie ma słów Wykopu.
- `PostCard` i `PostDetailView`: meta i tytuł zastępczy dla obu źródeł; brak `source` nie daje „undefined”.

### Integration Tests:

- Brak: zmiana nie dotyka API; `SpaRulesTest` pilnuje, że `rules.json` jest nadal zgodny z enumami serwera.

### Manual Testing Steps:

1. Agent: `npm run dev` z `dev/mock-api.js`, feed `/` — karty Wykopu i Reddita; strona szczegółów posta Reddita; `/kitchen-sink`.
2. Właściciel: ocena słów tytułu zastępczego w PR.

## Performance Considerations

Brak: wyszukanie w małej stałej mapie przy renderze.

## Migration Notes

Brak migracji. API i web wdrażają się niezależnie: starsze API bez `source` pokazuje surowy typ (`post`), co jest świadomym, krótkotrwałym kompromisem. Nowe API z nowym web nie ma tego problemu, a obecne API (S-29) już wysyła `source`.

## References

- PRD: `context/foundation/prd.md` FR-023, doprecyzowanie 2026-11-04 (decyzja Q-13)
- Roadmapa: `context/foundation/roadmap.md` S-37
- Destylacja domeny: `context/domain/domain-distillation.md` (D-12, R-45, B-11, Q-13, kandydat #4)
- Precedens tabeli poza `labels`: `szczur-radar-web/src/labels.ts` (`CHANNEL_PREFIXES`) i `rules.test.ts` (`channel prefixes`)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Etykiety typu zależne od źródła

#### Automated

- [x] 1.1 Testy jednostkowe SPA przechodzą (`npm test`) — 8db574b
- [x] 1.2 Typecheck i build przechodzą (`npm run build`) — 8db574b
- [x] 1.3 Test rejestru tabeli jest czerwony po usunięciu wiersza `reddit` z `ITEM_TYPE_WORDS` — 8db574b

### Phase 2: Widoki, testy komponentów i weryfikacja

#### Automated

- [x] 2.1 Testy SPA przechodzą (`npm test`)
- [x] 2.2 Typecheck i build przechodzą (`npm run build`)
- [x] 2.3 Żaden widok nie woła już `label('itemType'` (grep)
- [x] 2.4 `SpaRulesTest` przechodzi bez zmian po stronie API
- [x] 2.5 Agent sprawdza karty Wykopu i Reddita przez `playwright-cli` (kitchen sink i feed z mocka, oba motywy, 375 px)
- [x] 2.6 Agent sprawdza stronę szczegółów posta Reddita w mocku (meta i tytuł zastępczy)

#### Manual

- [ ] 2.7 Właściciel akceptuje słowa tytułu zastępczego w PR
