# szczur-radar-api

Backend Szczur Radar: **Spring Boot 4 na Javie 25**, budowany Mavenem przez wrapper (`./mvnw`, bez globalnego Mavena). Spring Security + Spring Session JDBC (sesja w Postgresie), Spring Data JPA (Hibernate) na schemacie z Flyway, Spring AI (klienci dostawców LLM, bez frameworka agentowego). Wszystkie polecenia poniżej uruchamiaj z katalogu `szczur-radar-api/` z JDK 25 (`export JAVA_HOME=$(/usr/libexec/java_home -v 25)` na macOS).

```bash
./mvnw -B verify                               # testy jednostkowe + integracyjne (wymaga Dockera)
./mvnw -q -DskipTests package                  # target/szczur-radar-api.jar
java -jar target/szczur-radar-api.jar migrate  # migracje Flyway i wyjście
java -jar target/szczur-radar-api.jar          # serwer na :8000 (PORT)
```

## Struktura

Pakiet bazowy `pl.szczurradar.api`, pakiety według funkcji — nie według warstw technicznych:

| Pakiet | Zawartość |
| --- | --- |
| `health` | sondy `GET /api/health` (bez bazy) i `GET /api/ready` (`SELECT 1`, 503 przy błędzie) |
| `user` | encja `User`, `UserRepository` (Spring Data JPA), `Role` (enum), `Emails` (jedna normalizacja e-maila), `SystemAccount` (konta, które nie są osobami: `SCHEDULER`) |
| `auth` | logowanie JSON, `/me`, logout, limiter prób, filtr Origin, odnawianie ciasteczka sesji, argon2 (`Passwords`); `auth/activation` — jednorazowe linki aktywacyjne (S-07): `ActivationLinks` (wydanie, reset konta, sprawdzenie i użycie linku), `ActivationSettings` (`WEB_PUBLIC_URL`, `ACTIVATION_LINK_TTL`), encja tokenu (w bazie tylko SHA-256) i publiczny `ActivationController` |
| `cli` | komendy operatora: `migrate`, `create-admin`, `create-user`, `reset-password`, `send-test-alert` (dyspozytor `CommandLine`; sprawdzenia przed startem Springa w `CommandChecks`; dwie komendy z linkiem aktywacyjnym na wspólnym `LinkCommand`) |
| `alerts` | zdarzenia dla operatora poza aplikacją (F-04): `AlertSettings` (`HEALTHCHECK_PING_URL`), `AlertEvent` (enum, dziś `TEST_SIGNAL`), `OperatorAlerts` (nigdy nie rzuca) z `HealthchecksAlerts` (check healthchecks.io → Telegram) i `NoOperatorAlerts`; bean tylko w serwerze (`AlertsConfiguration`) |
| `internal` | token crona `PIPELINE_TRIGGER_TOKEN` (`PipelineTriggerToken`) i jego filtr dla tras `/internal/**` (`PipelineTriggerTokenFilter`) |
| `config` | Spring Security, sesja i ciasteczko, format błędów `{"detail": …}`, konfiguracja dostawcy LLM |
| `agents` | `agents/assessor` — kontrakt oceny V2 jako kod (model, schemat, parser, walidator D1–D15, odsiew), prompt z wersją i `Assessor` (jedno wywołanie modelu na wpis) |
| `llm` | klienci dostawców LLM: w korzeniu API (`LlmClient` i typy wywołania, `LlmClientFactory`), po podpakiecie na dostawcę (`gemini/`, `deepseek/`, `openrouter/`), `transport/` — jedyne ponowienia (`TransportRetry`), schematy, maskowanie; mapa w `llm/CLAUDE.md` |
| `sources` | źródła danych → wspólny model (`SourcePost`) i wejście kontraktu (jak dodać źródło: `sources/CLAUDE.md`); `sources/wykop` — `WykopApiClient` (autoryzacja, `GET`), `WykopRepository` (wyszukiwarka: wpisy i znaleziska z tagiem w zakresie dat), `WykopMapper` (wpis → `post`, znalezisko → `link`, czas Europe/Warsaw), `WykopConfiguration` (bean z `WYKOP_KEY`/`WYKOP_SECRET`); `sources/reddit` (S-27) — `RedditApiClient` (Arctic Shift bez klucza, `GET`), `RedditRepository` (posty wybranych subredditów w zakresie czasu, stronicowanie po `created_utc`, posty zdjęte pominięte), `RedditMapper` (self-post → `post`, inny → `link`, czas UTC), `RedditConfiguration` (bean z `REDDIT_SUBREDDITS`); `sources/transport` — `HttpRetry`, jedna pętla ponowień klientów źródeł; `Source` (enum źródeł), `UnmappableItemException` (element, który nie może stać się postem) |
| `pipeline` | przebiegi pipeline'u (S-02): w korzeniu punkty wejścia (`PipelineRunService`, `PipelineStartupRecovery`, strażnik `RunWatchdog` z `PipelineStallSettings`, wyjątki); `run/` model przebiegu, `flow/` sekwencja kroków (`RunSequence`, na wątku kolejki `pipeline-run`), `step/` kroki, `assess/` ocena postów, `state/` warunkowe zapisy stanu; mapa w `pipeline/CLAUDE.md` |
| `notifications` | powiadomienia adminów w aplikacji (S-06): encja `Notification`, `NotificationKind` (enum, dziś `run_attention`), `NotificationRepository`, `RunNotifications` (powiadomienie każdego admina o przebiegu, który wymaga uwagi; idempotentne, nigdy nie rzuca) i `api/NotificationController` |
| `posts` | posty i każda próba oceny: encje `Post`, `Assessment`, repozytoria (lista od najnowszych, resztki do ponowienia, ostatnia ocena) |
| `raw` | surowe payloady źródeł osobno od danych aplikacji: `RawPayloadStore` (gzip + SHA-256 w tabeli `raw_payloads`) |
| `persistence` | wspólne klocki zapisu: `EnumValueConverter` (enum ↔ wartość z `CHECK`), `Sha256`; wszystkie konwertery enumów w `persistence/converters/`. Enum zapisywany w bazie ma `CHECK` swojej kolumny równy swoim wartościom, czego pilnuje `ClosedSetsIT` (nowy enum → wpis w rejestrze testu) |
| `text` | `Whitespace` — zbiór białych znaków kontraktu (§10), wspólny dla `agents`, `llm` i `sources` |
| `settings` | wspólne czytanie ustawień środowiska, bez zależności od reszty aplikacji: `DurationSetting` (jedna reguła czasu dla `LLM_TIMEOUT`, `ACTIVATION_LINK_TTL`, `PIPELINE_STALL_TIMEOUT`), `SettingText` (przycinanie wartości), `InvalidSettingsException` (zła wartość: blok `APPLICATION FAILED TO START`, kod `2` komend) |

