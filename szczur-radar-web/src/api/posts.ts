// /api/posts, /api/posts/{id}, /api/posts/{id}/raw and /api/admin/posts/failed — the contract of szczur-radar-api posts/api/*Controller.
import { apiFetch, apiFetchText } from './client'

/** The shared model's type of a post. */
export type ItemType = 'post' | 'link' | 'comment'

export type Relevance = 'investment_related' | 'not_investment_related' | 'unclear'
export type Direction = 'bullish' | 'bearish' | 'neutral' | 'mixed' | 'not_expressed' | 'unclear'
export type Flag = 'missing_context' | 'possible_sarcasm' | 'insufficient_content' | 'extraction_limit'
export type Modifier = 'humor' | 'promotion'
export type ClaimKind = 'factual_claim' | 'opinion' | 'investment_thesis' | 'question' | 'trade_action'
export type Attribution = 'author' | 'reported' | 'unclear'
export type TargetKind = 'company' | 'asset' | 'sector' | 'macro_topic' | 'other' | 'unknown'
export type Reference = 'explicit' | 'contextual' | 'ambiguous' | 'unresolved'
/** `null` where the direction has none (table §7.2 of the contract). */
export type Assertiveness = 'hedged' | 'unhedged' | null

/** A verbatim fragment of one text field of the input (contract V2 §3, §10). */
export interface Quote {
  document: 'item' | 'parent_post' | 'parent_comment'
  field: 'title' | 'body'
  quote: string
}

/** The author's stance on one target; `claim_ids` are the claims it rests on (contract V2 §3, §7). */
export interface Stance {
  direction: Direction
  assertiveness: Assertiveness
  claim_ids: string[]
}

/** A target of the assessment with the author's stance (contract V2 §3). */
export interface Target {
  id: string
  kind: TargetKind
  anchor: Quote
  stance: Stance
  reference: Reference
}

/** What the author states, with one quote from the post; a general claim has no `target_ids` (contract V2 §3). */
export interface Claim {
  id: string
  kind: ClaimKind
  attribution: Attribution
  target_ids: string[]
  evidence: Quote
  statement: string
}

/** The model's output, contract V2 — szczur-radar-api agents/assessor/contract/AssessmentOutputV2. */
export interface AssessmentOutput {
  relevance: Relevance
  modifiers: Modifier[]
  targets: Target[]
  claims: Claim[]
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

/** One assessed post, shaped like a feed item; 404 `post_not_found` for any other id, 400 for a malformed one. */
export function fetchPost(id: string): Promise<PostItem> {
  return apiFetch<PostItem>(`/api/posts/${encodeURIComponent(id)}`)
}

/** The post's source item exactly as fetched, as indented JSON text; the same 404 and 400 as `fetchPost`. */
export function fetchPostRaw(id: string): Promise<string> {
  return apiFetchText(`/api/posts/${encodeURIComponent(id)}/raw`)
}

/** Failed and dead posts with their latest attempt (admin). */
export function fetchFailedPosts(page = 0, size = 50): Promise<Page<FailedPost>> {
  return apiFetch<Page<FailedPost>>(`/api/admin/posts/failed?page=${page}&size=${size}`)
}
