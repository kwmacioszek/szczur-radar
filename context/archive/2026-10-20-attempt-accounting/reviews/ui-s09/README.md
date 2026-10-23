# Zrzuty `/admin` — sekcja „Nieudane posty” (kroki 4.4 i 4.5)

- Data: 2026-10-21
- Commit: `2a29202` (gałąź `feature/attempt-accounting`, kod `szczur-radar-web/src` bez zmian)
- API: zamockowane przez `szczur-radar-web/dev/mock-api.js` (MODE `data`); inne tryby to kopie pliku z podmienionym
  `MODE` (`empty`, `loading`, `error`) w scratchpadzie, poza repo.
- Przeglądarka: Chromium z `playwright-cli` (konfiguracja `.playwright/cli.config.json` worktree), sesja `-s=uis09`.

## Jak uruchomiono

```sh
cd szczur-radar-web && npx vite --port 5183 --strictPort &      # port 5183 zamiast 5173, żeby nie kolidować z innymi sesjami
cd <worktree root>
playwright-cli -s=uis09 open && playwright-cli -s=uis09 resize 1280 900
playwright-cli -s=uis09 run-code --filename=szczur-radar-web/dev/mock-api.js     # lub kopia z MODE='empty'|'loading'|'error'
playwright-cli -s=uis09 goto http://localhost:5183/admin
playwright-cli -s=uis09 set-color-scheme light|dark                              # emulacja prefers-color-scheme
playwright-cli -s=uis09 screenshot --full-page --filename=<png>                  # 4.4: cała strona
playwright-cli -s=uis09 screenshot "section[aria-labelledby=failed-title]" --filename=<png>   # 4.4: wycinek sekcji
playwright-cli -s=uis09 run-code --filename=<clip.js>   # 4.5: zrzut sekcji z marginesem 16 px (page.screenshot z clip),
                                                        # żeby obrys fokusu przy lewej krawędzi nie został ucięty
playwright-cli -s=uis09 resize 375 812                  # 375 px
playwright-cli -s=uis09 hover ".failed-posts li:nth-child(2) label"   # hover p2
playwright-cli -s=uis09 press Tab                       # powtarzane, aż document.activeElement == checkbox p4 (39 × Tab)
playwright-cli -s=uis09 eval "<pomiar>"                 # pomiary poniżej
```

Pełnostronicowe zrzuty są wysokie (ok. 3500 px na desktopie — 20 przebiegów nad listą); lista nieudanych postów jest na
samym dole. Dlatego obok każdego zrzutu 4.4 jest wycinek samej sekcji (`*-section.png`).

## Krok 4.4 — motywy i szerokości (MODE `data`)

| Wariant | Cała strona | Wycinek sekcji |
| --- | --- | --- |
| jasny, desktop 1280×900 | `4.4-light-desktop.png` | `4.4-light-desktop-section.png` |
| ciemny, desktop 1280×900 | `4.4-dark-desktop.png` | `4.4-dark-desktop-section.png` |
| jasny, 375×812 | `4.4-light-375.png` | `4.4-light-375-section.png` |
| ciemny, 375×812 | `4.4-dark-375.png` | `4.4-dark-375-section.png` |

Lista w MODE `data`: p1 (failed, niezgodność ze schematem), p2 (failed, błąd dostawcy, `błędy techniczne 1 ·
LlmUnavailableException: …`), p3 (martwy), p4 (wstrzymany: `wstrzymany · błędy techniczne 3 · IllegalStateException: …`).

### Pomiary przy 375 px

| Motyw | `documentElement.scrollWidth` | `documentElement.clientWidth` | Wynik |
| --- | --- | --- | --- |
| jasny | 375 | 375 | PASS |
| ciemny | 375 | 375 | PASS |

Wiersze listy (identyczne liczby w obu motywach; `li` / `.attempt` / `.technical-error`, scrollWidth × clientWidth, prawa
krawędź w px):

| Wiersz | `li` scroll/client | `.attempt` scroll/client | `.technical-error` scroll/client | prawa krawędź li / attempt / error |
| --- | --- | --- | --- | --- |
| p1 | 343 / 343 | 245 / 245 | — | 359 / 261 / — |
| p2 | 343 / 343 | 343 / 343 | 611 / 343 | 359 / 359 / 359 |
| p3 | 343 / 343 | 228 / 228 | — | 359 / 244 / — |
| p4 (wstrzymany) | 343 / 343 | 343 / 343 | 1158 / 343 | 359 / 359 / 359 |

