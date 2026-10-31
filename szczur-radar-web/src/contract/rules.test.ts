import { describe, expect, it } from 'vitest'
import mockApi from '../../dev/mock-api.js?raw'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '../api/activation'
import {
  type AttentionReason,
  type InterruptionCause,
  MAX_RUNS_PAGE_SIZE,
  type RunKind,
  type RunStatus,
  type RunStepName,
  type SourceFetchStatus,
  type StepStatus,
} from '../api/pipeline'
import {
  type Attribution,
  type ClaimKind,
  DIRECTIONS,
  type FailureClass,
  type Flag,
  type ItemType,
  MAX_ASSESSMENT_ATTEMPTS,
  type Modifier,
  type Reference,
  type Relevance,
  TARGET_KINDS,
} from '../api/posts'
import { MAX_QUERY_LENGTH, WHITE_SPACE } from '../feedFilter'
import { CHANNEL_PREFIXES, labels } from '../labels'
import { MAX_RANGE_DAYS } from '../missingDays'
import { attentionReasons, runTone, sourceTone, stepTone } from '../runs'
import rules from './rules.json'

// rules.json is every rule the SPA copies from the API, and SpaRulesTest (Java) compares it with the server. Here it
// is compared with the SPA's copies, so a rule changed on one side alone turns one of the two tests red.

type ValueSet = keyof typeof rules.values

/** The values of a set, in the file's (= the API's enum's) order. */
function valuesOf(set: ValueSet): string[] {
  return rules.values[set]
}

function sorted(items: readonly string[]): string[] {
  return [...items].sort()
}

describe('white space', () => {
  it('the SPA strips the code points of the API (contract V2 §10)', () => {
    const spa = new RegExp(`[${WHITE_SPACE}]`)
    const matched: number[] = []
    for (let code = 0; code <= 0xffff; code++) {
      if (spa.test(String.fromCharCode(code))) matched.push(code)
    }
    expect(matched).toEqual(rules.whitespace)
  })

  it('has no code point beyond the BMP, which the scan above would not see', () => {
    expect(rules.whitespace.every((code) => code <= 0xffff)).toBe(true)
  })
})

describe('limits', () => {
  const spa: Record<keyof typeof rules.limits, number> = {
    feedQueryMaxLength: MAX_QUERY_LENGTH,
    runRangeMaxDays: MAX_RANGE_DAYS,
    runsPageMaxSize: MAX_RUNS_PAGE_SIZE,
    passwordMinLength: PASSWORD_MIN_LENGTH,
    passwordMaxLength: PASSWORD_MAX_LENGTH,
    maxAssessmentAttempts: MAX_ASSESSMENT_ATTEMPTS,
  }

  it('the SPA knows exactly the limits of the file', () => {
    expect(sorted(Object.keys(spa))).toEqual(sorted(Object.keys(rules.limits)))
  })

  it.each(Object.entries(spa))('%s is the API\'s', (name, value) => {
    expect(value).toBe((rules.limits as Record<string, number>)[name])
  })
})

describe('labels', () => {
  const sets = Object.keys(rules.values).filter((set) => set !== 'attentionReason')

  it('are given for exactly the sets of values of the file (an attention reason has a text, not a label)', () => {
    expect(sorted(Object.keys(labels))).toEqual(sorted(sets))
  })

  it.each(sets)('%s has a label for each value and for no other', (set) => {
    expect(sorted(Object.keys(labels[set]))).toEqual(sorted(valuesOf(set as ValueSet)))
  })
})

describe('channel prefixes', () => {
  it('are given for exactly the sources of the file', () => {
    expect(sorted(Object.keys(CHANNEL_PREFIXES))).toEqual(sorted(valuesOf('source')))
  })
})

describe('the options of the feed filter', () => {
  it('target kinds are the API\'s, in its order', () => {
    expect([...TARGET_KINDS]).toEqual(rules.values.targetKind)
  })

  it('directions are the API\'s, in its order', () => {
    expect([...DIRECTIONS]).toEqual(rules.values.direction)
  })
})

