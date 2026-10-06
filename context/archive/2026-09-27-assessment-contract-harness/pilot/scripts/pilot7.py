"""Pilot 7: Span-01 / Span-01 Lite (Respan) przez OpenRouter na 143 postach #gielda z 28.09 — tylko pytania noul."""
import json, os, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor
S = os.path.dirname(os.path.abspath(__file__))     # skrypty i prompty
PILOT = os.path.dirname(S)                          # próbki i etykiety
RUNS = os.path.join(PILOT, "runs")                  # wyniki przebiegów
KEY = os.environ["OPENROUTER_API_KEY"]
posts = json.load(open(os.path.join(RUNS, "pilot2-results.json")))["kept"]
Q = {
 "investment": "Wpis ma treść inwestycyjną: dotyczy rynku, spółki, aktywa, sektora lub makro w kontekście ceny, wyceny, transakcji, portfela lub wyników — także krótko, slangiem lub żartem, jeśli wiadomo, o czym mowa.",
 "not_investment": "Wpis nie ma treści rynkowej: rozmowa o forum lub tagu, żale do użytkowników, pozdrowienia, żart bez konkretnego aktywa i bez kierunku, narzekanie na brokera lub aplikację.",
 "unclear": "Z samego tekstu nie da się ustalić, czy i o co chodzi: treść jest w obrazku, sam link bez komentarza albo zbyt ogólne westchnienie.",
 "has_stance": "Autor wyraża własne stanowisko inwestycyjne (pozytywne lub negatywne oczekiwanie albo ocenę) wobec konkretnej spółki, aktywa, sektora lub rynku — także przez sugestię.",
 "bullish": "Autor wyraża pozytywne oczekiwanie lub ocenę (wzrost, kupno, nadzieja na wzrost) wobec konkretnej spółki, aktywa, sektora lub rynku.",
 "bearish": "Autor wyraża negatywne oczekiwanie lub ocenę (spadek, sprzedaż, bańka) wobec konkretnej spółki, aktywa, sektora lub rynku.",
 "joke": "Wpis jest żartem, memem lub ironią.",
}
def call(model, post):
    it = post["input"]["item"]; text = ((it["title"] + "\n") if it["title"] else "") + (it["body"] or "")
    body = {"model": model, "state": text, "questions": {k: {"type": "noul", "instructions": v} for k, v in Q.items()}}
    req = urllib.request.Request("https://openrouter.ai/api/alpha/decisions", data=json.dumps(body).encode(), headers={"Content-Type": "application/json", "Authorization": "Bearer " + KEY})
    t0 = time.time()
    for a in range(4):
        try:
            r = json.load(urllib.request.urlopen(req, timeout=60))
            return {"id": post["id"], "answers": {k: v["noul"] for k, v in r["answers"].items()}, "usage": r.get("usage"), "model": r.get("model"), "sec": round(time.time() - t0, 2)}
        except urllib.error.HTTPError as e:
            err = e.read().decode()[:300]
            if e.code in (429, 500, 502, 503): time.sleep(3 * (a + 1)); continue
            return {"id": post["id"], "error": f"{e.code} {err}"}
        except Exception as e:
            if a < 3: time.sleep(3 * (a + 1)); continue
            return {"id": post["id"], "error": repr(e)}
    return {"id": post["id"], "error": "retries exhausted"}
for model, name in (("respan/span-01", "span01"), ("respan/span-01-lite", "span01lite")):
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=8) as ex: res = list(ex.map(lambda p: call(model, p), posts))
    json.dump(res, open(os.path.join(RUNS, f"pilot7-{name}.json"), "w"), ensure_ascii=False)
    errs = [r for r in res if "error" in r]
    print(f"{model}: {len(res)} postów, błędy {len(errs)}, {time.time()-t0:.0f}s, koszt ${sum((r.get('usage') or {}).get('cost') or 0 for r in res):.5f}, tokeny {sum((r.get('usage') or {}).get('input_tokens') or 0 for r in res)}", (errs[0]["error"][:200] if errs else ""))
