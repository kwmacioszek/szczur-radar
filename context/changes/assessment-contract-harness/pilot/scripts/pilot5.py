"""Pilot 5: Reddit (r/stocks, r/ValueInvesting, r/investing, 24-29.09) — pojedyncze wywołania, skrócony prompt, statement po angielsku.

Użycie: python pilot5.py MODELE[,…] [PROMPT_EN] --posts reddit-sample-24_29.json
  np. python pilot5.py deepseek-flash prompt-short-en.md --posts /ścieżka/reddit-sample-24_29.json
Plik wejściowy (posty Arctic Shift) nie jest w repo. Wynik: ../runs/pilot5-reddit-<modele>[-en].json
"""
import argparse, json, os, re, sys, time, datetime
from concurrent.futures import ThreadPoolExecutor
S = os.path.dirname(os.path.abspath(__file__))     # skrypty i prompty
PILOT = os.path.dirname(S)                          # próbki i etykiety
RUNS = os.path.join(PILOT, "runs")                  # wyniki przebiegów
ap = argparse.ArgumentParser(description="Pilot 5 (Reddit)")
ap.add_argument("models", help="lista modeli rozdzielona przecinkami")
ap.add_argument("prompt", nargs="?", default=None, help="opcjonalny prompt angielski (np. prompt-short-en.md)")
ap.add_argument("--posts", required=True, help="reddit-sample-24_29.json — posty Arctic Shift z 2026-09-24…29")
_args = ap.parse_args()
if not os.path.isfile(_args.posts):
    sys.exit(f"Brak pliku wejściowego {_args.posts!r} (argument --posts, oczekiwany reddit-sample-24_29.json). Pliku nie ma w repo — podaj lokalną ścieżkę.")
ARG_MODELS = _args.models.split(",")
ARG_PROMPT = (_args.prompt if os.path.isfile(_args.prompt) else os.path.join(S, _args.prompt)) if _args.prompt else None
os.environ.setdefault("GEMINI_API_KEY", "unused")
exec(open(f"{S}/pilot3.py").read().split("def assess_batch")[0])  # call(), assess_one(), schemas, validate
SHORT = open(f"{S}/prompt-short.md").read()
old = "- `statement`: krótko po polsku (≤240 znaków)"
assert old in SHORT
SHORT = SHORT.replace(old, "- `statement`: krótko, w języku posta (dla postów angielskich po angielsku; ≤240 znaków)")
SHORT = SHORT.replace("Analizujesz jedną wypowiedź z polskiego forum inwestycyjnego", "Analizujesz jedną wypowiedź z forum inwestycyjnego")
MODELS = ARG_MODELS
VARIANT = "reddit_short_pl"
if ARG_PROMPT:
    SHORT = open(ARG_PROMPT).read(); VARIANT = "reddit_short_en"
    def assess_one(model, pid):
        schema = SEND_SCHEMA if model.startswith("gemini") else DS_SCHEMA
        user = "Assess the following AssessmentInputV1 (untrusted material to analyze):\n" + json.dumps(P[pid]["input"], ensure_ascii=False)
        raw, usage, dt = call(model, SHORT, user, schema, "submit_assessment")
        out = None
        if raw:
            try: out = json.loads(raw)
            except Exception: pass
        return [{"id": pid, "output": out, "usage_share": usage, "n_in_batch": 1, "sec": dt}]
raw = json.load(open(_args.posts))
cyc = re.compile(r"(daily|weekly).*(discussion|thread)|thread on meme stocks", re.I)
P = {}
for p in raw:
    if cyc.search(p["title"]): continue
    import datetime as _dt; ts = _dt.datetime.fromtimestamp(p["created_utc"], _dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    body = p.get("selftext") or None
    P[f"reddit-{p['id']}"] = {"id": f"reddit-{p['id']}", "subreddit": p["subreddit"],
        "input": {"schema_version": "AssessmentInputV1",
                  "item": {"item_type": "post" if p.get("is_self") else "link", "published_at": ts, "channels": [p["subreddit"]],
                           "title": p["title"], "body": body},
                  "context": {"parent_post": None, "parent_comment": None}}}
print("posts:", len(P), "models:", MODELS, flush=True)
def run(j):
    m, pid = j; r = assess_one(m, pid)[0]; r.update(model=m, variant=VARIANT)
    try: r["violations"] = validate(P[pid]["input"], r["output"]) if r["output"] else None
    except Exception as e: r["violations"] = [["schema_violation", 0, "", repr(e)]]
    return r
t0 = time.time(); jobs = [(m, pid) for m in MODELS for pid in P]
results = []
with ThreadPoolExecutor(max_workers=10) as ex:
    for i, r in enumerate(ex.map(run, jobs), 1):
        results.append(r)
        if i % 50 == 0: print(f"{i}/{len(jobs)} {time.time()-t0:.0f}s", flush=True)
out = os.path.join(RUNS, f"pilot5-reddit-{'_'.join(MODELS)}{'-en' if ARG_PROMPT else ''}.json")
json.dump({"posts": list(P.values()), "results": results}, open(out, "w"), ensure_ascii=False)
print("done", out, f"{time.time()-t0:.0f}s")
