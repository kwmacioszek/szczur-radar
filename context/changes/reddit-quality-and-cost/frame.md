# Frame Brief: Jakość i koszt Reddita przed produkcją (S-28)

> Etap ustalania ram przed /10x-plan. Ten dokument rejestruje, co jest *faktycznie*
> przedmiotem problemu, oddzielone od tego, co początkowo założono.

## Zgłoszona obserwacja

Reddit działa w PR env, ale nie ma go na produkcji. Nikt nie zmierzył oceny tekstów angielskich: próbka złota i 480 nagrań są wyłącznie wykopowe. Nie ma budżetu kosztu na dzień ani przebieg, poza $10/mies. na kluczu. W PR env z 79 postów 70 ma ocenę, a 9 jest `dead`; wszystkie nieudane próby to walidacja wyjścia modelu (model z CI, nie produkcyjny).

## Początkowe ujęcie (zachowane)

- **Podana przyczyna lub podejście**: roadmapa S-28 (`roadmap.md:254`): przed włączeniem Reddita trzeba wiedzieć, czy ocena po angielsku spełnia progi kierunku i sprzeczności (≥ 70 % / ≤ 10 %) przy obecnym prompcie, i ile kosztuje dzień.
- **Proponowany kierunek**: zbudować angielską próbkę oznaczoną przez człowieka, zmierzyć progi, oszacować dzienny koszt dla wybranych subredditów, przy okazji rozstrzygnąć język posta w bazie.
- **Zawężenie przed delegowaniem**: główna obawa to „oceny merytorycznie błędne”. Rola wyniku: „nie jestem pewien”. Zapisany język posta: „osobna potrzeba”, nie część pytania o jakość.

## Mapa wymiarów

Obserwacja może pochodzić z każdego z tych wymiarów:

1. **Przyrząd** ← początkowe ujęcie: czy złota próbka i progi wykryją na długich postach angielskich błędne oceny, przy koszcie możliwym dla jednej osoby.
2. **Tor Reddita wokół modelu**: czy mapper, prefiltr, limity, walidatory i polski prompt psują ocenę niezależnie od modelu.
3. **Obiekt pomiaru**: czy to, co wiemy o Reddicie, dotyczy konfiguracji produkcyjnej.
4. **Wartość decyzji**: czy wynik zmieni to, co stanie się dalej.

## Badanie hipotez

