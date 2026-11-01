# Sources: documents

- documents: 8 requirements, 45 narrative (41 zarchiwizowanych zmian + `context/changes/bootstrap-verification/` + roadmapa + 2 zastąpione wersje w `context/foundation/archive/`), 19 reference, 5 engineering
- discovered from: repo root (`context/foundation/`, `context/changes/`, `context/archive/`, `context/deployment/`, README i instrukcje dla agentów); brak `--docs`
- excluded as documents: `context/map/`, `context/archive/2026-10-17-repo-map/`, `context/archive/2026-10-27-repo-map/` (analizy kodu z narzędzia, nie dokumenty domenowe), `context/archive/*-domain-distillation/` i `context/domain/` (poprzednia destylacja), `context/changes/post-m2-audit/` (audyt kodu; wnioski, nie dokument domenowy)
- **Uwaga dla czytającego:** dokumenty M-3 (roadmapa, plany, `change.md`) cytują identyfikatory poprzedniej destylacji (`R-05`, `D-01`, `Q-01`…). Traktuj je jako ślad decyzji, nie jako dowód definicji ani reguły. Odpowiedzi właściciela na te pytania (zapisane w roadmapie, frame'ach i planach) są dowodem decyzji.

## Domain documents

| path | kind | what it is about |
|---|---|---|
| `context/foundation/prd.md` | requirements | PRD v2 (`updated: 2026-10-13`, `status: draft`), dopisywany blokami „Doprecyzowanie” do 2026-10-30: wizja i problem, persony, kryteria sukcesu, historyjki, FR z priorytetami, NFR, logika biznesowa, kontrola dostępu, poza zakresem, otwarte pytania |
| `context/foundation/shape-notes.md` | requirements | notatki z odkrywania przed PRD; sekcje „Forward” z odłożonymi kierunkami |
| `context/foundation/llm-assessment/contract-v2.md` | requirements | kontrakt oceny treści przez model językowy, wersja 2: kształt wejścia i wyjścia, reguły poprawności, klasy błędów |
| `context/foundation/llm-assessment/examples.md` | requirements | przykłady wejść i wyjść kontraktu |
| `context/foundation/llm-assessment/agent-implementation.md` | requirements / engineering | jak kontrakt ma być odwzorowany w kodzie |
| `context/foundation/llm-assessment/hardening-sources.md` | reference | lista źródeł zewnętrznych (niezweryfikowanych) do przyszłego utwardzania oceny |
| `context/foundation/llm-assessment/README.md` | reference | przewodnik po folderze kontraktu |
| `context/foundation/roadmap.md` | requirements / narrative | roadmapa v4.3: kamienie M-1, M-2, M-3 (wszystkie zamknięte) z wynikami wycinków, kotwice zakresu, otwarte pytania, pozycje odłożone (`## Parked`, w tym kandydaci M-4 i M-5), historia kamieni, `## Done` |
| `context/foundation/verification-scope.md` | requirements | zakres kandydata na kamień M-4: weryfikacja i regresja (decyzja użytkownika 2026-10-27) |
| `context/foundation/ui-ux-proposals.md` | requirements | propozycje układu kandydata na kamień M-5 (nowe UI/UX) |
| `context/archive/<data>-<zmiana>/` (41 folderów zmian, lista: `ls context/archive`) | narrative | archiwa zmian: `change.md`, `research.md`, `frame.md`, `plan.md`, `plan-brief.md`, `reviews/*.md`, `follow-ups/*.md`; słownictwo i decyzje produktowe z 2026-09-27…2026-11-01. **Priorytetowa próbka (nowa od ostatniej destylacji):** `2026-10-29-source-isolation`, `2026-10-29-reddit-source`, `2026-10-30-reddit-quality-and-cost` (z `frame.md` i `report-2026-10-31.md`), `2026-10-30-source-aware-feed`, `2026-10-29-api-contract-guard`, `2026-10-27-run-attention-single-rule`, `2026-10-27-closed-sets-guard`, `2026-10-27-alert-ping-single-rule`, `2026-10-27-pipeline-concurrency-review`. **Priorytetowa próbka (reguły produktu):** `2026-10-06-first-assessed-wykop-feed`, `2026-10-12-daily-scheduled-run-freshness`, `2026-10-12-filter-by-assessment`, `2026-10-12-post-detail-view`, `2026-10-12-cli-user-onboarding`, `2026-10-15-pipeline-run-history-dashboard`, `2026-10-18-empty-run-signal`, `2026-10-20-attempt-accounting`, `2026-10-24-missed-night-detection`, `2026-10-24-error-visibility`, `2026-10-25-pipeline-without-embabel`, `2026-10-24-reliable-run-notifications` (`frame.md`) |
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

Z `context/foundation/prd.md` (v2):

- `prd.md:8-12` — `product_type: data-pipeline`; `target_scale: users: small, qps: low, data_volume: small`.
- `prd.md:21` — „Kilkuset postów i komentarzy dziennie na Wykopie (tag #gielda) i wybranych kanałach Reddita dotyczy inwestowania — wśród nich pojawiają się wartościowe sygnały i informacje, ale giną w szumie. Indywidualni inwestorzy śledzący te platformy nie mają czasu ani energii, by ręcznie przeglądać obie na bieżąco, więc regularnie przegapiają to, co warte uwagi, i tracą czas na przewijanie treści bez wartości.”
- `prd.md:23` — „Twórca narzędzia chce mieć pełną kontrolę nad tym, jak dane są zbierane, przetwarzane i oceniane — własny pipeline — i udostępnić go zaproszonym osobom, zamiast trzymać go tylko dla siebie.”
- `prd.md:27` — persona: „indywidualny inwestor śledzący dyskusje inwestycyjne na Wykopie i Reddicie (twórca narzędzia oraz zaproszone przez niego osoby). Sięga po narzędzie, gdy chce w krótkim czasie zorientować się, czy w ostatnich postach pojawiło się coś wartego uwagi, bez ręcznego przewijania obu platform.”
- `prd.md:31` — persona druga: „Administrator (operator wdrożenia) — zarządza dostępem: tworzy konta dla zaproszonych osób z przypisaną rolą (przez CLI) i przekazuje im link aktywacyjny poza systemem.”
- `prd.md:36-39` — kryteria sukcesu (primary): codzienny automatyczny przepływ „pobiera posty z Wykopu (#gielda) dla zadanego zakresu dat/godzin, mapuje je do wspólnego modelu, odrzuca duplikaty, przetwarza przez LLM według ustalonego schematu i zapisuje wynik do bazy danych”; użytkownik „widzi przetworzone posty w aplikacji webowej: listę chronologiczną z filtrowaniem po podstawowych polach klasyfikacji LLM (FR-011a) oraz widok szczegółowy pojedynczego posta”; „System kont działa end-to-end”; „Admin widzi w dashboardzie historię uruchomień przepływu”.
- `prd.md:42-45` — kryteria secondary: „Drugie źródło danych — Reddit (wybrane kanały) — dochodzi jako kolejne źródło w tym samym pipeline.”; „Wykres giełdowy z nałożonym wykresem trendu wykrytych sygnałów dla konkretnej spółki.”; dodatkowe sortowanie; ręczne wywołanie przepływu.
- `prd.md:48-50` — guardrails: „Jakość danych z LLM musi być wysoka — ekstrakcja/klasyfikacja bez halucynacji.”; „Koszt zapytań do LLM powinien być minimalizowany, gdzie to możliwe.”; „Niezalogowany/nieuprawniony użytkownik nigdy nie widzi danych ani funkcji spoza swojej roli.”
- `prd.md:286` — logika biznesowa: „Aplikacja opisuje znaczenie każdego zebranego posta tak, by użytkownik mógł szybko ocenić, czy jest wartościowym sygnałem inwestycyjnym: czy treść dotyczy inwestowania, jakich spółek lub tematów dotyczy, jakie stanowisko autor zajmuje wobec każdej z nich (i czy je osłabia, np. „może”), oraz jakie tezy, opinie, fakty, pytania i działania przedstawia — każde poparte dosłownym cytatem z posta.”
- `prd.md:288` — „LLM nie wydaje decyzji „wartościowy / niewartościowy” ani nie ocenia nowości — w MVP tę ocenę robi użytkownik na podstawie opisanej struktury”.
- `prd.md:315-323` — poza zakresem (Non-Goals): automatyczna wysyłka e-maili jako wymóg MVP; 2FA i logowanie zewnętrzne; przetwarzanie komentarzy pod postami; logika backupu w kodzie aplikacji; wysoka dostępność; dodatkowe źródła poza Wykop/Reddit; rozpoznawanie spółek i automatyczna decyzja „wartościowy sygnał”; personalizacja logiki oceny per user; rozbudowany system tożsamości poza `user`/`admin`.

## Change goals

- `context/foundation/roadmap.md:90` — M-3 (zamknięty 2026-11-01): „Awaria albo cisza jednego źródła nie psuje drugiego, ślepe punkty z audytu po M-2 są domknięte (bramka wdrożeń, reguły w kilku miejscach, niestabilne testy silnika), a posty z wybranych subredditów trafiają do tego samego feedu i tej samej oceny co Wykop — z jakością i kosztem zmierzonymi, zanim pójdą na produkcję.”
- `context/foundation/roadmap.md:348` — M-2 (zamknięty 2026-10-27), `roadmap.md:347` — M-1 (zamknięty 2026-10-17).
- `context/foundation/verification-scope.md`, `roadmap.md:308-309` — kandydaci M-4 (weryfikacja i regresja) i M-5 (nowe UI/UX).
- Cele pojedynczych zmian: `## Outcome` w sekcjach wycinków `roadmap.md` (S-01…S-29, F-01…F-04) oraz `change.md`/`plan.md` w `context/archive/<data>-<zmiana>/`. Nie służą do oceny Core.

## Limitations

- PRD ma status `draft` i frontmatter `updated: 2026-10-13`, ale dopisywane są do niego bloki „Doprecyzowanie” z późniejszymi decyzjami, najnowsze z 2026-10-30 (`prd.md:125, 190, 210, 225-227, 242-243, 263-271, 273-275, 281, 309`) — najnowsze zdanie o regule może być w takim bloku, nie w treści głównej.
- Część reguł produktu istnieje tylko w archiwach zmian, roadmapie (`## Parked`, notki wersji) i w `deploy-plan.md` (runbooki), nie w PRD; próbka archiwów jest wskazana powyżej, pełne czytanie 41 folderów nie jest wymagane.
- Dokumenty i kod są w dwóch językach: dokumenty po polsku, identyfikatory w kodzie po angielsku; terminy zostają w języku źródła.
