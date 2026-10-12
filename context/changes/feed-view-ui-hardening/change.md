---
change_id: feed-view-ui-hardening
title: Hardening UI feedu — tokeny, współdzielone komponenty i stany
status: impl_reviewed
created: 2026-10-12
updated: 2026-10-12
archived_at: null
---

## Notes

Zmiana wizualna przez `/10x-ui`. Zapowiedziana w `context/archive/2026-10-10-admin-panel-ui-hardening/plan.md:39` (FeedView + PostCard, przed S-03 i S-04, żeby nowe widoki dziedziczyły kontrakt).

- **Widok (jeden):** feed `/` — `szczur-radar-web/src/views/FeedView.vue` z `szczur-radar-web/src/components/PostCard.vue`. `AppHeader.vue` renderuje się w widoku, ale jest już na kontrakcie (bez zmian).
- **Źródło tokenów:** `:root` w `szczur-radar-web/src/style.css` — role kolorów dla obu motywów (`prefers-color-scheme`) oraz skale `--font-size-*`, `--space-*`, `--radius*`.
- **Wariant kontraktu:** istniejący system projektowy na zwykłych zmiennych CSS — rozszerzamy `:root` w `style.css` i `src/components/` (`AppButton`, `StatusMessage`, `StatusBadge`); bez Tailwinda, shadcn i nowych zależności.
- **Przed-audyt (2026-10-12):**
  - Skan literałów z `/10x-ui` (kolory, klasy palety, wartości arbitralne): 0 w obu plikach — są już na liście `CLEANED` w `styleContract.test.ts:85-95`.
  - Literały `px`/`rem` w `<style>`: `FeedView.vue` 6, `PostCard.vue` 21 (policzone ponownie w researchu); spoza skali m.in. pismo 13/15/18px (`PostCard.vue:54`, `:65`, `:74`, `:79`, `:89`, `:111`), odstęp 6px (`:78`, `:88`, `:107-108`), h1 `32px` powielający globalny `--font-size-xl` (`FeedView.vue:73`). Kontrola łapie tylko kolory, a reguła w `REPOSITORY_GUIDELINES.md` § UI wymaga odstępów i pisma ze skali.
  - Odczyty `var(--…)`: `FeedView` 2, `PostCard` 7 (żadnego `--space-*`, `--font-size-*`, `--radius*`, `--border`); `AdminView` dla porównania 20.
  - Importy `AppButton` / `StatusMessage` / `StatusBadge`: 0 (`AdminView` 3). Ręczne odpowiedniki: komunikat błędu `FeedView.vue:47`, przyciski pagera `:55`, `:57`, znacznik flagi `PostCard.vue:105-112`, kolor stanowiska `:97-103`, linia `color-mix` zamiast `--border` `:47`.
  - Kandydaci na zarzuty architektury: strona poza zakresem (`/?page=99`) pokazuje sam pager bez komunikatu (`FeedView.vue:49-58`); błąd wypiera listę i pager bez „Spróbuj ponownie” (`:47`); router bez `scrollBehavior` (`router/index.ts:47`); ładowanie bez `aria-busy`.
  - Bramka wizualna nie obejmuje feedu: `szczur-radar-web/dev/mock-api.js` nie zna `/api/posts` (404 → widok błędu), kitchen sink nie ma `PostCard`.
  - Pliki reguł: `REPOSITORY_GUIDELINES.md` § UI (tokeny, komponenty, zakaz literałów, kitchen sink, 7 stanów); `CLAUDE.md` / `AGENTS.md` tylko odsyłają do `/10x-ui`. Brak reguły zachęcającej do jednorazowych wartości.
- **Granice:** link do oryginału i widok szczegółowy → S-03 (`post-detail-view`); filtry i ich stan pusty → S-04 (`filter-by-assessment`); data ostatniej udanej aktualizacji nad listą → S-05. Tu tylko kontrakt feedu i `PostCard`, który te slice'y odziedziczą.
- Poza roadmapą: przy `/10x-implement` własne issue `feed-view-ui-hardening: …`.
