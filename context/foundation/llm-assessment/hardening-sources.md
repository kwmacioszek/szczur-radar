# LLM hardening po MVP — źródła (ironia, sarkazm, stanowisko)

> Status: materiał wejściowy dla roadmapy (`## Parked`: „LLM hardening: kierunek i stanowisko (w tym ironia)”), dodany 2026-10-05 na podstawie pomiaru F-02. Źródła zebrane przez użytkownika, **niezweryfikowane** — przed użyciem sprawdzić treść i liczby u źródła.

## Dlaczego

Raport zestawu wzorcowego F-02 (`context/changes/assessment-contract-harness/reports/gold-2026-10-05.md`, prompt v2.5): żaden z 13 zmierzonych modeli/wariantów nie spełnił progów **kierunku celów (≥75 %)** i **sprzecznych kierunków (≤3 %)**. Użytkownik złagodził je do ≥70 % / ≤10 % (2026-10-06, raport `gold-2026-10-06.md`); hardening ma przywrócić pierwotne. Korekta 2026-10-06 (pilot 9, `context/changes/assessment-contract-harness/frame.md`): ironia nie jest główną przyczyną — wg etykiet ironii użytkownika (15 ze 120 postów) na postach ironicznych kierunek jest gorszy (Luna 44 % vs 74 % na dosłownych), ale 4 z 5 sprzeczności Luny padają na posty dosłowne; Jev (TypeSafe) nie wykrywa ironii (AUC 0,49). Oznaczanie ironii było dla użytkownika bardzo trudne — zbiór ironii potrzebuje ostrzejszej definicji albo dwóch oceniających. Więcej rozumowania nie pomagało (DeepSeek/Luna `high` gorzej niż `low`/`medium`).

## Proponowany tor (po MVP)

organiczny polski zestaw wzorcowy z Wykopu (większy niż 120 postów, osobny zbiór kontrolny) → kierunek wobec celu → ironia/sarkazm → stanowisko dosłowne vs zamierzone → ablacja kontekstu → ablacja promptu → porównanie modeli → regresja kosztu i czasu.

Zasady z literatury: testować realne wypowiedzi, oceniać końcowe stanowisko, nie ufać wynikom na syntetycznym sarkazmie.

## Źródła

Bazowe:

1. Hong et al., 2026 — *You Really Didn't Get That? Benchmarking Social Pragmatic Inference for Indirect and Playful Chinese Online Comments*; 4 735 przypadków z prawdziwych interakcji, odzyskanie intencji z kontekstu (najlepszy model 81,42 %, ludzie 90,8 %). https://arxiv.org/abs/2609.04384
2. Albalawi et al., 2026-10-02 — *StanceEval 2026: The Second Stance Detection Shared Task*; tekst + cel → Favor / Against / None, generalizacja na nowe cele i domeny; sarkazm i dialekt jako źródła trudności. https://arxiv.org/abs/2610.03215
3. Mahato, Mishra, Ghosh, ACL 2026 — *LLMs in Sarcasm Detection? It's elementary! (Or is it?)*; prawie idealne wyniki na syntetycznym sarkazmie, okolice zgadywania na organicznej mowie (shortcut learning). https://aclanthology.org/2026.acl-long.1346/

Uzupełniające:

4. Guo, Sun, Guan, Findings of ACL 2026 — *Decision Biases and Intent-Irony Decoupling in Large Language Models*; model widzi elementy sytuacji, ale źle składa je w ocenę intencji/ironii. https://aclanthology.org/2026.findings-acl.962/
5. Haman, 2026 — *ContentBench / ResearchTalk v1.0*; 1000 syntetycznych postów, 500 „hard sarcasm”, ranking tanich modeli z kosztem — do wstępnego odsiewu modeli, nie jako dowód rozumienia. https://arxiv.org/abs/2602.19467, https://contentbench.github.io/
6. Xiong et al., WiNLP 2025 — *Sarc7*; 7 typów sarkazmu, zero-shot / few-shot / CoT / prompting oparty na emocjach. https://aclanthology.org/2025.winlp-main.25/, https://github.com/langlglang/sarc7
7. Lee et al., 2025 — *Pragmatic Metacognitive Prompting Improves LLM Performance on Sarcasm Detection*; prompt prowadzący przez kontekst, znaczenie dosłowne, intencję i sprzeczności. https://aclanthology.org/2025.chum-1.7/
