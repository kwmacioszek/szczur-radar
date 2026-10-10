// /api/posts and /api/admin/posts/failed — the contract of szczur-radar-api posts/api/*Controller.
import { apiFetch } from './client'

/** The shared model's type of a post. */
export type ItemType = 'post' | 'link' | 'comment'

export type Relevance = 'investment_related' | 'not_investment_related' | 'unclear'
export type Direction = 'bullish' | 'bearish' | 'neutral' | 'mixed' | 'not_expressed' | 'unclear'
export type Flag = 'missing_context' | 'possible_sarcasm' | 'insufficient_content' | 'extraction_limit'

/** A target of the assessment with the author's stance (contract V2 §3, the fields the list shows). */
export interface Target {
  id: string
  kind: string
  anchor: { document: string; field: string; quote: string }
  stance: { direction: Direction; assertiveness: 'hedged' | 'unhedged' | null; claim_ids: string[] }
  reference: string
}

/** The model's output, contract V2 (the fields the list shows; claims are for the detail view, S-03). */
export interface AssessmentOutput {
  relevance: Relevance
  modifiers: string[]
  targets: Target[]
  claims: unknown[]
  flags: Flag[]
}

export interface PostItem {
  id: string
  itemType: ItemType
  publishedAt: string
  title: string | null
  body: string | null
  author: string | null
  webUrl: string | null
  assessment: {
    output: AssessmentOutput
    /** `prefilter` for a post without content, decided without the model. */
    resultSource: 'model' | 'prefilter'
    assessedAt: string
  } | null
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export type PostStage = 'failed' | 'dead'

/** Why an assessment attempt failed — szczur-radar-api agents/assessor/validation/FailureClass. */
export type FailureClass =
  | 'provider_error'
  | 'timeout'
  | 'invalid_structured_output'
  | 'schema_violation'
  | 'invalid_reference'
  | 'fabricated_evidence'
  | 'domain_invariant_violation'

export interface FailedPost {
  id: string
  itemType: ItemType
  publishedAt: string
  title: string | null
  body: string | null
  webUrl: string | null
  stage: PostStage
  assessmentAttempts: number
  lastAttempt: {
    failureClass: FailureClass | null
    violations: { failureClass: FailureClass; rule: string; path: string; message: string }[]
    assessedAt: string
    provider: string | null
    model: string | null
  } | null
}

/** The feed: assessed posts, newest first. `page` from 0. */
export function fetchPosts(page: number, size = 50): Promise<Page<PostItem>> {
  return apiFetch<Page<PostItem>>(`/api/posts?page=${page}&size=${size}`)
}

/** Failed and dead posts with their latest attempt (admin). */
export function fetchFailedPosts(page = 0, size = 50): Promise<Page<FailedPost>> {
  return apiFetch<Page<FailedPost>>(`/api/admin/posts/failed?page=${page}&size=${size}`)
}
