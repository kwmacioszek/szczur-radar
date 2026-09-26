# Repository Guidelines

Szczur Radar collects investment posts (Wykop, later Reddit), classifies them with an LLM, and serves them to invited users. One repo contains two independently deployed application modules — a FastAPI backend and a Vue 3 + TypeScript SPA — plus a separate scheduled pipeline job.

## Hard Rules

- `context/` is the planning trail: never overwrite or reformat it; never write to `context/archive/` (@CLAUDE.md). `context/foundation/` docs are edited in place (@context/foundation/README.md); `lessons.md` is append-only.
- If code contradicts @context/foundation/prd.md, flag it — don't silently resolve it in code.
- `szczur-radar-web/` talks to the backend only through its REST API: no direct DB access and no LLM calls from the browser (FR-032).
- No public registration and no automated email in MVP. Accounts are created, bootstrapped, and recovered via backend CLI commands (PRD § Access Control, FR-026–028).
- A new data source changes only acquisition and mapping to the shared model; evaluation, persistence, and API layers stay untouched (PRD § Non-Functional Requirements).
- Check an FR's priority in the PRD before building it: `must-have` first, `nice-to-have` only when asked.

## Project Structure & Module Organization

- `szczur-radar-api/` — Python ≥3.13, managed by `uv`. `main.py` is still the `uv init` stub; no FastAPI app is wired yet. Planned stack: @context/foundation/tech-stack-api.md.
- `szczur-radar-web/` — Vite + Vue 3 + TypeScript SPA with no router, store, or tests yet: @context/foundation/tech-stack-web.md.
- `context/foundation/` — `prd.md` (requirements, FR priorities), `shape-notes.md`, `tech-stack-*.md`, `lessons.md`.
- `context/changes/bootstrap-verification/` — scaffold and audit logs per module.

## Build, Test, and Development Commands

- Backend: `cd szczur-radar-api && uv sync`; add packages with `uv add <pkg>` (never `pip install`); run with `uv run python main.py`.
- Frontend development, build, preview, and related scripts are defined in @szczur-radar-web/package.json.

## Coding Style & Testing

- No linter, formatter, or test runner is configured in either module. Don't assume `pytest`, `vitest`, `ruff`, or ESLint exist — add them explicitly with the change that needs them.
- Frontend TypeScript settings: @szczur-radar-web/tsconfig.app.json.

## Deployment

- Target platform: Railway. The platform decision is finalized in @context/foundation/infrastructure.md.
- `szczur-radar-web` is the only publicly exposed Railway service and serves the Vue SPA.
- Browser requests to `/api/*` on the web application's public origin are reverse-proxied to the private `szczur-radar-api` service over Railway private networking.
- `szczur-radar-api` must not have its own public domain.
- No GitHub Actions CI/check workflow is currently configured. Before enabling production autodeploy, add CI checks and enable Railway Wait for CI.
- Production deployment is planned through Railway native GitHub autodeploy from `main`.
- The scheduled LangGraph pipeline runs as a separate private Railway job/service and must not depend on the lifecycle of either the web or API service.
- Before implementing authentication, document the selected transport, session or token storage, and CSRF handling in the change plan. Browser authentication requests follow the same-origin `/api/*` route above.
<!-- BEGIN @przeprogramowani/10x-cli -->

## Zestaw narzędzi AI 10xDevs — Moduł 1, Lekcja 5

Wybierz platformę wdrożeniową i wdroż na produkcję za pomocą **łańcucha infra**:

```
(/10x-init  →  /10x-shape  →  /10x-prd  →  /10x-tech-stack-selector  →  /10x-bootstrapper  →  /10x-agents-md  →  /10x-rule-review  →  /10x-lesson)  →  /10x-infra-research  →  Plan Mode deploy
```

Pełny łańcuch Modułu 1 wdraża elementy z Lekcji 1–4 (uwzględnione ponownie, aby można było poprawić dowolny wcześniejszy kontrakt w trakcie pracy). `/10x-infra-research` jest głównym tematem lekcji; sam krok wdrożenia korzysta z wbudowanego w hosta **Plan Mode**, a nie z dedykowanej umiejętności — to artefakt (`context/deployment/deploy-plan.md`) jest przekazywany dalej.

