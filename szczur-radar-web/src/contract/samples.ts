// The full bodies of the shared API responses as typed TS literals, the other half of the JSON files next to this one:
// one sample per file, each named in SAMPLES by its file. The API's integration tests compare each real body, masked,
// with its file (ContractFiles); shapes.test.ts finds every file, requires its sample here and compares the two, and
// the type annotations make vue-tsc reject a field the type does not know. A field added in Java therefore changes the
// file (the integration test prints the new body), this sample and the TS type together, in one PR.
//
// Masks, as in the files: a UUID is '<uuid>', an ISO-8601 instant is '<instant>'; every other value is literal.
import type { ActivationLookup } from '../api/activation'
import type { User } from '../api/auth'
import type { Freshness } from '../api/freshness'
import type { NotificationList } from '../api/notifications'
import type { MissingDays, Run, SourceState } from '../api/pipeline'
import type { FailedPost, Page, PostItem } from '../api/posts'

/** A range run of the daily cron that ended partial: one source failed, elements were skipped, a post died. */
export const sampleRun: Run = {
  id: '<uuid>',
  kind: 'range',
  status: 'partial',
  interruptionCause: null,
  needsAttention: true,
  attentionReasons: ['source_failed', 'dead_posts', 'processing_errors', 'skipped'],
  scheduled: true,
  rangeFrom: '<instant>',
  rangeTo: '<instant>',
  sources: ['wykop', 'reddit'],
  requestedPostIds: null,
  createdAt: '<instant>',
  startedAt: '<instant>',
  finishedAt: '<instant>',
  lastActivityAt: '<instant>',
  fetched: 12,
  newPosts: 10,
  assessedOk: 8,
  assessedFailed: 1,
  processingErrors: 1,
  dead: 1,
  skipped: 2,
  sourcesFailed: 1,
  sourcesEmpty: 0,
  llmRetries: 3,
  flexFallbacks: 1,
  inFlight: 0,
  error: 'reddit failed: IllegalStateException: 503',
  steps: [
    {
      step: 'fetch',
      status: 'succeeded',
      startedAt: '<instant>',
      finishedAt: '<instant>',
      itemsIn: null,
      itemsOut: 12,
      error: null,
    },
    {
      step: 'store',
      status: 'succeeded',
      startedAt: '<instant>',
      finishedAt: '<instant>',
      itemsIn: 10,
      itemsOut: 10,
      error: null,
    },
    {
      step: 'select',
      status: 'succeeded',
      startedAt: '<instant>',
      finishedAt: '<instant>',
      itemsIn: 10,
      itemsOut: 10,
      error: null,
    },
    {
      step: 'assess',
      status: 'succeeded',
      startedAt: '<instant>',
      finishedAt: '<instant>',
      itemsIn: 10,
      itemsOut: 8,
      error: null,
    },
  ],
  sourceResults: [
    {
      source: 'wykop',
      status: 'succeeded',
      fetched: 12,
      skipped: 2,
      error: null,
      finishedAt: '<instant>',
    },
    {
      source: 'reddit',
      status: 'failed',
      fetched: 0,
      skipped: 0,
      error: 'IllegalStateException: 503',
      finishedAt: '<instant>',
    },
  ],
}

