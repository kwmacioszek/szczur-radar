# Jakość ocen per źródło — tezy w innym języku niż post (S-36) — Plan Brief

> Full plan: `context/changes/statement-language-quality/plan.md`
> Research: `context/changes/statement-language-quality/research.md`

## What & Why

Kontrakt wymaga, żeby teza (`statement`) była w języku posta, ale pilnuje tego tylko prompt i nikt tego nie mierzy na produkcji. S-36 daje operatorowi drugą miarę jakości per źródło — udział tez w innym języku niż post — w tym samym oknie i kanale co S-35, i zamyka FR-033. Przy okazji język posta zostaje zapisany w bazie (`posts.language`).

## Starting Point

S-35 zbudował kanał: okno 7 dni w `posts/AssessmentQuality`, linię `quality 7d` w podsumowaniu nocy, alarm `ASSESSMENT_QUALITY` przy przejściu przez próg i sekcję „Jakość ocen (7 dni)” w `/admin`. Tezy leżą w `assessments.output`, ale języka nie ma nigdzie w produkcie; jedyna reguła języka to kod pomiarowy S-28 w testach.

## Desired End State

Co noc podsumowanie ma przy każdym źródle linię `language 7d` z udziałem tez w innym języku, liczbą tez i progiem. Gdy udział przejdzie przez próg, operator dostaje jedną wiadomość `ASSESSMENT_QUALITY`, która wymienia każdą miarę, która tej nocy przeszła. W `/admin` każde źródło ma drugi wiersz z tą miarą i jej stanem, a każdy post ma zapisany język.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Reguła języka tez | Bez wyjątków, mierzona, niesprawdzana walidatorem | Decyzja Q-16 w kontrakcie §8 i FR-033. | Research |
| Okno i kanał | 7 dni warszawskich, linia w podsumowaniu, alarm przy przejściu | PRD: „tym samym kanałem i w tym samym oknie” co S-35. | Research |
| Język posta | Stała przy źródle (Wykop `pl`, Reddit `en`) | Wzorzec progu z S-35; różnica wobec wykrywania z tekstu to 0,04 pp miary. | Plan |
| `posts.language` | Kolumna z backfillem, wartość = język źródła; miara czyta ją z posta | Stanowisko właściciela „ma być zapisany”; późniejsze wykrywanie zmieni tylko zapis kolumny. | Plan |
| Język tezy | Naruszeniem jest tylko dowód drugiego języka; nierozstrzygnięta się nie liczy | Reguła S-28 dałaby na Wykopie 16,6 % fałszywych naruszeń. | Plan |
| Mianownik i minimum | Tezy udanych prób z modelem; próg sprawdzany od 500 tez | FR-033 mówi „udział tez”; przy 2 % i małym oknie kilka tez dawałoby alarm. | Plan |
| Progi | Wykop 2 %, Reddit 30 % | Okna z produkcji: Wykop 0,09–0,86 %, Reddit 14,01–20,76 %; zero alarmów na historii. | Plan |
| Alarm | Jedno `ASSESSMENT_QUALITY` z obiema miarami | Kolejka odrzuca drugie takie samo zdarzenie dla przebiegu w 10 min. | Plan |
| Stan „za mało” | `not_enough_attempts` → `not_enough_data` („za mało danych”) | Jedna wartość dla obu miar; dla języka brakuje tez, nie prób. | Plan |
| Gdzie liczy się reguła | W Javie (`text/LanguageEvidence`), baza tylko wyciąga tezy | Reguła istnieje w jednym miejscu, bez kopii w SQL. | Plan |
| Awaria odczytu tez | Osobna domena: własne rekordy, własna transakcja, `language: null` w API | Miara prób z S-35 nie może się pogorszyć przez S-36 (przegląd planu, F1). | Plan |

## Scope

