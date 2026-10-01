"""Pilot 6: Jev (TypeSafe, model decyzyjny) przez OpenRouter na 143 postach #gielda z 28.09."""
import json, os, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

S = os.path.dirname(os.path.abspath(__file__))
KEY = os.environ["OPENROUTER_API_KEY"]
MODEL = "typesafe/jev-1.13"
D = json.load(open(f"{S}/pilot2-results.json"))
posts = D["kept"]
def parsed(r):
    if r.get("output") is not None: return r["output"]
    try: return json.loads(r["raw"]) if r.get("raw") else None
    except Exception: return None
gem = {r["id"]: parsed(r) for r in D["results"] if r["model"] == "gemini-3.1-flash-lite" and r["variant"] == "short"}

DIRS = {
    "bullish": "Autor ocenia cel pozytywnie, oczekuje wzrostu, kupuje albo liczy na wzrost (także przez sugestię lub pytanie retoryczne z nadzieją).",
    "bearish": "Autor ocenia cel negatywnie, oczekuje spadku albo sprzedaje (także przez sugestię, np. „bańka”).",
    "neutral": "Autor jawnie deklaruje neutralność wobec celu.",
    "mixed": "Autor wyraża oba kierunki wobec tego samego celu (np. krótki i długi termin).",
    "not_expressed": "Cel jest wspomniany, ale autor nie wyraża stanowiska (sama informacja, szczere pytanie).",
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
        "has_stance": {"type": "noul",
            "instructions": "Czy autor wyraża własne stanowisko inwestycyjne (pozytywne lub negatywne oczekiwanie albo ocenę) wobec konkretnej spółki, aktywa, sektora lub rynku?",
            "criteria": {"true": "Rozsądny czytelnik odczyta z tekstu, że autor jest nastawiony byczo lub niedźwiedzio wobec czegoś konkretnego — także z sugestii.",
                         "false": "Sama informacja, szczere pytanie, żart bez kierunku albo treść nieinwestycyjna."}},
        "dominant": {"type": "choice",
            "instructions": "Jaki jest dominujący kierunek stanowiska autora w tym wpisie?",
            "criteria": {"bullish": "Przeważa oczekiwanie wzrostu lub pozytywna ocena.",
                         "bearish": "Przeważa oczekiwanie spadku lub negatywna ocena.",
                         "none": "Autor nie wyraża kierunku (informacja, pytanie, treść nieinwestycyjna) albo kierunki się równoważą lub są nierozstrzygalne."}},
    }
    o = gem.get(pid)
    for i, t in enumerate((o or {}).get("targets", [])[:8]):
        q[f"t{i}"] = {"type": "choice",
            "instructions": f"Jakie jest stanowisko autora wpisu wobec celu „{t['anchor']['quote']}”?", "criteria": DIRS}
    return q

def call(post):
    it = post["input"]["item"]
    body = {"model": MODEL, "state": {"title": it["title"], "body": it["body"]}, "questions": questions(post["id"])}
    req = urllib.request.Request("https://openrouter.ai/api/alpha/decisions", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json", "Authorization": "Bearer " + KEY})
    t0 = time.time()
    for attempt in range(4):
        try:
            r = json.load(urllib.request.urlopen(req, timeout=60))
            return {"id": post["id"], "answers": r.get("answers"), "usage": r.get("usage"), "model": r.get("model"), "sec": round(time.time() - t0, 2)}
        except urllib.error.HTTPError as e:
            err = e.read().decode()[:400]
            if e.code in (429, 500, 502, 503): time.sleep(3 * (attempt + 1)); continue
            return {"id": post["id"], "error": f"{e.code} {err}", "sec": round(time.time() - t0, 2)}
        except Exception as e:
            if attempt < 3: time.sleep(3 * (attempt + 1)); continue
            return {"id": post["id"], "error": repr(e), "sec": round(time.time() - t0, 2)}
    return {"id": post["id"], "error": "retries exhausted", "sec": round(time.time() - t0, 2)}

if __name__ == "__main__":
    run = sys.argv[1] if len(sys.argv) > 1 else "run1"
    subset = posts if run == "run1" else posts[:40]
    first = call(subset[0]); print("pierwsze wywołanie:", json.dumps(first, ensure_ascii=False)[:700], flush=True)
    if "error" in first: sys.exit(1)
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=8) as ex: res = list(ex.map(call, subset))
    json.dump(res, open(f"{S}/pilot6-jev-{run}.json", "w"), ensure_ascii=False)
    errs = [r for r in res if "error" in r]
    cost = sum((r.get("usage") or {}).get("cost") or 0 for r in res); tok = sum((r.get("usage") or {}).get("input_tokens") or 0 for r in res)
    print(f"done {len(res)} postów, błędy {len(errs)}, {time.time()-t0:.0f}s, tokeny wej. {tok}, koszt ${cost:.5f}")
    if errs: print("przykładowy błąd:", errs[0]["error"][:300])
