"""Ramowanie 2: łagodniejsze miary zgodności, koncentracja wartości, zgodność zbiorcza (bez nowych wywołań modeli)."""
import json, re, collections, os
PILOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
def norm(a): return re.sub(r"[^\w]", "", a.lower().lstrip("#$"))
def stance_t(o): return {norm(t["anchor"]["quote"]): t["stance"]["direction"] for t in o["targets"] if t["stance"]["direction"] != "not_expressed" and norm(t["anchor"]["quote"])}
def match(a, keys): return next((k for k in keys if a in k or k in a), None)
def parsed(r):
    if r.get("output") is not None: return r["output"]
    try: return json.loads(r["raw"]) if r.get("raw") else None
    except Exception: return None
def load_wykop(model):
    D = json.load(open(f"{PILOT}/runs/pilot2-results.json"))
    return {r["id"]: parsed(r) for r in D["results"] if r["model"] == model and r["variant"] == "short" and parsed(r)}
def load_reddit(f):
    D = json.load(open(f"{PILOT}/runs/{f}")); return {r["id"]: r["output"] for r in D["results"] if r["output"]}
SUB = {"investment_thesis", "trade_action"}
def substantive(o): return any(c["kind"] in SUB and c["attribution"] == "author" for c in o["claims"])
def has_stance(o): return bool(stance_t(o))
def net(o):
    """dominujący kierunek posta: bullish / bearish / brak"""
    d = collections.Counter(v for v in stance_t(o).values() if v in ("bullish", "bearish"))
    if not d: return "brak"
    return "bullish" if d["bullish"] > d["bearish"] else ("bearish" if d["bearish"] > d["bullish"] else "remis")
def report(name, A, B):
    ids = [i for i in A if i in B]; n = len(ids)
    print(f"\n===== {name} (n={n}) =====")
    # 4. łagodniejsze miary
    rel = sum(A[i]["relevance"] == B[i]["relevance"] for i in ids)
    hs = sum(has_stance(A[i]) == has_stance(B[i]) for i in ids)
    nt = sum(net(A[i]) == net(B[i]) for i in ids)
    both = [i for i in ids if has_stance(A[i]) and has_stance(B[i])]
    overlap = sum(1 for i in both if any(match(a, stance_t(B[i])) for a in stance_t(A[i])))
    same_dir_any = sum(1 for i in both if any((m := match(a, stance_t(B[i]))) and stance_t(B[i])[m] == d for a, d in stance_t(A[i]).items()))
    opp = sum(1 for i in ids if {net(A[i]), net(B[i])} == {"bullish", "bearish"})
    print(f"[miary łagodne] trafność {100*rel/n:.0f}% | 'czy post wyraża stanowisko' {100*hs/n:.0f}% | dominujący kierunek posta {100*nt/n:.0f}% | sprzeczny dominujący kierunek: {opp}")
    print(f"   oba modele widzą stanowisko: {len(both)} postów; wspólny cel w {100*overlap/max(len(both),1):.0f}%; wspólny cel z tym samym kierunkiem w {100*same_dir_any/max(len(both),1):.0f}%")
    # 5. koncentracja wartości
    sa = [i for i in ids if substantive(A[i])]; sb = [i for i in ids if substantive(B[i])]
    sboth = [i for i in ids if substantive(A[i]) and substantive(B[i])]; seither = [i for i in ids if substantive(A[i]) or substantive(B[i])]
    print(f"[koncentracja] posty z tezą/transakcją autora: model A {100*len(sa)/n:.0f}%, model B {100*len(sb)/n:.0f}%, oba {100*len(sboth)/n:.0f}%, którykolwiek {100*len(seither)/n:.0f}%")
    for label, sub in (("treściwe wg obu", sboth), ("pozostałe", [i for i in ids if i not in sboth])):
        if sub: print(f"   {label:16} n={len(sub):3} dominujący kierunek zgodny {100*sum(net(A[i])==net(B[i]) for i in sub)/len(sub):.0f}%")
    # 3. poziom zbiorczy
    def agg(X):
        c = collections.defaultdict(collections.Counter)
        for i in ids:
            for t in X[i]["targets"]: c[norm(t["anchor"]["quote"])][t["stance"]["direction"]] += 1
        return c
    ga, gb = agg(A), agg(B)
    top = lambda g: [k for k, _ in sorted(g.items(), key=lambda kv: -sum(kv[1].values()))[:10]]
    ta, tb = top(ga), top(gb)
    print(f"[zbiorczo] top-10 celów: wspólne {len(set(ta) & set(tb))}/10")
    print("   A:", ", ".join(f"{k}({sum(ga[k].values())})" for k in ta))
    print("   B:", ", ".join(f"{k}({sum(gb[k].values())})" for k in tb))
    tot = lambda X: collections.Counter(net(X[i]) for i in ids)
    a, b = tot(A), tot(B)
    print(f"   bilans dnia (posty): A bullish {a['bullish']} / bearish {a['bearish']} | B bullish {b['bullish']} / bearish {b['bearish']}")
    agree_sign = 0; common = [k for k in set(ga) & set(gb) if sum(ga[k].values()) >= 3 and sum(gb[k].values()) >= 3]
    for k in common:
        s = lambda g: (g[k]["bullish"] > g[k]["bearish"]) - (g[k]["bullish"] < g[k]["bearish"])
        agree_sign += s(ga) == s(gb)
    print(f"   cele z ≥3 wzmiankami w obu modelach: {len(common)}; ten sam znak bilansu (byczy/niedźwiedzi/remis): {agree_sign}/{len(common)}")
report("WYKOP #gielda 28.09 · A=gemini-3.1-flash-lite, B=deepseek-flash (skrócony prompt)", load_wykop("gemini-3.1-flash-lite"), load_wykop("deepseek-flash"))
report("REDDIT 24–29.09 · A=gemini-3.1-flash-lite, B=deepseek-flash (prompt EN)", load_reddit("pilot5-reddit-gemini-3.1-flash-lite-en.json"), load_reddit("pilot5-reddit-deepseek-flash-en.json"))
