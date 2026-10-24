# Review fixes — missed-night-detection

Odłożone poprawki z `reviews/impl-review.md` (triage 2026-10-24). Żadna nie blokuje merge'a.

- **F2 — test dwóch źródeł.** Gdy dojdzie drugie źródło (`Source`, np. Reddit), dopisać w `MissingDaysTest` przypadek dwóch źródeł z różnymi lukami: `sources` dnia w kolejności `Source`, dzień brakujący tylko jednemu źródłu.
- **F5 — składanie dni bez danych poza kontrolerem.** Gdy pojawi się drugi konsument dni bez danych (np. alarm z crona), przenieść odczyt pokryć i pierwszych pokrytych dni z `PipelineRunController.missingDays()` do usługi odczytu w `pipeline/`; przy okazji rozważyć jeden odczyt w transakcji `readOnly` i jedno zapytanie grupujące po źródłach.
