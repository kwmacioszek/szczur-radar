# F-07: Projekt UI/UX całej aplikacji (ui-design-system) — Plan Brief

> Full plan: `context/changes/ui-design-system/plan.md`
> Research: `context/changes/ui-design-system/research.md`

## What & Why

Projektujemy UI/UX całej aplikacji od nowa, według standardów (WCAG 2.2 AA, heurystyki Nielsena, wzorce dojrzałych systemów projektowych), i zapisujemy projekt w `context/foundation/ui-design.md` z tokenami, komponentami i prototypami każdego ekranu M-5. Aplikacja ma wyglądać jak dopracowany produkt, a nie surowy panel (decyzja właściciela, roadmapa v6.1). Właściciel nie zna frontendu, więc standardy dobiera i uzasadnia projektant, a właściciel ocenia wynik na obrazkach.

## Starting Point

SPA ma 7 tras, 11 komponentów i jeden plik tokenów w palecie A z S-46; krój jest systemowy, nie ma ikon ani cieni. Cztery makiety w `docs/visuals/ui-ux-proposals/` to cztery różne style i służą tylko jako lista funkcji. Bramy F-05, F-06 i S-46 (kontrast, zrzuty z tolerancją 0 pikseli, strażnik fontu, E2E po rolach i etykietach) wyznaczają, co da się potem wdrożyć.

## Desired End State

W repo jest zaakceptowany przez właściciela projekt: dokument z zasadami, układem ekranów, językiem wizualnym i komponentami oraz prototypy wszystkich ekranów w jasnym i ciemnym motywie, na komputer i telefon. F-08 i wycinki S-47–S-59 wdrażają go bez zgadywania: mają mapę tokenów i komponentów, listę zmian w bramach testowych i listę przepływów do testów E2E. Kod aplikacji jest bez zmian.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Kto dobiera standardy | Projektant (agent), z uzasadnieniem w dokumencie | Właściciel nie zna frontendu i ocenia wynik, nie zasady. | Plan |
| Rola makiet | Lista funkcji; wygląd od zera | Właściciel: makiety miały być tylko sugestią i kierunkiem. | Plan |
| Dojście do wyglądu | Trzy warianty na dwóch ekranach, wybór właściciela, potem całość | Wzorzec z S-46; błąd kierunku kosztuje dwa ekrany, nie całą aplikację. | Plan |
| Urządzenie | Głównie komputer; przy 375 px jedna kolumna | Tak właściciel korzysta z aplikacji. | Plan |
| Ekran startowy | Docelowo przegląd dnia; do S-59 lista wpisów z oceną obok | Właściciel chce zaczynać od przeglądu, ale powstaje on na końcu M-5. | Plan |
| Priorytety projektu | Sedno wpisu na liście, ocena obok listy, czytelny panel admina | Cztery bolączki właściciela z wywiadu. | Plan |
| Krój, paleta, ikony | Dobiera projektant; krój OFL w plikach aplikacji; jeden wariant zachowuje paletę A | Roadmapa zostawiła je F-07; właściciel wybierze na obrazkach. | Research / Plan |
| Zakres wymagań | Dokładnie PRD v4; pomysły ponad PRD tylko jako lista | Nie otwieramy wymagań przed terminem kursu. | Research / Plan |
| Elementy makiet bez wymagań | Wypadają („Analizy”, „Wątki”, „Ostatnie obserwacje”, „↑”); „Przejdź do źródła” jako link do oryginału | Nie mają FR albo łamią decyzje z 2026-11-15. | Research |
| Forma prototypów | Samodzielne strony HTML z tokenami w formacie `style.css`, poza aplikacją | F-08 przenosi tokeny bez tłumaczenia, a wzorce F-06 zostają nietknięte. | Research / Plan |
| Sposób oglądania | Prywatna strona z galerią pod linkiem; obrazki także w repo | Wybór właściciela. | Plan |
| Kontrola standardów | Skrypt (kontrast, axe, 375 px, rozmiar celów) i niezależny przegląd heurystyczny | Właściciel nie oceni standardów sam, więc sprawdza je agent z dowodem. | Research / Plan |
| Testy E2E ekranów | W wycinkach S-47–S-59; F-07 daje kontrakt i dopisuje zdanie w roadmapie | Test klika w aplikację, a ekrany powstaną dopiero w wycinkach. | Plan |
| Roadmapa | Lista różnic dla `/10x-roadmap`; bez przecinania wycinków | Tak ustala roadmapa v6.1. | Research |

