# Provider answers for the fixture days (S-31)

Real OpenRouter chat completions, one per post of the sources' fixture days that goes to the model
(`scenario/RecordedDays`): 33 posts, 13 of Wykop (`fixtures/wykop/search-entries-page.json` without the
three entries the prefilter stops, `search-links-page.json`), 14 of r/inwestowanie
(`fixtures/reddit/search-posts-inwestowanie.json`) and 6 of r/stocks (`search-posts-stocks.json`, the
posts taken down left out). The scenarios replay them through the production OpenRouter client on a
local server (`scenario/RecordedProvider`), matched by the request's user message.

- **Recorded:** 2026-11-12, 33 of 33 posts.
- **Model and settings:** the configuration `luna` of `../models.json`: provider `openrouter`, model
  `openai/gpt-6-luna`, service tier `default`, reasoning effort unset; temperature 0 and the strict JSON
  schema as the production client sends them.
- **Command** (from `szczur-radar-api/`; the key only from the shell):

  ```bash
  ( source ~/.config/szczur-radar/openrouter.env && ./mvnw -Pmeasurement test -Dtest=ScenarioDaysRecorder )
  ```

- **What is written:** the HTTP 200 body exactly as OpenRouter sent it (leading white space included),
  and only a body from which the production client returns the model's text. An answer the validator
  rejects is a good recording; a failed call (a refusal, the content filter, no text, no `choices`, an
  `error` in a 200, another status) is never written: it is tried again, and a post that keeps failing
  is left to the owner.
- **Record again** when the assessment contract or a source fixture or mapper changes: delete the files
  and run the command (`scenario/RecordedDaysTest` fails on a post without a recording or a recording
  without a post). A change of the system prompt alone needs none.

This directory is no configuration of `models.json`, so `RecordingsReplayTest` and `GoldRecorder` never
read it.

## Verdicts

The recorder's last table: each recording replayed through the client, the parser and the D1–D15
validator.

| File | Page | Verdict |
| --- | --- | --- |
| `wykop-entry-2.json` | WYKOP_ENTRIES | ok |
| `wykop-entry-3.json` | WYKOP_ENTRIES | ok |
| `wykop-entry-5.json` | WYKOP_ENTRIES | ok |
| `wykop-entry-6.json` | WYKOP_ENTRIES | ok |
| `wykop-entry-7.json` | WYKOP_ENTRIES | ok |
| `wykop-entry-9.json` | WYKOP_ENTRIES | ok |
| `wykop-entry-14.json` | WYKOP_ENTRIES | ok |
| `wykop-entry-15.json` | WYKOP_ENTRIES | ok |
| `wykop-entry-16.json` | WYKOP_ENTRIES | ok |
| `wykop-link-17.json` | WYKOP_LINKS | ok |
| `wykop-link-18.json` | WYKOP_LINKS | ok |
| `wykop-link-19.json` | WYKOP_LINKS | ok |
| `wykop-link-20.json` | WYKOP_LINKS | ok |
| `reddit-1xgm63i.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xgmvmo.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xgmw66.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xgn59l.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xgnzku.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xgq1zz.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xgrbst.json` | REDDIT_INWESTOWANIE | schema_violation |
| `reddit-1xgrwwg.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xgto1s.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xgvc49.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xgxe5i.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xh44sm.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xh51zz.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xh5lkk.json` | REDDIT_INWESTOWANIE | ok |
| `reddit-1xgfw9k.json` | REDDIT_STOCKS | ok |
| `reddit-1xgnw3d.json` | REDDIT_STOCKS | fabricated_evidence |
| `reddit-1xgptmy.json` | REDDIT_STOCKS | ok |
| `reddit-1xgrblm.json` | REDDIT_STOCKS | ok |
| `reddit-1xgriwj.json` | REDDIT_STOCKS | ok |
| `reddit-1xgrt5x.json` | REDDIT_STOCKS | ok |

31 `ok`, 2 rejected by the validator: `reddit-1xgrbst.json` (r/inwestowanie, `schema_violation`) and `reddit-1xgnw3d.json` (r/stocks, `fabricated_evidence`). Wykop: 13 of 13 `ok`; r/inwestowanie: 13 of 14; r/stocks: 5 of 6.
