# Stany części panelu admina spoza wzorców (faza 4, kryterium 4.8)

Zrzuty `playwright-cli` na buildzie produkcyjnym (`npm run build`, `npx vite preview --port 4191`) z atrapą
`dev/mock-api.js` zainstalowaną przed `goto` (`now: 2026-10-20T10:00:00Z`; zegar przeglądarki nie jest zatrzymany, więc
pola „Od”/„Do” i czas trwającego przebiegu biorą dzisiejszą datę maszyny). Szerokość 375 px, cała strona, motyw z
`localStorage` `szczur-radar-theme` (`light`/`dark`), sprawdzony po wczytaniu przez `data-theme` na `<html>`.

| Plik (`-light-375.png` i `-dark-375.png`) | Adres i tryby atrapy | Co pokazuje |
| --- | --- | --- |
| `runs-loading` | `/admin`, `MODE: 'loading'` | ładowanie części „Przebiegi”: szkielety „Nowy przebieg” (formularz), „Dni bez danych” (linie) i „Ostatnie przebiegi” (wiersze), „Odśwież” wyłączone, pasek części bez licznika |
| `quality-loading` | `/admin/quality`, `MODE: 'loading'` | ładowanie części „Jakość ocen”: szkielet linii pod nagłówkiem |
| `posts-loading` | `/admin/posts`, `MODE: 'loading'` | ładowanie części „Nieudane posty”: szkielet wierszy |
| `runs-error` | `/admin`, `MODE: 'error'` | błąd każdej sekcji „Przebiegów”: trzy komunikaty z „Spróbuj ponownie” |
| `quality-error` | `/admin/quality`, `MODE: 'error'` | błąd części „Jakość ocen” z „Spróbuj ponownie” |
| `posts-error` | `/admin/posts`, `MODE: 'error'` | błąd części „Nieudane posty” z „Spróbuj ponownie” |
| `posts-disabled` | `/admin/posts` (domyślne tryby) | bez zaznaczenia: „Zaznaczone: 0”, wyłączone „Ponów zaznaczone (0)” i „Przywróć martwe (0)” (skrypt sprawdza `disabled` obu przed zrzutem) |
| `runs-start-busy` | `/admin`, `START: 'accepted'` i dodatkowa trasa skryptu, która wstrzymuje `POST /api/admin/pipeline-runs` | zajęty „Uruchom”: przycisk „Uruchamianie…” ze znakiem pracy i `aria-busy="true"` (sprawdzone przed zrzutem) |
| `posts-page-2` | `/admin/posts?page=1`, `FAILED: 'many'` | druga strona nieudanych postów: 7 wierszy (51–57), „57 postów”, licznik 57 w pasku części, „Strona 2 z 2”, „Poprzednie” aktywne, „Następne” wyłączone |

Bez poziomego przewijania strony przy 320 px (`document.documentElement.scrollWidth`): `/admin` 320,
`/admin/quality` 320, `/admin/posts` 320, `/admin/posts` z `FAILED: 'many'` 320, `/admin/posts?page=1` z
`FAILED: 'many'` 320. Tabele przewijają się w swoich ramkach.
