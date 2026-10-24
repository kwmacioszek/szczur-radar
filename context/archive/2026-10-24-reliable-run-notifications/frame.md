# Frame Brief: S-12 — powiadomienia o przebiegach nie giną

> Etap ustalania ram przed /10x-plan. Ten dokument rejestruje, co jest *faktycznie*
> przedmiotem problemu, oddzielone od tego, co początkowo założono.

## Zgłoszona obserwacja

Brak obserwacji z produkcji ani PR env. Wycinek S-12 (issue #71) pochodzi z audytu kodu
`context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:61-64`:

- **A3** — chwilowy błąd bazy przy `runEnded`: nieudany przebieg admina nigdy nie trafia do dzwonka.
- **A5** — przebieg `partial` z martwym postem, którego `count` padł, ma w bazie `dead=0`, więc nikt nie dostaje powiadomienia.
- **A6** — krok `assess` zapisany jako `succeeded` w przebiegu `failed` (nieudany check LLM albo odrzucony klucz).

## Początkowe ujęcie (zachowane)

- **Podana przyczyna**: z audytu. `runEnded` połyka `RuntimeException`, nie ma ponowienia ani outboxa (A3); `needsAttention` czyta liczniki z bazy, a status pochodzi z wyników w pamięci (A5); `RunRecorder.step` zamyka krok SUCCEEDED, bo dispatcher zwraca `stoppedBy` zamiast rzucać (A6).
- **Proponowany kierunek**: wynik z roadmapy (`roadmap.md:204`) — admin dostaje każde należne powiadomienie także po chwilowym błędzie bazy, „wymaga uwagi” zgadza się z wynikiem przebiegu, krok oceny nie jest `succeeded` w przebiegu `failed`.
- **Zawężenie przed delegowaniem**: zjawiska znane tylko z audytu; główna obawa — „nie wiem”; granica „admin dostaje” (wiersz w bazie / widoczne w dzwonku / poza aplikacją) — nie rozdzielono. W trakcie frame użytkownik: „nie wiem, czy ta zmiana będzie mieć sens — to na razie hipotetyczna sytuacja”.

## Mapa wymiarów

1. **Zapis powiadomienia po końcu przebiegu** — `runEnded` pada po commicie statusu i nic go nie ponawia. ← początkowe ujęcie (A3)
2. **Ścieżki końca przebiegu bez `runEnded`** — przebieg kończy się drogą, która nie zgłasza końca.
3. **Liczniki a decyzja „wymaga uwagi”** — liczniki w bazie rozjeżdżają się z faktami. ← A5
4. **Stan kroku a status przebiegu** — sprzeczny obraz w `/admin`. ← A6
5. **Widoczność w dzwonku** — wiersz istnieje, admin go nie widzi.

## Badanie hipotez

| Hipoteza | Dowody | Werdykt |
| --- | --- | --- |
| 1. A3: powiadomienie ginie po błędzie bazy | Każda ścieżka commituje status, potem woła `runEnded` osobno (`PipelineRunService.java:245,284,339`, `PipelineStartupRecovery.java:103`); wyjątek połknięty (`RunNotifications.java:53-55`); brak jakiegokolwiek uzgadniania dla przebiegów już zakończonych. Okno: milisekundy po udanym commicie; Hikari czeka do 30 s na połączenie, więc potrzeba zerwania w trakcie zapytania. Przy realnej awarii bazy najpierw pada zapis statusu (ścieżka 2), nie powiadomienie. | SŁABE (mechanizm prawdziwy, realność niska) |
| 2. Ścieżka końca bez `runEnded` | Każdy zapis statusu końcowego w `RunRecorder` ma po sobie `runEnded`, także nowa ścieżka S-11 (`feature/stuck-run-self-heals`). Najbliżej luki: nieudany `fail` w `close` (`PipelineRunService.java:303,314`) → `runEnded` cicho widzi `RUNNING`, przebieg wisi do restartu i kończy jako `interrupted`, prawdziwa przyczyna ginie. S-11 skraca to oczekiwanie. | BRAK |
| 3. A5: liczniki a „wymaga uwagi” | Liczniki per post w osobnym autocommicie po `PostTransitions.record` (`ConcurrentAssessmentDispatcher.java:160-161,317-335`, `RunRecorder.java:101-119`); błąd → tylko WARN (`:332-334`). Status z pamięci (`RunFlow.java:151-156`), decyzja z bazy (`PipelineRun.java:282-294`). Liczniki mogą być tylko niższe od faktów; odwrotnie — niemożliwe. Wymaga tej samej klasy awarii co A3. Fakt trwały istnieje: `pipeline_runs.error` dla `partial` ⇔ błędy techniczne (`PipelineAgent.java:135`), martwe posty odtwarzalne po `assessments.run_id`. | Mechanizm SILNE, realność SŁABE |
| 4. A6: `assess` = `succeeded` w `failed` | Nadal prawda: `PipelineAgent.java:130-137` → `RunRecorder.java:165-169`; deterministyczne przy każdym zatrzymaniu przez LLM (`ConcurrentAssessmentDispatcher.java:147,256,271`). Decyzja o powiadomieniu nie czyta kroków — powiadomienie powstaje (`PipelineAgentIT`, `LlmOutageIT`). Skutek: „Ocena: zakończony” bez błędu kroku w przebiegu `failed` (`RunDetailView.vue:234-247`, `AdminView.vue:321-329`). | SILNE jako niespójność obrazu; BRAK jako problem powiadomień |
| 5. Widoczność w dzwonku | Lista = 10 najnowszych (`NotificationController.java:41`), licznik nieprzeczytanych liczy wszystkie; świadoma decyzja S-06 (komentarz przy `readAll`). Przebieg i tak widać w `/admin` z „wymaga uwagi”. Reszta to opóźnienia odświeżania (`NotificationBell.vue:77-92`). | SŁABE |

## Sygnały zawężające

- Wszystkie trzy zjawiska znane tylko z audytu; żadnego incydentu. W archiwum zmian brak zgłoszeń zgubionego powiadomienia.
- Przebiegi to „prawie tylko cron”. Cron ma własne sygnały przy `failed`/`interrupted` (kod wyjścia → Railway „deployment crashed”, healthchecks.io F-04), więc dzwonek nie jest tam jedynym kanałem.
- Wyjątek: `partial` z martwym postem / błędem technicznym kończy crona kodem 0 — dla niego dzwonek jest jedynym sygnałem. To dokładnie przypadek A5 (i A3 dla takiego przebiegu).
- Przebiegu zatrzymanego przez LLM użytkownik nie widział, więc A6 nikogo dotąd nie zmyliło.
- S-18 „i tak będzie”: linia ERROR „the admins could not be notified” (`RunNotifications.java:54`) trafi do Telegrama, więc A3 przestaje być ciche. A5 loguje tylko WARN (`ConcurrentAssessmentDispatcher.java:332-334`) — S-18 w obecnym zakresie (`roadmap.md:281`) go nie zobaczy.

## Konwencja między systemami

Zwykła odpowiedź na „efekt uboczny po commicie może przepaść” to outbox albo uzgadnianie (reconcile). Uzasadnia ją jednak częstość lub koszt zguby. Tu: pojedyncza instancja, jedna baza, okno milisekund, drugi kanał dla przebiegów crona, a po S-18 — alarm o każdej zgubie z A3. Projekt już przyjął tę samą postawę „best-effort + widoczna luka” dla webhooka monitora (`context/archive/2026-10-20-deploy-verification-hardening/plan.md:91`).

## Przeformułowany (lub potwierdzony) opis problemu

> **Rzeczywisty problem, wokół którego należy planować, to**: nic pilnego — S-12 w obecnym kształcie („powiadomienie nie ginie po błędzie bazy”) opisuje rzadki scenariusz bez incydentu, który po S-18 przestaje być cichy; zostają dwie drobne, ciche niespójności (A5, A6), które nie uzasadniają osobnego wycinka teraz.

Ujęcie z audytu jest technicznie prawdziwe, ale ocena „M” dla A3 nie wytrzymuje sprawdzenia: realny błąd bazy najpierw zatrzymuje zapis statusu, a ten przypadek kończy się powiadomieniem o `interrupted` po restarcie (szybciej po S-11). Jedyna droga, którą należne powiadomienie ginie bez żadnego śladu, to A5: wymaga tej samej rzadkiej awarii, a zostawia tylko WARN. A6 jest deterministyczne, ale kosmetyczne — nie dotyczy powiadomień i nikogo dotąd nie zmyliło.

## Pewność

**WYSOKA** — dowody file:line z trzech niezależnych tras, brak incydentu w historii, i rozstrzygające sygnały użytkownika (cron jako główna droga, S-18 w planach, A6 niewidziane). Nie przeprowadzono osobnego wyszukiwania „bez uprzedzeń”; jego rolę pełnią trzy rozłączne trasy, które zbiegły się w tym samym wniosku.

## Co zmienia się dla /10x-plan

Rekomendacja: nie planować S-12 teraz. Przenieść go do parked w roadmapie z tym uzasadnieniem, z warunkiem powrotu: pierwszy incydent zgubionego powiadomienia albo przebiegi admina uruchamiane bez patrzenia. Dwa drobne wątki można rozważyć osobno, nie jako S-12:

- A5 — dopisać do zakresu S-18, żeby nieudany zapis licznika był sygnałem (dziś to tylko WARN);
- A6 — ewentualnie mała poprawka spójności kroków przy okazji pracy w `pipeline/flow`.

Jeśli użytkownik mimo to chce S-12, plan powinien dotyczyć tylko A5 (decyzja „wymaga uwagi” zgodna z faktami) i A6. Nie powinien dotyczyć outboxa ani ponowień dla A3.

## Odniesienia

- Audyt: `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:61-64`
- Roadmapa: `context/foundation/roadmap.md:202-212` (S-12), `:279-292` (S-18)
- Kod: `notifications/RunNotifications.java:41-56`, `notifications/api/NotificationController.java:41`, `pipeline/PipelineRunService.java:241-339`, `pipeline/PipelineStartupRecovery.java:85-103`, `pipeline/assess/ConcurrentAssessmentDispatcher.java:147-193,317-361`, `pipeline/state/RunRecorder.java:76-169`, `pipeline/flow/RunFlow.java:110-156`, `pipeline/flow/PipelineAgent.java:105-151`, `pipeline/run/PipelineRun.java:282-294`, `tools/pipeline-trigger/trigger.sh`
- Zadania badawcze: trzy równoległe trasy (ścieżki końca + A3; liczniki + A5; stan kroku A6 + dzwonek)
