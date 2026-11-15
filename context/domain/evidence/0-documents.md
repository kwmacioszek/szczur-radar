# Sources: documents

- documents: 8 requirements, 61 narrative (57 zarchiwizowanych zmian + `context/changes/bootstrap-verification/` + roadmapa + 2 zastąpione wersje w `context/foundation/archive/`), 19 reference, 5 engineering
- discovered from: repo root (`context/foundation/`, `context/changes/`, `context/archive/`, `context/deployment/`, README i instrukcje dla agentów); brak `--docs`
- excluded as documents: `context/map/`, `context/archive/*-repo-map/` (analizy kodu z narzędzia, nie dokumenty domenowe), `context/archive/*-domain-distillation/` i `context/domain/` (poprzednie destylacje), `context/changes/post-m2-audit/` i `context/changes/post-m3-verification-audit/` (audyty kodu; wnioski, nie dokumenty domenowe — decyzje z nich, które przyjął właściciel, są w PRD v3 i roadmapie)
- **Uwaga dla czytającego:** dokumenty M-3 i M-4 (PRD v3, roadmapa, plany, `change.md`) cytują identyfikatory poprzednich destylacji (`R-05`, `D-01`, `Q-01`, `Q-13`…). Traktuj je jako ślad decyzji, nie jako dowód definicji ani reguły. Odpowiedzi właściciela na te pytania (zapisane w roadmapie, frame'ach i planach) są dowodem decyzji.

## Domain documents

| path | kind | what it is about |
|---|---|---|
| `context/foundation/prd.md` | requirements | PRD v3 (`updated: 2026-11-04`, `status: draft`), z blokami „Doprecyzowanie”, w tym nowe FR-033 (jakość per źródło) i FR-034 (przywracanie martwych postów): wizja i problem, persony, kryteria sukcesu, historyjki, FR z priorytetami, NFR, logika biznesowa, kontrola dostępu, poza zakresem, otwarte pytania |
| `context/foundation/shape-notes.md` | requirements | notatki z odkrywania przed PRD; sekcje „Forward” z odłożonymi kierunkami |
| `context/foundation/llm-assessment/contract-v2.md` | requirements | kontrakt oceny treści przez model językowy, wersja 2: kształt wejścia i wyjścia, reguły poprawności, klasy błędów |
| `context/foundation/llm-assessment/examples.md` | requirements | przykłady wejść i wyjść kontraktu |
| `context/foundation/llm-assessment/agent-implementation.md` | requirements / engineering | jak kontrakt ma być odwzorowany w kodzie |
| `context/foundation/llm-assessment/hardening-sources.md` | reference | lista źródeł zewnętrznych (niezweryfikowanych) do przyszłego utwardzania oceny |
| `context/foundation/llm-assessment/README.md` | reference | przewodnik po folderze kontraktu |
| `context/foundation/roadmap.md` | requirements / narrative | roadmapa v5.2: kamienie M-1…M-4 (wszystkie zamknięte, M-4 2026-11-15) z wynikami wycinków S-30–S-45, kotwice zakresu, otwarte pytania, pozycje odłożone (`## Parked`, w tym kandydat M-5), historia kamieni, `## Done` |
| `context/foundation/verification-scope.md` | requirements | zakres kamienia M-4 (weryfikacja i regresja), w tym lista tego, co zostaje ręczne |
| `context/foundation/ui-ux-proposals.md` | requirements | propozycje układu kandydata na kamień M-5 (nowe UI/UX); makiety w `docs/visuals/ui-ux-proposals/` |
| `context/archive/<data>-<zmiana>/` (57 folderów zmian, lista: `ls context/archive`) | narrative | archiwa zmian: `change.md`, `research.md`, `frame.md`, `plan.md`, `plan-brief.md`, `reviews/*.md`, `follow-ups/*.md`; słownictwo i decyzje produktowe z 2026-09-27…2026-11-15. **Priorytetowa próbka (nowa od ostatniej destylacji, M-4):** `2026-11-08-provider-refusal-breaker`, `2026-11-08-restore-dead-posts`, `2026-11-08-dead-posts-daily-digest`, `2026-11-11-per-source-failure-quality`, `2026-11-12-statement-language-quality`, `2026-11-11-post-store-loss-visible`, `2026-11-06-source-native-post-labels`, `2026-11-06-feed-regression-and-paging-bounds`, `2026-11-12-pr-env-scenarios-in-ci`, `2026-11-13-core-mutation-testing` (`mutation-baseline.md`), `2026-11-06-derived-persistence-guards`, `2026-11-11-derived-route-guards`, `2026-11-08-api-contract-shapes-round-two`, `2026-11-12-spa-rules-registry-round-two`, `2026-11-11-contract-bound-mocks`, `2026-11-05-it-test-isolation`. **Próbka M-3:** `2026-10-29-source-isolation`, `2026-10-29-reddit-source`, `2026-10-30-reddit-quality-and-cost`, `2026-10-30-source-aware-feed`, `2026-10-27-run-attention-single-rule`. **Priorytetowa próbka (reguły produktu):** `2026-10-06-first-assessed-wykop-feed`, `2026-10-12-daily-scheduled-run-freshness`, `2026-10-12-filter-by-assessment`, `2026-10-12-post-detail-view`, `2026-10-12-cli-user-onboarding`, `2026-10-15-pipeline-run-history-dashboard`, `2026-10-18-empty-run-signal`, `2026-10-20-attempt-accounting`, `2026-10-24-missed-night-detection`, `2026-10-24-error-visibility`, `2026-10-25-pipeline-without-embabel`, `2026-10-24-reliable-run-notifications` (`frame.md`) |
| `context/changes/bootstrap-verification/` | narrative | logi scaffoldu i audytu modułów z początku projektu |
| `context/foundation/archive/2026-10-01-llm-assessment-contract-v1.md`, `context/foundation/archive/shape-notes-2026-09-26-1137.md` | narrative (superseded) | zastąpione wersje kontraktu i notatek; tylko do listy dokumentów nieaktualnych |
| `README.md` (root) | reference | opis repozytorium |
| `szczur-radar-api/README.md` | reference | lokalna baza, migracje, ustawienia, komendy operatora, testy |
| `szczur-radar-web/README.md` | reference | uruchomienie SPA |
| `REPOSITORY_GUIDELINES.md`, `AGENTS.md`, `CLAUDE.md` | reference | instrukcje dla agentów: twarde reguły, struktura modułów, zasady UI, wdrożenia, workflow |
| `szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/CLAUDE.md` | reference | notatki o silniku przebiegów: miejsca, wejścia, reguły, testy |
| `szczur-radar-api/src/main/java/pl/szczurradar/api/llm/CLAUDE.md` | reference | notatki o klientach modeli: aktualizacje bibliotek, dodawanie dostawcy |
| `szczur-radar-api/src/main/java/pl/szczurradar/api/sources/CLAUDE.md` | reference | notatki o źródłach: dodawanie źródła, układ pakietu, użycie na żywo |
| `szczur-radar-api/src/main/java/pl/szczurradar/api/agents/assessor/CLAUDE.md` | reference | notatki o kontrakcie w kodzie: dodawanie i zmiana reguły |
| `szczur-radar-api/src/test/java/pl/szczurradar/api/measurement/{CLAUDE.md,README.md}` | reference | harness pomiarowy modeli (tylko testy) |
| `tools/db-backup/README.md`, `tools/pipeline-trigger/README.md`, `tools/healthchecks/README.md`, `tools/deploy-gate/README.md`, `tools/ci-runner/README.md` | reference | narzędzia operatora: kopia bazy, codzienny wyzwalacz, checki alarmów, bramka wdrożeń, runnery CI |
| `context/deployment/deploy-plan.md` | engineering | runbooki wdrożeń i dziennik produkcji per wycinek (ok. 2300 linii) |
| `context/foundation/lessons.md` | engineering | lekcje zespołu (reguły z kontekstem i problemem) |
| `context/foundation/tech-stack-api.md`, `context/foundation/tech-stack-web.md` | engineering | decyzje o stosie |
| `context/foundation/infrastructure.md` | engineering | decyzja o platformie wdrożenia |

## Product goals

Z `context/foundation/prd.md` (v3, `updated: 2026-11-04`):

- `prd.md:8-12` — `product_type: data-pipeline`; `target_scale: users: small, qps: low, data_volume: small`.
- `prd.md:23` — „Kilkuset postów i komentarzy dziennie na Wykopie (tag #gielda) i wybranych kanałach Reddita dotyczy inwestowania — wśród nich pojawiają się wartościowe sygnały i informacje, ale giną w szumie. Indywidualni inwestorzy śledzący te platformy nie mają czasu ani energii, by ręcznie przeglądać obie na bieżąco, więc regularnie przegapiają to, co warte uwagi, i tracą czas na przewijanie treści bez wartości.”
- `prd.md:25` — „Twórca narzędzia chce mieć pełną kontrolę nad tym, jak dane są zbierane, przetwarzane i oceniane — własny pipeline — i udostępnić go zaproszonym osobom, zamiast trzymać go tylko dla siebie.”
- `prd.md:29` — persona: „indywidualny inwestor śledzący dyskusje inwestycyjne na Wykopie i Reddicie (twórca narzędzia oraz zaproszone przez niego osoby). Sięga po narzędzie, gdy chce w krótkim czasie zorientować się, czy w ostatnich postach pojawiło się coś wartego uwagi, bez ręcznego przewijania obu platform.”
- `prd.md:33` — persona druga: „Administrator (operator wdrożenia) — zarządza dostępem: tworzy konta dla zaproszonych osób z przypisaną rolą (przez CLI) i przekazuje im link aktywacyjny poza systemem.”
- `prd.md:38-41` — kryteria sukcesu (primary): codzienny automatyczny przepływ „pobiera posty z Wykopu (#gielda) i wybranych subredditów Reddita (FR-023, od 2026-11-01) dla zadanego zakresu dat, mapuje je do wspólnego modelu, odrzuca duplikaty, przetwarza przez LLM według ustalonego schematu i zapisuje wynik do bazy danych”; użytkownik „widzi przetworzone posty w aplikacji webowej: listę chronologiczną z filtrowaniem po podstawowych polach klasyfikacji LLM (FR-011a) oraz widok szczegółowy pojedynczego posta”; „System kont działa end-to-end”; „Admin widzi w dashboardzie historię uruchomień przepływu”.
- `prd.md:44-47` — kryteria secondary (Reddit i ręczne wywołanie oznaczone jako zrealizowane): „Drugie źródło danych — Reddit (wybrane kanały) — dochodzi jako kolejne źródło w tym samym pipeline.”; „Wykres giełdowy z nałożonym wykresem trendu wykrytych sygnałów dla konkretnej spółki.”; dodatkowe sortowanie; ręczne wywołanie przepływu.
- `prd.md:50-52` — guardrails: „Jakość danych z LLM musi być wysoka — ekstrakcja/klasyfikacja bez halucynacji.”; „Koszt zapytań do LLM powinien być minimalizowany, gdzie to możliwe.”; „Niezalogowany/nieuprawniony użytkownik nigdy nie widzi danych ani funkcji spoza swojej roli.”
- `prd.md:309` — logika biznesowa: „Aplikacja opisuje znaczenie każdego zebranego posta tak, by użytkownik mógł szybko ocenić, czy jest wartościowym sygnałem inwestycyjnym: czy treść dotyczy inwestowania, jakich spółek lub tematów dotyczy, jakie stanowisko autor zajmuje wobec każdej z nich (i czy je osłabia, np. „może”), oraz jakie tezy, opinie, fakty, pytania i działania przedstawia — każde poparte dosłownym cytatem z posta.”
- `prd.md:311` — „LLM nie wydaje decyzji „wartościowy / niewartościowy” ani nie ocenia nowości — w MVP tę ocenę robi użytkownik na podstawie opisanej struktury”.
- `prd.md:338-346` — poza zakresem (Non-Goals): automatyczna wysyłka e-maili jako wymóg MVP; 2FA i logowanie zewnętrzne; przetwarzanie komentarzy pod postami; logika backupu w kodzie aplikacji; wysoka dostępność; dodatkowe źródła poza Wykop/Reddit; rozpoznawanie spółek i automatyczna decyzja „wartościowy sygnał”; personalizacja logiki oceny per user; rozbudowany system tożsamości poza `user`/`admin`.

## Change goals

- `context/foundation/roadmap.md:92` i `:435` — M-4 „Weryfikacja i regresja” (zamknięty 2026-11-15): testy łapią błędy, ręczne weryfikacje PR env jako scenariusze w CI, strażnicy liczą zbiory z kodu, PIT na rdzeniu; wymagania PRD v3 z nocy z Redditem (seria odmów, przywracanie martwych postów, podsumowanie nocy zamiast alarmu o martwym poście, jakość per źródło, typ posta słowami źródła).
- `context/foundation/roadmap.md` (`## Milestone History`) — M-1, M-2, M-3 zamknięte.
- `context/foundation/ui-ux-proposals.md`, `## Parked` w roadmapie — kandydat M-5 (nowe UI/UX z testami E2E).
- Cele pojedynczych zmian: `## Outcome` w sekcjach wycinków `roadmap.md` (S-01…S-45, F-01…F-04) oraz `change.md`/`plan.md` w `context/archive/<data>-<zmiana>/`. Nie służą do oceny Core.

## Limitations

- PRD v3 ma status `draft` (`updated: 2026-11-04`), ale bloki „Doprecyzowanie” dopisywano też później, najnowsze z 2026-11-11 i 2026-11-12 (S-35, S-36: `prd.md:271-272`) — najnowsze zdanie o regule może być w takim bloku, nie w treści głównej. Lista bloków: `grep -n Doprecyzowanie context/foundation/prd.md`.
- Część reguł produktu istnieje tylko w archiwach zmian, roadmapie (`## Parked`, notki wersji) i w `deploy-plan.md` (runbooki), nie w PRD; próbka archiwów jest wskazana powyżej, pełne czytanie 57 folderów nie jest wymagane.
- Dokumenty i kod są w dwóch językach: dokumenty po polsku, identyfikatory w kodzie po angielsku; terminy zostają w języku źródła.
