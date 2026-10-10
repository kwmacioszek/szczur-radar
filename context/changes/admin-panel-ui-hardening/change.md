---
change_id: admin-panel-ui-hardening
title: Hardening UI panelu admina — tokeny, współdzielone komponenty i stany
status: implemented
created: 2026-10-10
updated: 2026-10-10
archived_at: null
---

## Notes

Zmiana wizualna przez `/10x-ui`.

- **Widok (jeden):** panel administratora `/admin` — `szczur-radar-web/src/views/AdminView.vue` (z `AppHeader.vue`, który renderuje się w tym widoku).
- **Źródło tokenów:** `:root` w `szczur-radar-web/src/style.css` (dziś `--text`, `--text-h`, `--bg`, `--accent`, `--sans`; tryb ciemny przez `prefers-color-scheme`; paleta startera Vite).
- **Wariant kontraktu:** istniejący, szczątkowy system na zwykłych zmiennych CSS — rozszerzamy `:root` w `style.css` i `src/components/`; bez Tailwinda, shadcn i nowych zależności.
- **Przed-audyt (2026-10-10):** skan literałów — `AdminView.vue` 5 kolorów (`#188038` ×2, `#b06000`, `#d93025` ×2) i 18 wartości `px`, `AppHeader.vue` 1 kolor; te same kolory stanów także w FeedView, LoginView, PostCard. `AdminView` czyta tokeny 3×, importuje 1 komponent. Brak `:focus-visible` w całym `src/`, brak stanu ładowania w panelu, brak reguł UI w plikach reguł agenta, brak lintera.
- **Granica z S-06** (`pipeline-run-history-dashboard`): S-06 rozbuduje historię przebiegów w tym widoku. Tu tokeny, komponenty i stany; przebudowa układu listy przebiegów → deferred do S-06.
- Poza roadmapą: przy `/10x-implement` własne issue `admin-panel-ui-hardening: …`.