/** An assessed post with a bullish target resting on an author's claim and a target with only a reported one. */
export const samplePost: PostItem = {
  id: '<uuid>',
  source: 'wykop',
  channel: 'gielda',
  itemType: 'post',
  publishedAt: '<instant>',
  title: 'XTB i CDR po wynikach',
  body: 'XTB po wynikach poleci w górę, dokupuję.\nAnalityk pisze, że CDR spadnie.\n#gielda ',
  author: 'uzytkownik1',
  webUrl: 'https://wykop.pl/wpis/12345',
  assessment: {
    output: {
      relevance: 'investment_related',
      modifiers: [],
      targets: [
        {
          id: 't1',
          kind: 'company',
          anchor: { document: 'item', field: 'body', quote: 'XTB' },
          stance: { direction: 'bullish', assertiveness: 'unhedged', claim_ids: ['c1'] },
          reference: 'explicit',
        },
        {
          id: 't2',
          kind: 'company',
          anchor: { document: 'item', field: 'body', quote: 'CDR' },
          stance: { direction: 'not_expressed', assertiveness: null, claim_ids: [] },
          reference: 'explicit',
        },
      ],
      claims: [
        {
          id: 'c1',
          kind: 'investment_thesis',
          attribution: 'author',
          target_ids: ['t1'],
          evidence: { document: 'item', field: 'body', quote: 'XTB po wynikach poleci w górę' },
          statement: 'Teza testowa.',
        },
        {
          id: 'c2',
          kind: 'investment_thesis',
          attribution: 'reported',
          target_ids: ['t2'],
          evidence: { document: 'item', field: 'body', quote: 'Analityk pisze, że CDR spadnie' },
          statement: 'Teza testowa.',
        },
      ],
      flags: [],
    },
    resultSource: 'model',
    assessedAt: '<instant>',
  },
}

/** Both sources with an update, in the API's order. */
export const sampleFreshness: Freshness = {
  sources: [
    { source: 'wykop', lastUpdatedAt: '<instant>' },
    { source: 'reddit', lastUpdatedAt: '<instant>' },
  ],
}

/** The logged-in account (POST /api/auth/login and GET /api/auth/me answer the same shape). */
export const sampleUser: User = {
  id: '<uuid>',
  email: 'contract@szczur.test',
  role: 'admin',
}

/** The account a valid activation link sets a password for (POST /api/auth/activation/lookup). */
export const sampleActivationLookup: ActivationLookup = {
  email: 'contract@szczur.test',
}

/**
 * An admin's notifications (GET /api/admin/notifications), newest first: an unread one about a run interrupted by a
 * deploy, with its cause and error, and a read one about a partial run with a dead post and neither.
 */
export const sampleNotifications: NotificationList = {
  unread: 1,
  items: [
    {
      id: '<uuid>',
      kind: 'run_attention',
      createdAt: '<instant>',
      readAt: null,
      run: {
        id: '<uuid>',
        kind: 'retry',
        status: 'interrupted',
        interruptionCause: 'deploy',
        finishedAt: '<instant>',
        assessedFailed: 2,
        dead: 1,
        processingErrors: 3,
        skipped: 4,
        error: 'interrupted: a new deployment took over',
      },
    },
    {
      id: '<uuid>',
      kind: 'run_attention',
      createdAt: '<instant>',
      readAt: '<instant>',
      run: {
        id: '<uuid>',
        kind: 'retry',
        status: 'partial',
        interruptionCause: null,
        finishedAt: '<instant>',
        assessedFailed: 1,
        dead: 1,
        processingErrors: 0,
        skipped: 0,
        error: null,
      },
    },
  ],
}

/** A notification the API no longer finds the run of: `run` is null. */
export const sampleNotificationsWithoutRun: NotificationList = {
  unread: 1,
  items: [
    {
      id: '<uuid>',
      kind: 'run_attention',
      createdAt: '<instant>',
      readAt: null,
      run: null,
    },
  ],
}

/** Every registered source in the API's order (GET /api/admin/pipeline-runs/sources): Wykop configured, Reddit not. */
export const sampleSources: SourceState[] = [
  { source: 'wykop', configured: true },
  { source: 'reddit', configured: false },
]

/** The days without data (GET /api/admin/pipeline-runs/missing-days): one missing Reddit only, one missing both. */
export const sampleMissingDays: MissingDays = {
  from: '2026-10-09',
  to: '2026-10-22',
  days: [
    { day: '2026-10-19', sources: ['reddit'] },
    { day: '2026-10-20', sources: ['wykop', 'reddit'] },
  ],
}

/**
 * The 202 of a started range with leftovers (POST /api/admin/pipeline-runs): the retry run, then the range run, both
 * queued, so nothing has started and neither has a step or a source result.
 */
