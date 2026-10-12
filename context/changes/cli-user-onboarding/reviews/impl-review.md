<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: S-07 cli-user-onboarding

- **Plan**: context/changes/cli-user-onboarding/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5
- **Date**: 2026-10-12
- **Verdict**: NEEDS ATTENTION (triage 2026-10-12: 7 fixed — F1, F2, F3, F5, F6, F7, F8; 1 deferred — F4)
- **Findings**: 0 critical, 3 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

### Plan drift

- Every planned item is implemented as intended. Nothing is missing, and nothing from "What We're NOT Doing" was done: no auto-login, no rate limit on the activation routes, no token cleanup job, no `tools/db-backup` change, no OTT, no LoginView restyle.
- The existing `CreateAdminCommandTest`, `CreateAdminCommandIT` and `CommandLineTest` are unchanged after the refactor.
- Accepted refinements:
  - `reset` upserts the token before clearing the password (`ActivationLinks.java:102-106`). It is still one transaction, and the order matches the lock order of `activate()`, so the two cannot deadlock.
  - "sending" at 375 px is N/A, with the reason given in `ui/README.md`.

### Unplanned additions

All are harmless refinements within the plan's intent:
- the sealed `IssueOutcome` / `IssuedLink` / `ActivationResult` types;
- the `LinkCommand` base class;
- `CommandChecksTest`;
- `ErrorCodeTest` switched to `Map.ofEntries`, needed at 12 pairs;
- the `CommandLine` usage width;
- the stale header comment fixed in `src/api/auth.ts`;
- `activationFailure()`;
- the `lookup_failed` phase with a retry button;
- the logout mock.

### Automated criteria (re-run 2026-10-12)

- Phases 1–3: `./mvnw -B verify` PASS locally (569 unit tests, 125 integration tests; `MigrationsIT` 17, `LinkCommandIT` 5, `CreateAdminCommandIT` 7).
- Phase 4: `npm test` PASS (129/129) and `npm run build` PASS.
- Phase 5: CI on `1116a33` is green (run 38300076569). The branch sits on the current `origin/main` tip `6bebf82` (merge-base = tip; S-05 is not in `main`, so V6 is still free).

### Manual criteria

Each has evidence:
- 2.5: the commit message of `5f374c1`;
- 4.3 and 4.4: `ui/README.md` and 32 PNGs;
- 5.3–5.8: `deploy-plan.md`, section S-07.

### Considered and not reported

- argon2 inside the activation transaction: by plan design. It runs only for a valid, locked token.
- LoginView px spacing: listed under the plan's "NOT doing" and already noted in `ui/README.md`.
- The `/login` notice reappearing after a reload (`history.state` survives a reload): harmless.

## Findings

### F1 — A login in flight can outlive reset-password

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationLinks.java:94-109, :127-145 (with auth/AuthController.java:89-101)
- **Detail**:
  - How a login runs: it reads `password_hash` (`AccountDetailsService.loadUserByUsername`, a plain read), verifies argon2 (~50–100 ms), then calls `startSession`. Spring Session inserts the `spring_session` row only when the response commits.
  - The race: if the reset transaction (`DELETE FROM spring_session WHERE principal_name = …` plus clearing the password) commits between that read and that insert, the new session survives the reset and lives 30 days.
  - Why it is exploitable: successful logins are not rate-limited (`LoginRateLimiter` counts failures only). An attacker who holds the old password and keeps logging in almost always has one login in that window.
  - `activate()` does not delete the account's sessions, so such a session also outlives the owner's re-activation.
  - Effect: the plan's promise that `reset-password` "immediately logs out every session" (Desired End State) holds only when no login runs at the same time, and a compromised password is exactly when reset is used.
