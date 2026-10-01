"""Porównanie wyników modeli (pilot 2 i 3) z ręcznym wzorcem użytkownika."""
import json, os, re, sys, collections
PILOT = "/Users/krzysztofmacioszek/Develop/Projects/10xDevs/szczur-radar/context/changes/assessment-contract-harness/pilot"
LABELS = sys.argv[1] if len(sys.argv) > 1 else "labels-2026-09-28.json"
gold_raw = json.load(open(f"{PILOT}/{LABELS}"))["labels"]
sets = {s["post_id"]: s for s in json.load(open(f"{PILOT}/labeling-set-2026-09-28.json"))}
gold = {g["post_id"]: g for g in gold_raw if g.get("relevance") and not g.get("hard")}
print("wzorzec:", LABELS, "| oznaczone:", sum(1 for g in gold_raw if g.get("relevance")), "| trudne (pominięte w porównaniu):", sum(1 for g in gold_raw if g.get("hard")))

def norm(a): return re.sub(r"[^\w]", "", (a or "").lower().lstrip("#"))
def stance_targets(targets, get_anchor, get_dir):
    return {norm(get_anchor(t)): get_dir(t) for t in targets if get_dir(t) != "not_expressed" and norm(get_anchor(t))}
def gold_core(g): return g["relevance"], stance_targets(g["targets"], lambda t: t["anchor"], lambda t: t["direction"])
def model_core(o): return o["relevance"], stance_targets(o["targets"], lambda t: t["anchor"]["quote"], lambda t: t["stance"]["direction"])
def match(a, keys):
    return next((k for k in keys if a in k or k in a), None)

def score(outputs):
    """outputs: {post_id: AssessmentOutputV1|None} -> metryki względem wzorca"""
    n = rel_ok = core_ok = 0; tp = fp = fn = dir_ok = dir_n = missing = 0
    for pid, g in gold.items():
        n += 1; o = outputs.get(pid)
        if not o: missing += 1; fn += len(gold_core(g)[1]); continue
        gr, gt = gold_core(g); mr, mt = model_core(o)
        rel_ok += gr == mr
        matched = {}
        for a, d in gt.items():
            k = match(a, mt)
            if k: matched[a] = k; tp += 1; dir_n += 1; dir_ok += mt[k] == d
            else: fn += 1
        fp += sum(1 for k in mt if k not in matched.values())
        core_ok += gr == mr and len(matched) == len(gt) == len(mt) and all(mt[matched[a]] == d for a, d in gt.items())
    prec = tp / max(tp + fp, 1); rec = tp / max(tp + fn, 1)
    return dict(n=n, missing=missing, rel=rel_ok / n, core=core_ok / n, prec=prec, rec=rec, dir=dir_ok / max(dir_n, 1))

def parsed(r):
    if r.get("output") is not None: return r["output"]
    try: return json.loads(r["raw"]) if r.get("raw") else None
    except Exception: return None

rows = []
p2 = json.load(open(f"{PILOT}/runs/pilot2-results.json"))["results"]
for m in ["deepseek-flash", "gemini-3.1-flash-lite", "gemini-3.5-flash-lite"]:
    for v in ["long", "short"]:
        rows.append((f"{m} · pojedynczo · prompt {'pełny' if v == 'long' else 'skrócony'}",
                     score({r["id"]: parsed(r) for r in p2 if r["model"] == m and r["variant"] == v})))
if os.path.exists(f"{PILOT}/runs/pilot4-results.json"):
    p4 = json.load(open(f"{PILOT}/runs/pilot4-results.json"))
    for m in ["deepseek-flash", "gemini-3.1-flash-lite", "gemini-3.5-flash-lite"]:
        rows.append((f"{m} · pojedynczo · skrócony + reguły v0.2", score({r["id"]: r["output"] for r in p4 if r["model"] == m})))
p3 = json.load(open(f"{PILOT}/runs/pilot3-results.json"))
for m in ["gemini-3.1-flash-lite", "deepseek-flash"]:
    for v in ["chrono_5", "random_5", "chrono_10", "random_10", "chrono_15", "random_15", "hourly"]:
        rows.append((f"{m} · paczki {v} · skrócony", score({r["id"]: r["output"] for r in p3 if r["model"] == m and r["variant"] == v})))

print(f"wzorzec: {len(gold)} postów (pominięte bez etykiety: {len(gold_raw) - len(gold)})")
gt_all = [gold_core(g) for g in gold.values()]
print("rozkład trafności we wzorcu:", dict(collections.Counter(r for r, _ in gt_all)),
      "| celów ze stanowiskiem:", sum(len(t) for _, t in gt_all),
      "| kierunki:", dict(collections.Counter(d for _, t in gt_all for d in t.values())))
print(f"\n{'wariant':62} {'brak':>4} {'trafność':>8} {'rdzeń':>6} {'cele P':>7} {'cele R':>7} {'kierunek':>8}")
for name, s in rows:
    print(f"{name:62} {s['missing']:4} {100*s['rel']:7.0f}% {100*s['core']:5.0f}% {100*s['prec']:6.0f}% {100*s['rec']:6.0f}% {100*s['dir']:7.0f}%")

print("\n== notatki użytkownika ==")
for g in sorted(gold_raw, key=lambda g: g["n"]):
    if g.get("note") or g.get("hard"):
        s = sets[g["post_id"]]; body = ((s["title"] or "") + " " + (s["body"] or "")).replace("\n", " ")[:120]
        print(f"#{g['n']}{' [trudny]' if g.get('hard') else ''}: {g.get('note')!r}\n    post: {body}")
