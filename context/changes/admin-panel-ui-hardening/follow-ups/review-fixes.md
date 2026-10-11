# Implementation review fixes

- **Report**: ../reviews/impl-review.md
- **Date**: 2026-10-11

## F1 — Ponowienie po błędzie pokazuje fałszywe stany puste

- **Decision**: Napraw teraz — wybrane przez użytkownika po pokazaniu konkretnego diffu.
- **Status**: DONE
- **Changes**: `loadRuns()` i `loadFailed()` w `szczur-radar-web/src/views/AdminView.vue` ustawiają ładowanie przy pustej liście przed ponownym żądaniem; istniejące listy pozostają widoczne podczas odświeżania. Zaktualizowano komentarze stanów ładowania.
- **Regression coverage**: Dwa przypadki w `AdminView.test.ts`: pierwszy HTTP 500 → oczekujące ponowienie → odpowiedź pusta lub z danymi; tekst ładowania, `aria-busy`, brak fałszywego stanu pustego i niezależne zakończenie sekcji. Test zachowania list podczas odświeżania obejmuje przebiegi i nieudane posty.
- **Before fix**: Oba nowe przypadki FAIL; 21 pozostałych testów AdminView PASS.
- **Verification**: Node v24.21.0; pełne `npm test` 105/105 PASS; `npm run build && ! grep -ri kitchen dist/` PASS; `git diff --check` PASS.
- **Delivery**: Poprawka F1 w tym samym zestawie zmian co raport i dowody F2, na gałęzi `feature/admin-panel-ui-hardening` dla PR #42. CI wskazane w pierwotnym raporcie dla HEAD `79706bc` nie jest weryfikacją tej poprawki.

## F2 — Kontrola PR env ma tylko deklarację wykonania

- **Decision**: Napraw teraz — użytkownik wybrał rzeczywistą kontrolę PR env i zalogował się do panelu administratora.
- **Status**: DONE
- **Changes**: Dodano do `ui-after/README.md` datę, adres, commit i deployment PR #42, wynik kontroli na prawdziwych danych, tabelę 12 zrzutów oraz 4 dodatkowe zrzuty viewportu feedu. Checklist impl-review odhaczony po triage obu ustaleń.
- **Verification**: `/api/auth/me` HTTP 200, rola admin; anonimowo HTTP 401. API źródeł/przebiegów/nieudanych postów/feedu HTTP 200. Panel, feed i login: oba motywy, viewporty 1280×900 i 375×812, bez alertów UI i poziomego przewijania. Obejrzano wszystkie warianty panelu/logowania i viewportu feedu; wygląd zgodny z tokenami i zrzutami mocka.
- **Evidence**: `reviews/evidence/F2-pr-env-result.json`, `F2-capture.js`, `F2-feed-viewport.js`, `ui-after/pr42-*.png` i opis w `ui-after/README.md`.
- **Version**: Commit web `79706bc56b8822b7d22f14da1c2d2893a12ebfdf`, deployment `d69cf8b3-5d56-4975-922d-52f57733b181` SUCCESS. Kontrola F2 nie obejmuje wdrożenia poprawki F1.
