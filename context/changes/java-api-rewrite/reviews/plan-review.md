<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Backend w Javie (Spring Boot) zamiast Pythona/FastAPI

- **Plan**: `context/changes/java-api-rewrite/plan.md`
- **Mode**: Deep
- **Date**: 2026-10-02
- **Verdict**: SOUND after triage (initial: REVISE)
- **Findings**: initial 1 critical, 5 warnings, 0 observations; remaining 0
- **Triage**: complete — F1–F6 FIXED zgodnie z decyzjami użytkownika
- **Scope**: przegląd i poprawki dokumentacji; implementacja backendu nie została rozpoczęta.

## Verdicts

| Dimension | Initial verdict | After triage |
|-----------|-----------------|--------------|
| End-State Alignment | FAIL | PASS |
| Lean Execution | PASS | PASS |
| Architectural Fitness | PASS | PASS |
| Blind Spots | WARNING | PASS |
| Plan Completeness | WARNING | PASS |

## Grounding

Grounding: 7/7 paths ✓, 3/3 symbols ✓, brief↔plan ✓.

Sprawdzone istniejące ścieżki: `.github/workflows/ci.yml`, `.railway/railway.ts`, `.gitignore`, `README.md`, `REPOSITORY_GUIDELINES.md`, `szczur-radar-api/README.md`, `context/deployment/deploy-plan.md`. Symbole: `LoginRateLimiter.reserve`, `LoginRateLimiter.finish`, `PUBLIC_ROUTES`. Nowe pliki Javy są celami implementacji, nie istniejącymi plikami.

Weryfikacja kodu przez jednego subagenta GPT-6 Luna z reasoning xhigh: limiter i współbieżność, kontrakty SPA/auth, konfiguracja CI/Railway i zależne pliki. Dokumentacja zewnętrzna sprawdzona przez Context7 i Exa. Nie użyto subagentów Astra.

## Findings

