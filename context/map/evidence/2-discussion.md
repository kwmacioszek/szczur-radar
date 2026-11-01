# Evidence: discussion

Źródła: `gh` (kwmacioszek/szczur-radar, tylko odczyt: 85 PR, 50 issue, nagłówki 607 commitów gałęzi) oraz git na HEAD `5b4ac92`. Okno: cała historia, 2026-09-26 .. 2026-11-01. Limity: `gh pr list --json commits` przekracza limit GraphQL, więc commity PR pobrano stronicowanym `gh api graphql`; listy plików PR nie były pobierane. Stan repo czytany z gita, nie z drzewa roboczego.

Podział „przed / M-3”: przed `e5ab295` (77 commitów) / od `e5ab295` włącznie (13 commitów, 5,4 dnia). Pre-M-3 obejmuje 31 dni, więc liczby bezwzględne nie są porównywalne; porównuj udziały. Surowe dane: `context/map/.work/discussion/`.

Znaczniki: `evidence` = wynik polecenia, `inference` = wniosek lub przypisanie po nazwie, `unknown` = martwy punkt.

## Buzz per capability

Komórki `a / b` to przed / M-3. Przypisanie mechaniczne wg kontraktu: commit z numerem PR liczy się dla każdej możliwości, której plik dotknął. Wszystkie kolumny `evidence`, poza „issue” i „plany” (przypisanie po nazwie, `inference`; `.work/discussion/change-map.tsv`, zmiana wielomożliwościowa liczona w każdej).

