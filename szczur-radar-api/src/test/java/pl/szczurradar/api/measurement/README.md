# measurement — live runs and recordings (test-only)

Code here calls real LLM APIs and never runs in CI. Keys come only from your shell; they never go
into the repo, CI or Railway. Keep each provider's settings in its own file outside the repo
(`chmod 600`), e.g. `~/.config/szczur-radar/<provider>.env`:

```bash
export LLM_API_PROVIDER=<gemini|deepseek|openrouter> LLM_MODEL=<model id> LLM_API_KEY=<key>
```

A run creates one `LlmClient` (`LlmClientFactory.create(...)`) and reuses it for every post: the
client's HTTP pools and threads are not closed (`llm/CLAUDE.md`).

Tests here are tagged `measurement`: the default `./mvnw test` / `verify` (and CI) skip them, and
the `measurement` profile runs only them — no Spring context, no Docker:

```bash
( source ~/.config/szczur-radar/<provider>.env && ./mvnw -Pmeasurement test -Dtest=<Class> )
```

## Live smoke (`LiveSmokeTest`, plan phase 4)

Assesses the first 30 assessable posts of
`context/archive/2026-09-27-assessment-contract-harness/pilot/wykop-gielda-sample-2026-09-29.json` through
`Assessor` (prefilter, one call, parser, D1–D15) with the provider from `LLM_*`, and prints each
result and a summary: inputs read, prefilter rejections, model calls (the denominator), contract
compliance, failure classes, schema rules broken (`schema:additionalProperties`,
`schema:uniqueItems` close checks N1/N2 of `implementation-notes.md`), the model ids reported,
tokens (with cache), median time, retries and flex fallbacks. Nothing is written to the repo.
`-Dsmoke.sample=<path>` reads another sample of the same shape.

```bash
# one run per provider; the three runs of the plan:
#   gemini      LLM_MODEL=gemini-3.1-flash-lite
#   deepseek    LLM_MODEL=deepseek-v4-flash
#   openrouter  LLM_MODEL=openai/gpt-6-luna LLM_SERVICE_TIER=flex LLM_TIMEOUT=5m
( source ~/.config/szczur-radar/gemini.env && ./mvnw -Pmeasurement test -Dtest=LiveSmokeTest )
```

Pass mark per provider (plan, phase 4): compliance ≥ 90 % and at most one `provider_error` or
`invalid_structured_output`. Put the summaries (with the denominator; for Luna flex the retries,
fallbacks and median time) in the PR description.

## Recording provider payloads (`src/test/resources/fixtures/<provider>/`)

The client tests (`llm/*ClientTest`) replay one real success response per provider on a local HTTP
server. To re-record one, run from `szczur-radar-api/` (the request is example 1 of
`context/foundation/llm-assessment/examples.md`; the key is read from the env file and never
printed). Check the saved body for keys and personal data before committing.

