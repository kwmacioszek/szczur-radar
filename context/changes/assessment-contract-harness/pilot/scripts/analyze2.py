import json, os, re, collections, itertools
S = os.path.dirname(os.path.abspath(__file__))     # skrypty i prompty
PILOT = os.path.dirname(S)                          # próbki i etykiety
RUNS = os.path.join(PILOT, "runs")                  # wyniki przebiegów
D = json.load(open(os.path.join(RUNS, "pilot2-results.json")))
R = D["results"]; kept = {p["id"]: p for p in D["kept"]}
MODELS = ["deepseek-flash", "gemini-3.1-flash-lite", "gemini-3.5-flash-lite"]
# $/1M: (input cache-miss, input cache-hit, output) — ceny z niezweryfikowanego streszczenia cenników
PRICE = {"deepseek-flash": (0.30, 0.006, 1.20), "gemini-3.5-flash-lite": (0.30, 0.30, 2.50), "gemini-3.1-flash-lite": (0.10, 0.10, 0.40)}

def norm(a): return re.sub(r"[^\w]", "", a.lower().lstrip("#"))
def core(o):
    """rdzeń MVP: trafność + {anchor znormalizowany -> kierunek} dla celów z wyrażonym stanowiskiem"""
    if o is None: return None
    t = {norm(x["anchor"]["quote"]): x["stance"]["direction"] for x in o["targets"]
         if x["stance"]["direction"] not in ("not_expressed",)}
    return (o["relevance"], t)
def same_core(a, b):
    if a is None or b is None: return False
    if a[0] != b[0]: return False
    ka, kb = a[1], b[1]
    # dopasowanie anchorów po zawieraniu (np. 'makdolana' vs 'akcjemakdolana')
    for x, d in ka.items():
        m = [y for y in kb if x in y or y in x]
        if not m or kb[m[0]] != d: return False
    for y in kb:
        if not any(x in y or y in x for x in ka): return False
    return True
def uncertain(o):
    if o is None: return True
    if o["relevance"] == "unclear": return True
    if set(o.get("flags", [])) & {"possible_sarcasm", "missing_context"}: return True
    return any(t["stance"]["direction"] in ("unclear", "mixed") or t["reference"] in ("ambiguous", "unresolved") for t in o["targets"])

by = {(r["model"], r["variant"], r["id"]): r for r in R}
print(f"posty ocenione: {len(kept)} (pominięte bez tekstu: {D['skipped']})\n")
print("== zgodność z kontraktem, tokeny, koszt ==")
for v in ("long", "short"):
    for m in MODELS:
        rs = [by[(m, v, i)] for i in kept]
        st = collections.Counter(r["status"] for r in rs)
        u = [r["usage"] for r in rs if isinstance(r["usage"], dict) and "error" not in r["usage"]]
        inp = sum(x.get("promptTokenCount") or 0 for x in u) / max(len(u), 1)
        out = sum((x.get("candidatesTokenCount") or 0) + (x.get("thoughtsTokenCount") or 0) for x in u) / max(len(u), 1)
        hit = sum(x.get("cacheHit") or 0 for x in u) / max(len(u), 1)
        pm, ph, po = PRICE[m]
        cost = ((inp - hit) * pm + hit * ph + out * po) / 1e6
        other = {k: c for k, c in st.items() if k != "ok"}
        print(f"{v:5} {m:22} ok={st['ok']:3}/{len(rs)} ({100*st['ok']/len(rs):.0f}%) in={inp:5.0f} (cache {hit:4.0f}) out={out:4.0f} "
              f"$/post={cost:.5f} $/mies(12k)={cost*12000:6.1f} {other}")
print()
print("== zgodność rdzenia (trafność + cele ze stanowiskiem) między modelami, tylko odpowiedzi sparsowane ==")
def parsed(r):
    if "output" in r: return r["output"]
    try: return json.loads(r["raw"]) if r.get("raw") else None
    except Exception: return None
for v in ("long", "short"):
    for a, b in itertools.combinations(MODELS, 2):
        pairs = [(parsed(by[(a, v, i)]), parsed(by[(b, v, i)])) for i in kept]
        pairs = [(x, y) for x, y in pairs if x and y]
        rel = sum(x["relevance"] == y["relevance"] for x, y in pairs)
        cr = sum(same_core(core(x), core(y)) for x, y in pairs)
        print(f"{v:5} {a:22} vs {b:22} n={len(pairs)} relevance={100*rel/len(pairs):.0f}% rdzeń={100*cr/len(pairs):.0f}%")
    allthree = [[parsed(by[(m, v, i)]) for m in MODELS] for i in kept]
    ok3 = [x for x in allthree if all(x)]
    agree3 = sum(same_core(core(x[0]), core(x[1])) and same_core(core(x[0]), core(x[2])) for x in ok3)
    print(f"{v:5} wszystkie 3 zgodne w rdzeniu: {agree3}/{len(ok3)} ({100*agree3/len(ok3):.0f}%)")
print()
print("== kaskada: tani model + eskalacja (symulacja) ==")
for v in ("long", "short"):
    for m in MODELS:
        others = [x for x in MODELS if x != m]
        esc_val = esc_unc = esc_dis = 0; esc_any = 0
        for i in kept:
            r = by[(m, v, i)]; o = parsed(r)
            e1 = r["status"] != "ok"
            e2 = uncertain(o)
            ref = parsed(by[(others[0], v, i)])
            e3 = not same_core(core(o), core(ref))
            esc_val += e1; esc_unc += e2; esc_dis += e3; esc_any += (e1 or e2 or e3)
        n = len(kept)
        print(f"{v:5} {m:22} eskalacja: walidacja={100*esc_val/n:.0f}% niepewność={100*esc_unc/n:.0f}% "
              f"niezgoda z {others[0]}={100*esc_dis/n:.0f}% | łącznie={100*esc_any/n:.0f}%")