**In scope:**
- `text/Language` i reguła `text/LanguageEvidence`
- język i próg języka przy `Source`
- `posts.language` (V16 z backfillem) ze strażnikami
- miara i przejście przez próg w `AssessmentQuality`, z osobną obsługą awarii odczytu tez
- linia `language 7d` i treść alarmu
- pola języka w `GET /api/admin/assessment-quality` i wiersz w `/admin`
- dokumentacja: PRD, kontrakt §8, runbook S-36, roadmapa

**Out of scope:**
- wykrywanie języka z tekstu posta i pole `language` z modelu
- naprawa przyczyny w prompcie (nowa `PROMPT_VERSION`)
- język posta w API postów i w UI
- biblioteka do wykrywania języka, języki poza `pl` i `en`
- osobne zdarzenie alarmu, alarm co noc, zapisany stan
- kroki po merge'u (runbook i issue, nie Progress)

## Architecture / Approach

Język jest wartością przy źródle i przy poście. Jedno zapytanie zwraca tezy udanych prób z okna razem z językiem ich posta, a `LanguageEvidence` mówi dla każdej, czy jest w niej dowód polskiego albo angielskiego. `AssessmentQuality` liczy z tego udział i stan jako osobne okno miary języka i porównuje te same dwa okna co miara prób. `NightSummary` i trasa admina czytają oba okna osobno, więc awaria odczytu tez kosztuje tylko linię i wiersz języka. Nie ma nowej trasy ani nowego zdarzenia.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Język przy źródle i przy poście | Reguła języka, język i próg przy `Source`, kolumna `posts.language` z backfillem, karta S-36 na rysunku roadmapy | Migracja `NOT NULL` bez wartości domyślnej: revert wymaga nowej migracji |
| 2. Miara języka tez | Zapytanie o tezy, własne okno i przejście miary języka, nazwa stanu `not_enough_data` | Zmiana nazwy stanu dotyka API i SPA w jednym commicie |
| 3. Podsumowanie nocy i alarm | Linia `language 7d` z własnym odczytem, jedno zdarzenie z obiema miarami, dokumentacja | Odczyt tez musi iść w osobnej transakcji; limit 4096 bajtów ntfy |
| 4. Sekcja w `/admin` | Pola języka w API (`language` albo `null`), trzeci plik kontraktu, wiersz w sekcji, atrapa, zrzuty | Pliki kontraktu, próbki i atrapa muszą zmienić się razem |
| 5. Weryfikacja w PR env | CI, trasa, noc z testowym tematem ntfy, akceptacja właściciela | Alarmu w PR env nie da się wywołać; dowodzą go testy |

**Prerequisites:** S-35 na `main` (jest); dostęp `railway` do PR env; issue S-36 i gałąź `feature/statement-language-quality` z `origin/main`.
**Estimated effort:** ok. 3–4 sesje w 5 fazach.

## Open Risks & Assumptions

- Język ze źródła liczy na Wykopie 28 poprawnych angielskich tez jako naruszenia i pomija 22 polskie na angielskich postach (43 takie posty w 35 dniach). Miara to znosi (0,32 % wobec 0,28 %), ale `posts.language` tych postów mówi `pl`.
- Reguła przeocza krótkie naruszenia bez słów funkcyjnych (2 z 60 sprawdzonych nierozstrzygniętych tez), więc miara jest lekko zaniżona.
- Próg Reddita opiera się na 12 dniach danych; może wymagać korekty PR-em.
- Zapytanie o tezy czyta `output` ok. 3,5 tys. prób na okno; czas odpowiedzi trasy na produkcji sprawdza runbook po merge'u.
- Etykieta „za mało danych” zastępuje zaakceptowane 2026-11-11 „za mało prób” i czeka na akceptację właściciela.

## Success Criteria (Summary)

- Operator widzi co noc udział tez w innym języku dla każdego źródła, bez czytania logów.
- Przejście przez próg daje jedną wiadomość ntfy; trwanie ponad progiem widać w linii i w `/admin`, bez kolejnych alarmów.
- Każdy post ma zapisany język, a miara liczy się z danych produkcyjnych bez zmiany promptu.
