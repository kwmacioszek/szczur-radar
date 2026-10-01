import json, os, re, collections, statistics as st
S = os.path.dirname(os.path.abspath(__file__))     # skrypty i prompty
PILOT = os.path.dirname(S)                          # próbki i etykiety
RUNS = os.path.join(PILOT, "runs")                  # wyniki przebiegów
D = json.load(open(os.path.join(RUNS, "pilot2-results.json")))
def parsed(r):
    if r.get("output") is not None: return r["output"]
    try: return json.loads(r["raw"]) if r.get("raw") else None
    except Exception: return None
def model(m): return {r["id"]: parsed(r) for r in D["results"] if r["model"] == m and r["variant"] == "short" and parsed(r)}
G, DS = model("gemini-3.1-flash-lite"), model("deepseek-flash")
J = {r["id"]: r["answers"] for r in json.load(open(os.path.join(RUNS, "pilot6-jev-run1.json"))) if r.get("answers")}
J2 = {r["id"]: r["answers"] for r in json.load(open(os.path.join(RUNS, "pilot6-jev-run2.json"))) if r.get("answers")}
gold = {g["post_id"]: g for g in json.load(open(os.path.join(PILOT, "labels-2026-09-28.json")))["labels"] if g.get("relevance")}
def norm(a): return re.sub(r"[^\w]", "", a.lower().lstrip("#$"))
def net_llm(o):
    d = collections.Counter(t["stance"]["direction"] for t in o["targets"] if t["stance"]["direction"] in ("bullish", "bearish"))
    return "brak" if not d else ("bullish" if d["bullish"] > d["bearish"] else "bearish" if d["bearish"] > d["bullish"] else "remis")
def net_gold(g):
    d = collections.Counter(t["direction"] for t in g["targets"] if t["direction"] in ("bullish", "bearish"))
    return "brak" if not d else ("bullish" if d["bullish"] > d["bearish"] else "bearish" if d["bearish"] > d["bullish"] else "remis")
def net_jev(a): return "brak" if a["dominant"]["choice"] == "none" else a["dominant"]["choice"]
ids = [i for i in J if i in G and i in DS]
pct = lambda a, b: f"{100*a/max(b,1):.0f}%"
print(f"postów: {len(ids)}; Jev rozkład trafności: {dict(collections.Counter(J[i]['relevance']['choice'] for i in ids))}; dominujący: {dict(collections.Counter(net_jev(J[i]) for i in ids))}")
print("\n== A. Jev vs modele (n=%d) ==" % len(ids))
for name, M in (("gemini-3.1-flash-lite", G), ("deepseek-flash", DS)):
    rel = sum(J[i]["relevance"]["choice"] == M[i]["relevance"] for i in ids); dom = sum(net_jev(J[i]) == net_llm(M[i]) for i in ids)
    opp = sum({net_jev(J[i]), net_llm(M[i])} == {"bullish", "bearish"} for i in ids)
    print(f"Jev vs {name:22} trafność {pct(rel,len(ids))} | dominujący kierunek {pct(dom,len(ids))} | sprzeczny {opp}")
print(f"(dla porównania gemini vs deepseek: trafność {pct(sum(G[i]['relevance']==DS[i]['relevance'] for i in ids),len(ids))} | dominujący kierunek {pct(sum(net_llm(G[i])==net_llm(DS[i]) for i in ids),len(ids))})")
print("\n== B. względem Twoich etykiet z rundy 1 (n=%d; oznaczane ostrożniej i bez instrukcji) ==" % len([i for i in gold if i in J]))
gid = [i for i in gold if i in ids]; clear = [i for i in gid if gold[i]["relevance"] != "unclear"]
for name, rel_f, net_f in (("Jev", lambda i: J[i]["relevance"]["choice"], lambda i: net_jev(J[i])),
                           ("gemini-3.1-flash-lite", lambda i: G[i]["relevance"], lambda i: net_llm(G[i])),
                           ("deepseek-flash", lambda i: DS[i]["relevance"], lambda i: net_llm(DS[i]))):
    r3 = sum(rel_f(i) == gold[i]["relevance"] for i in gid)
    r2 = sum((rel_f(i) == "investment_related") == (gold[i]["relevance"] == "investment_related") for i in clear)
    dm = sum(net_f(i) == net_gold(gold[i]) for i in gid); opp = sum({net_f(i), net_gold(gold[i])} == {"bullish", "bearish"} for i in gid)
    unc = sum(1 for i in gid if gold[i]["relevance"] == "unclear" and rel_f(i) == "unclear")
    print(f"{name:22} trafność 3-klasowa {pct(r3,len(gid))} | binarna (bez Twoich nierozstrzygalnych, n={len(clear)}) {pct(r2,len(clear))} | Twoje 'nierozstrzygalne' trafione {unc}/19 | dominujący kierunek {pct(dm,len(gid))} | sprzeczny {opp}")
