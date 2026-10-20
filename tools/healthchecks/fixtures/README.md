# fixtures

Odpowiedzi API zarządzania healthchecks.io (v3) dla `test.sh`:

| Plik | Odpowiedź |
| --- | --- |
| `post-pipeline-trigger-created.json`, `post-db-backup-created.json`, `post-api-events-created.json` | `POST /api/v3/checks/` z `unique: ["name"]`, nowy check (HTTP 201, stan `new`) |
| `get-checks.json` | `GET /api/v3/checks/` po `checks.sh apply`: trzy checki środowiska `szczur-radar-pr-99` (checki cronów `up`, check zdarzeń `new`) i check innego środowiska; `test.sh` bierze z niej też odpowiedzi dla checków, które już istnieją (HTTP 200) |
| `error-401.json` | odpowiedź na zły klucz API (HTTP 401) |

**Te pliki nie są nagrane.** Zbudowano je według kształtu z dokumentacji (https://healthchecks.io/docs/api/), bo przy implementacji nie było klucza konta. UUID i adresy (`00000000-0000-4000-8000-0000000000a1` itd.) są zmyślone i nie należą do żadnego projektu. Krok ręczny 3.5 planu `out-of-app-alert-channel` zastępuje je prawdziwymi odpowiedziami z konta operatora (testowy projekt albo testowe checki, usunięte po nagraniu). Przed zapisem w repo trzeba w nich podmienić każdy UUID checka i kanału (z `ping_url`, `update_url`, `pause_url`, `resume_url`, `badge_url`, `channels`) na zmyślony, bo adres pingu to sekret. Klucza API odpowiedzi nie zawierają. Po podmianie `test.sh` musi dalej przechodzić: stałe `u1`…`u3` w teście to UUID-y z tych plików.
