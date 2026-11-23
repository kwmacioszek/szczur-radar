<script setup lang="ts">
import { type ShallowRef, computed, nextTick, onMounted, ref, useTemplateRef } from 'vue'
import {
  type MissingDays,
  RUNS_PAGE_SIZE,
  type Run,
  type SourceState,
  fetchMissingDays,
  fetchRuns,
  fetchSources,
  retryPosts,
  startErrorMessage,
  startRange,
} from '../api/pipeline'
import { type FailedPost, MAX_PAGE_SIZE, fetchFailedPosts, restorePosts } from '../api/posts'
import {
  type LanguageShare,
  type QualityStatus,
  type QualityWindow,
  type SourceQuality,
  fetchAssessmentQuality,
} from '../api/quality'
import AppButton from '../components/AppButton.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusBadge, { type BadgeTone } from '../components/StatusBadge.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { formatDate, formatDateTime, formatDuration, label, plural } from '../labels'
import { type MissingRange, missingRanges } from '../missingDays'
import { useEvery } from '../polling'
import { isActive, requester, runDuration, runRange, runStatusLabel, runTone, stepTone } from '../runs'

/** Yesterday in Warsaw, the usual day to run. */
function yesterday(): string {
  const date = new Date(Date.now() - 24 * 60 * 60 * 1000)
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Warsaw' }).format(date)
}

/**
 * Every section reads its own data and has its own states (the shared ones): a skeleton while it loads with nothing on
 * screen, an error with its own "Spróbuj ponownie", an empty list. A section's error stays on screen, its button busy,
 * until the answer of a new read (`xRetrying`), whoever started it. `by` says who started a read: `retry` is that
 * button, `action` another control the user pressed ("Odśwież", "Uruchom", "Ponów zaznaczone", "Przywróć martwe"),
 * none a read nobody pressed for (mount, a run that ended, the poll).
 */
type Read = { by?: 'retry' | 'action' }

const startHeading = useTemplateRef<HTMLElement>('start-heading')
const missingHeading = useTemplateRef<HTMLElement>('missing-heading')
const qualityHeading = useTemplateRef<HTMLElement>('quality-heading')
const runsHeading = useTemplateRef<HTMLElement>('runs-heading')
const failedHeading = useTemplateRef<HTMLElement>('failed-heading')
const sourcesErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('sources-error-state')
const missingErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('missing-error-state')
const qualityErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('quality-error-state')
const runsErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('runs-error-state')
const failedErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('failed-error-state')

/**
 * The focus a section's own retry owes: its busy button lost the focus, so the latest read of the section to settle
 * puts it on the heading (`tabindex="-1"`) after a success, or back on "Spróbuj ponownie" after another failure, also
 * when a read nobody pressed for superseded the retry. A read from another control leaves the focus on that control.
 */
function sectionFocus(
  heading: Readonly<ShallowRef<HTMLElement | null>>,
  errorState: Readonly<ShallowRef<{ focus(): void } | null>>,
) {
  let owed = false
  return {
    claim({ by }: Read): void {
      if (by === 'retry') owed = true
      else if (by === 'action') owed = false
    },
    async settle(ok: boolean): Promise<void> {
      if (!owed) return
      owed = false
      await nextTick()
      if (ok) heading.value?.focus()
      else errorState.value?.focus()
    },
  }
}

const sourcesFocus = sectionFocus(startHeading, sourcesErrorState)
const missingFocus = sectionFocus(missingHeading, missingErrorState)
const qualityFocus = sectionFocus(qualityHeading, qualityErrorState)
const runsFocus = sectionFocus(runsHeading, runsErrorState)
const failedFocus = sectionFocus(failedHeading, failedErrorState)

// --- start a range run ------------------------------------------------------------------
const from = ref(yesterday())
const to = ref(yesterday())
const sources = ref<SourceState[]>([])
/** True until the first answer about the sources, unless an error is on screen. */
const sourcesLoading = ref(true)
const sourcesError = ref(false)
/** A new read runs while the error is on screen. */
const sourcesRetrying = ref(false)
/** The number of the latest read; the answer or error of any earlier one is dropped. */
let latestSources = 0
const chosenSources = ref<string[]>([])
const starting = ref(false)
const startMessage = ref<string | null>(null)
const startError = ref<string | null>(null)

