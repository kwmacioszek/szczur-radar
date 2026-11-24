# Kontrola w PR env (S-51, kryterium 4.5)

- **Środowisko i commit:** `szczur-radar-pr-204`, `https://szczur-radar-web-szczur-radar-pr-204.up.railway.app`, wdrożony commit `90030f9`. CI na nim było zielone (13/13).
- **Konto:** jednorazowe konto admina `agent-stream-and-inspector@example.com`, założone przez `create-user --role admin` za zgodą właściciela. Aktywację i logowanie zrobiłem przez `curl`: aktywacja 204, logowanie 200, `/api/auth/me` → `admin`. Link, hasło i ciasteczko były tylko w zmiennych powłoki i w pliku stanu w scratchpadzie, usuniętym po kontroli. Konto zostaje w bazie PR env.
- **Dane:** baza PR env była pusta („Brak ocenionych postów”). Uruchomiłem więc przebieg `POST /api/admin/pipeline-runs` z zakresem 2026-11-23…2026-11-23 i oboma źródłami. Run `cac2f668-d798-4d8b-9885-55d9c3204562` skończył się statusem `partial`:
  - pobrane 1001 postów (Wykop 908, Reddit 93, oba `succeeded`), wszystkie nowe;
  - ocenione 914, a 87 z nieudaną oceną.
- **Narzędzie:** Chromium przez `playwright-cli`, z wczytanym stanem sesji.

| Krok | Wynik |
| --- | --- |
| 2. `/` → `/posts`, lista bez „Wczytywanie…”, wiersze w regionach dni | adres `/posts`; brak „Wczytywanie…”; 50 wierszy; jeden region dnia „Wczoraj, 23 listopada”; 0 wierszy poza regionem dnia |
| 3. Klik pierwszego wiersza | dialog „Wpis użytkownika roboboro”; adres `/posts/9d0eaaa7-d450-4ae0-b121-bfd0abe6ca04`; wiersz `aria-current="true"`; „Otwórz oryginał na wykop.pl (otwiera się w nowej karcie)” → host `wykop.pl`; „Surowe dane” `aria-expanded="true"`, w regionie „Surowe dane posta” poprawny JSON |
| 4. Esc | dialog zamknięty, adres `/posts`, fokus na wierszu |
| 5. Adres posta z drugiej strony listy (`/posts?page=1`), otwarty wprost po wejściu na `/posts` | `/posts/2134059a-71fc-49eb-a6f9-99b127d73edb`: panel sam wczytał post („Wpis użytkownika ParadygmatYT”, nagłówek „Ocena”); posta nie ma na stronie 1 (0 odnośników) |
| 6. 375 px | `scrollWidth` 375 na `/posts`; dialog `{x:0, y:0, width:375, height:812}`, czyli całe okno; „wstecz” zamyka go, adres `/posts` |