export const sampleStartedRuns: Run[] = [
  {
    id: '<uuid>',
    kind: 'retry',
    status: 'queued',
    interruptionCause: null,
    needsAttention: false,
    attentionReasons: [],
    scheduled: false,
    rangeFrom: null,
    rangeTo: null,
    sources: [],
    requestedPostIds: null,
    createdAt: '<instant>',
    startedAt: null,
    finishedAt: null,
    lastActivityAt: null,
    fetched: 0,
    newPosts: 0,
    assessedOk: 0,
    assessedFailed: 0,
    processingErrors: 0,
    dead: 0,
    skipped: 0,
    sourcesFailed: 0,
    sourcesEmpty: 0,
    llmRetries: 0,
    flexFallbacks: 0,
    inFlight: 0,
    error: null,
    steps: [],
    sourceResults: [],
  },
  {
    id: '<uuid>',
    kind: 'range',
    status: 'queued',
    interruptionCause: null,
    needsAttention: false,
    attentionReasons: [],
    scheduled: false,
    rangeFrom: '<instant>',
    rangeTo: '<instant>',
    sources: ['wykop'],
    requestedPostIds: null,
    createdAt: '<instant>',
    startedAt: null,
    finishedAt: null,
    lastActivityAt: null,
    fetched: 0,
    newPosts: 0,
    assessedOk: 0,
    assessedFailed: 0,
    processingErrors: 0,
    dead: 0,
    skipped: 0,
    sourcesFailed: 0,
    sourcesEmpty: 0,
    llmRetries: 0,
    flexFallbacks: 0,
    inFlight: 0,
    error: null,
    steps: [],
    sourceResults: [],
  },
]

/**
 * A page of the feed (GET /api/posts?page=0&size=10), newest first: a model's assessment with a modifier and a flag,
 * a comment without a title, an author or an address, and a Reddit link without a body that the prefilter assessed.
 */
export const samplePostsPage: Page<PostItem> = {
  items: [
    {
      id: '<uuid>',
      source: 'wykop',
      channel: 'gielda',
      itemType: 'post',
      publishedAt: '<instant>',
      title: 'XTB i CDR po wynikach',
      body: 'XTB po wynikach poleci w górę, dokupuję.\nAnalityk pisze, że CDR spadnie.\n#gielda ',
      author: 'uzytkownik1',
      webUrl: 'https://wykop.pl/wpis/12345',
      assessment: {
        output: {
          relevance: 'investment_related',
          modifiers: ['humor'],
          targets: [
            {
              id: 't1',
              kind: 'company',
              anchor: {
                document: 'item',
                field: 'body',
                quote: 'XTB',
              },
              stance: {
                direction: 'bullish',
                assertiveness: 'unhedged',
                claim_ids: ['c1'],
              },
              reference: 'explicit',
            },
            {
              id: 't2',
              kind: 'company',
              anchor: {
                document: 'item',
                field: 'body',
                quote: 'CDR',
              },
              stance: {
                direction: 'not_expressed',
                assertiveness: null,
                claim_ids: [],
              },
              reference: 'explicit',
            },
          ],
          claims: [
            {
              id: 'c1',
              kind: 'investment_thesis',
              attribution: 'author',
              target_ids: ['t1'],
              evidence: {
                document: 'item',
                field: 'body',
                quote: 'XTB po wynikach poleci w górę',
              },
              statement: 'Teza testowa.',
            },
            {
              id: 'c2',
              kind: 'investment_thesis',
              attribution: 'reported',
              target_ids: ['t2'],
              evidence: {
                document: 'item',
                field: 'body',
                quote: 'Analityk pisze, że CDR spadnie',
              },
              statement: 'Teza testowa.',
            },
          ],
          flags: ['possible_sarcasm'],
        },
        resultSource: 'model',
        assessedAt: '<instant>',
      },
    },
    {
      id: '<uuid>',
      source: 'wykop',
      channel: 'gielda',
      itemType: 'comment',
      publishedAt: '<instant>',
      title: null,
      body: 'XTB po wynikach poleci w górę, dokupuję.\nAnalityk pisze, że CDR spadnie.\n#gielda ',
      author: null,
      webUrl: null,
      assessment: {
        output: {
          relevance: 'investment_related',
          modifiers: [],
          targets: [
            {
              id: 't1',
              kind: 'company',
              anchor: {
                document: 'item',
                field: 'body',
                quote: 'XTB',
              },
              stance: {
                direction: 'bullish',
                assertiveness: 'unhedged',
                claim_ids: ['c1'],
              },
              reference: 'explicit',
            },
            {
              id: 't2',
              kind: 'company',
              anchor: {
                document: 'item',
                field: 'body',
                quote: 'CDR',
              },
              stance: {
                direction: 'not_expressed',
                assertiveness: null,
                claim_ids: [],
              },
              reference: 'explicit',
            },
          ],
          claims: [
            {
              id: 'c1',
              kind: 'investment_thesis',
              attribution: 'author',
              target_ids: ['t1'],
              evidence: {
                document: 'item',
                field: 'body',
                quote: 'XTB po wynikach poleci w górę',
              },
              statement: 'Teza testowa.',
            },
            {
              id: 'c2',
              kind: 'investment_thesis',
              attribution: 'reported',
              target_ids: ['t2'],
              evidence: {
                document: 'item',
                field: 'body',
                quote: 'Analityk pisze, że CDR spadnie',
              },
              statement: 'Teza testowa.',
            },
          ],
          flags: [],
        },
        resultSource: 'model',
        assessedAt: '<instant>',
      },
    },
    {
      id: '<uuid>',
      source: 'reddit',
      channel: 'stocks',
      itemType: 'link',
      publishedAt: '<instant>',
      title: '🚀🚀',
      body: null,
      author: 'uzytkownik2',
      webUrl: 'https://www.reddit.com/r/stocks/comments/abc123/',
      assessment: {
        output: {
          relevance: 'unclear',
          modifiers: [],
          targets: [],
          claims: [],
          flags: ['insufficient_content'],
        },
        resultSource: 'prefilter',
        assessedAt: '<instant>',
      },
    },
  ],
  page: 0,
  size: 10,
  total: 3,
}

