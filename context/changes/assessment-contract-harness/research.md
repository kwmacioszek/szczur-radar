---
date: 2026-09-29T22:33:03+02:00
researcher: Claude (Opus 5.5) dla kwmacioszek
git_commit: b2e26c89a16e047fd9cdbd239ce873c2de907092
branch: chore/archive-s01
repository: szczur-radar
topic: "Kontrakt input/output LLM V1, liczba dostawców (2 zamiast 3–4?) i LangGraph — dowody przed ponownym ramowaniem F-02"
tags: [research, llm-assessment, contract-v1, providers, gemini, deepseek, langchain, langgraph, assessment-contract-harness]
status: complete
last_updated: 2026-10-03
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Follow-up 2026-10-03: przeplanowanie p2–p4 w Javie z Embabel (structured output, zamknięte modele, dostawcy, port reguł kontraktu V2). Sekcja LangChain/LangGraph (B, C) zastąpiona dla implementacji przez F-03 — patrz sekcja Follow-up."
---

# Research: kontrakt V1, liczba dostawców i LangGraph dla `assessment-contract-harness`

**Date**: 2026-09-29T22:33:03+02:00
**Researcher**: Claude (Opus 5.5) dla kwmacioszek
**Git Commit**: b2e26c89a16e047fd9cdbd239ce873c2de907092
**Branch**: chore/archive-s01
**Repository**: szczur-radar

## Research Question

> „chciałbym omówić sam kontrakt input/output dla LLM, założenie, że będą 3–4 dostawcy (może wystarczy 2? np. Gemini i DeepSeek), i też sam LangGraph, żeby dobrze go sprawdzić”

Badanie łączy dowody wewnętrzne (repo, `context/`) z zewnętrznymi (dokumentacja dostawców, LangChain/LangGraph — źródła w linkach). Istniejący `plan.md` (status `plan_reviewed`) jest tu traktowany jako badany materiał, nie jako ustalenie.

## Summary

1. **Kontrakt V1** jest spójny i kompletny co do kształtu, ale ma cztery miejsca, w których plan już teraz dopowiada reguły ponad tekst kontraktu (niezmiennik 13 dla `mixed`, niepusty cytat, anchor dla targetów `unresolved`/`ambiguous`, reguły `claim_ids` przy `unclear`), co wg reguły zmiany §14 powinno iść przez zmianę kontraktu, nie przez kod. Część kontraktu (kontekst rodzica, typ `comment`, przypadek `contextual_target`) nie jest osiągalna w MVP, bo PRD wyklucza komentarze. Najbardziej ryzykowny dla modeli niezmiennik to 8 (cytat jako dokładny podciąg) — nie znaleziono publicznych pomiarów tej zdolności dla tekstu nieangielskiego.
2. **Wielu dostawców (OpenAI / Gemini / DeepSeek / Claude)** nie pochodzi z PRD ani z roadmapy — pojawia się dopiero w `change.md:12` i w planie. Roadmapa mówi o „prawdziwym modelu” (l. pojedyncza) i zostawia wybór dostawcy oraz budżet jako otwarte pytania użytkownika. Para **Gemini + DeepSeek** pokrywa dwa różne mechanizmy structured output (natywny JSON Schema vs strict function calling w becie), obie mają tanie modele i żadna nie egzekwuje wszystkich limitów kontraktu po stronie API — więc walidacja backendowa (już zaplanowana) pozostaje jedyną bramką niezależnie od liczby dostawców.
3. **LangGraph** jest przyjęty w `tech-stack-api.md:29` na podstawie preferencji z archiwalnej sesji uzasadnionej przyszłymi węzłami ML/embeddingów; żaden dokument nie wymaga, by F-02 lub S-02 go używały, i nigdzie nie ma mowy o checkpointingu. Dla liniowego, dziennego batcha kilkuset postów LangGraph (Functional API) jest dopuszczalny, ale nie niezbędny; jego realne ryzyka (fan-out i limit współbieżności, retry vs „brak ponowień semantycznych”, tabele checkpointera poza Alembic, testy z fake modelami) nie są dziś pokryte przez żaden krok planu — plan F-02 jawnie wyklucza graf (`plan.md:13`, `plan.md:38`).

## Detailed Findings

### A. Kontrakt input/output V1

**Kształt i granice (potwierdzone)**

- Przepływ `RAW -> adapter -> AssessmentInputV1 -> LLM -> AssessmentOutputV1 -> validation` (`context/foundation/llm-assessment/contract-v1.md:14-16`); analizowany jest wyłącznie `item`, parent służy do interpretacji odniesień (`contract-v1.md:76`).
- Do modelu nie trafiają source, autor, engagement, URL, ID, język (`contract-v1.md:78-90`); provenance trzyma backend (`contract-v1.md:482-497`, `agent-implementation.md:127-147`).
- Output: `relevance`, `modifiers`, `targets` (z `anchor` i `stance` per target), `claims` (z `evidence` i `supported_by_claim_ids`), `flags` (`contract-v1.md:98-149`); zamknięte enumy w §9 (`contract-v1.md:394-414`), 16 niezmienników (`contract-v1.md:420-435`), limity (`contract-v1.md:443-454`).
- Kontrakt sam stwierdza, że JSON Schema nie zastępuje backendowej walidacji evidence, referencji i grafu (`contract-v1.md:437`).
- Definition of done warstwy kontraktu w `agent-implementation.md:186-198` wymaga, by „wymagane fixtures przechodzą” — bez wskazania liczby modeli ani liczby uruchomień. Liczby „3 uruchomienia” i „macierz ≥3 modeli” są decyzjami planu (`plan.md:22`, `plan.md:248`), nie kontraktu.
- Retry jest jawnie osobną decyzją implementacyjną (`agent-implementation.md:87`).

**Miejsca, w których plan dopowiada reguły ponad kontrakt** (każde to kandydat na regułę zmiany §14: `contract-v1.md:518-528`, `agent-implementation.md:170-182` — „nie twórz drugiego »tymczasowego« kontraktu w kodzie”):

1. *Niezmiennik 13 a `mixed`.* Kontrakt: „Rozstrzygnięty stance autora ma claim z `attribution=author`” (`contract-v1.md:433`, też `:282`), bez definicji „rozstrzygnięty”; niezmiennik 16 wymaga `claim_ids` tylko dla `bullish|bearish|neutral` (`contract-v1.md:435`), a 15 dla `mixed|unclear` reguluje tylko `assertiveness` (`contract-v1.md:434`). Plan rozszerza 13 na `mixed` i sam nazywa to „surowszym niż dosłowny niezmiennik 16” (`plan.md:56`).
2. *Niepusty cytat.* Kontrakt nie ma minimalnej długości `quote` (`contract-v1.md:443-452`); plan dodaje min. 1 znak (`plan.md:58`, `plan.md:76`).
3. *Anchor targetów `unresolved`/`ambiguous`.* Wszystkie klucze są wymagane (`contract-v1.md:421`), `anchor` jest obiektem z `quote` (`contract-v1.md:225-233`); kontrakt nie mówi, co jest anchorem dla targetu `unresolved`, a przykład §4 podaje tylko `target.reference = unresolved` bez anchora (`examples.md:161-177`). Nie jest też określone, czy `anchor` może być `null`.
4. *`unclear` a `claim_ids`.* Dla `direction = unclear` kontrakt reguluje tylko `assertiveness = null` (`contract-v1.md:274`, `:434`); dozwolone/wymagane `claim_ids` nie są określone.

**Przypadki niejednoznaczne z założenia** (asercje muszą to tolerować):

- `reported_claim`: `kind` = „`factual_claim` lub `investment_thesis`” (`examples.md:235`).
- `sarcasm`: dwa poprawne zachowania — rozpoznany sarkazm bez flagi albo `possible_sarcasm` (`examples.md:330-336`).
- `two_targets_opposite_stance`: „sama sprzedaż nie musi zawsze oznaczać bearish” (`examples.md:157`).

**Część kontraktu poza zasięgiem MVP**

