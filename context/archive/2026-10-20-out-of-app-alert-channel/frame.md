# Frame Brief: Kanał alarmowy poza aplikacją (F-04)

> Etap ustalania ram przed /10x-plan. Ten dokument rejestruje, co jest *faktycznie*
> przedmiotem problemu, oddzielone od tego, co początkowo założono.

## Zgłoszona obserwacja

Część złych zdarzeń nie daje operatorowi żadnego sygnału poza aplikacją: cron, który nie
wystartował lub utknął w QUEUED, brakująca noc, ścieżki kończące się tylko logiem ERROR,
zdarzenia widoczne tylko w dzwonku. Jedyny dzisiejszy alarm zewnętrzny to ręcznie ustawiona
reguła konta Railway „deployment crashed”, której nie ma w IaC (audyt C2, C11;
`context/foundation/roadmap.md:129`).

## Początkowe ujęcie (zachowane)

- **Podana przyczyna lub podejście** (roadmapa F-04, `roadmap.md:133-146`): brakuje jednego
  kanału poza aplikacją, „do którego API i oba crony mogą wysłać sygnał”, w limitach kosztów.
- **Proponowany kierunek**: „tylko minimalne podłączenie: kontrakt wysyłki i jeden sygnał
  testowy”, raz sprawdzone na produkcji; konkretne alarmy dodają S-13 i S-18. W rozmowie
  użytkownik doprecyzował: Telegram i healthchecks.io.
- **Zawężenie przed delegowaniem**: push Railway widziany „tylko w testach”; jak operator
  dotąd dowiadywał się o awariach — „nie jestem pewien”; najpierw sprawdzić, czy F-04 nie
  powiela się z `deploy-verification-hardening` (dalej: DVH).

## Mapa wymiarów

1. **Brak kanału** ← początkowe ujęcie — nie ma dokąd wysłać sygnału poza aplikacją.
2. **Kanał powstaje gdzie indziej** — reguła Railway działa dla cronów, DVH dodaje push
   (przypisanie issue → GitHub Mobile); F-04 by go powielało.
3. **Wykrywanie braku, nie wysyłka** — cron, który nie wystartował, niczego nie wyśle;
   kontrakt wysyłki z definicji nie pokrywa C2.
4. **API bez nadawcy** — zdarzenia z API (ERROR, dzwonek) nie mają drogi z procesu.

## Badanie hipotez

| Hipoteza | Dowody | Werdykt |
| --- | --- | --- |
| 1. Brak kanału (początkowe) | Crash crona → `CRASHED` → push przez regułę konta, sprawdzone w testach (`deploy-plan.md:463,503,544,1071`); DVH buduje push przez przypisanie issue (DVH `plan.md:14,69`). Kanał istnieje dla zdarzeń „coś padło” — brakuje go dla innych klas | SŁABE |
| 2. Powielenie z DVH | DVH reaguje tylko na webhooki deployu Railway; Worker przyjmuje wyłącznie payload Railway (`szczur-radar-monitor/worker/src/index.ts:24-30,81-87,128-157`); poza zakresem: „reconcile, harmonogram, wykrywanie zgubionych webhooków” i „błędy po starcie” (DVH `plan.md:91-93`); „Push na telefon innym kanałem niż przypisanie issue. To F-04.” (`:98`). Wspólny jest tylko crash crona (i może crash loop API) | SŁABE |
| 3. Wykrywanie braku | Pominięty takt crona Railway jest cichy (docs Railway, `guides/cron-workers-queues#cron-jobs`); reguła działa tylko na `CRASHED`; QUEUED ~11 min bez sygnału (`deploy-plan.md:361`); tymczasowy harmonogram zostaje do następnego merge'a (`:296,411`); świeżość = koniec ostatniego przebiegu, bez progu (`deploy-plan.md:467`, `FeedView.vue:172-174`); API usypia (`.railway/railway.ts:121`) i nie ma harmonogramu poza watchdogiem (`RunWatchdog.java:55`); roadmapa sama: „aplikacja śpi, więc sama tego nie zgłosi” (`roadmap.md:137`) | SILNE |
| 4. API bez nadawcy | ERROR-only: `RunNotifications.java:53-54`, `PipelineStartupRecovery.java:94-97`, `RunWatchdog.java:96-98`, `PipelineRunService.java:313-315`; `/api/ready` 503 bez logu (`HealthController.java:42-44`); dzwonek = tylko INSERT (`NotificationRepository.java:51-54`), jedyny szew `RunNotifications.runEnded` (`:41`) pokrywa wyniki przebiegów, nie ścieżki ERROR; ruch wychodzący z API jest (LLM, Wykop); brak actuatora/logback (`pom.xml`) | SILNE |

