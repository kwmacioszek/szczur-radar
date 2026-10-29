<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useTemplateRef } from 'vue'
import {
  type MissingDays,
  type Run,
  type SourceState,
  fetchMissingDays,
  fetchRuns,
  fetchSources,
  retryPosts,
  startErrorMessage,
  startRange,
} from '../api/pipeline'
import { type FailedPost, fetchFailedPosts } from '../api/posts'
import AppButton from '../components/AppButton.vue'
import AppHeader from '../components/AppHeader.vue'
import StatusBadge from '../components/StatusBadge.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { formatDate, formatDateTime, formatDuration, label } from '../labels'
import { type MissingRange, missingRanges } from '../missingDays'
import { useEvery } from '../polling'
import { isActive, requester, runDuration, runRange, runStatusLabel, runTone, stepTone } from '../runs'

/** Yesterday in Warsaw, the usual day to run. */
function yesterday(): string {
  const date = new Date(Date.now() - 24 * 60 * 60 * 1000)
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Warsaw' }).format(date)
}

// --- start a range run ------------------------------------------------------------------
const from = ref(yesterday())
const to = ref(yesterday())
const sources = ref<SourceState[]>([])
/** True until the first answer about the sources. */
const sourcesLoading = ref(true)
const sourcesError = ref<string | null>(null)
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
async function loadSources(): Promise<void> {
  sourcesLoading.value = true
  sourcesError.value = null
  try {
    sources.value = await fetchSources()
    chosenSources.value = sources.value.filter((s) => s.configured).map((s) => s.source)
  } catch {
    sourcesError.value = 'Nie udało się wczytać listy źródeł.'
  } finally {
    sourcesLoading.value = false
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
    await loadRuns()
  } catch (err) {
    startError.value = startErrorMessage(err)
  } finally {
    starting.value = false
  }
}

// --- days without data ------------------------------------------------------------------
const fromInput = useTemplateRef<HTMLInputElement>('from-input')
const missing = ref<MissingDays | null>(null)
/** Loading without an answer yet; a reload keeps the ranges on screen. */
const missingLoading = ref(true)
const missingError = ref<string | null>(null)
/** The number of the latest read; the answer or error of any earlier one is dropped. */
let latestMissing = 0
const ranges = computed(() => (missing.value ? missingRanges(missing.value.days) : []))

// Read on mount, with "Odśwież" and after a run of the list stops being active: then the coverage may have changed.
async function loadMissing(): Promise<void> {
  const current = ++latestMissing
  missingLoading.value = missing.value === null
  missingError.value = null
  try {
    const answer = await fetchMissingDays()
    if (current === latestMissing) missing.value = answer
  } catch {
    if (current === latestMissing) missingError.value = 'Nie udało się wczytać dni bez danych.'
  } finally {
    if (current === latestMissing) missingLoading.value = false
  }
}

/** The days, as a run's range is shown, and the sources; a `YYYY-MM-DD` day is its UTC midnight, that day in Warsaw. */
function rangeText(range: MissingRange): string {
  const days = runRange({ rangeFrom: range.from, rangeTo: range.to })
  return `${days} · ${range.sources.map((source) => label('source', source)).join(', ')}`
}

