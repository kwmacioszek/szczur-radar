---
change_id: ui-design-system
title: Ui design system
status: implementing
created: 2026-11-19
updated: 2026-11-20
archived_at: null
---

## Notes

Stan po fazie 2 (2026-11-20), dla sesji, która podejmie fazę 3:

- **Źródłem wyglądu jest już `context/foundation/ui-design/`**: `tokens.css` i `pairs.json` (63 pary), `fonts/`, `icons/`, `prototypes/prototype.css` (wyłącznie tokeny, stany wskazania i fokusu także jako klasy `.is-hover` i `.is-focus`) oraz `prototypes/components.html` (28 kotwic `c-<nazwa>`). Ekrany fazy 3 składa się z klas `prototype.css`; katalogi `directions/d/` i `directions/e/` zostają tylko jako zapis decyzji.
- **Właściciel ocenia wyłącznie na obrazkach** i nie zna frontendu: decyzje projektowe podejmuje projektant, a pytania do właściciela są o wrażenie, nie o terminy. Dwa razy odrzucił wygląd „jak panel administratora”: unikać bocznych kolumn, formularzy filtrów na wierzchu, zdań objaśniających i wszystkiego naraz.
- **Galeria**: prywatny Artifact „Warianty wyglądu Szczur Radar” (adres w rozmowie, nie w repo; do znalezienia na liście artefaktów właściciela). Publikować pod tym samym adresem stronę zbudowaną przez `gallery/inline.py` (zrzuty wbudowane), bo u właściciela osobne pliki obrazków się nie ładowały. Arkusz komponentów nie był jeszcze pokazany w galerii; wchodzi do galerii pełnego zestawu w fazie 3.
- **Strony prototypu mają kopię ikon**: po `<body>` znacznik `<!--sprite-->`, a potem `node tools/design-check/inline-sprite.mjs context/foundation/ui-design/prototypes/screens`. Znaki spoza podzbiorów krojów (strzałki, trójkąty) muszą być ikonami, inaczej kontrola kroju jest czerwona.
- **Słowo „post”**, nie „wpis”. Etykiety istniejące w testach E2E zostają (lista w `ui-design.md`, „Treść i etykiety”); jedyna zmiana to „Surowe dane” zamiast „Dane dla nerdów: surowy JSON źródła”. Czas w powiadomieniach jak w aplikacji: „2 h temu”, po dobie data z godziną.
- **Części panelu admina to odnośniki** (`nav`, `aria-current="page"`), nie karty: każda część ma własny adres. Trzecia część nazywa się „Nieudane posty”.
- **Odstępstwa od planu w fazie 2**: trzy kroje zamiast dwóch (JetBrains Mono dla surowych danych; razem 199 kB z budżetu 300 kB), wszystkie zmienne; zrzuty arkusza przy 375 px to kadr okna, przy 1280 px cała strona (budżet PNG); tabele tokenów w dokumencie wygenerował skrypt w tej sesji, nie podagent; `tools/design-check` dostało `inline-sprite.mjs` i poprawkę `doc` (ścieżka arkusza porównywana jako bezwzględna, błąd wykryty przy pierwszym uruchomieniu na prawdziwych danych).
- **Budżet PNG (15 MB)**: `directions/` 8,0 MB i `ui-design/screens/` 3,0 MB. Na około 64 zrzuty ekranów fazy 3 zostają 4 MB: zrzuty 1× i przy 375 px kadr okna, jeśli cała strona się nie mieści.
- **Ikony do rozliczenia w fazie 3**: `chevron-down`, `rotate-ccw`, `arrow-down-up` i `calendar` nie są jeszcze użyte w żadnym komponencie. Ekrany fazy 3 ich używają albo pliki, wiersze `README.md` i wiersze tabeli „Ikony” znikają (plan: tylko ikony użyte).
- **Do fazy 4** (różnice wobec roadmapy i PRD): sformułowanie S-51 („obok listy” → panel na życzenie), doprecyzowanie FR-039 (udział stanowisk w całym dniu), paleta z S-46 zastąpiona, nowe role koloru i tokeny dla F-08, zmiana etykiety „Surowe dane”.
- `tools/design-check`: `inventory` wymaga katalogów `a`, `b`, `c` i obejmuje każdy dalszy katalog z `tokens.css`; przeglądarka to pełny Chromium z polskimi ustawieniami.