describe('tones', () => {
  it.each(valuesOf('stepStatus'))('step status %s has a tone', (status) => {
    expect(stepTone(status as StepStatus)).toBeTypeOf('string')
  })

  it.each(valuesOf('sourceStatus'))('source status %s has a tone', (status) => {
    expect(sourceTone(status as SourceFetchStatus)).toBeTypeOf('string')
  })

  it.each(valuesOf('runStatus'))('run status %s has a tone', (status) => {
    expect(runTone({ status: status as RunStatus, interruptionCause: null, skipped: 0 })).toBeTypeOf('string')
  })
})

describe('reasons a run needs attention', () => {
  const run = {
    needsAttention: true,
    interruptionCause: null,
    dead: 0,
    processingErrors: 0,
    skipped: 0,
    error: null,
    sourceResults: [],
  }

  it.each(valuesOf('attentionReason'))('%s has a text of its own, not the code as it came', (code) => {
    const texts = attentionReasons({ ...run, attentionReasons: [code as AttentionReason] })
    expect(texts).toHaveLength(1)
    expect(texts[0]).not.toBe(code)
  })
})

// Each tuple must list every member of its union: `Exhaustive` is `never` for a tuple that misses one, and `satisfies`
// rejects a member the union does not have, so the build fails on either. The tuples are then the sets of the file.
/** Compiles only when T lists every member of U; extra members fail `satisfies`. */
type Exhaustive<U, T extends readonly U[]> = [Exclude<U, T[number]>] extends [never] ? T : never

const RUN_STATUSES = ['queued', 'running', 'succeeded', 'partial', 'failed', 'interrupted'] as const satisfies readonly RunStatus[]
const RUN_KINDS = ['range', 'retry', 'posts'] as const satisfies readonly RunKind[]
const STEP_STATUSES = ['running', 'succeeded', 'failed', 'interrupted'] as const satisfies readonly StepStatus[]
const RUN_STEPS = ['fetch', 'store', 'select', 'assess', 'summarize'] as const satisfies readonly RunStepName[]
const INTERRUPTION_CAUSES = ['deploy', 'restart', 'shutdown', 'lost'] as const satisfies readonly InterruptionCause[]
const ATTENTION_REASONS = [
  'failed',
  'interrupted',
  'source_failed',
  'source_empty',
  'dead_posts',
  'processing_errors',
  'skipped',
] as const satisfies readonly AttentionReason[]
const SOURCE_STATUSES = ['succeeded', 'empty', 'failed'] as const satisfies readonly SourceFetchStatus[]
const ITEM_TYPES = ['post', 'link', 'comment'] as const satisfies readonly ItemType[]
const RELEVANCES = ['investment_related', 'not_investment_related', 'unclear'] as const satisfies readonly Relevance[]
const FLAGS = [
  'missing_context',
  'possible_sarcasm',
  'insufficient_content',
  'extraction_limit',
] as const satisfies readonly Flag[]
const MODIFIERS = ['humor', 'promotion'] as const satisfies readonly Modifier[]
const CLAIM_KINDS = [
  'factual_claim',
  'opinion',
  'investment_thesis',
  'question',
  'trade_action',
] as const satisfies readonly ClaimKind[]
const ATTRIBUTIONS = ['author', 'reported', 'unclear'] as const satisfies readonly Attribution[]
const REFERENCES = ['explicit', 'contextual', 'ambiguous', 'unresolved'] as const satisfies readonly Reference[]
const FAILURE_CLASSES = [
  'provider_error',
  'timeout',
  'invalid_structured_output',
  'schema_violation',
  'invalid_reference',
  'fabricated_evidence',
  'domain_invariant_violation',
] as const satisfies readonly FailureClass[]

/**
 * The TS unions that have a list of values in the file, as exhaustive tuples. Not here: `TargetKind` and `Direction`
 * (derived from `TARGET_KINDS` and `DIRECTIONS`, compared in order above) and `source`, which the SPA types as `string`.
 */
