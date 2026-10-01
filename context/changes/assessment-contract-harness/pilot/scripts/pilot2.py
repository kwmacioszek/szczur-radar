"""Pilot 2: wszystkie posty #gielda z 28.09 × tanie modele × prompt pełny/skrócony."""
import json, os, re, sys, time, urllib.error
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

S = os.path.dirname(os.path.abspath(__file__))
sys.argv = ["pilot.py", "none"]
exec(open(f"{S}/pilot.py").read().split("items = []")[0])  # SCHEMA, SYSTEM (pełny), validate, call, call_deepseek

PROMPTS = {"long": SYSTEM, "short": open(f"{S}/prompt-short.md").read()}
MODELS = ["deepseek-flash", "gemini-3.1-flash-lite", "gemini-3.5-flash-lite"]

posts = json.load(open(f"{S}/gielda-2026-09-28.json"))
strip = lambda t: re.sub(r"#\w+|@\w+|https?://\S+", "", t or "").strip()
kept, skipped = [], 0
for p in posts:
    if p["resource"] == "entry":
        title, body = None, p.get("content")
    else:
        title, body = p.get("title"), p.get("description")
    if len(strip(title) + strip(body)) < 3:
        skipped += 1
        continue
    ts = datetime.strptime(p["created_at"], "%Y-%m-%d %H:%M:%S").strftime("%Y-%m-%dT%H:%M:%S+02:00")
    kept.append({"id": f"{p['resource']}-{p['id']}",
                 "input": {"schema_version": "AssessmentInputV1",
                           "item": {"item_type": "post" if p["resource"] == "entry" else "link", "published_at": ts,
                                    "channels": ["gielda"], "title": title or None, "body": body or None},
                           "context": {"parent_post": None, "parent_comment": None}}})
print(f"posts={len(posts)} kept={len(kept)} skipped(no text)={skipped}", flush=True)

def run_one(model, variant, post):
    global SYSTEM
    for attempt in range(4):
        raw, usage, dt = call_with(model, variant, post["input"])
        err = usage.get("error", "") if isinstance(usage, dict) else ""
        if raw is None and err and any(c in err for c in ('"code": 429', '"code": 503', '"code": 500', "rate", "overloaded", "UNAVAILABLE")):
            time.sleep(3 * (attempt + 1)); continue
        break
    rec = {"model": model, "variant": variant, "id": post["id"], "usage": usage, "sec": round(dt, 1), "raw": raw}
    if raw is None:
        rec["status"] = "provider_error" if "error" in usage else "invalid_structured_output"
    else:
        try:
            out = json.loads(raw); rec["output"] = out
            vio = validate(post["input"], out); rec["violations"] = vio
            rec["status"] = "ok" if not vio else vio[0][0]
        except Exception as e:
            rec["status"] = "invalid_structured_output"; rec["error"] = repr(e)
    return rec

_sys_cache = {}
def call_with(model, variant, inp):
    # call()/call_deepseek() czytają globalny SYSTEM — podmieniamy go per wywołanie w kopii funkcji
    g = dict(globals()); g["SYSTEM"] = PROMPTS[variant]
    fn = _sys_cache.get(model.startswith("deepseek"))
    f = call_deepseek if model.startswith("deepseek") else call
    import types
    f2 = types.FunctionType(f.__code__, g, f.__name__, f.__defaults__, f.__closure__)
    if not model.startswith("deepseek"):
        g["call_deepseek"] = types.FunctionType(call_deepseek.__code__, g, "call_deepseek")
    try:
        return f2(model, inp)
    except Exception as e:
        return None, {"error": f"exception {e!r}"}, 0.0

jobs = [(m, v, p) for v in PROMPTS for m in MODELS for p in kept]
results = []
t0 = time.time()
with ThreadPoolExecutor(max_workers=12) as ex:
    for i, rec in enumerate(ex.map(lambda j: run_one(*j), jobs), 1):
        results.append(rec)
        if i % 50 == 0: print(f"{i}/{len(jobs)} {time.time()-t0:.0f}s", flush=True)
json.dump({"kept": kept, "skipped": skipped, "results": results}, open(f"{S}/pilot2-results.json", "w"), ensure_ascii=False)
print("done", len(results), f"{time.time()-t0:.0f}s")
