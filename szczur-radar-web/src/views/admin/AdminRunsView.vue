<script setup lang="ts">
// The part "Przebiegi" of the admin panel (/admin): starting a run, the days without data and the latest runs. The
// three sections read and fail on their own; "Odśwież" reads them together, a started run reads the runs again and a
// run that ends reads the days again (it may have covered some).
import { computed, nextTick, onMounted, ref, useId, useTemplateRef } from 'vue'
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
import DataTable from '../../components/DataTable.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import LoadingState from '../../components/LoadingState.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import StatusMessage from '../../components/StatusMessage.vue'
import { formatCount, formatDate, formatDateTime, formatDuration, label } from '../../labels'
import { type MissingRange, missingRanges } from '../../missingDays'
import { useEvery } from '../../polling'
import { isActive, runDuration, runRange, runStatusLabel, runTone } from '../../runs'
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
const fromId = useId()
const toId = useId()
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

/** The days, as a run's range is shown; a `YYYY-MM-DD` day is its UTC midnight, that day in Warsaw. */
function rangeDays(range: MissingRange): string {
  return runRange({ rangeFrom: range.from, rangeTo: range.to }) ?? ''
}

function rangeSources(range: MissingRange): string {
  return range.sources.map((source) => label('source', source)).join(', ')
}

/** The days and the sources, the name of the range's button. */
function rangeText(range: MissingRange): string {
  return `${rangeDays(range)} · ${rangeSources(range)}`
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

/**
 * The classes of a count's cell: a 0 is muted; above 0 a count of failed or dead posts is bad news (`down`, the
 * destructive tone).
 */
function countClass(value: number, bad = false): string | undefined {
  if (value === 0) return 'zero'
  return bad ? 'down' : undefined
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
    <div class="cols-2 part-top">
      <section class="card" aria-labelledby="start-title" :aria-busy="sourcesLoading || sourcesRetrying ? 'true' : undefined">
        <h2 id="start-title" ref="start-heading" class="card-title" tabindex="-1">Nowy przebieg</h2>
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
            <div class="field">
              <label :for="fromId">Od</label>
              <input :id="fromId" ref="from-input" v-model="from" type="date" required />
            </div>
            <div class="field">
              <label :for="toId">Do</label>
              <input :id="toId" v-model="to" type="date" required />
            </div>
            <fieldset class="sources">
              <legend>Źródła</legend>
              <label v-for="source in sources" :key="source.source" class="check">
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
              icon="i-play"
              :disabled="chosenSources.length === 0"
              :busy="starting"
              busy-label="Uruchamianie…"
              :aria-describedby="startHint ? 'start-hint' : undefined"
            >
              Uruchom
            </AppButton>
          </form>
          <p v-if="startHint" id="start-hint" class="note hint">{{ startHint }}</p>
        </template>
        <StatusMessage v-if="startMessage" tone="ok" class="message">{{ startMessage }}</StatusMessage>
        <StatusMessage v-if="startError" tone="error" class="message">{{ startError }}</StatusMessage>
      </section>

      <section class="card" aria-labelledby="missing-title" :aria-busy="missingLoading || missingRetrying ? 'true' : undefined">
        <h2 id="missing-title" ref="missing-heading" class="card-title" tabindex="-1">Dni bez danych</h2>
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
            <ul class="rows missing-ranges">
              <li v-for="range in ranges" :key="range.from" class="missing-range">
                <span>
                  <span class="row-main missing-days">{{ rangeDays(range) }}</span>
                  {{ ' ' }}
                  <span class="row-sub missing-sources">{{ rangeSources(range) }}</span>
                </span>
                <AppButton
                  :aria-label="`Wpisz do formularza: ${rangeText(range)}`"
                  :disabled="!formShown"
                  @click="fillForm(range)"
                >
                  Wpisz do formularza
                </AppButton>
              </li>
            </ul>
            <p class="note hint">
              Tuż po północy (czasu polskiego) przedwczorajszy dzień może tu być przez 2–3 godziny, zanim pobierze go nocny przebieg.
            </p>
          </template>
        </template>
      </section>
    </div>

    <section
      ref="runs-section"
      class="part-section"
      aria-labelledby="runs-title"
      :aria-busy="runsLoading || runsRetrying ? 'true' : undefined"
    >
      <div class="section-head">
        <h2 id="runs-title" ref="runs-heading" class="section-title" tabindex="-1">Ostatnie przebiegi</h2>
        <AppButton icon="i-refresh-cw" :disabled="runsLoading" @click="refresh">Odśwież</AppButton>
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
        <DataTable class="section-state" caption="Ostatnie przebiegi, od najnowszego">
          <thead>
            <tr>
              <th scope="col">Przebieg</th>
              <th scope="col">Rodzaj</th>
              <th scope="col">Stan</th>
              <th scope="col">Zakres</th>
              <th scope="col" class="num">Ocenione</th>
              <th scope="col" class="num">Nieudane</th>
              <th scope="col" class="num">Martwe</th>
              <th scope="col" class="num">Czas</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="run in runs" :key="run.id" class="run" :data-status="run.status">
              <th scope="row">
                <RouterLink
                  class="details"
                  :to="{ name: 'admin-run', params: { id: run.id } }"
                  :aria-label="`Szczegóły przebiegu z ${formatDateTime(run.createdAt)}`"
                >
                  {{ formatDateTime(run.createdAt) }}
                </RouterLink>
              </th>
              <td class="kind">{{ label('runKind', run.kind) }}</td>
              <td class="state-cell">
                <span class="badges">
                  <StatusBadge class="status" :tone="runTone(run)">{{ runStatusLabel(run) }}</StatusBadge>
                  <StatusBadge v-if="run.needsAttention" class="attention" tone="destructive" icon>wymaga uwagi</StatusBadge>
                </span>
              </td>
              <td class="range">{{ runRange(run) }}</td>
              <td class="num assessed" :class="countClass(run.assessedOk)">{{ formatCount(run.assessedOk) }}</td>
              <td class="num failed" :class="countClass(run.assessedFailed, true)">{{ formatCount(run.assessedFailed) }}</td>
              <td class="num dead" :class="countClass(run.dead, true)">{{ formatCount(run.dead) }}</td>
              <td class="num duration">{{ duration(run) }}</td>
            </tr>
          </tbody>
        </DataTable>
        <div v-if="hasOlder" class="actions">
          <AppButton icon="i-chevron-down" :busy="olderLoading" busy-label="Wczytywanie…" @click="loadOlder">
            Pokaż starsze
          </AppButton>
        </div>
        <StatusMessage v-if="olderError" tone="error" class="message">{{ olderError }}</StatusMessage>
      </template>
    </section>
  </div>
</template>

<style scoped>
/* The part starts apart from the bar of the parts, and its runs apart from the cards above them (the design's
   `.section`). */
.part-top,
.part-section {
  margin-top: var(--space-8);
}

/* "Nowy przebieg" and "Dni bez danych" side by side, one under the other below 900 px. */
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

.card-title {
  margin: 0;
  font-size: var(--font-size-3xl);
}

.section-title {
  margin: 0;
  font-size: var(--font-size-4xl);
}

.section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
}

/* The fields, the sources and "Uruchom" in one row where they fit, lined up at their bottom. */
.start {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3) var(--space-4);
  margin-top: var(--space-4);
}