/** Why "Uruchom" is disabled, once the sources are known; null when it is not. */
const startHint = computed(() => {
  if (sourcesLoading.value || sourcesError.value) return null
  if (!sources.value.some((source) => source.configured)) return 'Żadne źródło nie jest skonfigurowane na serwerze.'
  if (chosenSources.value.length === 0) return 'Wybierz co najmniej jedno źródło.'
  return null
})

// Loaded until the first success (on mount, "Spróbuj ponownie", "Odśwież" after an error), so the
// configured sources are checked by default once and the admin's later choice is kept.
async function loadSources(read: Read = {}): Promise<void> {
  const current = ++latestSources
  sourcesFocus.claim(read)
  sourcesRetrying.value = sourcesError.value
  sourcesLoading.value = !sourcesError.value
  let ok = false
  try {
    const answer = await fetchSources()
    if (current !== latestSources) return
    sources.value = answer
    chosenSources.value = answer.filter((s) => s.configured).map((s) => s.source)
    sourcesError.value = false
    ok = true
  } catch {
    if (current !== latestSources) return
    sourcesError.value = true
  } finally {
    if (current === latestSources) {
      sourcesLoading.value = false
      sourcesRetrying.value = false
      await sourcesFocus.settle(ok)
    }
  }
}

async function start(): Promise<void> {
  if (starting.value) return
  starting.value = true
  startMessage.value = null
  startError.value = null
  try {
    const runs = await startRange(from.value, to.value, chosenSources.value)
    startMessage.value = `Uruchomiono: ${runs.map((run) => label('runKind', run.kind)).join(', ')}.`
    await loadRuns({ by: 'action' })
  } catch (err) {
    startError.value = startErrorMessage(err)
  } finally {
    starting.value = false
  }
}

// --- days without data ------------------------------------------------------------------
const fromInput = useTemplateRef<HTMLInputElement>('from-input')
const missing = ref<MissingDays | null>(null)
/** Loading without an answer yet (and no error on screen); a reload keeps the ranges on screen. */
const missingLoading = ref(true)
const missingError = ref(false)
const missingRetrying = ref(false)
/** The number of the latest read; the answer or error of any earlier one is dropped. */
let latestMissing = 0
const ranges = computed(() => (missing.value ? missingRanges(missing.value.days) : []))

// Read on mount, with "Odśwież" and after a run of the list stops being active: then the coverage may have changed.
async function loadMissing(read: Read = {}): Promise<void> {
  const current = ++latestMissing
  missingFocus.claim(read)
  missingRetrying.value = missingError.value
  missingLoading.value = missing.value === null && !missingError.value
  let ok = false
  try {
    const answer = await fetchMissingDays()
    if (current !== latestMissing) return
    missing.value = answer
    missingError.value = false
    ok = true
  } catch {
    if (current !== latestMissing) return
    missingError.value = true
  } finally {
    if (current === latestMissing) {
      missingLoading.value = false
      missingRetrying.value = false
      await missingFocus.settle(ok)
    }
  }
}

/** The days, as a run's range is shown, and the sources; a `YYYY-MM-DD` day is its UTC midnight, that day in Warsaw. */
function rangeText(range: MissingRange): string {
  const days = runRange({ rangeFrom: range.from, rangeTo: range.to })
  return `${days} · ${range.sources.map((source) => label('source', source)).join(', ')}`
}

/** The form of "Nowy przebieg" is on screen: not while its sources load or failed (a state stands in its place). */
const formShown = computed(() => !sourcesLoading.value && !sourcesError.value)

/**
 * Puts the range into "Nowy przebieg" (only configured sources can be checked) and moves the focus to "Od". Without
 * the form there is nothing to fill: the button is disabled, as the range's sources cannot be checked yet.
 */
function fillForm(range: MissingRange): void {
  if (!formShown.value) return
  from.value = range.from
  to.value = range.to
  chosenSources.value = range.sources.filter((source) =>
    sources.value.some((state) => state.source === source && state.configured),
  )
  startMessage.value = null
  startError.value = null
  fromInput.value?.focus()
}