- **Fix A ⭐ Recommended**: In `activate()`, also delete the account's sessions (`deleteSessions(user.getId())`, the same transaction, after the token lock). Add an `ActivationLinksIT` case: a session created after the reset is gone after the activation. Update the docs to say that sessions are revoked at the reset and again at the activation.
  - Strength: closes the window at the moment the owner regains access. It reuses the reset's SQL, takes ~3 lines plus one test, and follows the usual rule that a new password logs out everywhere.
  - Tradeoff: between the reset and the activation (≤ 12 h) a session that straddled the reset still works.
  - Confidence: HIGH — the same SQL already runs in the server context (`ActivationLinksIT` tests reset against the real Spring Session repository).
  - Blind spot: the race was not reproduced in a test; it is traced from the code.
- **Fix B**: A credentials version on `users` (e.g. `password_changed_at`), stored in the session at login and checked on every request; a session older than the last reset or activation is rejected.
  - Strength: closes the window completely, including between the reset and the activation.
  - Tradeoff: a new migration, a check on every authenticated request and a session attribute — well beyond S-07's plan.
  - Confidence: MED — touches the session and filter chain that every route depends on.
  - Blind spot: how it interacts with `SessionCookieRenewalFilter` and Spring Session's attribute serialization.
- **Decision**: FIXED via Fix A.
  - Code: `activate()` deletes the account's sessions in its transaction.
  - Test: `ActivationLinksIT.activationLogsTheAccountOutEverywhere`. It fails without the fix (break-check) and passes with it.
  - Docs: README and deploy-plan S-07.
  - Fix B parked after the MVP: `roadmap.md` v2.14, Parked "Unieważnianie sesji wersją poświadczeń"; renderer and PNG updated.

### F2 — Activation routes renew a logged-in browser's session cookie

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/auth/SessionCookieRenewalFilter.java:34-35
- **Detail**:
  - `EXCLUDED_PATHS` lists only `/api/health`, `/api/ready`, `/api/auth/login` and `/api/auth/logout`.
  - For a browser that holds another account's valid `sr_session` (the cookie has `Path=/api`, and `/activate` stays open to a logged-in user), a 2xx answer from `/api/auth/activation/lookup` or `/api/auth/activation` sends `Set-Cookie: sr_session` (renewal).
  - This contradicts the auth contract (plan.md:88, "set no cookie"), the controller javadoc (`ActivationController.java:21`), `szczur-radar-api/README.md:215` and `src/api/activation.ts`.
  - `ActivationIT` tests only anonymous calls.
  - The impact is negligible: the SPA ends that session right after the activation.
- **Fix**: Add both activation paths to `EXCLUDED_PATHS`. Add an `ActivationIT` case that sends a valid session cookie and asserts no `Set-Cookie` on either route.
- **Decision**: FIXED.
  - Code: both paths are in `EXCLUDED_PATHS`, and the javadoc says why.
  - Test: `ActivationIT.aBrowserLoggedInAsAnotherAccountGetsNoCookie`. It fails without the fix on `Set-Cookie of POST /api/auth/activation/lookup` and passes with it; `AuthIT` stays green.

### F3 — Exit codes and option names held as int/String constants

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/cli/LinkCommand.java:40-43, cli/CommandChecks.java:23, cli/CreateUserCommand.java:29
- **Detail**:
  - The closed set of exit codes now lives in five places:
    - `LinkCommand.ISSUED = 0` / `REFUSED = 1` (new);
    - `CommandLine.USAGE_ERROR = 2`;
    - the `CreateAdminCommand.USAGE_ERROR` alias;
    - `AdminBootstrap.CREATED` / `REFUSED`;
    - the literal 0/1 in `MigrateCommand`.
  - Option names are free `String` constants in a `Set<String>` (`CommandChecks.EMAIL = "email"`, `CreateUserCommand.ROLE = "role"`).
  - This breaks the lessons.md rule "Zamknięte zbiory wartości trzymaj w enumach, nie w wolnych stringach" (codes and command names are named there). The pattern predates S-07, but this change doubles it.
