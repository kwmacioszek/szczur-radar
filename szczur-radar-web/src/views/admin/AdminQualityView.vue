<script setup lang="ts">
// The part "Jakość ocen" of the admin panel (/admin/quality): both quality measures per source over the last days, a
// card per source (ui-design/prototypes/screens/admin-quality.html). It is read on every entry into the part, so a run
// that ended meanwhile counts.
import { computed, onMounted, ref, useTemplateRef } from 'vue'
import {
  type LanguageShare,
  type QualityStatus,
  type QualityWindow,
  type SourceQuality,
  fetchAssessmentQuality,
} from '../../api/quality'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import LoadingState from '../../components/LoadingState.vue'
import StatusBadge, { type BadgeTone } from '../../components/StatusBadge.vue'
import { formatCount, label, plural } from '../../labels'
import { runRange } from '../../runs'
import { useAdminPanel } from './adminPanel'
import { type Read, sectionFocus } from './sectionState'

const panel = useAdminPanel()

const qualityHeading = useTemplateRef<HTMLElement>('quality-heading')
const qualityErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('quality-error-state')
const qualityFocus = sectionFocus(qualityHeading, qualityErrorState)

const quality = ref<QualityWindow | null>(null)
/** Loading without an answer yet; a reload keeps the window on screen. */
const qualityLoading = ref(true)
const qualityError = ref(false)
const qualityRetrying = ref(false)
/** The number of the latest read; the answer or error of any earlier one is dropped. */
let latestQuality = 0

async function loadQuality(read: Read = {}): Promise<void> {
  const current = ++latestQuality
  qualityFocus.claim(read)
  qualityRetrying.value = qualityError.value
  qualityLoading.value = quality.value === null && !qualityError.value
  let ok = false
  try {
    const answer = await fetchAssessmentQuality()
    if (current !== latestQuality) return
    quality.value = answer
    qualityError.value = false
    ok = true
  } catch {
    if (current !== latestQuality) return
    qualityError.value = true
  } finally {
    if (current === latestQuality) {
      qualityLoading.value = false
      qualityRetrying.value = false
      await qualityFocus.settle(ok)
      panel.sectionLoaded()
    }
  }
}

/** The state is the API's; the colour only reinforces its text. */
const QUALITY_TONES: Record<QualityStatus, BadgeTone> = {
  ok: 'success',
  above_threshold: 'destructive',
  not_enough_data: 'neutral',
}

function qualityTone(status: QualityStatus): BadgeTone {
  return QUALITY_TONES[status] ?? 'neutral'
}

/** The window's days, as a run's range is shown; a `YYYY-MM-DD` day is its UTC midnight, that day in Warsaw. */
const windowText = computed(() =>
  quality.value ? runRange({ rangeFrom: quality.value.from, rangeTo: quality.value.to }) : null,
)

// Only for display: the state against the threshold comes from the API, never from these numbers.
const share = new Intl.NumberFormat('pl-PL', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 })
const threshold = new Intl.NumberFormat('pl-PL', { style: 'percent', maximumFractionDigits: 0 })

/**
 * The bar's scale is 1.5 × the threshold, so the threshold's mark stands at two thirds of the bar (its place in the
 * style) and a share above it still shows how far.
 */
const BAR_SCALE = 1.5

/** One measure of a source against its threshold, as its card shows it. */
interface Measure {
  name: string
  /** The share, e.g. `6,1%`; null without anything to count (shown as a dash). */
  value: string | null
  /** E.g. `191 z 3134 prób · próg 5%`. */
  counts: string
  status: QualityStatus
  /** The bar's fill in percent of its width. */
  fill: number
}

function measure(name: string, part: number, whole: number, nouns: [string, string], thresholdPercent: number, status: QualityStatus): Measure {
  const limit = `próg ${threshold.format(thresholdPercent / 100)}`
  if (whole === 0) {
    return { name, value: null, counts: `0 ${nouns[1]} · ${limit}`, status, fill: 0 }
  }
  const percent = (part / whole) * 100
  const scale = thresholdPercent * BAR_SCALE
  const fill = scale > 0 ? Math.min(100, (percent / scale) * 100) : percent > 0 ? 100 : 0
  const noun = plural(whole, nouns[0], nouns[1], nouns[1])
  return {
    name,
    value: share.format(part / whole),
    counts: `${formatCount(part)} z ${formatCount(whole)} ${noun} · ${limit}`,
    status,
    fill: Math.round(fill * 10) / 10,
  }
}

