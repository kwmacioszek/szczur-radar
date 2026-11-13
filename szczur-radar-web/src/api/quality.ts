// /api/admin/assessment-quality — the contract of szczur-radar-api posts/api/AssessmentQualityController.
import { apiFetch } from './client'
import type { FailureClass } from './posts'

/**
 * A measure's window against its threshold — posts/QualityStatus, computed by the API, one rule for both measures:
 * enough attempts (theses) and the share not above the threshold (`ok`), strictly above it (`above_threshold`), or
 * fewer than `minAttempts` attempts (`minTheses` theses), so the threshold is not checked (`not_enough_data`).
 */
export type QualityStatus = 'ok' | 'above_threshold' | 'not_enough_data'

/** The failed attempts of one failure class. */
export interface ClassCount {
  failureClass: FailureClass
  count: number
}

/**
 * One source's theses of successful attempts with a model call in the window (S-36): how many, how many of them with
 * evidence of another language than their post's, the source's threshold and the state. Counts, not shares.
 */
export interface LanguageShare {
  theses: number
  otherLanguage: number
  /** The source's threshold in percent of its theses. */
  thresholdPercent: number
  status: QualityStatus
}

/** One source's assessment attempts with a model call in the window (retries included, the prefilter's left out). */
export interface SourceQuality {
  /** The source's value (`wykop`, `reddit`). */
  source: string
  attempts: number
  failed: number
  /** The failed attempts per class: only classes with one, in the API's order of classes. */
  classes: ClassCount[]
  /** The source's threshold in percent of its attempts. */
  thresholdPercent: number
  status: QualityStatus
  /** The source's theses in another language than their post; `null` only when the API could not read the theses. */
  language: LanguageShare | null
}

/** The window `from`–`to` (both included, `YYYY-MM-DD`: D-6…D in Warsaw) and its sources. */
export interface QualityWindow {
  from: string
  to: string
  windowDays: number
  /** The fewest attempts of a source for its threshold of failed attempts to be checked. */
  minAttempts: number
  /** The fewest theses of a source for its threshold of theses in another language to be checked. */
  minTheses: number
  /** Each source with an attempt in the window, in the API's order of sources; empty without one. */
  sources: SourceQuality[]
}

/** The quality of the assessments over the last `windowDays` days up to today, per source (admin only). */
export function fetchAssessmentQuality(): Promise<QualityWindow> {
  return apiFetch<QualityWindow>('/api/admin/assessment-quality')
}
