# measurement — live runs and recordings (test-only)

Code here calls real LLM APIs and never runs in CI. Keys come only from your shell; they never go
into the repo, CI or Railway. Keep each provider's settings in its own file outside the repo
(`chmod 600`), e.g. `~/.config/szczur-radar/<provider>.env`:

```bash
export LLM_API_PROVIDER=<gemini|deepseek|openrouter> LLM_MODEL=<model id> LLM_API_KEY=<key>
```

## Recording provider payloads (`src/test/resources/fixtures/<provider>/`)

The client tests (`llm/*ClientTest`) replay one real success response per provider on a local HTTP
server. To re-record one, run from `szczur-radar-api/` (the request is example 1 of
`context/foundation/llm-assessment/examples.md`; the key is read from the env file and never
printed). Check the saved body for keys and personal data before committing.

```bash
# request body: system prompt + example 1 input + the V2 schema simplified per provider exactly as
# llm/ProviderSchemas does ($ref inlined; Gemini without count limits; DeepSeek also without
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

# DeepSeek: JSON mode, thinking off, schema in the system prompt
( source ~/.config/szczur-radar/deepseek.env; python3 - <<'EOF' |
import json, os; r = json.load(open("/tmp/llm-request.json"))
print(json.dumps({"model": os.environ["LLM_MODEL"], "temperature": 0,
  "response_format": {"type": "json_object"}, "thinking": {"type": "disabled"},
  "messages": [{"role": "system", "content": r["system"] + "\n\nJSON Schema:\n" + json.dumps(r["deepseek"])},
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
