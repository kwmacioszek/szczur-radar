# Awaria dostawcy LLM ani błąd naszego kodu nie zabija postów (S-09) — Plan Brief

> Full plan: `context/changes/attempt-accounting/plan.md`

## What & Why

Dziś „próba” to każdy zapisany wynik `failed`. Nie odróżnia więc winy posta od winy dostawcy ani od winy naszego kodu (audyt, Architecture Insights). Skutki:

- 20 minut awarii OpenRouter zużywa próbę każdemu ocenianemu postowi, a trzy takie noce robią z niego `dead` (B2);
- wolny flex z timeoutami zużywa próby zamiast przejść na standard (B3);
- wyjątek SDK w kliencie udaje odmowę modelu (B9);
- post z błędem w naszym kodzie wraca co noc bez końca (A1).

Plan rozdziela te trzy winy, żeby nic poza samym postem nie zabijało posta i żeby nic nie wracało w nieskończoność.

## Starting Point

- `PostTransitions.Outcome.after(boolean ok, …)` liczy próbę przy każdym zapisanym `failed`; trzecia daje `dead`.
- `TransportRetry` rzuca ten sam `LlmCallException` po awarii i po odmowie.
- `TransportException.ofThrowable` zamienia nieznany wyjątek na `REJECTED`.
- Błąd techniczny zwalnia post bez żadnego licznika.
- Jedyny wzorzec „nie zużywaj prób, zatrzymaj przebieg” to `LlmConfigurationException`.

## Desired End State

- **Awaria dostawcy:** po 3 kolejnych wywołaniach zakończonych awarią przebieg kończy się `failed` („the LLM provider is unavailable …”), wysyła powiadomienie, a cron wychodzi z kodem 1. Żaden post nie zyskuje próby ani błędu technicznego. Pojedyncza awaria to błąd techniczny posta (`partial`).
- **Flex** przechodzi na standard przy każdej mieszance błędów ponawialnych.
- **Wyjątek SDK** wychodzi jako błąd techniczny, a nie jako `failed provider_error`.
- **Trzy błędy techniczne z rzędu** wstrzymują post: RETRY go nie bierze, a lista „Nieudane posty” w `/admin` pokazuje go z ostatnim błędem i pozwala go ponowić.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Pojedyncza awaria na poście | Błąd techniczny posta (bez próby, +1 do licznika technicznego) | Jeden licznik na „nie wina treści”, a deterministyczny 5xx na jednym poście też ma limit | Plan |
| Bezpiecznik | 3 kolejne awarie (wyczerpane ponowienia), stała w kodzie | Przy ~2% zwykłych porażek trzy z rzędu to prawie na pewno awaria; staje po ~9 żądaniach HTTP | Plan |
| Zakres bezpiecznika | Tylko awarie (5xx/timeout/połączenie/429 po ponowieniach), odmowy nie | Odmowa to wina posta albo żądania, nie dostępności | Plan |
| Posty z serii, która zatrzymała przebieg | Niczego nie liczą; awarie dolicza się dopiero na końcu kroku, gdy przebieg się nie zatrzymał | Awaria dostawcy nigdy nie dotyka stanu postów, także przy wielodniowej awarii | Plan |
| Limit błędów technicznych | Wstrzymanie po 3 z rzędu, odwracalne przez admina; zapisany wynik zeruje licznik | `dead` byłby nieodwracalny, a po naprawie błędu w kodzie post ma się dać odzyskać | Plan |
| Fallback flex | Po każdym wyczerpanym tierze flex, bez względu na mieszankę | Wolny flex przestaje zużywać próby; `maxCallDuration` już liczy dwa tiery | Plan (B3 z audytu) |
| Wyjątek SDK w kliencie | Wychodzi jako `IllegalStateException` (spoza `LlmCallException`) z oczyszczonym komunikatem i oryginałem jako przyczyną | Trafia w istniejącą ścieżkę błędu technicznego, która teraz ma limit, a klucz nie wycieka do bazy ani `/admin` | Plan (B9 z audytu; przegląd planu F2) |
| Dane historyczne | Bez migracji; runbook z zapytaniem tylko do odczytu | Stary `provider_error` mieszał odmowy i awarie, a zgadywanie z komunikatu na produkcji jest ryzykowne | Plan |
| Liczniki przebiegu | Pojedyncza awaria w istniejącym `processing_errors`, bez nowego licznika | Mniej zmian w API, `/internal` i UI; reguła powiadomień działa bez zmian | Plan |

## Scope

**In scope:**
- `LlmUnavailableException`, fallback flex i nieklasyfikowany wyjątek w trzech klientach.
- Migracja V10 (`posts.technical_failures`, `posts.last_technical_error`), zapisy w `PostTransitions`, RETRY bez wstrzymanych.
- Bezpiecznik i odroczone liczenie w dyspozytorze; `Assessor` przepuszcza niedostępność; kod pomiarowy.
- API i UI listy nieudanych z wstrzymanymi postami.
- Doprecyzowanie w PRD, `CLAUDE.md` pakietów, runbook S-09 i poprawki w `deploy-plan.md`.

