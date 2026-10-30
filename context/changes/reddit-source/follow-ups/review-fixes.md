# Implementation review fixes

- [x] F1 — Poprawiono polecenie w opisowych kryteriach fazy 4 na `rg -n -F` i dopisano sprostowanie wyniku kontroli 4.5. Ponowne wykonanie: exit 0, oba pliki znalezione. Historyczna sekcja Progress bez zmian.
- [x] F2 — Sprostowano deklaracje zachowania selftext i dodano w README odtwarzalny filtr anonimizacji wzmianki posta 1xh5lkk. Oba filtry wykonane na obu fixture’ach zachowują ich zawartość; przypadek z syntetycznym nickiem potwierdza zamianę etykiety i URL-u bez zmian innych pól. Pliki JSON bez zmian.
- [x] F3 — RedditMapper zamienia DateTimeException na UnmappableItemException. Test fetchera dla Instant.MAX.getEpochSecond() i Long.MAX_VALUE zachowuje pozostałe 13 postów przy fetched=14 i skipped=1. Przed poprawką oba przypadki odtworzyły błąd; po poprawce Reddit*Test: 70 testów bez błędów i pominięć.