/**
 * A page of the failed list (GET /api/admin/posts/failed?page=0&size=10), newest first: a failed post with one
 * violation and no technical error, a failed one whose attempt names no provider or model, a dead one, and a stored
 * one on hold, never assessed, with its technical error.
 */
export const sampleFailedPostsPage: Page<FailedPost> = {
  items: [
    {
      id: '<uuid>',
      itemType: 'post',
      publishedAt: '<instant>',
      title: 'XTB i CDR po wynikach',
      body: 'XTB po wynikach poleci w górę, dokupuję.\nAnalityk pisze, że CDR spadnie.\n#gielda ',
      webUrl: 'https://wykop.pl/wpis/12345',
      stage: 'failed',
      assessmentAttempts: 1,
      lastAttempt: {
        failureClass: 'schema_violation',
        violations: [
          {
            failureClass: 'schema_violation',
            rule: 'schema:required',
            path: '$.claims[0]',
            message: 'required property \'evidence\' not found',
          },
        ],
        assessedAt: '<instant>',
        provider: 'gemini',
        model: 'gemini-3.1-flash-lite',
      },
      technicalFailures: 0,
      lastTechnicalError: null,
      onHold: false,
    },
    {
      id: '<uuid>',
      itemType: 'link',
      publishedAt: '<instant>',
      title: 'CD Projekt: wyniki za kwartał',
      body: null,
      webUrl: 'https://wykop.pl/link/778/cd-projekt-wyniki-za-kwartal',
      stage: 'failed',
      assessmentAttempts: 2,
      lastAttempt: {
        failureClass: 'timeout',
        violations: [
          {
            failureClass: 'timeout',
            rule: 'call',
            path: '$',
            message: 'no answer within 300s',
          },
        ],
        assessedAt: '<instant>',
        provider: null,
        model: null,
      },
      technicalFailures: 0,
      lastTechnicalError: null,
      onHold: false,
    },
    {
      id: '<uuid>',
      itemType: 'link',
      publishedAt: '<instant>',
      title: 'PKO BP: dywidenda rekordowa',
      body: 'Kupuję PKO przed dywidendą.',
      webUrl: 'https://wykop.pl/link/777/pko-bp-dywidenda-rekordowa',
      stage: 'dead',
      assessmentAttempts: 3,
      lastAttempt: {
        failureClass: 'fabricated_evidence',
        violations: [
          {
            failureClass: 'fabricated_evidence',
            rule: 'D6',
            path: '$.claims[0].evidence.quote',
            message: 'quote is not a fragment of item.body',
          },
        ],
        assessedAt: '<instant>',
        provider: 'gemini',
        model: 'gemini-3.1-flash-lite',
      },
      technicalFailures: 0,
      lastTechnicalError: null,
      onHold: false,
    },
    {
      id: '<uuid>',
      itemType: 'comment',
      publishedAt: '<instant>',
      title: null,
      body: 'Orlen znowu w dół, trzymam.',
      webUrl: null,
      stage: 'stored',
      assessmentAttempts: 0,
      lastAttempt: null,
      technicalFailures: 3,
      lastTechnicalError: 'IllegalStateException: a bug in the mapper',
      onHold: true,
    },
  ],
  page: 0,
  size: 10,
  total: 4,
}