```bash
# request body: system prompt + example 1 input + the V2 schema simplified per provider exactly as
# llm/transport/ProviderSchemas does ($ref inlined; Gemini without count limits; DeepSeek also without
# lengths; OpenRouter also without uniqueItems, which OpenAI strict mode rejects)
python3 - <<'EOF' > /tmp/llm-request.json
import json
schema = json.load(open("src/main/resources/assessor/assessment-output-v2.schema.json"))
def simplify(node, drop, props=False):
    if isinstance(node, dict):
        if not props and "$ref" in node:
            return simplify(schema["$defs"][node["$ref"].split("/")[-1]], drop)
        return {k: simplify(v, drop, not props and k == "properties") for k, v in node.items()
                if props or k not in drop | {"$defs", "$schema"}}
    return [simplify(v, drop) for v in node] if isinstance(node, list) else node
counts = {"minItems", "maxItems"}
post = {"schema_version": "AssessmentInputV1", "item": {"item_type": "post",
        "published_at": "2026-09-28T16:36:16+02:00", "channels": ["gielda"], "title": None,
        "body": "to koniec dla pamięciorzy \n#gielda "},
        "context": {"parent_post": None, "parent_comment": None}}
print(json.dumps({"system": "Oceń wpis według kontraktu. Zwróć wyłącznie JSON zgodny ze schematem.",
                  "user": json.dumps(post, ensure_ascii=False, separators=(",", ":")),
                  "gemini": simplify(schema, counts),
                  "deepseek": simplify(schema, counts | {"minLength", "maxLength"}),
                  "openrouter": simplify(schema, counts | {"minLength", "maxLength", "uniqueItems"})},
                 ensure_ascii=False))
EOF

# DeepSeek: JSON mode, thinking off, schema in the system prompt after the client's fixed paragraph
# (DeepSeekClient.SCHEMA_INSTRUCTION). Run it twice: the second answer reports prompt cache hits.
( source ~/.config/szczur-radar/deepseek.env; python3 - <<'EOF' |
import json, os; r = json.load(open("/tmp/llm-request.json"))
print(json.dumps({"model": os.environ["LLM_MODEL"], "temperature": 0,
  "response_format": {"type": "json_object"}, "thinking": {"type": "disabled"},
  "messages": [{"role": "system", "content": r["system"] + "\n\nReturn exactly one JSON object, with no other text, that conforms to this JSON Schema:\n" + json.dumps(r["deepseek"], ensure_ascii=False, separators=(",", ":"))},
               {"role": "user", "content": r["user"]}]}, ensure_ascii=False))
EOF
  curl -sS https://api.deepseek.com/chat/completions -H "Authorization: Bearer $LLM_API_KEY" \
    -H 'Content-Type: application/json' --data-binary @- ) > src/test/resources/fixtures/deepseek/chat-completion.json

# Gemini (Google AI Studio): generateContent with a response JSON schema
( source ~/.config/szczur-radar/gemini.env; python3 - <<'EOF' |
import json; r = json.load(open("/tmp/llm-request.json"))
print(json.dumps({"systemInstruction": {"parts": [{"text": r["system"]}]},
  "contents": [{"role": "user", "parts": [{"text": r["user"]}]}],
  "generationConfig": {"temperature": 0, "responseMimeType": "application/json",
                       "responseJsonSchema": r["gemini"]}}, ensure_ascii=False))
EOF
  curl -sS "https://generativelanguage.googleapis.com/v1beta/models/$LLM_MODEL:generateContent" \
    -H "x-goog-api-key: $LLM_API_KEY" -H 'Content-Type: application/json' --data-binary @- ) \
  > src/test/resources/fixtures/gemini/generate-content.json

# OpenRouter (flex upstream for Luna): strict json_schema
( source ~/.config/szczur-radar/openrouter.env; python3 - <<'EOF' |
import json, os; r = json.load(open("/tmp/llm-request.json"))
print(json.dumps({"model": os.environ["LLM_MODEL"], "temperature": 0,
  "response_format": {"type": "json_schema", "json_schema": {"name": "json_schema", "strict": True, "schema": r["openrouter"]}},
  "provider": {"only": ["openai/flex"], "allow_fallbacks": False},
  "messages": [{"role": "system", "content": r["system"]}, {"role": "user", "content": r["user"]}]}, ensure_ascii=False))
EOF
  curl -sS https://openrouter.ai/api/v1/chat/completions -H "Authorization: Bearer $LLM_API_KEY" \
    -H 'Content-Type: application/json' --data-binary @- ) > src/test/resources/fixtures/openrouter/chat-completion.json
```

The recordings only need to be real responses of the API shape; whether the model's answer meets
the contract is measured separately (live smoke, gold recordings).

## Gold sample (`WykopGoldSampler`, plan phase 5)

Reads every #gielda entry and link added on the requested days through
`sources/wykop/WykopRepository` (Wykop search API: `GET search/entries` and `GET search/links` with
`tags[]`, `date_from`/`date_to`, 50 per page until `pagination.total`), maps
each element with `sources/wykop/WykopMapper`, anonymizes it (`@nick` and authors' nicks →
`@uzytkownikN`, URLs → `<url>`, white space untouched), drops entries without content (`Prefilter`),
draws 60 posts per day with a fixed seed and writes
`src/test/resources/fixtures/assessor/gold/posts.json` (`[{post_id, day, input}]`). Raw API answers
are never written. Days 2026-09-28 and 2026-09-29 (the pilots) are refused. Each post records
`media: {photo, embed, survey}` (presence only) for the labeling sheet; the model sees text only.
Links to the original posts are not written anywhere (a link leads to the author).

```bash
# keys of a Wykop app (dev.wykop.pl), only in your shell
( export WYKOP_KEY=… WYKOP_SECRET=… && \
  ./mvnw -Pmeasurement test -Dtest=WykopGoldSampler -Dgold.days=<d1>,<d2> )
# optional: -Dgold.perDay=60 -Dgold.seed=20261004 -Dgold.out=<path>
```

Check the printed counts and skim `posts.json` for nicks or names the anonymizer missed before
committing it. Then label every post in
`context/archive/2026-09-27-assessment-contract-harness/pilot/labeling-gold.html` (guidelines v1.0) and save the
export as `src/test/resources/fixtures/assessor/gold/labels.json`. `GoldDataTest` is skipped until
the files exist and mandatory from then on (≥ 100 posts not marked "hard", two days, no nicks or
URLs, every anchor taken from its post). Metrics: `GoldMetrics` (thresholds in `GoldMetrics.Metric`).

### Metric definitions (`GoldMetrics`, the same in CI and in the report)

Every labeled post counts; a post without a valid output (`failed` or not recorded) has no relevance
and no targets — it is wrong wherever it counts and never leaves a denominator. Posts labeled
"hard" count only for compliance. Targets are paired one-to-one by `AnchorMatcher`. An empty gold
denominator is a measurement error (`InsufficientGoldException`), never a passed threshold.