- **Fix A ⭐ Recommended**: In a separate refactor commit:
  - add an `ExitCode` enum in `cli/` (`OK(0)`, `REFUSED(1)`, `USAGE(2)`) used by every command (`CommandLine` maps it to the process code);
  - add an `Option` enum (`EMAIL`, `ROLE`) carrying the flag name, used by `CommandChecks.options`.

  The tests keep their assertions.
  - Strength: one place per set, as the lessons rule asks; the CLI is still small (6 commands/classes).
  - Tradeoff: touches `create-admin`, `migrate` and their tests (~8 files), mechanically.
  - Confidence: HIGH — the same refactor as `CommandType` from `java-api-rewrite`.
  - Blind spot: some tests may compare against the int constants and need a mechanical update.
- **Fix B**: Leave it and record a follow-up change for the whole CLI.
  - Strength: keeps this PR focused on S-07.
  - Tradeoff: the rule keeps being broken, and the next command adds a sixth place.
  - Confidence: HIGH — nothing depends on it today.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A, after a wider discussion. It goes in a separate refactor commit.
  - `ExitCode` (`OK` 0, `REFUSED` 1, `FAILED` 1, `USAGE` 2) is the return type of `Command.run`. `FAILED` was split from `REFUSED`, so a failed `migrate` or a database error is no longer called a refusal; both stay process code 1.
  - `Option` (`EMAIL`, `ROLE`) is used by `CommandChecks.options`, which returns `Map<Option, String>`.
  - Removed: `CommandLine.USAGE_ERROR`, the alias in `CreateAdminCommand`, `AdminBootstrap.CREATED/REFUSED`, `LinkCommand.ISSUED/REFUSED`, `CommandChecks.EMAIL`, `CreateUserCommand.ROLE`, and the literals in `MigrateCommand`.
  - Tests: the assertions were rewritten mechanically to `ExitCode`. New `ExitCodeTest` pins the process codes; `CommandChecksTest` adds a case for an option the command does not accept.
  - The process exit codes are unchanged.
  - `REPOSITORY_GUIDELINES.md`: the enum rule now names CLI exit codes and options.

### F4 — Duration settings are parsed by two copies with different rules

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationSettings.java:137-160 vs config/LlmProviderProperties.java:175-195
- **Detail**:
  - `ActivationSettings.linkTtl` and `trimmed` copy `LlmProviderProperties.timeout` and `trimmed`: environment value → `DurationStyle.detectAndParse` → a `problems` list.
  - The rules differ. The new copy refuses a bare number. `LLM_TIMEOUT=120` is still accepted as 120 ms, the production pitfall recorded in deploy-plan.md (F-02, "Rule for variables").
  - lessons.md says to find abstractions while the code is small.
- **Fix A ⭐ Recommended**: Extract one small parser, e.g. `config/DurationSetting.parse(name, text, min, max, problems)`, which requires a unit. Use it for `ACTIVATION_LINK_TTL` and `LLM_TIMEOUT` and delete both `trimmed` copies.
  - Strength: one rule for every duration variable; the 120 ms pitfall is closed in code, not only in the docs.
  - Tradeoff: a behaviour change outside S-07. An environment with a bare `LLM_TIMEOUT` would fail `migrate` and the start (the deploy stops, the old version keeps running). Check production's value first (deploy-plan says `300s`; PR envs delete it).
  - Confidence: MED — production's current `LLM_TIMEOUT` was not checked in this review.
  - Blind spot: other Java duration settings, if any appear later.
- **Fix B**: A follow-up change (duration settings).
  - Strength: no LLM configuration change rides on the S-07 merge.
  - Tradeoff: two copies with different rules stay; the next duration setting picks one of them.
  - Confidence: HIGH.
  - Blind spot: None significant.
- **Decision**: DEFERRED via Fix B — queued as a separate change in `follow-ups/review-fixes.md`.

