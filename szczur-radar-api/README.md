# szczur-radar-api

> **F-03 w toku:** backend jest już w Javie (Spring Boot 4, Java 25, Maven). Reszta tego pliku opisuje jeszcze Pythona i zostanie przepisana w fazie 4 (`context/changes/java-api-rewrite/plan.md`). Aktualna jest sekcja niżej.

## Dostawca LLM (Embabel)

Każde uruchomienie kontekstu Springa — serwer, komendy `migrate` i `create-admin` oraz testy `*IT` — startuje platformę Embabel z **jednym** dostawcą LLM. Wybierają go trzy ustawienia środowiska, zmieniane zawsze razem:

| Zmienna | Znaczenie |
| --- | --- |
| `LLM_API_PROVIDER` | `gemini` (Google AI Studio, natywny Google GenAI), `deepseek` (DeepSeek bezpośrednio) albo `openrouter` (API zgodne z OpenAI) |
| `LLM_MODEL` | dokładne ID modelu tego dostawcy |
| `LLM_API_KEY` | prawdziwy klucz tego dostawcy — sekret: nigdy w repo, logach ani argumentach |

```bash
# DeepSeek bezpośrednio (modele w Embabel: deepseek-v4-flash, deepseek-v4-pro, deepseek-chat, deepseek-reasoner)
export LLM_API_PROVIDER=deepseek LLM_MODEL=deepseek-v4-flash LLM_API_KEY=…
# Gemini przez Google AI Studio (bez Vertex AI)
export LLM_API_PROVIDER=gemini LLM_MODEL=gemini-3.1-flash-lite LLM_API_KEY=…
# OpenRouter: LLM_MODEL to dokładne ID z katalogu OpenRouter (np. dla Luny), nie sama nazwa
export LLM_API_PROVIDER=openrouter LLM_MODEL=<id-z-katalogu-openrouter> LLM_API_KEY=…
```

- Mapowanie na właściwości Embabel: `src/main/resources/application-<dostawca>.properties` (profil włączany przez `LLM_API_PROVIDER`). Pozostali dostawcy są wyłączeni, więc ich klucze nie są potrzebne. OpenRouter dostaje `base-url=https://openrouter.ai/api/v1`; SDK OpenAI dokleja `chat/completions`.
- Brak lub błąd którejkolwiek z trzech wartości przerywa start z komunikatem (bez wartości klucza) — `config/LlmProviderProperties`. Start i szkieletowy agent (`agents/SkeletonAgent`) **nie wywołują LLM**, więc start nie sprawdza ważności klucza u dostawcy.
- Lokalnie trzymaj ustawienia poza repo, np. w `~/.config/szczur-radar/llm.env` (`chmod 600`) i `source` przed `./mvnw verify` lub `java -jar`.
- CI (job `backend`): `LLM_API_PROVIDER` i `LLM_MODEL` jako GitHub Actions *variables*, `LLM_API_KEY` jako *secret*. Railway: te same trzy zmienne w serwisie `szczur-radar-api` każdego środowiska, ustawione **przed** deployem (`.railway/railway.ts` deklaruje je przez `preserve()`, które niczego nie tworzy). Klucz produkcyjny nie trafia do CI ani PR env.
- Przy kodzie równoległym podawaj własny executor: Java 25 widzi limit CPU kontenera (`availableProcessors()` może być 1); rdzeń Embabel ma własny executor.

Backend Szczur Radar: FastAPI + SQLAlchemy 2 (async, psycopg 3) + Alembic, zależności zarządzane przez `uv` (nigdy `pip`). Wszystkie polecenia poniżej uruchamiaj z katalogu `szczur-radar-api/`.

```bash
uv sync --locked          # zależności z uv.lock
uv run python main.py     # lokalny serwer deweloperski na :8000
```

## Baza lokalna

