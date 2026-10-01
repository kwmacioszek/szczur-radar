# Pilotaże oceny LLM (F-02)

Dowody, na których oparto kontrakt V2 (`context/foundation/llm-assessment/contract-v2.md`) i plan zmiany. Opis przebiegów i wniosków: `../frame.md` (sekcje „Pilot …”).

## Układ

| Miejsce | Zawartość |
| --- | --- |
| `./` | próbki i etykiety: `wykop-gielda-sample-2026-09-29.json`, `labeling-set-2026-09-28.json`, `labels-2026-09-28.json`, arkusze `labeling*.html`, instrukcja `labeling-guidelines.md` |
| `scripts/` | skrypty pilotaży i analiz oraz prompty (`prompt-short.md`, `prompt-short-en.md`, `prompt-v2-rules.md`) |
| `runs/` | zachowane wyniki przebiegów (zanonimizowane) |

Ścieżki w skryptach są wyprowadzane z `__file__`, więc skrypty działają z dowolnego katalogu roboczego. Wszystkie dane są zanonimizowane: nicki → `@uzytkownikN`, adresy → `<url>`, białe znaki bez zmian. W folderze nie ma kluczy API.

## Analizy offline (bez wywołań modeli, bez kluczy)

Działają na zachowanych próbkach, etykietach i `runs/`:

```bash
P=context/changes/assessment-contract-harness/pilot/scripts
python3 $P/analyze2.py        # pilot 2: zgodność z kontraktem, koszt, zgodność modeli, symulacja kaskady
python3 $P/analyze3.py        # pilot 3: paczkowanie vs pojedyncze wywołania
python3 $P/analyze5.py        # pilot 5: Reddit
python3 $P/analyze6.py        # pilot 6: Jev (TypeSafe)
python3 $P/analyze7.py        # pilot 7: Span-01 (Respan)
python3 $P/analyze8.py        # pilot 8: GPT-6 Luna
python3 $P/compare_gold.py    # piloty 2–4 względem etykiet rundy 1
python3 $P/frame2_checks.py   # ramowanie, runda 2
```

`analyze3.py` (przez `pilot.py`) czyta kontrakt V1 z archiwum: `context/foundation/archive/2026-10-01-llm-assessment-contract-v1.md`. Zależności przez `exec`: `analyze3.py` → `analyze2.py` + `pilot.py`; `analyze7.py`, `analyze8.py` → `analyze6.py`.

## Ponowne wykonanie pilotaży (wywołania modeli, koszt)

Klucze tylko w powłoce, nigdy w repo: `GEMINI_API_KEY`, `DEEPSEEK_API_KEY`, `OPENROUTER_API_KEY`. Wyniki są zapisywane do `runs/` i **nadpisują** zachowane pliki o tej samej nazwie; nowych wyników z surowych danych nie commituj bez anonimizacji.

| Skrypt | Wymagane dane wejściowe (nie ma ich w repo) | Klucze | Wynik w `runs/` |
| --- | --- | --- | --- |
| `pilot.py MODELE [PLIK] --p1 gielda-p1.json --p2 gielda-p2.json` | surowe strony strumienia #gielda z Wykop API v3 (2026-09-29) | Gemini, DeepSeek (wg modeli) | `PLIK` (np. `pilot1-gemini.json`) |
| `pilot2.py --posts gielda-2026-09-28.json` | surowe wpisy #gielda z 2026-09-28 | Gemini, DeepSeek | `pilot2-results.json` |
| `pilot3.py` | — (`runs/pilot2-results.json`) | Gemini, DeepSeek | `pilot3-results.json` |
| `pilot4.py` | — (`runs/pilot2-results.json`, `labeling-set-2026-09-28.json`) | Gemini, DeepSeek | `pilot4-results.json` |
| `pilot5.py MODELE [PROMPT_EN] --posts reddit-sample-24_29.json` | posty Arctic Shift (r/stocks, r/ValueInvesting, r/investing, 2026-09-24…29) | DeepSeek (+ Gemini) | `pilot5-reddit-<modele>[-en].json` |
| `pilot6.py [run1\|run2]` | — (`runs/pilot2-results.json`) | OpenRouter | `pilot6-jev-<run>.json` |
| `pilot7.py` | — (`runs/pilot2-results.json`) | OpenRouter | `pilot7-span01*.json` |
| `pilot8.py` | — (`runs/pilot2-results.json`) | OpenRouter | `pilot8-gpt6luna.json` |

`pilot.py`, `pilot2.py` i `pilot5.py` sprawdzają pliki wejściowe przed jakimkolwiek wywołaniem API; brak pliku kończy się komunikatem z nazwą argumentu i oczekiwanego pliku. Brak tych danych nie blokuje analiz historycznych — opierają się na zachowanych `runs/`. Zależności przez `exec`: `pilot2.py`, `pilot3.py`, `pilot8.py` → `pilot.py`; `pilot4.py`, `pilot5.py` → `pilot3.py`.

Przykład:

```bash
python3 $P/pilot5.py deepseek-flash prompt-short-en.md --posts ~/dane/reddit-sample-24_29.json
```
