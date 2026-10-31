<!-- PLAN-REVIEW-REPORT -->
# Plan Review: S-28 reddit-quality-and-cost — Obraz konfiguracji produkcyjnej na Reddicie przed włączeniem

- **Plan**: `context/changes/reddit-quality-and-cost/plan.md`
- **Mode**: Deep
- **Date**: 2026-10-30
- **Verdict**: REVISE → SOUND after triage (all 7 findings fixed in the plan)
- **Findings**: 0 critical, 6 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING (F6, fixed) |
| Blind Spots | WARNING (F1, F2, F5, fixed) |
| Plan Completeness | WARNING (F3, F4, F7, fixed) |

## Grounding

13/13 paths ✓, 14/14 symbols ✓, brief↔plan ✓, Progress↔Phase ✓ (4 fazy; po triage 21 wierszy).

Zweryfikowane w kodzie (sub-agent): `RedditMapper.map` daje wejście produkcyjne (`channels = [subreddit]`, `item_type` z `is_self`, `title`/`body` osobno); `RedditSearchResult.removed` i `skippedElements` są dostępne; `Recording` ma wszystkie pola (`serviceTier`, `reasoningEffort`, `channel`, `cachedTokens`, `flexFallback`, `status`, `failureClass`, `violations`), ale `write/readAll` i `Replay.of` czytają stały katalog; `Replay.judge` jest prywatny; `luna` ma ceny `openrouter`, `openai` i `openai/flex` z `cachedInput`; nagrania `luna` (120, 0 nieudanych): śr. 3370 wej. / 3188 cache / 882 wyj. → 550 × 3 próby ≈ $0,8–1,3; `GoldLabels.Label.hard` istnieje, `readLabels` jest ścisłe; `Anonymizer` pakietowy, obsługuje `@nick`, URL, e-mail, nicki autorów, nie `u/nick`; `assessments` ma `service_tier`, `flex_fallback`, `input/cached/output_tokens`, `status`, `assessed_at`; profil `measurement` po tagu; brak skanów pakietu, które nowe klasy mogłyby zepsuć.

## Findings

### F1 — Bezpiecznik $2 nie ma ścieżki po zadziałaniu

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Faza 1 §4 (`maxUsd`), Faza 2 kryteria 2.3 i 2.5
- **Detail**: Po przekroczeniu `maxUsd` ($2) test pada i 2.3 nie przejdzie; plan nie mówi, co dalej. Szacunek z nagrań `luna`: 550 × 3 próby ≈ $0,8–1,3 (+~$0,3 za dłuższe selftexty). Scenariusz z wysokim `dead` jest tym, w którym $2 jest blisko.
- **Fix A ⭐ Recommended**: bezpiecznik $4, kryterium 2.5 „≤ $4, oczekiwany ~$1”; $2 jako odniesienie w raporcie.
  - Strength: zero mechanizmu; nadal 40 % limitu klucza.
  - Tradeoff: wyższy sufit wydatku na kluczu wspólnym z Wykopem.
  - Confidence: HIGH — liczby ze 120 nagrań Luny.
  - Blind spot: tokeny megawątków nie były mierzone.
- **Fix B**: zostawić $2 i dopisać ścieżkę awaryjną (zgłoszenie kosztu, decyzja o `maxUsd`/`limit`, wyjątek w 2.3).
  - Strength: każdy dolar powyżej $2 decyduje człowiek.
  - Tradeoff: przerwa w przebiegu i kryterium z wyjątkiem.
  - Confidence: MEDIUM.
  - Blind spot: żadnego istotnego.
- **Decision**: FIXED via Fix A (plan §1.4, kryterium 2.5, Performance, Progress 2.5, plan-brief)

### F2 — Tryb produkcji to nie tylko `LLM_SERVICE_TIER`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details, kryterium 2.4
- **Detail**: Plan przenosi do przebiegu tylko tryb; `LLM_REASONING_EFFORT` zmienia wyjście i koszt, `LLM_TIMEOUT` — odsetek niepowodzeń transportu. `Recording.reasoningEffort` pozwala to sprawdzić w 2.4.
- **Fix**: trzy opcjonalne ustawienia z `production-llm.json` (unset = unset); `reasoningEffort` w kombinacji `jq` 2.4.
- **Decision**: FIXED

### F3 — „Próba” nierozstrzygnięta wobec `Recording.attempts` i produkcji

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Implementation Approach („Terminy”), Faza 1 §4
- **Detail**: `Assessor.assess` to jedno wywołanie; `Recording.attempts` to ponowienia transportu (`ATTEMPTS_PER_TIER = 3`). Pętla 3 × `assess` to nowa logika rejestratora; produkcja rozkłada próby na noce.
- **Fix**: definicja próby = jedna linia `recordings.jsonl`; wznawianie liczy linie na `post_id`; uwaga w raporcie o przybliżeniu.
- **Decision**: FIXED

### F4 — Wiersz Progress 4.7 wymaga merge'u przed archiwizacją

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 4 Manual Verification, Progress 4.7
- **Detail**: `/10x-archive` poprzedza merge, więc wiersz nie da się odhaczyć; sprzeczne z lekcją „Implementacja, przegląd i archiwizacja na jednej gałęzi”.
- **Fix**: usunąć 4.7; zamknięcie #122 jako krok runbooku po weryfikacji produkcji.
- **Decision**: FIXED

### F5 — Lista polskich słów funkcyjnych pokrywa się z angielskimi tokenami

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Faza 1 §2
- **Detail**: `a`, `i`, `to`, `on`, `no`, `by`, `ma` są też angielskimi tokenami; „AAPL is going to rally on earnings, no doubt” miałoby trzy trafienia.
- **Fix**: lista bez homografów (`że`, `się`, `nie`, `jest`, `oraz`, `ale`, `czy`, `będzie`, `które`, `jednak`) i angielska teza negatywna w `RedditMeasuresTest`.
- **Decision**: FIXED

### F6 — Anonimizacja a wejście produkcyjne: brak jawnego kroku `RedditMapper.map`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architectural Fitness
- **Location**: Faza 1 §3
- **Detail**: `Anonymizer.apply` jest wykopowy; bez jawnej kolejności `text` → kopia `RedditItem` → `RedditMapper.map` implementator mógłby złożyć `Item` ręcznie i zmierzyć inny tekst niż produkcja.
- **Fix**: kolejność wprost w Contract §3 (jak `WykopGoldSampler.java:101`), `volume.json` z `RedditSearchResult.removed`/`skippedElements`, test równości `channels` i `item_type` z `RedditMapper.map` na oryginale.
- **Decision**: FIXED

### F7 — Zapis i odczyt `Recording` poza stałym katalogiem

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Faza 1 §4 i §5
- **Detail**: `Recording.write/readAll` i `Replay.of` czytają stały katalog; §4 nie nazywa serializacji, §5 zakłada, że widoczność `judge` wystarczy.
- **Fix**: `ContractJson.MAPPER.writeValueAsString(Recording.of(…))` + `APPEND`; raport czyta linie sam i woła `judge` per linia, `Replay.of` nietknięte.
- **Decision**: FIXED