.field {
  display: grid;
  flex: 1 1 9rem;
  gap: var(--space-1);
  min-width: 0;
}

.field label,
.sources legend {
  color: var(--text-h);
  font-size: var(--font-size-sm);
  font-weight: var(--weight-medium);
}

.sources legend {
  margin-bottom: var(--space-1);
  padding: 0;
}

.sources {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-4);
  min-width: 0;
  min-height: var(--control-height);
  margin: 0;
  padding: 0;
  border: 0;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-h);
  cursor: pointer;
}

.note {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.hint {
  margin: var(--space-3) 0 0;
}

/* A message standing alone in its section, apart from what is above and below it; the part's selector outranks the
   message's own zero margin. */
.admin-part .message {
  margin-block: var(--space-3) 0;
}

/* The state of a section (loading, error, empty) and the table stand apart from the heading above them. */
.section-state {
  margin-top: var(--space-4);
}

.rows {
  margin: var(--space-3) 0 0;
  padding: 0;
  list-style: none;
}

.rows > li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--line);
}

.rows > li:last-child {
  border-bottom: 0;
}

.row-main {
  color: var(--text-h);
  font-weight: var(--weight-medium);
}

.row-sub {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

/* The only cell that wraps: a run's state and its "wymaga uwagi" go one under the other where the row is short of
   room, so the table keeps its columns in the frame on a wide screen (each badge stays in one line). */
.state-cell {
  white-space: normal;
}

.badges {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-2);
  padding-block: var(--space-1);
}

.badges > * {
  white-space: nowrap;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  margin-top: var(--space-4);
}

@media (max-width: 899px) {
  .cols-2 {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