- PRD wyklucza przetwarzanie komentarzy z MVP (`context/foundation/prd.md:285`); roadmapa odkłada komentarze i wprost zaznacza, że kontrakt obsługuje `comment` i kontekst rodzica, „nieużywane w MVP” (`context/foundation/roadmap.md:237`, `:134`).
- Wniosek (nie sprawdzony w API Wykopu): dla typów wchodzących do MVP (`post`, `link` — `contract-v1.md:68-71`) nie ma rodzica, więc w MVP `context.parent_* = null`, a niezmienniki 5 i 11 oraz wymagany fixture `contextual_target` (`agent-implementation.md:103`, `examples.md:243-285`) sprawdzają ścieżkę, której produkcja S-02 nie wywoła. Kontrakt nadal wymienia ten fixture jako wymagany.

**Niezmiennik 8 — dokładny cytat**

- Kontrakt wymaga dosłownego podciągu (`contract-v1.md:26`, `:427`); plan wyklucza normalizację (`plan.md:31`, `plan.md:44`).
- Zewnętrznie: nie znaleziono benchmarku wierności dosłownych cytatów w tekście nieangielskim. Pokrewne źródła: ekstrakcja spanów przez LLM w ACL-Verbatim osiąga word-F1 ok. 47–49 (angielski, mierzy wybór spanu, nie kopiowanie znak w znak) — https://arxiv.org/html/2605.21102v1; narzędzia weryfikacji cytatów stosują normalizację Unicode/cudzysłowów — https://github.com/pierreolivierbonin/verbatimeter. Typowe tryby błędu (białe znaki, cudzysłowy, emoji, poprawianie polskich znaków/literówek, parafraza) są w źródłach anegdotyczne.
- Materiał testowy zawiera emoji (`examples.md:209`) i wieloliniowe body (`examples.md:14-16`) — właśnie te cechy, przy których normalizacja przez model jest najbardziej prawdopodobna.

### B. Liczba dostawców: 3–4 czy 2 (Gemini + DeepSeek)

**Skąd wziął się wymóg wielu dostawców (repo)**

- Brak w PRD, shape-notes, infrastrukturze i tech-stacku: przeszukanie `context/` nie znalazło nazw OpenAI/Gemini/DeepSeek/Claude poza folderem tej zmiany. Jedyny guardrail kosztowy PRD: „Koszt zapytań do LLM powinien być minimalizowany, gdzie to możliwe” (`prd.md:49`); wymóg jakości: „bez halucynacji” (`prd.md:48`).
- Roadmapa: F-02 ma być „sprawdzony na prawdziwym modelu” (`roadmap.md:47`, `roadmap.md:97`); wybór dostawcy/modelu i koszt oceny to otwarte Unknown, owner: user, bez blokady (`roadmap.md:105`); Open Roadmap Question 5 — brak pułapu budżetu LLM (`roadmap.md:222`).
- Pierwsze wystąpienie listy czterech dostawców: `context/changes/assessment-contract-harness/change.md:12`; decyzja „trzeba porównać, który działa najlepiej” i obowiązkowa macierz DeepSeek + Gemini: `plan-brief.md:21`, `plan-brief.md:25`, `plan.md:22`, `plan.md:248`. Folder zmiany ma jeden commit (`ef4041b`).
- Sekrety: klucz LLM jako współdzielona zmienna Railway dla API i crona (`infrastructure.md:94`); F-01 ma zapieczętować sekrety LLM poza środowiskami PR (`roadmap.md:83`). Żaden dokument nie wymienia dostawcy.

**Gemini (zewnętrzne; ceny do ponownej weryfikacji na żywych stronach)**

- Structured output przyjmuje podzbiór JSON Schema: typy podstawowe, `enum`, `required`, `additionalProperties`, `minItems/maxItems`; blog Google deklaruje też `anyOf`, `$ref`, `minLength/maxLength`, ale strona dokumentacji tego nie potwierdza; bardzo duże/zagnieżdżone schematy „mogą zostać odrzucone” bez podanego limitu — https://ai.google.dev/gemini-api/docs/structured-output, https://blog.google/innovation-and-ai/technology/developers-tools/gemini-api-structured-outputs/.
- Znana pułapka: przekroczenie `max_output_tokens` daje pusty `text`/`parsed` — https://github.com/googleapis/python-genai/issues/1039.
- `langchain-google-genai`: `with_structured_output` domyślnie `json_schema` (→ `response_json_schema`), także `function_calling` i `json_mode`; `usage_metadata` zawiera tokeny rozumowania w output — https://github.com/langchain-ai/langchain-google/blob/main/libs/genai/langchain_google_genai/chat_models.py.
- Ceny wg pobranej strony (per 1M tokenów, standard): `gemini-3.5-flash-lite` $0.30 in / $2.50 out; Flash 3.6–3.8 $0.75 / $3.75 w promocji do 2026-12-31, potem $1.50 / $7.50; batch o połowę taniej — https://ai.google.dev/gemini-api/docs/pricing. Pobrane przez streszczenie strony, więc do ponownej weryfikacji.

**DeepSeek (zewnętrzne; ceny do ponownej weryfikacji)**

- JSON mode nie egzekwuje schematu; strict function calling jest w becie (`base_url=https://api.deepseek.com/beta`, `strict: true`), wymaga `required` + `additionalProperties: false` na każdym obiekcie, wspiera `enum`, `anyOf`, `$ref/$defs`, **nie wspiera** `minLength/maxLength` ani `minItems/maxItems` (czy są odrzucane, czy ignorowane — nieustalone) — https://api-docs.deepseek.com/guides/tool_calls, https://api-docs.deepseek.com/guides/json_mode.
- `langchain-deepseek` po cichu zamienia `method="json_schema"` na `function_calling`, a przy `strict=True` przełącza na endpoint `/beta` — https://github.com/langchain-ai/langchain/blob/master/libs/partners/deepseek/langchain_deepseek/chat_models.py. Zgodne z ustaleniem F1 przeglądu planu (`reviews/plan-review.md`).
- Modele wg strony cennika: `deepseek-flash` i `DeepSeek-V4-Pro`; Flash: $0.30 cache-miss in / $1.20 out, off-peak o połowę taniej (poza 01–04 i 06–10 UTC pn–pt) — https://api-docs.deepseek.com/quick_start/pricing. Nie potwierdzono statusu nazw `deepseek-chat`/`deepseek-reasoner`.

**Co wynika dla pary Gemini + DeepSeek**

- Obie pokrywają dwa różne mechanizmy structured output (natywny JSON Schema vs strict tool calling), czyli właśnie tę różnorodność, którą port `AssessmentProvider` ma obsłużyć (`plan.md:139-145`). OpenAI i Anthropic w planie idą tą samą ścieżką co Gemini (`json_schema` + `raw.text`, `plan.md:142`).
- Na tych dwóch sprawdzonych ścieżkach żadna nie gwarantuje po stronie API wszystkich limitów §11 (DeepSeek: brak limitów długości i liczności; Gemini: `maxLength` niepotwierdzone), więc limity egzekwuje Pydantic + walidator backendu — co plan już zakłada (`plan.md:14`, `plan.md:50`) i co kontrakt nakazuje (`contract-v1.md:437`).
- Wolumen odniesienia: stary Szczur Radar miał 11 767 wpisów z 29 dni (`shape-notes.md:406`), ok. 406 wpisów/dzień. Koszt dzienny zależy od tokenów na ocenę, których dziś nikt nie zmierzył — plan przewiduje ich pomiar (`plan.md:292`).
- Bez strony zewnętrznej nie ma dowodu, że którakolwiek z par modeli trzyma kontrakt; to jest dokładnie ryzyko, które F-02 ma zamknąć (`roadmap.md:106`).

**LangChain vs bezpośrednie SDK (fakty, bez decyzji)**

