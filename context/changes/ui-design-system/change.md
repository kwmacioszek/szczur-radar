---
change_id: ui-design-system
title: Ui design system
status: implementing
created: 2026-11-19
updated: 2026-11-20
archived_at: null
---

## Notes

Stan po fazie 3 (2026-11-20): właściciel zaakceptował komplet bez poprawek („Jest okej”), zapis w `ui-design.md`, sekcja „Decyzja właściciela”. Dla sesji, która podejmie fazę 4:

- **Komplet jest w `context/foundation/ui-design/`**: `screens.json` (19 ekranów), `prototypes/screens/*.html`, 80 zrzutów w `screens/` (19 ekranów i arkusz komponentów, jasny i ciemny, 1280 i 375 px; cała strona, a `post` i `target` jako kadr okna, bo panel przewija się osobno). Strony ekranów są statycznym HTML; powstały jednorazowymi skryptami poza repo, więc zmiany robi się wprost w plikach HTML i w `prototypes/prototype.css`.
- **Po każdej zmianie ekranu**: `node tools/design-check/inline-sprite.mjs context/foundation/ui-design/prototypes/screens` (gdy zmieniły się ikony), kontrola `pages`, zrzuty (`shots … --out context/foundation/ui-design/screens --full-page`, potem `--only post:1280,target:1280,post:375,target:375` bez `--full-page`), zmniejszenie PNG skryptem `gallery/quantize.mjs` (wymaga `sharp`, instrukcja w nagłówku) i ponowna publikacja galerii.
- **Galeria**: prywatny Artifact „Warianty wyglądu Szczur Radar” (adres w rozmowie, nie w repo). Publikować pod tym samym adresem stronę zbudowaną przez `gallery/inline.py` (zrzuty wbudowane; 11,7 MB z limitu 16 MB). Pokazuje komplet w grupach, z przełącznikiem motywu i szerokości; wcześniejsze warianty są zwinięte na dole.
- **Przegląd heurystyczny** (`reviews/heuristic-review.md`): osobny agent, dwa przejścia; 9 ustaleń blokujących zamkniętych w przejściu 2, `**Otwarte blokujące:** 0`. Uwagi H-11 i H-25–H-28 z przejścia 2 zostały poprawione po nim (flaga niepewności w wierszu, okno jakości 14.10–20.10, format miary jakości, nazwa „Szukaj w postach” w opisie pól, liczba ocenionych w przebiegu z 20.10); w pliku przeglądu mają dalej stan „otwarte”, bo stan zmienia tylko recenzent, a trzeciego przejścia nie było.
- **Decyzje projektu z tej fazy**: nad listą pole „Szukaj w postach” (treść, FR-036), a filtr po celu „Spółka lub temat” w panelu „Filtry” (FR-011a); włączone filtry jako zdejmowane znaczniki nad listą; lista bez ukrytego domyślnego okresu; data danych z dniem i godziną per źródło, widoczna także na telefonie; przełącznik „Inwestycyjne / Wszystkie” także w katalogu i na karcie celu; lista powiadomień w DOM zaraz po dzwonku; progi jakości z FR-033 (Wykop 5% i 2%, Reddit 25% i 30%); miara „kanał jako cel” nie jest rysowana przed S-60; części panelu admina to odnośniki, nie karty.
- **Właściciel ocenia wyłącznie na obrazkach** i nie zna frontendu: decyzje projektowe podejmuje projektant, pytania do właściciela są o wrażenie. Dwa razy odrzucił wygląd „jak panel administratora”.
- **Budżet PNG (15 MB)**: `directions/` 8,0 MB i `ui-design/screens/` 4,3 MB po zmniejszeniu palety.
- **Ikony**: 19 z Lucide i 6 własnych; `calendar` usunięta, bo żaden ekran jej nie używał.
- **Do fazy 4** (różnice wobec roadmapy i PRD): sformułowanie S-51 („obok listy” → panel na życzenie), doprecyzowanie FR-039 (udział stanowisk w całym dniu), paleta z S-46 zastąpiona, nowe role koloru i tokeny dla F-08 (w tym `--z-bar`), zmiana etykiety „Surowe dane”, nowe etykiety („Szukaj w postach”, „Filtry”, znaczniki filtrów, „Poprzednie”/„Następne”), format daty danych w pasku, reguły WCAG 1.3.5 i 3.3.8 dopisane do listy, zakres dni z półpauzą bez spacji.
- `tools/design-check`: `inventory` wymaga katalogów `a`, `b`, `c` i obejmuje każdy dalszy katalog z `tokens.css`; przeglądarka to pełny Chromium z polskimi ustawieniami; `pages` na 19 ekranach trwa około 2–3 min.