| Możliwość | PR dotykające | Commity przekrojowe (>3) | Zamknięte bez merge'a | Review ludzkie | Reverty na `main` | Issue | Otwarte | Bugi z etykietą | Plany | Markery /1k |
|---|---|---|---|---|---|---|---|---|---|---|
| auth | 12 / 1 | 10 / 1 | 0 | 0 | 0 | 3 / 0 | 0 | 0 | 3 / 0 | 0 |
| accounts | 7 / 1 | 6 / 1 | 0 | 0 | 0 | 1 / 0 | 0 | 0 | 1 / 0 | 0 |
| ingest | 6 / 2 | 4 / 2 | 0 | 0 | 0 | 2 / 1 | 1 (#121) | 0 | 2 / 1 | 0 |
| assessment | 7 / 0 | 7 / 0 | 1 / 0 (#23) | 0 | 0 | 1 / 0 | 0 | 0 | 1 / 0 | 0 |
| llm-providers | 11 / 0 | 9 / 0 | 0 | 0 | 0 | 3 / 0 | 0 | 0 | 3 / 0 | 0 |
| pipeline-runs | 13 / 6 | 10 / 4 | 0 | 0 | 0 | 6 / 2 | 0 | 0 | 6 / 2 | 0 |
| daily-run | 5 / 4 | 4 / 3 | 0 | 0 | 0 | 1 / 1 | 0 | 0 | 1 / 1 | 0 |
| feed | 11 / 3 | 10 / 3 | 0 | 0 | 0 | 3 / 1 | 0 | 0 | 3 / 1 | 0 |
| admin-console | 12 / 5 | 12 / 4 | 0 | 0 | 0 | 4 / 1 | 0 | 0 | 3 / 1 | 0 |
| operator-alerts | 4 / 1 | 2 / 0 | 0 | 0 | 0 | 2 / 1 | 0 | 0 | 2 / 1 | 0 |
| db-backup | 6 / 3 | 2 / 2 | 0 | 0 | 0 | 2 / 0 | 0 | 0 | 2 / 0 | 0 (1 fałszywe) |
| model-evaluation | 6 / 2 | 4 / 1 | 1 / 0 (#23) | 0 | 0 | 1 / 1 | 0 | 0 | 1 / 1 | 0 |
| shared-ui | 14 / 4 | 12 / 3 | 0 | 0 | 0 | 2 / 0 | 0 | 0 | 2 / 0 | 0 |
| shared-foundations | 14 / 6 | 14 / 5 | 0 | 0 | 0 | 0 / 2 | 0 | 0 | 0 / 2 | 0 |
| platform | 32 / 2 | 14 / 1 | 3 / 0 (#10, #92, #97) | 0 | 0 | 15 / 1 | 0 | 0 | 6 / 1 | 0 |
| docs-planning (proza) | 67 / 13 | 19 / 5 | 0 | 0 | 0 | 1 / 0 | 0 | 0 | — | — |

Mianowniki `evidence`: 81 z 90 commitów ma numer PR; commity przekrojowe to 19 z 77 przed i 5 z 13 w M-3. Nieprzypisane: 0 commitów; 1 issue (#61) i foldery obu audytów są przekrojowe, więc ich ustalenia liczę osobno. Sumy zgodne z `coverage.txt` (platform 37, pipeline-runs 19, docs-planning 89).

Udział commitów okresu (`evidence`, policzone z tabeli): platform 45 % → 15 %, pipeline-runs 17 % → 46 %, shared-foundations 18 % → 46 %, admin-console 16 % → 38 %, daily-run 6 % → 31 %.

Review: 0 wpisów `reviews` na 85 PR (`evidence`). To ustalenie, nie mediana. Rolę przeglądu pełnią artefakty agenta w `context/archive/*/reviews/`: 48 przeglądów implementacji (36 + 11 + `pipeline-concurrency-review` bez nowych ustaleń) i 21 przeglądów planu (`evidence`).

Największe PR według wierszy kodu (`evidence`): S-02 158, F-02 138, F-03 98, S-06 76, S-10 69, S-27 62 (M-3), S-07 53, S-20 46 (M-3). Liczby `additions` z forge pomijam, bo obejmują nagrania i fikstury.

Tarcie po nazwie (przed / M-3). `inference` dla przypisania, `evidence` dla liczb:

| Możliwość | Zmiany | Nieplanowane `fix` wg tematu | Krytyczne w plan-review | Impl-review W+O | NEEDS ATTENTION | Audyt M-1 | Audyt M-2 | Parked dług (nowe od `e5ab295`) |
|---|---|---|---|---|---|---|---|---|
| auth | 3 / 0 | 0 / 0 | 2 / 0 | 12+5 / 0 | 4 / 0 | 0 | 0 | 2 (1) |
| accounts | 1 / 0 | 1 / 0 | 0 | 3+5 / 0 | 1 / 0 | 0 | 0 | 2 (1) |
| ingest | 3 / 1 | 0 / 0 | 0 | 13+15 / 5+8 | 3 / 1 | 3 | 1 | 1 (0) |
| assessment | 1 / 0 | 0 / 0 | 0 | 19+21 / 0 | 4 / 0 | 1 | 0 | 3 (1) |
| llm-providers | 3 / 0 | 2 / 0 | 1 / 0 | 5+9 / 0 | 1 / 0 | 7 | 0 | 2 (1) |
| pipeline-runs | 6 / 2 | 1 / 0 | 3 / 0 | 25+31 / 4+8 | 7 / 1 | 8 | 6 | 4 (1) |
| daily-run | 1 / 1 | 1 / 0 | 0 | 1+4 / 2+2 | 0 / 1 | 2 | 3 | 3 (1) |
| feed | 3 / 1 | 1 / 0 | 0 | 7+12 / 2+2 | 1 / 1 | 0 | 1 | 1 (0) |
| admin-console | 4 / 1 | 1 / 0 | 1 / 0 | 4+12 / 0+2 | 0 / 0 | 1 | 1 | 3 (0) |
| operator-alerts | 2 / 1 | 0 / 1 | 2 / 0 | 4+14 / 3+2 | 1 / 1 | 2 | 3 | 4 (1) |
| db-backup | 2 / 0 | 0 / 0 | 0 | 8+10 / 0 | 2 / 0 | 1 | 1 | 1 (0) |
| model-evaluation | 1 / 1 | 0 / 0 | 0 | 19+21 / 2+5 | 4 / 0 | 0 | 1 | 1 (0) |
| shared-ui | 2 / 0 | 1 / 0 | 0 | 2+2 / 0 | 0 / 0 | 0 | 1 | 1 (1) |
| shared-foundations | 0 / 2 | 0 / 0 | 0 | 0 / 3+8 | 0 / 0 | 0 | 4 | 2 (1) |
| platform | 9 / 1 | 13 / 0 | 4 / 2 | 21+21 / 1+5 | 5 / 1 | 8 | 5 | 5 (1) |

Audyt M-1 (33 ustalenia, bez C15) i audyt M-2 (27, bez stanów E5, K0, K6) powstały przed `e5ab295`; pracę z audytu M-2 wykonał M-3. Rozkład: `.work/discussion/audit-m1.tsv`, `audit-findings.tsv`, `parked.tsv`, `unplanned-topics.tsv`.

## Friction

Wskaźniki zbiorcze, przed / M-3 (`evidence`, klasyfikacja nagłówków regexem: `.work/discussion/fix-commits.tsv`):

| Wskaźnik | Przed | M-3 |
|---|---|---|
| Commity gałęzi zmergowanych zmian, bez merge'y | 451 | 99 |
| Nieplanowane `fix` na gałęzi | 21 (4,7 %) | 1 (1,0 %) |
| `fix` z przeglądu | 32 | 10 |
| `fix` jako faza planu (`pN`) | 13 | 2 |
| Impl-review z NEEDS ATTENTION | 23 z 36 (64 %) | 5 z 11 (45 %) |
| Ostrzeżenia na impl-review | 2,8 | 1,8 |
| Krytyczne w impl-review | 0 | 0 |
| Krytyczne w plan-review | 10 na 18 przeglądów | 2 na 3 (oba S-21) |
| Zmiany z plan-review | 17 z 28 | 3 z 10 (S-20, S-21, S-28) |
| Commity weryfikacji i księgowania na gałęzi | 205 (45 %) | 48 (48 %) |

Reverty i hotfixy na `main` (`evidence`): 0 commitów `Revert`, jedyne trafienie `git log --grep` to treść `f2974f2`. Tematy `fix` na `main`: 3 (`646df78`, `9ef707e`, `427ebc3`), wszystkie przed M-3. Reverty wewnątrz PR: 2, oba cofają tymczasową zmianę do testu w PR env (patrz Mechanical signals).

PR zamknięte bez merge'a: 4 z 85, wszystkie przed M-3, żaden nie odrzucony w przeglądzie (`evidence`, komentarze):
- #10: test zakresu tokenu Railway.
- #23: F-02 w Pythonie, porzucone na rzecz Javy i zastąpione #31.
- #92 i #97: ta sama gałąź `deploy-verification-hardening`, zastąpione przez #99, bo Railway po incydencie nie odtworzył PR env.

Drugie podejścia:

| Możliwość | Łańcuch | Oznaczenie |
|---|---|---|
| platform | S-14 `deploy-verify` (`2abc64c`, 10-19) → pliki `deploy-verify.yml` i `verify-deploy.sh` usunięte po 4 dniach w `9ef690b` (monitor zewnętrzny, trzeci PR z tej gałęzi) → S-21 przepisuje `gate.jq` i `verify-apply.sh` (`f2974f2`, 10-28, M-3). Audyt P1, P2: bramka zakładała, że każda zmiana IaC deployuje, a diagnoza z #73 była w połowie błędna. #73 otwarte ponownie 10-21, zamknięte 10-24, korekta 10-28. | `evidence`; prawdziwe drugie podejście |
| assessment, model-evaluation | Kontrakt V1 → V2 (2026-10-01), F-02 #23 → #31. Żaden z 12 modeli nie spełnił progów, progi złagodzone do ≥70 % / ≤10 % (10-06). S-28: Outcome zmieniony z „spełnienie progów” na „obraz konfiguracji” (v4.2), Reddit włączony bez napraw, naprawy w Parked. | `evidence`; jedyne miejsce, gdzie cel jakości obniżono dwa razy |
| pipeline-runs | S-02 na Embabel (10-09) → S-17 bez Embabel (`e869a07`, 10-27), 18 dni. Lekcja FIRE_ONCE. Wcześniejsze S-08, S-09, S-11 i późniejsze S-20, S-26 wracają do tego samego silnika, przeważnie z audytu. S-26 domyka niestabilny test dyspozytora (E2). | `evidence` dla łańcucha; `inference`: planowe utwardzanie, nie seria awarii |
| operator-alerts | F-04 → S-18 → S-25, ten sam kanał. Reguła ping URL miała cztery warianty (P4). | `evidence` |
| ingest | S-02 → S-16 (kompletność Wykopu, B5, B6) → S-27 (wspólna pętla `HttpRetry`). | `inference`: rozszerzanie planowane w `sources/CLAUDE.md`, nie porażka |
| db-backup | F-01 (#44, #46) → S-15 (C9: listy tabel odstawały od migracji). #74 otwarte ponownie razem z #73. | `evidence` |
| daily-run, feed | S-05 → S-29 „świeżość bez kłamstwa”; pozycja Parked o nieudanym odczycie daty zostaje. | `evidence` |
| auth, accounts | S-01 w Pythonie → F-03 w Javie: 16 plików usuniętych po medianie 3 dni. Obejście filtra Origin znalezione po merge'u, poprawione w #28. | `evidence`; przepisanie planowe, ale po nim powstała lekcja o przeglądzie przed merge'em |
| shared-foundations | Brak. S-23 i S-24 to nowe strażniki, pierwsza praca w M-3. | `evidence` |

Incydent Railway 2026-10-20/21 to jedna przyczyna wielu sygnałów (`evidence`, komentarze): close/reopen na #92, #93, #94, #95, #97, rotacja sekretów na gałęzi S-09, wpisy #98. Ten sam objaw (PR env bez deploymentów) wrócił na #124 w M-3.

Issue `deploy-health`: 7 z 50, zero prawdziwych awarii wdrożenia wśród opisanych (`evidence`): #84–#88 to ręczne testy ścieżki alarmu. #90 i #98 pokazują sondy 200 przy stanie `failure`; #98 zamknięto jako skutek incydentu, przyczyna #90 to `unknown`.

Otwarte: tylko #121 (S-27), czeka na noce 2 i 4 listopada (`evidence`). Bugi z etykietą: 0, a etykiety istnieją tylko dwie (`deploy-health`, `enhancement`). Tracker nie jest rejestrem błędów; dług żyje w `## Parked` i audytach (`inference`).

Ogon po merge'u (merge → zamknięcie issue, `evidence`): najdłużej #51 (5,4 d), #73 i #74 (4,9 d), #17 (3,5 d). Mediana M-2 0,38 d, M-3 0,01 d. W M-3 5 z 9 issue zamknięto w 4–14 min po merge'u, z komentarzem o CI i deployu; tylko S-20 i S-25 czekały do następnego dnia. `inference`: płytsza weryfikacja produkcji w wycinkach bez zmian zachowania, co pasuje do zakresu M-4. Nie oceniam, czy wystarczyła.

Dlaczego głośno (`inference`):
- platform: **psuło się i wymagało powtórek**. Przed M-3 tu trafiało 13 z 21 nieplanowanych poprawek i 6 z 12 krytycznych w plan-review; w M-3 wyraźny spadek, ale dwie krytyczne wróciły w S-21.
- pipeline-runs: **ważne**. Najwięcej planów i ustaleń obu audytów, wysoka liczba przeglądów, a jednak 1 nieplanowana poprawka i 0 revertów.
- assessment, model-evaluation: **cel, którego nie osiągnięto**. Mało zmian, jedna duża, cel obniżany, naprawy zaparkowane.
- shared-foundations, admin-console, daily-run w M-3: **dotykane przez kontrakt między warstwami**. Wysoki udział, małe tarcie.

## Planning attention

Liczby przed / M-3 (`evidence` dla liczb, `inference` dla przypisania po nazwie; zmiana wielomożliwościowa liczona w każdej, `*-repo-map` i `*-domain-distillation` pominięte):

| Możliwość | Linie `plan.md` | Pliki `research.md` | Wiersze prozy w folderach zmian (bez rename) | `frame.md` |
|---|---|---|---|---|
| pipeline-runs | 3479 / 995 | 4 / 0 | 46 / 10 | 1 (S-11) |
| platform | 2896 / 326 | 4 / 1 | 48 / 6 | 1 (deploy-verification-hardening) |
| admin-console | 1736 / 272 | 3 / 0 | 28 / 5 | 1 (S-12) |
| auth | 1684 / 0 | 2 / 0 | 30 / 0 | 0 |
| feed | 1402 / 387 | 3 / 0 | 23 / 5 | 0 |
| ingest | 1083 / 519 | 1 / 1 | 16 / 7 | 0 |
| llm-providers | 1315 / 0 | 1 / 0 | 21 / 0 | 0 |
| db-backup | 1173 / 0 | 2 / 0 | 11 / 0 | 0 |
| operator-alerts | 1020 / 278 | 0 / 0 | 12 / 5 | 1 (F-04) |
| daily-run | 676 / 387 | 1 / 0 | 6 / 5 | 0 |
| model-evaluation | 599 / 387 | 1 / 0 | 72 / 17 | 2 (F-02, S-28) |
| shared-ui | 884 / 0 | 2 / 0 | 24 / 0 | 0 |
| shared-foundations | 0 / 672 | 0 / 1 | 0 / 9 | 0 |
| accounts | 630 / 0 | 1 / 0 | 7 / 0 | 0 |
| assessment | 599 / 0 | 1 / 0 | 72 / 0 | 1 (F-02) |

Artefakty poza folderami zmian (`evidence`):
- `context/changes/post-m2-audit/` jest przed `e5ab295`, status `preparing`, nadal poza archiwum. Z 27 ustaleń: 21 powiązanych z wycinkami `done`, 5 w Parked (E3, P6, P8, P9, K5), 1 bez działania (P7). Powiązanie to nie dowód zamknięcia (`inference`). S7 domknięte tylko pomiarem; naprawy w Parked. S4 w roadmapie zostało pytaniem przy S-27.
- `## Parked`: 32 → 36. Dodane od `e5ab295`: M-4, M-5, niska waga audytu M-2, `posts.language`, naprawy oceny Reddita, porównanie modeli. Usunięte: FR-023, izolacja źródeł. Typy: 18 dług, 17 produkt, 1 przekrój (`inference` dla typu i przypisania).
- Open Roadmap Questions: 5. Weryfikacja produkcji z M-1 (feed, admin-console), żywy Railway (platform), FR-025 (poza mapą), budżet LLM rozstrzygnięty w S-28 (model-evaluation), status PRD `draft` (docs-planning).
- PRD Open Questions: 4, z czego 2 rozstrzygnięte. Otwarte: `product_type` (docs-planning), FR-025 (poza mapą).
- `lessons.md`: 21 lekcji, ok. 10 o procesie pracy z agentem, 5 o Railway i platformie, 2 ingest, po 1 assessment, pipeline-runs, shared-foundations, db-backup (`inference`). W M-3 dopisana 1.
- Pliki prozy o największym churnie, przed+M-3 / w tym M-3: `roadmap.md` 56/13, `deploy-plan.md` 55/6, `REPOSITORY_GUIDELINES.md` 38/8, `pipeline/CLAUDE.md` 19/6, `sources/CLAUDE.md` 9/4.
- `deploy-plan.md` ma 2301 linii. Największe sekcje: F-01 206, F-04 162, S-06 148, S-14 147. Największe w M-3: S-27 115, S-20 91.
- Kandydaci na kolejne kamienie: `context/foundation/verification-scope.md` (M-4), `context/foundation/ui-ux-proposals.md` (M-5).

Wniosek o M-3 (`inference`): uwaga przeszła z platformy na kontrakt silnik ↔ SPA ↔ źródła, zgodnie z audytem M-2. Bramką stał się przegląd implementacji, a przegląd planu stał się wyjątkiem. S-27, największa kodowo zmiana M-3, nie ma plan-review; miała NEEDS ATTENTION w przeglądzie fazy 5.

## Debt markers

`git grep -E 'TODO|FIXME|HACK|XXX'` na `5b4ac92`, po odfiltrowaniu szumu (`evidence`):
- Kod: 1 trafienie, `mktemp … XXXXXX` w `tools/db-backup/restore-check.sh:47`. To nie dług. Pozostałe 14 możliwości: 0 trafień na łącznie ok. 65 tys. linii.
- Proza: 19 trafień na 70 934 linii (nieczytane, tylko liczba).
- Podział na okresy nie ma sensu przy zerze.

`inference`: markery tu nic nie mierzą, bo dług ma nazwane wpisy w `## Parked`. Ich rozkład jest w drugiej tabeli.

Nieaktualny wpis Parked (`evidence`): „Dni bez danych przy drugim źródle (S-13, F2)” każe dopisać test, a `MissingDaysTest.java:79,87-91` ma już przypadki z `Source.REDDIT`. Roadmapa ma więc co najmniej jeden wpis do usunięcia; audyt M-2 opisywał ten sam typ rozjazdu dokumentów z kodem.

## Mechanical signals

Odfiltrowane regułami `false-signals.md`, poza rankingiem ryzyka:

| Sygnał | Liczba | Reguła | Powód |
|---|---|---|---|
| Nieplanowane `fix` w platform | 13 / 0 | 5, 4 | Konfiguracja Railway i CI poprawiana iteracyjnie, wspólna przyczyna: nowa platforma. Ranking opiera się na łańcuchu weryfikacji wdrożeń, nie na tym stosunku. |
| Issue i komentarze platform | 15 / 1 issue | 4, 7 | 7 z 15 to `deploy-health`; komentarze na issue to wymagane przez workflow statusy. |
| Komentarze na PR | 76 `railway-app`, 16 z konta `kwmacioszek` | 7 | Bot i notatki operacyjne (close/reopen, wyniki weryfikacji). Żaden nie jest review. |
| Reverty w PR | 2 (#81, #124) | 12 | Cofnięcie tymczasowej zmiany crona do testu w PR env. |
| Jeden `fix` z przeglądu na zmianę | 32 / 10 | 3 | Krok workflow, nie miara jakości. Liczą się ustalenia, nie te commity. |
| Pliki usunięte w F-03 | 30 wierszy | 11, 12 | Zaplanowane przepisanie z Pythona. Historia, której już nie ma. |
| PR dotykające shared-foundations | 14 / 6, przekrojowe 14 / 5 | 12, 2 | Współdzielony kod dotykany przy okazji wycinków; dotyk nie jest pracą nad tą możliwością. |
| shared-foundations tylko w M-3 w tabeli planów | 2 zmiany | 4 | Nowa możliwość w budowie jest gęsto planowana. |
| Proza model-evaluation i assessment | 72 / 17 i 72 / 0 | 4, 8 | Jedna zmiana F-02 liczona w obu; raporty pomiaru. |
| Parked produktowe | 17 pozycji | 8 | Decyzje o zakresie (`nice-to-have`), nie tarcie. |
| Commity weryfikacji i księgowania | 45 % / 48 % | 3 | Wynik regexa po nagłówkach, kształt workflow `Progress`. |
| Koncentracja na jednej osobie | 1 człowiek, 83 z 90 commitów z trailerem Claude | 6 | Baseline, nie ustalenie. |

## Unknowns

- `unknown`: czy komentarze z konta `kwmacioszek` pisał człowiek, czy agent na jego tokenie. Treść sugeruje agenta, `gh` tego nie rozróżnia.
- `unknown (external)`: issue i przebiegi `verify.yml` w `kwmacioszek/szczur-radar-monitor`. Od `9ef690b` alarmy `deploy-health` żyją tam, więc spadek do zera w tym repo o niczym nie świadczy.
- `unknown`: czerwone przebiegi CI per job i test. `gh run list` nieużyte, poza zakresem opisu. Niestabilność znana tylko z audytu (E2) i Parked (losowe padanie budowy obrazów).
- `unknown`: błędy produkcyjne. Error trackera nie ma (decyzja S-18); logi Railway, healthchecks.io i Telegram poza zakresem tego skanu.
- `unknown`: tarcia w PR ukryte przez squash i przepisanie historii gałęzi. Nagłówki commitów PR pokazują tylko to, co przetrwało rebase.
- `unknown`: czy S-27 działa stabilnie na nocach z Redditem. #121 otwarte, pierwsza noc 2 listopada.
- `unknown`: przyczyna #90 (sondy 200 przy stanie `failure`, brak komentarza zamykającego).
- `inference`: klasyfikacja `fix` opiera się na konwencji nagłówków; `change-map.tsv` i tematy `unplanned-topics.tsv` to przypisanie ręczne po nazwie. S-08, S-09, S-12, S-19, S-26 i F-03 nie mają wiersza w kolumnie Evidence kontraktu.
- Rekomendacja narzędzia: brak potrzeby instalacji. Zamiast `rg` użyto `git grep` na `5b4ac92`, bo drzewo robocze zmienia równoległa sesja.

## Polecenia

- `gh repo view`; `gh pr list --state all --limit 200|100 --json …`; `gh issue list --state all --limit 300 --json …`; `gh api graphql --paginate` (commity PR, nagłówki i pełne wiadomości).
- `git log --format=… --grep=…`, `git ls-tree -r --name-only 5b4ac92`, `git show 5b4ac92:…`, `git show d7c6113:context/foundation/roadmap.md`, `git grep -c -I ''`, `git grep -n -I -E 'TODO|FIXME|HACK|XXX'`, `git grep -n -i reddit`.
- `python3 -I` na `.work/commit-files.tsv` i `.work/attribute.py`; skrypty i wyniki w `.work/discussion/` (`reviews.py`, `analyze.py`, `agg.py`, `markers.py`, `change-map.tsv`, `per-change.json`, `reviews.json`, `issues-mapped.json`, `fix-commits.tsv`, `audit-m1.tsv`, `audit-findings.tsv`, `parked.tsv`, `unplanned-topics.tsv`).
