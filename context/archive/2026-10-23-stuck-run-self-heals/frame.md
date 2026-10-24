# Frame Brief: utknięty przebieg sam wraca do obiegu (S-11)

> Etap ustalania ram przed /10x-plan. Ten dokument rejestruje, co jest *faktycznie*
> przedmiotem problemu, oddzielone od tego, co początkowo założono.
> Ścieżki kodu względem `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/`, chyba że podano inaczej. Stan kodu: `45d18c4`.

## Zgłoszona obserwacja

Audyt z 2026-10-17 (`context/archive/2026-10-17-post-mvp-pipeline-audit/research.md`, A2 [O + I], A4 [I]) i roadmapa S-11: przebieg `running` po instancji, która zniknęła bez `onClose`, blokuje kolejkę do następnego restartu — `existsActive` daje 409, cron ponawia 409 przez 60 min i kończy CRASHED; pomaga dopiero uśpienie lub restart API. Post przejęty w wyścigu `claim` z odzyskiwaniem zostaje `assessing` przy przerwanym przebiegu. Żaden z tych stanów nie był zgłoszony jako widziany incydent.

## Początkowe ujęcie (zachowane)

- **Podana przyczyna**: odzyskiwanie działa raz, przed portem (`PipelineStartupRecovery`); watchdog widzi tylko przebiegi z pamięci tej instancji (`RunWatchdog`, `ownRuns`); `claim` bez `FOR SHARE` widzi migawkę sprzed interrupt.
- **Proponowany kierunek**: przebieg i post wracają do obiegu bez restartu API; test wyścigu na bazie jest częścią wycinka.
- **Zawężenie przed delegowaniem**: użytkownik nie jest pewien, czy stan widział na produkcji/PR env; nie rozdzielił A2 od A4; nie wie, kiedy dwie instancje działają naraz.
- **Zawężenie po badaniu**: realnie boli **nocny cron** i **bramka deployu** (nie admin w `/admin`). Które stany utknięcia wchodzą w zakres — „nie wiem”.

## Mapa wymiarów

1. **Powstawanie sieroty na Railway** — czy okna overlapu i SIGKILL w ogóle istnieją.
2. **Istniejąca samonaprawa przez uśpienie** — uśpienie → pobudka → `recover()`; kiedy zawodzi.
3. **Wykrywanie tylko przy starcie, watchdog tylko dla własnych** ← początkowe ujęcie (A2).
4. **Wyścig `claim` z odzyskiwaniem** ← początkowe ujęcie (A4).
5. **Ślady na produkcji** — czy sieroty, 409 albo odłożone posty są w logach.

## Badanie hipotez

