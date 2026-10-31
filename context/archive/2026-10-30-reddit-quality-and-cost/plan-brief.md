# S-28: obraz konfiguracji produkcyjnej na Reddicie — Plan Brief

> Full plan: `context/changes/reddit-quality-and-cost/plan.md`
> Frame brief: `context/changes/reddit-quality-and-cost/frame.md`

## What & Why

Rzeczywisty problem: nikt nie widział, co konfiguracja produkcyjna (`gpt-6-luna`, prompt v2.5) robi z prawdziwym postem z Reddita, a decyzja o włączeniu potrzebuje zgrubnego obrazu tego zachowania, nie rozstrzygnięcia progów 70 % / 10 %. Mierzymy to przed włączeniem, bo oceny zostają w feedzie na stałe, tezy po polsku byłyby problemem, a najgorszy wariant kosztu przekracza limit klucza, który obsługuje też Wykop.

## Starting Point

Harness `measurement/` jest ogólny poza próbnikiem i ma ścieżkę do produkcyjnego `Assessor`, ale złota próbka i bramka CI są wykopowe. Istniejące dane o Reddicie pochodzą z innych modeli, a szczegóły błędów z PR-132 przepadły razem ze środowiskiem.

## Desired End State

W folderze zmiany jest raport z danymi źródłowymi, a właściciel zapisał decyzję o włączeniu i budżet. Raport pokazuje odsetek ocen i `dead`, odrzucenia według klasy i długości, liczbę celów, język tez, wpływ megawątków, koszt łączny z Wykopem i pominięte cele. Dokumenty nie wymagają już spełnienia progów, a bramka CI się nie zmieniła.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Rola wyniku | Materiał do decyzji, nie bramka | Właściciel uznał nawet co czwartą błędną ocenę za znośną | Frame |
| Kryterium | Obraz zachowania, progi tylko jako odniesienie | Godzina oznaczania nie rozstrzygnie 70 % | Frame |
| Język posta | `posts.language` poza S-28, do `## Parked` | Osobna potrzeba właściciela | Frame |
| Prompt | Bez zmian; naprawa to osobna zmiana po decyzji | Nowa wersja unieważnia 480 nagrań przy zapasie 2 celów | Frame |
| Pominięty cel | Spółka lub aktywo, o których autor coś twierdzi | Odpowiada obawie i mieści się w godzinie | Plan |
| Arkusz | Podpowiedź modelu z podświetlonymi celami | Więcej postów w godzinie; uwaga: wynik może wyjść lepszy | Plan |
| Subreddity | `stocks`, `ValueInvesting`, `investing` | Dokładnie to, co włączy runbook | Plan |
| Kto uruchamia | Agent: `openrouter.env` i odczyt z produkcji | Koszt łączny z prawdziwych liczb | Plan |
| Metoda | Produkcyjny `Assessor` w `measurement/`, nie skrypt | Te same walidatory, klient i `flex` | Plan |
| Miejsce danych | `sample/` w folderze zmiany, poza `fixtures/` | Dowód zostaje po archiwizacji, bramka CI nietknięta | Plan |
| Anonimizacja | Przed oceną, jak w złotej próbce | Repo jest publiczne | Plan |
| Liczba prób | Do 3, jak na produkcji | Mierzy `dead`, nie tylko pierwszą próbę | Plan |

## Scope

**In scope:**

- `RedditMeasures`, `RedditSampler`, `RedditRecorder`, `RedditReport`, `RedditSheet` z testami czystej logiki
- 7 dni, ok. 550 postów na konfiguracji produkcyjnej, odczyt tokenów Wykopa
- Arkusz `labeling-reddit.html` i godzina oceny
- Raport, decyzja, korekty `deploy-plan.md` i roadmapy, komentarze w #122 i #121

**Out of scope:**

- Cokolwiek w `src/main`, prompt, kontrakt, limity, filtry, `models.json`, złota próbka
- `posts.language`, filtr po źródle, ponowna ocena postów
- Inne subreddity i modele
- Włączenie Reddita na produkcji i weryfikacja w PR env

## Architecture / Approach

`RedditSampler` czyta posty przez `RedditRepository` i `RedditMapper`, anonimizuje je i zapisuje `posts.json` z `volume.json`. `RedditRecorder` puszcza je przez `Assessor` i dopisuje każdą próbę do `recordings.jsonl`, wznawiając się po przerwaniu. `RedditReport` ocenia próby ponownie, bez modelu, i pisze raport. `RedditSheet` wybiera 40 postów dla arkusza, którego `labels.json` zasila ostatnią część raportu.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Narzędzia pomiaru | Klasy pomiarowe, testy, README | Bramka CI ruszona przypadkiem |
| 2. Przebieg na żywo | Dane i automatyczna część raportu | Wolny `flex`, zły tryb względem produkcji |
| 3. Godzina oceny | Arkusz i część o pominiętych celach | Długie posty zjedzą godzinę |
| 4. Decyzja i dokumenty | Decyzja, spójne dokumenty, issue | Brak budżetu opóźni decyzję |

**Prerequisites:** S-27 na `main`; `~/.config/szczur-radar/openrouter.env`; dostęp `railway` do odczytu produkcji; godzina właściciela.

**Estimated effort:** ok. 3 sesje agenta plus godzina właściciela; koszt modelu ok. $1, bezpiecznik $4.

## Open Risks & Assumptions

- Wynik jest zgrubny: mała próbka oceny, podpowiedź modelu i jedna osoba oceniająca.
- Anonimizacja zmienia tekst względem produkcji (`<url>`, `@uzytkownikN`); wpływ na ocenę uznano za pomijalny.
- Treść zanonimizowanych postów Reddita trafi do publicznego repo, tak jak 243 posty pilota.
- Klucz z `openrouter.env` może liczyć się do limitu $10 produkcji.

## Success Criteria (Summary)

- Właściciel widzi w raporcie, co Luna robi z Redditem, i podejmuje decyzję na liczbach, nie na szacunkach z innych modeli.
- Pominięte cele, język tez, `dead` i koszt łączny mają konkretne wartości.
- CI i bramka nagrań są zielone bez żadnej zmiany w `src/main` ani `fixtures/`.
