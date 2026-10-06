"""Pilot 9: Jev względem wzorca F-02 (kierunek wobec celów z etykiet, trafność, dominujący kierunek) i ironia.

Ironia: wariant (a) — etykiety użytkownika `pilot/irony-labels-gold.json` (z `labeling-irony.html`); bez nich
wyniki ironii nie są wypisywane, żeby nie sugerować etykiet. Wariant (b) — `--proxy`: posty, w których
nagrane LLM odwracają kierunek z wzorca.
"""
import json, os, sys

S = os.path.dirname(os.path.abspath(__file__))
PILOT = os.path.dirname(S)
ROOT = os.path.abspath(os.path.join(PILOT, "../../../.."))
FIX = os.path.join(ROOT, "szczur-radar-api/src/test/resources/fixtures")
labels = {l["post_id"]: l for l in json.load(open(os.path.join(FIX, "assessor/gold/labels.json")))}
# --day: pilot 9b (post na tle pozostałych postów dnia)
RUN = "runs/pilot9-jev-gold-day.json" if "--day" in sys.argv else "runs/pilot9-jev-gold.json"
J = {r["id"]: r["answers"] for r in json.load(open(os.path.join(PILOT, RUN))) if r.get("answers")}
IRONY = os.path.join(PILOT, "irony-labels-gold.json")
LLMS = ["luna", "luna-low", "deepseek-flash", "gemini-flash-lite"]
OPP = {"bullish": "bearish", "bearish": "bullish"}
NONE = {"unclear", "not_expressed"}

def pct(a, b): return f"{100 * a / b:.1f}% ({a}/{b})" if b else "—"
def same(e, a): return e == a or (e in NONE and a in NONE)
def dominant(dirs):
    b, s = dirs.count("bullish"), dirs.count("bearish")
    return "bullish" if b > s else "bearish" if s > b else None

def llm_targets(model, pid):
    """Kierunki LLM wobec celów wzorca (dopasowanie kotwic przez zawieranie, bez aliasów — przybliżenie)."""
    f = os.path.join(FIX, "llm/recordings", model, pid + ".json")
    if not os.path.exists(f): return None
    try: out = json.loads(json.load(open(f))["rawText"])
    except Exception: return None
    got = [(t["anchor"]["quote"].lower(), t["stance"]["direction"]) for t in out.get("targets", [])]
    res = []
    for g in labels[pid]["targets"]:
        a = g["anchor"].lower()
        res.append(next((d for q, d in got if a in q or q in a), None))
    return res

def jev_targets(pid):
    a = J[pid]
    return [a[f"t{i}"]["choice"] if f"t{i}" in a else None for i in range(len(labels[pid]["targets"]))]

def score(get):
    """Kierunek wobec celów wzorca (niedopasowany = zły), sprzeczności, dominujący kierunek (jak GoldMetrics)."""
    dr = dt = con = pol = domr = domt = 0
    for pid, l in labels.items():
        if l["hard"] or pid not in J: continue
        got = get(pid)
        if got is None: got = [None] * len(l["targets"])
        exp = [t["direction"] for t in l["targets"]]
        for e, a in zip(exp, got):
            if e != "not_expressed":
                dt += 1; dr += same(e, a)
            if e in OPP:
                pol += 1; con += a == OPP[e]
        d = dominant(exp)
        if d:
            domt += 1; domr += d == dominant([a for a in got if a])
    return dr, dt, con, pol, domr, domt

print("Kierunek wobec celów wzorca (Jev dostaje kotwice z etykiet; LLM — własne cele, dopasowane przybliżenie):")
for name, get in [("jev", jev_targets)] + [(m, lambda pid, m=m: llm_targets(m, pid)) for m in LLMS]:
    dr, dt, con, pol, domr, domt = score(get)
    print(f"  {name:18} kierunek {pct(dr, dt):18} sprzeczne {pct(con, pol):16} dominujący {pct(domr, domt)}")

rr = rt = dr2 = dt2 = 0
for pid, l in labels.items():
    if l["hard"] or pid not in J: continue
    if l["relevance"] != "unclear":
        rt += 1; rr += J[pid]["relevance"]["choice"] == l["relevance"]
    d = dominant([t["direction"] for t in l["targets"]])
    if d:
        dt2 += 1; dr2 += J[pid]["dominant"]["choice"] == d
print(f"Jev: trafność {pct(rr, rt)}, pytanie o dominujący kierunek posta {pct(dr2, dt2)}")
over = [pid for pid, l in labels.items() if len(l["targets"]) > 8]
if over: print(f"uwaga: {len(over)} postów ma >8 celów, Jev pytany o pierwsze 8")

def auc(pos, neg):
    if not pos or not neg: return None
    return sum((p > n) + 0.5 * (p == n) for p in pos for n in neg) / (len(pos) * len(neg))

def irony_report(title, truth):
    """truth: post_id → True/False (posty spoza słownika pominięte)."""
    ids = [p for p in truth if p in J]
    pos = [J[p]["ironic"]["noul"] for p in ids if truth[p]]
    neg = [J[p]["ironic"]["noul"] for p in ids if not truth[p]]
    print(f"\n{title}: {len(pos)} tak, {len(neg)} nie")
    a = auc(pos, neg)
    print(f"  AUC wyniku ironii Jev: {a:.2f}" if a is not None else "  AUC: brak obu klas")
    for th in (0.3, 0.5, 0.7):
        tp = sum(s >= th for s in pos); fp = sum(s >= th for s in neg)
        print(f"  próg {th}: oznaczone {tp + fp}, trafione {tp}/{len(pos)} ironicznych, fałszywe {fp}/{len(neg)}")

if os.path.exists(IRONY):
    il = {r["post_id"]: r["ironic"] for r in json.load(open(IRONY)) if r.get("ironic") in ("yes", "no")}
    irony_report("Ironia (a) względem etykiet użytkownika", {p: v == "yes" for p, v in il.items()})
else:
    print("\nIronia (a): brak pilot/irony-labels-gold.json — wyniki ironii ukryte do czasu oznaczenia.")

if "--proxy" in sys.argv:
    flipped = {}
    for pid, l in labels.items():
        if not any(t["direction"] in OPP for t in l["targets"]): continue
        flipped[pid] = False
        for m in LLMS:
            got = llm_targets(m, pid) or []
            if any(a == OPP.get(t["direction"]) for t, a in zip(l["targets"], got)): flipped[pid] = True
    irony_report("Ironia (b) zastępczo: posty, w których któryś LLM odwraca kierunek wzorca", flipped)