// --- assessment quality -----------------------------------------------------------------
const quality = ref<QualityWindow | null>(null)
/** Loading without an answer yet; a reload keeps the window on screen. */
const qualityLoading = ref(true)
const qualityError = ref(false)
const qualityRetrying = ref(false)
/** The number of the latest read; the answer or error of any earlier one is dropped. */
let latestQuality = 0

// Read on mount, with "Odśwież" and after a run of the list stops being active: then new attempts may count.
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

// --- runs -------------------------------------------------------------------------------
/** How often the list is read again while a run is queued or running. */
const REFRESH_MS = 10 * 1000

const runsSection = useTemplateRef<HTMLElement>('runs-section')
const runs = ref<Run[]>([])
/** All runs in the API, from the latest answer. */
const runsTotal = ref(0)
/** Loading for the first time (and no error on screen); a refresh keeps the runs, or their empty state, on screen. */
const runsLoading = ref(true)
const runsError = ref(false)
const runsRetrying = ref(false)
/** An answer came at least once. */
let runsLoaded = false
const olderLoading = ref(false)
const olderError = ref<string | null>(null)
/** The time durations of running runs count to: the last read. */
const now = ref(Date.now())
/** Pages of RUNS_PAGE_SIZE read so far (the next "Pokaż starsze" reads this page number). */
let pagesLoaded = 0
/** The number of the latest refresh; the answer or error of any earlier one is dropped. */
let latestRefresh = 0
let refreshing = false

const hasOlder = computed(() => runs.value.length < runsTotal.value)

/** `fresh` (the newest runs) first, then the rows read before that it does not hold, in their order. */
function mergeRuns(fresh: Run[], shown: Run[]): Run[] {
  const ids = new Set(fresh.map((run) => run.id))
  return [...fresh, ...shown.filter((run) => !ids.has(run.id))]
}

/**
 * Reads the newest runs again: page 0 as large as the rows on screen (at most MAX_PAGE_SIZE; older rows stay), so
 * the pages read with "Pokaż starsze" do not disappear. Rows are merged by id: a new run shifts the pages.
 */
async function loadRuns(read: Read = {}): Promise<void> {
  const current = ++latestRefresh
  runsFocus.claim(read)
  refreshing = true
  runsRetrying.value = runsError.value
  runsLoading.value = !runsLoaded && !runsError.value
  let ok = false
  try {
    const page = await fetchRuns(0, Math.min(Math.max(runs.value.length, RUNS_PAGE_SIZE), MAX_PAGE_SIZE))
    if (current !== latestRefresh) return
    const wasActive = new Set(runs.value.filter(isActive).map((run) => run.id))
    runs.value = mergeRuns(page.items, runs.value)
    // A run that ended may have covered days without data and stored new assessment attempts.
    if (runs.value.some((run) => wasActive.has(run.id) && !isActive(run))) {
      void loadMissing()
      void loadQuality()
    }
    runsTotal.value = page.total
    pagesLoaded = Math.max(pagesLoaded, 1)
    now.value = Date.now()
    runsLoaded = true
    runsError.value = false
    ok = true
  } catch {
    if (current !== latestRefresh) return
    runsError.value = true
  } finally {
    if (current === latestRefresh) {
      runsLoading.value = false
      runsRetrying.value = false
      refreshing = false
      await runsFocus.settle(ok)
    }
  }
}

/** The next page of older runs, appended without the rows already shown. */
async function loadOlder(): Promise<void> {
  if (olderLoading.value) return
  olderLoading.value = true
  olderError.value = null
  let firstAdded: string | null = null
  try {
    const page = await fetchRuns(pagesLoaded, RUNS_PAGE_SIZE)
    const ids = new Set(runs.value.map((run) => run.id))
    const added = page.items.filter((run) => !ids.has(run.id))
    runs.value = [...runs.value, ...added]
    firstAdded = added[0]?.id ?? null
    // An empty page means nothing is left, whatever the total said.
    runsTotal.value = page.items.length === 0 ? runs.value.length : page.total
    pagesLoaded++
  } catch {
    olderError.value = 'Nie udało się wczytać starszych przebiegów.'
  } finally {
    olderLoading.value = false
  }
  // Once everything is read the button goes away; the focus goes to the first row it added.
  if (!hasOlder.value && firstAdded !== null) {
    await nextTick()
    const at = runs.value.findIndex((run) => run.id === firstAdded)
    runsSection.value?.querySelectorAll<HTMLElement>('.run .details')[at]?.focus()
  }
}

