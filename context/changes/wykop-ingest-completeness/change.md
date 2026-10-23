---
change_id: wykop-ingest-completeness
title: S-16 — Wykop nie gubi postów po cichu
status: implemented
created: 2026-10-20
updated: 2026-10-21
archived_at: null
---

## Notes

dotyczy S-16

### Sonda stronicowania (`WykopPagingProbe`, kontrole 1.3 i 1.4)

Uruchomienie: 2026-10-21 20:13 UTC, agent, klucze z `~/.config/szczur-radar/wykop.env` w podpowłoce (0 wystąpień klucza i sekretu w wyjściu). Zakres: 30 zamkniętych dni 2026-09-21…2026-10-20 (Warsaw), #gielda, `search/entries` i `search/links`, 50 na stronę, `sort=newest`, 300 ms między żądaniami; 96 s.

- Przejścia (zasób × dzień): 60, nieudane 0, limit stron 0; stron: 155 (wpisy 1–10 stron na dzień, linki zawsze 1).
- Elementy: 5643 otrzymane, 5643 unikalne, 0 duplikatów, 0 bez id; wpisów 24–459 na dzień, linków 0–7.
- Niespójności: `total` zmieniony między stronami 0; pusta strona przed `total` 0; krótka strona przed `total` 0; unikalnych poniżej `total` 0; dzisiejsza reguła stopu (otrzymane z duplikatami) zatrzymałaby się przed unikalnym `total` 0 razy.
- `status` unikalnych elementów (elementy / bez tekstu): wpisy `visible` 5556 / 0; linki bez pola `status` 87 / 0. Elementów bez tekstu: 0.

Wniosek dla niewiadomej z roadmapy („czy wyszukiwarka zwraca usunięte elementy i w jakim kształcie”): w 30 dniach wyszukiwarka nie zwróciła żadnego elementu o `status` innym niż `visible` (linki nie mają tego pola) ani żadnego elementu bez tekstu. Usunięte elementy albo nie trafiają do wyników wyszukiwania, albo w tym okresie ich nie było; sonda nie odróżnia tych przypadków, ale w obu żaden usunięty element nie zostałby zapisany ani oceniony. Stronicowanie było spójne we wszystkich 60 przejściach, więc ponowne czytanie z fazy 3 nie powinno się włączać w zwykłe noce (koszt: 0 dodatkowych żądań w tym okresie).

Bramka decyzyjna (1.4): (a) elementy o `status` innym niż `visible`/brak z tekstem: 0; (b) dni ze zmianą `total` między stronami: 0. Bramka się nie zapala, więc decyzja nie jest potrzebna, a faza 3 zostaje bez zmian.

### Sekrety w nowych komunikatach (kontrola 2.5, część 1)

2026-10-21, agent: każde nowe `log.*` i każdy nowy komunikat wyjątku z `git diff origin/main -- szczur-radar-api/src/main` przypisane do testu. Wszystko w `WykopApiClient` idzie przez `clean` (klucz, sekret, bieżący token i token żądania → `<hidden>`, potem limit długości).

| Miejsce | Co trafia do treści | Test |
| --- | --- | --- |
| `WykopApiClient` `exchange`: `INTERRUPTED` w trakcie żądania | ścieżka żądania (bez query i nagłówków) | `anInterruptDuringARequestStopsTheCallWithoutTheSecrets` (nowy) |
| `exchange`: błąd po 3 próbach (`HTTP_STATUS`) | treść odpowiedzi (może cytować sekrety) | `retriesQuotingTheSecretsLeaveThemOutOfTheErrorAndTheWarnings` (cały stack trace) |
| `exchange`: błąd po 3 próbach (`TRANSPORT`) | metoda, ścieżka, `IOException` JDK | `aConnectionErrorIsATransportFailureAfterThreeAttempts` (treść); sekretów brak z konstrukcji, `clean` jak wyżej |
| `exchange`: WARN „Retrying Wykop …” | treść odpowiedzi | `retriesQuotingTheSecretsLeaveThemOutOfTheErrorAndTheWarnings` (WARN) |
| `exchange`: `INTERRUPTED` w czekaniu | tylko `GET <ścieżka>` | `anInterruptedWaitStopsTheCallAndKeepsTheInterruptFlag`; sekretów brak z konstrukcji |
| `requireTime`: `DEADLINE_EXCEEDED` | treść ostatniej odpowiedzi | `aDeadlineAfterAnAnswerQuotingTheSecretsLeavesThemOutOfTheError` (nowy) |
| `WykopRepository` `search`: WARN „Reading Wykop … again” | ścieżka zasobu, liczby, powód | pełna treść przypięta: 4 testy `WykopRepositoryTest` |
| `search`: `INCOMPLETE_PAGING` | jw. | pełna treść (`withMessage`): 2 testy `WykopRepositoryTest` |