Wiersz wstrzymanego posta nie wychodzi poza swój wiersz (`li` 343 = 343, `.attempt` 343 = 343, prawa krawędź 359 =
krawędź wiersza): PASS. `.technical-error` ma scrollWidth 1158 przy clientWidth 343 — to treść ucięta zamierzenie
(`overflow: hidden`, `text-overflow: ellipsis`, `white-space: nowrap`; pełny tekst w atrybucie `title`), nie przelew
układu.

## Krok 4.5 — 7 stanów listy (jasny motyw, desktop 1280×900)

| Stan | Plik | Jak uzyskano / co sprawdzono |
| --- | --- | --- |
| default | `4.5-default.png` | MODE `data`, kursor poza listą. |
| hover | `4.5-hover.png` | `hover` nad etykietą wiersza p2; `li.matches(':hover') === true`. Lista nie ma żadnego stylu `:hover` (tło wiersza `rgba(0,0,0,0)`, kolor bez zmian, `cursor: default` na etykiecie) — zrzut jest praktycznie identyczny z default (różnica tylko w natywnym wyglądzie checkboxa). Natywny tooltip `title` błędu technicznego nie trafia na zrzut. |
| focus-visible | `4.5-focus-visible.png` | 39 × Tab od początku strony; `document.activeElement` to checkbox p4 (`value = "p4"`), `:focus-visible === true`, obrys `solid 2px rgb(42, 95, 196)`, `outline-offset: 2px` — obrys widoczny na zrzucie. Tab pomija wyłączony checkbox p3. |
| disabled | `4.5-disabled.png` | Checkbox martwego posta p3 („Kupiłem PKO, zobaczymy”): atrybut `disabled` obecny, `:disabled === true`; klik w jego etykietę go nie zaznacza (`checked === false`). Widać też wyłączony przycisk „Ponów zaznaczone” (nic nie zaznaczono). |
| empty | `4.5-empty.png` | MODE `empty`: „Brak nieudanych postów.”, bez przycisku „Ponów zaznaczone”. |
| loading | `4.5-loading.png` | MODE `loading` (żądania `/api/admin/*` wiszą 60 s), zrzut zaraz po `goto`: „Wczytywanie…”, sekcja ma `aria-busy="true"`. |
| error | `4.5-error.png` | MODE `error` (500): `StatusMessage` z `role="alert"`: „Nie udało się wczytać nieudanych postów.”, bez przycisku. |

Żaden stan nie jest N/A.

## Zauważone kwestie wizualne (do oceny przez człowieka)

Ocena wyglądu należy do człowieka; poniżej tylko obserwacje.

1. Wiersz wstrzymanego posta (p4): na desktopie i przy 375 px separator „·” zostaje na końcu pierwszej linii
   (`wstrzymany · błędy techniczne 3 ·`), a błąd techniczny zaczyna się w nowej linii, ucięty wielokropkiem. Długi błąd
   nie rozpycha układu (patrz pomiary), ale wisząca kropka na końcu linii wygląda na urwaną.
2. Przy 375 px błąd p2 (`LlmUnavailableException: openrouter: HTTP 503 …`) też przechodzi do nowej linii i jest ucięty
   wielokropkiem; pełna treść tylko w `title` (niedostępna dotykiem na telefonie).
3. Martwy post p3 na desktopie ma linię `martwy · próby 3 · zmyślony cytat` wyrównaną do prawej w tym samym wierszu co
   tekst (krótki tekst mieści się obok, `justify-content: space-between`), a pozostałe wiersze mają ją pod tekstem po
   lewej — niespójne położenie zależne od długości tekstu.
4. Checkbox przylega do tekstu wpisu bez odstępu (natywny checkbox, brak `gap` w etykiecie).
5. Hover nie daje żadnej informacji zwrotnej na wierszu (patrz tabela 4.5).
6. Kontrast (zmierzony w stronie, wstrzymany wiersz): jasny motyw — tekst wpisu, linia próby, „wstrzymany” i błąd
   techniczny `rgb(95,102,114)` na białym 5,78:1, „błędy techniczne” `rgb(217,48,37)` 4,77:1; ciemny motyw — tekst
   `rgb(156,163,175)` na `rgb(22,23,29)` 7,04:1, „błędy techniczne” `rgb(248,113,113)` 6,46:1. Wszystkie ≥ 4,5:1.
   Tekst wpisu (excerpt) ma kolor drugorzędny, ten sam co linia próby — wpis i metadane różnią się tylko rozmiarem.
7. „wstrzymany” / „martwy” wyróżnione tylko pogrubieniem w kolorze drugorzędnym; „błędy techniczne N” na czerwono
   (`--destructive`).