// While the section shows its error there is nothing to refresh: "Spróbuj ponownie" or "Odśwież" reads it again.
useEvery(REFRESH_MS, () => {
  if (!refreshing && !runsError.value && runs.value.some(isActive)) void loadRuns()
})

function duration(run: Run): string | null {
  const ms = runDuration(run, now.value)
  return ms === null ? null : formatDuration(ms)
}

function counters(run: Run): string {
  return [
    `pobrane ${run.fetched}`,
    `nowe ${run.newPosts}`,
    `ocenione ${run.assessedOk}`,
    `nieudane ${run.assessedFailed}`,
    `martwe ${run.dead}`,
    `pominięte ${run.skipped}`,
  ].join(' · ')
}

// --- failed posts -----------------------------------------------------------------------
const failed = ref<FailedPost[]>([])
/** Loading for the first time (and no error on screen); a refresh keeps the list, or its empty state, on screen. */
const failedLoading = ref(true)
const failedError = ref(false)
const failedRetrying = ref(false)
let failedLoaded = false
/** The number of the latest read; the answer or error of any earlier one is dropped. */
let latestFailed = 0
/** One selection of the list, of both kinds: the dead posts in it are restored, the others retried. */
const selected = ref<string[]>([])
const retryingSelected = ref(false)
const retryMessage = ref<string | null>(null)
const retryError = ref<string | null>(null)
const restoring = ref(false)
const restoreMessage = ref<string | null>(null)
const restoreError = ref<string | null>(null)
const stageOf = computed(() => new Map(failed.value.map((post) => [post.id, post.stage])))
/** The checked failed posts and posts on hold: "Ponów zaznaczone" runs them. */
const selectedRetryable = computed(() =>
  selected.value.filter((id) => stageOf.value.has(id) && stageOf.value.get(id) !== 'dead'),
)
/** The checked dead posts: no run takes them, "Przywróć martwe" gives them back to assessment. */
const selectedDead = computed(() => selected.value.filter((id) => stageOf.value.get(id) === 'dead'))

async function loadFailed(read: Read = {}): Promise<void> {
  const current = ++latestFailed
  failedFocus.claim(read)
  failedRetrying.value = failedError.value
  failedLoading.value = !failedLoaded && !failedError.value
  let ok = false
  try {
    const answer = (await fetchFailedPosts()).items
    if (current !== latestFailed) return
    failed.value = answer
    selected.value = selected.value.filter((id) => stageOf.value.has(id))
    failedLoaded = true
    failedError.value = false
    ok = true
  } catch {
    if (current !== latestFailed) return
    failedError.value = true
  } finally {
    if (current === latestFailed) {
      failedLoading.value = false
      failedRetrying.value = false
      await failedFocus.settle(ok)
    }
  }
}

/** Only the outcome of the latest action on the list stays on screen. */
function clearFailedMessages(): void {
  retryMessage.value = null
  retryError.value = null
  restoreMessage.value = null
  restoreError.value = null
}

async function retrySelected(): Promise<void> {
  const postIds = selectedRetryable.value
  if (retryingSelected.value || restoring.value || postIds.length === 0) return
  retryingSelected.value = true
  clearFailedMessages()
  try {
    await retryPosts(postIds)
    retryMessage.value = `Ponowienie ${postIds.length} post(ów) w kolejce.`
    selected.value = selected.value.filter((id) => !postIds.includes(id))
    await loadRuns({ by: 'action' })
  } catch (err) {
    retryError.value = startErrorMessage(err)
  } finally {
    retryingSelected.value = false
  }
}

async function restore(): Promise<void> {
  const postIds = selectedDead.value
  if (restoring.value || retryingSelected.value || postIds.length === 0) return
  restoring.value = true
  clearFailedMessages()
  try {
    const { restored } = await restorePosts(postIds)
    restoreMessage.value =
      restored === 0
        ? 'Żaden z zaznaczonych postów nie był już martwy.'
        : `Przywrócono ${restored} post(ów) do oceny. Oceni je kolejny przebieg.`
    selected.value = selected.value.filter((id) => !postIds.includes(id))
    // A restored post is no longer on the list.
    await loadFailed({ by: 'action' })
  } catch {
    restoreError.value = 'Nie udało się przywrócić postów.'
  } finally {
    restoring.value = false
  }
}

