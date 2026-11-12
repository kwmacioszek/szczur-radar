/*
 * Mock /api/* for screenshots of /admin, a run (/admin/runs/:id), the feed (/), a post (/posts/:id) and /activate
 * without a running API (playwright-cli run-code), kept for /10x-ui changes. Another mode = run a copy of this file
 * with MODE, NOTIFICATIONS, AUTH, LOGIN, START, ACTIVATION, FRESHNESS, MISSING or QUALITY replaced.
 * Ids are UUIDs like the API's (a malformed id in a path is 400 invalid_request, an unknown one 404): the 5th group
 * is a kind and a number, 00000000-0000-4000-8000-<kind 4 digits><number 8 digits> — kind 0001 a run, 0002 a
 * notification, 0003 a failed post, 0004 a feed post (number from 0), 0005 an account. Below, <run N> is
 * 00000000-0000-4000-8000-0001 followed by N as 8 digits (run 1 = 00000000-0000-4000-8000-000100000001).
 * MODE: 'data'         runs + failed posts; the feed has 120 posts (3 pages of 50), each with its page and raw data,
 *       'loading'      admin calls (the notification bell's too), /api/posts and /api/posts/:id (with /raw) hang 60 s,
 *       'error'        admin calls (the bell's too), /api/posts and /api/posts/:id (with /raw) answer 500,
 *       'empty'        no runs, no failed posts, the feed's total is 0, every post id and run id is 404,
 *       'loading-next' feed page 0 answers, later pages hang 60 s (admin as in 'data'),
 *       'error-next'   feed page 0 answers, later pages answer 500 (admin as in 'data'),
 *       'raw-loading'  /api/posts/:id/raw hangs 60 s (the rest as in 'data'),
 *       'raw-error'    /api/posts/:id/raw answers 500 (the rest as in 'data').
 * FRESHNESS (independent of MODE; another variant = a copy of this file with FRESHNESS replaced), the label above the
 * feed — /api/freshness answers like pipeline/api/FreshnessController.java:
 *       'date'  { sources: [wykop, reddit] }, both 2026-10-13T03:12:00Z (05:12 in Warsaw), the default,
 *       'apart' both sources, Reddit older: 2026-10-11T03:12:00Z (a night it failed or gave no post since),
 *       'wykop' Wykop alone: the one source with an update, as on production until S-28,
 *       'none'  { sources: [] }: no update yet,
 *       'error' 500; every other route answers as MODE says.
 * MISSING (the "Dni bez danych" section of /admin; MODE 'loading' and 'error' apply first), like
 * /api/admin/pipeline-runs/missing-days of pipeline/api/PipelineRunController.java, relative to today in Warsaw (D):
 *       'gaps'  the window D-15…D-2 with Wykop missing D-9…D-7 and D-4, Reddit D-8, D-5 and D-4, the default,
 *       'none'  the window and no day,
 *       'error' 500.
 * QUALITY (the "Jakość ocen (7 dni)" section of /admin; MODE 'loading' and 'error' apply first), like
 * /api/admin/assessment-quality of posts/api/AssessmentQualityController.java, the window D-6…D:
 *       'data'  Wykop ok (2.6 % of 3134 attempts against 5 %), Reddit above its threshold (27.4 % of 598 against 25 %),
 *               the default,
 *       'few'   Wykop ok, Reddit with 46 attempts: fewer than minAttempts, the threshold not checked,
 *       'none'  the window and no source (no attempt in it),
 *       'error' 500.
 * Runs (/api/admin/pipeline-runs?page&size, 32 in all: "Pokaż starsze" has a second page of 20; page 0…21474836,
 * size 1…100, else 400 invalid_request) answer like
 * pipeline/api/PipelineRunController.java, the newest first; a run that needs attention has its reason codes in
 * attentionReasons, along the API's rule (pipeline/run/PipelineRun.attentionReasons). Every range run has
 * sourceResults (each source's status, fetched, skipped, error) and the counters sourcesFailed / sourcesEmpty;
 * unless said otherwise its one source, Wykop, succeeded:
 *       run 1, running: an admin's range, assessing now (19 of 41, 4 in flight, last activity 20 s ago),
 *       run 2, source-empty: an admin's range of Wykop and Reddit, partial: Reddit answered without a post
 *                  (fetched 0; ['source_empty'], "reddit gave no post to store (fetched 0, skipped 0)"); Wykop's
 *                  posts assessed,
 *       run 3, source-failed: the cron's night of Wykop and Reddit, partial: Reddit threw after its attempts
 *                  (['source_failed'], its row's error "RedditApiException: … answered HTTP 503 …", the run's
 *                  "reddit failed: …"); Wykop's posts stored and assessed,
 *       run 4, skipped: an admin's range, succeeded with 3 skipped elements (needs attention: a warning, ['skipped']),
 *       run 5, deploy: the cron's night, interrupted by a new deployment (['interrupted']: a warning), its assess step
 *                  interrupted,
 *       run 6, lost: an admin's range, interrupted `lost`: its instance was lost mid-assess, and the cron's start request
 *                  at 00:30 — the first start request after PIPELINE_STALL_TIMEOUT (39 min) without activity —
 *                  cleaned it up, its assess step interrupted,
 *       run 7, empty: an admin's range of today just after midnight, failed: its source gave no post (fetched 0, its
 *                  fetch step failed, "NoPostsFetchedException: wykop gave no post to store (fetched 0, skipped 0)";
 *                  Wykop's result empty),
 *       run 8, stalled: the cron's night, failed: "no progress for 39m 00s: the run was stopped" (the watchdog),
 *       run 9, dead: the cron's night, partial with 1 dead post (needs attention: ['dead_posts']),
 *       run 10, ok: the cron's night, succeeded; runs 11–32 older nights, some partial with failed posts only.
 *   /api/admin/pipeline-runs/<id> is that run (e.g. /admin/runs/00000000-0000-4000-8000-000100000001, the running
 *   one); a malformed id is 400 invalid_request, any other id 404 {"detail":"not_found"}.
 * POST /api/admin/pipeline-runs answers like PipelineRunController.start by START (the request is validated first:
 *   a range from/to up to today in Warsaw, at most 7 days, optional sources, or 1–100 postIds, never both; else 400
 *   invalid_request): 'accepted' pretends the pipeline has no active run, although run 1 on the list is running:
 *   202 with the new queued runs — a range run, after a retry run while a post is left to retry; postIds → a posts
 *   run, but a dead post among them is 400 post_dead (the new runs head the list and stay queued until the mock is
 *   installed again). A second start (a range or postIds) while a run of an earlier start is queued is refused like
 *   the API refuses it: 409 pipeline_busy, no new run. 'busy' 409 pipeline_busy at once, 'not_configured' 503
 *   source_not_configured (a range only; postIds as in 'accepted').
 * NOTIFICATIONS (the bell; MODE 'loading' and 'error' apply first), like notifications/api/NotificationController.java:
 *       'unread' 5 unread (notifications 1–5, about runs 3, 4, 5, 7, 8) and 1 read (6, about run 9), the
 *                default; a read one stays read until the mock is installed again
 *                (POST /api/admin/notifications/<notification N>/read and …/notifications/read → 204; a malformed id 400),
 *       'none'   no notifications (unread 0),
 *       'error'  500.
 * Failed posts (/api/admin/posts/failed?page&size, like posts/api/FailedPostController.java; failed posts 1 and 2
 * failed, 3 dead, 4 on hold, each attempt with its violations). POST /api/admin/posts/restore {postIds} answers like posts/api/DeadPostController.java: each named post that is
 * still dead becomes stored with no attempts and leaves the list, {restored: N}; 1–100 post UUIDs or 400 invalid_request.
 * A restored post stays restored until the mock is installed again; MODE 'loading' and 'error' apply first.
 * /api/posts answers like posts/api/PostController.java: page, size, total; a page past the end (e.g. /?page=9)
 * is 200 with items [] and the real total. /api/posts/<feed post N> (N from 0 to 119, e.g. post 5 =
 * 00000000-0000-4000-8000-000400000005) is the feed's post N, a malformed id is 400 invalid_request, any other id
 * 404 {"detail":"post_not_found"}; its /raw is indented JSON text shaped like a Wykop entry, and for a Reddit post
 * like an element of Arctic Shift's `data` (cut to its main fields). Each post has source and channel (without its prefix): every third one (n % 3 === 2)
 * is Reddit's, channel stocks, ValueInvesting or investing in turn, with a https://www.reddit.com/r/<channel>/… address;
 * the rest are Wykop's, channel gielda.
 * AUTH, the session at the start, kept like the API: 'admin' (/api/auth/me → the admin), 'user' (role user: every
 *   /api/admin/ route is 403 forbidden) or 'anonymous' (no session: every route but login, logout, activation and
 *   health is 401 not_authenticated), for the public /activate and /login. The session then follows the calls.
 * LOGIN, POST /api/auth/login {email, password} (like auth/LoginRequest.java: both required — a missing or null one,
 *   an object or an array is 400 invalid_request; a number is read as its text, like Jackson, and an empty one is
 *   no bad request: it is 401 invalid_credentials in every mode but 'limited', as no account matches it): 'ok' 200 with the account and
 *   a session (admin@szczur-radar.pl is the admin, any other e-mail a user), 'invalid' 401 invalid_credentials,
 *   'limited' 429 too_many_attempts with Retry-After. POST /api/auth/logout → 204 and no session.
 * START: 'accepted', 'busy' or 'not_configured', see POST /api/admin/pipeline-runs above.
 * ACTIVATION (/activate#<any token>; the token is not checked): 'valid' (lookup → e-mail, activation → 204, then
 *   /login with the "password set" notice), 'invalid' (both → 400 activation_link_invalid), 'policy' (activation →
 *   400 password_policy), 'checking' (lookup hangs 60 s), 'sending' (activation hangs 60 s), 'error' (both → 500).
 *   Client-side validation errors need no mode: submit a short or mismatched password.
 * The filter (FR-011a) has the API's meaning: q, kind and direction keep the posts with one target meeting every
 * given condition — its quote contains q (Polish lower case, q stripped of the contract §10 white space, blank = no
 * condition), its kind, its stance's direction (an empty kind or direction = no condition); total counts them; an
 * unknown kind or direction, or a q over 100 characters or with a NUL, is 400 invalid_request.
 * E.g. /?q=orlen&direction=bullish, /?kind=macro_topic, and an empty filter result: /?q=zzz. In 'empty' mode total is
 * 0 with or without a filter.
 * Only paths starting with /api/ are mocked — a '**\/api/**' glob would also catch Vite's /src/api/*.ts modules.
 * failureClass values are the API's FailureClass (agents/assessor/validation/FailureClass.java), lower case.
 * With AUTH = 'admin' the header shows "Panel admina" and the notification bell on every view.
 * /kitchen-sink needs no mock: dev only, no session, fixed data.
 *
 *   cd szczur-radar-web && npx vite --port 5173 --strictPort &
 *   cd <repo root>   # playwright-cli open reads .playwright/cli.config.json (Chromium) from the cwd; the file is
 *                    # not in git, so from a git worktree add --config=<main checkout>/.playwright/cli.config.json
 *   playwright-cli open && playwright-cli resize 1280 900
 *   playwright-cli run-code --filename=szczur-radar-web/dev/mock-api.js
 *   playwright-cli goto http://localhost:5173/admin   # the feed: http://localhost:5173/ (?page=N, from 0);
 *                                                    # a run: http://localhost:5173/admin/runs/00000000-0000-4000-8000-000100000001;
 *                                                    # a post: http://localhost:5173/posts/00000000-0000-4000-8000-000400000005
 *                                                    # (a Reddit one: post 2, …000400000002);
 *                                                    # /activate#token with AUTH = 'anonymous'
 *   playwright-cli set-color-scheme light|dark && playwright-cli screenshot --full-page --filename=<png>
 */
