---
change_id: assessment-contract-harness
title: Kontrakt oceny LLM V2 z walidatorem i pomiarem na zestawie wzorcowym
status: archived
created: 2026-09-27
updated: 2026-10-06
archived_at: 2026-10-06T07:35:54Z
---

## Notes

Roadmap F-02. Kontrakt V2 (`context/foundation/llm-assessment/contract-v2.md`, V1 w archiwum) oparty na pilotażach na prawdziwych postach: rdzeń (trafność, cele ze stanowiskiem autora) + tezy z cytatem, bez grafu argumentów. Kod w Javie (Spring Boot 4, Embabel; plan v3): modele, walidator D1–D15 z klasyfikacją błędów i normalizacją cytatów, odsiew wpisów bez treści, `Assessor` z jednym wywołaniem dostawcy, klienci Gemini / DeepSeek / OpenRouter, konektor Wykop (`sources/wykop`). Pomiar na zestawie wzorcowym (120 postów oznaczonych przez użytkownika): model domyślny dla S-02 — GPT-6 Luna (`medium`) przez OpenRouter, nagrana w trybie standardowym (flex do wyboru operatora); progi kierunku i sprzeczności złagodzone decyzją użytkownika (raport `reports/gold-2026-10-06.md`). Plan v3 zastępuje wcześniejszy zakres (V1 jako kod w Pythonie, 12 wymyślonych przypadków, macierz 4 dostawców).
