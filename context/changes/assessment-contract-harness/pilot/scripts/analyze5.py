import json, collections, re, statistics as st, sys, os
S = os.path.dirname(os.path.abspath(__file__))     # skrypty i prompty
PILOT = os.path.dirname(S)                          # próbki i etykiety
RUNS = os.path.join(PILOT, "runs")                  # wyniki przebiegów
def load(f): 
    D = json.load(open(f)); return D["results"], {p["id"]: p for p in D["posts"]}
def norm(a): return re.sub(r"[^\w]", "", a.lower().lstrip("#$"))
def core(o):
    return (o["relevance"], {norm(t["anchor"]["quote"]): t["stance"]["direction"] for t in o["targets"] if t["stance"]["direction"] != "not_expressed"})
def same_core(a, b):
    if a[0] != b[0]: return False
    for x, d in a[1].items():
        m = [y for y in b[1] if x in y or y in x]
        if not m or b[1][m[0]] != d: return False
    return all(any(x in y or y in x for x in a[1]) for y in b[1])
def unc(o): return o["relevance"] == "unclear" or bool(set(o["flags"]) & {"possible_sarcasm", "missing_context"}) or any(t["stance"]["direction"] in ("unclear", "mixed") or t["reference"] in ("ambiguous", "unresolved") for t in o["targets"])
def summarize(name, f, price):
    R, P = load(f); n = len(R); outs = [r for r in R if r["output"]]
    ok = sum(1 for r in outs if not r["violations"])
    cls = collections.Counter(v[0] if v[0] != "schema_violation" else "limit: " + v[3] for r in outs for v in (r["violations"] or []))
    posts_cls = collections.Counter(r["violations"][0][0] for r in outs if r["violations"])
    fab = collections.Counter()
    for r in outs:
        it = P[r["id"]]["input"]["item"]; o = r["output"]
        for q in [t["anchor"] for t in o["targets"]] + [e for c in o["claims"] for e in c["evidence"]]:
            txt = it.get(q["field"]) or ""
            if q["quote"] and q["quote"] in txt: continue
            other = it.get("title" if q["field"] == "body" else "body") or ""
            nm = lambda s: re.sub(r"\s+", " ", s.replace("*", "").replace("\\", "")).strip().lower()
            fab["zły field" if q["quote"] in other else ("tylko markdown/spacje/wielkość liter" if nm(q["quote"]) in nm(txt) or nm(q["quote"]) in nm(other) else "inna treść")] += 1
    pl = sum(1 for r in outs for c in r["output"]["claims"] if re.search(r"[ąćęłńóśźż]| jest | się | że ", c["statement"].lower()))
    nclaims = sum(len(r["output"]["claims"]) for r in outs)
    u = [r["usage_share"] for r in R if "error" not in r["usage_share"]]
    tin = st.mean(x["in"] for x in u); tout = st.mean(x["out"] for x in u); tc = st.mean(x.get("cache") or 0 for x in u)
    cost = ((tin - tc) * price[0] + tc * price[1] + tout * price[2]) / 1e6
    tg = [t for r in outs for t in r["output"]["targets"]]
    print(f"== {name} ==")
    print(f"zgodne z kontraktem: {ok}/{n} ({100*ok/n:.0f}%) | brak odpowiedzi: {n-len(outs)} | pierwsza przyczyna odrzucenia: {dict(posts_cls)}")
    print(f"naruszenia: {dict(cls)}")
    print(f"niezgodne cytaty: {dict(fab)}")
    print(f"statement po polsku: {pl}/{nclaims} ({100*pl/max(nclaims,1):.0f}%)")
    print(f"cele: {len(tg)} (mediana {st.median(len(r['output']['targets']) for r in outs)}/post), bez stanowiska {sum(1 for t in tg if t['stance']['direction']=='not_expressed')}; claimy mediana {st.median(len(r['output']['claims']) for r in outs)}")
    print(f"niepewność: {100*sum(unc(r['output']) for r in outs)/len(outs):.0f}% | trafność: {dict(collections.Counter(r['output']['relevance'] for r in outs))}")
    print(f"tokeny/post wej {tin:.0f} (cache {tc:.0f}) wyj {tout:.0f} | $/post {cost:.5f} | mediana czasu {st.median(r['sec'] for r in R):.1f}s\n")
    return {r["id"]: r["output"] for r in outs}
def agree(a, b, label):
    ids = [i for i in a if i in b]
    rel = sum(a[i]["relevance"] == b[i]["relevance"] for i in ids); c = sum(same_core(core(a[i]), core(b[i])) for i in ids)
    print(f"{label}: n={len(ids)} trafność {100*rel/len(ids):.0f}% rdzeń {100*c/len(ids):.0f}%")
if __name__ == "__main__":
    DS = (0.30, 0.006, 1.20); G31 = (0.10, 0.10, 0.40)
    runs = {}
    for name, f, price in [("deepseek-flash · prompt PL", "pilot5-reddit-deepseek-flash.json", DS), ("deepseek-flash · prompt EN", "pilot5-reddit-deepseek-flash-en.json", DS),
                           ("gemini-3.1-flash-lite · prompt EN", "pilot5-reddit-gemini-3.1-flash-lite-en.json", G31)]:
        if os.path.exists(os.path.join(RUNS, f)): runs[name] = summarize(name, os.path.join(RUNS, f), price)
    names = list(runs)
    for i in range(len(names)):
        for j in range(i + 1, len(names)): agree(runs[names[i]], runs[names[j]], f"{names[i]} vs {names[j]}")