function excerpt(post: FailedPost): string {
  const text = post.title ?? post.body ?? ''
  return text.length > 120 ? `${text.slice(0, 120)}…` : text
}

async function refresh(): Promise<void> {
  const read: Read = { by: 'action' }
  await Promise.all([
    loadRuns(read),
    loadFailed(read),
    loadMissing(read),
    loadQuality(read),
    sourcesError.value ? loadSources(read) : undefined,
  ])
}

onMounted(async () => {
  await Promise.all([loadSources(), loadMissing(), loadQuality(), loadRuns(), loadFailed()])
})
</script>

<template>
  <div class="admin">
    <PageHeader class="head" title="Panel admina" />

    <section aria-labelledby="start-title" :aria-busy="sourcesLoading || sourcesRetrying ? 'true' : undefined">
      <h2 id="start-title" ref="start-heading" tabindex="-1">Nowy przebieg</h2>
      <LoadingState v-if="sourcesLoading" class="section-state" shape="form" />
      <ErrorState
        v-else-if="sourcesError"
        ref="sources-error-state"
        class="section-state"
        title="Nie udało się wczytać listy źródeł"
        :retrying="sourcesRetrying"
        @retry="loadSources({ by: 'retry' })"
      >
        Bez listy źródeł nie da się uruchomić przebiegu.
      </ErrorState>
      <template v-else>
        <form class="start" @submit.prevent="start">
          <label>Od <input ref="from-input" v-model="from" type="date" required /></label>
          <label>Do <input v-model="to" type="date" required /></label>
          <fieldset class="sources">
            <legend>Źródła</legend>
            <label v-for="source in sources" :key="source.source">
              <input
                v-model="chosenSources"
                type="checkbox"
                :value="source.source"
                :disabled="!source.configured"
              />
              {{ label('source', source.source) }}<template v-if="!source.configured"> (nieskonfigurowane)</template>
            </label>
          </fieldset>
          <AppButton
            type="submit"
            variant="primary"
            :disabled="chosenSources.length === 0"
            :busy="starting"
            busy-label="Uruchamianie…"
            :aria-describedby="startHint ? 'start-hint' : undefined"
          >
            Uruchom
          </AppButton>
        </form>
        <p v-if="startHint" id="start-hint" class="hint">{{ startHint }}</p>
      </template>
      <StatusMessage v-if="startMessage" tone="ok" class="message">{{ startMessage }}</StatusMessage>
      <StatusMessage v-if="startError" tone="error" class="message">{{ startError }}</StatusMessage>
    </section>

    <section aria-labelledby="missing-title" :aria-busy="missingLoading || missingRetrying ? 'true' : undefined">
      <h2 id="missing-title" ref="missing-heading" tabindex="-1">Dni bez danych</h2>
      <LoadingState v-if="missingLoading" class="section-state" shape="lines" />
      <ErrorState
        v-else-if="missingError"
        ref="missing-error-state"
        class="section-state"
        title="Nie udało się wczytać dni bez danych"
        :retrying="missingRetrying"
        @retry="loadMissing({ by: 'retry' })"
      >
        Pozostałe sekcje działają osobno.
      </ErrorState>
      <template v-else-if="missing">
        <EmptyState v-if="ranges.length === 0" class="section-state" icon="i-circle-check" title="Brak dni bez danych">
          Każdy dzień od {{ formatDate(missing.from) }} do {{ formatDate(missing.to) }} ma dane.
        </EmptyState>
        <template v-else>
          <ul class="missing-ranges">
            <li v-for="range in ranges" :key="range.from" class="missing-range">
              <span class="missing-days">{{ rangeText(range) }}</span>
              <AppButton
                :aria-label="`Wpisz do formularza: ${rangeText(range)}`"
                :disabled="!formShown"
                @click="fillForm(range)"
              >
                Wpisz do formularza
              </AppButton>
            </li>
          </ul>
          <p class="hint">
            Tuż po północy (czasu polskiego) przedwczorajszy dzień może tu być przez 2–3 godziny, zanim pobierze go nocny przebieg.
          </p>
        </template>
      </template>
    </section>

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

    <section ref="runs-section" aria-labelledby="runs-title" :aria-busy="runsLoading || runsRetrying ? 'true' : undefined">
      <div class="section-head">
        <h2 id="runs-title" ref="runs-heading" tabindex="-1">Ostatnie przebiegi</h2>
        <AppButton :disabled="runsLoading" @click="refresh">Odśwież</AppButton>
      </div>
      <LoadingState v-if="runsLoading" class="section-state" shape="rows" />
      <ErrorState
        v-else-if="runsError"
        ref="runs-error-state"
        class="section-state"
        title="Nie udało się wczytać przebiegów"
        :retrying="runsRetrying"
        @retry="loadRuns({ by: 'retry' })"
      >
        Pozostałe sekcje działają osobno.
      </ErrorState>
      <EmptyState v-else-if="runs.length === 0" class="section-state" icon="i-inbox" title="Jeszcze nie było przebiegu">
        Pierwszy uruchomisz w sekcji „Nowy przebieg”.
      </EmptyState>
      <template v-else>
        <ul class="runs">
          <li v-for="run in runs" :key="run.id" class="run" :data-status="run.status">
            <p class="run-head">
              <strong>{{ label('runKind', run.kind) }}</strong>
              <StatusBadge class="status" :tone="runTone(run)">{{ runStatusLabel(run) }}</StatusBadge>
              <StatusBadge v-if="run.needsAttention" class="attention" tone="destructive">wymaga uwagi</StatusBadge>
              <span>{{ formatDateTime(run.createdAt) }}</span>
              <span v-if="run.sources.length">{{ run.sources.map((s) => label('source', s)).join(', ') }}</span>
              <RouterLink
                class="details"
                :to="{ name: 'admin-run', params: { id: run.id } }"
                :aria-label="`Szczegóły przebiegu z ${formatDateTime(run.createdAt)}`"
              >
                Szczegóły
              </RouterLink>
            </p>
            <p class="run-meta">
              <span v-if="runRange(run)" class="range">zakres {{ runRange(run) }}</span>
              <span class="requester">uruchomił {{ requester(run) }}</span>
              <span v-if="duration(run)" class="duration">czas {{ duration(run) }}</span>
            </p>
            <p class="counters">
              {{ counters(run) }}
              · <span :class="{ alarm: run.processingErrors > 0 }">błędy techniczne {{ run.processingErrors }}</span>
            </p>
            <p v-if="run.steps.length" class="steps">
              <template v-for="step in run.steps" :key="step.step + step.startedAt">
                <span v-if="step.status === 'succeeded'">
                  {{ label('step', step.step) }}<template v-if="step.itemsOut !== null"> ({{ step.itemsOut }})</template>
                </span>
                <StatusBadge v-else :tone="stepTone(step.status)">
                  {{ label('step', step.step) }} ({{ label('stepStatus', step.status) }})
                </StatusBadge>
              </template>
            </p>
            <p v-if="run.error" class="run-error">{{ run.error }}</p>
          </li>
        </ul>
        <AppButton v-if="hasOlder" class="older" :busy="olderLoading" busy-label="Wczytywanie…" @click="loadOlder">
          Pokaż starsze
        </AppButton>
        <StatusMessage v-if="olderError" tone="error" class="message">{{ olderError }}</StatusMessage>
      </template>
    </section>

    <section aria-labelledby="failed-title" :aria-busy="failedLoading || failedRetrying ? 'true' : undefined">
      <h2 id="failed-title" ref="failed-heading" tabindex="-1">Nieudane posty</h2>
      <LoadingState v-if="failedLoading" class="section-state" shape="rows" />
      <ErrorState
        v-else-if="failedError"
        ref="failed-error-state"
        class="section-state"
        title="Nie udało się wczytać nieudanych postów"
        :retrying="failedRetrying"
        @retry="loadFailed({ by: 'retry' })"
      >
        Pozostałe sekcje działają osobno.
      </ErrorState>
      <EmptyState v-else-if="failed.length === 0" class="section-state" icon="i-circle-check" title="Brak nieudanych postów">
        Żaden post nie czeka na ponowienie ani nie jest martwy.
      </EmptyState>
      <template v-else>
        <ul class="failed-posts">
          <li v-for="post in failed" :key="post.id">
            <label>
              <input v-model="selected" type="checkbox" :value="post.id" />
              <span class="excerpt">{{ excerpt(post) }}</span>
            </label>
            <span class="attempt">
              <template v-if="post.stage === 'dead'"><strong>martwy</strong> · </template>
              <template v-else-if="post.onHold"><strong>wstrzymany</strong> · </template>
              <!-- A stored post on hold has no stored attempt since it was stored (a restored one keeps
                   its older lastAttempt, which is not shown): only its technical errors. -->
              <template v-if="post.stage !== 'stored'">
                próby {{ post.assessmentAttempts }}
                <template v-if="post.lastAttempt?.failureClass">
                  · {{ label('failureClass', post.lastAttempt.failureClass) }}
                </template>
              </template>
              <template v-if="post.stage !== 'dead' && post.technicalFailures > 0">
                <template v-if="post.stage !== 'stored'"> · </template>
                <span class="alarm">błędy techniczne {{ post.technicalFailures }}</span>
                <template v-if="post.lastTechnicalError">
                  · <span class="technical-error" :title="post.lastTechnicalError">{{ post.lastTechnicalError }}</span>
                </template>
              </template>
            </span>
          </li>
        </ul>
        <div class="failed-actions">
          <AppButton
            :disabled="selectedRetryable.length === 0 || restoring"
            :busy="retryingSelected"
            busy-label="Ponawianie…"
            @click="retrySelected"
          >
            Ponów zaznaczone ({{ selectedRetryable.length }})
          </AppButton>
          <AppButton
            :disabled="selectedDead.length === 0 || retryingSelected"
            :busy="restoring"
            busy-label="Przywracanie…"
            @click="restore"
          >
            Przywróć martwe ({{ selectedDead.length }})
          </AppButton>
        </div>
      </template>
      <StatusMessage v-if="retryMessage" tone="ok" class="message">{{ retryMessage }}</StatusMessage>
      <StatusMessage v-if="retryError" tone="error" class="message">{{ retryError }}</StatusMessage>
      <StatusMessage v-if="restoreMessage" tone="ok" class="message">{{ restoreMessage }}</StatusMessage>
      <StatusMessage v-if="restoreError" tone="error" class="message">{{ restoreError }}</StatusMessage>
    </section>
  </div>