async page => {
  // Runs as pipeline/api/PipelineRunController.java answers them: range instants (Warsaw days), steps with their
  // statuses, progress counters. Built per request, so the running run's times stay "a moment ago".
  const MIN = 60000
  // Ids are UUIDs like the API's, readable by their kind (the 5th group's first 4 digits) and number: 1 a run,
  // 2 a notification, 3 a failed post, 4 a feed post, 5 an account.
  const uuid = (kind, n) => `00000000-0000-4000-8000-${String(kind).padStart(4, '0')}${String(n).padStart(8, '0')}`
  const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
  // The runs by key, in the order of their numbers (run 1 is the first key): the named ones, then 22 older nights.
  const olderDay = (n) => new Date(Date.UTC(2026, 9, 11 - n)).toISOString().slice(0, 10)
  const RUN_KEYS = ['running', 'source-empty', 'source-failed', 'skipped', 'deploy', 'lost', 'empty', 'stalled', 'dead',
    'ok', ...Array.from({ length: 22 }, (_, n) => olderDay(n))]
  const runId = (key) => uuid(1, RUN_KEYS.indexOf(key) + 1)
  const ago = (ms) => new Date(Date.now() - ms).toISOString()
  const step = (name, status, startedAt, finishedAt, itemsIn, itemsOut, error = null) =>
    ({ step: name, status, startedAt, finishedAt, itemsIn, itemsOut, error })
  // A night's steps from 00:30 UTC of `day` (YYYY-MM-DD) with `posts` to assess; `assess` ends with `last`.
  const night = (day, posts, last = 'succeeded', error = null) => [
    step('fetch', 'succeeded', `${day}T00:30:02Z`, `${day}T00:30:40Z`, null, 470),
    step('store', 'succeeded', `${day}T00:30:40Z`, `${day}T00:30:44Z`, 470, posts),
    step('select', 'succeeded', `${day}T00:30:44Z`, `${day}T00:30:44Z`, posts, posts),
    step('assess', last, `${day}T00:30:44Z`, `${day}T00:52:10Z`, posts, last === 'succeeded' ? posts : null, error),
    ...(last === 'succeeded' ? [step('summarize', 'succeeded', `${day}T00:52:10Z`, `${day}T00:52:10Z`, null, null)] : []),
  ]
  // One source's result of a range run, as PipelineRunController's SourceResultView: `succeeded` (gave a post),
  // `empty` (no post) or `failed` (threw; only then an error).
  const sourceResult = (source, status, fetched, skipped, finishedAt, error = null) =>
    ({ source, status, fetched, skipped, error, finishedAt })
  // The cron's run of the night of `day`: D-2…D-1 in Warsaw (22:00 UTC of D-3 to 21:59:59 UTC of D-1, summer time).
  // Without sourceResults in `o`, its one source (Wykop) gave the run's fetched and skipped: succeeded, or empty
  // when every fetched element was skipped.
  const nightly = (key, day, o) => {
    const d = new Date(`${day}T00:00:00Z`)
    const back = (days) => new Date(d.getTime() - days * 24 * 60 * MIN).toISOString().slice(0, 10)
    const r = {
      id: runId(key), kind: 'range', status: 'succeeded', interruptionCause: null, needsAttention: false, attentionReasons: [],
      scheduled: true,
      rangeFrom: `${back(3)}T22:00:00Z`, rangeTo: `${back(1)}T21:59:59Z`, sources: ['wykop'], requestedPostIds: null,
      createdAt: `${day}T00:30:00Z`, startedAt: `${day}T00:30:01Z`, finishedAt: `${day}T00:52:10Z`,
      lastActivityAt: `${day}T00:52:10Z`, fetched: 470, newPosts: 400, assessedOk: 400, assessedFailed: 0,
      processingErrors: 0, dead: 0, skipped: 0, sourcesFailed: 0, sourcesEmpty: 0, llmRetries: 9, flexFallbacks: 2,
      inFlight: 0, error: null, steps: night(day, 400), ...o,
    }
    if (o?.sourceResults) return r
    const fetchEnd = r.steps.find((s) => s.step === 'fetch')?.finishedAt ?? r.startedAt
    const status = r.fetched > r.skipped ? 'succeeded' : 'empty'
    return { ...r, sourcesEmpty: status === 'empty' ? 1 : 0,
      sourceResults: [sourceResult('wykop', status, r.fetched, r.skipped, fetchEnd)] }
  }
  const allRuns = () => [
    // An admin's range run, assessing now: 19 of 41 done, 4 in flight.
    {
      ...nightly('running', '2026-10-15', { scheduled: false, status: 'running', fetched: 270, newPosts: 41,
        assessedOk: 17, assessedFailed: 2, llmRetries: 3, flexFallbacks: 1, inFlight: 4 }),
      createdAt: ago(12 * MIN), startedAt: ago(12 * MIN), finishedAt: null, lastActivityAt: ago(20 * 1000),
      steps: [
        step('fetch', 'succeeded', ago(12 * MIN), ago(11.5 * MIN), null, 270),
        step('store', 'succeeded', ago(11.5 * MIN), ago(11.4 * MIN), 270, 41),
        step('select', 'succeeded', ago(11.4 * MIN), ago(11.4 * MIN), 41, 41),
        step('assess', 'running', ago(11.4 * MIN), null, 41, null),
      ],
      sourceResults: [sourceResult('wykop', 'succeeded', 270, 0, ago(11.5 * MIN))],
    },
    // An admin's range of Wykop and Reddit: Reddit answered, but without a post; Wykop's posts were assessed.
    nightly('source-empty', '2026-10-16', { scheduled: false, status: 'partial', needsAttention: true,
      attentionReasons: ['source_empty'], sources: ['wykop', 'reddit'], sourcesEmpty: 1,
      rangeFrom: '2026-10-14T22:00:00Z', rangeTo: '2026-10-15T21:59:59Z', createdAt: '2026-10-16T08:00:00Z',
      startedAt: '2026-10-16T08:00:01Z', finishedAt: '2026-10-16T08:21:40Z', lastActivityAt: '2026-10-16T08:21:40Z',
      error: 'reddit gave no post to store (fetched 0, skipped 0)',
      steps: [
        step('fetch', 'succeeded', '2026-10-16T08:00:02Z', '2026-10-16T08:00:51Z', null, 470),
        step('store', 'succeeded', '2026-10-16T08:00:51Z', '2026-10-16T08:00:55Z', 470, 400),
        step('select', 'succeeded', '2026-10-16T08:00:55Z', '2026-10-16T08:00:55Z', 400, 400),
        step('assess', 'succeeded', '2026-10-16T08:00:55Z', '2026-10-16T08:21:40Z', 400, 400),
        step('summarize', 'succeeded', '2026-10-16T08:21:40Z', '2026-10-16T08:21:40Z', null, null),
      ],
      sourceResults: [
        sourceResult('wykop', 'succeeded', 470, 0, '2026-10-16T08:00:40Z'),
        sourceResult('reddit', 'empty', 0, 0, '2026-10-16T08:00:51Z'),
      ] }),
    // The cron's night of Wykop and Reddit: Reddit threw after its attempts; Wykop's posts were stored and assessed.
    nightly('source-failed', '2026-10-16', { status: 'partial', needsAttention: true,
      attentionReasons: ['source_failed'], sources: ['wykop', 'reddit'], sourcesFailed: 1,
      error: 'reddit failed: RedditApiException: Reddit GET /r/inwestowanie/new answered HTTP 503: upstream'
        + ' connect error after 3 attempts',
      sourceResults: [
        sourceResult('wykop', 'succeeded', 470, 0, '2026-10-16T00:30:40Z'),
        sourceResult('reddit', 'failed', 0, 0, '2026-10-16T00:30:39Z', 'RedditApiException: Reddit GET'
          + ' /r/inwestowanie/new answered HTTP 503: upstream connect error after 3 attempts'),
      ] }),
    // An admin's range whose source skipped 3 elements (unexpected shape or not mappable): succeeded, a warning.
    nightly('skipped', '2026-10-15', { scheduled: false, needsAttention: true, attentionReasons: ['skipped'],
      skipped: 3, newPosts: 397, assessedOk: 397, createdAt: '2026-10-15T07:00:00Z', startedAt: '2026-10-15T07:00:01Z',
      finishedAt: '2026-10-15T07:21:30Z', lastActivityAt: '2026-10-15T07:21:30Z', steps: [
        step('fetch', 'succeeded', '2026-10-15T07:00:02Z', '2026-10-15T07:00:40Z', null, 470),
        step('store', 'succeeded', '2026-10-15T07:00:40Z', '2026-10-15T07:00:44Z', 467, 397),
        step('select', 'succeeded', '2026-10-15T07:00:44Z', '2026-10-15T07:00:44Z', 397, 397),
        step('assess', 'succeeded', '2026-10-15T07:00:44Z', '2026-10-15T07:21:30Z', 397, 397),
        step('summarize', 'succeeded', '2026-10-15T07:21:30Z', '2026-10-15T07:21:30Z', null, null),
      ] }),
    nightly('deploy', '2026-10-15', { status: 'interrupted', interruptionCause: 'deploy', needsAttention: true,
      attentionReasons: ['interrupted'], finishedAt: '2026-10-15T00:41:10Z', lastActivityAt: '2026-10-15T00:41:02Z',
      assessedOk: 212, assessedFailed: 3,
      error: 'interrupted: a new deployment took over',
      steps: night('2026-10-15', 400).slice(0, 3).concat(
        step('assess', 'interrupted', '2026-10-15T00:30:44Z', '2026-10-15T00:41:10Z', 400, null)) }),
    // An admin's range of 14.10 (Warsaw) whose instance was lost while assessing; the cron's start request at 00:30
    // found it without activity for over 39 min and interrupted it `lost`.
    nightly('lost', '2026-10-14', { scheduled: false, status: 'interrupted', interruptionCause: 'lost',
      needsAttention: true, attentionReasons: ['interrupted'], rangeFrom: '2026-10-13T22:00:00Z',
      rangeTo: '2026-10-14T21:59:59Z', createdAt: '2026-10-14T23:00:00Z', startedAt: '2026-10-14T23:00:01Z',
      finishedAt: '2026-10-15T00:30:00Z',
      lastActivityAt: '2026-10-14T23:08:30Z', assessedOk: 97, assessedFailed: 1,
      error: 'interrupted: the instance running it was lost without closing it',
      steps: [
        step('fetch', 'succeeded', '2026-10-14T23:00:02Z', '2026-10-14T23:00:40Z', null, 470),
        step('store', 'succeeded', '2026-10-14T23:00:40Z', '2026-10-14T23:00:44Z', 470, 400),
        step('select', 'succeeded', '2026-10-14T23:00:44Z', '2026-10-14T23:00:44Z', 400, 400),
        step('assess', 'interrupted', '2026-10-14T23:00:44Z', '2026-10-15T00:30:00Z', 400, null),
      ] }),
    // An admin's range of 15.10 (Warsaw) started 10 minutes after its midnight: the source gave no post.
    nightly('empty', '2026-10-15', { scheduled: false, status: 'failed', needsAttention: true,
      attentionReasons: ['failed'],
      rangeFrom: '2026-10-14T22:00:00Z', rangeTo: '2026-10-15T21:59:59Z', createdAt: '2026-10-14T22:10:00Z',
      startedAt: '2026-10-14T22:10:01Z', finishedAt: '2026-10-14T22:10:03Z', lastActivityAt: '2026-10-14T22:10:03Z',
      fetched: 0, skipped: 0, newPosts: 0, assessedOk: 0, llmRetries: 0, flexFallbacks: 0,
      error: 'NoPostsFetchedException: wykop gave no post to store (fetched 0, skipped 0)',
      steps: [step('fetch', 'failed', '2026-10-14T22:10:01Z', '2026-10-14T22:10:03Z', null, null,
        'wykop gave no post to store (fetched 0, skipped 0)')] }),
    nightly('stalled', '2026-10-14', { status: 'failed', needsAttention: true, attentionReasons: ['failed'],
      finishedAt: '2026-10-14T01:20:31Z', lastActivityAt: '2026-10-14T00:41:31Z', assessedOk: 160, assessedFailed: 1,
      error: 'no progress for 39m 00s: the run was stopped',
      steps: night('2026-10-14', 400, 'failed', 'no progress for 39m 00s: the run was stopped') }),
    nightly('dead', '2026-10-13', { status: 'partial', needsAttention: true, attentionReasons: ['dead_posts'],
      assessedOk: 462, assessedFailed: 7, dead: 1, newPosts: 469, steps: night('2026-10-13', 469) }),
    nightly('ok', '2026-10-12'),
    // Older nights: 22 more, so the list has a second page ("Pokaż starsze"); every third one partial without a
    // dead post (nothing to attend to).
    ...Array.from({ length: 22 }, (_, n) => {
      const day = olderDay(n)
      return nightly(day, day, n % 3 === 0 ? { status: 'partial', assessedOk: 393, assessedFailed: 7 } : {})
    }),
  ]
  // Notifications as notifications/api/NotificationController.java answers them, about the runs above; reading one
  // (or all) is kept until the mock is installed again.
  const readIds = new Set()
  const notifiedRun = (r) => ({ id: r.id, kind: r.kind, status: r.status, interruptionCause: r.interruptionCause,
    finishedAt: r.finishedAt, assessedFailed: r.assessedFailed, dead: r.dead, processingErrors: r.processingErrors,
    skipped: r.skipped, error: r.error })
  const allNotifications = () => {
    const byId = Object.fromEntries(allRuns().map((r) => [r.id, r]))
    const about = (key) => notifiedRun(byId[runId(key)])
    return [
      { id: uuid(2, 1), createdAt: '2026-10-16T00:52:11Z', readAt: null, run: about('source-failed') },
      { id: uuid(2, 2), createdAt: '2026-10-15T07:21:31Z', readAt: null, run: about('skipped') },
      { id: uuid(2, 3), createdAt: '2026-10-15T00:41:12Z', readAt: null, run: about('deploy') },
      { id: uuid(2, 4), createdAt: '2026-10-14T22:10:04Z', readAt: null, run: about('empty') },
      { id: uuid(2, 5), createdAt: '2026-10-14T01:20:31Z', readAt: null, run: about('stalled') },
      { id: uuid(2, 6), createdAt: '2026-10-13T00:52:11Z', readAt: '2026-10-13T06:10:00Z', run: about('dead') },
    ].map((n) => ({ kind: 'run_attention', ...n, readAt: n.readAt ?? (readIds.has(n.id) ? new Date().toISOString() : null) }))
  }
  // Failed posts as posts/api/FailedPostController.java answers them, newest first (ids 1–4): p1 failed, p2 failed with a single
  // provider outage counted as a technical error (both reasons), p3 dead, p4 stored on hold (three technical errors
  // in a row, no stored attempt; its long error is cut in the row, whole in its title).
  const violation = (failureClass, rule, path, message) => ({ failureClass, rule, path, message })
  const noTechnical = { technicalFailures: 0, lastTechnicalError: null, onHold: false }
  const failed = [
    { id: uuid(3, 1), itemType: 'post', publishedAt: '2026-10-08T12:00:00Z', title: null, body: 'CDR po wynikach -8% na otwarciu, ktoś jeszcze trzyma? Ja dobieram, bo wycena po spadku wygląda rozsądnie, a guidance na Q4 nie zmieniło się mimo opóźnienia premiery.', webUrl: 'https://wykop.pl/wpis/1', stage: 'failed', assessmentAttempts: 1, lastAttempt: { failureClass: 'schema_violation', violations: [violation('schema_violation', 'schema:required', '$.claims[0]', "required property 'evidence' not found")], assessedAt: '2026-10-09T05:02:00Z', provider: 'openrouter', model: 'luna' }, ...noTechnical },
    { id: uuid(3, 2), itemType: 'link', publishedAt: '2026-10-08T09:00:00Z', title: 'KNF ostrzega przed nowym schematem piramidy finansowej', body: null, webUrl: 'https://wykop.pl/link/2', stage: 'failed', assessmentAttempts: 2, lastAttempt: { failureClass: 'provider_error', violations: [violation('provider_error', 'call', '$', 'openrouter: HTTP 503 Service Unavailable, failed after 3 attempts')], assessedAt: '2026-10-09T05:02:00Z', provider: 'openrouter', model: 'luna' }, technicalFailures: 1, lastTechnicalError: 'LlmUnavailableException: openrouter: HTTP 503 Service Unavailable, failed after 3 attempts', onHold: false },
    { id: uuid(3, 3), itemType: 'post', publishedAt: '2026-10-07T18:00:00Z', title: null, body: 'Kupiłem PKO, zobaczymy', webUrl: 'https://wykop.pl/wpis/3', stage: 'dead', assessmentAttempts: 3, lastAttempt: { failureClass: 'fabricated_evidence', violations: [violation('fabricated_evidence', 'D6', '$.claims[0].evidence.quote', 'quote is not a fragment of item.body')], assessedAt: '2026-10-09T05:02:00Z', provider: 'openrouter', model: 'luna' }, ...noTechnical },
    { id: uuid(3, 4), itemType: 'comment', publishedAt: '2026-10-07T09:00:00Z', title: null, body: 'Ten wykres XTB to klasyczna głowa z ramionami, ja bym uważał.', webUrl: 'https://wykop.pl/wpis/4#comment-4', stage: 'stored', assessmentAttempts: 0, lastAttempt: null, technicalFailures: 3, lastTechnicalError: 'IllegalStateException: the parent post of comment 81234567 is missing from the source payload, so the comment cannot be mapped to AssessmentInputV1 without its context', onHold: true },
  ]
  // The feed: post n is variant n % 6 — a long text (cut at 200 characters), a link without a body, a short post,
  // a comment with two targets, a bare URL without spaces (no content: the prefilter's output) and a link with five
  // (a card shows a link's title, so its body is seen only on the post's page). The targets cover four kinds:
  // company, macro_topic (rynek), asset (ropa) and sector (banki).
  const FEED_TOTAL = 120
  const feedTexts = [
    { itemType: 'post', title: null, body: 'CDR po wynikach -8% na otwarciu, ktoś jeszcze trzyma? Ja dobieram, bo wycena po spadku wygląda rozsądnie, a guidance na Q4 nie zmieniło się mimo opóźnienia premiery. Zobaczymy, czy rynek to doceni przed świętami, bo historycznie grudzień był dla nich mocny, a sentyment na forum jest teraz fatalny.' },
    { itemType: 'link', title: 'KNF ostrzega przed nowym schematem piramidy finansowej', body: null },
    { itemType: 'post', title: null, body: 'Kupiłem PKO, zobaczymy, banki i tak zarobią na stopach' },
    { itemType: 'comment', title: null, body: 'Ten wykres XTB to klasyczna głowa z ramionami, ja bym uważał. Allegro też słabo.' },
    { itemType: 'post', title: null, body: 'https://stooq.pl/q/?s=cdr&c=1d&t=l&a=lg&b=0&d=20261012&f=20261012&l=0&z=1&i=1&n=1&r=1' },
    { itemType: 'link', title: 'Orlen ogłasza buyback za 4 mld zł', body: 'Zarząd rekomenduje skup akcji własnych, ropa tanieje.\nDla Orlenu to dobra wiadomość, PKN wyceniam bez zmian, Lotos to już historia, a PGE ma swoje plusy i minusy.\nRynek i tak żyje teraz tylko stopami.' },
  ]
  const target = (id, quote, direction, assertiveness, claimIds = [], kind = 'company') => ({
    id, kind, anchor: { document: 'item', field: 'body', quote },
    stance: { direction, assertiveness, claim_ids: claimIds }, reference: 'explicit',
  })
  const claim = (id, kind, attribution, targetIds, quote, statement, field = 'body') => ({
    id, kind, attribution, target_ids: targetIds, evidence: { document: 'item', field, quote }, statement,
  })
  // Contract V2 §7.2: assertiveness is null for mixed, unclear and not_expressed; a bullish, bearish, neutral or
  // mixed stance rests on 1–4 claims (claim_ids), at least one of them the author's; not_expressed rests on none.
  // Every claim in claim_ids has the target in its target_ids. Quotes are verbatim fragments of the post.
  const feedAssessments = [
    {
      relevance: 'investment_related',
      targets: [target('t1', 'CDR', 'bullish', 'hedged', ['c1', 'c2']), target('t2', 'rynek', 'unclear', null, [], 'macro_topic')],
      claims: [
        claim('c1', 'trade_action', 'author', ['t1'], 'Ja dobieram', 'Autor dokupuje akcje CDR po spadku.'),
        claim('c2', 'investment_thesis', 'author', ['t1'], 'wycena po spadku wygląda rozsądnie', 'Wycena CDR po spadku jest rozsądna.'),
        claim('c3', 'factual_claim', 'author', ['t1'], 'guidance na Q4 nie zmieniło się mimo opóźnienia premiery', 'Prognoza CDR na Q4 nie zmieniła się mimo opóźnienia premiery.'),
      ],
      flags: [],
    },
    {
      relevance: 'investment_related',
      targets: [],
      claims: [claim('c1', 'factual_claim', 'reported', [], 'KNF ostrzega przed nowym schematem piramidy finansowej', 'KNF ostrzega przed nowym schematem piramidy finansowej.', 'title')],
      flags: ['missing_context'],
    },
    {
      relevance: 'investment_related',
      targets: [target('t1', 'PKO', 'bullish', 'unhedged', ['c1']), target('t2', 'banki', 'bullish', 'hedged', ['c2'], 'sector')],
      claims: [
        claim('c1', 'trade_action', 'author', ['t1'], 'Kupiłem PKO', 'Autor kupił akcje PKO.'),
        claim('c2', 'opinion', 'author', ['t2'], 'banki i tak zarobią na stopach', 'Banki i tak zarobią na stopach procentowych.'),
      ],
      flags: ['insufficient_content'],
    },
    {
      relevance: 'investment_related',
      targets: [target('t1', 'XTB', 'bearish', 'hedged', ['c1']), target('t2', 'Allegro', 'bearish', 'unhedged', ['c2'])],
      claims: [
        claim('c1', 'investment_thesis', 'author', ['t1'], 'Ten wykres XTB to klasyczna głowa z ramionami', 'Wykres XTB tworzy formację głowy z ramionami.'),
        claim('c2', 'opinion', 'author', ['t2'], 'Allegro też słabo', 'Allegro radzi sobie słabo.'),
      ],
      flags: ['possible_sarcasm', 'missing_context'],
    },
    null, // the prefilter: no content, decided without the model
    {
      relevance: 'investment_related',
      targets: [
        target('t1', 'Orlen', 'bullish', 'unhedged', ['c1', 'c2']),
        target('t2', 'PKN', 'neutral', 'unhedged', ['c2']),
        target('t3', 'Lotos', 'not_expressed', null),
        target('t4', 'PGE', 'mixed', null, ['c3']),
        target('t5', 'ropa', 'bearish', 'unhedged', ['c5'], 'asset'),
      ],
      claims: [
        claim('c1', 'factual_claim', 'author', ['t1'], 'Orlen ogłasza buyback za 4 mld zł', 'Orlen ogłosił skup akcji własnych za 4 mld zł.', 'title'),
        // One claim about two targets.
        claim('c2', 'investment_thesis', 'author', ['t1', 't2'], 'Dla Orlenu to dobra wiadomość, PKN wyceniam bez zmian', 'Skup akcji to dobra wiadomość dla Orlenu, a wycena PKN się nie zmienia.'),
        claim('c3', 'opinion', 'author', ['t4'], 'PGE ma swoje plusy i minusy', 'PGE ma zarówno zalety, jak i wady.'),
        // A general claim: no target.
        claim('c4', 'opinion', 'author', [], 'Rynek i tak żyje teraz tylko stopami', 'Rynek reaguje teraz wyłącznie na stopy procentowe.'),
        claim('c5', 'factual_claim', 'author', ['t5'], 'ropa tanieje', 'Ropa tanieje.'),
      ],
      flags: [],
    },
  ]
  // Where post n came from: every third is Reddit's, so its variants are a post and a link, what Reddit gives.
  const REDDIT_CHANNELS = ['stocks', 'ValueInvesting', 'investing']
  const feedOrigin = (n) => {
    if (n % 3 !== 2) return { source: 'wykop', channel: 'gielda', webUrl: `https://wykop.pl/wpis/${n}` }
    const channel = REDDIT_CHANNELS[Math.floor(n / 3) % REDDIT_CHANNELS.length]
    return { source: 'reddit', channel, webUrl: `https://www.reddit.com/r/${channel}/comments/${n}/` }
  }
  const feedItem = (n) => {
    const k = n % feedTexts.length
    const assessment = feedAssessments[k]
    return {
      id: uuid(4, n), ...feedOrigin(n), ...feedTexts[k], author: `uzytkownik${n}`,
      publishedAt: new Date(Date.UTC(2026, 9, 12, 20, 0) - n * 17 * 60000).toISOString(),
      assessment: assessment
        ? { output: { modifiers: [], ...assessment }, resultSource: 'model', assessedAt: '2026-10-12T21:00:00Z' }
        : { output: { relevance: 'unclear', modifiers: [], targets: [], claims: [], flags: ['insufficient_content'] }, resultSource: 'prefilter', assessedAt: '2026-10-12T21:00:00Z' },
    }
  }
  // The stored source item of post n, indented like the API's /raw answer: a Wykop entry (src/test/resources/fixtures/
  // wykop/entry.json of the API) for Wykop's post, one element of Arctic Shift's `data` (fixtures/reddit/, cut to its
  // main fields) for Reddit's.
  const feedRaw = (n) => {
    const item = feedItem(n)
    if (item.source === 'reddit') {
      const permalink = `/r/${item.channel}/comments/${n}/`
      return JSON.stringify({
        id: `1x${String(n).padStart(5, '0')}`, name: `t3_1x${String(n).padStart(5, '0')}`, author: item.author,
        subreddit: item.channel, subreddit_name_prefixed: `r/${item.channel}`, title: item.title ?? '',
        selftext: item.body ?? '', permalink, url: `https://www.reddit.com${permalink}`, is_self: true, over_18: false,
        score: 6, num_comments: 10, created_utc: Math.floor(Date.parse(item.publishedAt) / 1000),
      }, null, 2)
    }
    return JSON.stringify({
      id: 81230000 + n, slug: `wpis-${n}`,
      author: { username: item.author, gender: null, company: false, avatar: '<url>', status: 'active', color: 'orange', verified: false },
      created_at: item.publishedAt.slice(0, 19).replace('T', ' '),
      title: item.title, content: item.body,
      media: { photo: null, embed: null, photos: [], survey: null },
      tags: ['gielda', 'inwestycje'], votes: { up: 12, down: 1, users: [] },
      comments: { items: [], count: 3 }, resource: item.itemType === 'link' ? 'link' : 'entry', status: 'visible',
    }, null, 2)
  }
  const KINDS = ['company', 'asset', 'sector', 'macro_topic', 'other', 'unknown']
  const DIRECTIONS = ['bullish', 'bearish', 'neutral', 'mixed', 'not_expressed', 'unclear']
  // One target meets every given condition, as in posts/PostRepository.java (null = no condition).
  const matches = (item, q, kind, direction) => item.assessment.output.targets.some((t) =>
    (q === null || t.anchor.quote.toLocaleLowerCase('pl').includes(q))
    && (kind === null || t.kind === kind)
    && (direction === null || t.stance.direction === direction))
  const MODE = 'data'
  const NOTIFICATIONS = 'unread'
  const AUTH = 'admin'
  const LOGIN = 'ok'
  const START = 'accepted'
  const ACTIVATION = 'valid'
  const FRESHNESS = 'date'
  const MISSING = 'gaps'
  const QUALITY = 'data'
  // The session, as the API keeps it: who is signed in now (AUTH sets the start; login and logout change it).
  const ADMIN_EMAIL = 'admin@szczur-radar.pl'
  const account = (email) => ({ id: uuid(5, email === ADMIN_EMAIL ? 1 : 2), email, role: email === ADMIN_EMAIL ? 'admin' : 'user' })
  let session = AUTH === 'anonymous' ? null : account(AUTH === 'user' ? 'user@szczur-radar.pl' : ADMIN_EMAIL)
  // Days without data, relative to today in Warsaw: D-n as YYYY-MM-DD (counted on UTC midnights, so no clock change
  // shifts a day).
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Warsaw' }).format(new Date())
  const dMinus = (n) => new Date(Date.parse(`${today}T00:00:00Z`) - n * 24 * 60 * MIN).toISOString().slice(0, 10)
  const SOURCES = ['wykop', 'reddit']
  const missingDays = () => ({
    from: dMinus(15), to: dMinus(2),
    days: MISSING === 'none' ? [] : [[9, ['wykop']], [8, SOURCES], [7, ['wykop']], [5, ['reddit']], [4, SOURCES]]
      .map(([n, sources]) => ({ day: dMinus(n), sources })),
  })
  // The quality window by QUALITY: counts per source, the state as the API computes it.
  const WYKOP_QUALITY = { source: 'wykop', attempts: 3134, failed: 82, classes: [
    { failureClass: 'schema_violation', count: 10 }, { failureClass: 'invalid_reference', count: 4 },
    { failureClass: 'fabricated_evidence', count: 62 }, { failureClass: 'domain_invariant_violation', count: 6 },
  ], thresholdPercent: 5, status: 'ok' }
  const QUALITY_SOURCES = {
    data: [WYKOP_QUALITY, { source: 'reddit', attempts: 598, failed: 164, classes: [
      { failureClass: 'provider_error', count: 3 }, { failureClass: 'schema_violation', count: 80 },
      { failureClass: 'fabricated_evidence', count: 72 }, { failureClass: 'domain_invariant_violation', count: 9 },
    ], thresholdPercent: 25, status: 'above_threshold' }],
    few: [WYKOP_QUALITY, { source: 'reddit', attempts: 46, failed: 7, classes: [
      { failureClass: 'schema_violation', count: 4 }, { failureClass: 'fabricated_evidence', count: 3 },
    ], thresholdPercent: 25, status: 'not_enough_attempts' }],
    none: [],
  }
  const assessmentQuality = () => ({ from: dMinus(6), to: today, windowDays: 7, minAttempts: 100,
    sources: QUALITY_SOURCES[QUALITY] })
  // The sources of /api/freshness by FRESHNESS, in the API's order.
  const WYKOP_UPDATE = { source: 'wykop', lastUpdatedAt: '2026-10-13T03:12:00Z' }
  const FRESHNESS_SOURCES = {
    date: [WYKOP_UPDATE, { source: 'reddit', lastUpdatedAt: '2026-10-13T03:12:00Z' }],
    apart: [WYKOP_UPDATE, { source: 'reddit', lastUpdatedAt: '2026-10-11T03:12:00Z' }],
    wykop: [WYKOP_UPDATE],
    none: [],
  }
  const hang = () => new Promise((r) => setTimeout(r, 60000))
  const json = (route, body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  const invalid = (route) => json(route, { detail: 'invalid_request' }, 400)
  // `page` and `size` as the API reads them (PageLimits): whole numbers, page 0…MAX_PAGE, size 1…100, an absent or
  // empty one is the default; null = a 400 invalid_request.
  const pageParams = (url, defaultSize) => {
    const read = (name, fallback) => {
      const raw = url.searchParams.get(name)
      return raw === null || raw === '' ? fallback : /^[+-]?\d+$/.test(raw) ? Number(raw) : NaN
    }
    const pageNumber = read('page', 0)
    const size = read('size', defaultSize)
    if (!(pageNumber >= 0) || pageNumber > 21474836 || !(size >= 1 && size <= 100)) return null
    return { pageNumber, size }
  }
  // Runs started by POST /api/admin/pipeline-runs, newest first; they stay until the mock is installed again.
  const started = []
  // The days of a range as Warsaw calendar days: the instant its first day starts and its last day ends (to - 1 s).
  const warsawOffset = (ms) => Date.parse(new Date(ms).toLocaleString('sv-SE', { timeZone: 'Europe/Warsaw' }).replace(' ', 'T') + 'Z') - ms
  const warsawStart = (day) => {
    const utc = Date.parse(`${day}T00:00:00Z`)
    return utc - warsawOffset(utc - warsawOffset(utc))
  }
  const wholeSecond = (ms) => new Date(ms).toISOString().replace('.000Z', 'Z')
  const queuedRun = (kind, o = {}) => ({
    id: uuid(1, 1000 + started.length), kind, status: 'queued', interruptionCause: null, needsAttention: false,
    attentionReasons: [], scheduled: false, rangeFrom: null, rangeTo: null, sources: [], requestedPostIds: null,
    createdAt: new Date().toISOString(), startedAt: null, finishedAt: null, lastActivityAt: null, fetched: 0,
    newPosts: 0, assessedOk: 0, assessedFailed: 0, processingErrors: 0, dead: 0, skipped: 0, sourcesFailed: 0,
    sourcesEmpty: 0, llmRetries: 0, flexFallbacks: 0, inFlight: 0, error: null, steps: [], sourceResults: [], ...o,
  })
  const runs = () => [...started, ...(MODE === 'empty' ? [] : allRuns())]
  // The request body as PipelineRunController.StartRequest reads and validates it: a range (from, to, optional
  // sources) or 1–100 postIds, never both; null = a 400 invalid_request.
  const isDay = (v) => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) && !Number.isNaN(Date.parse(`${v}T00:00:00Z`))
    && new Date(`${v}T00:00:00Z`).toISOString().startsWith(v)
  const readStart = (body) => {
    if (typeof body !== 'object' || body === null || Array.isArray(body)) return null
    const { from = null, to = null, sources = null, postIds = null } = body
    if ((from !== null && !isDay(from)) || (to !== null && !isDay(to))) return null
    if (sources !== null && (!Array.isArray(sources) || sources.some((s) => !SOURCES.includes(s)))) return null
    if (postIds !== null && (!Array.isArray(postIds) || postIds.some((id) => typeof id !== 'string' || !UUID.test(id)))) return null
    if (postIds !== null) {
      return from === null && to === null && sources === null && postIds.length >= 1 && postIds.length <= 100
        ? { postIds } : null
    }
    if (from === null || to === null || from > to || to > today) return null
    if ((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / (24 * 60 * MIN) >= 7) return null
    return { from, to, sources: sources ?? [] }
  }
  await page.unrouteAll().catch(() => {})
  await page.route((u) => u.pathname.startsWith('/api/'), async (route) => {
    const url = new URL(route.request().url())
    const p = url.pathname
    const method = route.request().method()
    if (p === '/api/auth/login') {
      const body = route.request().postDataJSON()
      // Like LoginRequest (@NotNull String): missing, null or not a scalar is a 400; a number becomes its text.
      const missing = (v) => v === undefined || v === null || typeof v === 'object'
      if (typeof body !== 'object' || body === null || Array.isArray(body) || missing(body.email) || missing(body.password)) {
        return invalid(route)
      }
      if (LOGIN === 'limited') {
        return route.fulfill({ status: 429, contentType: 'application/json', headers: { 'Retry-After': '60' },
          body: JSON.stringify({ detail: 'too_many_attempts' }) })
      }
      const email = String(body.email).trim().toLowerCase()
      // No account has an empty e-mail or an empty password, so the API refuses them in every mode but 'limited'.
      if (LOGIN === 'invalid' || email === '' || String(body.password) === '') {
        return json(route, { detail: 'invalid_credentials' }, 401)
      }
      session = account(email)
      return json(route, session)
    }
    if (p === '/api/auth/logout') {
      session = null
      return route.fulfill({ status: 204 })
    }
    if (p.startsWith('/api/auth/activation')) {
      const lookup = p === '/api/auth/activation/lookup'
      if (ACTIVATION === 'checking' && lookup) await hang()
      if (ACTIVATION === 'sending' && !lookup) await hang()
      if (ACTIVATION === 'error') return json(route, { detail: 'internal_error' }, 500)
      if (ACTIVATION === 'invalid') return json(route, { detail: 'activation_link_invalid' }, 400)
      if (lookup) return json(route, { email: 'jan.kowalski@example.com' })
      if (ACTIVATION === 'policy') return json(route, { detail: 'password_policy' }, 400)
      return route.fulfill({ status: 204 })
    }
    // Like the API (deny by default): every other route needs a session, /api/admin/ an admin's.
    if (!['/api/health', '/api/ready', '/api/deploy-safety'].includes(p)) {
      if (session === null) return json(route, { detail: 'not_authenticated' }, 401)
      if (p.startsWith('/api/admin/') && session.role !== 'admin') return json(route, { detail: 'forbidden' }, 403)
    }
    if (p === '/api/auth/me') return json(route, session)
    if (MODE === 'loading' && p.startsWith('/api/admin')) await hang()
    if (MODE === 'error' && p.startsWith('/api/admin')) return json(route, { detail: 'internal_error' }, 500)
    if (p === '/api/admin/pipeline-runs/sources') return json(route, SOURCES.map((source) => ({ source, configured: true })))
    if (p === '/api/admin/pipeline-runs/missing-days') {
      return MISSING === 'error' ? json(route, { detail: 'internal_error' }, 500) : json(route, missingDays())
    }
    if (p === '/api/admin/assessment-quality' && method === 'GET') {
      return QUALITY === 'error' ? json(route, { detail: 'internal_error' }, 500) : json(route, assessmentQuality())
    }
    if (p === '/api/admin/pipeline-runs' && method === 'GET') {
      const params = pageParams(url, 20)
      if (params === null) return invalid(route)
      const { pageNumber, size } = params
      const all = runs()
      return json(route, { items: all.slice(pageNumber * size, (pageNumber + 1) * size), page: pageNumber, size, total: all.length })
    }
    if (p === '/api/admin/pipeline-runs' && method === 'POST') {
      // Like PipelineRunController.start: the request is validated first, then the service refuses (busy, a dead
      // post, a source not configured) or queues the runs.
      const request = readStart(route.request().postDataJSON())
      if (request === null) return invalid(route)
      if (START === 'busy') return json(route, { detail: 'pipeline_busy' }, 409)
      if (START === 'not_configured' && request.postIds === undefined) return json(route, { detail: 'source_not_configured' }, 503)
      // Like PipelineRunService.queue: a run of an earlier start is still queued, so the pipeline is busy.
      if (started.some((run) => run.status === 'queued')) return json(route, { detail: 'pipeline_busy' }, 409)
      if (request.postIds !== undefined) {
        if (failed.some((post) => post.stage === 'dead' && request.postIds.includes(post.id))) {
          return json(route, { detail: 'post_dead' }, 400)
        }
        started.unshift(queuedRun('posts', { requestedPostIds: request.postIds }))
        return json(route, [started[0]], 202)
      }
      // Leftovers (failed posts, or stored ones not on hold) get a retry run before the range.
      const retry = failed.some((post) => post.stage === 'failed' || (post.stage === 'stored' && !post.onHold))
        ? queuedRun('retry') : null
      if (retry) started.unshift(retry)
      const nextDay = new Date(Date.parse(`${request.to}T00:00:00Z`) + 24 * 60 * MIN).toISOString().slice(0, 10)
      const range = queuedRun('range', { rangeFrom: wholeSecond(warsawStart(request.from)),
        rangeTo: wholeSecond(warsawStart(nextDay) - 1000), sources: request.sources.length === 0 ? SOURCES : request.sources })
      started.unshift(range)
      return json(route, retry ? [retry, range] : [range], 202)
    }
    const runPath = p.match(/^\/api\/admin\/pipeline-runs\/([^/]+)$/)
    if (runPath) {
      const id = decodeURIComponent(runPath[1])
      if (!UUID.test(id)) return invalid(route)
      const found = runs().find((r) => r.id === id)
      return found ? json(route, found) : json(route, { detail: 'not_found' }, 404)
    }
    if (p === '/api/admin/notifications') {
      if (NOTIFICATIONS === 'error') return json(route, { detail: 'internal_error' }, 500)
      const items = NOTIFICATIONS === 'none' ? [] : allNotifications()
      return json(route, { unread: items.filter((n) => !n.readAt).length, items })
    }
    if (p === '/api/admin/notifications/read') {
      allNotifications().forEach((n) => readIds.add(n.id))
      return route.fulfill({ status: 204 })
    }
    const readPath = p.match(/^\/api\/admin\/notifications\/([^/]+)\/read$/)
    if (readPath) {
      const id = decodeURIComponent(readPath[1])
      if (!UUID.test(id)) return invalid(route)
      if (NOTIFICATIONS === 'none' || !allNotifications().some((n) => n.id === id)) return json(route, { detail: 'not_found' }, 404)
      readIds.add(id)
      return route.fulfill({ status: 204 })
    }
    if (p === '/api/admin/posts/failed') {
      const params = pageParams(url, 50)
      if (params === null) return invalid(route)
      const { pageNumber, size } = params
      // Like the API: a stored post is on the list only while it is on hold (a restored one is not).
      const all = MODE === 'empty' ? [] : failed.filter((post) => post.stage !== 'stored' || post.onHold)
      return json(route, { items: all.slice(pageNumber * size, (pageNumber + 1) * size), page: pageNumber, size, total: all.length })
    }
    if (p === '/api/admin/posts/restore' && method === 'POST') {
      // Like posts/api/DeadPostController.java: each named post that is still dead becomes stored with no attempts.
      const ids = route.request().postDataJSON()?.postIds
      if (!Array.isArray(ids) || ids.length === 0 || ids.length > 100
        || ids.some((id) => typeof id !== 'string' || !UUID.test(id))) {
        return invalid(route)
      }
      const dead = MODE === 'empty' ? [] : failed.filter((post) => post.stage === 'dead' && ids.includes(post.id))
      // Its attempts stay, as in the API: lastAttempt is kept.
      dead.forEach((post) => Object.assign(post, { stage: 'stored', assessmentAttempts: 0 }))
      return json(route, { restored: dead.length })
    }
    if (p === '/api/freshness') {
      if (FRESHNESS === 'error') return json(route, { detail: 'internal_error' }, 500)
      return json(route, { sources: FRESHNESS_SOURCES[FRESHNESS] })
    }
    if (p === '/api/posts') {
      const params = pageParams(url, 50)
      // Like the API (PageLimits.MAX_PAGE): a page above it is a 400, never a page past the end.
      if (params === null) return invalid(route)
      const { pageNumber, size } = params
      const later = pageNumber > 0
      if (MODE === 'loading' || (MODE === 'loading-next' && later)) await hang()
      if (MODE === 'error' || (MODE === 'error-next' && later)) return json(route, { detail: 'internal_error' }, 500)
      const rawQ = url.searchParams.get('q')
      // Like the API: an empty kind or direction is no condition; q loses the contract §10 white space at both ends.
      const kind = url.searchParams.get('kind') || null
      const direction = url.searchParams.get('direction') || null
      if ((kind !== null && !KINDS.includes(kind)) || (direction !== null && !DIRECTIONS.includes(direction))
        || (rawQ !== null && (rawQ.length > 100 || rawQ.includes('\0')))) return invalid(route)
      const WS = '\\t-\\r\\x1C-\\x20\\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000'
      const stripped = rawQ === null ? '' : rawQ.replace(new RegExp(`^[${WS}]+|[${WS}]+$`, 'g'), '')
      const q = stripped === '' ? null : stripped.toLocaleLowerCase('pl')
      const all = []
      for (let n = 0; n < (MODE === 'empty' ? 0 : FEED_TOTAL); n++) all.push(feedItem(n))
      const filtered = q === null && kind === null && direction === null ? all : all.filter((item) => matches(item, q, kind, direction))
      const items = filtered.slice(pageNumber * size, (pageNumber + 1) * size)
      return json(route, { items, page: pageNumber, size, total: filtered.length })
    }
    const detail = p.match(/^\/api\/posts\/([^/]+)(\/raw)?$/)
    if (detail) {
      const raw = detail[2] !== undefined
      const id = decodeURIComponent(detail[1])
      if (!UUID.test(id)) return invalid(route)
      if (MODE === 'loading' || (MODE === 'raw-loading' && raw)) await hang()
      if (MODE === 'error' || (MODE === 'raw-error' && raw)) return json(route, { detail: 'internal_error' }, 500)
      const n = id.startsWith(uuid(4, 0).slice(0, 28)) ? Number(id.slice(28)) : NaN
      if (MODE === 'empty' || !(n < FEED_TOTAL)) return json(route, { detail: 'post_not_found' }, 404)
      return raw ? route.fulfill({ status: 200, contentType: 'application/json', body: feedRaw(n) }) : json(route, feedItem(n))
    }
    return json(route, { detail: 'not_found' }, 404)
  })
}
