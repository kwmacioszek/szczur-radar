---
change_id: assessment-contract-harness
title: Kontrakt oceny LLM V2 z walidatorem i pomiarem na zestawie wzorcowym
status: implementing
created: 2026-09-27
updated: 2026-10-03
archived_at: null
---

## Notes

Roadmap F-02. Kontrakt V2 (`context/foundation/llm-assessment/contract-v2.md`, V1 w archiwum) oparty na pilotażach na prawdziwych postach: rdzeń (trafność, cele ze stanowiskiem autora) + tezy z cytatem, bez grafu argumentów. Kod: modele, walidator z klasyfikacją błędów i normalizacją cytatów, odsiew wpisów bez treści, `assess()` z jednym wywołaniem dostawcy. Pomiar na zestawie wzorcowym (~120 postów oznaczonych przez użytkownika): GPT-6 Luna flex (pierwszy wybór), DeepSeek Flash (zapas), Gemini 3.1 Flash-Lite (punkt odniesienia) → model domyślny dla S-02. Plan v2 zastępuje wcześniejszy zakres (V1 jako kod, 12 wymyślonych przypadków, macierz 4 dostawców).
