import json, os, collections, statistics as st
S = os.path.dirname(os.path.abspath(__file__))
exec(open(f"{S}/analyze6.py").read().split('ids = [i for i in J')[0])   # G, DS, J (Jev), gold, net_llm, net_gold, net_jev
def load(n): return {r["id"]: r["answers"] for r in json.load(open(f"{S}/pilot7-{n}.json")) if r.get("answers")}
A, L = load("span01"), load("span01lite")
ids = [i for i in A if i in G and i in DS and i in J]
pct = lambda a, b: f"{100*a/max(b,1):.0f}%"
diff = [abs(A[i][k] - L[i][k]) for i in ids for k in A[i]]
print(f"postów {len(ids)} | pełny vs Lite: średnia różnica prawdopodobieństw {st.mean(diff):.4f}, maks. {max(diff):.3f}")
def rel(a): return "investment_related" if a["investment"] >= 0.5 else "not_investment_related"
def net(a):
    b, s = a["bullish"], a["bearish"]
    if max(b, s) < 0.5: return "brak"
    return "bullish" if b > s else "bearish"
for name, X in (("Span-01", A),):
    print(f"\n== {name} ==  inwestycyjne {sum(rel(X[i])=='investment_related' for i in ids)}/{len(ids)}; dominujący {dict(collections.Counter(net(X[i]) for i in ids))}; 'unclear'>=0.5: {sum(X[i]['unclear']>=0.5 for i in ids)}; joke>=0.5: {sum(X[i]['joke']>=0.5 for i in ids)}")
    for mn, M in (("gemini-3.1-flash-lite", G), ("deepseek-flash", DS)):
        r = sum((rel(X[i]) == "investment_related") == (M[i]["relevance"] == "investment_related") for i in ids); d = sum(net(X[i]) == net_llm(M[i]) for i in ids)
        opp = sum({net(X[i]), net_llm(M[i])} == {"bullish", "bearish"} for i in ids)
        print(f"  vs {mn:22} trafność (binarnie) {pct(r,len(ids))} | dominujący kierunek {pct(d,len(ids))} | sprzeczny {opp}")
    d = sum(net(X[i]) == net_jev(J[i]) for i in ids); r = sum((rel(X[i]) == "investment_related") == (J[i]["relevance"]["choice"] == "investment_related") for i in ids)
    print(f"  vs Jev                    trafność (binarnie) {pct(r,len(ids))} | dominujący kierunek {pct(d,len(ids))}")
    gid = [i for i in gold if i in ids]; clear = [i for i in gid if gold[i]["relevance"] != "unclear"]
    r2 = sum((rel(X[i]) == "investment_related") == (gold[i]["relevance"] == "investment_related") for i in clear)
    dm = sum(net(X[i]) == net_gold(gold[i]) for i in gid); opp = sum({net(X[i]), net_gold(gold[i])} == {"bullish", "bearish"} for i in gid)
    print(f"  vs Twoje etykiety: trafność binarna (n={len(clear)}) {pct(r2,len(clear))} [Jev 82%, Gemini 82%, DeepSeek 73%] | dominujący kierunek {pct(dm,len(gid))} [Jev 54%, Gemini 56%, DeepSeek 74%] | sprzeczny {opp}")
    # sygnały niepewności
    def split(conf_f, bad_f, label, pool):
        xs = sorted(pool, key=conf_f); n = len(xs); bad = [i for i in xs if bad_f(i)]
        low, mid, high = xs[: n // 4], xs[n // 4: n // 2], xs[n // 2:]
        rate = lambda g: pct(sum(bad_f(i) for i in g), len(g))
        print(f"  {label}: spornych {len(bad)}/{n} | dolne 25%: {rate(low)} | kolejne 25%: {rate(mid)} | górne 50%: {rate(high)} | dolne 25% łapie {pct(sum(bad_f(i) for i in low), len(bad))}")
    conf_rel = lambda i: abs(X[i]["investment"] - 0.5)
    print("  -- pewność trafności = odległość 'investment' od 0,5 --")
    split(conf_rel, lambda i: G[i]["relevance"] != DS[i]["relevance"], "trafność: gemini≠deepseek", ids)
    split(conf_rel, lambda i: gold[i]["relevance"] == "unclear" or gold[i].get("hard"), "Twoje 'nierozstrzygalne'/'trudne'", gid)
    print("  -- pytanie 'unclear' (wysokie = niepewne) --")
    split(lambda i: -X[i]["unclear"], lambda i: G[i]["relevance"] != DS[i]["relevance"], "trafność: gemini≠deepseek", ids)
    split(lambda i: -X[i]["unclear"], lambda i: gold[i]["relevance"] == "unclear" or gold[i].get("hard"), "Twoje 'nierozstrzygalne'/'trudne'", gid)
    gu = [i for i in gid if gold[i]["relevance"] == "unclear"]
    print(f"  mediana 'unclear' dla Twoich nierozstrzygalnych {st.median(X[i]['unclear'] for i in gu):.2f} vs pozostałych {st.median(X[i]['unclear'] for i in gid if i not in gu):.2f}")
    print("  -- pewność kierunku = |bullish − bearish| --")
    split(lambda i: abs(X[i]["bullish"] - X[i]["bearish"]), lambda i: net_llm(G[i]) != net_llm(DS[i]), "kierunek: gemini≠deepseek", ids)
res = json.load(open(f"{S}/pilot7-span01.json"))
print(f"\nkoszt/post ${st.mean(x['usage']['cost'] for x in res):.7f}; przy 12k postów/mies. ${12000*st.mean(x['usage']['cost'] for x in res):.2f}; tokeny/post {st.mean(x['usage']['input_tokens'] for x in res):.0f}; mediana czasu {st.median(x['sec'] for x in res):.2f}s")
