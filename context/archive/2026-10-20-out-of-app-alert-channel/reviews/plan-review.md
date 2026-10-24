<!-- PLAN-REVIEW-REPORT -->
# Plan Review: F-04: kanał alarmowy poza aplikacją

- **Plan**: context/changes/out-of-app-alert-channel/plan.md
- **Mode**: Deep
- **Date**: 2026-10-20
- **Verdict**: REVISE → SOUND po poprawkach
- **Findings**: 1 critical, 2 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | WARNING |
| Blind Spots | FAIL |
| Plan Completeness | WARNING |

## Grounding
14/14 paths ✓, 5/5 symbols ✓, brief↔plan ✓, Progress↔Phase 23/23 ✓

## Findings

### F1 — Check bez pierwszego meldunku nigdy nie alarmuje

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Critical Implementation Details; Phase 3 (`checks.sh apply`); Phase 4 (4.6, runbook)
- **Detail**: Nowy check ma stan „new”, a healthchecks.io liczy termin tylko dla „up” (`hc/api/models.py:309-336,365`). Bez pierwszego meldunku check milczy zawsze: świeże checki produkcji, zły adres od pierwszego dnia, scenariusz 4.6.
- **Fix**: `checks.sh apply` uzbraja checki cronów w stanie „new” jednym meldunkiem sukcesu i sprawdza stan „up”; runbook i 4.6 sprawdzają stan; poprawione zdanie w Critical Implementation Details.
- **Decision**: FIXED

### F2 — Zły adres w API zatrzymałby też pre-deploy migrate

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architectural Fitness
- **Location**: Phase 2, §1 Ustawienie
- **Detail**: Komendy budują ten sam kontekst Springa bez weba (`SzczurRadarApiApplication.java:36-40`), więc bean bez warunku powstaje też w `migrate`.
- **Fix A ⭐ Recommended**: `@ConditionalOnWebApplication` — zła wartość zatrzymuje tylko start serwera, jak `PIPELINE_STALL_TIMEOUT`.
  - Strength: migrate nie zależy od kanału alarmowego; stara wersja dalej działa.
  - Tradeoff: błąd wychodzi po migracji.
  - Confidence: HIGH — wzorzec `RunWatchdog`, `PipelineStartupRecovery`.
  - Blind spot: None significant.
- **Fix B**: bean bez warunku — zła wartość zatrzymuje już `migrate`, jak `LLM_*`.
  - Strength: najwcześniejszy błąd.
  - Tradeoff: opcjonalny kanał blokuje migracje.
  - Confidence: HIGH.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A)

### F3 — Kryterium 4.2 (SVG) niczego nie sprawdza

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 4, Automated Verification 4.2
- **Detail**: `render_roadmap_svg.py` nie czyta `roadmap.md` (dane wpisane ręcznie, `:4-7`, `:267`), więc brak zmian po ponownym uruchomieniu nic nie mówi o zgodności z roadmapą.
- **Fix**: 4.2 usunięte; nowe kryterium ręczne 4.11 (dane i data w skrypcie zgodne z roadmapą, SVG wygenerowane na nowo).
- **Decision**: FIXED

### F4 — Karencja zależy od ręcznie ustawianych limitów czasu

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Critical Implementation Details (karencje)
- **Detail**: Karencje wynikają z `TRIGGER_TIMEOUT` i `BACKUP_TIMEOUT` (lista `ignore`, zmiana ręczna); dłuższy limit bez dłuższej karencji daje fałszywy alarm.
- **Fix**: runbook F-04 i README obu cronów: zmiana limitu wymaga `checks.sh apply` z nową karencją.
- **Decision**: FIXED

### F5 — Weryfikacja może uruchamiać crony ręcznie

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 4, §3 Weryfikacja w PR env
- **Detail**: Ręczne uruchomienie crona z panelu działa (`deploy-plan.md:1155`); zdanie „No manual trigger” (`:296`) jest nieaktualne.
- **Fix**: crony w PR env uruchamiane ręcznie, harmonogram zmienia tylko testowy check; sekcja F-04 poprawia `:296`; 4.9 bez przywracania harmonogramów.
- **Decision**: FIXED
