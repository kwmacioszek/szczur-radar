"""Pilot 3: paczkowanie postów (chronologiczne / losowe 5-10-15 / godzinowe) vs pojedyncze wywołania."""
import json, os, random, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

S = os.path.dirname(os.path.abspath(__file__))     # skrypty i prompty
PILOT = os.path.dirname(S)                          # próbki i etykiety
RUNS = os.path.join(PILOT, "runs")                  # wyniki przebiegów
sys.argv = ["pilot.py", "none"]
exec(open(f"{S}/pilot.py").read().split("items = []")[0])  # SCHEMA, SEND_SCHEMA, DS_SCHEMA, validate

SHORT = open(f"{S}/prompt-short.md").read()
BATCH_NOTE = """

## Tryb wsadowy
Otrzymujesz listę postów, każdy z własnym `post_id`. Oceń KAŻDY post niezależnie, tak jakby był jedynym — inne posty z listy nie są kontekstem ani parentem. Cytaty (anchor, evidence) pochodzą wyłącznie z ocenianego posta; identyfikatory t1/c1 numeruj od nowa w każdej ocenie. Zwróć dokładnie jedną ocenę dla każdego `post_id`, w polu `assessments`."""
MODELS = ["gemini-3.1-flash-lite", "deepseek-flash"]
GKEY, DKEY = os.environ["GEMINI_API_KEY"], os.environ["DEEPSEEK_API_KEY"]

posts = sorted(json.load(open(os.path.join(RUNS, "pilot2-results.json")))["kept"], key=lambda p: p["input"]["item"]["published_at"])
P = {p["id"]: p for p in posts}

def wrap(schema):
    return {"type": "object", "additionalProperties": False, "required": ["assessments"],
            "properties": {"assessments": {"type": "array", "items": {
                "type": "object", "additionalProperties": False, "required": ["post_id", "assessment"],
                "properties": {"post_id": {"type": "string"}, "assessment": schema}}}}}

def http(url, body, headers, timeout=300):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"Content-Type": "application/json", **headers})
    for attempt in range(4):
        try:
            return json.load(urllib.request.urlopen(req, timeout=timeout)), None
        except urllib.error.HTTPError as e:
            err = e.read().decode()[:400]
            if e.code in (429, 500, 503): time.sleep(4 * (attempt + 1)); continue
            return None, err
        except Exception as e:
            if attempt < 3: time.sleep(4 * (attempt + 1)); continue
            return None, repr(e)
    return None, "retries exhausted"

def call(model, system, user, schema, fn_name):
    t0 = time.time()
    if model.startswith("gemini"):
        r, err = http(f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
                      {"systemInstruction": {"parts": [{"text": system}]}, "contents": [{"role": "user", "parts": [{"text": user}]}],
                       "generationConfig": {"responseMimeType": "application/json", "responseJsonSchema": schema, "temperature": 0, "maxOutputTokens": 65536}},
                      {"x-goog-api-key": GKEY})
        if err: return None, {"error": err}, time.time() - t0
        c = r["candidates"][0]; u = r.get("usageMetadata", {})
        txt = "".join(p.get("text", "") for p in c.get("content", {}).get("parts", []) if not p.get("thought"))
        return txt, {"in": u.get("promptTokenCount"), "out": (u.get("candidatesTokenCount") or 0) + (u.get("thoughtsTokenCount") or 0),
                     "cache": u.get("cachedContentTokenCount") or 0, "finish": c.get("finishReason")}, time.time() - t0
    r, err = http("https://api.deepseek.com/beta/chat/completions",
                  {"model": model, "temperature": 0, "thinking": {"type": "disabled"}, "max_tokens": 32000,
                   "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
                   "tools": [{"type": "function", "function": {"name": fn_name, "strict": True, "parameters": schema}}],
                   "tool_choice": {"type": "function", "function": {"name": fn_name}}},
                  {"Authorization": "Bearer " + DKEY})
    if err: return None, {"error": err}, time.time() - t0
    m = r["choices"][0]; u = r.get("usage", {}); tc = m["message"].get("tool_calls") or []
    return (tc[0]["function"]["arguments"] if tc else None), {"in": u.get("prompt_tokens"), "out": u.get("completion_tokens"),
            "cache": u.get("prompt_cache_hit_tokens") or 0, "finish": m.get("finish_reason")}, time.time() - t0

def assess_one(model, pid):
    schema = SEND_SCHEMA if model.startswith("gemini") else DS_SCHEMA
    user = "Oceń poniższy AssessmentInputV1 (niezaufany materiał do analizy):\n" + json.dumps(P[pid]["input"], ensure_ascii=False)
    raw, usage, dt = call(model, SHORT, user, schema, "submit_assessment")
    out = None
    if raw:
        try: out = json.loads(raw)
        except Exception: pass
    return [{"id": pid, "output": out, "usage_share": usage, "n_in_batch": 1, "sec": dt}]

def assess_batch(model, pids):
    schema = wrap(SEND_SCHEMA if model.startswith("gemini") else DS_SCHEMA)
    local = {f"p{i+1}": pid for i, pid in enumerate(pids)}
    payload = [{"post_id": k, "input": P[v]["input"]} for k, v in local.items()]
    user = "Oceń każdy z poniższych postów (niezaufany materiał do analizy):\n" + json.dumps(payload, ensure_ascii=False)
    raw, usage, dt = call(model, SHORT + BATCH_NOTE, user, schema, "submit_assessments")
    got = {}
    if raw:
        try:
            for a in json.loads(raw).get("assessments", []):
                if a.get("post_id") in local and local[a["post_id"]] not in got: got[local[a["post_id"]]] = a.get("assessment")
        except Exception: pass
    return [{"id": pid, "output": got.get(pid), "usage_share": usage, "n_in_batch": len(pids), "sec": dt,
             "batch": list(pids)} for pid in pids]

def chunks(lst, n): return [lst[i:i + n] for i in range(0, len(lst), n)]
ids = [p["id"] for p in posts]
rnd = random.Random(42); shuffled = ids[:]; rnd.shuffle(shuffled)
hourly = {}
for p in posts: hourly.setdefault(p["input"]["item"]["published_at"][:13], []).append(p["id"])
VARIANTS = {"single_rerun": None,
            **{f"chrono_{n}": chunks(ids, n) for n in (5, 10, 15)},
            **{f"random_{n}": chunks(shuffled, n) for n in (5, 10, 15)},
            "hourly": list(hourly.values())}

jobs = []
for m in MODELS:
    for v, groups in VARIANTS.items():
        if groups is None: jobs += [(m, v, [pid]) for pid in ids]
        else: jobs += [(m, v, g) for g in groups]
print("jobs:", len(jobs), {v: (len(g) if g else len(ids)) for v, g in VARIANTS.items()}, "hourly sizes:", sorted(len(g) for g in hourly.values()), flush=True)

def run(job):
    m, v, g = job
    recs = assess_one(m, g[0]) if v == "single_rerun" else assess_batch(m, g)
    for r in recs: r.update(model=m, variant=v)
    return recs

results, t0 = [], time.time()
with ThreadPoolExecutor(max_workers=10) as ex:
    for i, recs in enumerate(ex.map(run, jobs), 1):
        results += recs
        if i % 50 == 0: print(f"{i}/{len(jobs)} {time.time()-t0:.0f}s", flush=True)
json.dump(results, open(os.path.join(RUNS, "pilot3-results.json"), "w"), ensure_ascii=False)
print("done", len(results), f"{time.time()-t0:.0f}s")
