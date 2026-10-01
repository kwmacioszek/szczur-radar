"""Pilot 4: prompt skrócony + reguły z instrukcji v0.2, 70 postów zestawu wzorcowego, pojedyncze wywołania."""
import json, os, sys, time
from concurrent.futures import ThreadPoolExecutor
S = os.path.dirname(os.path.abspath(__file__))
PILOT = "/Users/krzysztofmacioszek/Develop/Projects/10xDevs/szczur-radar/context/changes/assessment-contract-harness/pilot"
exec(open(f"{S}/pilot3.py").read().split("def assess_batch")[0])  # call(), assess_one(), P, SHORT, schemas, validate
SHORT = SHORT + open(f"{PILOT}/scripts/prompt-v2-rules.md").read()
ids = [s["post_id"] for s in json.load(open(f"{PILOT}/labeling-set-2026-09-28.json"))]
MODELS = ["gemini-3.1-flash-lite", "gemini-3.5-flash-lite", "deepseek-flash"]
jobs = [(m, pid) for m in MODELS for pid in ids]
def run(j):
    m, pid = j; r = assess_one(m, pid)[0]; r.update(model=m, variant="v2_rules")
    try: r["violations"] = validate(P[pid]["input"], r["output"]) if r["output"] else None
    except Exception as e: r["violations"] = [["schema_violation", 0, "", repr(e)]]
    return r
t0 = time.time()
with ThreadPoolExecutor(max_workers=10) as ex: results = list(ex.map(run, jobs))
json.dump(results, open(f"{S}/pilot4-results.json", "w"), ensure_ascii=False)
for m in MODELS:
    rs = [r for r in results if r["model"] == m]
    ok = sum(1 for r in rs if r["output"] and not r["violations"]); miss = sum(1 for r in rs if not r["output"])
    tin = sum((r["usage_share"].get("in") or 0) for r in rs) / len(rs); tout = sum((r["usage_share"].get("out") or 0) for r in rs) / len(rs)
    print(f"{m:22} ok={ok}/{len(rs)} brak={miss} in={tin:.0f} out={tout:.0f}")
print("done", f"{time.time()-t0:.0f}s")