/**
 * A page of runs (GET /api/admin/pipeline-runs?page=0&size=10), newest first: a succeeded retry run that never
 * recorded its times, an admin's run of chosen posts, an admin's retry run a restart interrupted in its open assess
 * step, and the cron's partial range run (as `sampleRun`).
 */
export const sampleRunsPage: Page<Run> = {
  items: [
    {
      id: '<uuid>',
      kind: 'retry',
      status: 'succeeded',
      interruptionCause: null,
      needsAttention: false,
      attentionReasons: [],
      scheduled: false,
      rangeFrom: null,
      rangeTo: null,
      sources: [],
      requestedPostIds: null,
      createdAt: '<instant>',
      startedAt: null,
      finishedAt: null,
      lastActivityAt: null,
      fetched: 0,
      newPosts: 0,
      assessedOk: 0,
      assessedFailed: 0,
      processingErrors: 0,
      dead: 0,
      skipped: 0,
      sourcesFailed: 0,
      sourcesEmpty: 0,
      llmRetries: 0,
      flexFallbacks: 0,
      inFlight: 0,
      error: null,
      steps: [],
      sourceResults: [],
    },
    {
      id: '<uuid>',
      kind: 'posts',
      status: 'succeeded',
      interruptionCause: null,
      needsAttention: false,
      attentionReasons: [],
      scheduled: false,
      rangeFrom: null,
      rangeTo: null,
      sources: [],
      requestedPostIds: ['<uuid>', '<uuid>'],
      createdAt: '<instant>',
      startedAt: '<instant>',
      finishedAt: '<instant>',
      lastActivityAt: '<instant>',
      fetched: 0,
      newPosts: 0,
      assessedOk: 2,
      assessedFailed: 0,
      processingErrors: 0,
      dead: 0,
      skipped: 0,
      sourcesFailed: 0,
      sourcesEmpty: 0,
      llmRetries: 0,
      flexFallbacks: 0,
      inFlight: 0,
      error: null,
      steps: [
        {
          step: 'select',
          status: 'succeeded',
          startedAt: '<instant>',
          finishedAt: '<instant>',
          itemsIn: null,
          itemsOut: 2,
          error: null,
        },
        {
          step: 'assess',
          status: 'succeeded',
          startedAt: '<instant>',
          finishedAt: '<instant>',
          itemsIn: 2,
          itemsOut: 2,
          error: null,
        },
      ],
      sourceResults: [],
    },
    {
      id: '<uuid>',
      kind: 'retry',
      status: 'interrupted',
      interruptionCause: 'restart',
      needsAttention: true,
      attentionReasons: ['interrupted'],
      scheduled: false,
      rangeFrom: null,
      rangeTo: null,
      sources: [],
      requestedPostIds: null,
      createdAt: '<instant>',
      startedAt: '<instant>',
      finishedAt: '<instant>',
      lastActivityAt: '<instant>',
      fetched: 0,
      newPosts: 0,
      assessedOk: 2,
      assessedFailed: 1,
      processingErrors: 0,
      dead: 0,
      skipped: 0,
      sourcesFailed: 0,
      sourcesEmpty: 0,
      llmRetries: 1,
      flexFallbacks: 0,
      inFlight: 0,
      error: 'interrupted: the application restarted',
      steps: [
        {
          step: 'select',
          status: 'succeeded',
          startedAt: '<instant>',
          finishedAt: '<instant>',
          itemsIn: null,
          itemsOut: 5,
          error: null,
        },
        {
          step: 'assess',
          status: 'interrupted',
          startedAt: '<instant>',
          finishedAt: null,
          itemsIn: 5,
          itemsOut: null,
          error: 'interrupted: the application restarted',
        },
      ],
      sourceResults: [],
    },
    {
      id: '<uuid>',
      kind: 'range',
      status: 'partial',
      interruptionCause: null,
      needsAttention: true,
      attentionReasons: ['source_failed', 'dead_posts', 'processing_errors', 'skipped'],
      scheduled: true,
      rangeFrom: '<instant>',
      rangeTo: '<instant>',
      sources: ['wykop', 'reddit'],
      requestedPostIds: null,
      createdAt: '<instant>',
      startedAt: '<instant>',
      finishedAt: '<instant>',
      lastActivityAt: '<instant>',
      fetched: 12,
      newPosts: 10,
      assessedOk: 8,
      assessedFailed: 1,
      processingErrors: 1,
      dead: 1,
      skipped: 2,
      sourcesFailed: 1,
      sourcesEmpty: 0,
      llmRetries: 3,
      flexFallbacks: 1,
      inFlight: 0,
      error: 'reddit failed: IllegalStateException: 503',
      steps: [
        {
          step: 'fetch',
          status: 'succeeded',
          startedAt: '<instant>',
          finishedAt: '<instant>',
          itemsIn: null,
          itemsOut: 12,
          error: null,
        },
        {
          step: 'store',
          status: 'succeeded',
          startedAt: '<instant>',
          finishedAt: '<instant>',
          itemsIn: 10,
          itemsOut: 10,
          error: null,
        },
        {
          step: 'select',
          status: 'succeeded',
          startedAt: '<instant>',
          finishedAt: '<instant>',
          itemsIn: 10,
          itemsOut: 10,
          error: null,
        },
        {
          step: 'assess',
          status: 'succeeded',
          startedAt: '<instant>',
          finishedAt: '<instant>',
          itemsIn: 10,
          itemsOut: 8,
          error: null,
        },
      ],
      sourceResults: [
        {
          source: 'wykop',
          status: 'succeeded',
          fetched: 12,
          skipped: 2,
          error: null,
          finishedAt: '<instant>',
        },
        {
          source: 'reddit',
          status: 'failed',
          fetched: 0,
          skipped: 0,
          error: 'IllegalStateException: 503',
          finishedAt: '<instant>',
        },
      ],
    },
  ],
  page: 0,
  size: 10,
  total: 4,
}

/** Every contract file (its name next to this one) with its sample; shapes.test.ts requires one for each file. */
export const SAMPLES: Record<string, unknown> = {
  'run.json': sampleRun,
  'post.json': samplePost,
  'freshness.json': sampleFreshness,
  'user.json': sampleUser,
  'activation-lookup.json': sampleActivationLookup,
  'notifications.json': sampleNotifications,
  'notifications-without-run.json': sampleNotificationsWithoutRun,
  'sources.json': sampleSources,
  'missing-days.json': sampleMissingDays,
  'started-runs.json': sampleStartedRuns,
  'posts-page.json': samplePostsPage,
  'failed-posts-page.json': sampleFailedPostsPage,
  'runs-page.json': sampleRunsPage,
}