Pokrycie przypadków dziś i po DVH:

| Przypadek | Reguła Railway | DVH | Nikt |
| --- | --- | --- | --- |
| Cron uruchomił się i padł | tak (testy) | prawdopodobnie, niesprawdzone na żywo | |
| Cron nie wystartował / QUEUED / zła cron-tabela | nie | nie | **tak** |
| Ścieżki ERROR w API (C11) | nie | nie | **tak** |
| Zdarzenia tylko z dzwonka (przebieg admina, `partial` z `dead`) | tylko nocny przez exit 1 | nie | **tak** |
| Crash loop API | nieznane (`roadmap.md:323`) | częściowo (`crashed`/`oom_killed`) | |

## Sygnały zawężające

- Push Railway nie był jeszcze widziany przy prawdziwej awarii — dzisiejszy jedyny alarm jest
  sprawdzony tylko w testach i nie ma go w IaC.
- DVH nie rezerwuje dla F-04 drogi ani kontraktu; oddaje mu „push innym kanałem” (DVH `plan.md:98`).
- Krok 4 (pytania) pominięty: Krok 3 dał silne dowody dla wymiarów 3 i 4 i słabe dla 1 i 2.

## Konwencja między systemami

Dwie klasy sygnału zwykle obsługuje się osobno: **oczekiwany sygnał w czasie** (heartbeat /
dead-man's switch: zadanie melduje się, zewnętrzny obserwator alarmuje przy braku meldunku) i
**zdarzenie** (aplikacja wysyła wiadomość do kanału). Wcześniejsze zmiany tego projektu już
przypisały pierwszą klasę F-04: „dead-man's switch (F-04)”
(`context/archive/2026-10-18-backup-check-completeness/plan-brief.md:49`, `plan.md:88`), „osobny
slice” (`context/archive/2026-10-18-safe-deploy-gate/plan.md:111`). Kierunek użytkownika
(healthchecks.io) to właśnie obserwator braku; Telegram to kanał dla zdarzeń.

## Przeformułowany opis problemu

> **Rzeczywisty problem, wokół którego należy planować, to**: nikt poza aplikacją nie oczekuje
> nocnego przebiegu ani backupu, więc ich brak jest niewidoczny, a API nie ma żadnej drogi, by
> wyprowadzić zdarzenie poza proces — sam kanał „do wysyłki” rozwiązuje tylko drugą połowę.

Opis F-04 w roadmapie („kanał, do którego API i crony mogą wysłać sygnał”, „kontrakt wysyłki”)
jest węższy niż to, co od F-04 zależy: S-13 potrzebuje obserwatora braku sygnału, a S-14 i S-15
odesłały dead-man's switch właśnie do F-04. Przypadek „cron padł” jest już pokryty przez regułę
Railway (i ma zostać pokryty przez DVH), więc nie jest tym, czego brakuje. Rozwiązanie daje
alarm dla nocy, która się nie odbyła, oraz drogę, którą S-18 podłączy ścieżki ERROR z API.

## Pewność

**WYSOKA** dla wymiaru 3 (dokumentacja Railway + incydent QUEUED + wcześniejsze decyzje
projektu) i 4 (kod). Otwarte, nie zmieniające ram, do rozstrzygnięcia w `/10x-plan`:
- czy F-04 obejmuje obie klasy sygnału, czy tylko oczekiwany sygnał (zdarzenia z API → S-18);
- stanowisko wobec powielenia alarmu przy crashu crona (reguła Railway, DVH, F-04);
- fakty o healthchecks.io niepotwierdzone w dokumentacji: Telegram w darmowym planie Hobbyist
  ($0, 20 checków, 100 wpisów logu na check — potwierdzone), czy alarm w Telegramie zawiera
  treść pingu, co przywraca check do „up” po `/fail`;
- czy reguła „deployment crashed” obejmuje crash loop API (`roadmap.md:323`).

## Co zmienia się dla /10x-plan

Plan F-04 powinien dotyczyć dwóch klas sygnału — oczekiwanego meldunku cronów z alarmem przy
jego braku oraz drogi zdarzenia z API — a nie samego „kontraktu wysyłki”; przypadek „cron padł”
nie wymaga nowej pracy. Jeśli plan zmieni zakres F-04 lub S-13/S-18, zaktualizować wpisy w
`roadmap.md`; DVH nie musi być skończone przed F-04, chyba że plan wybierze jego drogę pushu.

## Ograniczenia dla planu

Koszt i usypianie (decyzja użytkownika, 2026-10-20: „nie chcę przez to nabić sobie kosztów”).
API i web mają `sleepApplication: true` (`.railway/railway.ts:121,201`), a watchdog i odpytywanie
dzwonka są zaprojektowane tak, by nie budzić API (`deploy-plan.md:720,734`). Dlatego:

- **Sygnały tylko wychodzące.** Crony i API wysyłają na zewnątrz (meldunek, wiadomość); żadna
  zewnętrzna usługa nie odpytuje produkcji (`/api/ready`, `/healthz`, strony) cyklicznie.
- **Bez nowych zegarów w API.** Żadnego okresowego zadania w API (np. „co N minut sprawdź, czy
  noc się odbyła”); brak meldunku wykrywa obserwator poza Railway.
- **Bez kanału przychodzącego.** Bot tylko wysyła: bez long-pollingu i bez webhooka do API lub weba.
- **API wysyła tylko w czasie, gdy i tak pracuje** (przebieg, żądanie); wysyłka nie może go
  budzić ani przedłużać jego pracy w odczuwalny sposób.
- **Usługi w darmowych planach** (healthchecks.io Hobbyist $0; Telegram Bot API bez opłat) —
  żadnego nowego stałego kosztu przy limicie $10 Railway.

## Odniesienia

- `context/foundation/roadmap.md:94,129,133-146,214-222,278-285,323`
- `context/archive/2026-10-17-post-mvp-pipeline-audit/research.md:91-92,100,105-121,279`
- `context/deployment/deploy-plan.md:286,296,361,411,463,467,503,544,732,1071`
- `.railway/railway.ts:121,235,277`; `szczur-radar-api/.../notifications/RunNotifications.java:41,53-54`
- `.../pipeline/PipelineStartupRecovery.java:94-97`, `RunWatchdog.java:55,96-98`,
  `PipelineRunService.java:313-315`, `health/HealthController.java:42-44`
- DVH (worktree `szczur-radar/.claude/worktrees/deploy-verification-hardening`, niezacommitowane):
  `context/changes/deploy-verification-hardening/{frame,plan,plan-brief}.md`;
  repo `szczur-radar-monitor@61ec310`, `worker/src/index.ts`
- healthchecks.io: `/pricing/`, `/docs/http_api/`, `/docs/configuring_checks/` (2026-10-20)
- Agenci badawczy: wykrywanie braku, pokrycie z DVH, API jako nadawca (bez TaskCreate)