- LangChain dodaje warstwę mapowania metod per dostawca (np. cicha zamiana w DeepSeek), co zmienia zbiór egzekwowanych ograniczeń; bezpośrednie SDK (google-genai, SDK zgodne z OpenAI na `/beta` DeepSeeka) dają kontrolę nad wysyłanym schematem i też zwracają zużycie tokenów. Nie znaleziono źródła porównującego oba podejścia — to wniosek.
- Pydantic generuje `anyOf [X, null]` dla `Optional` i `$defs/$ref` dla modeli zagnieżdżonych; wsparcie różni się per dostawca (patrz wyżej) — w razie odrzucenia schemat trzeba spłaszczyć w adapterze (plan to dopuszcza: `plan.md:50`).
- LangGraph i tak zależy od `langchain-core` (zob. C), więc rezygnacja z integracji LangChain dla dostawców nie usuwa `langchain-core` z zależności, jeśli LangGraph zostaje.

### C. LangGraph

**Co mówi repo**

- Źródło wyboru: archiwalna sesja shape — „flow oparty o LangGraph” (`context/foundation/archive/shape-notes-2026-09-26-1137.md:43`), uzasadnione planowanym rozszerzeniem o „lekkie modele ML/AI, embeddingi i klasyfikatory … bez przepisywania architektury” (`…-1137.md:230`).
- Aktualne shape-notes: to „NIE jest zatwierdzony wybór technologiczny … do ponownej oceny przy /10x-tech-stack-selector” (`context/foundation/shape-notes.md:385`, `:397`).
- `tech-stack-api.md:29` przyjmuje go: FastAPI „wrapping a LangGraph-orchestrated data and LLM pipeline”, LangGraph niezależny od HTTP, wywoływalny z joba, CLI, API i testów.
- Infrastruktura: osobny prywatny serwis cron Railway uruchamiający pipeline i kończący się (`REPOSITORY_GUIDELINES.md:44`, `infrastructure.md:130`, `context/deployment/deploy-plan.md:141`); serwisu harmonogramu jeszcze nie ma (`roadmap.md:75`).
- Brak jakiejkolwiek wzmianki o checkpointingu w `context/` (przeszukany cały katalog).
- Żaden dokument nie wymaga, by F-02 lub S-02 użyły LangGraph; jedynie plan F-02 zakłada, że S-02 osadzi `assess()` w węźle (`plan.md:13`, `plan.md:38`).
- Zależności backendu dziś: brak `langchain*` i `langgraph`; `psycopg[binary]>=3.2` jest już obecny (`szczur-radar-api/pyproject.toml:11`) — to psycopg 3, którego wymaga checkpointer Postgres.

**Co mówi dokumentacja (zewnętrzne)**

- Stan: LangGraph 1.x (PyPI 1.2.10, GitHub 1.2.11), wydania mniej więcej co tydzień; 1.2 dodał `TimeoutPolicy` (tylko węzły async), `error_handler` na węźle i drain/resume — https://pypi.org/project/langgraph/, https://docs.langchain.com/oss/python/langgraph/changelog-py.
- Functional API (`@entrypoint`/`@task`) jest rekomendowane dla „linear workflows with simple logic”; Graph API dla rozgałęzień, współdzielonego stanu, równoległości z łączeniem i wizualizacji — https://docs.langchain.com/oss/python/langgraph/choosing-apis.
- Fan-out per post: `Send` z reducerem (`Annotated[list, operator.add]`); `RetryPolicy(retry_on=…)` per węzeł — https://docs.langchain.com/oss/python/langgraph/graph-api, https://docs.langchain.com/oss/python/langgraph/fault-tolerance. Klucz `max_concurrency` w configu **nie został potwierdzony** w pobranej dokumentacji.
- Checkpointer jest opcjonalny. `PostgresSaver` (langgraph-checkpoint-postgres 3.1.2) wymaga psycopg 3 + psycopg-pool, połączeń z `autocommit=True` i `row_factory=dict_row`, tworzy własne tabele przez `.setup()` (poza Alembic), checkpointy trzeba przycinać, a README zaleca `LANGGRAPH_STRICT_MSGPACK` ze względu na deserializację — https://pypi.org/project/langgraph-checkpoint-postgres/. Wznowienie powtarza cały nieudany węzeł (węzły muszą być idempotentne) — https://docs.langchain.com/oss/python/langgraph/durable-execution.
- Testy: graf budowany w fabryce, kompilowany z `MemorySaver` per test; pojedynczy węzeł przez `compiled.nodes[...]` — https://docs.langchain.com/oss/python/langgraph/test. `GenericFakeChatModel` nie implementuje `bind_tools`, więc `with_structured_output` rzuca `NotImplementedError` (issue otwarte w chwili sprawdzenia) — https://github.com/langchain-ai/langchain/issues/36277. Wstrzyknięcie własnej funkcji `assess` omija problem i pasuje do portu z planu (`plan.md:28`).
- Wytyczne ogólne: Anthropic zaleca najprostsze rozwiązanie i bezpośrednie API, ostrzegając, że frameworki zaciemniają prompty i odpowiedzi — https://www.anthropic.com/engineering/building-effective-agents. LangChain pozycjonuje LangGraph dla długotrwałych, stanowych agentów i trwałego wykonania — https://docs.langchain.com/oss/python/langgraph/overview. Nie znaleziono strony LangChain mówiącej, kiedy go nie używać.

**Wniosek porównawczy (inferencja)**

- Przy ok. 406 postach/dzień (`shape-notes.md:406`), braku ponowień semantycznych (`plan.md:39`) i deduplikacji po ID źródła (`prd.md:89`, FR-003) zwykła pętla/asyncio z własną tabelą przebiegów daje wznowienie przez ponowne uruchomienie i pominięcie zapisanych ID. Wartość LangGraph w tym projekcie leży głównie w uzasadnieniu z archiwalnej sesji (przyszłe węzły ML — `…-1137.md:230`) i w spójności z decyzją stackową, nie w potrzebach MVP.
- Ryzyka, których dziś nie retiruje żaden krok planu: (1) czy fan-out ma realny limit współbieżności zgodny z limitami API dostawców; (2) czy `RetryPolicy` da się zawęzić do błędów transportowych, żeby nie łamać „brak ponowień semantycznych”; (3) tabele checkpointera poza Alembic vs reguła „każda migracja z działającym downgrade” (`REPOSITORY_GUIDELINES.md`) i CI z Postgresem; (4) testy offline grafu przy problemie fake modeli; (5) waga zależności i obrazu na Railway; (6) zapis historii przebiegu i provenance z węzłów.

## Code References

- `context/foundation/llm-assessment/contract-v1.md:14-16` — przepływ oceny
- `context/foundation/llm-assessment/contract-v1.md:282` / `:433` — niezmiennik 13 („rozstrzygnięty stance”)
- `context/foundation/llm-assessment/contract-v1.md:420-437` — niezmienniki i zastrzeżenie o JSON Schema
- `context/foundation/llm-assessment/contract-v1.md:443-454` — limity V1
- `context/foundation/llm-assessment/contract-v1.md:518-528` — reguła zmiany §14
- `context/foundation/llm-assessment/agent-implementation.md:87` — retry jako osobna decyzja
- `context/foundation/llm-assessment/agent-implementation.md:91-123` — wymagane fixtures i testy walidatora
- `context/foundation/llm-assessment/agent-implementation.md:186-198` — definicja done warstwy kontraktu
- `context/foundation/llm-assessment/examples.md:157`, `:235`, `:330-336` — przypadki niejednoznaczne z założenia
- `context/foundation/prd.md:48-49` — guardraile jakości i kosztu; `:285` — komentarze poza MVP
- `context/foundation/roadmap.md:47`, `:97`, `:105`, `:222` — F-02 „na prawdziwym modelu”, Unknown dostawcy, ORQ5
- `context/foundation/roadmap.md:134`, `:237` — komentarze odłożone, kontekst rodzica nieużywany w MVP
- `context/foundation/tech-stack-api.md:29` — przyjęcie LangGraph
- `context/foundation/shape-notes.md:385`, `:397`, `:406` — status preferencji LangGraph, wolumen historyczny
- `context/foundation/archive/shape-notes-2026-09-26-1137.md:43`, `:230` — pierwotne uzasadnienie LangGraph
- `context/foundation/infrastructure.md:94`, `:130` — sekret LLM, serwis cron
- `context/changes/assessment-contract-harness/change.md:12` — pierwsza lista 4 dostawców
- `context/changes/assessment-contract-harness/plan.md:13`, `:38`, `:56`, `:58`, `:142-145`, `:248` — graf poza F-02, rozszerzenia kontraktu, adaptery, macierz
- `szczur-radar-api/pyproject.toml:11` — psycopg 3 już w zależnościach