### Router zadań — od czego zacząć

| Umiejętność                                                                                                                                                                                    | Użyj, gdy                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Infrastruktura (temat lekcji)**                                                                                                                                                              |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `/10x-infra-research [path-to-tech-stack-or-prd]`                                                                                                                                              | Masz `context/foundation/tech-stack.md` (a najlepiej również `prd.md`) i musisz wybrać platformę wdrożeniową dla MVP. Umiejętność ładuje stack jako twarde ograniczenie, przeprowadza 5-pytaniowy wywiad z deweloperem (trwałe połączenia, wrażliwość na koszty, istniejąca znajomość, zasięg globalny, preferencja współlokalizacji), uruchamia równoległe badania podagentów dotyczące sześciu kandydatów na platformę, ocenia je jako Pass/Partial/Fail według pięciu kryteriów przyjaznych agentom z `references/agent-friendly-criteria.md`, wybiera trzy najlepsze oraz wykonuje trójperspektywiczną kontrolę antybiasową lidera (adwokat diabła, pre-mortem, unknown unknowns) przed zapisaniem `context/foundation/infrastructure.md`. Użyj PO `/10x-tech-stack-selector`, PRZED `/10x-implement`. |
| **Wdrożenie (wbudowane w hosta, nie jest umiejętnością)**                                                                                                                                      |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| Plan Mode deploy                                                                                                                                                                               | Masz `infrastructure.md` + `tech-stack.md` i chcesz przejrzeć plan tylko do odczytu, zanim jakakolwiek zmiana trafi na platformę. Aktywuj tryb planowania swojego asystenta AI do kodowania (na przykład asystent terminalowy może używać przełącznika trybu, a IDE może oferować dedykowany przycisk) z promptem \"Wykonajmy pierwsze wdrożenie w oparciu o `@infrastructure.md`, zgodnie ze stackiem z `@tech-stack.md`\". Przeczytaj plan, zażądaj poprawek, zatwierdź go, a następnie pozwól agentowi wykonać działania. Zatwierdzony plan pozostaje w `context/deployment/deploy-plan.md`, aby planowanie kamieni milowych w następnej lekcji mogło odwołać się do tego, co jest już wdrożone i które sekrety są już podłączone.                                                                      |
| **W razie potrzeby uruchom ponownie wcześniejsze kroki**                                                                                                                                       |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `/10x-init` / `/10x-shape` / `/10x-prd` / `/10x-tech-stack-selector` / `/10x-bootstrapper` / `/10x-agents-md` / `/10x-rule-review` / `/10x-lesson` / `/10x-stack-assess` / `/10x-health-check` | Zebrane razem, aby można było poprawić dowolny wcześniejszy kontrakt w trakcie pracy. Jeśli kontrola antybiasowa wymusi zmianę platformy, która wpływa na decyzję ukształtowaną przez stack (np. \"this DB doesn't fit any platform we'd accept\"), uruchom ponownie `/10x-tech-stack-selector`, aby zachować zgodność `tech-stack.md` i `infrastructure.md`.                                                                                                                                                                                                                                                                                                                                                                                                                                              |

### Jak łańcuch przekazuje pracę dalej

- `/10x-infra-research` odczytuje `context/foundation/tech-stack.md` (język, framework, runtime, baza danych) jako **twarde ograniczenia** — platformy, które nie mogą uruchomić stacka, są odrzucane przed oceną. Odczytuje również `context/foundation/prd.md` (skalę, opóźnienia, oczekiwania dotyczące dostępności) jako **miękkie wagi** podczas oceniania. Oba dane wejściowe są opcjonalne, lecz zdecydowanie zalecane; bez nich umiejętność kontynuuje, ale wyświetla ostrzeżenie.
- Umiejętność zapisuje `context/foundation/infrastructure.md` jako trzeci kontrakt fundamentowy: frontmatter (`project`, `researched_at`, `recommended_platform`, `runner_up`, `context_type`, `tech_stack`) oraz treść obejmującą rekomendację, pełne porównanie platform z macierzą ocen, ustalenia antybiasowe, historię operacyjną (preview / secrets / rollback / approval / logs) i rejestr ryzyk wiążący każdy wpis z perspektywą, która go ujawniła. W przypadku kolizji umiejętność pyta: nadpisać, zapisać jako `infrastructure-v2.md` czy przerwać.
- Plan Mode odczytuje razem `infrastructure.md` i `tech-stack.md`. Agent tworzy krok po kroku plan obejmujący zautomatyzowane kroki, za które odpowiada, bramki ręcznej konfiguracji (tworzenie konta, konfiguracja sekretów), dokładne polecenia wdrożeniowe (polecenia Pages i Workers NIE są wymienne w Cloudflare — plan musi je określać) oraz kroki weryfikacyjne. Plan jest odrzucany/edytowany, aż będzie poprawny; dopiero wtedy Plan Mode zostaje zakończony i rozpoczyna się wykonanie. Zatwierdzony plan trafia do `context/deployment/deploy-plan.md` i jest wykorzystywany dalej przez umiejętności planowania kamieni milowych jako źródło prawdy o „tym, co jest już wdrożone”.

### Co rejestrują umiejętności lekcji (a czego NIE)

- **`/10x-infra-research` rejestruje**: shortlistę platform ocenionych według pięciu kryteriów przyjaznych agentom (jakość CLI, stopień zarządzania/serverless, dokumentacja czytelna dla agentów, stabilne/skryptowalne API wdrożeniowe, MCP lub pierwszorzędna integracja agentowa), trzy wyniki antybiasowe dotyczące lidera (ponumerowane słabości, 150–200-słowowa narracja porażki, 3–5 unknown-unknowns), historię operacyjną z jedną konkretną odpowiedzią na każdą oś (nie kategoriami) oraz rejestr ryzyk, w którym każdy wiersz wskazuje perspektywę źródłową (`Devil's advocate` / `Pre-mortem` / `Unknown unknowns` / `Research finding`). Status każdej funkcji niebędącej GA jest rejestrowany inline (`beta` / `preview` / `region-limited` / `deprecated`) wraz z datą sprawdzenia statusu.
- **`/10x-infra-research` NIE** buduje obrazów Docker ani nie zapisuje Dockerfile, nie konfiguruje potoków CI/CD ani nie planuje poza zakresem MVP (wieloregionowe HA jest wyraźnie poza zakresem). NIE podejmuje decyzji za Ciebie — użytkownik akceptuje, zmienia na drugie miejsce lub przerywa po kontroli, a decyzja ta jest rejestrowana w wyniku.
- **Plan Mode** rejestruje: jawną bramkę człowieka między „agent ma plan” a „agent modyfikuje produkcję”. Artefakt (`deploy-plan.md`) stanowi ścieżkę audytu dla pytania „co miało się wydarzyć”, gdy rzeczywiste uruchomienie pójdzie źle. Plan Mode NIE zastępuje `/10x-infra-research` (decyzja o platformie musi być już podjęta — Plan Mode planuje wdrożenie, nie wybiera miejsca wdrożenia).

### Pięć kryteriów przyjaznych agentom (i dlaczego są kluczowe)

Kryteria tworzące macierz ocen `/10x-infra-research` nie są ogólnymi osiami „dobrej platformy” — są to konkretne cechy określające, czy agent może obsługiwać tę platformę podczas sesji bez konieczności prowadzenia go za rękę:

1. **CLI-first** — każda rutynowa operacja ma udokumentowane polecenie; agent nie musi klikać w panelu.
2. **Managed / serverless** — mniej ruchomych części oznacza mniej sposobów, na które agent (lub Ty) może zepsuć coś, czym powinna zajmować się platforma.
3. **Dokumentacja czytelna dla agentów** — dokumentacja markdown / `llms.txt` / hostowana na GitHubie, którą agent może pobrać i przeanalizować, a nie marketingowe strony renderowane przez JS.
4. **Stabilne, skryptowalne API wdrożeniowe** — przewidywalne kody wyjścia, ustrukturyzowane dane wyjściowe, brak interaktywnych promptów w środku wdrożenia.
5. **Serwer MCP lub pierwszorzędna integracja agentowa** — bonus, nie wymóg. Samo CLI wystarcza dla MVP; MCP jest opłacalne, gdy agent wykonuje dziesiątki ustrukturyzowanych zapytań wobec stanu na żywo.

Twarde filtry są stosowane przed oceną (wymóg trwałego połączenia odrzuca Netlify/Vercel działające wyłącznie serverless; niezgodność runtime ze stackiem całkowicie odrzuca platformę). Odpowiedzi z wywiadu następnie zmieniają wagi kryteriów — wrażliwość na koszty penalizuje drogie poziomy bazowe, znajomość rozstrzyga remisy, preferencja globalnego zasięgu faworyzuje platformy edge-native, a preferencja współlokalizacji faworyzuje zintegrowane bazy danych.

### Antybias jako dyscyplina decyzyjna (nie teatr)

Każda rozmowa badawcza z LLM ma wbudowane przechylenie w stronę tego, co użytkownik już zasygnalizował. `/10x-infra-research` stosuje trzy ustrukturyzowane perspektywy wobec lidera PRZED zapisaniem pliku, nie po:

- **Adwokat diabła** — _znajdź słabości, ukryte koszty i tryby awarii właściwe dla wdrażania `<this stack>` na `<this platform>`_. Wynikiem jest ponumerowana lista 3–5 konkretów, a nie kategorii.
- **Pre-mortem** — _sześć miesięcy później ta decyzja okazała się kompletną katastrofą; przeanalizuj założenia i niedoszacowane ryzyka, które do tego doprowadziły_. Wynikiem jest narracja o długości 150–200 słów; narracje ujawniają konkretne kształty porażki, które ukrywają abstrakcyjne listy ryzyk.
- **Unknown unknowns** — _co jest prawdą o tej kombinacji, czego nie ujawniają w oczywisty sposób strona marketingowa i dokumentacja?_ Wynikiem jest 3–5 nieoczywistych ryzyk.

Po kontroli użytkownik ma trzy rzeczywiste opcje: **kontynuować z liderem i uwzględnić ryzyka w rejestrze**, **zmienić na drugie miejsce** (i ponownie wykonać kontrolę dla nowego lidera) albo **zmienić na trzecie miejsce**. Trzecia opcja jest rzadka; jeśli nigdy nie pojawia się w wielu uruchomieniach, kontrola zdegradowała się do rytuału i powinna zostać przepisana.

Dwie dodatkowe techniki (nie wymagają umiejętności, surowe prompty) należą do tego samego zestawu narzędzi: zmuszanie modelu do porównania trzech alternatyw w tabeli markdown (struktura jest lepsza niż „ta sama odpowiedź innymi słowami”) oraz rotacja ról (ta sama decyzja oczami frontend developera, osoby odpowiedzialnej za bezpieczeństwo i właściciela kosztów — ujawnij koszt ponoszony przez każdą rolę i zaproponuj alternatywy, jeśli którakolwiek z nich się waha).

### CLI vs MCP dla operacyjności infrastruktury na żywo

Po wdrożeniu agent potrzebuje sposobu komunikacji z działającą platformą. Dwie ścieżki, uzupełniające się, a nie konkurujące:

- **CLI** (`wrangler`, `flyctl`, `vercel`, `gh`) — jawne i audytowalne, dane wyjściowe pozostają w terminalu, bezpieczniejsze ustawienia domyślne dla nieodwracalnych działań (np. `netlify deploy` domyślnie tworzy draft; należy przekazać `--prod`). Najlepsze dla MVP: minimalna konfiguracja, niski koszt kontekstu (brak wstępnie załadowanych schematów narzędzi), a agent musi znać polecenie (w czym pomaga umiejętność dla danego narzędzia).
- **MCP** — dedykowany serwer udostępniający ustrukturyzowane narzędzia ze schematami (`pages_deployments_list` itd.). Każdy podłączony serwer MCP dodaje definicje narzędzi do okna kontekstu, więc koszt kumuluje się między serwerami. Jest opłacalny, gdy agent wykonuje wiele zapytań typu discovery wobec stanu na żywo (logi, różnice wdrożeń), a ustrukturyzowany JSON jest lepszy niż parsowanie danych wyjściowych CLI.

Rozsądne ustawienie domyślne: zacznij od CLI, dodaj MCP, gdy zauważysz powtarzający się wzorzec przechodzenia przez `--help`, który agent musi wykonywać, aby odpowiedzieć na klasę pytań. Ujęcie Anthropic w [building-agents-that-reach-production](https://claude.com/blog/building-agents-that-reach-production-systems-with-mcp) brzmi: „API, CLI i MCP to trzy uzupełniające się ścieżki” — wybieraj według zadania, nie hype'u.

### Granica dostępu produkcyjnego (minimalne uprawnienia, człowiek przy nieodwracalnych działaniach)

Zarówno CLI, jak i MCP mogą dać agentowi bezpośredni dostęp do produkcji. Lekcja ustanawia domyślną postawę:

- **Tokeny są ograniczone zakresem, nie są kluczami głównymi.** W Cloudflare: token API ograniczony do Pages lub Workers dla jednego projektu, bez DNS, bez Workers Secrets dla niepowiązanych projektów, bez rozliczeń. Odpowiednik AWS / GCP: ograniczona rola IAM z `console-only-user` lub dostępem tylko do odczytu na produkcji, pełnym dostępem na stagingu.
- **Tokeny znajdują się w zmiennych środowiskowych, nie w `.mcp.json` commitowanym do repozytorium.** Agent pobiera je przez serwer MCP lub wykrywanie zmiennych środowiskowych CLI, a nie przez zwykły tekst w rozmowie.
- **Niszczące działania są zarezerwowane wyłącznie dla człowieka.** Usunięcie bazy danych, rotacja głównego sekretu, usunięcie projektu — są to operacje wykonywane ręcznie w panelu, nawet jeśli agent je sugeruje. Ręczne kliknięcie kosztuje 30 sekund; sprzątanie po zautomatyzowanym błędzie kosztuje godziny.

To jest podejście dla MVP. W miarę dojrzewania projektu naturalną ewolucją jest uzyskanie przez staging pełnego dostępu agenta, podczas gdy produkcja staje się tylko do odczytu — omówione w późniejszych modułach.

### Ścieżki fundamentów używane przez tę lekcję

- `context/foundation/tech-stack.md` — dane wejściowe (przekazanie z Lekcji 2, twarde ograniczenia)
- `context/foundation/prd.md` — dane wejściowe (przekazanie z Lekcji 1, miękkie wagi)
- `context/foundation/infrastructure.md` — dane wyjściowe (trzeci kontrakt fundamentowy)
- `context/deployment/deploy-plan.md` — dane wyjściowe wdrożenia Plan Mode (ścieżka audytu „tego, co miało się wydarzyć”)
- `context/foundation/lessons.md` — powtarzające się reguły i pułapki (użyj `/10x-lesson` z Lekcji 4, jeśli podczas badań lub wdrożenia zauważysz klasę błędów agenta)
- `docs/reference/contract-surfaces.md` — rejestr kluczowych nazw

### Uniwersalny język

Dostarczona umiejętność nie zawiera odniesień do 10xDevs / kohort / certyfikacji. Lista kandydatów na platformę (Cloudflare, Vercel, Netlify, Fly.io, Railway, Render) jest początkową perspektywą badawczą, a nie zestawem rekomendacji — kluczowy jest potok oceniania + wywiadu + kontroli, a platforma nieobecna na domyślnej liście może zostać dodana przez rozszerzenie kroku badawczego. Pięć kryteriów przyjaznych agentom stanowi rzeczywisty rdzeń artefaktu; `/10x-infra-research` ponownie odczytuje je z `references/agent-friendly-criteria.md`, aby ewoluowały wraz z platformami.

Umiejętności nie mogą zapisywać do `context/archive/`. Zarchiwizowane zmiany są niezmienne; jeśli rozstrzygnięta ścieżka docelowa zaczyna się od `context/archive/`, przerwij z komunikatem: \"This change is archived. Open a new change with `/10x-new` instead.\"

<!-- END @przeprogramowani/10x-cli -->