**Out of scope:**
- Przeliczanie historycznych prób i wskrzeszanie `dead`.
- Bezpiecznik na odmowy.
- Progi ustawiane przez zmienne środowiskowe.
- Nowy licznik przebiegu.
- B8 i B10.
- Ujednolicenie „brak `choices`” w DeepSeek.
- Ponowienie przebiegu tej samej nocy.

## Architecture / Approach

`TransportRetry` po wyczerpaniu ponowień (albo przy przerwaniu) rzuca `LlmUnavailableException extends LlmCallException`, a `Assessor` przepuszcza go jak `LlmConfigurationException`. Dyspozytor:

- trzyma licznik awarii z rzędu, który zeruje każde wywołanie z odpowiedzią (prefiltr nie);
- przy trzeciej awarii ustawia `stoppedBy`, więc przebieg kończy się `failed`, a reszta postów wraca bez prób;
- po zebraniu wyników kroku, tylko gdy przebieg się nie zatrzymał i wciąż jest `running`, dolicza każdej pojedynczej awarii błąd techniczny posta i `processing_errors`. Strażnik przebiegu (jak w `claim`) sprawia, że deploy, nakładka instancji ani watchdog nie doliczają błędów postom, które przerwały.

Błąd naszego kodu dolicza się od razu, w tym samym warunkowym `UPDATE`, który zwalnia post. Wstrzymanie to licznik `technical_failures >= 3`, a nie nowy stan. Predykaty RETRY i listy nieudanych biorą go pod uwagę.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Klasyfikacja w `llm/` | `LlmUnavailableException`, fallback flex przy mieszance, wyjątek SDK bez `REJECTED` | Wyjątek SDK, który był chwilową winą dostawcy, staje się błędem technicznym (ma limit od fazy 2) |
| 2. Licznik błędów technicznych | V10, `releaseFailed`/zerowanie, RETRY bez wstrzymanych | Strażnik właściciela i zerowanie w `record` muszą zostać jednym zapisem |
| 3. Bezpiecznik | Awaria bez próby, 3 z rzędu → `failed`, odroczone liczenie | Kolejność: licznik dopiero po kroku, anulowanie przed liczeniem |
| 4. Lista nieudanych | Wstrzymane posty w API i `/admin`, do ponowienia | Zmiana kształtu odpowiedzi; zrzuty i 7 stanów |
| 5. Dokumentacja | PRD, `CLAUDE.md`, runbook S-09 z zapytaniem | Rozjazd z dotychczasowymi zdaniami o flex i ponowieniach |

**Prerequisites:** brak (S-09 nie ma zależności). Gałąź `feature/attempt-accounting` od świeżego `origin/main`, issue #68, PR env do weryfikacji.
**Estimated effort:** ~3–4 sesje w 5 fazach (fazy 1–3 to backend z testami, faza 4 to mała zmiana UI, faza 5 to dokumentacja).

## Open Risks & Assumptions

- **Wyjątek SDK bez statusu** (np. błąd parsowania odpowiedzi) był czasem chwilową winą dostawcy. Teraz liczy się jako błąd techniczny i po 3 nocach wstrzymuje post. To jest odwracalne i widoczne na liście nieudanych.
- **„Z rzędu” liczy się w kolejności zakończenia** przy współbieżności 4. Kilka postów w locie kończy się jeszcze po decyzji, ale niczego nie liczą.
- **Bezpiecznika nie da się wywołać w PR env:** deepseek-v4-flash odpowiada w ≤ 5,1 s, a `LLM_TIMEOUT` ma minimum 10 s. Łańcuch transport → bezpiecznik pokrywa IT z prawdziwym klientem na skryptowanym serwerze (HTTP 500), a PR env sprawdza tylko regresję zwykłego przebiegu.
- **Komunikat nieznanego wyjątku SDK** trafia do `posts.last_technical_error` i jest widoczny w `/admin`. Dlatego klient opakowuje go w `IllegalStateException` z komunikatem oczyszczonym przez `ProviderMessages.clean` (klucz → `<hidden>`).
- **Posty, którym awarie już zużyły próby, zostają, jak są.** Skalę pokaże zapytanie z runbooka.

## Success Criteria (Summary)

- Awaria dostawcy w środku nocy kończy przebieg `failed` z sygnałem, a żaden post nie traci próby.
- Post z powtarzalnym błędem technicznym przestaje wracać co noc i jest widoczny na liście nieudanych z błędem i przyciskiem ponowienia.
- Flex z mieszanką błędów kończy się oceną na tierze standardowym zamiast zużytą próbą.
