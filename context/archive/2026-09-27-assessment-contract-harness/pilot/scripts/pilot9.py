"""Pilot 9: Jev (TypeSafe) przez OpenRouter na 120 postach wzorca F-02 — kierunek wobec celów z etykiet i ironia.

`pilot9.py` — sam post; `pilot9.py day` (pilot 9b) — post oceniany na tle pozostałych postów wzorca z tego dnia."""
import json, os, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

S = os.path.dirname(os.path.abspath(__file__))     # skrypty i prompty
PILOT = os.path.dirname(S)                          # próbki i etykiety
RUNS = os.path.join(PILOT, "runs")                  # wyniki przebiegów
ROOT = os.path.abspath(os.path.join(PILOT, "../../../.."))
GOLD = os.path.join(ROOT, "szczur-radar-api/src/test/resources/fixtures/assessor/gold")
KEY = os.environ.get("OPENROUTER_API_KEY") or os.environ["LLM_API_KEY"]
MODEL = "typesafe/jev-1.13"
posts = json.load(open(os.path.join(GOLD, "posts.json")))
labels = {l["post_id"]: l for l in json.load(open(os.path.join(GOLD, "labels.json")))}

# Kierunki jak w kontrakcie V2 (§7), z regułami, na których LLM-y myliły się najczęściej (§7.1.2, §7.1.7).
DIRS = {
    "bullish": "Autor ocenia cel pozytywnie, oczekuje wzrostu, kupuje albo liczy na wzrost — także przez sugestię, pytanie retoryczne lub opis wzrostu ceny.",
    "bearish": "Autor ocenia cel negatywnie, oczekuje spadku albo sprzedaje — także przez sugestię, opis spadku ceny albo ironiczną pochwałę straty („brawo, kolejne -20%”).",
    "neutral": "Autor jawnie deklaruje neutralność wobec celu.",
    "mixed": "Autor wyraża oba kierunki wobec tego samego celu (np. krótki i długi termin).",
    "not_expressed": "Cel jest wspomniany, ale autor nie wyraża stanowiska (sama informacja, szczere pytanie bez opisu ruchu ceny).",
    "unclear": "Stanowisko jest, ale jego kierunku nie da się ustalić (np. scenariusz „albo w górę, albo w dół”, nierozstrzygalna ironia).",
}

def questions(pid):
    q = {
        "relevance": {"type": "choice",
            "instructions": "Czy ten wpis z polskiego forum giełdowego (tag #gielda) ma treść inwestycyjną?",
            "criteria": {
                "investment_related": "Autor mówi o rynku, spółce, aktywie, sektorze lub makro w kontekście ceny, wyceny, transakcji, portfela lub wyników — także krótko, slangiem, żartem lub wulgarnie, jeśli wiadomo, o czym mowa.",
                "not_investment_related": "Brak treści rynkowej: rozmowa o forum lub tagu, żale do użytkowników, pozdrowienia, żart bez konkretnego aktywa i bez kierunku, narzekania na brokera lub aplikację.",
                "unclear": "Z samego tekstu nie da się ustalić, czy i o co chodzi: treść jest w obrazku, sam link bez komentarza, zbyt ogólne westchnienie."}},
        "dominant": {"type": "choice",
            "instructions": "Jaki jest dominujący kierunek stanowiska autora w tym wpisie?",
            "criteria": {"bullish": "Przeważa oczekiwanie wzrostu lub pozytywna ocena.",
                         "bearish": "Przeważa oczekiwanie spadku lub negatywna ocena.",
                         "none": "Autor nie wyraża kierunku (informacja, pytanie, treść nieinwestycyjna) albo kierunki się równoważą lub są nierozstrzygalne."}},
        "ironic": {"type": "noul",
            "instructions": "Czy wpis jest ironiczny lub sarkastyczny — autor mówi coś innego (zwykle odwrotnie) niż ma na myśli albo kpi?",
            "criteria": {"true": "Dosłowne znaczenie jest odwrotne do zamierzonego albo autor drwi (np. „no pięknie, kolejne -20%, idealna inwestycja”).",
                         "false": "Wpis należy czytać dosłownie, także gdy jest wulgarny, emocjonalny albo żartobliwy bez odwrócenia znaczenia."}},
    }
    for i, t in enumerate(labels[pid]["targets"][:8]):
        q[f"t{i}"] = {"type": "choice",
            "instructions": f"Jakie jest stanowisko autora wpisu wobec celu „{t['anchor']}”?", "criteria": DIRS}
    return q

DAY = len(sys.argv) > 1 and sys.argv[1] == "day"
CONTEXT_CHARS = 1500   # przycięcie pojedynczego posta w kontekście dnia

def state(post):
    it = post["input"]["item"]
    if not DAY: return {"title": it["title"], "body": it["body"]}
    others = sorted((p for p in posts if p["day"] == post["day"] and p["post_id"] != post["post_id"]),
                    key=lambda p: p["input"]["item"]["published_at"])
    return {"assessed_post": {"published_at": it["published_at"], "title": it["title"], "body": it["body"]},
            "note": "Pytania dotyczą wyłącznie assessed_post. same_day_posts to inne wpisy z tagu #gielda z tego dnia — kontekst nastrojów, żartów i wydarzeń, do których autor może nawiązywać.",
            "same_day_posts": [{"published_at": p["input"]["item"]["published_at"],
                                "body": (p["input"]["item"]["body"] or "")[:CONTEXT_CHARS]} for p in others]}

def call(post):
    body = {"model": MODEL, "state": state(post), "questions": questions(post["post_id"])}
    req = urllib.request.Request("https://openrouter.ai/api/alpha/decisions", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json", "Authorization": "Bearer " + KEY})
    t0 = time.time()
    for attempt in range(4):
        try:
            r = json.load(urllib.request.urlopen(req, timeout=60))
            return {"id": post["post_id"], "answers": r.get("answers"), "usage": r.get("usage"), "model": r.get("model"), "sec": round(time.time() - t0, 2)}
        except urllib.error.HTTPError as e:
            err = e.read().decode()[:400]
            if e.code in (429, 500, 502, 503): time.sleep(3 * (attempt + 1)); continue
            return {"id": post["post_id"], "error": f"{e.code} {err}", "sec": round(time.time() - t0, 2)}
        except Exception as e:
            if attempt < 3: time.sleep(3 * (attempt + 1)); continue
            return {"id": post["post_id"], "error": repr(e), "sec": round(time.time() - t0, 2)}
    return {"id": post["post_id"], "error": "retries exhausted", "sec": round(time.time() - t0, 2)}

if __name__ == "__main__":
    out = os.path.join(RUNS, "pilot9-jev-gold-day.json" if DAY else "pilot9-jev-gold.json")
    first = call(posts[0])
    # Tylko status — odpowiedzi nie są wypisywane, żeby nie sugerować etykiet ironii przed ich oznaczeniem.
    print("pierwsze wywołanie:", "błąd " + first["error"][:300] if "error" in first else f"ok ({first.get('model')})", flush=True)
    if "error" in first: sys.exit(1)
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=8) as ex: res = list(ex.map(call, posts))
    json.dump(res, open(out, "w"), ensure_ascii=False)
    errs = [r for r in res if "error" in r]
    cost = sum((r.get("usage") or {}).get("cost") or 0 for r in res); tok = sum((r.get("usage") or {}).get("input_tokens") or 0 for r in res)
    print(f"done {len(res)} postów, błędy {len(errs)}, {time.time()-t0:.0f}s, tokeny wej. {tok}, koszt ${cost:.5f}")
    if errs: print("przykładowy błąd:", errs[0]["error"][:300])
