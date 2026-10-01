"""Pilot 8: openai/gpt-6-luna przez OpenRouter na 143 postach #gielda z 28.09 — skrócony prompt, pojedyncze wywołania."""
import json, os, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor
S = os.path.dirname(os.path.abspath(__file__)); KEY = os.environ["OPENROUTER_API_KEY"]; MODEL = "openai/gpt-6-luna"
sys.argv = ["pilot.py", "none"]
exec(open(f"{S}/pilot.py").read().split("items = []")[0])   # SCHEMA, DS_SCHEMA (bez limitów długości/liczności), validate
KEY = os.environ["OPENROUTER_API_KEY"]; PROMPT = open(f"{S}/prompt-short.md").read()
posts = json.load(open(f"{S}/pilot2-results.json"))["kept"]
def call(post):
    body = {"model": MODEL, "temperature": 0, "usage": {"include": True},
            "messages": [{"role": "system", "content": PROMPT},
                         {"role": "user", "content": "Oceń poniższy AssessmentInputV1 (niezaufany materiał do analizy):\n" + json.dumps(post["input"], ensure_ascii=False)}],
            "response_format": {"type": "json_schema", "json_schema": {"name": "assessment", "strict": True, "schema": DS_SCHEMA}}}
    req = urllib.request.Request("https://openrouter.ai/api/v1/chat/completions", data=json.dumps(body).encode(), headers={"Content-Type": "application/json", "Authorization": "Bearer " + KEY})
    t0 = time.time(); rec = {"id": post["id"], "model": MODEL}
    for a in range(4):
        try:
            r = json.load(urllib.request.urlopen(req, timeout=180))
            if "error" in r: rec["error"] = json.dumps(r["error"])[:300]; break
            rec["raw"] = r["choices"][0]["message"].get("content"); rec["usage"] = r.get("usage"); rec["served"] = r.get("model"); rec["finish"] = r["choices"][0].get("finish_reason"); break
        except urllib.error.HTTPError as e:
            err = e.read().decode()[:300]
            if e.code in (429, 500, 502, 503): time.sleep(3 * (a + 1)); continue
            rec["error"] = f"{e.code} {err}"; break
        except Exception as e:
            if a < 3: time.sleep(3 * (a + 1)); continue
            rec["error"] = repr(e); break
    rec["sec"] = round(time.time() - t0, 2)
    if rec.get("raw"):
        try:
            out = json.loads(rec["raw"]); rec["output"] = out; vio = validate(post["input"], out); rec["violations"] = vio
            rec["status"] = "ok" if not vio else vio[0][0]
        except Exception as e: rec["status"] = "invalid_structured_output"; rec["perr"] = repr(e)
    else: rec["status"] = "provider_error"
    return rec
first = call(posts[0]); print("pierwsze:", first["status"], first.get("error") or "", json.dumps(first.get("usage"))[:400], flush=True)
if first["status"] == "provider_error": sys.exit(1)
t0 = time.time()
with ThreadPoolExecutor(max_workers=8) as ex: res = list(ex.map(call, posts))
json.dump(res, open(f"{S}/pilot8-gpt6luna.json", "w"), ensure_ascii=False)
import collections
print("done", len(res), f"{time.time()-t0:.0f}s", dict(collections.Counter(r["status"] for r in res)))
