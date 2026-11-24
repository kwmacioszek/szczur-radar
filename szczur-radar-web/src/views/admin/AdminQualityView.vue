<script setup lang="ts">
// The part "Jakość ocen" of the admin panel (/admin/quality): both quality measures per source over the last days.
// It is read on every entry into the part, so a run that ended meanwhile counts.
import { onMounted, ref, useTemplateRef } from 'vue'
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
import { formatDate, label, plural } from '../../labels'
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
  above_threshold: 'warning',
  not_enough_data: 'neutral',
}

function qualityTone(status: QualityStatus): BadgeTone {
  return QUALITY_TONES[status] ?? 'neutral'
}

// Only for display: the state against the threshold comes from the API, never from these numbers.
const share = new Intl.NumberFormat('pl-PL', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 })
const threshold = new Intl.NumberFormat('pl-PL', { style: 'percent', maximumFractionDigits: 0 })

/** `part` of the source's attempts, e.g. `2,6%`. */
function shareOf(row: SourceQuality, part: number): string {
  return share.format(part / row.attempts)
}

/** E.g. `nieudane 5,0% z 200 prób · próg 5%`. */
function failedText(row: SourceQuality): string {
  const attempts = `${row.attempts} ${plural(row.attempts, 'próby', 'prób', 'prób')}`
  return `nieudane ${shareOf(row, row.failed)} z ${attempts} · próg ${threshold.format(row.thresholdPercent / 100)}`
}

const LANGUAGE_MEASURE = 'tezy w innym języku niż post'

/**
 * E.g. `tezy w innym języku niż post 0,3% z 3476 tez · próg 2%`, also below `minTheses` like the failed attempts'
 * row (the badge says the threshold is not checked); without a thesis `tezy w innym języku niż post: 0 tez · próg 30%`.
 */
function languageText(language: LanguageShare): string {
  const limit = `próg ${threshold.format(language.thresholdPercent / 100)}`
  if (language.theses === 0) {
    return `${LANGUAGE_MEASURE}: 0 tez · ${limit}`
  }
  const theses = `${language.theses} ${plural(language.theses, 'tezy', 'tez', 'tez')}`
  return `${LANGUAGE_MEASURE} ${share.format(language.otherLanguage / language.theses)} z ${theses} · ${limit}`
}

/** E.g. `niezgodność ze schematem 2,0% · zmyślony cytat 3,0%`, in the API's order of classes. */
function classesText(row: SourceQuality): string {
  return row.classes.map((item) => `${label('failureClass', item.failureClass)} ${shareOf(row, item.count)}`).join(' · ')
}

onMounted(async () => {
  await loadQuality()
})
</script>

<template>
  <div class="admin-part">
    <section aria-labelledby="quality-title" :aria-busy="qualityLoading || qualityRetrying ? 'true' : undefined">
      <h2 id="quality-title" ref="quality-heading" tabindex="-1">Jakość ocen (7 dni)</h2>
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
        <p class="quality-window">
          Od {{ formatDate(quality.from) }} do {{ formatDate(quality.to) }} · próby z wywołaniem modelu (bez prefiltra), z ponowieniami;
          próg sprawdzany od {{ quality.minAttempts }} prób; tezy udanych ocen, próg sprawdzany od {{ quality.minTheses }} tez
        </p>
        <EmptyState
          v-if="quality.sources.length === 0"
          class="section-state"
          icon="i-inbox"
          :title="`Brak ocen w ostatnich ${quality.windowDays} dniach`"
        >
          Miary pojawią się po ocenach z kolejnego przebiegu.
        </EmptyState>
        <ul v-else class="quality-sources">
          <li v-for="row in quality.sources" :key="row.source" class="quality-source" :data-status="row.status">
            <p class="quality-head">
              <strong>{{ label('source', row.source) }}</strong>
              <StatusBadge :tone="qualityTone(row.status)">{{ label('qualityStatus', row.status) }}</StatusBadge>
            </p>
            <p class="quality-failed">{{ failedText(row) }}</p>
            <p v-if="row.classes.length" class="quality-classes">{{ classesText(row) }}</p>
            <p v-if="row.language" class="quality-language" :data-status="row.language.status">
              <span>{{ languageText(row.language) }}</span>
              <StatusBadge :tone="qualityTone(row.language.status)">{{ label('qualityStatus', row.language.status) }}</StatusBadge>
            </p>
            <p v-else class="quality-language">{{ LANGUAGE_MEASURE }}: nie udało się policzyć</p>
          </li>
        </ul>
      </template>
    </section>
  </div>
</template>

<style scoped>
h2 {
  margin: var(--space-5) 0 var(--space-2);
  font-size: var(--font-size-2xl);
  color: var(--text-h);
}

/* The state of a section (loading, error, empty) stands apart from the heading above it. */
.section-state {
  margin-top: var(--space-4);
}

.quality-sources {
  margin: 0;
  padding: 0;
  list-style: none;
}

.quality-source {
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--line);
}

.quality-source p {
  margin: var(--space-0) 0;
}

.quality-head,
.quality-language {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-3);
}

.quality-window {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-sm);
}

.quality-failed,
.quality-classes,
.quality-language {
  font-size: var(--font-size-sm);
}
</style>