### F1 — Token bucket nie zachowuje obecnego limitu logowań

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — rzeczywisty kompromis; wymaga świadomej decyzji.
- **Dimension**: End-State Alignment
- **Location**: Critical Implementation Details; faza 2.8; Testing Strategy.
- **Detail**: `app/auth/rate_limit.py` przechowuje timestampy porażek w ruchomym oknie i osobne liczniki oczekujących prób. Kubełek z ciągłym uzupełnianiem 5 tokenów/15 min dopuści próbę po 3 min od pięciu jednoczesnych porażek; Python blokuje do 15 min. Reset kubełka po sukcesie nie może usuwać innych trwających rezerwacji. Dowody: `szczur-radar-api/app/auth/rate_limit.py:43`, `:106`, `:118`; `szczur-radar-api/tests/test_rate_limit.py:49`, `:71`; [Bucket4j reference](https://bucket4j.com/8.18.0/toc.html).
- **Fix A ⭐ Recommended**: Przenieść obecny algorytm ruchomego okna i rezerwacji do Javy.
  - Strength: Zachowuje kontrakt i istniejące testy.
  - Tradeoff: Własna niewielka klasa limitera zamiast Bucket4j.
  - Confidence: HIGH — zachowanie opisują kod i testy.
  - Blind spot: Konieczny test sukcesu podczas innych trwających prób.
- **Fix B**: Zachować Bucket4j jako jawną zmianę kontraktu i doprecyzować refill/reset/rezerwacje.
  - Strength: Zachowuje wybraną bibliotekę.
  - Tradeoff: Inna gwarancja limitu porażek w ruchomym oknie.
  - Confidence: HIGH — różnica algorytmów jest jednoznaczna.
  - Blind spot: Nadal wymaga zaprojektowania obsługi rezerwacji.
- **Decision**: FIXED — użytkownik wybrał Fix A.
- **Applied**: Plan i brief opisują port limitera, atomowe sprawdzanie obu limitów, zachowanie cudzych rezerwacji po sukcesie, sprzątanie i dokładne `Retry-After`. Usunięto Bucket4j/Caffeine z planowanych zależności; dodano scenariusze granic czasu i współbieżności.

### F2 — Ciasteczko ogranicza sesję mimo deklarowanych 30 dni bezczynności

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — rzeczywisty kompromis; wymaga świadomej decyzji.
- **Dimension**: End-State Alignment
- **Location**: faza 2.6; AuthIT.
- **Detail**: Aktualizacja czasu dostępu sesji w bazie nie odnawia automatycznie `Max-Age` ciasteczka. Aktywność w dniu 29 nie zapobiega jego wygaśnięciu w dniu 30 od wystawienia. Dowód: [Spring Session — SessionRepositoryFilter](https://github.com/spring-projects/spring-session/blob/main/spring-session-core/src/main/java/org/springframework/session/web/http/SessionRepositoryFilter.java).
- **Fix A ⭐ Recommended**: Odnawiać ciasteczko po poprawnych uwierzytelnionych żądaniach API.
  - Strength: Realizuje wybrane 30 dni bezczynności.
  - Tradeoff: Dodatkowa logika odnawiania i nagłówki `Set-Cookie`.
  - Confidence: HIGH — warunek zapisu ciasteczka jest widoczny w kodzie biblioteki.
  - Blind spot: Test musi obejmować ciasteczko oraz sesję JDBC.
- **Fix B**: Zachować ustawienia i opisać limit 30 dni od wystawienia ciasteczka.
  - Strength: Prostsza implementacja, bliższa dotychczasowemu S-01.
  - Tradeoff: Zmienia przyjęty kontrakt bezczynności.
  - Confidence: HIGH.
  - Blind spot: Brak istotnych.
- **Decision**: FIXED — użytkownik wybrał Fix A.
- **Applied**: Plan określa `SessionCookieRenewalFilter`, odnowienie istniejącego ID z tymi samymi atrybutami przed zatwierdzeniem odpowiedzi, wykluczenia błędów/sond/logout i osobną obsługę loginu. Testy obejmują aktywność w dniu 29, przekroczenie pierwotnego terminu i 30 dni bezczynności. Brief zsynchronizowany.

### F3 — Domyślny request cache może utworzyć sesję przy odpowiedzi 401

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąska.
- **Dimension**: Blind Spots
- **Location**: faza 2.5–2.7; AuthIT.
- **Detail**: Domyślny `HttpSessionRequestCache` może zapisać anonimowe żądanie w sesji przed odpowiedzią 401, np. przy `Accept: text/html`. Jest to sprzeczne z kontraktem braku ciasteczka w odpowiedziach błędów. Dowód: [Spring Security architecture](https://docs.spring.io/spring-security/reference/7.0/servlet/architecture.html).
- **Fix**: Ustawić `NullRequestCache` i sprawdzić 401 bez `Set-Cookie` oraz bez nowych rekordów sesji.
- **Decision**: FIXED — użytkownik zaakceptował proponowaną poprawkę.
- **Applied**: Konfiguracja jawnie wyłącza request cache; AuthIT sprawdza anonimowe `/api/auth/me` z `Accept: application/json` i `text/html`, wraz ze stanem tabel sesji.

### F4 — Rollback nie odtwarza sekretu wymaganego przez Pythona

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąska.
- **Dimension**: Blind Spots
- **Location**: Migration Notes.
- **Detail**: Pierwotny plan usuwał `JWT_SECRET`, a procedura powrotu do Pythona nie odtwarzała jego wartości. Stary backend wymaga sekretu przy starcie (`szczur-radar-api/main.py:23`, `app/auth/tokens.py:32`); `preserve()` nie tworzy brakującej wartości. Generator CI działa tylko dla środowisk PR (`.github/workflows/ci.yml:159`).
- **Fix**: Pierwotna propozycja — odtworzyć sekret przed uruchomieniem Pythona.
- **Decision**: FIXED — inne rozwiązanie użytkownika: powrót do Pythona jest wykluczony.
- **Applied**: Usunięto procedurę revertu do Pythona z planu i briefu. Ścieżka awaryjna to naprawa Javy/konfiguracji i ponowne wdrożenie oraz migracje fix-forward. PITR odzyskuje dane; odtworzony schemat wymaga zgodności z Javą przed podłączeniem. Przerwa po nieudanym pierwszym przełączeniu trwa do skutecznej naprawy.

### F5 — Awaryjne wyłączenie Embabel przeczy kryteriom sukcesu

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest wąska.
- **Dimension**: End-State Alignment
- **Location**: faza 3.2–3.3; kryterium 3.2.
- **Detail**: Pierwotny fallback pozwalał wyłączyć autokonfigurację poza testem, mimo wymagania działającej platformy i zarejestrowanego agenta w serwerze.
- **Fix**: Usunąć fallback i testować rzeczywistą konfigurację serwera.
- **Decision**: FIXED — użytkownik wybrał prawdziwy klucz oraz wspólną konfigurację `LLM_API_PROVIDER`, `LLM_API_KEY`, `LLM_MODEL`.
- **Applied**: Jeden aktywny dostawca na uruchomienie: Gemini przez Google AI Studio, DeepSeek bezpośrednio lub Luna przez OpenRouter. Brak kluczy niewybranych dostawców nie blokuje startu. Plan opisuje mapowanie na Embabel, dostarczenie konfiguracji do CI/Railway, jawne błędy brakującej konfiguracji i test rejestracji bez wywołań LLM. Usunięto wymaganie `OPENAI_API_KEY` i fallback. Historyczny tytuł kroku Progress 3.2 pozostawiono zgodnie z regułą jego niezmienności; jego nowe znaczenie jest jawnie określone w fazie 3.
- **Sources**: [Embabel provider configuration](https://github.com/embabel/embabel-agent/blob/main/embabel-agent-docs/src/main/asciidoc/getting-started/installing/page.adoc), [OpenRouter model identifiers](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties).

### F6 — Roadmapa nadal wymaga odrzuconych mechanizmów

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — szybka decyzja; poprawka jest oczywista i wąska.
- **Dimension**: Plan Completeness
- **Location**: faza 4; `context/foundation/roadmap.md`, F-03.
- **Detail**: Roadmapa wymagała `token_version`, migracji z cofnięciem i pozostawiała otwarte pytanie o zachowanie starych kont. Plan przyjął już sesje JDBC, fix-forward i czystą bazę, ale nie obejmował synchronizacji roadmapy.
- **Fix**: Dodać synchronizację roadmapy do fazy 4, zaktualizować Markdown, generator Swift i grafikę.
- **Decision**: FIXED — użytkownik zlecił aktualizację roadmapy, także Markdown.
- **Applied**: Zsynchronizowano F-03, powiązane ustalenia F-02, historyczny opis migracji S-01 i odroczony zakres FR-029. Roadmapa i generator mają rewizję v2.4 z 2026-10-02; PNG wygenerowany i obejrzany. F-03 zachowuje status `planning`, a postęp implementacji nie został oznaczony jako wykonany.

## Verification after triage

- Spójne 4 fazy i 26 par kryterium sukcesu ↔ Progress; jedna sekcja Progress na końcu, brak checklist poza nią.
- Plan/brief/roadmapa odzwierciedlają decyzje użytkownika; historyczny tytuł 3.2 ma jawną korektę w fazie 3.
- Generator uruchomiony: `CLANG_MODULE_CACHE_PATH=/private/tmp/szczur-clang-cache SWIFT_MODULE_CACHE_PATH=/private/tmp/szczur-swift-cache swift -sdk /Library/Developer/CommandLineTools/SDKs/MacOSX14.5.sdk tools/render_roadmap.swift`.
- `docs/visuals/roadmap-mvp.png` sprawdzony wizualnie: treści kart mieszczą się, statusy i wersja są zgodne z Markdownem.
- `git diff --check` bez błędów. Nie uruchamiano testów aplikacji — zmieniono dokumentację i generator grafiki.

## Triage summary

- Fixed: F1 (Fix A), F2 (Fix A), F3, F4 (fix-forward bez powrotu do Pythona), F5 (prawdziwy klucz i wspólne `LLM_*`), F6.
- Skipped: none.
- Accepted without a plan change: none.
- Dismissed: none.
- Verdict after fixes: REVISE → SOUND.
- Next: `/10x-implement java-api-rewrite phase 1`.

SOUND jest oceną poprawionego planu, nie potwierdzeniem działającej implementacji lub wdrożenia. Weryfikacja integracji bibliotek, CI, PR env i produkcji pozostaje w kryteriach faz.
