// The full bodies of the three large API responses as typed TS literals, the other half of the files next to this
// one: run.json (GET /api/admin/pipeline-runs/{id}), post.json (GET /api/posts/{id}) and failed-post.json (an item
// of GET /api/admin/posts/failed). The API's integration tests compare each real body, masked, with its file
// (ContractFiles); shapes.test.ts compares each file with the sample here, and the annotations make vue-tsc reject
// a field the type does not know. A field added in Java therefore changes the file (the integration test prints the
// new body), this sample and the TS type together, in one PR.
//
// Masks, as in the files: a UUID is '<uuid>', an ISO-8601 instant is '<instant>'; every other value is literal.
import type { Run } from '../api/pipeline'
import type { FailedPost, PostItem } from '../api/posts'

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

/** A post of the failed list: failed once on the schema, with one violation, and one technical failure. */
export const sampleFailedPost: FailedPost = {
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
        message: "required property 'evidence' not found",
      },
    ],
    assessedAt: '<instant>',
    provider: 'gemini',
    model: 'gemini-3.1-flash-lite',
  },
  technicalFailures: 1,
  lastTechnicalError: 'IllegalStateException: a bug in the mapper',
  onHold: false,
}
