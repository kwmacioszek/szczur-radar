# Frame Brief: Wiarygodna weryfikacja wdrożeń

> Etap ustalania ram przed /10x-plan. Ten dokument rejestruje, co jest *faktycznie*
> przedmiotem problemu, oddzielone od tego, co początkowo założono.

## Zgłoszona obserwacja

Obecny `deploy-verify` generuje uruchomienia `skipped` i potrafi zakończyć się zielono mimo wykrytej
awarii. Brak wiarygodnego sygnału green/red po deployu.

## Początkowe ujęcie (zachowane)

- **Podana przez użytkownika przyczyna lub podejście**: workflow `deployment_status` w tym samym repo
  musi zostać zielony, bo czerwony zablokowałby Railway Wait for CI i redeploy tego samego commita;
  weryfikacja nie sprawdza naprawdę połączenia API z PostgreSQL.
- **Proponowany przez użytkownika kierunek**: rozważyć osobne repozytorium monitoringu, Railway
  webhooks i zewnętrzny status commita — po zweryfikowaniu tych założeń; zachować bezpieczeństwo
  produkcji, PR envy i ochronę przed duplikatami zdarzeń.
- **Zawężenie przed delegowaniem**: wszystkie trzy objawy razem (zieleń mimo awarii, szum `skipped`,
  alarmy jak #90 przy sondach 200); docelowo powiadomienie push; użytkownik nie wiedział, że
  `/api/ready` robi `SELECT 1`, i chce weryfikować także `szczur-radar-web`.

## Mapa wymiarów

1. **Źródło zdarzeń (GitHub Deployments od Railway)** — status na poziomie środowiska, bez nazwy
   usługi, wiele statusów na deploy; werdykt zależy od tego, który status przyszedł.
2. **Ograniczenie Wait for CI** ← początkowe ujęcie — czy „nigdy czerwony” wynika z mechaniki Railway,
   czy z wybranego kanału wyniku.
3. **Wierność sond** — czy 200 dowodzi, że działa *ten* commit API i web, z bazą.
4. **Kanał alarmu i duplikaty** — issue zamiast push; wyścig przy kilku zdarzeniach w ciągu ~2 s.

## Badanie hipotez

| Hipoteza | Dowody | Werdykt |
| --- | --- | --- |
| 1. Zdarzenia Railway są złym nośnikiem werdyktu | 30 deployów / 181 statusów: `description` puste, `log_url` = panel środowiska, brak nazwy usługi; sekwencje `in_progress`→S→F→S na jednym deployu (pr-80/a2dd334: 6×S, 3×F); status `success` doklejony do starszego deployu (23d7262, 20:39:06); prod 1a6d300 utknął w `in_progress` (usługi pominięte przez watch paths) — weryfikacji nie było; commity bez zmian w usługach (4cac83f, 5f8714a) nie mają deployu. 29 `skipped` = 1:1 statusy `in_progress`. #90: `failure` = celowy `CRASHED` crona `pipeline-trigger` (`context/deployment/deploy-plan.md:1150-1158`), api/web zdrowe | SILNE |
| 2. „Nigdy czerwony” jest wymuszone przez Wait for CI | Docs Railway (Wait for CI): liczą się tylko check suites GitHub Actions, wniosek per workflow run, „Checks from other GitHub apps are ignored”, failed → skip, skipped/neutral nie blokują. Społeczność: deploy tego samego SHA po zmianie zmiennych dostał SKIPPED „CI check suite failed”. Railway sam publikuje per-usługowe commit statusy `szczur-radar - <service>` z `failure` i kolejne deploye tego SHA przeszły. Założenie S-14 opierało się na docs, nie na teście (`deploy-plan.md:947,975`). | SŁABE jako ujęcie: ograniczenie jest prawdziwe, ale **wąskie** — dotyczy tylko konkluzji workflow Actions w tym repo, nie werdyktu jako takiego |
| 3. Sondy nie dowodzą zdrowia wdrożonego commita | `DEPLOY_SHA` tylko drukowany (`tools/deploy-gate/verify-deploy.sh:145`), brak endpointu wersji w API/web; nieudany preDeploy `migrate` zostawia starą wersję serwującą (`.railway/railway.ts:113-118`, `deploy-plan.md:95`); `/healthz` = stałe `respond "ok" 200` (`szczur-radar-web/Caddyfile:18-20`), SPA fallback zwraca `index.html` z 200 na brakujący asset (`Caddyfile:36-41`); `/api/ready` = `SELECT 1` (`HealthController.java:37-46`) — połączenie tak, schemat/wersja nie; web w PR env często SKIPPED i stary (`deploy-plan.md:228,1012`) | SILNE |
| 4. Kanał i duplikaty | Issue to nie push; F-04 `out-of-app-alert-channel` zablokowane (`roadmap.md:94,133-146`); reguła „deployment crashed” istnieje tylko dla cronów, dla api/web niezweryfikowana (`roadmap.md:323`). Duplikaty #84–#87 to testy ręczne; mechanizm (lag listy issue ~2 s, brak concurrency) realny w ruchu (#90: F 20:47:51 i 20:47:56) | SILNE dla duplikatów; kanał push poza zakresem (patrz niżej) |

## Sygnały zawężające

- „Deploy nieudany” = **każda usługa** środowiska, także crony → alarm #90 nie był fałszywy, był
  **nieprzypisany**: GitHub Deployment nie mówi, która usługa padła, a późniejszy `success` go maskuje.
- **Push może przyjść później** (razem z F-04) — teraz liczy się wiarygodny czerwony/zielony sygnał.
- Web: użytkownik nie jest pewien, co znaczy „działa”; pyta o Playwright E2E (przeklikanie prostego
  przepływu) w osobnym repo. Dowody: `/healthz` nic nie mówi o SPA; Wait for CI ignoruje inne repo i
  commit statusy, więc osobne repo **nie jest potrzebne** do uniknięcia blokady — może mieć inne
  powody (izolacja testów E2E), to decyzja dla planu. Przepływ z logowaniem na produkcji wymaga konta
  syntetycznego — to osobna decyzja bezpieczeństwa (konta tylko z CLI, PRD FR-026–028).

## Konwencja między systemami

Weryfikację po deployu zwykle wiąże się z tożsamością wdrożenia (endpoint wersji/commita
porównywany z oczekiwanym SHA) i raportuje per usługa w kanale, który nie bramkuje kolejnych
deployów (commit status / check innej aplikacji, webhook platformy). Tu oba elementy są nieobecne:
werdykt idzie z sygnału środowiskowego, a jedyny kanał „czerwieni” to ten, który bramkuje.

## Przeformułowany opis problemu

> **Rzeczywisty problem, wokół którego należy planować, to**: werdykt po deployu powstaje z sygnału,
> który nie identyfikuje usługi ani wdrożonej wersji (statusy GitHub Deployment od Railway + sondy bez
> tożsamości commita), i jest raportowany wyłącznie przez konkluzję workflow — jedyny kanał, który
> Wait for CI liczy i który przez to musi zostać zielony.

Osobne repo nie jest warunkiem „czerwieni bez blokady”: Wait for CI ignoruje commit statusy i checki
innych aplikacji (docs Railway), więc nieblokujący czerwony wynik jest możliwy także w tym repo.
`/api/ready` już sprawdza Postgres; luką jest brak powiązania odpowiedzi z wdrożonym commitem
(i schematem) oraz płytki check weba. Rozwiązanie tego daje: brak `skipped`-szumu, alarmy z nazwą
usługi, wykrycie „stara wersja serwuje”, czerwony wynik widoczny przy commicie bez blokowania redeployu.

## Pewność

**WYSOKA** — mechanika zdarzeń zmierzona na 181 statusach, semantyka Wait for CI z aktualnych docs
Railway (sprawdzone ponownie 2026-10-20), luki sond z kodu. Otwarte, nie zmieniające ram:
- czy `railway redeploy` tego samego SHA ponownie czeka na CI (tylko społeczność; docs milczą);
- czy reguła „deployment crashed” obejmuje api/web (ważne dla F-04, nie dla tej zmiany);
- czy webhook Railway (z nazwą usługi, `isEphemeral`, bez podpisu, best-effort z ponowieniami)
  pokrywa wszystkie stany, w tym deploy utknięty / pominięty przez watch paths.

## Co zmienia się dla /10x-plan

Plan powinien dotyczyć **źródła werdyktu** (zdarzenie per usługa zamiast statusu środowiska;
tożsamość wdrożonego commita w sondach; definicja „web działa”) i **kanału wyniku** (czerwień poza
konkluzją workflow), z deduplikacją zdarzeń — a nie lokalizacji monitoringu. Push zostaje dla F-04;
E2E Playwright jako osobna decyzja zakresu (ew. przez `/10x-e2e-setup`), nie warunek tej zmiany.

## Odniesienia

- `.github/workflows/deploy-verify.yml:12-29`, `tools/deploy-gate/verify-deploy.sh:25-39,145,163-175`
- `tools/deploy-gate/web-url.sh:18-27`, `tools/deploy-gate/lib.sh:25-37`
- `szczur-radar-api/src/main/java/pl/szczurradar/api/health/HealthController.java:37-46`
- `szczur-radar-web/Caddyfile:18-41`, `.railway/railway.ts:113-121,199`
- `context/deployment/deploy-plan.md:95,228,947,950,975,1012,1025-1028,1150-1158`
- `context/archive/2026-10-18-safe-deploy-gate/` (research.md:90,127,139; plan.md:481,494,749)
- `context/foundation/roadmap.md:94,129,133-146,323`; `context/foundation/prd.md:258-260`
- Railway docs: `deployments/github-autodeploys#wait-for-ci`, `observability/webhooks`
- GitHub: deployments 7184332811, 7179087036, 7179185161; issues #84–#90; runs 39321996335, 39322308603
- Agenci badawczy: zdarzenia Railway, Wait for CI/webhooks, wierność sond, decyzje S-14 (bez TaskCreate)
