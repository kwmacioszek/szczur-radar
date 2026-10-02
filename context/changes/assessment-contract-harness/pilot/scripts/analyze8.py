import json, os, collections, statistics as st
S = os.path.dirname(os.path.abspath(__file__))     # skrypty i prompty
PILOT = os.path.dirname(S)                          # próbki i etykiety
RUNS = os.path.join(PILOT, "runs")                  # wyniki przebiegów
exec(open(f"{S}/analyze6.py").read().split('ids = [i for i in J')[0])   # G, DS, J (Jev), gold, net_llm, net_gold, norm
R = json.load(open(os.path.join(RUNS, "pilot8-gpt6luna.json"))); L = {r["id"]: r["output"] for r in R if r.get("output")}
pct = lambda a, b: f"{100*a/max(b,1):.0f}%"
stt = collections.Counter(r["status"] for r in R)
print("GPT-6 Luna: zgodność z kontraktem", pct(stt["ok"], len(R)), dict(stt))
for r in R:
    if r["status"] != "ok": print("  ", r["id"], r["status"], (r.get("error") or str(r.get("violations")))[:200])
u = [r["usage"] for r in R if r.get("usage")]
rt = [((x.get("completion_tokens_details") or {}).get("reasoning_tokens") or 0) for x in u]
print(f"tokeny/post: wej {st.mean(x['prompt_tokens'] for x in u):.0f} (z cache {st.mean((x.get('prompt_tokens_details') or {}).get('cached_tokens') or 0 for x in u):.0f}), wyj {st.mean(x['completion_tokens'] for x in u):.0f} (w tym rozumowanie {st.mean(rt):.0f}); koszt/post ${st.mean(x['cost'] for x in u):.5f}; łącznie ${sum(x['cost'] for x in u):.3f}; przy 12k/mies. ${12000*st.mean(x['cost'] for x in u):.1f}; mediana czasu {st.median(r['sec'] for r in R):.1f}s")
ids = [i for i in L if i in G and i in DS]
print(f"rozkład: trafność {dict(collections.Counter(L[i]['relevance'] for i in ids))} | dominujący {dict(collections.Counter(net_llm(L[i]) for i in ids))} | celów/post mediana {st.median(len(L[i]['targets']) for i in ids)}")
def unc(o): return o["relevance"] == "unclear" or bool(set(o["flags"]) & {"possible_sarcasm", "missing_context"}) or any(t["stance"]["direction"] in ("unclear", "mixed") or t["reference"] in ("ambiguous", "unresolved") for t in o["targets"])
print(f"sygnał niepewności w odpowiedzi: Luna {pct(sum(unc(L[i]) for i in ids),len(ids))} | Gemini {pct(sum(unc(G[i]) for i in ids),len(ids))} | DeepSeek {pct(sum(unc(DS[i]) for i in ids),len(ids))}")
print("\n== zgodność między modelami (n=%d) ==" % len(ids))
for a, A, b, B in (("Luna", L, "Gemini 3.1 FL", G), ("Luna", L, "DeepSeek Flash", DS), ("Gemini 3.1 FL", G, "DeepSeek Flash", DS)):
    rel = sum(A[i]["relevance"] == B[i]["relevance"] for i in ids); dom = sum(net_llm(A[i]) == net_llm(B[i]) for i in ids); opp = sum({net_llm(A[i]), net_llm(B[i])} == {"bullish", "bearish"} for i in ids)
    print(f"{a:14} vs {b:15} trafność {pct(rel,len(ids))} | dominujący kierunek {pct(dom,len(ids))} | sprzeczny {opp}")
gid = [i for i in gold if i in ids]; clear = [i for i in gid if gold[i]["relevance"] != "unclear"]
print("\n== względem Twoich etykiet z rundy 1 (n=%d; binarnie n=%d) ==" % (len(gid), len(clear)))
for name, M in (("GPT-6 Luna", L), ("Gemini 3.1 Flash-Lite", G), ("DeepSeek Flash", DS)):
    r2 = sum((M[i]["relevance"] == "investment_related") == (gold[i]["relevance"] == "investment_related") for i in clear)
    r3 = sum(M[i]["relevance"] == gold[i]["relevance"] for i in gid)
    unc_hit = sum(1 for i in gid if gold[i]["relevance"] == "unclear" and M[i]["relevance"] == "unclear")
    dm = sum(net_llm(M[i]) == net_gold(gold[i]) for i in gid); opp = sum({net_llm(M[i]), net_gold(gold[i])} == {"bullish", "bearish"} for i in gid)
    print(f"{name:22} trafność binarna {pct(r2,len(clear))} | 3-klasowa {pct(r3,len(gid))} | Twoje 'nierozstrzygalne' trafione {unc_hit}/19 | dominujący kierunek {pct(dm,len(gid))} | sprzeczny {opp}")
