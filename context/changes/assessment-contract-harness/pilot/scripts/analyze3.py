import json, os, re, collections, statistics as st
S = os.path.dirname(os.path.abspath(__file__))
exec(open(f"{S}/analyze2.py").read().split("by = {")[0])  # norm, core, same_core, kept, PRICE, validate via pilot? (validate z pilot.py)
import sys; sys.argv = ["pilot.py", "none"]
exec(open(f"{S}/pilot.py").read().split("items = []")[0])

R3 = json.load(open(f"{S}/pilot3-results.json"))
p2 = json.load(open(f"{S}/pilot2-results.json"))["results"]
def parsed(r):
    if r.get("output") is not None: return r["output"]
    try: return json.loads(r["raw"]) if r.get("raw") else None
    except Exception: return None
base = {(r["model"], r["id"]): parsed(r) for r in p2 if r["variant"] == "short"}
PRICE = {"deepseek-flash": (0.30, 0.006, 1.20), "gemini-3.1-flash-lite": (0.10, 0.10, 0.40)}

def texts(pid):
    it = kept[pid]["input"]["item"]; return [t for t in (it["title"], it["body"]) if t]
def quotes(o):
    qs = [t["anchor"]["quote"] for t in o.get("targets", [])]
    qs += [e["quote"] for c in o.get("claims", []) for e in c.get("evidence", [])]
    return qs

order = ["single_rerun", "chrono_5", "random_5", "chrono_10", "random_10", "chrono_15", "random_15", "hourly"]
print(f"{'model':22} {'wariant':13} {'brak':>5} {'ok':>6} {'przeciek':>8} {'trafn.=':>7} {'rdzeń=':>7} {'tok in/out na post':>20} {'$/mies 12k':>10} {'czas/wywoł.':>11}")
for m in ["gemini-3.1-flash-lite", "deepseek-flash"]:
    for v in order:
        rs = [r for r in R3 if r["model"] == m and r["variant"] == v]
        if not rs: continue
        n = len(rs); missing = ok = leak = rel = cor = comparable = 0
        for r in rs:
            o = r["output"]; pid = r["id"]
            if not o: missing += 1; continue
            try:
                vio = validate(kept[pid]["input"], o)
            except Exception:
                vio = [("schema", 0, "", "")]
            ok += not vio
            if r.get("batch"):
                own = texts(pid); others = [t for q in r["batch"] if q != pid for t in texts(q)]
                if any(not any(q in t for t in own) and any(q in t for t in others) for q in quotes(o)): leak += 1
            b = base.get((m, pid))
            if b:
                comparable += 1
                rel += o.get("relevance") == b.get("relevance")
                try: cor += same_core(core(o), core(b))
                except Exception: pass
        # koszt: usage per wywołanie / liczba postów w wywołaniu
        calls = {}
        for r in rs:
            key = tuple(r.get("batch") or [r["id"]]); calls[key] = r["usage_share"]
        u = [x for x in calls.values() if "error" not in x]
        tin = sum(x.get("in") or 0 for x in u) / n; tout = sum(x.get("out") or 0 for x in u) / n; tc = sum(x.get("cache") or 0 for x in u) / n
        pm, ph, po = PRICE[m]; cost = ((tin - tc) * pm + tc * ph + tout * po) / 1e6
        secs = st.median(r["sec"] for r in rs)
        errs = sum(1 for x in calls.values() if "error" in x)
        print(f"{m:22} {v:13} {missing:5} {100*ok/n:5.0f}% {leak:8} {100*rel/max(comparable,1):6.0f}% {100*cor/max(comparable,1):6.0f}% "
              f"{tin:9.0f}/{tout:<9.0f} {cost*12000:10.2f} {secs:9.1f}s" + (f"  błędy wywołań={errs}" if errs else ""))
    print()
