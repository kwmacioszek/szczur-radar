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