Lokalny Postgres to ten sam `compose.local.yaml` co w głównym `README.md` — Postgres **18** (obraz Railway `postgres-ssl:18`) nasłuchujący tylko na `127.0.0.1:5432`:

```bash
# z katalogu głównego repozytorium
docker compose -f compose.local.yaml up -d postgres
```

Instancja ma bazę deweloperską `szczur_radar_local`. Testy używają **osobnej** bazy w tej samej instancji — utwórz ją raz:

```bash
docker compose -f compose.local.yaml exec postgres \
  psql -U szczur_local -d szczur_radar_local -c 'CREATE DATABASE szczur_radar_test'
```

Zmienne środowiskowe (aplikacja i Alembic czytają ten sam `DATABASE_URL`; `postgresql://` jest automatycznie zamieniane na sterownik `postgresql+psycopg://`):

```bash
export DATABASE_URL=postgresql://szczur_local:local_only@127.0.0.1:5432/szczur_radar_local
export TEST_DATABASE_URL=postgresql://szczur_local:local_only@127.0.0.1:5432/szczur_radar_test
```

`DATABASE_URL` nie jest potrzebny do importu aplikacji ani do testów sond — engine powstaje leniwie przy pierwszym użyciu.

## Migracje

Schemat bazy należy wyłącznie do migracji Alembic w `migrations/versions/` (`app/models.py` to modele ORM i `target_metadata`).

```bash
uv run alembic upgrade head      # zastosuj wszystkie migracje (na DATABASE_URL)
uv run alembic downgrade -1      # cofnij ostatnią
uv run alembic current           # bieżąca rewizja bazy
uv run alembic heads             # musi być dokładnie jedna głowa
uv run alembic check             # modele zgodne z migracjami?
```

Nowa migracja: `uv run alembic revision --autogenerate --rev-id 0002_<opis> -m "<opis>"` (plik `migrations/versions/0002_<opis>.py`), potem ręczny przegląd pliku. Zasady:

- każda migracja ma działający `downgrade()` — sprawdza go `tests/test_migrations.py` (upgrade → downgrade → upgrade na prawdziwym Postgresie);
- ograniczenia mają jawne nazwy (`ck_…`, `uq_…`), żeby kolejne migracje mogły się do nich odwołać;
- na produkcji po utworzeniu danych preferuj fix-forward (nowa migracja) zamiast downgrade.

Na Railway migracje uruchamia **pre-deploy** serwisu API (`uv run --no-dev alembic upgrade head`, `.railway/railway.ts`) przed startem każdej nowej wersji — nieudana migracja zatrzymuje deploy, a poprzednia wersja dalej działa.

## Testy

```bash
uv run pytest -q                                   # bez bazy: testy `db` są pomijane
TEST_DATABASE_URL=… REQUIRE_DB_TESTS=1 uv run pytest -q   # pełny zestaw, jak w CI
```

- Testy wymagające Postgresa mają marker `@pytest.mark.db`. Dostają automatycznie (`tests/conftest.py`): schemat zbudowany od zera migracjami (`alembic upgrade head` raz na sesję), `DATABASE_URL` ustawiony na `TEST_DATABASE_URL`, puste tabele przed każdym testem oraz zamknięty i wyzerowany engine aplikacji po teście.
- Bez `TEST_DATABASE_URL` testy `db` są **pomijane** — chyba że ustawiono `REQUIRE_DB_TESTS=1`: wtedy uruchomienie **kończy się błędem**. CI (`.github/workflows/ci.yml`, job `backend`) ustawia oba i uruchamia testy na kontenerze `postgres:18`, więc testy bazodanowe nigdy nie są w nim cicho pomijane.
- `TEST_DATABASE_URL` musi wskazywać **dedykowaną bazę testową** (nazwa zawiera `test`) — fixture usuwa i odtwarza jej schemat `public`.
- Testy sond (`tests/test_health.py`) działają bez żadnej bazy i bez `DATABASE_URL`.
