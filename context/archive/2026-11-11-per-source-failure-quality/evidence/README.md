# Dane z produkcji do progów S-35 (2026-11-11)

Jeden odczyt tylko do odczytu, wykonany przez agenta podczas `/10x-plan` za zgodą właściciela:

```sh
RAILWAY_PROJECT_ID=<project> railway ssh -s postgres -e production -- psql -U postgres -d railway --csv \
  -c "SET default_transaction_read_only = on" -c "<SQL>"
```

```sql
SELECT (a.assessed_at AT TIME ZONE 'Europe/Warsaw')::date AS day, p.source,
       count(*) AS attempts,
       count(*) FILTER (WHERE a.status = 'failed') AS failed,
       count(*) FILTER (WHERE a.failure_class = 'provider_error') AS pe,
       count(*) FILTER (WHERE a.failure_class = 'timeout') AS t,
       count(*) FILTER (WHERE a.failure_class = 'invalid_structured_output') AS iso,
       count(*) FILTER (WHERE a.failure_class = 'schema_violation') AS sv,
       count(*) FILTER (WHERE a.failure_class = 'invalid_reference') AS ir,
       count(*) FILTER (WHERE a.failure_class = 'fabricated_evidence') AS fe,
       count(*) FILTER (WHERE a.failure_class = 'domain_invariant_violation') AS div,
       count(DISTINCT a.run_id) AS runs
FROM assessments a JOIN posts p ON p.id = a.post_id
WHERE a.result_source = 'model' AND a.assessed_at > now() - interval '35 days'
GROUP BY 1, 2 ORDER BY 1, 2
```

Wynik: `prod-failures-2026-11-11.csv` (same liczby, bez treści postów). Mianownik: próby z wywołaniem modelu
(`result_source = 'model'`), ponowienia włącznie, prefiltr pominięty.

## Okno kroczące 7 dni (dni kalendarzowe Warszawy, `[D-6, D]`)

| Źródło | Stan ustalony | Klasy (udział prób w oknie) |
|---|---|---|
| Wykop (od 2026-10-16) | 2,0–2,8 % przy 1 076–3 258 próbach | `fabricated_evidence` 1,4–2,1 %, `schema_violation` 0,1–0,5 %, `domain_invariant_violation` 0,1–0,3 % |
| Reddit (od 2026-11-02) | 14,6–18,4 % przy 201–678 próbach | `schema_violation` 7,6–10,4 %, `fabricated_evidence` 5,4–8,6 %, `domain_invariant_violation` 0–1,7 % |

- Incydent 2026-10-09 (Wykop, `provider_error` 23 z 43 prób, „`choices` is not set”): okno 13.10 = 19,0 % (147 prób), 14.10 = 6,0 %, 15.10 = 4,7 %.
- Pojedyncza noc Wykopa przy małym wolumenie: 8.11 = 11,0 % (82 próby), 9.11 = 10,0 % (60), 25.10 = 9,5 % (63) — powód okna 7 dni.
- Reddit pierwsze okna: 1.11 = 10,2 % przy 88 próbach (poniżej minimum 100).

Progi planu (Wykop 5 %, Reddit 25 %, minimum 100 prób, alarm przy przejściu) dają na tych 35 dniach jeden alarm:
Wykop 2026-10-13 (okno 19,0 % przy 147 próbach; okno dzień wcześniej 46 prób, poniżej minimum).