## Scope

**In scope:**
- `context/foundation/ui-design.md`: zasady, zadania użytkownika, układ ekranów, język wizualny, komponenty, etykiety, kontrakt E2E, wdrożenie
- Trzy warianty wyglądu i wybór właściciela
- Tokeny, krój pisma z licencją, ikony, arkusz komponentów ze stanami
- Prototypy każdego ekranu M-5 w obu motywach przy 1280 i 375 px, z przeglądem heurystycznym
- Zdanie w roadmapie o teście E2E w każdym wycinku; punkt w wytycznych repo

**Out of scope:**
- Kod aplikacji, tokeny w `style.css`, wzorce F-06, strażnik fontu (F-08)
- Testy E2E nowych ekranów (wycinki) i testy klikające po prototypach
- Przecinanie wycinków w roadmapie i zmiany PRD
- Trzeci motyw, zewnętrzny CDN fontów, biblioteki UI

## Architecture / Approach

Trwały wynik leży w `context/foundation/` (dokument i katalog `ui-design/` z tokenami, krojami, ikonami, prototypami i zrzutami), skrypt kontroli w `tools/design-check/` (stała ścieżka także po archiwizacji), materiał roboczy w folderze zmiany (warianty, galeria, przegląd heurystyczny). Prototypy to strony HTML ze wspólnym CSS opartym wyłącznie na tokenach; motyw ustawia `data-theme`, jak w aplikacji. Skrypt ładuje Playwright i axe z modułu web i sprawdza prototypy tymi samymi regułami co brama w CI. Właściciel ogląda galerię publikowaną jako prywatna strona.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Podstawy i trzy warianty wyglądu | Zasady, układ ekranów, 3 warianty na 2 ekranach, galeria; wybór właściciela | Żaden wariant nie trafia w gust i potrzebna jest druga runda |
| 2. Język wizualny i komponenty | Tokeny, krój, ikony, arkusz komponentów ze stanami | Role kolorów poza formatem, którego wymaga test aplikacji |
| 3. Prototypy wszystkich ekranów | Każdy ekran w 4 odmianach, przegląd heurystyczny; akceptacja właściciela | Prototyp pokazuje coś sprzecznego z PRD albo dane bez API bez oznaczenia |
| 4. Przekazanie do wdrożenia | Mapa dla F-08, kontrakt E2E, różnice wobec roadmapy, wytyczne | Lista zmian w bramach niepełna i F-08 odkrywa je w CI |

**Prerequisites:** `npm ci` w `szczur-radar-web/` i `npx playwright install chromium` (kontrole stron); sesja interaktywna z narzędziem Artifact (galeria); dostęp do sieci po pliki krojów i ikon.
**Estimated effort:** około 3–4 sesje w 4 fazach, z dwoma zatrzymaniami na decyzję właściciela.

## Open Risks & Assumptions

- Wybór wariantu może wymagać poprawek albo połączenia dwóch wariantów; plan to dopuszcza przed zapisem decyzji.
- Jeśli projekt zmieni podział ekranów, `/10x-roadmap` przetnie S-47–S-59 (v6.2) przed F-08.
- Zrzuty dodają do repo do około 15 MB.
- Kontrole na prototypach nie zastępują bram aplikacji: F-08 musi przejść kontrast, zrzuty i axe na prawdziwych widokach.
- Propozycje dla niewiadomych właściciela (pola sortowania, okres katalogu) są niewiążące do czasu planów ich wycinków.

## Success Criteria (Summary)

- Właściciel wybrał wariant i zaakceptował komplet ekranów na galerii.
- Prototypy przechodzą kontrolę kontrastu, dostępności i szerokości 375 px, a przegląd heurystyczny nie ma otwartych ustaleń blokujących.
- F-08 i wycinki mają w `ui-design.md` wszystko do wdrożenia: tokeny, komponenty, zmiany w bramach i listę przepływów do testów E2E.
