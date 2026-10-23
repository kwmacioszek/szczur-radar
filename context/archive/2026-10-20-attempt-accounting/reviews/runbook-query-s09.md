# Krok 5.3: zapytanie z runbooka S-09 (dowody)

- Data: 2026-10-21, commit `805c912`, wykonał agent.
- Baza: jednorazowy kontener `postgres:18`, schemat z `java -jar target/szczur-radar-api.jar migrate` (V1–V10). Kontener usunięty po sprawdzeniu.
- Zapytanie: wycięte dosłownie z `context/deployment/deploy-plan.md`, sekcja S-09, „Query — posts whose attempts outages used up before S-09”.

## Dane testowe

| Post | Etap | Nieudane próby | Oczekiwane |
|---|---|---|---|
| `dead-outages` | dead | 2× `provider_error` + 1× `timeout`, reguła `call`, „failed after 3 attempts: …” | liczony |
| `failed-timeout` | failed | 1× `timeout`, „failed after 3 attempts: …” | liczony |
| `failed-mixed` | failed | awaria + `schema_violation` | pominięty |
| `failed-choices` | failed | `provider_error` „`choices` is not set” | pominięty |
| `dead-fabricated` | dead | 2 awarie + `fabricated_evidence` | pominięty |
| `failed-refusal` | failed | `provider_error` „HTTP 400 Bad Request” (odmowa) | pominięty |
| `assessed-after` | assessed | awaria, potem ocena ok | pominięty (etap) |
| `stored-fresh` | stored | brak | pominięty (etap) |

## Wynik

```
 stage  | posts
 dead   |     1
 failed |     1
```

Ten sam warunek z listą identyfikatorów zamiast liczby: `dead-outages` (dead), `failed-timeout` (failed). Zgodne z oczekiwanym.

## Nic się nie zmieniło

Liczba wierszy i md5 całej zawartości tabel przed i po zapytaniu (`posts | md5 | assessments | md5`):

```
przed: 8|5abee6307bf53eafbf3f80ab232152e4|13|c822ceccbae8d7e2c0cccd0b8d5dc76c
po:    8|5abee6307bf53eafbf3f80ab232152e4|13|c822ceccbae8d7e2c0cccd0b8d5dc76c
```

Zapytanie działa w `BEGIN READ ONLY` … `ROLLBACK`.

## PR env `szczur-radar-pr-96` (2026-10-23)

Ta sama kopia zapytania (`BEGIN READ ONLY` … `ROLLBACK`) przez `railway ssh -s postgres`. W bazie: 278 postów (261 `assessed`, 17 `failed`, każdy z naruszeniem kontraktu z przebiegu z kroku 3.5).

- Wynik: 0 wierszy, zgodnie z oczekiwaniem.
- Przed i po: `posts` 278 / md5 `2674a8c5f3757621918833e6df6e7a66`, `assessments` 278 / md5 `69e871c4ddab4e34ae48285fb0bc2469`. Bez zmian.

## Skrypt danych testowych

```sql
\set ON_ERROR_STOP on
BEGIN;
insert into users (email, password_hash, role) values ('admin@example.com', 'not-a-hash', 'admin');
insert into pipeline_runs (kind, status, range_from, range_to, sources, requested_by, started_at, finished_at)
  select 'range', 'partial', now() - interval '1 day', now(), array['wykop'], id, now(), now() from users where email = 'admin@example.com';
-- p: id label, stage, attempts
create temp table seed (label text, stage text, attempts int, fails jsonb);
insert into seed values
 -- counted: every failed attempt an outage after retries
 ('dead-outages',     'dead',     3, '[["provider_error","failed after 3 attempts: openrouter: HTTP 503"],["provider_error","failed after 3 attempts: openrouter: HTTP 502"],["timeout","failed after 3 attempts: timed out after 300s"]]'),
 ('failed-timeout',   'failed',   1, '[["timeout","failed after 3 attempts: timed out after 300s"]]'),
 -- not counted
 ('failed-mixed',     'failed',   2, '[["provider_error","failed after 3 attempts: openrouter: HTTP 503"],["schema_violation",null]]'),
 ('failed-choices',   'failed',   1, '[["provider_error","com.openai.errors.OpenAIInvalidDataException: `choices` is not set"]]'),
 ('dead-fabricated',  'dead',     3, '[["provider_error","failed after 3 attempts: HTTP 500"],["timeout","failed after 3 attempts: timed out"],["fabricated_evidence",null]]'),
 ('failed-refusal',   'failed',   1, '[["provider_error","openrouter: HTTP 400 Bad Request"]]'),
 ('assessed-after',   'assessed', 1, '[["provider_error","failed after 3 attempts: HTTP 503"]]'),
 ('stored-fresh',     'stored',   0, '[]');
with raw as (
  insert into raw_payloads (source, content_encoding, media_type, payload, sha256)
  select 'wykop', 'gzip', 'application/json', decode('1f8b08000000000000ff', 'hex'), repeat('a', 64) from seed
  returning id
), numbered_raw as (select id, row_number() over () n from raw),
   numbered_seed as (select *, row_number() over () n from seed)
insert into posts (source, item_type, source_item_id, published_at, title, body, raw_payload_id, content_hash, stage,
    assessment_attempts, first_run_id)
  select 'wykop', 'post', s.label, now(), s.label, 'body', r.id, repeat('b', 64), s.stage, s.attempts, (select id from pipeline_runs)
  from numbered_seed s join numbered_raw r using (n);
-- failed attempts
insert into assessments (post_id, run_id, status, failure_class, input, input_hash, violations, result_source,
    provider, model, channel, service_tier, flex_fallback, contract_version, assessed_at, duration_ms, attempts)
  select p.id, p.first_run_id, 'failed', f ->> 0, '{}', repeat('c', 64),
    case when f ->> 1 is null then jsonb_build_array(jsonb_build_object('failureClass', f ->> 0, 'rule', 'D3', 'path', '$.targets[0]', 'message', 'contract'))
         else jsonb_build_array(jsonb_build_object('failureClass', f ->> 0, 'rule', 'call', 'path', '$', 'message', f ->> 1)) end,
    'model', 'openrouter', 'test-model', 'openrouter', 'default', false, 'v2', now() - (o || ' hours')::interval, 1, 3
  from seed s join posts p on p.source_item_id = s.label, jsonb_array_elements(s.fails) with ordinality as x(f, o);
-- the ok assessment of the assessed post
insert into assessments (post_id, run_id, status, input, input_hash, output, violations, result_source, provider, model,
    channel, service_tier, flex_fallback, output_mode, prompt_version, contract_version, assessed_at, duration_ms, attempts)
  select id, first_run_id, 'ok', '{}', repeat('c', 64), '{}', '[]', 'model', 'openrouter', 'test-model', 'openrouter',
    'default', false, 'json_schema', 'test', 'v2', now(), 1, 1 from posts where source_item_id = 'assessed-after';
COMMIT;
```
