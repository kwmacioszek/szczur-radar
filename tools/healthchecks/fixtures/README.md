# fixtures

Odpowiedzi API zarządzania healthchecks.io (v3) dla `test.sh`:

| Plik | Odpowiedź |
| --- | --- |
| `post-pipeline-trigger-created.json`, `post-db-backup-created.json`, `post-api-events-created.json` | `POST /api/v3/checks/` z `unique: ["name"]`, nowy check (HTTP 201, stan `new`) |
| `get-checks.json` | `GET /api/v3/checks/` po `checks.sh apply`: trzy checki środowiska `szczur-radar-pr-99` (checki cronów `up`, check zdarzeń `new`) i check innego środowiska; `test.sh` bierze z niej też odpowiedzi dla checków, które już istnieją (HTTP 200) |
| `error-401.json` | odpowiedź na zły klucz API (HTTP 401) |

**Nagrane 2026-10-23** z prawdziwego API (konto operatora, klucz read-write): `checks.sh apply szczur-radar-pr-99` przez lokalny proxy zapisujący odpowiedzi, potem `checks.sh delete szczur-radar-pr-99`. Check innego środowiska w `get-checks.json` to prawdziwy `pr-95/pipeline-trigger` z tej samej odpowiedzi. Odpowiedzi z kluczem read-write mają też pola `uuid`, `filter_default_fail` i `filter_http_body`, których dokumentacja v3 nie wymienia. Każdy UUID checka, kanału i odznaki (w `uuid`, `ping_url`, `update_url`, `pause_url`, `resume_url`, `badge_url`, `channels`) podmieniono na zmyślony (`00000000-0000-4000-8000-0000000000a1` itd.), a segment klucza odznaki w `badge_url` na `2`, bo adres pingu to sekret. Klucza API odpowiedzi nie zawierają. `error-401.json` to odpowiedź na zły klucz o prawidłowej długości (krótszy daje `missing api key`). Przy ponownym nagraniu `test.sh` musi dalej przechodzić: stałe `u1`…`u3` w teście to UUID-y z tych plików.

## `ping-urls.txt`

To nie jest nagranie odpowiedzi API, tylko tabela przypadków jednej reguły adresu check-in `HEALTHCHECK_PING_URL` (S-25): linie `accept <adres>` albo `refuse <adres>` (werdykt, jedna spacja, reszta linii to adres, także ze spacjami; linie puste i zaczynające się od `#` są pomijane). Czytają ją `AlertSettingsTest` w `szczur-radar-api` (wzorzec `AlertSettings.PING_PATTERN`) i krok 9 `test.sh` (blok walidacji wycięty z `tools/db-backup/backup.sh`, ten sam co w `tools/pipeline-trigger/trigger.sh`); obie strony muszą rozstrzygnąć każdy wiersz tak jak tabela. Adresy są zmyślone (UUID-y `00000000-…`), żaden nie jest sekretem. Nowy przypadek reguły dopisuje się tutaj, nie w kodzie testu.