print("\n== C. czy confidence Jev wskazuje posty sporne? ==")
def split(conf_f, bad_f, label, pool):
    xs = sorted(pool, key=conf_f); n = len(xs); bad = [i for i in xs if bad_f(i)]
    low = xs[: n // 4]; mid = xs[n // 4: n // 2]; high = xs[n // 2:]
    rate = lambda g: pct(sum(bad_f(i) for i in g), len(g))
    print(f"{label}: spornych {len(bad)}/{n} | najniższe 25% confidence: {rate(low)} spornych | kolejne 25%: {rate(mid)} | górne 50%: {rate(high)} | najniższe 25% łapie {pct(sum(bad_f(i) for i in low), len(bad))} wszystkich spornych")
split(lambda i: J[i]["relevance"]["confidence"], lambda i: G[i]["relevance"] != DS[i]["relevance"], "trafność: gemini≠deepseek", ids)
split(lambda i: J[i]["dominant"]["confidence"], lambda i: net_llm(G[i]) != net_llm(DS[i]), "kierunek: gemini≠deepseek", ids)
split(lambda i: J[i]["relevance"]["confidence"], lambda i: gold[i]["relevance"] == "unclear" or gold[i].get("hard"), "trafność: Twoje 'nierozstrzygalne' lub 'trudne'", gid)
split(lambda i: J[i]["relevance"]["confidence"], lambda i: (J[i]["relevance"]["choice"] == "investment_related") != (gold[i]["relevance"] == "investment_related"), "trafność: Jev myli się wzgl. Ciebie (binarnie, bez nierozstrz.)", clear)
split(lambda i: J[i]["dominant"]["confidence"], lambda i: net_jev(J[i]) != net_gold(gold[i]), "kierunek: Jev ≠ Twoja etykieta", gid)
print(f"mediana confidence: trafność {st.median(J[i]['relevance']['confidence'] for i in ids):.2f}, kierunek {st.median(J[i]['dominant']['confidence'] for i in ids):.2f}")
print("\n== D. kierunek wobec celu wskazanego przez Gemini ==")
n = same = opp = 0; ds_n = ds_same = 0; confs_same, confs_diff = [], []
for i in ids:
    for k, t in enumerate(G[i]["targets"][:8]):
        a = J[i].get(f"t{k}")
        if not a: continue
        n += 1; gd = t["stance"]["direction"]; ok = a["choice"] == gd; same += ok
        (confs_same if ok else confs_diff).append(a["confidence"])
        opp += {a["choice"], gd} == {"bullish", "bearish"}
        x = norm(t["anchor"]["quote"]); m = [u for u in DS[i]["targets"] if x and (x in norm(u["anchor"]["quote"]) or norm(u["anchor"]["quote"]) in x)]
        if m: ds_n += 1; ds_same += m[0]["stance"]["direction"] == a["choice"]
print(f"cele: {n}; Jev = Gemini: {pct(same,n)}; sprzeczne (bullish↔bearish): {opp}; Jev = DeepSeek na wspólnych celach: {pct(ds_same,ds_n)} (n={ds_n})")
print(f"mediana confidence gdy zgodne {st.median(confs_same):.2f}, gdy niezgodne {st.median(confs_diff):.2f}")
print("\n== E. powtarzalność (40 postów powtórzonych) ==")
r = [i for i in J2 if i in J]
print(f"ta sama trafność {pct(sum(J[i]['relevance']['choice']==J2[i]['relevance']['choice'] for i in r),len(r))} | ten sam dominujący kierunek {pct(sum(J[i]['dominant']['choice']==J2[i]['dominant']['choice'] for i in r),len(r))} | średnia różnica has_stance {st.mean(abs(J[i]['has_stance']['noul']-J2[i]['has_stance']['noul']) for i in r):.3f}")
res = json.load(open(os.path.join(RUNS, "pilot6-jev-run1.json")))
print(f"\n== F. koszt i czas == tokeny wej./post {st.mean(x['usage']['input_tokens'] for x in res):.0f}; koszt/post ${st.mean(x['usage']['cost'] for x in res):.6f}; przy 12k postów/mies. ${12000*st.mean(x['usage']['cost'] for x in res):.2f}; mediana czasu {st.median(x['sec'] for x in res):.2f}s")