| Hipoteza | Dowody | Werdykt |
| --- | --- | --- |
| Przyrząd: progi nie rozstrzygną na długich postach | Widełki Wilsona przy 54/74 to 61,9–81,8 %, a odrzucone modele (48/74) nakładają się na nie. Dolna granica powyżej 70 % przy prawdziwych 75 % wymaga ok. 288 celów, czyli 180–290 postów (1,0–1,6 liczonego celu na post w pilocie). Niedopasowany cel liczy się jako błąd, więc wynik kierunku podąża za szerokością wzorca: 67,3 % albo 42,6 % przy tej samej trafności na dopasowanych (~69 %). 18 z 72 stanowisk Luny na Wykopie jest niewidocznych dla obu progów. Progi 75 % / 3 % były decyzją akceptacyjną, nie pomiarem (`plan-brief.md:68` archiwum F-02). Przeciw: `AnchorMatcher` działa po angielsku i na markdownie (525/538 w pilocie 5). | SILNE |
| Tor Reddita: psuje ocenę niezależnie od modelu | Limity bez zmian od V1 (`contract-v2.md:271-281`), a `ProviderSchemas` nie wysyła ich DeepSeekowi ani OpenRouterowi (`ProviderSchemas.java:27`), więc model zna je tylko z promptu. Jedno naruszenie odrzuca cały post (`OutputParser.java:79-85`). Limity w pilocie 5: DeepSeek EN 3 / 15 / 38 %, PL 4 / 31 / 62 % przy < 1000 / 1000–2999 / 3000+ znaków, ale Gemini 0 % przy każdej długości, więc efekt zależy od modelu. Megawątki nie są odfiltrowane (`RedditRepository.java:187`). Język tez nie jest sprawdzany. Przeciw: prefiltr bramkuje tylko teksty poniżej 3 znaków, ucinania nie ma, trafność 242–243/243. | SŁABE jako przyczyna niezależna od modelu; SILNE dla megawątków i braku kontroli języka |
| Obiekt pomiaru: dowody nie dotyczą produkcji | Nagrania Luny to 120 razy `gielda`, zero Reddita. Pilot 5: kontrakt V1, skrócony prompt, DeepSeek i Gemini. PR env: `deepseek-v4-flash` w trybie JSON, produkcja: Luna w trybie strict (`OpenRouterClient.java:164`). Ten sam DeepSeek i prompt v2.5: Wykop 115/120, Reddit 59/79 przy pierwszym przejściu. Szczegóły błędów `schema_violation` przepadły ze środowiskiem PR-132. Przeciw: bazowy pomiar Wykopu nie jest przestarzały wersyjnie (prompt, kontrakt i `models.json` bez zmian od `663c687`). | SILNE |
| Wartość decyzji: wynik niczego nie zmieni | Wyłączenie to jedna zmienna, ale oceny zostają: retencja bezterminowa, brak ukrywania, filtra po źródle i ponownej oceny (`AssessmentSelection.java:64`, `PostFilter.java:18`). `deploy-plan.md:2073` uznaje S-28 za bramkę, `roadmap.md:262` zostawia to otwarte. Koszt z Wykopem na flex szacunkowo $2,6–7,2/mies., najgorszy wariant na `default` $14,4 przy limicie $10, którego wyczerpanie zatrzymuje też Wykop. Rzeczywistych wydatków nie ma w repo. `posts.language` nie jest potrzebne do wyniku S-28. | SILNE dla roli wyniku, SŁABE dla kosztu jako blokady |

## Sygnały zawężające

- **Typ błędu:** „Pominięte cele” oraz „Nie jestem pewien”. Właściciel nie wskazał odwrotnego kierunku ani stanowiska, którego nie ma. S-28 nazywa tylko kierunek i sprzeczności, a recall celów (≥ 80 %) jest wśród siedmiu progów, lecz nie w S-28.
- **Skutek błędu:** przy hipotetycznie co czwartej błędnej ocenie „znośne i tak”. To pierwsza konkretna odpowiedź na Q-04: wynik jest materiałem do decyzji, nie twardą bramką.
- **Czas:** około godziny na oznaczanie. Przy potrzebnych 180–290 postach progu 70 % nie da się rozstrzygnąć.
- **Język tez:** „Tezy po polsku” są problemem. Reguła kontraktu pasuje tu do oczekiwań właściciela, ale pilot dał ok. 64 % tez po polsku, a nikt tego nie sprawdza.
- **Liczba celów rozjeżdża się na długich postach (pilot 5):** Gemini ma medianę 2 cele przy każdej długości, DeepSeek 3 / 5 / 8. Limit 12 celów prawie nie gra roli (0–3 posty z 243). To dokładnie obszar obawy o pominięte cele, a wynik Luny nie jest znany.

## Konwencja między systemami

Lekcja „Przed zamrożeniem kontraktu LLM zrób tani pilot skryptem na żywych danych” (`lessons.md:54-59`) każe najpierw puścić prawdziwe dane przez model i mierzyć więcej niż jedną miarę. F-02 szło tą drogą: pilot bez etykiet, potem wzorzec. Raport F-02 sam zapisał, że przy małej próbce różnica kilku punktów to szum (`gold-2026-10-06.md:93`), a na Wykopie brak spełnionych progów skończył się decyzją właściciela o ich obniżeniu (`:12`), nie blokadą. Wiodąca hipoteza zgadza się z tą konwencją.

## Przeformułowany opis problemu