## Architecture Insights

- Kontrakt celowo przerzuca gwarancje na backend (`contract-v1.md:437`); różnice w obsłudze schematów przez dostawców wpływają więc na odsetek odrzuceń, nie na poprawność zapisanych ocen.
- Port dostawcy z planu (`plan.md:139-145`) i funkcja `assess()` są naturalnym szwem zarówno dla testów, jak i dla późniejszego osadzenia w LangGraph lub w zwykłej pętli — decyzja o orkiestratorze nie musi wpływać na kod F-02.
- Reguła §14 jest jedynym sankcjonowanym sposobem zmiany kontraktu; dopowiedzenia z planu (A.1–A.4) są dziś zapisane tylko w planie.

## Historical Context (from prior changes)

- `context/foundation/archive/shape-notes-2026-09-26-1137.md:43`, `:230` — LangGraph jako preferencja, uzasadniona przyszłym ML (twierdzenie nadal aktualne jako *motywacja*; status „niezatwierdzony” z `shape-notes.md:397` został zastąpiony przyjęciem w `tech-stack-api.md:29`).
- `context/changes/assessment-contract-harness/reviews/plan-review.md` — F1 (surowa odpowiedź per dostawca) i F2 (timeouty/ponowienia Gemini) potwierdzone zewnętrznie w tym badaniu; F3 (luka kontraktu blokuje done) dotyczy tych samych miejsc co A.1–A.4.
- `context/archive/2026-09-27-admin-bootstrap-and-login/` — nie dotyczy LLM; nie badano.

## Related Research

Brak innych `research.md` w `context/changes/**` ani `context/archive/**`.

## Open Questions

1. Czy dopowiedzenia A.1–A.4 (mixed, pusty cytat, anchor dla `unresolved`/`ambiguous`, `claim_ids` przy `unclear`) mają trafić do `contract-v1.md` przez §14 przed implementacją, czy dopiero po failing fixtures?
2. Czy `contextual_target` i ścieżka rodzica mają być wymagane na prawdziwym modelu w F-02, skoro MVP nie przetwarza komentarzy (`prd.md:285`)?
3. Ile dostawców: 2 (Gemini + DeepSeek) wystarcza do pokrycia obu mechanizmów structured output; czy porównanie z OpenAI/Anthropic jest dla użytkownika wartością samą w sobie? (decyzja użytkownika; `roadmap.md:105`)
4. Jaki budżet kosztu na ocenę / na dzień (ORQ5, `roadmap.md:222`) — bez niego „wybór modelu domyślnego” nie ma kryterium kosztowego.
5. Gdzie sprawdzić LangGraph: w F-02 (spike obok `assess()`), w S-02 (pierwszy prawdziwy pipeline), czy w osobnej zmianie foundation — i czy checkpointing jest w ogóle potrzebny w MVP?
6. LangChain dla dostawców vs bezpośrednie SDK — do rozstrzygnięcia w planie; niezależne od decyzji o LangGraph, poza wspólnym `langchain-core`.
7. Niezweryfikowane: `max_concurrency` w LangGraph, obsługa `maxLength`/`$defs` przez Gemini, zachowanie DeepSeek strict przy nieobsługiwanych słowach kluczowych, aktualne nazwy modeli i ceny.

---

# Follow-up 2026-10-03: przeplanowanie p2–p4 w Javie z Embabel

**Date**: 2026-10-03T10:05:32+02:00
**Researcher**: Claude (Opus 5.5) dla kwmacioszek
**Git Commit**: 0ee766fa635ebe605f59ddb5194620ffb8219441
**Branch**: feature/assessment-contract-harness-java
**Repository**: szczur-radar

## Research Question (follow-up)

> „assessment-contract-harness — follow-up: przeplanowanie p2–p4 w Javie z Embabel (structured output, zamknięte modele, dostawcy, port reguł kontraktu V2)”

Badanie łączy dowody wewnętrzne (kod Java na tej gałęzi, zarchiwizowana zmiana `java-api-rewrite`, implementacja Pythonowa p2–p4 na gałęzi `feature/assessment-contract-harness_old`) z zewnętrznymi (źródła jarów Embabel 1.5.2 i Spring AI 2.0.1 z Maven Central, bytecode SDK dostawców, dokumentacja DeepSeek). Anchory `PY:` = `feature/assessment-contract-harness_old:szczur-radar-api/` (czytane przez `git show`). Anchory do Embabel/Spring AI to ścieżki w jarach `*-sources.jar` wersji 1.5.2 / 2.0.1. Nie uruchamiano wywołań na żywo ani testów.

## Summary (follow-up)

