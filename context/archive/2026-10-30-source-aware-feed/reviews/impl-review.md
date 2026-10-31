<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-29: źródło widoczne w feedzie, świeżość bez kłamstwa

- **Plan**: context/changes/source-aware-feed/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-10-31
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 2 observations

Faza 3 ma oczekujący wiersz Manual 3.11, więc domyślny zakres by ją pominął; przegląd objął ją jawnie.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Success Criteria

Uruchomione na `5a5600f` w tym przeglądzie: `./mvnw -B verify` (995 testów jednostkowych, 326 IT, z testowymi `LLM_*`), `npm test` (511), `npm run build`, kryteria 1.4, 1.5, 2.4–2.6, 3.1–3.5, 3.7 (na zapisanych `BEFORE`, `AFTER`), 3.9, 3.10. Kryteria 3.6 i 3.8 wymagają sesji w PR env: przeszły na żywo 2026-10-30 na tym samym commicie i nie były powtarzane. Manual 3.11 czeka na ocenę użytkownika.

## Findings

### F1 — Nowy SPA przy starym API pokazuje `undefined · undefined` przy każdym poście

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/labels.ts:84, szczur-radar-web/src/components/PostCard.vue:24, szczur-radar-web/src/views/PostDetailView.vue:156, context/deployment/deploy-plan.md (S-29, Deploy window)
- **Detail**: API i web wdrażają się w nieustalonej kolejności. `FeedView.vue:174` chroni etykietę przed starym kształtem `/api/freshness`, ale nic nie chroni `source` i `channel` posta: przy starym API są `undefined`, `label()` zwraca je bez zmian, a `postOrigin` składa napis `undefined · undefined`. Plan nazywa fazę 1 „bezpieczną w obie strony wdrożenia” (linia 64), a Deploy window mówi „The list works”. Ten sam napis może się pojawić po revercie, bo okno działa wtedy odwrotnie. Żaden test tego nie obejmuje.
- **Fix A ⭐ Recommended**: Renderować oba `<span class="origin">` z `v-if="post.source"`, z jednym testem w `PostCard.test.ts` i jednym w `PostDetailView.test.ts`.
  - Strength: Ten sam wzorzec co `freshness?.sources` (`FeedView.vue:174`): w oknie wdrożenia oznaczenia nie ma, a plan i Deploy window stają się prawdziwe.
  - Tradeoff: Warunek na polu wymaganym w typie TS; potrzebny krótki komentarz.
  - Confidence: HIGH — mała zmiana w dwóch plikach, a precedens jest w tej samej zmianie.
  - Blind spot: None significant.
- **Fix B**: Zostawić kod i dopisać ten stan do Deploy window i Rollback.
  - Strength: Bez zmian w kodzie, CI i PR env.
  - Tradeoff: Przez kilkadziesiąt sekund lub minuty użytkownicy widzą `undefined · undefined` na każdej karcie.
  - Confidence: MEDIUM — długość okna nie jest zmierzona, a web zwykle wstaje szybciej niż API z `migrate`.
  - Blind spot: Rzeczywista kolejność wdrożeń na Railway.
- **Decision**: FIXED (Fix A)

### F2 — README API dalej mówi, że API nie pokazuje kanału

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/README.md:174
- **Detail**: Zdanie „API jej jeszcze nie pokazuje (S-29).” jest fałszywe: `PostController.java` zwraca `channel`. Takie samo zdanie w `prd.md` zostało poprawione, ale plan wskazał w README tylko linię 290, a bramka 3.3 nie łapie tego brzmienia.
- **Fix**: Zastąpić zdanie informacją, że API oddaje kanał w polu `channel` odpowiedzi posta, bez przedrostka (S-29).
- **Decision**: FIXED

### F3 — Wyłączone źródło zostaje w etykiecie z zamrożoną datą, a runbook tego nie mówi

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: context/deployment/deploy-plan.md (S-29, „Which sources are listed”), szczur-radar-api/src/main/java/pl/szczurradar/api/pipeline/run/PipelineRunRepository.java:82
- **Detail**: Etykieta nie czyta konfiguracji, co jest decyzją właściciela. Dokumenty opisują tylko włączenie źródła. Po cofnięciu Reddita przez usunięcie `REDDIT_SUBREDDITS` (runbook S-27) użytkownicy będą widzieć `Reddit <stara data>` bez końca. To prawda, ale wygląda jak awaria, a „Dni bez danych” filtrują po skonfigurowanych źródłach, więc obie powierzchnie zachowają się inaczej.
- **Fix**: Dopisać w sekcji S-29 jedno zdanie, że źródło wyłączone zostaje w etykiecie ze swoją ostatnią datą.
- **Decision**: FIXED

### F4 — Sekcja S-20 mówi bez zastrzeżenia, że etykieta się przesuwa

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/deployment/deploy-plan.md:1934
- **Detail**: Zdanie „the label "Dane zaktualizowane" moves” dotyczy przebiegu z padniętym źródłem. Od S-29 przesuwa się tylko data źródła, które dało posty. Odpowiednik w `prd.md` dostał dopisek, a linia 1937 tej samej sekcji mówi to poprawnie, więc tekst jest tylko niepełny.
- **Fix**: Dopisać w linii 1934: „(since S-29 only the date of the source that gave posts)”.
- **Decision**: FIXED