/** Puts the range into "Nowy przebieg" (only configured sources can be checked) and moves the focus to "Od". */
function fillForm(range: MissingRange): void {
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
/** Rows per page of "Pokaż starsze". */
const PAGE_SIZE = 20
/** The API's largest page: a refresh reads at most this many of the newest rows again. */
const MAX_PAGE_SIZE = 100
/** How often the list is read again while a run is queued or running. */
const REFRESH_MS = 10 * 1000

const runsSection = useTemplateRef<HTMLElement>('runs-section')
const runs = ref<Run[]>([])
/** All runs in the API, from the latest answer. */
const runsTotal = ref(0)
/** Loading without a list; a refresh keeps existing runs on screen. */
const runsLoading = ref(true)
const runsError = ref<string | null>(null)
const olderLoading = ref(false)
const olderError = ref<string | null>(null)
/** The time durations of running runs count to: the last read. */
const now = ref(Date.now())
/** Pages of PAGE_SIZE read so far (the next "Pokaż starsze" reads this page number). */
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
async function loadRuns(): Promise<void> {
  const current = ++latestRefresh
  refreshing = true
  runsLoading.value = runs.value.length === 0
  runsError.value = null
  try {
    const page = await fetchRuns(0, Math.min(Math.max(runs.value.length, PAGE_SIZE), MAX_PAGE_SIZE))
    if (current !== latestRefresh) return
    const wasActive = new Set(runs.value.filter(isActive).map((run) => run.id))
    runs.value = mergeRuns(page.items, runs.value)
    // A run that ended may have covered days without data.
    if (runs.value.some((run) => wasActive.has(run.id) && !isActive(run))) void loadMissing()
    runsTotal.value = page.total
    pagesLoaded = Math.max(pagesLoaded, 1)
    now.value = Date.now()
  } catch {
    if (current !== latestRefresh) return
    runsError.value = 'Nie udało się wczytać przebiegów.'
  } finally {
    if (current === latestRefresh) {
      runsLoading.value = false
      refreshing = false
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
    const page = await fetchRuns(pagesLoaded, PAGE_SIZE)
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

useEvery(REFRESH_MS, () => {
  if (!refreshing && runs.value.some(isActive)) void loadRuns()
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
/** Loading without a list; a refresh keeps existing failed posts on screen. */
const failedLoading = ref(true)
const failedError = ref<string | null>(null)
const selected = ref<string[]>([])
const retrying = ref(false)
const retryMessage = ref<string | null>(null)
const retryError = ref<string | null>(null)
/** Failed posts and posts on hold; never a dead one. */
const retryable = computed(() => failed.value.filter((post) => post.stage !== 'dead'))

async function loadFailed(): Promise<void> {
  failedLoading.value = failed.value.length === 0
  failedError.value = null
  try {
    failed.value = (await fetchFailedPosts()).items
    selected.value = selected.value.filter((id) => retryable.value.some((post) => post.id === id))
  } catch {
    failedError.value = 'Nie udało się wczytać nieudanych postów.'
  } finally {
    failedLoading.value = false
  }
}

async function retry(): Promise<void> {
  if (retrying.value || selected.value.length === 0) return
  retrying.value = true
  retryMessage.value = null
  retryError.value = null
  try {
    await retryPosts(selected.value)
    retryMessage.value = `Ponowienie ${selected.value.length} post(ów) w kolejce.`
    selected.value = []
    await loadRuns()
  } catch (err) {
    retryError.value = startErrorMessage(err)
  } finally {
    retrying.value = false
  }
}

function excerpt(post: FailedPost): string {
  const text = post.title ?? post.body ?? ''
  return text.length > 120 ? `${text.slice(0, 120)}…` : text
}

async function refresh(): Promise<void> {
  await Promise.all([loadRuns(), loadFailed(), loadMissing(), sourcesError.value ? loadSources() : undefined])
}

onMounted(async () => {
  await Promise.all([loadSources(), loadMissing(), loadRuns(), loadFailed()])
})
</script>

<template>
  <AppHeader />
  <main class="admin">
    <h1>Panel admina</h1>

    <section aria-labelledby="start-title">
      <h2 id="start-title">Nowy przebieg</h2>
      <form class="start" @submit.prevent="start">
        <label>Od <input ref="from-input" v-model="from" type="date" required /></label>
        <label>Do <input v-model="to" type="date" required /></label>
        <fieldset class="sources" :aria-busy="sourcesLoading ? 'true' : undefined">
          <legend>Źródła</legend>
          <span v-if="sourcesLoading" class="loading">Wczytywanie…</span>
          <template v-else-if="sourcesError">
            <StatusMessage tone="error" class="sources-error">{{ sourcesError }}</StatusMessage>
            <AppButton @click="loadSources">Spróbuj ponownie</AppButton>
          </template>
          <template v-else>
            <label v-for="source in sources" :key="source.source">
              <input
                v-model="chosenSources"
                type="checkbox"
                :value="source.source"
                :disabled="!source.configured"
              />
              {{ label('source', source.source) }}<template v-if="!source.configured"> (nieskonfigurowane)</template>
            </label>
          </template>
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
      <StatusMessage v-if="startMessage" tone="ok">{{ startMessage }}</StatusMessage>
      <StatusMessage v-if="startError" tone="error">{{ startError }}</StatusMessage>
    </section>

    <section aria-labelledby="missing-title" :aria-busy="missingLoading ? 'true' : undefined">
      <h2 id="missing-title">Dni bez danych</h2>
      <p v-if="missingLoading" class="loading">Wczytywanie…</p>
      <StatusMessage v-else-if="missingError" tone="error">{{ missingError }}</StatusMessage>
      <template v-else-if="missing">
        <p v-if="ranges.length === 0" class="empty">
          Brak dni bez danych od {{ formatDate(missing.from) }} do {{ formatDate(missing.to) }}.
        </p>
        <template v-else>
          <ul class="missing-ranges">
            <li v-for="range in ranges" :key="range.from" class="missing-range">
              <span class="missing-days">{{ rangeText(range) }}</span>
              <AppButton :aria-label="`Wpisz do formularza: ${rangeText(range)}`" @click="fillForm(range)">
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

    <section ref="runs-section" aria-labelledby="runs-title" :aria-busy="runsLoading ? 'true' : undefined">
      <div class="section-head">
        <h2 id="runs-title">Ostatnie przebiegi</h2>
        <AppButton @click="refresh">Odśwież</AppButton>
      </div>
      <p v-if="runsLoading" class="loading">Wczytywanie…</p>
      <StatusMessage v-else-if="runsError" tone="error">{{ runsError }}</StatusMessage>
      <p v-else-if="runs.length === 0" class="empty">Jeszcze nie było przebiegu.</p>
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
        <StatusMessage v-if="olderError" tone="error">{{ olderError }}</StatusMessage>
      </template>
    </section>

    <section aria-labelledby="failed-title" :aria-busy="failedLoading ? 'true' : undefined">
      <h2 id="failed-title">Nieudane posty</h2>
      <p v-if="failedLoading" class="loading">Wczytywanie…</p>
      <StatusMessage v-else-if="failedError" tone="error">{{ failedError }}</StatusMessage>
      <p v-else-if="failed.length === 0" class="empty">Brak nieudanych postów.</p>
      <template v-else>
        <ul class="failed-posts">
          <li v-for="post in failed" :key="post.id">
            <label>
              <input
                v-model="selected"
                type="checkbox"
                :value="post.id"
                :disabled="post.stage === 'dead'"
              />
              <span class="excerpt">{{ excerpt(post) }}</span>
            </label>
            <span class="attempt">
              <template v-if="post.stage === 'dead'"><strong>martwy</strong> · </template>
              <template v-else-if="post.onHold"><strong>wstrzymany</strong> · </template>
              <!-- A stored post on hold has no stored attempt: only its technical errors. -->
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
        <AppButton :disabled="selected.length === 0" :busy="retrying" busy-label="Ponawianie…" @click="retry">
          Ponów zaznaczone
        </AppButton>
      </template>
      <StatusMessage v-if="retryMessage" tone="ok">{{ retryMessage }}</StatusMessage>
      <StatusMessage v-if="retryError" tone="error">{{ retryError }}</StatusMessage>
    </section>
  </main>
</template>

<style scoped>
.admin {
  max-width: 56rem;
  margin: 0 auto;
  padding: var(--space-4);
  box-sizing: border-box;
}

h1 {
  margin: var(--space-2) 0 var(--space-4);
}

h2 {
  margin: var(--space-5) 0 var(--space-2);
  font-size: var(--font-size-lg);
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
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

/* The parent's selector outranks the message's own margin, so it sits in the fieldset row. */
.sources .sources-error {
  margin: 0;
}

.hint {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-sm);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.runs,
.failed-posts,
.missing-ranges {
  margin: 0;
  padding: 0;
  list-style: none;
}

.run,
.failed-posts li,
.missing-range {
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--border);
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
.steps,
.attempt {
  font-size: var(--font-size-sm);
}

.details {
  color: var(--accent);
  text-decoration: none;
}

.details:hover {
  text-decoration: underline;
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