1. **Punkt wyjścia.** Faza 1 (kontrakt V2, PRD, roadmapa) jest na tej gałęzi (`context/foundation/llm-assessment/contract-v2.md`). Fazy 2–3 i kod fazy 4 (bez danych) istnieją wyłącznie w Pythonie na gałęzi `feature/assessment-contract-harness_old` (PR #23 zamknięty bez merge'a: „implementacja F-02 zostanie wykonana w Javie”); roadmapa mówi, że ten kod „nie jest przenoszony” (`context/foundation/roadmap.md:109`). Jest jednak kompletną, przetestowaną specyfikacją wykonawczą do portu (reguły D1–D15, tabela §7.2, prompt v2.2, odsiew, miary wzorca) i zawiera ustalenia empiryczne, których nie ma w `plan.md` (sekcja „Decyzje tylko w kodzie Pythona”). Danych wzorca (`posts.json`, `labels.json`) nie wytworzono w żadnym commicie.
2. **Structured output w Embabel 1.5.2 nie spełnia wymagań kontraktu, jeśli używać `createObject`.** Dla trzech używanych dostawców (google-genai, deepseek, openai-custom) Embabel dokleja schemat JSON jako tekst do promptu systemowego (natywny tryb nie jest włączony), parsuje odpowiedź pobłażliwie (usuwa ```` ```json ````, akceptuje przecinki końcowe, pojedyncze cudzysłowy, komentarze), domyślnie ponawia całe wywołanie do 10 razy przy każdym wyjątku, w tym błędzie parsowania (ponowienia semantyczne, zakazane przez plan), dokleja przykład i — dla DeepSeek i Gemini — linię „Knowledge cutoff”. `Usage` Embabel nie ma tokenów cache ani czasu wywołania. Wniosek: `createObject` nadaje się co najwyżej do prototypu; do oceny zgodnej z kontraktem potrzebne jest wywołanie poniżej warstwy `createObject` (Spring AI `ChatModel.call(Prompt)` z opcjami dostawcy), a Embabel pozostaje rejestrem modeli/powłoką `@Action`.
3. **Ponowienia są wielowarstwowe i domyślnie włączone** (data-binding Embabel 10×, `RetryTemplate` modelu: Gemini 10×, DeepSeek 4×, wewnętrzne SDK: google-genai 5×, openai-java 2×). Dla Gemini sam iloczyn `RetryTemplate` modelu (10) i SDK (5) daje do 50 żądań HTTP na jedno wywołanie modelu, a przy `createObject` warstwa data-binding (10) mnoży to dalej (inferencja z iloczynu ustawień domyślnych, nie zmierzona). Plan wymaga „jedynie 3 prób transportowych w adapterze” — w Javie oznacza to budowę własnych klientów/`ChatModel` z wyłączonymi ponowieniami albo jawne ustawienia wszystkich warstw.
4. **Różnice dostawców przenoszą się na Javę, ale w innym miejscu.** Gemini: Spring AI przekazuje `minItems/maxItems` do `responseJsonSchema` (pułapka HTTP 400 z pilota wraca), a gubi `additionalProperties`; brak timeoutu klienta w konfiguracji Embabel. DeepSeek: Spring AI ma tylko `JSON_OBJECT` (bez schematu) — co zgadza się z pythonowym trybem domyślnym (JSON mode + schemat w prompcie); `DeepSeekApi.Usage` gubi `prompt_cache_hit_tokens`. OpenRouter (openai-java): `OpenAiChatOptions.extraBody` pozwala wysłać `provider.only=["openai/flex"]`, `responseFormat` JSON_SCHEMA ma domyślnie `strict=true`, tokeny cache są w `getCacheReadInputTokens()` — ale przez Embabel `extraBody`/`serviceTier` nie da się ustawić.
5. **Zamknięte modele w Javie wymagają świadomej konfiguracji.** Jackson 3.1.5 ma domyślnie `FAIL_ON_UNKNOWN_PROPERTIES=false`, `FAIL_ON_MISSING_CREATOR_PROPERTIES=false`, `FAIL_ON_NUMBERS_FOR_ENUMS=false`, dopuszcza koercję skalarów; `@Size` liczy jednostki UTF-16, a kontrakt liczy code pointy; kontrakt definiuje biały znak przez Pythonowe `str.isspace()` (`contract-v2.md:290`), a `Character.isWhitespace` nie obejmuje U+00A0/U+2007/U+202F. Ścisła walidacja wymaga własnego `ObjectMapper` (lub walidatora JSON Schema networknt, już na classpath) i własnych sprawdzeń długości.
6. **Pomiar trzech dostawców koliduje z „jeden dostawca na uruchomienie”.** Obecna konfiguracja (`application.properties:36-46`, profile `application-<provider>.properties`) włącza jedną autokonfigurację; `AgentSkeletonIT` sprawdza, że rejestrowane są tylko modele wybranego dostawcy. Pomiar wymaga albo trzech przebiegów z przełączeniem `LLM_*`, albo osobnej ścieżki budującej klientów spoza autokonfiguracji (z kluczami trzech dostawców). To decyzja do planu.

## Detailed Findings (follow-up)

### D. Stan wyjściowy i co jest do przeniesienia

- Fazy na gałęzi Pythonowej (`git log main..feature/assessment-contract-harness_old`): p1 `35da4ee`, p2 `e0ddf27`, p3 `403335b` + `a5929ee` (prompt v2.2), refaktor warstw `b721eb7`, p4 kod i arkusz bez danych `f358ac1`, refaktory walidatora `4e68531`, `d7b904f`, łańcuch kroków `645333f`. Różnica `plan.md` między gałęziami: tylko odhaczone 1.1–3.11 z SHA.
- PR #23 zamknięty z komentarzem „Zamknięte bez merge'a: implementacja F-02 zostanie wykonana w Javie po java-api-rewrite”. Archiwum F-03: „kod F-02 z zamkniętego PR #23 nie jest przenoszony” (`context/archive/2026-10-01-java-api-rewrite/research.md:25,27`); Embabel jest „pod F-02”, logika F-02 poza F-03 (`…/plan.md:5,43,378`).
- **Modele kontraktu** (`PY:app/agents/assessor/models/`): wejście lax + `extra=forbid`, wyjście `strict` + `extra=forbid`; wszystkie klucze wymagane, bez wartości domyślnych; `CONTRACT_VERSION="V2"` (`models/__init__.py:49`). Limity: targets ≤12, claims ≤24, `anchor.quote` 1..100, `evidence.quote` 1..320, `statement` 1..240, `stance.claim_ids` ≤4, `claim.target_ids` ≤12, id min 1; modifiers/flags bez powtórzeń. Test `"🚀"*limit` przechodzi, `limit+1` nie (`tests/agents/assessor/test_contract.py:179`). Asymetria: `Item` wymaga `null` zamiast tekstu z samych białych znaków (`item.py:17-26`), `Parent` tylko „co najmniej jedno pole z tekstem”.
- **Kolejność pól (ustalenie empiryczne, tylko w kodzie):** żaden obiekt nie kończy się obiektem zagnieżdżonym, bo DeepSeek gubił nawiasy — `Target`: `id, kind, anchor, stance, reference`; `Claim`: `id, kind, attribution, target_ids, evidence, statement` (`models/target.py:11-14`, `app/agents/CLAUDE.md:24`, test `tests/llm/test_providers.py:131`; commit `403335b`). Przykład w `contract-v2.md` §3 ma inną kolejność; kontrakt nie mówi, że kolejność kluczy ma znaczenie.
- **Potok walidacji:** `JsonDecoder` (pusty tekst / błąd parsowania → `invalid_structured_output`, ścieżka `$`, płotki markdown odrzucane — `test_validation.py:484`) → `SchemaValidator` (każdy błąd → `schema_violation`, ścieżki `$.claims[0].id`) → reguły D1–D15 w ustalonej kolejności (`validation/rules/__init__.py:19-37`), klasa wyniku = pierwsza obecna w porządku `invalid_reference → fabricated_evidence → domain_invariant_violation`, inaczej klasa pierwszego naruszenia (`violation.py`).
- **Reguły D1–D15** (pełna tabela: id, klasa, ścieżka, komunikat) są w `PY:app/agents/assessor/validation/rules/*.py`; D5→D7→D6 to jedna reguła cytatu (brak pola → `invalid_reference`; pusty po normalizacji → `domain_invariant_violation`; nie-podciąg → `fabricated_evidence`); kolejność iteracji: najpierw wszystkie evidence, potem anchory (`validation/context.py:40-45`). Tabela §7.2 jako dane (`rules/stance_table.py:22-29`): bullish/bearish/neutral — assertiveness wymagane, claim_ids wymagane, claim autora wymagany; mixed — null / wymagane / wymagany; unclear — null / opcjonalne / nie; not_expressed — null / zabronione / nie. Komunikaty używają Pythonowego `repr` (`'t1'`) — parzystość komunikatów w Javie nie jest wymagana przez kontrakt.
- **Odsiew** (`PY:app/agents/assessor/prefilter.py`): regex `https?://\S*|<url>|#\w+|@\w+` (URL przed tagiem), usunięcie z tytułu i treści, `strip()`, łączna długość ≥3 code pointy; wynik bez modelu `{"relevance":"unclear","modifiers":[],"targets":[],"claims":[],"flags":["insufficient_content"]}`. Test `"#gielda 🚀🚀🚀"` jest ocenialny (`test_prefilter.py:31-50`).
- **Wynik i pochodzenie** (`result.py`, `provenance.py`): `Provenance` ma względem planu dodatkowe pole `output_mode` (tryb DeepSeek); `prefilter()` → `attempts=0`; `channel` = nazwa dostawcy lub `openrouter`.
- **Prompt** (`PY:app/agents/assessor/prompt.py`): `PROMPT_VERSION="v2.2"`; wiadomość użytkownika = stały prefiks + kompaktowy JSON wejścia w kolejności deklaracji, bez escapowania znaków spoza ASCII, czas UTC jako `Z`. Dla DeepSeek w trybie JSON schemat doklejany do promptu systemowego.
- **Warstwa dostawców** (`PY:app/llm/providers/base.py:120-143,286-354`): uproszczenie schematu (inline `$ref`, usunięcie `title` poza korzeniem; Gemini bez `minItems/maxItems`, DeepSeek i OpenAI także bez `minLength/maxLength`); pętla ponowień: 429/5xx/połączenie/timeout, do 3 prób na tryb, `retry-after` (sekundy, max 60 s) albo `min(2^(n-1), 30)` s; po 3 odmowach 429 w flex przejście na tryb standardowy (łącznie do 6 prób); odmowa modelu → `provider_error` bez ponowień. Gemini `max_retries=1`, bo w SDK Google 0 oznacza „domyślne 5” (ustalenie empiryczne).
- **DeepSeek — tryb JSON jako domyślny (decyzja tylko w kodzie, plan mówi o strict function calling: `plan.md:248`):** docstring `deepseek.py` — smoke 2026-10-01, 30 postów: json 96,7%, tool_strict 83,3%, tool 83,3%; commit `a5929ee` (prompt v2.2): Gemini 100%, DeepSeek JSON 93,3%, Luna flex 96,7%. Nagrania z tych przebiegów nie są w repo, więc liczb nie da się odtworzyć.
- **Faza 4** (`PY:app/sources/wykop/mapper.py`, `evaluation.py`, `scripts/fetch_wykop_gold.py`, `tests/agents/assessor/test_gold.py`): mapowanie `entry→post`, `link→link`, czas Wykopu jako Europe/Warsaw; miary i progi 0,95/0,80/0,80/0,70/0,75/0,75/≤0,03; `AnchorNormalizer` używa `casefold()` (≠ `toLowerCase`), dopasowanie granic `(?<!\w)…(?!\w)` z Unicode `\w`; skojarzenie celów przez ścieżki powiększające. `pilot/labeling-gold.html` jest niezależny od Pythona (localStorage, wczytuje `posts.json`, eksportuje `labels.json`) i jest na tej gałęzi.
- **Dane wielokrotnego użytku bez zmian:** `PY:tests/agents/assessor/fixtures/real_posts.json` (12 rekordów z prawdziwymi U+00A0 — kopiować bajt w bajt), przykłady 1–7 z `examples.md`, `pilot/wykop-gielda-sample-2026-09-29.json`.

### E. Embabel 1.5.2 — co daje, a co przeszkadza

- **API wywołania (zweryfikowane w źródłach):** `OperationContext.ai()` → `Ai.withLlm(LlmOptions|String)` → `PromptRunner` (`withSystemPrompt`, `withGenerateExamples`, `withToolLoopInspectors`, `creating(Class).withoutValidation()`), `createObject(String|List<Message>, Class)`, `generateText` = `createObject(…, String.class)` (`embabel-agent-api:…/api/common/{Ai,PromptRunner,PromptRunnerOperations}.kt`). `LlmOptions.withModel(name).withTemperature(…).withTimeout(…)` (`embabel-agent-ai:…/LlmOptions.kt:150,197,229,337`).
- **Schemat:** generowany przez victools (draft 2020-12, `FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT`) w `JacksonOutputConverter` i **doklejany do promptu systemowego jako tekst** (`ToolLoopLlmOperations.buildInitialMessages`, ~:754-790). Natywny structured output istnieje (`nativeStructuredOutputSupport.kt`), ale dla google-genai, deepseek i openai-custom `SpringAiLlmService` jest budowany bez wsparcia natywnego, a polityka i tak odrzuca schematy z tablicą obiektów (issue #1946, zamknięte bez zmiany). Nie ma publicznego sposobu podania własnego schematu.
- **Parser pobłażliwy:** `JacksonOutputConverter.convert` usuwa płotki i akceptuje m.in. przecinki końcowe, pojedyncze cudzysłowy, komentarze. Sukces `createObject` nie oznacza, że surowy tekst przeszedłby nasz ścisły walidator. Błąd parsowania → `InvalidLlmReturnFormatException(llmReturn, …)` z surowym tekstem tylko ostatniej próby.
- **Ponowienia semantyczne domyślnie włączone:** `LlmDataBindingProperties` (`embabel-agent-api:…/spi/support/LlmDataBindingProperties.kt:44`) `max-attempts=10`, ponawia całe wywołanie przy dowolnym wyjątku poza wąskim zbiorem; jedyną kontrolą jest `embabel.agent.platform.llm-operations.data-binding.max-attempts=1` (wyłącza także ponowienia transportowe na tym poziomie). Walidacja Jakarta dodaje jedno ponowne zapytanie z informacją o naruszeniach — wyłączane przez `withoutValidation()` lub brak adnotacji.
- **Ponowienia modelu i SDK:** `GoogleGenAiModelsConfig.kt:90` `maxAttempts=10` (5 s ×5 do 180 s); DeepSeek 4 (1,5 s ×2 do 60 s); openai-custom ma właściwość, ale fabryka jej nie używa. SDK: google-genai 1.65.0 `RetryInterceptor` 5 prób na 408/429/5xx, bez `Retry-After`; openai-java 4.49.0 `maxRetries=2`, honoruje `Retry-After(-Ms)`. Żadnego z ustawień SDK nie da się zmienić przez właściwości Embabel.
- **Timeouty:** `LlmOptions.timeout` albo `…prompts.default-timeout` (60 s) przez `Asyncer` + `cancel(true)` (żądanie HTTP może trwać dalej); klient Gemini budowany przez Embabel nie ma `HttpOptions.timeout` (nieskończony); openai-java w Spring AI stosuje per-request `chatOptions.getTimeout()` (domyślnie 60 s).
- **Zużycie:** `core/Usage.kt` ma `promptTokens`, `completionTokens`, `nativeUsage` — bez tokenów cache i bez czasu (`LlmInvocation.runningTime = Duration.ZERO`). Tokeny cache tylko z `nativeUsage` per dostawca. Hak `withToolLoopInspectors(...).afterLlmCall` daje surowy tekst i usage przed parsowaniem (raz na próbę).
- **Wybór modelu:** wszystkie beany `LlmService` w kontekście są wybieralne po nazwie (`LlmOptions.withModel`). Inferencja (nieuruchomiona): trzy autokonfiguracje mogą działać jednocześnie, bo mają rozłączne prefiksy; dziś każdy profil nadpisuje całą listę `spring.autoconfigure.exclude`. Gemini rejestruje modele z listy YAML (`google-genai-models.yml`, domyślna temperatura 0,7); DeepSeek — cztery stałe beany (`deepseek-chat`, `deepseek-reasoner`, `deepseek-v4-flash`, `deepseek-v4-pro`; też `application-deepseek.properties:1-2`); openai-custom — lista z `…openai.custom.models`.
- **Brak `extraBody`/`serviceTier` przez Embabel:** konwertery opcji mapują tylko temperaturę, topP, maxTokens i kary; jedyny dodatkowy kanał to nagłówki HTTP. Spring AI `OpenAiChatOptions` ma oba pola — wymagają własnego `OpenAiChatModel` (np. zarejestrowanego jako `SpringAiLlmService`) albo wywołania `ChatModel` bezpośrednio.
- **Ścieżka niższego poziomu w Embabel:** `ModelProvider.getLlm(ByNameModelSelectionCriteria)` → `SpringAiLlmService` → `createMessageSender(LlmOptions).call(...)` (bez data-binding retry i bez doklejania schematu) albo `.getChatModel().call(Prompt)` z opcjami dostawcy (pełny surowy tekst, natywne `Usage` Spring AI). Zostają ponowienia modelu i SDK.
- **Testy:** `FakeOperationContext`/`FakePromptRunner` (`com.embabel.agent.test.unit`, w `embabel-agent-api`) zwracają gotowe obiekty z pominięciem parsowania — nie testują ścieżki surowego tekstu. `EmbabelMockitoIntegrationTest` (`embabel-agent-test`) mockuje `LlmOperations`. Reguła repo o nagranych prawdziwych payloadach (`REPOSITORY_GUIDELINES.md:34`) wskazuje na stub poniżej Embabel (poziom `ChatModel` lub HTTP).
- **Znane problemy:** #1813 (otwarte) — Gemini z `includeThoughts` zwraca tekst myśli zamiast JSON; #2028 (otwarte) — opisy stałych enum gubione w schemacie; #1870 — `createObjectIfPossible` połykało błędy infrastruktury (nie używać do pomiaru). Dokumentacja online (`https://docs.embabel.com/llms.txt`) opisuje 1.5.3-SNAPSHOT, nie 1.5.2.

### F. Spring AI 2.0.1 per dostawca (stos zweryfikowany `./mvnw dependency:tree`)

- **Stos:** Spring AI 2.0.1 przez Embabel; `google-genai:1.65.0`; `openai-java-core/okhttp:4.49.0` (Spring AI 2.0 OpenAI używa oficjalnego SDK); Jackson 3 `tools.jackson.core:jackson-databind:3.1.5` (Jackson 2.21.5 też obecny tranzytywnie); victools `jsonschema-generator` 5.0.0; networknt `json-schema-validator:3.0.1` (przez `spring-ai-client-chat`); Hibernate Validator 9.0.1. Brak `spring-ai-autoconfigure-retry` i `spring-ai-autoconfigure-model-*`, więc `spring.ai.retry.*` nie działa w tej aplikacji.
- **Gemini** (`GoogleGenAiChatOptions`, `GoogleGenAiChatModel`): `outputSchema(json)` ustawia `responseSchema` i `application/json`; schemat trafia do `responseJsonSchema` przez deserializację do `com.google.genai.types.Schema` — zachowane m.in. `anyOf`, `enum`, `min/maxItems`, `min/maxLength`, `required`, `propertyOrdering`; po cichu gubione `additionalProperties`, `$defs/$ref`, `const`, `oneOf/allOf`. Usage: `getCacheReadInputTokens()` = `cachedContentTokenCount`; `getCompletionTokens` nie obejmuje `thoughtsTokenCount`. Każda część odpowiedzi to osobna `Generation` z flagą `isThought`. Wszystkie wyjątki SDK opakowane w `RuntimeException("Failed to generate content")` — Embabel traktuje je jako ponawialne, także 400 (inferencja wysokiej pewności).
- **DeepSeek** (`DeepSeekChatOptions`, `DeepSeekApi`): `ResponseFormat` tylko `TEXT`/`JSON_OBJECT`; nie implementuje `StructuredOutputChatOptions`; myślenie wyłączane przez `thinking(Thinking.DISABLED)` (domyślnie włączone w API). Strict tools wymagają `baseUrl=…/beta` dla wszystkich wywołań. `Usage` ma tylko `prompt_tokens_details.cached_tokens` i ignoruje `prompt_cache_hit_tokens/miss_tokens` z dokumentacji DeepSeek — trafienia cache są gubione. 4xx (w tym 429) → `NonTransientAiException`, 5xx → `TransientAiException`. Rozjazd nazw: dokumentacja DeepSeek podaje `deepseek-flash`, Spring AI/Embabel `deepseek-v4-flash` — do sprawdzenia na żywo.
- **OpenRouter przez openai-java** (`OpenAiChatOptions`): `responseFormat` JSON_SCHEMA (nazwa `json_schema`, `strict` domyślnie `true`), `serviceTier`, `reasoningEffort`, `extraBody` (→ `additionalBodyProperties`, zagnieżdżone mapy serializują się, więc `provider: {only: ["openai/flex"], allow_fallbacks: false}` jest możliwe), `timeout` (domyślnie 60 s), `maxRetries` (domyślnie 3 przy kliencie budowanym przez Spring AI; SDK 2). Usage: cache z `promptTokensDetails().cachedTokens()`. Niepotwierdzone: czy OpenRouter przekazuje `service_tier`.
- **Surowa odpowiedź:** `model.call(new Prompt(msgs, options)).getResult().getOutput().getText()`; `getMetadata().getUsage()` (prompt, completion, cache read, native); `getMetadata().getModel()`.
- **Schemat ze Spring AI `JsonSchemaGenerator`:** `additionalProperties:false` rekurencyjnie, wszystkie pola wymagane — z pułapką: `@JsonProperty("x")` bez `required=true` i `@Nullable` czynią pole opcjonalnym; honoruje `@JsonPropertyOrder`; **nie** honoruje Jakarta `@Size/@NotNull` (honoruje Swagger `@Schema`). `BeanOutputConverter.convert` jest pobłażliwy (wyłącza `FAIL_ON_UNKNOWN_PROPERTIES`).

### G. Zamknięte modele i reguły tekstowe w Javie

- **Jackson 3.1.5 — wartości domyślne (zweryfikowane w źródłach):** `FAIL_ON_UNKNOWN_PROPERTIES=false`, `FAIL_ON_MISSING_CREATOR_PROPERTIES=false`, `FAIL_ON_NULL_CREATOR_PROPERTIES=false`, `FAIL_ON_TRAILING_TOKENS=true`, `FAIL_ON_NUMBERS_FOR_ENUMS=false`, `ALLOW_COERCION_OF_SCALARS=true`. Ścisły mapper do walidacji wymaga jawnego włączenia/wyłączenia tych cech (inferencja, nie wykonano). Ustawienie `spring.jackson.deserialization.fail-on-unknown-properties=true` (`application.properties:33-34`) dotyczy mappera Boota; czy używa go Embabel — niezweryfikowane.
- **Długości:** `SizeValidatorForCharSequence` (Hibernate Validator 9.0.1) liczy `length()` (UTF-16); `"Zażółć 🐀"` → `length()=9`, `codePointCount=8` (jshell). Kontrakt i JSON Schema `maxLength` liczą code pointy.
- **Białe znaki:** `contract-v2.md:290` definiuje biały znak jako Pythonowe `str.isspace()`. `Character.isWhitespace` pomija U+00A0, U+2007, U+202F; `Character.isSpaceChar` pomija `\t`, `\n`, `\r`. Zbiór Pythona to klasy bidi WS/B/S + kategoria Zs (m.in. `\x1c–\x1f`, `\x85`, U+1680, U+2000–200A, U+2028/9, U+205F, U+3000). Archiwum F-03 już to zgłosiło (`context/archive/2026-10-01-java-api-rewrite/research.md:175`). Wektory testowe: `PY:tests/agents/assessor/test_validation.py:73-89`.
- **Regex:** Java `\w` wymaga `Pattern.UNICODE_CHARACTER_CLASS`, a i wtedy obejmuje znaki łączące (Mn/Mc/Me), których Pythonowe `\w` nie obejmuje; `casefold()` ≠ `toLowerCase(Locale.ROOT)` (np. ß).
- **Konwencje kodu:** rekordy dla typów wartości, enumy z `@JsonValue` małymi literami dla zamkniętych zbiorów (`user/Role.java:18-29`, `config/ErrorCode.java:47-50`; reguła `REPOSITORY_GUIDELINES.md:16` i lekcja o enumach), jspecify `@Nullable` punktowo bez `@NullMarked`, package-by-feature; README zapowiada moduły `llm` i `sources` obok `agents` „bez mieszania infrastruktury z domeną” (`szczur-radar-api/README.md:25`).

### H. Testy, CI i konfiguracja w Javie

- `*Test` (Surefire, bez Springa i Dockera) vs `*IT` (Failsafe, Spring + Testcontainers) (`pom.xml:145-174`). Każdy kontekst Springa wymaga prawdziwych `LLM_API_PROVIDER/LLM_MODEL/LLM_API_KEY` (`LlmConfigurationCheck`, `AgentSkeletonIT.java:27-34`) — kryteria planu „import bez zmiennych środowiskowych” mają w Javie sens tylko dla `*Test`.
- `LlmProviderConfigTest` testuje ścieżkę OpenRouter lokalnym `HttpServer` + SDK OpenAI bez sieci (`LlmProviderConfigTest.java:72-110`) — gotowy wzorzec testu adaptera na nagranym payloadzie.
- Folder `src/test/resources/fixtures/<service>/` wymagany przez `REPOSITORY_GUIDELINES.md:34` jeszcze nie istnieje.
- CI: job `backend` wymaga ustawień LLM i deklaruje „nothing calls the LLM in CI” (`.github/workflows/ci.yml:26-43`); przebieg na żywo musi pozostać poza CI (odpowiednik markera `live`: np. osobna klasa/tag JUnit wyłączona w Surefire/Failsafe — do decyzji w planie).
- Railway: brak usługi pipeline/cron (`.railway/railway.ts:120-122`); `LLM_*` tylko w API (`:94-100`).

### I. Nieaktualne sformułowania do poprawy w planie i dokumentach

- `plan.md`: wszystkie odwołania do `uv`, `pytest`, Pydantic, LangChain, `app/assessment/*`, `pyproject.toml`/markera `live`, `init_chat_model`, `ChatOpenAI`, specyfikacji `provider:model` z prefiksami `google_genai/deepseek/openai`, kluczy `GOOGLE_API_KEY/DEEPSEEK_API_KEY/OPENAI_API_KEY` oraz zdań „brak integracji z LLM” i „sekrety LLM w CI poza zakresem” (m.in. `plan.md:7,11-13,28-32,38-39,48,61,66-84`, faza 2 `:171-220`, faza 3 `:230-300`, faza 4 `:314-344`, faza 5 `:368-410`, `:430-457`). `plan-brief.md:13,17,31,45,49,53,57,65,69,76-77`.
- `agent-implementation.md:13` („Pydantic, `extra="forbid"`”), `:18` (`normalize_whitespace`), `:23` (bezpośredni OpenAI — w Javie OpenAI tylko przez OpenRouter), `:143-144` (literały Pythona).
- `contract-v2.md:288-290` — definicja białych znaków przez Pythona (normatywna; wymaga definicji neutralnej językowo albo jawnej listy znaków — zmiana kontraktu przez regułę zmiany).
- `plan.md:248` vs kod: DeepSeek w trybie JSON (nie strict function calling) — ustalenie empiryczne do przeniesienia do planu.

## Code References (follow-up)

- `szczur-radar-api/src/main/resources/application.properties:36-46` — jeden dostawca na uruchomienie, `default-llm=${LLM_MODEL}`, wykluczenia autokonfiguracji
- `szczur-radar-api/src/main/resources/application-{gemini,deepseek,openrouter}.properties` — mapowanie `LLM_*` na Embabel; nazwy modeli DeepSeek
- `szczur-radar-api/src/main/java/pl/szczurradar/api/config/LlmProviderProperties.java:26-89` — enum dostawców, walidacja trójki `LLM_*`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/SkeletonAgent.java:14-29` — szkielet `@Agent/@Action` do zastąpienia
- `szczur-radar-api/src/test/java/pl/szczurradar/api/agents/AgentSkeletonIT.java:49-78` — asercje rejestracji modeli i brak wywołań LLM
- `szczur-radar-api/src/test/java/pl/szczurradar/api/config/LlmProviderConfigTest.java:72-110` — test OpenRouter na lokalnym serwerze HTTP
- `szczur-radar-api/pom.xml:20-23,87-107` — wersje Embabel/Spring AI, startery dostawców
- `.github/workflows/ci.yml:26-52` — LLM w CI tylko jako konfiguracja
- `context/foundation/llm-assessment/contract-v2.md:284-292` — normalizacja przez Python `str.isspace()`
- `context/foundation/roadmap.md:101-114` — F-02: zamknięte modele, trzy kanały pomiaru, blokada „przeplanowanie p2–p4 w Javie”
- `PY:app/agents/assessor/validation/rules/__init__.py:19-37`, `rules/stance_table.py:22-29` — kolejność reguł D1–D15, tabela §7.2
- `PY:app/llm/providers/base.py:120-143,286-354` — uproszczenie schematu, pętla ponowień i flex
- `PY:app/agents/assessor/prompt.py` — prompt v2.2
- `embabel-agent-api-1.5.2:…/spi/support/LlmDataBindingProperties.kt:44` — `maxAttempts=10`
- `embabel-agent-google-genai-autoconfigure-1.5.2:…/GoogleGenAiModelsConfig.kt:90` — `maxAttempts=10`

## Architecture Insights (follow-up)

- Kontrakt przerzuca gwarancje na backend; w Javie oznacza to, że **żadna warstwa frameworka nie może parsować ani poprawiać odpowiedzi przed naszym walidatorem**. `createObject` łamie to w trzech miejscach (pobłażliwy parser, ponowienia przy błędzie parsowania, doklejony schemat/przykład zmieniający prompt) — stąd potrzeba ścieżki „surowy tekst”.
- Naturalny podział (inferencja): `llm` — infrastruktura dostawców (budowa `ChatModel` per dostawca z wyłączonymi ponowieniami, uproszczenie schematu per dostawca, pętla prób transportowych, odczyt usage/cache, enum dostawców); `agents/assessor` — kontrakt (rekordy + enumy), ścisły parser, reguły D1–D15, odsiew, prompt, `prepare/evaluate/assess`; `sources/wykop` — mapper. Embabel jako powłoka `@Agent/@Action` dla przyszłego pipeline'u S-02 i rejestr nazw modeli; sam akt oceny nie korzysta z `createObject`.
- Pythonowy refaktor (reguła na klasę, tabela §7.2 jako dane, łańcuch kroków, parzystość werdyktów) przekłada się wprost na Javę (interfejs reguły, `enum`/`record` dla tabeli, `sealed` wynik) — to wybór projektowy dla planu, nie wymóg kontraktu.

## Historical Context (follow-up)

- `context/archive/2026-10-01-java-api-rewrite/research.md:207-220` — Embabel zamiast LangGraph; `createObject` i „czy da się wymusić zamknięte modele — do sprawdzenia przy F-02” (**rozstrzygnięte tutaj: nie przez `createObject`**); rekomendacja deterministycznego łańcucha `@Action` (nadal aktualna).
- `context/archive/2026-10-01-java-api-rewrite/research.md:175` — problem `str.isspace()` vs Java (nadal aktualny, nierozwiązany w kontrakcie).
- `context/archive/2026-10-01-java-api-rewrite/reviews/plan-review.md:94-104` (F5) — wybór trójki `LLM_*` i jednego dostawcy na uruchomienie; Luna przez OpenRouter. Brak zapisanego uzasadnienia rezygnacji z bezpośredniego OpenAI poza usunięciem `OPENAI_API_KEY` (luka).
- `context/archive/2026-10-01-java-api-rewrite/plan.md:38` — „Default LLM … not found” (issue #1358) — powód wymagania prawdziwego klucza w testach.
- Sekcje B i C tego dokumentu (LangChain, LangGraph) — **zastąpione dla implementacji** przez decyzję F-03 (Embabel); ustalenia o API dostawców (Gemini `minItems`, DeepSeek strict/thinking) **nadal aktualne** i potwierdzone w Spring AI (sekcja F).

## Related Research

- `context/archive/2026-10-01-java-api-rewrite/research.md` — badanie przepisania na Javę i wyboru Embabel.

## Open Questions (follow-up)

1. **Ścieżka wywołania:** `createObject` Embabel (prostsze, ale pobłażliwe i z ponowieniami semantycznymi) vs `SpringAiLlmService.getChatModel().call(Prompt)` / własne `ChatModel` z opcjami dostawcy (surowy tekst, natywny schemat, kontrola ponowień)? Dowody wskazują na drugą opcję; decyzja w planie.
2. **Pomiar trzech dostawców:** trzy przebiegi z przełączaniem `LLM_*` (zgodne z obecną konfiguracją) czy osobna ścieżka pomiarowa z trzema kluczami naraz (wymaga zmiany profili i asercji `AgentSkeletonIT`)?
3. **Luna flex:** czy pierwszy wybór pozostaje „Luna przez OpenRouter, `provider.only=["openai/flex"]`” — co wymaga własnego `OpenAiChatModel` z `extraBody`, bo Embabel tego nie przekazuje?
4. **Kontrakt §10:** zastąpić definicję przez Pythonowe `str.isspace()` jawną listą znaków (zmiana kontraktu przez regułę zmiany) czy odtworzyć zbiór Pythona w Javie?
5. **Port czy przepisanie:** przenieść 1:1 strukturę z gałęzi Pythonowej (reguły, łańcuch kroków, testy jako wektory) mimo zapisu „kod nie jest przenoszony” (`roadmap.md:109`), czy zaprojektować od nowa i użyć Pythona tylko jako specyfikacji/wektorów testowych?
6. **Zakres fazy 4:** skrypt pobrania próbki z Wykopu w Javie (komenda CLI?) czy pozostawić Pythonowy skrypt jako narzędzie jednorazowe (wymaga Pythonowego `is_assessable` — rozjazd definicji)?
7. **Niezweryfikowane:** jednoczesne działanie trzech autokonfiguracji; przekazywanie `service_tier` przez OpenRouter; obecna nazwa modelu DeepSeek Flash (`deepseek-flash` vs `deepseek-v4-flash`); który `ObjectMapper` używa Embabel; aktualność procentów ze smoke (brak nagrań w repo).