| Hipoteza | Dowody | Werdykt |
| --- | --- | --- |
| 1. Okna sieroty są realne | Railway: stara instancja dostaje SIGTERM dopiero, gdy nowa jest aktywna; `overlapSeconds`/`drainingSeconds` domyślnie 0 (docs.railway.com/deployments/reference#singleton-deploys); nic z tego nie ustawione (`.railway/railway.ts:105-121`, brak `server.shutdown` w `application*.properties`). Produkcja 2026-10-23: odzyskiwanie nowej instancji ~15:11:50, stara żyje do 15:12:01 (~10 s). Hook zamknięcia trwał ~40 ms, ale nigdy z przebiegiem w toku. | SILNE (okno), SŁABE (SIGKILL w trakcie `onClose` — niezaobserwowany) |
| 2. Uśpienie już leczy sierotę | Pobudka = nowy JVM i `recover()` przed portem (`PipelineStartupRecovery.java:35-41,76-79`; `deploy-plan.md:1291`). Zombie przeżywa tylko, gdy coś trzyma API obudzone: ponowienia 409 crona co 60 s przez 60 min (`tools/pipeline-trigger/trigger.sh:31-32,236-244`), bramka deployu co 60 s do 90 min (`tools/deploy-gate/wait-safe.sh:47,104-121`), `/admin` i strona przebiegu co 10 s przy aktywnym przebiegu, bez sprawdzania widoczności (`szczur-radar-web/src/views/AdminView.vue:84,166-168`, `src/polling.ts:5-12`). | SILNE — zmienia ujęcie (patrz niżej) |
| 3. Wykrywanie tylko przy starcie / tylko własne | Potwierdzone: `RunWatchdog.java:29,91-93`; `PipelineStartupRecovery.java:94-97` (błąd tylko logowany); `RunWatchdogIT:240-257` przypina „cudzy przebieg zostaje”. Ale „żywa instancja nie może rozpoznać martwego przebiegu” jest przesadzone: `RunRecorder.stall` (`state/RunRecorder.java:199-211`) ocenia `running` po `last_activity_at` na zegarze bazy, bez warunku właściciela — zawęża go tylko `own` w pamięci. Dla `queued` sygnału brak (tylko `created_at`). | SILNE (z korektą) |
| 4. Wyścig `claim` (A4) | `claim` = jedno zdanie z `EXISTS` bez `FOR SHARE` (`state/PostTransitions.java:72-75`); późny claim może przejść po interrupt (EvalPlanQual nie sprawdza wiersza podzapytania). Okno ~1 ms, realne przy overlapie deployu. Skutek: `record` (`:95-97`) zapisuje ocenę, `count` (`RunRecorder.java:107`, `WHERE status=running`) jej nie liczy; post utyka `assessing` tylko, gdy stara instancja zginie przed `record`/`release`. `releaseOrphans` ma jednego wołającego — start. Test tylko odtwarza stan końcowy (`PipelineAgentIT:349-365`). | SŁABE jako źródło odczuwanego bólu (nie blokuje kolejki) |
| 5. Ślady na produkcji | Logi 2026-10-17…23 (~7 dni retencji): zero „Marked … unfinished”, „Put back”, `pipeline_busy`, „made no progress”; cron bez 409. Jedno nieudane `recover()`: 2026-10-23 07:12:22 UTC, `password authentication failed for user "postgres"`. Przebieg 22 h z 2026-10-09 poza retencją. | BRAK sierot; SILNE dla ścieżki „nieudane odzyskiwanie” |

## Sygnały zawężające

- Ból odczuwają cron (60 min 409 → CRASHED → noc bez danych) i bramka deployu (`DeploySafetyController.java:44-50` zgłasza zombie jako `pipeline_running`, trzyma apply i deploy do 90 min — wstrzymując ten deploy, który by zombie usunął).
- Obaj są **jednocześnie ofiarą i źródłem utrzymania**: ich polling (co 60 s, poniżej progu uśpienia 5–10 min) nie pozwala API zasnąć, a pobudka to dziś jedyny uzdrowiciel. Zombie sam się podtrzymuje, dopóki ktoś pyta.
- Wszystkie trzy blokujące stany — `running` po martwej instancji (A2), `queued` bez wykonawcy (A8, `PipelineRunService.java:183-190`), nieudane `recover()` — dają ten sam skutek: wiersz `queued`/`running` bez żywego wykonawcy, któremu ufa `existsActive` (`run/PipelineRunRepository.java:19-21`, bez wieku i właściciela). A4 tego skutku nie daje.
- Sprzeczność do rozstrzygnięcia w planie: `RunWatchdog.java:30-32` uzasadnia brak zapytań w bezczynności tym, że ruch do Postgresa nie pozwala API zasnąć, a Spring Session JDBC czyści sesje co minutę (domyślny `cleanup-cron` `0 * * * * *`, brak nadpisania w `application.properties:39-43`) i API mimo to zasypia. Faktyczna reguła Railway — niewiadoma.

## Konwencja między systemami

Kolejki z „jednym aktywnym zadaniem” zwykle wiążą blokadę z żywotnością właściciela (lease/heartbeat z wygaśnięciem), a nie ze samym statusem — to wcześniejsza przesłanka, nie dowód z projektu. Projekt ma już de facto heartbeat dla `running` (`last_activity_at` + `PIPELINE_STALL_TIMEOUT`), ale używa go tylko u właściciela; dla `queued` nic. Wiodąca hipoteza jest z tą konwencją zgodna.

## Przeformułowany opis problemu

> **Rzeczywisty problem, wokół którego należy planować, to**: każda bramka (`queue()` → 409 dla crona i admina, `/api/deploy-safety` dla bramki deployu) ufa wierszowi `queued`/`running` bez sprawdzenia, czy ktoś go jeszcze wykonuje, a jedyny mechanizm, który taki wiersz usuwa, działa przy starcie JVM — którego te same bramki, odpytując, nie dopuszczają.

Początkowe ujęcie (A2) trafia w przyczynę, ale jest węższe niż problem: obejmuje ten sam skutek z trzech źródeł (A2, A8, nieudane `recover()` — to ostatnie faktycznie wystąpiło 2026-10-23), a „samonaprawa” musi zadziałać, gdy API jest obudzone przez cron albo bramkę deployu, nie tylko gdy zaśnie. A4 to osobny, drobny defekt spójności (liczniki vs `assessments`, rzadko post `assessing`): nie blokuje kolejki ani bramki, więc nie wynika z odczuwanego bólu — roadmapa wpisała go do S-11, plan musi świadomie zdecydować, czy zostaje (lekcja „Uzasadniaj zakres celem użytkownika”).

## Pewność

- **ŚREDNIA** — mechanizm i okna są silnie udowodnione w kodzie, dokumentacji Railway i jednym deployu z produkcji; brak zaobserwowanej sieroty w 7 dniach logów, a SIGKILL w trakcie `onClose` przy przebiegu w toku jest niesprawdzony. Planowanie nie wymaga odtworzenia — test na bazie (dwie „instancje” na jednej bazie) jest tańszym dowodem niż incydent.

## Co zmienia się dla /10x-plan

Plan powinien celować w skutek „wiersz aktywny bez żywego wykonawcy blokuje cron i bramkę deployu na obudzonym API”, obejmując zarówno `running` (jest sygnał `last_activity_at`), jak i `queued` (sygnału brak) oraz nieudane `recover()`, i zachowując sen API — z rozstrzygnięciem sprzeczności z `RunWatchdog.java:30-32`. Musi zmienić `RunWatchdogIT:240-257` i jawnie zdecydować o A4 (w zakresie czy osobno) oraz o ryzyku zatrzymania przez inną instancję żywego, zawieszonego przebiegu (`stopStalled` wraca przed `cancel`, `PipelineRunService.java:279-281`).

## Odniesienia

- Pliki źródłowe: `PipelineStartupRecovery.java:35-47,76-105`; `RunWatchdog.java:29-32,91-101`; `PipelineRunService.java:174-195,272-281,330-345`; `state/RunRecorder.java:107,199-232`; `state/PostTransitions.java:72-75,95-97,162-174`; `api/DeploySafetyController.java:44-50`; `tools/pipeline-trigger/trigger.sh:236-245`; `tools/deploy-gate/wait-safe.sh:47,104-121`; `.railway/railway.ts:105-121`.
- Testy: `RunWatchdogIT:240-257`, `PipelineAgentIT:274-316,349-365,504-505`, `PipelineStartupRecoveryIT:47-69`.
- Dokumenty: `context/deployment/deploy-plan.md:957,962` (runbook zombie: restart API); `context/archive/2026-10-15-pipeline-run-history-dashboard/reviews/impl-review.md:74` (S-06 F3).
- Powiązane badania: brak `research.md` (badanie przerwane na rzecz frame).
- Zadania badawcze: 4 agenci tylko do odczytu — Railway overlap + logi produkcji; ścieżka uśpienia; cykl życia sieroty w kodzie; wyścig `claim` (A4).