Kolejne moduły dochodzą obok, bez mieszania infrastruktury z domeną (`context/foundation/lessons.md`).

## Dostawca LLM

Każde uruchomienie kontekstu Springa — serwer, komendy operatora (`migrate`, `create-admin`, `create-user`, `reset-password`) oraz testy `*IT` — czyta i sprawdza ustawienia **jednego** dostawcy LLM (`llm/LlmProviderProperties`, bez platformy agentowej). Wybierają go trzy ustawienia środowiska, zmieniane zawsze razem:

| Zmienna | Znaczenie |
| --- | --- |
| `LLM_API_PROVIDER` | `gemini` (Google AI Studio, natywny Google GenAI), `deepseek` (DeepSeek bezpośrednio) albo `openrouter` (API zgodne z OpenAI) |
| `LLM_MODEL` | dokładne ID modelu tego dostawcy |
| `LLM_API_KEY` | prawdziwy klucz tego dostawcy — sekret: nigdy w repo, logach ani argumentach |

```bash
# DeepSeek bezpośrednio (np. deepseek-v4-flash, deepseek-v4-pro, deepseek-chat, deepseek-reasoner)
export LLM_API_PROVIDER=deepseek LLM_MODEL=deepseek-v4-flash LLM_API_KEY=…
# Gemini przez Google AI Studio (bez Vertex AI)
export LLM_API_PROVIDER=gemini LLM_MODEL=gemini-3.1-flash-lite LLM_API_KEY=…
# OpenRouter: LLM_MODEL to dokładne ID z katalogu OpenRouter (np. dla Luny), nie sama nazwa
export LLM_API_PROVIDER=openrouter LLM_MODEL=<id-z-katalogu-openrouter> LLM_API_KEY=…
```

- Klient wybranego dostawcy powstaje z tych zmiennych; pozostali dostawcy nie są konfigurowani, więc ich klucze nie są potrzebne. OpenRouter ma bazę `https://openrouter.ai/api/v1` (`llm/openrouter/OpenRouterClient`); SDK OpenAI dokleja `chat/completions`.

Opcjonalne ustawienia wywołania klienta oceny (`llm/`), nie są sekretami:

| Zmienna | Znaczenie |
| --- | --- |
| `LLM_SERVICE_TIER` | `default` (domyślnie) albo `flex` — tańszy tryb OpenAI, może odmawiać (429); tylko z `openrouter` (routing `provider.only=["openai/flex"]`, gdy trzy próby na flex się nie udały — dowolna mieszanka 429, 5xx, przekroczenia czasu i błędu połączenia — to samo wywołanie przechodzi na tryb standardowy) |
| `LLM_REASONING_EFFORT` | `none`, `minimal`, `low`, `medium`, `high`, `xhigh`; tylko z `openrouter`; brak = domyślne dostawcy |
| `LLM_TIMEOUT` | limit jednej próby HTTP, np. `120s`, `5m` (domyślnie `120s`, od `10s` do `30m`; dla flex dłuższy) |

