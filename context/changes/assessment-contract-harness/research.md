---
date: 2026-09-29T22:33:03+02:00
researcher: Claude (Opus 5.5) dla kwmacioszek
git_commit: b2e26c89a16e047fd9cdbd239ce873c2de907092
branch: chore/archive-s01
repository: szczur-radar
topic: "Kontrakt input/output LLM V1, liczba dostawców (2 zamiast 3–4?) i LangGraph — dowody przed ponownym ramowaniem F-02"
tags: [research, llm-assessment, contract-v1, providers, gemini, deepseek, langchain, langgraph, assessment-contract-harness]
status: complete
last_updated: 2026-09-29
last_updated_by: Claude (Opus 5.5)
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