const UNION_TUPLES: Partial<Record<ValueSet, readonly string[]>> = {
  runStatus: RUN_STATUSES satisfies Exhaustive<RunStatus, typeof RUN_STATUSES>,
  runKind: RUN_KINDS satisfies Exhaustive<RunKind, typeof RUN_KINDS>,
  stepStatus: STEP_STATUSES satisfies Exhaustive<StepStatus, typeof STEP_STATUSES>,
  step: RUN_STEPS satisfies Exhaustive<RunStepName, typeof RUN_STEPS>,
  interruptionCause: INTERRUPTION_CAUSES satisfies Exhaustive<InterruptionCause, typeof INTERRUPTION_CAUSES>,
  attentionReason: ATTENTION_REASONS satisfies Exhaustive<AttentionReason, typeof ATTENTION_REASONS>,
  sourceStatus: SOURCE_STATUSES satisfies Exhaustive<SourceFetchStatus, typeof SOURCE_STATUSES>,
  itemType: ITEM_TYPES satisfies Exhaustive<ItemType, typeof ITEM_TYPES>,
  relevance: RELEVANCES satisfies Exhaustive<Relevance, typeof RELEVANCES>,
  flag: FLAGS satisfies Exhaustive<Flag, typeof FLAGS>,
  modifier: MODIFIERS satisfies Exhaustive<Modifier, typeof MODIFIERS>,
  claimKind: CLAIM_KINDS satisfies Exhaustive<ClaimKind, typeof CLAIM_KINDS>,
  attribution: ATTRIBUTIONS satisfies Exhaustive<Attribution, typeof ATTRIBUTIONS>,
  reference: REFERENCES satisfies Exhaustive<Reference, typeof REFERENCES>,
  failureClass: FAILURE_CLASSES satisfies Exhaustive<FailureClass, typeof FAILURE_CLASSES>,
}

describe('the unions of src/api', () => {
  it.each(Object.entries(UNION_TUPLES))('%s lists the values of the file', (set, tuple) => {
    expect(sorted(tuple ?? [])).toEqual(sorted(valuesOf(set as ValueSet)))
  })

  it('every set of the file is a union here, except the ones named above', () => {
    const others = Object.keys(rules.values).filter((set) => !(set in UNION_TUPLES))
    expect(sorted(others)).toEqual(['direction', 'source', 'targetKind'])
  })
})

describe('error codes', () => {
  const files = import.meta.glob<string>('../**/*.{ts,vue}', { query: '?raw', import: 'default', eager: true })
  const sources: Record<string, string> = {}
  for (const [path, text] of Object.entries(files)) {
    if (!path.endsWith('/contract/rules.test.ts')) sources[path] = text
  }
  sources['dev/mock-api.js'] = mockApi

  /** The codes written as `detail: '<code>'`, `new ApiError(<status>, '<code>')`, and `case '<code>'` after `.detail)`. */
  function literals(text: string): string[] {
    const found = [
      ...text.matchAll(/\bdetail:\s*['"`]([a-z_]+)['"`]/g),
      ...text.matchAll(/new ApiError\(\s*\d+,\s*['"`]([a-z_]+)['"`]/g),
    ]
    if (text.includes('.detail)')) found.push(...text.matchAll(/\bcase\s+['"`]([a-z_]+)['"`]/g))
    return found.map((match) => match[1])
  }

  const hits = Object.entries(sources).flatMap(([path, text]) => literals(text).map((code) => ({ path, code })))

  it('finds the codes, so a broken pattern does not pass in silence', () => {
    expect(hits.length).toBeGreaterThanOrEqual(20)
  })

  it('the SPA, its tests and the mock use only codes the API has', () => {
    const unknown = hits.filter((hit) => !rules.errorCodes.includes(hit.code))
    expect(unknown).toEqual([])
  })
})
