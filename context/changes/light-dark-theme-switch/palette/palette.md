# Paleta motywu: dwa warianty do wyboru (S-46, faza 2)

Tła z makiet M-5: jasne ciepłe papierowe (rejon `#f7f4ee`, „Czytnik”) i ciemne grafitowo-morskie (rejon `#101a20`, „Strumień”); `--surface` o krok od tła (jasny: jaśniejsza karta na papierze, ciemny: o stopień jaśniejszy panel). Jeden akcent w obu motywach. Wariant A: akcent niebieski (odcień ok. 215°). Wariant B: akcent morski (odcień ok. 195°, bardziej zielonkawy). Oba odsunięte od zieleni `--success` (ok. 140°) i czerwieni `--destructive`. `--success`, `--warning` i `--destructive` dostrojone do nowych teł; `--stance-neutral` / `--stance-mixed` to kolory tekstu stanowiska, `--highlight` to tło podświetlonego cytatu.

Wartości są też w `variant-a.css` i `variant-b.css` (format `style.css`: blok `:root` i `:root[data-theme='dark']`). Zrzuty: `variant-{a,b}-kitchen-sink-{light,dark}-375.png` (katalog przy 375 px) i `variant-{a,b}-feed-{light,dark}-1280.png` (feed `/`, atrapa `AUTH: 'user'`, 1280 px); wariant wstrzyknięty jako styl strony na `npm run dev`.

## Wartości ról

| Rola | A jasny | A ciemny | B jasny | B ciemny |
| --- | --- | --- | --- | --- |
| `--bg` | `#f7f4ee` | `#101a20` | `#f7f4ee` | `#101a20` |
| `--text` | `#4d5560` | `#a7b4bc` | `#4d5560` | `#a7b4bc` |
| `--text-h` | `#1a1814` | `#eef3f5` | `#1a1814` | `#eef3f5` |
| `--accent` | `#1d4fa8` | `#7fb0ff` | `#0a6a88` | `#5cc8e8` |
| `--on-accent` | `#ffffff` | `#101a20` | `#ffffff` | `#101a20` |
| `--success` | `#1a7a3e` | `#4cc77c` | `#1a7a3e` | `#4cc77c` |
| `--warning` | `#8f5400` | `#e6b04a` | `#8f5400` | `#e6b04a` |
| `--destructive` | `#bf2f28` | `#f0786f` | `#bf2f28` | `#f0786f` |
| `--surface` | `#fffdf8` | `#17252d` | `#fffdf8` | `#17252d` |
| `--highlight` | `#f4e3a0` | `#3a3519` | `#f4e3a0` | `#3a3519` |
| `--stance-neutral` | `#55606b` | `#93a2ac` | `#55606b` | `#93a2ac` |
| `--stance-mixed` | `#8a5200` | `#e0b25a` | `#8a5200` | `#e0b25a` |

## Współczynniki kontrastu (WCAG 2.x, wymagane ≥ 4.5:1)

19 par; policzone skryptem (ten sam wzór co `src/styleContract.test.ts`).

| Para (tekst na tle) | A jasny | A ciemny | B jasny | B ciemny |
| --- | --- | --- | --- | --- |
| `--text` na `--bg` | 6.87 | 8.31 | 6.87 | 8.31 |
| `--text` na `--surface` | 7.42 | 7.39 | 7.42 | 7.39 |
| `--text-h` na `--bg` | 16.15 | 15.77 | 16.15 | 15.77 |
| `--text-h` na `--surface` | 17.44 | 14.02 | 17.44 | 14.02 |
| `--accent` na `--bg` | 6.99 | 8.03 | 5.58 | 9.14 |
| `--accent` na `--surface` | 7.55 | 7.14 | 6.02 | 8.13 |
| `--success` na `--bg` | 4.91 | 8.20 | 4.91 | 8.20 |
| `--success` na `--surface` | 5.30 | 7.30 | 5.30 | 7.30 |
| `--warning` na `--bg` | 5.57 | 8.96 | 5.57 | 8.96 |
| `--warning` na `--surface` | 6.01 | 7.97 | 6.01 | 7.97 |
| `--destructive` na `--bg` | 5.24 | 6.40 | 5.24 | 6.40 |
| `--destructive` na `--surface` | 5.66 | 5.69 | 5.66 | 5.69 |
| `--stance-neutral` na `--bg` | 5.85 | 6.72 | 5.85 | 6.72 |
| `--stance-neutral` na `--surface` | 6.31 | 5.98 | 6.31 | 5.98 |
| `--stance-mixed` na `--bg` | 5.82 | 8.97 | 5.82 | 8.97 |
| `--stance-mixed` na `--surface` | 6.28 | 7.98 | 6.28 | 7.98 |
| `--on-accent` na `--accent` | 7.68 | 8.03 | 6.12 | 9.14 |
| `--text-h` na `--highlight` | 13.79 | 11.02 | 13.79 | 11.02 |
| `--text` na `--highlight` | 5.87 | 5.81 | 5.87 | 5.81 |
| **najniższy** | 4.91 | 5.69 | 4.91 | 5.69 |

## Wynik testu kontrastu

Prawdziwy test `npx vitest run src/styleContract.test.ts` na tymczasowej podmianie obu bloków w `szczur-radar-web/src/style.css` wartościami wariantu (podmiana cofnięta bit w bit po każdym teście, `diff` względem kopii pusty):

- wariant A: 60 testów zielonych (38 par × motyw i pozostałe przypadki pliku), 0 czerwonych;
- wariant B: 60 testów zielonych (38 par × motyw i pozostałe przypadki pliku), 0 czerwonych.

## Decyzja właściciela

Wybór (A / B / poprawki konkretnych ról): _do uzupełnienia_
