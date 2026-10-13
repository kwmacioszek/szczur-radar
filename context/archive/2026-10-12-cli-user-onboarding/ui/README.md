# Zrzuty — `/activate` i `/login` po aktywacji

Dowód bramki wizualnej fazy 4 planu `cli-user-onboarding` (kryteria ręczne 4.3 i 4.4).

Jak powstały:

- `npx vite --port 5173` i `playwright-cli` (Chromium, `--config` z głównego checkoutu);
- `szczur-radar-web/dev/mock-api.js` w kopiach z podmienionymi stałymi: `AUTH = 'anonymous'` i `ACTIVATION` = `valid`, `checking`, `invalid`, `error`, `sending` albo `policy`;
- przed każdym zrzutem `about:blank`, potem adres `/activate#<token>`, żeby SPA startowało od zera; samo przejście na adres różniący się tylko `#` nie przeładowuje strony;
- rozmiary 1280×900 i 375×812, motyw przez `set-color-scheme light|dark`;
- hover i fokus wymuszone w przeglądarce (`hover`, dwa razy `Tab` z pola „Nowe hasło” do przycisku „Ustaw hasło”).

Przy 375 px nie ma poziomego przewijania: `scrollWidth` = `clientWidth` = 375 (formularz).

Nazwy plików: `p4-<widok>-<stan>-<szerokość>-<motyw>.png`.

Zrzuty odświeżone 2026-10-12 po przeglądzie implementacji (F5, F8):
- formularz bierze fokus na pole „Nowe hasło”, gdy się pojawia;
- adres traci fragment z tokenem zaraz po jego odczycie.

## Macierz 7 stanów

| Stan | 1280 jasny | 1280 ciemny | 375 jasny | 375 ciemny |
| --- | --- | --- | --- | --- |
| default (formularz z e-mailem konta, fokus w polu „Nowe hasło”) | `p4-activate-form-1280-light.png` | `p4-activate-form-1280-dark.png` | `p4-activate-form-375-light.png` | `p4-activate-form-375-dark.png` |
| hover (przycisk „Ustaw hasło”) | `p4-activate-hover-1280-light.png` | `p4-activate-hover-1280-dark.png` | N/A — ekran dotykowy nie ma hover | N/A — jak obok |
| focus-visible (przycisk po `Tab`) | `p4-activate-focus-1280-light.png` | `p4-activate-focus-1280-dark.png` | N/A — ten sam komponent `AppButton` i token `--ring` co przy 1280 | N/A — jak obok |
| disabled (wysyłanie: przycisk zajęty, „Zapisywanie…”) | `p4-activate-sending-1280-light.png` | `p4-activate-sending-1280-dark.png` | N/A — ten sam przycisk co przy 1280; stan pilnuje `ActivateView.test.ts` | N/A — jak obok |
| error — walidacja w przeglądarce (za krótkie hasło) | `p4-activate-validation-1280-light.png` | `p4-activate-validation-1280-dark.png` | `p4-activate-validation-375-light.png` | `p4-activate-validation-375-dark.png` |
| error — polityka hasła z serwera (`password_policy`) | `p4-activate-policy-1280-light.png` | `p4-activate-policy-1280-dark.png` | N/A — ten sam układ co walidacja | N/A — jak obok |
| error — link nieważny (`activation_link_invalid`) | `p4-activate-invalid-1280-light.png` | `p4-activate-invalid-1280-dark.png` | `p4-activate-invalid-375-light.png` | `p4-activate-invalid-375-dark.png` |
| error — nie udało się sprawdzić linku (sieć, 5xx; „Spróbuj ponownie”) | `p4-activate-lookup-error-1280-light.png` | `p4-activate-lookup-error-1280-dark.png` | `p4-activate-lookup-error-375-light.png` | `p4-activate-lookup-error-375-dark.png` |
| empty | N/A — widok nie ma listy ani danych, które mogłyby być puste; link bez tokenu to stan „link nieważny” | N/A | N/A | N/A |
| loading (sprawdzanie linku, `aria-busy`) | `p4-activate-checking-1280-light.png` | `p4-activate-checking-1280-dark.png` | `p4-activate-checking-375-light.png` | `p4-activate-checking-375-dark.png` |

Po udanej aktywacji: `/login` z komunikatem „Hasło ustawione. Zaloguj się.” i wpisanym e-mailem.

| 1280 jasny | 1280 ciemny | 375 jasny | 375 ciemny |
| --- | --- | --- | --- |
| `p4-login-activated-1280-light.png` | `p4-login-activated-1280-dark.png` | `p4-login-activated-375-light.png` | `p4-login-activated-375-dark.png` |

## Uwagi

- Pola `/activate` biorą odstępy ze skali (`--space-*`). Pola `/login` mają jeszcze surowe `px`, więc są ok. 4 px wyższe. Wyrównanie to osobna zmiana `/10x-ui` dla `LoginView`.
- „Sprawdzanie linku…” jest wyrównane do lewej, jak treść karty; tytuł karty jest wyśrodkowany, jak w `/login`.