</template>

<style scoped>
.head {
  margin-bottom: var(--space-4);
}

h2 {
  margin: var(--space-5) 0 var(--space-2);
  font-size: var(--font-size-2xl);
  color: var(--text-h);
}

.start {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3) var(--space-4);
}

.sources {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin: 0;
  padding: var(--space-1) var(--space-2);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.hint {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-sm);
}

/* A message standing alone in its section, apart from what is above and below it; the page's selector outranks the
   message's own zero margin. */
.admin .message {
  margin-block: var(--space-3);
}

/* The state of a section (loading, error, empty) stands apart from the heading above it. */
.section-state {
  margin-top: var(--space-4);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.runs,
.failed-posts,
.missing-ranges,
.quality-sources {
  margin: 0;
  padding: 0;
  list-style: none;
}

.run,
.failed-posts li,
.missing-range,
.quality-source {
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--line);
}

.missing-range {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
}

.run p,
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

.run-head,
.run-meta,
.steps {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-3);
}

.alarm,
.run-error {
  color: var(--destructive);
}

.run-meta,
.counters,
.steps,
.attempt,
.quality-failed,
.quality-classes,
.quality-language {
  font-size: var(--font-size-sm);
}

.older {
  margin-top: var(--space-3);
}

.run-error {
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.failed-posts li {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
}

.excerpt {
  overflow-wrap: anywhere;
}

.failed-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-3);
}

/* Never wider than the row, so a long technical error is cut below instead of pushing the layout. */
.attempt {
  min-width: 0;
  max-width: 100%;
}

/* One line, cut with an ellipsis; the full text is in its title (and in the text, for a screen reader). */
.technical-error {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
</style>