| Metric | Numerator / denominator | Threshold |
| --- | --- | --- |
| contract compliance | valid outputs / all labeled posts | ≥ 95 % |
| relevance (binary) | model relevance = gold / posts with gold relevance other than `unclear` (model `unclear` = wrong) | ≥ 80 % |
| target recall | matched gold targets / gold targets | ≥ 80 % |
| target precision | matched model targets / model targets (0 when the model returns none) | ≥ 70 % |
| direction accuracy | gold targets with a direction other than `not_expressed` whose matched model target has the same direction / all such gold targets — **an unmatched gold target is wrong** (review of phase 5, F7: kept strict); `unclear` and `not_expressed` count as the same answer (2026-10-05) | ≥ 70 % ¹ |
| dominant direction | posts whose model dominant direction (more bullish than bearish targets or the reverse) equals the gold one / posts where gold has one | ≥ 75 % |
| contradictions | gold bullish/bearish targets whose matched model target has the opposite direction / gold bullish/bearish targets (unmatched = not a contradiction) | ≤ 10 % ¹ |

¹ Relaxed from ≥ 75 % and ≤ 3 % by the user's decision of 2026-10-06: no measured model met them; the
default (`luna`) keeps a margin and closing the gap is post-MVP "LLM hardening" (report
`context/archive/2026-09-27-assessment-contract-harness/reports/gold-2026-10-06.md`).

## Paging probe (`WykopPagingProbe`, S-16)

Measures, on live answers and before the paging logic changes, how the Wykop search pages behave:
for each of the last `probe.days` closed days (default 30, Warsaw time) it reads the #gielda
`GET search/entries` and `GET search/links` page by page straight through
`sources/wykop/WykopApiClient` (raw pages, not `WykopRepository`; the same query: `tags[]`, the whole
day as `date_from`/`date_to`, `sort=newest`, 50 per page, 300 ms between requests). A resource stops
at an empty page, a short page or when its unique elements (by resource and id) reach the first
page's `pagination.total` (at most 200 pages).

It prints only counts — no content, nicks, ids or URLs, and nothing is written to disk:

- per day and resource: pages, each page's `total` (and whether it changed between pages), elements
  received / unique / duplicated, an empty or short page before the total, unique elements below the
  total, and whether the pre-S-16 stop rule (received elements, duplicates included, reach the total) would
  have stopped short of the unique total;
- for the whole run: the same counts summed, the distribution of the elements' `status` per resource
  (absent and `null` apart) with how many of each have no text (an entry with blank `content`, a link
  with blank `title` and `description`), and the elements without text.

```bash
# keys of a Wykop app (dev.wykop.pl), only in your shell
( export WYKOP_KEY=… WYKOP_SECRET=… && \
  ./mvnw -Pmeasurement test -Dtest=WykopPagingProbe )
# optional: -Dprobe.days=30
```

Cost: about 7 requests per day (~6 pages of entries, 1 of links) and one `auth`, about 1–2 min for
30 days. The run fails at the end when a request failed (everything is printed first). The aggregates
and the conclusion (does the search return deleted elements, and in what shape; how often the paging
is inconsistent) go into `context/changes/wykop-ingest-completeness/change.md` (`## Notes`).

## Recording, replay and report (plan phase 6)

`src/test/resources/fixtures/llm/models.json` lists the measured configurations (`id`, `role`:
preferred | fallback | reference, provider, model, tier, reasoning effort, prices per billing channel
in USD per 1M tokens, with their sources and date) and `default` — the configuration S-02 uses, set
after the report. A run with `LLM_*` belongs to the configuration with the same provider, model,
tier and reasoning effort; add a configuration there before recording it.

```bash
# one run per configuration; resumes where it stopped (-Dgold.rerecord=true records everything again)
( source ~/.config/szczur-radar/openrouter.env && ./mvnw -Pmeasurement test -Dtest=GoldRecorder )
( source ~/.config/szczur-radar/deepseek.env   && ./mvnw -Pmeasurement test -Dtest=GoldRecorder )
( source ~/.config/szczur-radar/gemini.env     && ./mvnw -Pmeasurement test -Dtest=GoldRecorder )

# report (no model calls)
./mvnw -Pmeasurement test -Dtest=GoldReport \
  -Dreport.out=../context/archive/2026-09-27-assessment-contract-harness/reports/gold-$(date +%F).md
```

Each assessment becomes `fixtures/llm/recordings/<configId>/<post_id>.json`: the raw answer exactly as
returned and the call's metadata (tokens with cache, duration, attempts, tier, flex fallback, prompt
and contract version) and the verdict. Only recordings with the current `PROMPT_VERSION` and
`CONTRACT_VERSION` count; after a prompt or contract change all three configurations are recorded
again. `RecordingsReplayTest` (a plain `*Test`, in CI, no secrets) judges every current recording
again and fails on a changed verdict, and — once `default` is set — requires a current recording of
every gold post for it and every `GoldMetrics` threshold met.

