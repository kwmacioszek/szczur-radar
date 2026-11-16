# Evidence: discussion

Źródła: `gh` (kwmacioszek/szczur-radar, tylko odczyt: 107 PR, 66 issue; pliki `.work/prs.json`, `.work/issues.json`), git na HEAD `ef56ddc`, `commit-files.tsv`, archiwum `context/archive/2026-11-*`. Okno: cała historia 2026-09-26 .. 2026-11-15. Skrypt: `.work/disc.py`. Podział: przed M-4 (93 commity, w tym #138) / M-4 (19 commitów: #140, #156–#173 bez #159). Jeden człowiek w oknie (baseline z kontraktu), więc żadnej koncentracji nie traktuję jako ustalenia. Znaczniki: `evidence` = wynik polecenia, `inference` = przypisanie po nazwie, `unknown` = martwy punkt. Limity: brak `gh pr view` per PR (nie potrzebne); `additions/deletions` z forge zawierają fikstury i docs, więc rozmiaru PR nie rankuję.

## Buzz per capability
Komórki `przed / M-4`. Commity: raz na capability, której dotykają (pliki `noise == "-"`). Zmiany = pliki. Plany/impl-review przypisane po nazwie zmiany (`inference`). `impl F` = liczba `### F<n>` w `reviews/impl-review*.md`; NA = werdykt NEEDS ATTENTION. Review ludzkie, zamknięte bez merge'a, reverty, markery: kolumny stałe (patrz niżej).

| Capability | Commity | Zmiany (pliki) | Plany M-4 | impl F (M-4) | NA | Fix w treści squasha* |
|---|---|---|---|---|---|---|
| regression-harness | 15 / 12 | 24 / 55 | 8 | 50 (6,3/zmianę) | 4 z 8 | n/d |
| pipeline-runs | 19 / 11 | 179 / 41 | 2 | 8 | 1 z 2 | n/d |
| admin-console | 17 / 7 | 97 / 48 | 1 | 10 | 0 | n/d |
| feed | 13 / 9 | 88 / 28 | 2 | 6 | 0 | n/d |
| operator-alerts | 5 / 5 | 34 / 38 | 1 | 8 | 1 z 1 | n/d |
| quality-monitoring | 0 / 4 | 0 / 31 | 2 | 14 | 1 z 2 | n/d |
| daily-run | 9 / 7 | 32 / 23 | 0 (współdzielone z S-34/S-35) | — | — | |
| shared-foundations (kubełek) | 23 / 11 | 130 / 24 | — | — | — | |
| shared-ui (kubełek) | 18 / 4 | 65 / 7 | — | — | — | |
| ingest | 8 / 4 | 99 / 8 | 0 | — | — | |
| assessment | 7 / 2 | 100 / 9 | 0 | — | — | |
| accounts | 8 / 3 | 81 / 5 | 0 | — | — | |
| auth | 13 / 4 | 92 / 8 | 0 | — | — | |
| platform | 37 / 4 | 173 / 12 | 0 w M-4 (`deploy-verification-hardening` leży nieśledzony w `context/changes/`, poza M-4) | — | — | |
| db-backup | 9 / 1 | 20 / 1 | 0 | — | — | |
| llm-providers | 10 / 0 | 124 / 0 | 0 | — | — | |
| model-evaluation | 8 / 1 | 42 / 1 | 0 | — | — | |
| docs-planning (osobno) | 92 / 19 | 916 / 205 | — | — | — | |

Wspólne (`evidence`): PR-y M-4 = 19 (wszystkie zmergowane), commity przekrojowe (> 3 capability) w M-4 = 13 z 19 (np. #169 12 capability, #162 i #166 po 10, #171 9) — mianownik dla ostrożności przy co-change i „commity per capability". Nieprzypisane: 0 plików. Zamknięte bez merge'a w całej historii: 4 PR (#10, #23, #92, #97), wszystkie przed M-4 (`evidence`); w M-4 0. Review ludzkie na PR: 0 wpisów `reviews` na 107 PR (komentarze: 98 `railway-app` bot, 16 właściciel) — to ustalenie, nie mediana. Reverty na `main`: 0 (`git log` po temacie `revert`/`hotfix`: 0). Tematy `fix` na PR: 3 w całej historii, w M-4 tylko #164 („S-34 closeout: poprawki z przeglądu"). Markery TODO/FIXME/HACK/XXX w kodzie na HEAD: 0 we wszystkich capability (`git grep`, ~110 tys. wierszy).
\* Podtematy squasha (`* fix…`, druga warstwa, oznaczona jako taka): przed 66 z 505 (13 %), M-4 12 z 133 (9 %). Rozbicie per capability nie liczone (podcommity nie mają własnych plików; byłby to prywatny schemat).

Issue (`evidence`): 66, wszystkie zamknięte, 0 otwartych. Etykiety: `deploy-health` 7 (#84–#90, #98: „Niezdrowe wdrożenie", PR env 80/81, lag-test, production), `enhancement` 1. Issue M-4 = #139–#155 (S-30..S-45, jedno na wycinek), wszystkie zamknięte; 0 bugów etykietowanych w M-4. Poprzednia mapa: #121 otwarte (Reddit); teraz zamknięte.

## Friction
- Drugie podejścia (`evidence` z tematów PR): S-34 = #162 (zmiana) + #164 (closeout z poprawkami przeglądu, migracja V14 i lekcja): jedyny wycinek M-4 z dwoma PR. #164 miał 385+/117− w 23 plikach przy #162 3063+/917− w 55; poprawki przeglądu potrzebowały osobnego PR i migracji (fix-forward), bo #162 zmergowano przed końcem przeglądu (`inference` z tematu).
- Rundy 2 jako wycinki, nie poprawki: S-41 (#163, „druga runda — kształty") po S-24 (#131), S-42 (#170, „druga runda") po rejestrze reguł SPA. Rejestr kontraktu SPA↔API wracał trzy razy (S-24, S-41, S-42) i jeszcze S-43 (atrapy): kontrakt SPA↔API jest najczęściej poprawianym obszarem sieci regresji (`evidence` tematy; kierunek „stabilizuje się" = `inference`).
- impl-review M-4: 16 przeglądów, 98 ustaleń (`### F`), 7 werdyktów NEEDS ATTENTION: it-test-isolation, derived-persistence-guards, dead-posts-daily-digest, contract-bound-mocks, per-source-failure-quality, post-store-loss-visible, core-mutation-testing. Najwięcej: restore-dead-posts 10, contract-bound-mocks 10, derived-persistence-guards 9, dead-posts-daily-digest 8, per-source-failure-quality 8. Ustalenia klasy „strażnik/atrapa nie wymusza tego, co obiecuje": contract-bound-mocks F1 (atrapa 400 tam, gdzie API 401), F2, F5, F8; derived-persistence-guards F2, F3, F6, F9; core-mutation-testing 7. To jest wzorzec M-4: narzędzia regresji same wymagają przeglądu (`inference`; wspólna przyczyna: kilka wycinków M-4 jest budową tej warstwy, więc regression-harness jest zarazem „gorący" i „zaplanowany" — liczone raz, reguła 4).
- Ustalenia o bezpieczeństwie danych i ścieżkach błędu (`evidence`, tytuły): post-store-loss-visible F1 (przyczyna odrzucenia czytana z komunikatu, który może nieść tekst posta), F2 (Hibernate loguje wiersze z tekstem posta; wolumen sprawił, że Railway zgubił własne linie S-38), F4; dead-posts-daily-digest F1 (ścieżki błędu po 202 nie wysyłają podsumowania), F2 (niechroniony zapis ciała może zmienić kod wyjścia crona), F3 (brak indeksu, plan twierdził, że jest), F8 (`variable.delete` — incydent S-32); per-source-failure-quality F1 (awaria okna jakości gubi też NIGHT_SUMMARY), F4 (PR env zaprzeczył planowi: ASSESSMENT_QUALITY się pojawił, Wykop 5,9 %). Skupione w pipeline-runs / operator-alerts / quality-monitoring.
- Incydent wdrożeniowy z lekcji (`evidence`): S-34 ustawiło `NTFY_URL`/`NTFY_TOKEN` na produkcji przed merge'em #162; w tej samej minucie zmergowana zmiana zatrzymała się na `variable.delete`. Wniosek dopisany jako lekcja. Dotyczy platform / operator-alerts; PR-y #160/#164 dotknęły `lessons.md`.
- Drugi incydent proceduralny w lekcjach M-4: w PR env nie było konta admina, a jego założenie drukuje link aktywacyjny (sekret); lekcja nakazuje jednorazowe konto zakładane przez agenta (S-32).
- Rozmiar M-4 vs wcześniej (`evidence`): 19 PR w 10 dni (2026-11-05..11-15) wobec 93 w 40 dniach; podtematów squasha 133 (śr. 7,0/PR) wobec 505 (5,4/PR) — PR M-4 są średnio większe i przeglądane w 16 z 19 przypadków przez agenta (impl-review).

## Planning attention
`evidence` (liczba folderów w `context/archive/2026-11-0[5-9]…2026-11-13`: 16 zmian M-4 z recenzjami planu i implementacji; 62 foldery w `context/archive` razem, 65 plików `reviews/impl-review*`). Przypisanie po nazwie (`inference`): regression-harness 8 zmian, quality-monitoring 2, feed 2, pipeline-runs 2, admin-console 1, operator-alerts 1. Pozostałe capability 0 planów w M-4. Plan-review: 74 ustalenia w 15 plikach. W M-4 planowanie kieruje się niemal w całości w sieć regresji i nową capability jakości; rdzeń (assessment, ingest, llm-providers) bez planu — stabilny albo zamrożony (`inference`). `docs-planning`: 205 zmian plików w M-4 (19 commitów) — dyskusja, nie ranking (reguła 8). Roadmapa: sekcja `## Parked` ma 88 pozycji-list/podsekcji (`grep`; nie rozdzielam „nowe od M-3", `unknown`).

## Debt markers
TODO/FIXME/HACK/XXX: 0 w ~110 tys. wierszy kodu i skryptów (`evidence`, `git grep` na HEAD, bez fikstur). Dług jest jawnie w roadmapie (`## Parked`) i lekcjach, nie w kodzie (`inference`). Lekcje: 174 wiersze, +14 w M-4 (2 nowe wpisy: konto admina w PR env; nowa zmienna z `railway.ts` tuż przed merge'em).

## Mechanical signals
- Werdykty NEEDS ATTENTION i liczby ustaleń: rosną z długością planu i liczbą faz; wycinki budujące strażników mają więcej ustaleń z natury (reguła 4, wspólna przyczyna: budowa warstwy). Nie rankuję regression-harness jako ryzyka produktu (runtime nie ucierpi).
- Zliczenie słów „critical/high/low" w plikach przeglądów: szum szablonu (nagłówki), nie używane.
- `deploy-health` ×7 w PR env 80/81 i lag-test: skutek iteracji wdrożenia w czasie `deploy-verification-hardening` (reguła 5), nie awaria produktu; `production` #98 jeden.
- Zamknięte bez merge'a #92/#97: ten sam temat (hardening monitora po deployu), jeszcze w toku — przed M-4.
- Commity przekrojowe (13 z 19): kontrakt, rejestr, `lessons.md`, `roadmap.md` — workflow lockstep strzeżony testami (reguła 3), nie ukryty kontrakt.
- Zero review ludzkich: baseline jednej osoby (reguła 6); rolę przeglądu pełnią artefakty agenta, więc „brak review" nie jest sygnałem między capability.
- `quality-monitoring` 0 przed: capability nowa w M-4 (reguła 4).
- Plany/impl-review dla `platform`, `db-backup`, `llm-providers`: 0 w M-4 nie znaczy „zdrowe" — po prostu nie ruszane.

## Unknowns
- `unknown`: produkcyjny tracker błędów / logi (Railway) nie dostępny; incydent z logami S-38 znany tylko z przeglądu.
- `unknown`: wynik `deploy-verification-hardening` (zmiana nieśledzona, PR #92/#97 zamknięte) — stan po M-4 niejasny.
- `unknown`: czy drugie podejścia poza tematami PR istnieją na gałęziach niezmergowanych (nie listowałem gałęzi).
- `unknown (external)`: monitor `szczur-radar-monitor` ma własne issue/alerty w innym repo.
- `unknown`: ile z 98 ustaleń impl-review naprawiono vs odroczono do Parked (nie rozstrzygałem po treści).

## Commands (skrót)
`python3 .work/disc.py` (commity/PR/pliki per capability z `commit-files.tsv`); `git log --format=%B` + parser podcommitów; `gh pr list/issue list --state all --limit 200 --json …`; `gh pr view 162/164 --json additions,deletions,changedFiles`; `git grep -nE '\b(TODO|FIXME|HACK|XXX)\b'`; `grep -cE '^### F[0-9]+'` po `reviews/` M-4; `git diff c8a6488..HEAD -- context/foundation/lessons.md`.