- Błędna wartość albo `flex`/`LLM_REASONING_EFFORT` z innym dostawcą niż `openrouter` przerywa start z komunikatem jak dla trzech zmiennych powyżej.
- **Czasy** (`LLM_TIMEOUT`, `ACTIVATION_LINK_TTL`, `PIPELINE_STALL_TIMEOUT`) mają jedną regułę (`settings/DurationSetting`): liczba całkowita z jednostką pisaną małymi literami — `ns`, `us`, `ms`, `s`, `m`, `h`, `d` — np. `300s`, `5m`, w zakresie danego ustawienia (granice włącznie). Goła liczba (`LLM_TIMEOUT=120` — Spring czytałby ją jako milisekundy), zapis ISO-8601 (`PT5M`), ułamek (`1.5h`), spacja w środku (`12 h`), wielka litera (`5M`) i wartość spoza zakresu są odrzucane komunikatem z nazwą zmiennej i podaną wartością, np. „LLM_TIMEOUT needs a unit, e.g. 300s or 5m (got '120'; a bare number would be milliseconds)”, „… must be a duration such as 300s or 5m (got 'PT5M')”, „… must be from 10s to 30m (got '9s')”. Pusta albo nieustawiona wartość daje domyślną.
- Zły `LLM_*` zatrzymuje start serwera i każdej komendy operatora, także pre-deploy `migrate`: Spring Boot loguje blok `APPLICATION FAILED TO START` (opis z listą problemów i działanie, bez stack trace'u; stack trace tylko na DEBUG), a komenda kończy się kodem `2` (sekcja „Komendy operatora”).
- Ocena buduje `ChatModel` Spring AI wprost: klient z `llm/` (`LlmClientFactory` → `create()` w pakiecie dostawcy) tworzy go z tych samych zmiennych, z wyłączonymi ponowieniami frameworka i SDK; jedyne ponowienia (do 3 prób na 429, 5xx, przekroczenie czasu, błąd połączenia) są w `llm/TransportRetry`. Klient powstaje leniwie — start nie łączy się z dostawcą.
- Brak lub błąd którejkolwiek z trzech wartości przerywa start z komunikatem (bez wartości klucza) — `llm/LlmProviderProperties`. Start **nie wywołuje LLM** (klient powstaje przy pierwszej ocenie), więc nie sprawdza ważności klucza u dostawcy.
- Lokalnie trzymaj ustawienia poza repo, np. w `~/.config/szczur-radar/llm.env` (`chmod 600`) i `source` przed `./mvnw verify` lub `java -jar`.
- CI (job `backend`): `LLM_API_PROVIDER` i `LLM_MODEL` jako GitHub Actions *variables*, `LLM_API_KEY` jako *secret*. PR env: job `railway-config` (krok „Ensure PR-env LLM settings”) ustawia w serwisie `szczur-radar-api` te same trzy wartości z CI — także nadpisuje klucz skopiowany przez Railway z produkcji — i usuwa skopiowane z produkcji opcjonalne `LLM_SERVICE_TIER`, `LLM_REASONING_EFFORT`, `LLM_TIMEOUT` (pasują do dostawcy produkcji, a nie CI; np. `flex` tylko z `openrouter`). Produkcja: operator ustawia trzy zmienne **przed** deployem, a `LLM_API_KEY` jest **zapieczętowany** (sealed), żeby Railway nie kopiował go do nowych PR env. `.railway/railway.ts` deklaruje je przez `preserve()`, które niczego nie tworzy.
- Przy kodzie równoległym podawaj własny executor: Java 25 widzi limit CPU kontenera (`availableProcessors()` może być 1).

## Wykop

Przebieg pipeline'u pobiera wpisy i znaleziska z Wykopu (S-02) kluczem aplikacji:

| Zmienna | Znaczenie |
| --- | --- |
| `WYKOP_KEY` | klucz aplikacji Wykop API v3 — sekret: nigdy w repo, logach ani argumentach |
| `WYKOP_SECRET` | sekret aplikacji — j.w. |

- Obie są **opcjonalne**: bez nich serwer, komendy i testy startują normalnie, a Wykop nie jest skonfigurowanym źródłem — przebieg zakresu dat, który go wymaga (domyślnie: wszystkie skonfigurowane źródła), jest odrzucany jako `source_not_configured` (`sources/wykop/WykopConfiguration`, `pipeline/step/SourceIngest`). Tylko jedna z dwóch przerywa start z komunikatem (bez wartości).
- Start nie łączy się z Wykopem: token aplikacji jest pobierany przy pierwszym zapytaniu.
- Lokalnie jak klucz LLM: poza repo, np. `~/.config/szczur-radar/wykop.env` (`chmod 600`), i `source` przed uruchomieniem.
- PR env: job `railway-config` (krok „Ensure PR-env Wykop keys”) ustawia te same klucze aplikacji z sekretów GitHub `WYKOP_KEY`/`WYKOP_SECRET` (bez nich ostrzega, a PR env działa bez Wykopu). Produkcja: operator ustawia obie i je pieczętuje; `.railway/railway.ts` deklaruje je przez `preserve()` (`context/deployment/deploy-plan.md`, sekcja `first-assessed-wykop-feed`).

## Reddit

Przebieg pipeline'u pobiera posty wybranych subredditów (S-27) z publicznego archiwum Reddita **Arctic Shift** (`https://arctic-shift.photon-reddit.com`), bez klucza i rejestracji (`sources/reddit/`):

| Zmienna | Znaczenie |
| --- | --- |
| `REDDIT_SUBREDDITS` | subreddity do pobrania, po przecinku, np. `stocks,ValueInvesting,investing` — nie sekret, wolno ją wypisać. Każda nazwa bez białych znaków na brzegach, z opcjonalnym przedrostkiem `r/` (tylko małymi literami), pisana jak na Reddicie (wielkość liter zostaje), 2–21 znaków `[A-Za-z0-9_]`; nazwa powtórzona w innej wielkości liter liczy się raz (zostaje pierwsza), puste elementy listy są pomijane |

- **Opcjonalna:** bez niej (albo pusta) serwer, komendy i testy startują normalnie, a Reddit nie jest skonfigurowanym źródłem: `GET /api/admin/pipeline-runs/sources` pokazuje go jako `configured: false`, przebieg zakresu, który go nazywa, jest odrzucany jako `source_not_configured`, a noc i „Dni bez danych” go nie widzą (`sources/reddit/RedditConfiguration`, `RedditFetcher`). Wartość bez żadnego subredditu (np. ` , `) albo zła nazwa przerywa start serwera i każdej komendy ze Springiem, także pre-deploy `migrate`, z komunikatem `Invalid Reddit configuration: REDDIT_SUBREDDITS …` nazywającym winny element.
- **Pobranie:** `GET /api/posts/search` z `subreddit`, `after`, `before`, `sort=asc`, `limit=100` (`RedditRepository`), subreddit po subreddicie, po czasie publikacji (`created_utc`, sekundy UTC). Arctic Shift nie ma kursora ani liczby wyników, a jego granice są wyłączne, więc zakres jest o sekundę szerszy z obu stron, a kolejna strona zaczyna od sekundy ostatniego posta (post przeczytany drugi raz liczy się raz). Strona krótsza niż 100 kończy subreddit; pełna strona bez nowego posta albo cofająca się w czasie jest błędem źródła (`INCOMPLETE_PAGING`), nigdy wynikiem z dziurą; najwyżej 50 stron na subreddit (`PAGE_LIMIT`), pauza 300 ms między stronami i subredditami. Self-post (`is_self`) to `post`, każdy inny (link, obraz, crosspost) to `link`, także z treścią; `posts.channel` to nazwa subredditu z payloadu, a RAW to pełny obiekt posta Reddita. Żądania mają `User-Agent: szczur-radar (+https://github.com/kwmacioszek/szczur-radar)`.
- **Świeżość:** post jest w archiwum kilka–kilkanaście sekund po publikacji. Liczniki (`score`, `num_comments`) archiwum uzupełnia po ok. 36 h, ale zapisany post nie jest odświeżany: RAW ma je z chwili pobrania.
- **Posty zdjęte i skasowane** (`removed_by_category` ustawione albo `selftext` równe `[removed]` / `[deleted]`) to stan posta w źródle, nie treść: nie są elementami pobrania (ani `fetched`, ani `skipped`, bo pominięty element dawałby alarm co noc), a ich liczbę podaje jedna linia INFO na subreddit: `Reddit r/<nazwa>: N post(s) in range, R removed or deleted left out, S of unexpected shape`. Post zdjęty po pobraniu zostaje w bazie (bez synchronizacji usunięć; PRD, NFR retencji).
- **Limity i ponowienia:** limity Arctic Shift są dynamiczne. 429, 422 („Timeout. Maybe slow down a bit”), 5xx i błędy transportu są ponawiane do 3 prób przez wspólną pętlę `sources/transport/HttpRetry`, z czekaniem z `Retry-After` albo `X-RateLimit-Reset` (do 30 s), inaczej 2 s i 4 s; każde ponowienie to linia WARN. Inny 4xx i odpowiedź bez listy `data` (także z polem `error`) kończą źródło od razu. Całe pobranie (wszystkie subreddity) ma termin 10 min (`FETCH_DEADLINE`), razem z ostatnią próbą poniżej najkrótszego progu strażnika (15 min), więc padające archiwum kończy źródło `failed` (`DEADLINE_EXCEEDED`), a nie przebieg „bez postępu”.
- **Horyzont:** Arctic Shift nie ma własnych danych — czyta oficjalne Data API Reddita, które Reddit zamyka publicznie w marcu 2027; co wtedy stanie się z archiwum, nie wiadomo. Objaw końca: co noc `reddit` `empty` albo `failed` (przebieg `partial` z `source_empty` / `source_failed`, `RUN_NEEDS_ATTENTION`). Działanie: runbook w `context/deployment/deploy-plan.md`, sekcja S-27 (usunięcie `REDDIT_SUBREDDITS`). Status archiwum: `https://status.arctic-shift.photon-reddit.com`.
- Lokalnie: `REDDIT_SUBREDDITS=stocks java -jar target/szczur-radar-api.jar` (albo `./mvnw spring-boot:run`), z dostępem do sieci. Testów (`./mvnw verify`) nie uruchamiaj z tą zmienną: testy integracyjne mają własny Reddit nad lokalnym serwerem (`LocalReddit`), a drugi bean repozytorium psuje ich kontekst.
- PR env: job `railway-config` (krok „Ensure PR-env Reddit settings”) ustawia wartość z repository variable `REDDIT_SUBREDDITS` (pierwsza konfiguracja: `stocks,ValueInvesting,investing`); bez niej ostrzega, a PR env działa bez Reddita. Produkcja: zmienna nieustawiona do S-28, potem ustawia ją operator runbookiem (`context/deployment/deploy-plan.md`, sekcja S-27). Nie jest sekretem, więc nie jest pieczętowana; `.railway/railway.ts` deklaruje ją przez `preserve()`.

## Pipeline

Przebieg działa w procesie API, w tle (mapa: `src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md`). Admin uruchamia go w `/admin` albo endpointem; co noc uruchamia go serwis cron `pipeline-trigger` (S-05, `tools/pipeline-trigger/`). Przebiegi trafiają do kolejki i idą po jednym. Restart lub deploy przerywa trwający przebieg: przy starcie API, zanim otworzy port, oznacza go jako `interrupted` i oddaje jego posty do ponowienia.

| Zmienna | Znaczenie |
| --- | --- |
| `PIPELINE_LLM_CONCURRENCY` | liczba równoległych ocen LLM w przebiegu (domyślnie `4`, min. `1`) |
| `PIPELINE_TRIGGER_TOKEN` | token crona dla tras `/internal/**` — sekret: nigdy w repo, logach ani argumentach. Opcjonalny: bez niego każde żądanie `/internal/**` dostaje 401. Ustawiony musi mieć co najmniej 32 znaki (np. `openssl rand -hex 32`), inaczej serwer nie startuje (komunikat bez wartości). Czyta go tylko serwer, nie `migrate` ani `create-admin` |
| `PIPELINE_STALL_TIMEOUT` | po jakim czasie bez postępu strażnik (`RunWatchdog`) zatrzymuje przebieg tej instancji, z jednostką, np. `39m`, `1h`. Opcjonalna; domyślnie najdłuższe pojedyncze wywołanie LLM (`TransportRetry.maxCallDuration` dla `LLM_TIMEOUT` i `LLM_SERVICE_TIER`) plus `5m`, ale nie mniej niż `15m`: `15m` przy ustawieniach domyślnych (`120s`, `default`), `39m` na produkcji (`300s`, `flex`). Krótsza jawna wartość jest dozwolona (testy, sprawdzenie ręczne), a serwer loguje przy starcie WARN z tą granicą. Zakres od `1ms` do `1d` (= `24h`); goła liczba, zapis ISO-8601 albo wartość spoza zakresu przerywają start serwera blokiem `APPLICATION FAILED TO START` z nazwą zmiennej (reguła czasu w sekcji „Dostawca LLM”). Czyta ją tylko serwer, nie `migrate` ani komendy CLI |
| `RAILWAY_DEPLOYMENT_ID` | id deploymentu; ustawia go Railway w każdym deploymencie, nie operator. Przebieg zapamiętuje deployment instancji, która go zakolejkowała, a odzyskiwanie przy starcie rozpoznaje po nim przerwanie przez deploy (`deploy`) i restart (`restart`). Lokalnie i w testach go nie ma: przerwany przebieg ma wtedy przyczynę `restart` |

- **Przebieg dzienny:** `POST /internal/pipeline-runs/daily` z nagłówkiem `Authorization: Bearer <token>`, bez treści, uruchamia ten sam start co przycisk admina dla dni D-2…D-1 (kalendarz warszawski) ze wszystkich skonfigurowanych źródeł. Zleceniodawcą jest konto systemowe `scheduler@system.invalid` (migracja `V6`, `login_enabled = false`, bez hasła — nie zaloguje się). Odpowiedzi: 202 `[{id, kind}]`, 409 `pipeline_busy`, 503 `source_not_configured`, 401 bez tokenu lub z innym. `GET /internal/pipeline-runs/{id}` zwraca status przebiegu z licznikami, czasami (`startedAt`, `finishedAt`, `lastActivityAt`), przyczyną przerwania, trwającym krokiem (`step`) i liczbą postów do oceny (`assessTotal`); 404 `not_found` dla nieznanego id. Z tej odpowiedzi cron loguje heartbeat.
- Trasy `/internal/**` mają osobny, bezstanowy łańcuch filtrów (`config/SecurityConfig`): tylko token, nigdy sesja, bez ciasteczek; token nie działa na `/api/**`. Na Railway osiąga je tylko sieć prywatna, bo Caddy przekazuje wyłącznie `/api/*`.
- Lokalnie (token tylko dla lokalnej bazy; `PG*`, `LLM_*`, `WYKOP_*` jak niżej, w tym samym terminalu co jar):
  ```bash
  export PIPELINE_TRIGGER_TOKEN=$(openssl rand -hex 32)
  java -jar target/szczur-radar-api.jar
  # w drugim terminalu, z tym samym PIPELINE_TRIGGER_TOKEN (token przez stdin, nie w argumentach):
  printf 'header = "Authorization: Bearer %s"\n' "$PIPELINE_TRIGGER_TOKEN" \
    | curl -sS -X POST --config - http://localhost:8000/internal/pipeline-runs/daily
  ```
  Przebieg widać w `/admin`. Przez `npm run dev` (`http://localhost:5173/internal/…`) żądanie nie trafia do API: Vite przekazuje tylko `/api`. Cały cron lokalnie: `tools/pipeline-trigger/test.sh` (atrapa API, bez Railway).

- Zakres dat: najpierw (jeśli są) ponowienie nieudanych postów, potem pobranie zakresu z wybranych źródeł (puste = wszystkie skonfigurowane), zapis nowych postów i ocena tylko tych, które ten przebieg zapisał pierwszy. Ponowny zakres tego samego dnia nie dodaje postów.
- **Źródła zakresu (S-20):** źródła są pobierane po kolei, każde osobno. Wynik źródła — `succeeded` (dało co najmniej jeden post), `empty` (odpowiedziało, ale bez posta: nic w zakresie albo same pominięte elementy) albo `failed` (rzuciło wyjątek; błąd `<SimpleName>: <message>`) — trafia do `pipeline_run_sources` zaraz po tym źródle, razem z licznikami przebiegu, i przesuwa `last_activity_at`. Padłe albo puste źródło nie zatrzymuje pozostałych: posty źródeł, które je dały, są zapisywane i oceniane, a przebieg kończy się `partial` z powodem `source_failed` / `source_empty` i błędem nazywającym źródło (np. `reddit failed: …`). **Pusty przebieg** (S-08): gdy żadne źródło nie dało posta, krok `fetch` i przebieg kończą się `failed` z `NoPostsFetchedException`, który nazywa każde źródło z jego wynikiem; dalszych kroków nie ma. Strona przebiegu w `/admin` pokazuje wynik każdego źródła, a „Dni bez danych” liczą dzień jako pokryty dla źródła tylko z jego wyniku `succeeded`.
- Post ma do 3 policzonych prób oceny (`failed`); trzecia nieudana robi z niego `dead` (bez ponawiania). Błąd techniczny nie zużywa próby.
- **Awaria dostawcy (S-09):** wywołanie, które po ponowieniach nadal kończy się 429, 5xx, timeoutem albo błędem połączenia, nie zużywa próby. Pojedyncza taka awaria jest błędem technicznym posta, a trzy z rzędu zatrzymują przebieg jako `failed` (bezpiecznik), z postami odłożonymi bez prób. Post z 3 błędami technicznymi z rzędu jest wstrzymany: ponowienie nieudanych go nie bierze, „Ponów zaznaczone” tak.
- Przed oceną przebieg sprawdza LLM jednym małym wywołaniem (`LlmClient.check()`); gdy się nie uda, żaden post nie jest brany, a przebieg kończy się `failed` z przyczyną. Odrzucony klucz, brak środków lub nieznany model (HTTP 401/402/404) w trakcie przebiegu też go zatrzymuje bez liczenia prób.
- **Postęp (S-06):** liczniki oceny (ok, nieudane, martwe, błędy techniczne, ponowienia HTTP, powroty z flex) i `last_activity_at` rosną po każdym poście, a start i koniec każdego kroku przesuwa `last_activity_at`. Log API ma linie INFO o starcie i końcu przebiegu, starcie i końcu kroku oraz postępie oceny (najwyżej co minutę i po ostatnim poście), bez treści postów.
- **Strażnik:** przebieg tej instancji bez aktywności przez `PIPELINE_STALL_TIMEOUT` kończy się `failed` z błędem „no progress for <próg>: the run was stopped”, jego otwarte kroki są zamknięte, posty wracają bez zużytej próby, a kolejny przebieg w kolejce rusza. Bez własnych przebiegów strażnik nie pyta bazy, więc API może zasnąć.
- **Przyczyna przerwania:** przerwany przebieg ma `interruption_cause` — `deploy` (przejął go nowy deployment), `restart` (ten sam deployment albo nieznany) lub `shutdown` (instancja się zamykała) — a jego otwarte kroki mają status `interrupted`.
- **Powiadomienia:** przebieg, który kończy się `failed`, `interrupted` albo `partial` z martwym postem lub błędem technicznym, daje każdemu adminowi z logowaniem jedno powiadomienie w aplikacji (dzwoneczek w nagłówku). `partial` z samymi nieudanymi postami do ponowienia nie daje powiadomienia: ponowi je kolejny przebieg. Bez pusha i e-maila.

## Baza lokalna

Lokalny Postgres to `compose.local.yaml` z głównego `README.md` — Postgres **18** nasłuchujący tylko na `127.0.0.1:5432`:

```bash
# z katalogu głównego repozytorium
docker compose -f compose.local.yaml up -d postgres
```

Aplikacja buduje URL JDBC z rozbitych zmiennych (jak na Railway, bez parsowania `DATABASE_URL`):

```bash
export PGHOST=127.0.0.1 PGPORT=5432 PGDATABASE=szczur_radar_local PGUSER=szczur_local PGPASSWORD=local_only
```

Wszystkie pięć musi być ustawione: bez nich start się nie powiedzie (Spring Session sprawdza URL JDBC już przy starcie). Sama baza nie musi działać — pula łączy jest leniwa, `/api/health` odpowiada, a `/api/ready` zwraca 503.

Każde zapytanie ma koniec: sterownik przerywa instrukcję, która czeka na odpowiedź bazy dłużej niż 300 s (`socketTimeout` w `application.properties`; serwer, komendy, `migrate` i testy), więc zawieszone połączenie nie trzyma wątku na zawsze.

Lokalnie po `http://` (bez HTTPS) ustaw `COOKIE_SECURE=false`, jeśli przeglądarka nie zapisuje ciasteczka `Secure` (Chrome i Firefox akceptują je na `localhost`). Tylko wartości `0`, `false`, `no` wyłączają `Secure`.

Wolumen sprzed F-03 (baza z czasów Pythona) daje przy `migrate` błąd „Found non-empty schema(s) … but no schema history table”: wyzeruj go `docker compose -f compose.local.yaml down -v` (kasuje lokalne dane), potem `migrate` i `create-admin`. Zmienne (`PG*`, `LLM_*`, `WYKOP_*`) ustawiaj w tym samym terminalu, w którym startuje jar.

## Migracje (Flyway, fix-forward)

Schemat należy wyłącznie do plików SQL w `src/main/resources/db/migration/` (`V<n>__<opis>.sql`). Serwer **nie** migruje przy starcie (`spring.flyway.enabled=false`); robi to komenda `migrate`:

```bash
java -jar target/szczur-radar-api.jar migrate   # albo: ./mvnw spring-boot:run -Dspring-boot.run.arguments=migrate
```

- **Fix-forward:** wdrożonej migracji nigdy się nie edytuje; błąd naprawia kolejna migracja. Flyway Community nie ma `undo`; ostatecznością jest odtworzenie bazy z codziennego dumpa (serwis cron `db-backup`, `tools/db-backup/`; runbook w `context/deployment/deploy-plan.md`, sekcja F-01).
- Ograniczenia mają jawne nazwy (`ck_…`, `uq_…`), żeby kolejne migracje mogły się do nich odwołać.
- `MigrationsIT` stosuje wszystkie migracje od zera na pustej bazie i sprawdza ograniczenia.
- `V13__posts_channel.sql` (S-27) dodaje `posts.channel`: kanał, z którego post przyszedł, w pisowni źródła (tag Wykopu bez `#`, nazwa subredditu jak w payloadzie; ta sama wartość co `channels[0]` wejścia oceny). Istniejące posty Wykopu dostały w backfillu `gielda`; kolumna jest `NOT NULL` z `ck_posts_channel CHECK (channel <> '')`, bez listy wartości (subreddity to zbiór otwarty). API jej jeszcze nie pokazuje (S-29).
- **JPA nie zarządza schematem:** `spring.jpa.hibernate.ddl-auto=none`, a przy starcie Hibernate nie łączy się z bazą (jawny dialekt, `hibernate.boot.allow_jdbc_metadata_access=false`), więc serwer startuje bez bazy. Każda zmiana encji wymaga nowej migracji; `JpaSchemaIT` (`ddl-auto=validate` na schemacie z Flyway) wywala build, gdy encja i schemat się rozjadą.
- Na Railway migracje uruchamia **pre-deploy** serwisu API (`java -jar target/szczur-radar-api.jar migrate`, `.railway/railway.ts`) w kontenerze z tego samego obrazu (katalog `/app`) przed startem nowej wersji — nieudana migracja zatrzymuje deploy, a poprzednia wersja działa dalej.

## Komendy operatora

Ten sam jar: bez argumentów startuje serwer, z nazwą komendy — komendę bez serwera WWW. Nieznana komenda → kod 2 i opis użycia na stderr.

```bash
java -jar target/szczur-radar-api.jar create-admin --email admin@example.com
java -jar target/szczur-radar-api.jar create-user --email jan@example.com --role admin   # bez --role: user
java -jar target/szczur-radar-api.jar reset-password --email jan@example.com
# produkcja / PR env: najpierw obudź API (uśpiony serwis nie ma kontenera — `railway ssh`
# trafia wtedy na bramkę Railway i nic nie uruchamia), potem w powłoce kontenera:
curl https://<domena-web>/api/health
railway ssh -s szczur-radar-api -e <środowisko>
java -jar target/szczur-radar-api.jar create-user --email jan@example.com   # e-mail bez < >
```

Opcje podaje się jako `--nazwa wartość` albo `--nazwa=wartość`; nieznana lub powtórzona opcja to błąd użycia. Argumenty, e-mail i zmienne `PG*` są sprawdzane przed startem kontekstu Springa, więc błąd użycia nie dotyka bazy. Komendy uruchamiają wspólny kontekst Springa, więc też potrzebują trzech ustawień LLM — poza `send-test-alert`, która działa bez Springa i bez bazy. Zła wartość ustawienia w tym kontekście (np. `LLM_TIMEOUT=120`) jest błędem konfiguracji: Spring Boot loguje blok `APPLICATION FAILED TO START`, komenda dopisuje na stderr jedną linię `<komenda>: invalid configuration: …` i kończy się kodem `2`. Inna awaria kontekstu albo pracy (np. nieosiągalna baza, nieudana migracja) daje `<komenda> failed: …` i kod `1`. Dotyczy też `migrate`, więc pre-deploy na Railway zatrzymuje deploy, a poprzednia wersja działa dalej.

### `create-admin`

`create-admin` tworzy **pierwszego** administratora i odmawia, gdy admin już istnieje: kolejnego admina zakłada `create-user --email <e-mail> --role admin`, a utracony dostęp przywraca `reset-password --email <e-mail>`. Hasło jest pobierane dwa razy z ukrytego promptu, nigdy z argumentu, i nie trafia do logów; polityka 12–128 znaków. Bootstrap jest serializowany blokadą `pg_advisory_xact_lock`, więc równoległe uruchomienia tworzą dokładnie jednego admina. Kody wyjścia: 0 utworzono, 1 odmowa lub błąd, 2 błąd użycia lub konfiguracji (brak/zły e-mail, brak zmiennych `PG*`, brak konsoli, zła wartość ustawienia, np. `LLM_*`).

### `create-user` i `reset-password` (S-07)

Obie komendy wydają jednorazowy link do ustawienia hasła. Nie pytają o hasło, więc nie potrzebują konsoli.

- `create-user --email <e-mail> [--role user|admin]` zakłada konto bez hasła (domyślnie rola `user`) i wypisuje jego link. Zajęty e-mail → odmowa z podpowiedzią `reset-password`.
- `reset-password --email <e-mail>` przywraca dowolne istniejące konto, także admina, do stanu „czeka na aktywację”. W jednej transakcji kasuje hasło, usuwa wszystkie sesje konta (wylogowanie wszędzie) i wydaje nowy link; poprzedni link przestaje działać, rola zostaje. Nieznany e-mail → odmowa z podpowiedzią `create-user`. Ustawienie hasła linkiem jeszcze raz usuwa sesje konta: logowanie, które sprawdziło hasło przed zatwierdzeniem resetu, zapisuje sesję już po nim, więc ta sesja kończy się najpóźniej przy aktywacji.
- Konto systemowe (`login_enabled = false`, np. `scheduler@system.invalid` z S-05, `user/SystemAccount`) nigdy się nie loguje, więc nie dostaje linku: obie komendy odmawiają (kod 1, „… is a system account …”) i nic nie zapisują, a link do takiego konta jest nieważny.

Na stdout: jedna linia opisu (konto, rola, ważność do czasu UTC, jednorazowość), a w ostatniej linii sam link `<WEB_PUBLIC_URL>/activate#<token>`. Osoba otwiera link, ustawia hasło (12–128 znaków) i loguje się na `/login`. Link działa raz, a nowy link tego samego konta unieważnia poprzedni.

**Link jest sekretem:** kto go ma, ustawia hasło konta. Przekaż go osobie poza systemem i nie wklejaj go do issue, PR, czatu ani logów. Komenda wypisuje go tylko na stdout, nigdy na stderr ani do logu. Token jest we fragmencie adresu (`#`), więc nie trafia do logów serwera, a baza trzyma tylko jego SHA-256.

| Zmienna | Znaczenie |
| --- | --- |
| `WEB_PUBLIC_URL` | wymagana: publiczny adres aplikacji web — sam schemat, host i opcjonalny port, bez ścieżki (np. `https://szczur-radar-web-production.up.railway.app`); lokalnie `http://localhost:5173` (`npm run dev`) |
| `ACTIVATION_LINK_TTL` | opcjonalna: ważność linku z jednostką, np. `12h`, `90m` (domyślnie `12h`, od `1m` do `7d`); goła liczba (Spring czytałby ją jako milisekundy) i zapis ISO-8601 są odrzucane (reguła czasu w sekcji „Dostawca LLM”) |

Obie zmienne czytają tylko te dwie komendy, przed startem Springa, więc zła wartość nie zatrzymuje serwera.

Na Railway `WEB_PUBLIC_URL` pochodzi z `.railway/railway.ts`: referencja `https://${{szczur-radar-web.RAILWAY_PUBLIC_DOMAIN}}` daje w każdym środowisku adres jego własnego serwisu web (produkcja — domena produkcji, PR env — domena PR env). `ACTIVATION_LINK_TTL` jest opcjonalna: bez niej link jest ważny 12 h, a operator ustawia ją tylko, żeby to zmienić (IaC ją zachowuje przez `preserve()`). Szczegóły wdrożenia i runbook po merge'u: `context/deployment/deploy-plan.md`, sekcja S-07.

Kody wyjścia:

- 0: link wydany;
- 1: odmowa (zajęty e-mail, brak konta albo konto systemowe; nic nie zapisano) albo błąd;
- 2: błąd użycia lub konfiguracji (brak/zły e-mail, nieznana rola lub opcja, brak zmiennych `PG*`, brak/zły `WEB_PUBLIC_URL`, `ACTIVATION_LINK_TTL` bez jednostki, w ISO-8601 lub poza zakresem, zła wartość `LLM_*`); komunikat nazywa opcję albo zmienną.

```bash
# lokalnie: Postgres z compose.local.yaml, PG* i LLM_* jak wyżej, w tym samym terminalu
WEB_PUBLIC_URL=http://localhost:5173 java -jar target/szczur-radar-api.jar create-user --email jan@example.com
```

### `send-test-alert` (F-04)

Wysyła zdarzenie testowe `TEST_SIGNAL` do checka zdarzeń API w healthchecks.io, który alarmuje w Telegramie: przychodzi wiadomość „down” z opisem zdarzenia (np. „TEST_SIGNAL (test of the operator alert channel, nothing is wrong): send-test-alert from <host>”), a zaraz po niej „up”. Tak sprawdza się cały kanał alarmowy od API do telefonu. Komenda nie uruchamia Springa: nie potrzebuje `PG*` ani `LLM_*`, tylko `HEALTHCHECK_PING_URL`, i nie przyjmuje argumentów.

| Zmienna | Znaczenie |
| --- | --- |
| `HEALTHCHECK_PING_URL` | opcjonalna: adres pingu checka zdarzeń API w healthchecks.io, np. `https://hc-ping.com/<uuid>` — **sekret** (kto go zna, może meldować do checka): nigdy w repo, logach, issue ani argumentach. Bez niej (albo pusta) API niczego nie wysyła poza aplikację — tak działają PR envy bez testowego checka, lokalne uruchomienia i testy. Ustawiona musi być bezwzględnym adresem `https://` z hostem, bez zapytania i fragmentu; inaczej serwer nie startuje (blok `APPLICATION FAILED TO START` z nazwą zmiennej, **bez powtórzenia wartości**). Czyta ją serwer przy starcie i `send-test-alert`; `migrate` i pozostałe komendy jej nie czytają, więc zła wartość nie zatrzymuje pre-deployu |

Jedno zdarzenie to dwa żądania: `POST <adres>/fail` z opisem (przyciętym do 1000 znaków), a po odpowiedzi 2xx `POST <adres>` z „ok”, bo healthchecks.io alarmuje tylko przy zmianie stanu i bez powrotu do „up” następne zdarzenie nie dałoby wiadomości. Jedna próba na żądanie, bez ponowień, limity 5 s na połączenie i 10 s na żądanie. Porażka nigdy nie psuje wywołującego: kończy się jedną linią WARN ze zdarzeniem i statusem albo klasą wyjątku, nigdy z adresem. W F-04 woła go tylko ta komenda; ścieżki błędów API podłączy S-18.

```bash
# lokalnie (adres testowego checka trzymaj poza repo, jak klucz LLM)
HEALTHCHECK_PING_URL=https://hc-ping.com/<uuid> java -jar target/szczur-radar-api.jar send-test-alert
# produkcja / PR env: jak create-user — najpierw obudź API, potem w powłoce kontenera
# (zmienna jest już ustawiona w serwisie szczur-radar-api):
curl https://<domena-web>/api/health
railway ssh -s szczur-radar-api -e <środowisko>
java -jar target/szczur-radar-api.jar send-test-alert
```

Na stdout jedna linia o dostarczeniu, na stderr powód porażki; adresu nie wypisuje nigdzie. Kody wyjścia:

- 0: zdarzenie dostarczone, check znów „up”;
- 1: nie dostarczone (odpowiedź spoza 2xx, brak odpowiedzi, przekroczony czas — przyczyna w linii WARN; bez ponowienia);
- 2: błąd użycia lub konfiguracji (argument, brak albo zła wartość `HEALTHCHECK_PING_URL`).

## Obraz Dockera (Railway, CI)

`Dockerfile` w tym katalogu buduje API na Railway (wykrywany automatycznie, jak w `szczur-radar-web`) i w CI (job `api-container-smoke`) — ten sam obraz lokalnie:

```bash
docker build -t szczur-radar-api .
# Postgres z compose.local.yaml; z kontenera host to host.docker.internal (macOS/Windows)
docker run --rm -e PGHOST=host.docker.internal -e PGPORT=5432 -e PGDATABASE=… -e PGUSER=… -e PGPASSWORD=… \
  -e LLM_API_PROVIDER -e LLM_MODEL -e LLM_API_KEY szczur-radar-api migrate
docker run --rm -p 8000:8000 -e PORT=8000 … szczur-radar-api        # serwer
```

- Etap build: `eclipse-temurin:25-jdk-noble`, `./mvnw` (zależności w osobnej warstwie, testy pominięte — uruchamia je CI). Etap runtime: `eclipse-temurin:25-jre-noble`, użytkownik `app` bez roota, `WORKDIR /app`, jar w `/app/target/szczur-radar-api.jar`.
- `ENTRYPOINT` uruchamia jar z `-XX:MaxRAMPercentage=75` (domyślnie JVM bierze na heap 25% pamięci kontenera). Dodatkowe flagi: zmienna `JAVA_TOOL_OPTIONS`. Bez `CMD`: argumenty `docker run` to nazwa komendy.
- `.railway/railway.ts` nie ustawia komendy startowej (zastąpiłaby `ENTRYPOINT`, bez rozwijania zmiennych). Pre-deploy działa w kontenerze z tego obrazu względem `/app`. Powłoka `railway ssh` działa jako root, aplikacja jako `app`.

## Testy

```bash
source ~/.config/szczur-radar/llm.env   # LLM_API_PROVIDER, LLM_MODEL, LLM_API_KEY
./mvnw -B verify
```

- **`*Test`** (Surefire): testy jednostkowe bez kontekstu aplikacji i bez Dockera — JUnit, AssertJ, Mockito.
- **`*IT`** (Failsafe): `@SpringBootTest` + `MockMvcTester` + Testcontainers Postgres 18 (`TestcontainersConfiguration`, `@ServiceConnection`). Brak Dockera **wywala** `verify` — testy nie są pomijane. CI (job `backend`) uruchamia to samo, na runnerze z Dockerem (`ubuntu-latest` albo self-hosted z `tools/ci-runner`).
- **Autoryzacja:** każda nowa trasa jest chroniona domyślnie (deny-by-default w `config/SecurityConfig`). Trasa publiczna wymaga wpisu w `SecurityConfig` **i** w `PUBLIC_ROUTES` w `AuthorizationCompletenessIT` z uzasadnieniem — test woła każde mapowanie bez sesji i oczekuje 401. Trasy admina idą pod `/api/admin/` (rola ADMIN, 403 `forbidden` dla zwykłego użytkownika). Trasy `/internal/**` (cron) bez tokenu dają 401, więc nie trafiają do `PUBLIC_ROUTES`.
- **`measurement`** (tag JUnit, kod tylko w `src/test/java/.../measurement/`): przebiegi na żywych API z kluczami z powłoki — domyślne `test`/`verify` (i CI) je pomijają; uruchomienie: `./mvnw -Pmeasurement test -Dtest=<Klasa>` (bez Springa i Dockera), opis w `src/test/java/pl/szczurradar/api/measurement/README.md`.
- **Fixture'y usług zewnętrznych** (Wykop, Arctic Shift dla Reddita, dostawcy LLM): prawdziwe odpowiedzi jako JSON w `src/test/resources/fixtures/<usługa>/`, bez wymyślanych przykładów.

## API i sesja (kontrakt z SPA)

- Trasy: `GET /api/health`, `GET /api/ready`, `POST /api/auth/login` (JSON `{email, password}`), `POST /api/auth/logout` (zawsze 204), `GET /api/auth/me` → `{id, email, role}`.
- Aktywacja linkiem (S-07), trasy publiczne: `POST /api/auth/activation/lookup` (`{token}` → 200 `{email}`) i `POST /api/auth/activation` (`{token, password}` → 204). Token SPA bierze z fragmentu linku i wysyła tylko w body. Obie trasy nie tworzą sesji i nie ustawiają ciasteczka; aktywacja usuwa wszystkie sesje konta, a potem osoba loguje się przez `POST /api/auth/login`. Hasło spoza polityki (`password_policy`) nie zużywa linku.
- Feed i pipeline (S-02): `GET /api/posts?page&size` (ocenione posty od najnowszych), `GET /api/freshness` (S-05, każda zalogowana rola: `{lastUpdatedAt}` — koniec przebiegu RANGE `succeeded`/`partial` z najdalej sięgającym zakresem albo `null`), admin: `POST /api/admin/pipeline-runs` (`{from, to, sources?}` albo `{postIds}` → 202), `GET /api/admin/pipeline-runs?page&size` (przebiegi od najnowszych, z krokami i licznikami; `size` do 100), `GET /api/admin/pipeline-runs/{id}` (S-06: jeden przebieg jak na liście — z `scheduled`, `needsAttention`, `interruptionCause`, `lastActivityAt`, `llmRetries`, `flexFallbacks`, `inFlight` — albo 404 `not_found`), `GET /api/admin/pipeline-runs/sources` (`[{source, configured}]`), `GET /api/admin/posts/failed` (nieudane, martwe i wstrzymane — S-09: `stored` albo `failed` z 3 błędami technicznymi z rzędu — z ostatnią próbą, `technicalFailures`, `lastTechnicalError` i `onHold`), `GET /api/admin/llm/check` (jedno małe, prawdziwe wywołanie skonfigurowanym kluczem i modelem: `{provider, model, ok, refused, answeredBy, error}`).
- Powiadomienia admina (S-06, każdy admin ma własne i własne „przeczytane”): `GET /api/admin/notifications` → `{unread, items}` (`unread` — wszystkie nieprzeczytane zalogowanego admina, `items` — jego 10 najnowszych `{id, kind, createdAt, readAt, run: {id, kind, status, interruptionCause, finishedAt, assessedFailed, dead, processingErrors, error}}`), `POST /api/admin/notifications/{id}/read` (204, idempotentne; nieznane albo cudze id → 404 `not_found`), `POST /api/admin/notifications/read` (204: oznacza wszystkie nieprzeczytane zalogowanego admina, także poza 10 na liście).
- Błędy aplikacji mają kształt `{"detail": "<kod>"}`: `invalid_credentials`, `not_authenticated`, `forbidden`, `too_many_attempts` (+ `Retry-After`), `cross_origin`, `invalid_request`, `unsupported_media_type`, `activation_link_invalid` (400: link nieznany, użyty, wygasły albo zastąpiony nowszym — nieodróżnialnie), `password_policy` (400: hasło spoza 12–128 znaków), `pipeline_busy` (409), `post_dead` (400), `source_not_configured` (503), `not_found` (404: nieznany przebieg na `/internal/**` i w `GET /api/admin/pipeline-runs/{id}`, nieznane albo cudze powiadomienie). Nieznane pola w JSON są odrzucane.
- Sesja: ciasteczko `sr_session` (`HttpOnly; Secure; SameSite=Strict; Path=/api; Max-Age=30 dni`), dane w tabelach `SPRING_SESSION*`; wygasa po 30 dniach bezczynności, ciasteczko jest odnawiane po każdym udanym, uwierzytelnionym żądaniu API. Unieważnienie wszystkich sesji użytkownika: `FindByIndexNameSessionRepository.findByPrincipalName(<uuid>)` (principal = UUID użytkownika); komendy CLI nie mają repozytorium sesji, więc `reset-password` usuwa wiersze `spring_session` tego konta SQL-em.
- CSRF: `SameSite=Strict` + `OriginGuardFilter` (obcy `Origin` przy zapisie → 403); tokeny CSRF Springa są wyłączone.
- Limiter: 5 nieudanych prób / 15 min na e-mail i 20 na IP (`X-Real-IP` z krawędzi Railway), w pamięci procesu — poprawny przy jednej replice.