### F5 — The live token stays in the address bar and browser history

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/views/ActivateView.vue:34
- **Detail**:
  - The token is read from `route.hash` and never removed.
  - While the form is open, or if the person abandons the page, `/activate#<token>`, still valid up to 12 h, stays in the address bar and in the browser's global history. On a shared computer, so does the history sync.
  - Only a successful activation (`router.replace` to `/login`) overwrites that entry.
- **Fix**: After reading the token, remove the fragment (`router.replace({ hash: '' })`) and keep the token only in memory. Add a test. A reload then needs the original link, which still works.
- **Decision**: FIXED.
  - Code: `ActivateView` reads the token once, then `router.replace({ path, query })` drops the fragment.
  - Test: `ActivateView.test.ts` "drops the token from the address once it has read it". It fails without the fix and passes with it.
  - Limit: this cleans the address bar and the tab's back/forward entry. A browser's global history may still have recorded the visit (browser-dependent), and the code comment says so.

### F6 — Concurrent create-user for one e-mail prints a raw database error

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-api/src/main/java/pl/szczurradar/api/auth/activation/ActivationLinks.java:77-81, cli/LinkCommand.java:116-118
- **Detail**:
  - Two `create-user` runs for the same e-mail both pass `existsByEmail`.
  - The loser gets a `DataIntegrityViolationException` from `uq_users_email`, and stderr shows `create-user failed: could not execute statement [ERROR: duplicate key …]`.
  - The exit code (1) is correct, and no token has been written. Only the message is wrong, and with one operator this is unlikely.
- **Fix**: In `createUser`, map the `uq_users_email` violation to `Refused(EMAIL_TAKEN)`, so the hint "use reset-password" appears.
- **Decision**: FIXED.
  - Code: `createUser` catches `DataIntegrityViolationException` outside its (rolled-back) transaction. It returns `Refused(EMAIL_TAKEN)` when the e-mail now exists and rethrows anything else.
  - Test: `ActivationLinksIT.anEmailTakenWhileCreatingIsRefused` reproduces the race deterministically: an uncommitted insert holds the unique index while `createUser` waits on it. It fails without the fix with the raw exception and passes with it.

### F7 — Progress row 2.6 is checked without its N/A annotation

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/cli-user-onboarding/plan.md:589
- **Detail**:
  - The success criterion (plan.md:287) asks for the row to be closed "jako N/A z adnotacją" when S-05 is not in `main`.
  - The row reads `- [x] 2.6 … — 5f374c1`; the N/A reason is only in the commit message.
  - Anyone reading Progress or `/10x-status`, including S-05 when it merges second, would think the system-account refusal exists.
- **Fix**: Change the row to `- [x] 2.6 Warunkowo: odmowa dla konta systemowego S-05 — N/A: S-05 nie jest w main, odmowę dopisuje S-05 (5f374c1)`.
- **Decision**: FIXED. The row now carries the N/A annotation and keeps the SHA last (` — 5f374c1`), as the Progress format expects.

### F8 — The switch from "checking" to the form is not announced

- **Severity**: 🔍 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: szczur-radar-web/src/views/ActivateView.vue:115
- **Detail**:
  - "Sprawdzanie linku…" is not a live region, and focus stays where it was when the form appears.
  - A screen-reader user gets no signal that the form ("Ustaw hasło dla …") is ready.
  - The error states are fine (`role="alert"` via `StatusMessage`), and the section has `aria-busy`.
- **Fix**: When the phase becomes `form`, move focus to the first password field (`nextTick` + a ref). Add a test.
- **Decision**: FIXED.
  - Code: `check()` focuses "Nowe hasło" (`nextTick` + ref) once the lookup succeeds, outside the lookup's `try`.
  - Test: `ActivateView.test.ts` "moves the focus to the new password once the form shows". It fails without the fix and passes with it.
  - The screenshots in `ui/` were regenerated (all 32, both themes, 1280 and 375 px, no horizontal scroll at 375 px). The default state now shows the focused field, and the focus-visible shot needs two `Tab`s; `ui/README.md` is updated.