> **Rzeczywisty problem, wokół którego należy planować, to**: nikt nie widział, co konfiguracja produkcyjna (`gpt-6-luna`, prompt v2.5) robi z prawdziwym postem z Reddita, a decyzja o włączeniu potrzebuje zgrubnego obrazu tego zachowania, nie rozstrzygnięcia progów 70 % / 10 %.

Cel początkowego ujęcia zostaje: pomiar i szacunek kosztu przed produkcją. Zmienia się przedmiot i kryterium. Pytanie „czy spełnia progi kierunku i sprzeczności” przy godzinie oznaczania i tolerancji właściciela nie ma odpowiedzi, a obawa dotyczy głównie pominiętych celów. Pierwsze niewiadome nie wymagają etykiet: jaka część postów dostaje ocenę, ile celów ma ocena, w jakim języku są tezy i ile to kosztuje.

Sprawdzono też wariant „włączyć bez pomiaru, skoro błędy są znośne”. Nie wystarcza z trzech powodów: oceny zostają na stałe, więc tezy po polsku trafiłyby do feedu na trwałe; nieznana część postów może co noc kończyć jako `dead` i wywoływać alarm; najgorszy wariant kosztu przekracza limit klucza, który obsługuje też Wykop.

## Pewność

**WYSOKA** dla przeformułowania: fakt, że Luna nie widziała Reddita, jest sprawdzony w nagraniach, granice pomiaru są policzone, a sygnały właściciela rozstrzygają kierunek. Wiedzy o samym zachowaniu Luny na Reddicie brief nie daje: tę lukę ma zamknąć zmiana.

Otwarte po ramowaniu:

- Typ błędu nie jest w pełni rozdzielony (właściciel zaznaczył też „nie jestem pewien”).
- Budżet (Q-03) nie istnieje, a aktualny tryb produkcji (`flex` czy `default`) wymaga sprawdzenia.
- Czy tezy po polsku blokują włączenie, czy są tylko problemem do naprawy później. To najmocniejszy kandydat na warunek, ale właściciel go tak nie nazwał.

## Co zmienia się dla /10x-plan

Plan ma dać obraz zachowania konfiguracji produkcyjnej na próbce Reddita: odsetek ocen i odrzuceń według klasy i długości posta, liczba celów, język tez, wpływ megawątków, tokeny i koszt przy rzeczywistym wolumenie. Do tego godzina pracy człowieka nakierowana na pominięte cele. Wynik jest materiałem do decyzji; plan musi uzgodnić z tym `deploy-plan.md:2073` i opis S-28 w roadmapie.

Ograniczenia dla planu:

- `posts.language` wychodzi z S-28 jako osobna potrzeba.
- Reddit w plikach złotej próbki wpadłby do bramki CI (`GoldLabels.java:31-33`, `RecordingsReplayTest.java:44-53`).
- Nowa `PROMPT_VERSION` unieważnia 480 nagrań przy zapasie 2 celów na Wykopie, więc naprawa języka tez to świadoma decyzja po pomiarze, a nie część pomiaru.

## Odniesienia

- Pliki źródłowe: `roadmap.md:252-265`, `deploy-plan.md:2047-2052,2073,2094`, `contract-v2.md:220,271-281,297`, `system-prompt.md:52`, `GoldMetrics.java:194`, `measurement/README.md:146-163`, `ProviderSchemas.java:27`, `AssessmentSelection.java:64`, `RedditRepository.java:187`
- Archiwum: `context/archive/2026-09-27-assessment-contract-harness/` (`frame.md:253-290`, `reports/gold-2026-10-06.md`, `pilot/runs/pilot5-reddit-*.json`), `context/archive/2026-10-29-reddit-source/reviews/impl-review-phase-5.md`
- Domena: `context/domain/domain-distillation.md:160-161,250-251`
- Powiązane badania: brak `research.md`. Dowody z czterech równoległych badań (przyrząd, tor Reddita, obiekt pomiaru, wartość decyzji) i własnego przeliczenia `pilot5-reddit-*.json`; szacunki kosztu i liczności prób policzono offline na liczbach z tych źródeł.