const FAILED_MEASURE = 'Nieudane próby oceny'
const LANGUAGE_MEASURE = 'Tezy w innym języku niż post'

function failedMeasure(row: SourceQuality): Measure {
  return measure(FAILED_MEASURE, row.failed, row.attempts, ['próby', 'prób'], row.thresholdPercent, row.status)
}

function languageMeasure(language: LanguageShare): Measure {
  return measure(LANGUAGE_MEASURE, language.otherLanguage, language.theses, ['tezy', 'tez'], language.thresholdPercent, language.status)
}

/** The measure in words, the name of its bar: e.g. `Nieudane próby oceny: 6,1%, 191 z 3134 prób · próg 5%`. */
function measureText(shown: Measure): string {
  return shown.value === null ? `${shown.name}: ${shown.counts}` : `${shown.name}: ${shown.value}, ${shown.counts}`
}

/** The look of a measure: its value and bar in the destructive tone above the threshold, a muted bar without data. */
function measureClass(status: QualityStatus): string | undefined {
  if (status === 'above_threshold') return 'measure-over'
  if (status === 'not_enough_data') return 'measure-idle'
  return undefined
}

/** E.g. `niezgodność ze schematem 2,0% · zmyślony cytat 3,0%`, in the API's order of classes, of the attempts. */
function classesText(row: SourceQuality): string {
  return row.classes.map((item) => `${label('failureClass', item.failureClass)} ${share.format(item.count / row.attempts)}`).join(' · ')
}

/** Either measure above its threshold puts the source above it; else the state of its failed attempts. */
function sourceStatus(row: SourceQuality): QualityStatus {
  return row.status === 'above_threshold' || row.language?.status === 'above_threshold' ? 'above_threshold' : row.status
}

onMounted(async () => {
  await loadQuality()
})
</script>

<template>
  <div class="admin-part">
    <section
      class="part-section"
      aria-labelledby="quality-title"
      :aria-busy="qualityLoading || qualityRetrying ? 'true' : undefined"
    >
      <div class="section-head">
        <h2 id="quality-title" ref="quality-heading" class="section-title" tabindex="-1">Jakość ocen (7 dni)</h2>
        <span v-if="windowText" class="note quality-window">{{ windowText }}</span>
      </div>
      <LoadingState v-if="qualityLoading" class="section-state" shape="lines" />
      <ErrorState
        v-else-if="qualityError"
        ref="quality-error-state"
        class="section-state"
        title="Nie udało się wczytać jakości ocen"
        :retrying="qualityRetrying"
        @retry="loadQuality({ by: 'retry' })"
      >
        Pozostałe sekcje działają osobno.
      </ErrorState>
      <template v-else-if="quality">
        <EmptyState
          v-if="quality.sources.length === 0"
          class="section-state"
          icon="i-inbox"
          :title="`Brak ocen w ostatnich ${quality.windowDays} dniach`"
        >
          Miary pojawią się po ocenach z kolejnego przebiegu.
        </EmptyState>
        <template v-else>
          <div class="cols-2 section-state">
            <section
              v-for="row in quality.sources"
              :key="row.source"
              class="card quality-source"
              :aria-labelledby="`quality-${row.source}`"
              :data-status="sourceStatus(row)"
            >
              <div class="card-head">
                <h3 :id="`quality-${row.source}`" class="card-title">{{ label('source', row.source) }}</h3>
                <StatusBadge
                  class="source-status"
                  :tone="qualityTone(sourceStatus(row))"
                  :icon="sourceStatus(row) === 'above_threshold'"
                >
                  {{ label('qualityStatus', sourceStatus(row)) }}
                </StatusBadge>
              </div>
              <div class="measures">
                <div
                  v-for="shown in [failedMeasure(row), ...(row.language ? [languageMeasure(row.language)] : [])]"
                  :key="shown.name"
                  class="measure"
                  :class="[measureClass(shown.status), shown.name === FAILED_MEASURE ? 'quality-failed' : 'quality-language']"
                  :data-status="shown.status"
                >
                  <span class="measure-name">{{ shown.name }}</span>
                  <span class="measure-value">{{ shown.value ?? '–' }}</span>
                  <span class="measure-meta">
                    <span class="measure-counts">{{ shown.counts }}</span>
                    <StatusBadge :tone="qualityTone(shown.status)">{{ label('qualityStatus', shown.status) }}</StatusBadge>
                  </span>
                  <div class="meter" role="img" :aria-label="measureText(shown)">
                    <span class="meter-fill" :style="{ width: `${shown.fill}%` }"></span>
                    <span class="meter-limit"></span>
                  </div>
                  <span v-if="shown.name === FAILED_MEASURE && row.classes.length" class="measure-note quality-classes">
                    {{ classesText(row) }}
                  </span>
                </div>
                <p v-if="!row.language" class="measure quality-language">{{ LANGUAGE_MEASURE }}: nie udało się policzyć</p>
              </div>
            </section>
          </div>
          <p class="note quality-note">
            Próg jest sprawdzany od {{ formatCount(quality.minAttempts) }}
            {{ plural(quality.minAttempts, 'próby', 'prób', 'prób') }} i od {{ formatCount(quality.minTheses) }}
            {{ plural(quality.minTheses, 'tezy', 'tez', 'tez') }}. Kreska na pasku to próg.
          </p>
        </template>
      </template>
    </section>
  </div>
