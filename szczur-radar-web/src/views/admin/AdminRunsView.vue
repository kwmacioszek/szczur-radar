<script setup lang="ts">
// The part "Przebiegi" of the admin panel (/admin): starting a run, the days without data and the latest runs. The
// three sections read and fail on their own; "Odśwież" reads them together, a started run reads the runs again and a
// run that ends reads the days again (it may have covered some).
import { computed, nextTick, onMounted, ref, useTemplateRef } from 'vue'
import {
  type MissingDays,
  RUNS_PAGE_SIZE,
  type Run,
  type SourceState,
  fetchMissingDays,
  fetchRuns,
  fetchSources,
  startErrorMessage,
  startRange,
} from '../../api/pipeline'
import { MAX_PAGE_SIZE } from '../../api/posts'
import AppButton from '../../components/AppButton.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import LoadingState from '../../components/LoadingState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import StatusMessage from '../../components/StatusMessage.vue'
import { formatDate, formatDateTime, formatDuration, label } from '../../labels'
import { type MissingRange, missingRanges } from '../../missingDays'
import { useEvery } from '../../polling'
import { isActive, requester, runDuration, runRange, runStatusLabel, runTone, stepTone } from '../../runs'
import { useAdminPanel } from './adminPanel'
import { type Read, sectionFocus } from './sectionState'

const panel = useAdminPanel()

/** Yesterday in Warsaw, the usual day to run. */
function yesterday(): string {
  const date = new Date(Date.now() - 24 * 60 * 60 * 1000)
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Warsaw' }).format(date)
}

const startHeading = useTemplateRef<HTMLElement>('start-heading')
const missingHeading = useTemplateRef<HTMLElement>('missing-heading')
const runsHeading = useTemplateRef<HTMLElement>('runs-heading')
const sourcesErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('sources-error-state')
const missingErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('missing-error-state')
const runsErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('runs-error-state')

const sourcesFocus = sectionFocus(startHeading, sourcesErrorState)
const missingFocus = sectionFocus(missingHeading, missingErrorState)
const runsFocus = sectionFocus(runsHeading, runsErrorState)

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
      panel.sectionLoaded()
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
      panel.sectionLoaded()
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
    // A run that ended may have covered days without data. The assessment quality is read anew when its part opens.
    if (runs.value.some((run) => wasActive.has(run.id) && !isActive(run))) {
      void loadMissing()
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
      panel.sectionLoaded()
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

async function refresh(): Promise<void> {
  const read: Read = { by: 'action' }
  await Promise.all([loadRuns(read), loadMissing(read), sourcesError.value ? loadSources(read) : undefined])
}

onMounted(async () => {
  await Promise.all([loadSources(), loadMissing(), loadRuns()])
})
</script>

<template>
  <div class="admin-part">
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
  </div>
</template>

<style scoped>
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

/* A message standing alone in its section, apart from what is above and below it; the part's selector outranks the
   message's own zero margin. */
.admin-part .message {
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
.missing-ranges {
  margin: 0;
  padding: 0;
  list-style: none;
}

.run,
.missing-range {
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

.run p {
  margin: var(--space-0) 0;
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
.steps {
  font-size: var(--font-size-sm);
}

.older {
  margin-top: var(--space-3);
}

.run-error {
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}
</style>