Break-check nowych testów: zdjęcie `clean` z `DEADLINE_EXCEEDED` i klucz doklejony do komunikatu przerwania — za każdym razem czerwony był dokładnie nowy test; kod przywrócony. `./mvnw -B test`: 708 testów zielonych. Część 2 (licznik `WYKOP_KEY`/`WYKOP_SECRET` w logach API PR env) czeka na przebieg z 4.3.

### Przebieg w PR env (kontrola 4.3) i logi (2.5, część 2)

2026-10-23, agent, `szczur-radar-pr-94` z API na `7154395` (CI run 39928010630 zielony 8/8). Konto admina `agent-s16@example.com` założone przez `railway ssh … create-user --role admin`; link aktywacyjny i hasło tylko w plikach 600 poza repo, nigdzie nie wypisane.

- Przebieg `e95f631b-89d9-4f3f-b042-52d6aedbfd12`, zakres 2026-10-21…2026-10-22 (Warsaw), start 09:03:47 UTC (`/api/deploy-safety` → `safe:true`), koniec 09:07:43 UTC.
- Wynik: `partial` — `fetched` 579, `skipped` 0, `newPosts` 579, `assessedOk` 559, `assessedFailed` 20, `dead` 0, `processingErrors` 0, `needsAttention` false; wszystkie kroki `succeeded`, krok `fetch` 8 s. `partial` pochodzi z oceny (20 postów `failed` do ponowienia w następnym przebiegu, poza zakresem S-16), nie z pobierania.
- Logi API PR env za 09:03–09:08:30 UTC (79 linii): „Retrying Wykop” 0, „Reading Wykop … again” 0, „stayed inconsistent” 0, WARN 0, ERROR 0; wystąpienia wartości `WYKOP_KEY`/`WYKOP_SECRET` (`grep -cF`, bez wypisywania): 0 — zarówno kluczy PR env (odczytanych z `railway variables` do pliku 600 i usuniętych), jak i lokalnych z `wykop.env`. Klucze PR env są inne niż lokalne (porównanie `cmp`, bez wypisywania).
- Porównanie z `total` wyszukiwarki: niewykonane. Sonda z `wykop.env` na te same dni (09:04:20 i 09:04:52 UTC) dostała HTTP 401 na wszystkie 4 wyszukiwania; 2026-10-21 te same (lokalne) klucze działały, a PR env pobiera innymi kluczami, które działają. Zapas z planu (`fetched` nocnego przebiegu produkcji za ten sam zakres D-2…D-1) wymaga odczytu produkcji — czeka na decyzję człowieka.

### Decyzje człowieka (2026-10-23)

- 3.4: treść komunikatów WARN i `INCOMPLETE_PAGING` zaakceptowana (zasób, ile z ilu i powód wystarczają operatorowi).
- 4.3: status `partial` przebiegu `e95f631b` wystarcza — pobranie pełne (579, `skipped` 0, bez ostrzeżeń), 20 ocen `failed` jest poza zakresem S-16. Do zamknięcia 4.3 zostaje porównanie `fetched` z `total` sondy; lokalne klucze z `wykop.env` odrzuca już `auth` (HTTP 401), więc sonda czeka na nowe klucze.