</template>

<style scoped>
/* The part starts apart from the bar of the parts (the design's `.section`). */
.part-section {
  margin-top: var(--space-8);
}

.section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
}

.section-title {
  margin: 0;
  font-size: var(--font-size-4xl);
}

.note {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

/* The state of a section (loading, error, empty) and the cards stand apart from the heading above them. */
.section-state {
  margin-top: var(--space-4);
}

/* The sources side by side, one under the other below 900 px. */
.cols-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: var(--space-4);
}

.card {
  min-width: 0;
  padding: var(--space-5);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  background: var(--surface);
}

.card-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
}

.card-title {
  margin: 0;
  font-size: var(--font-size-3xl);
}

.measures {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-4);
}

/* The name and the counts on the left, the value beside them; the bar and the note across the measure. */
.measure {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-1) var(--space-4);
  margin: 0;
  padding-top: var(--space-4);
  border-top: 1px solid var(--line);
}

.measure-name {
  color: var(--text-h);
  font-weight: var(--weight-medium);
}

.measure-value {
  grid-row: span 2;
  color: var(--text-h);
  font-family: var(--font-display);
  font-size: var(--font-size-5xl);
  font-weight: var(--weight-medium);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  font-variant-numeric: tabular-nums;
}

.measure-over .measure-value {
  color: var(--destructive);
}

.measure-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.measure-note {
  grid-column: 1 / -1;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

/* The measure against its threshold: the fill is the share, the mark the threshold; the numbers stand beside it as
   text. */
.meter {
  position: relative;
  grid-column: 1 / -1;
  height: var(--bar-height);
  margin-top: var(--space-1);
  border-radius: var(--radius-full);
  background: var(--line);
}

.meter-fill {
  display: block;
  height: 100%;
  border-radius: var(--radius-full);
  background: var(--success);
}

.measure-over .meter-fill {
  background: var(--destructive);
}

.measure-idle .meter-fill {
  background: var(--border);
}

/* The threshold on a scale of 1.5 × the threshold (BAR_SCALE): two thirds of the bar. */
.meter-limit {
  position: absolute;
  top: calc(-1 * var(--space-1));
  bottom: calc(-1 * var(--space-1));
  left: 66.6%;
  width: 2px;
  background: var(--text-h);
}

.quality-note {
  margin: var(--space-4) 0 0;
}

@media (max-width: 899px) {
  .cols-2 {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
