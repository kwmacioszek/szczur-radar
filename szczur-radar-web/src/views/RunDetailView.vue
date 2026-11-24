<script setup lang="ts">
// One pipeline run (admin), also one beyond the list in /admin, laid out as ui-design/prototypes/screens/run.html: the
// run named by its creation time (as the link of the runs table names it) with its status and cause, why it needs
// attention, its kind, range, sources, who started it and its times, the counters as figures, each source's result as
// a card (a range run), the steps as a numbered list and the error; while it is queued or running, its progress, read
// again every 10 s until it ends.
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '../api/client'
import { type Run, type RunStep, fetchRun } from '../api/pipeline'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusBadge from '../components/StatusBadge.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { formatCount, formatDateTime, formatDuration, formatRelative, label, plural } from '../labels'
import { useEvery } from '../polling'
import {
  assessProgress,
  attentionReasons,
  currentStep,
  isActive,
  requester,
  runDuration,
  runRange,
  runStatusLabel,
  runTone,
  sourceTone,
  stepDuration,
  stepTone,
} from '../runs'

/** How often a queued or running run is read again. */
const REFRESH_MS = 10 * 1000

const route = useRoute()
const header = useTemplateRef<InstanceType<typeof PageHeader>>('header')
const errorState = useTemplateRef<InstanceType<typeof ErrorState>>('error-state')

/** `missing`: 404 or 400, an id that is not a run; `failed`: any other error. */
type LoadState = 'loading' | 'failed' | 'missing' | 'loaded'

const state = ref<LoadState>('loading')
/** A retry is in progress: its error stays, with the button busy, until the answer. */
const retrying = ref(false)
const run = ref<Run | null>(null)
/** The last refresh of a running run failed; the run on screen is the one read before. */
const refreshFailed = ref(false)
/** The time durations and "ostatnia aktywność" count to: the last read. */
const now = ref(Date.now())
/** The number of the latest load; the answer or error of any earlier one (and of its refreshes) is dropped. */
let latestLoad = 0
let refreshing = false

/** The id in the address; null while the router leaves this route. */
const runId = computed(() =>
  route.name === 'admin-run' && typeof route.params.id === 'string' ? route.params.id : null,
)

/** The same time as the label of the run's link in the runs table: `Szczegóły przebiegu z <createdAt>`. */
const title = computed(() => (run.value ? `Przebieg z ${formatDateTime(run.value.createdAt)}` : 'Przebieg'))
const range = computed(() => (run.value ? runRange(run.value) : null))
const progress = computed(() => (run.value ? assessProgress(run.value) : null))
/** The step running now; `w kolejce` before the first one. */
const stepNow = computed(() => {
  if (!run.value) return '—'
  const step = currentStep(run.value)
  if (step) return label('step', step.step)
  return run.value.status === 'queued' ? 'w kolejce' : '—'
})

/** A figure with its name, as shown in a list of counters; `alarm`: more than none is set in the destructive tone. */
type Figure = { name: string; value: number; alarm?: boolean }

function figures(shown: Run): Figure[] {
  return [
    { name: 'Pobrane', value: shown.fetched },
    { name: 'Nowe', value: shown.newPosts },
    { name: 'Ocenione', value: shown.assessedOk },
    { name: 'Nieudane', value: shown.assessedFailed, alarm: true },
    { name: 'Martwe', value: shown.dead, alarm: true },
    { name: 'Pominięte', value: shown.skipped },
    { name: 'Błędy techniczne', value: shown.processingErrors },
    { name: 'Ponowienia LLM', value: shown.llmRetries },
    { name: 'Powroty z flex', value: shown.flexFallbacks },
  ]
}

function progressFigures(shown: Run): Figure[] {
  return [
    { name: 'w toku', value: shown.inFlight },
    { name: 'ok', value: shown.assessedOk },
    { name: 'nieudane', value: shown.assessedFailed },
    { name: 'martwe', value: shown.dead },
    { name: 'błędy techniczne', value: shown.processingErrors },
    { name: 'ponowienia LLM', value: shown.llmRetries },
    { name: 'powroty z flex', value: shown.flexFallbacks },
  ]
}

function duration(ms: number | null): string {
  return ms === null ? '—' : formatDuration(ms)
}

/**
 * A step's elements out and its time, e.g. `248 elementów · 20 s`; only the time while it has given nothing out yet
 * (running, or ended without a count), a dash with neither.
 */
function stepDetail(shown: RunStep): string {
  const parts: string[] = []
  if (shown.itemsOut !== null) {
    parts.push(`${formatCount(shown.itemsOut)} ${plural(shown.itemsOut, 'element', 'elementy', 'elementów')}`)
  }
  const time = stepDuration(shown, now.value)
  if (time !== null) parts.push(formatDuration(time))
  return parts.length > 0 ? parts.join(' · ') : '—'
}

/**
 * `retry`: from the error's own button, whose error stays until the answer. The busy button loses the focus, so after
 * the answer it goes to the heading, or back to "Spróbuj ponownie" after another failure.
 */
async function load(id: string, { retry = false } = {}): Promise<void> {
  const current = ++latestLoad
  retrying.value = retry
  if (!retry) state.value = 'loading'
  run.value = null
  refreshFailed.value = false
  try {
    const result = await fetchRun(id)
    if (current !== latestLoad) return
    run.value = result
    now.value = Date.now()
    state.value = 'loaded'
    retrying.value = false
    await nextTick()
    if (current === latestLoad) header.value?.focus()
  } catch (error) {
    if (current !== latestLoad) return
    const missing = error instanceof ApiError && (error.status === 404 || error.status === 400)
    state.value = missing ? 'missing' : 'failed'
    retrying.value = false
    if (!retry) return
    await nextTick()
    if (current !== latestLoad) return
    if (missing) header.value?.focus()
    else errorState.value?.focus()
  }
}

/** Reads a queued or running run again; nothing once it has ended. The run stays on screen meanwhile. */
async function refresh(): Promise<void> {
  const shown = run.value
  if (!shown || !isActive(shown) || refreshing) return
  const current = latestLoad
  refreshing = true
  try {
    const result = await fetchRun(shown.id)
    if (current !== latestLoad) return
    run.value = result
    refreshFailed.value = false
  } catch {
    if (current !== latestLoad) return
    refreshFailed.value = true
  } finally {
    refreshing = false
    if (current === latestLoad) now.value = Date.now()
  }
}

function retry(): void {
  if (runId.value !== null) void load(runId.value, { retry: true })
}

useEvery(REFRESH_MS, () => void refresh())

watch(
  runId,
  (id) => {
    if (id !== null) void load(id)
  },
  { immediate: true },
)
</script>

<template>
  <div class="run-detail">
    <RouterLink class="back" :to="{ name: 'admin' }"><AppIcon class="back-icon" name="i-chevron-left" />Panel admina</RouterLink>
    <section aria-labelledby="run-title" :aria-busy="state === 'loading' || retrying ? 'true' : undefined">
      <PageHeader ref="header" class="head" :title="title" heading-id="run-title">
        <template #status>
          <StatusBadge v-if="run" class="status" :tone="runTone(run)">{{ runStatusLabel(run) }}</StatusBadge>
        </template>
      </PageHeader>
      <LoadingState v-if="state === 'loading'" class="view-state" shape="lines" :count="4" />
      <ErrorState
        v-else-if="state === 'failed'"
        ref="error-state"
        class="view-state"
        title-tag="h2"
        title="Nie udało się wczytać przebiegu"
        :retrying="retrying"
        @retry="retry"
      >
        Serwer nie odpowiedział.
      </ErrorState>
      <EmptyState
        v-else-if="state === 'missing'"
        class="view-state"
        title-tag="h2"
        icon="i-inbox"
        title="Nie ma takiego przebiegu"
      >
        Przebieg nie istnieje albo został usunięty z historii.
        <template #action>
          <RouterLink :to="{ name: 'admin' }">Wróć do panelu admina</RouterLink>
        </template>
      </EmptyState>
      <template v-else-if="run">
        <StatusMessage v-if="run.needsAttention" tone="error" class="attention">
          <strong>Wymaga uwagi:</strong> {{ attentionReasons(run).join('; ') }}
        </StatusMessage>
        <StatusMessage v-if="refreshFailed" tone="error" class="refresh-error">
          Nie udało się odświeżyć przebiegu. Kolejna próba za 10 s.
        </StatusMessage>

        <dl class="meta">
          <div>
            <dt>Rodzaj</dt>
            <dd class="kind">{{ label('runKind', run.kind) }}</dd>
          </div>
          <div v-if="range">
            <dt>Zakres</dt>
            <dd class="range">{{ range }}</dd>
          </div>
          <div>
            <dt>Źródła</dt>
            <dd class="sources">
              {{ run.sources.length ? run.sources.map((source) => label('source', source)).join(', ') : '—' }}
            </dd>
          </div>
          <div>
            <dt>Uruchomił</dt>
            <dd class="requester">{{ requester(run) }}</dd>
          </div>
          <div>
            <dt>Start</dt>
            <dd class="started">
              <time v-if="run.startedAt" :datetime="run.startedAt">{{ formatDateTime(run.startedAt) }}</time>
              <template v-else>—</template>
            </dd>
          </div>
          <div>
            <dt>Koniec</dt>
            <dd class="finished">
              <time v-if="run.finishedAt" :datetime="run.finishedAt">{{ formatDateTime(run.finishedAt) }}</time>
              <template v-else>—</template>
            </dd>
          </div>
          <div>
            <dt>Czas trwania</dt>
            <dd class="duration">{{ duration(runDuration(run, now)) }}</dd>
          </div>
        </dl>
      </template>
    </section>

    <template v-if="state === 'loaded' && run">
      <section v-if="isActive(run)" class="part progress" aria-labelledby="progress-title">
        <h2 id="progress-title">Postęp</h2>
        <p class="current-step">Krok: <strong>{{ stepNow }}</strong></p>
        <template v-if="progress?.total">
          <label class="progress-label" for="assess-progress">Ocena: {{ progress.done }} z {{ progress.total }}</label>
          <progress id="assess-progress" :value="progress.done" :max="progress.total" />
        </template>
        <ul class="figures">
          <li v-for="figure in progressFigures(run)" :key="figure.name">
            {{ figure.name }} <strong>{{ figure.value }}</strong>
          </li>
        </ul>
        <p v-if="run.lastActivityAt" class="activity">
          Ostatnia aktywność:
          <time :datetime="run.lastActivityAt">{{ formatRelative(run.lastActivityAt, now) }}</time>
        </p>
      </section>

      <section class="part" aria-labelledby="counters-title">
        <h2 id="counters-title">Liczniki</h2>
        <dl class="stats counters">
          <div
            v-for="figure in figures(run)"
            :key="figure.name"
            class="stat"
            :class="{ 'stat-alarm': figure.alarm && figure.value > 0 }"
          >
            <dt>{{ figure.name }}</dt>
            <dd>{{ formatCount(figure.value) }}</dd>
          </div>
        </dl>
      </section>

      <section v-if="run.kind === 'range'" class="part source-results" aria-labelledby="source-results-title">
        <h2 id="source-results-title">Wynik per źródło</h2>
        <p v-if="run.sourceResults.length === 0" class="none">Jeszcze żadne źródło nie zakończyło pobrania.</p>
        <ul v-else class="source-list">
          <li
            v-for="result in run.sourceResults"
            :key="result.source"
            class="source-result"
            :data-status="result.status"
          >
            <div class="source-head">
              <h3 class="source-name">{{ label('source', result.source) }}</h3>
              <StatusBadge :tone="sourceTone(result.status)" :icon="result.status !== 'succeeded'">
                {{ label('sourceStatus', result.status) }}
              </StatusBadge>
            </div>
            <p class="source-meta">
              pobrane {{ formatCount(result.fetched) }} · pominięte {{ formatCount(result.skipped) }} · posty
              {{ formatCount(result.fetched - result.skipped) }}
            </p>
            <p v-if="result.error" class="source-error">{{ result.error }}</p>
          </li>
        </ul>
      </section>

      <section class="part" aria-labelledby="steps-title">
        <h2 id="steps-title">Kroki</h2>
        <p v-if="run.steps.length === 0" class="none">Żaden krok się jeszcze nie zaczął.</p>
        <ol v-else class="steps">
          <li
            v-for="(shown, index) in run.steps"
            :key="shown.step + shown.startedAt"
            class="step"
            :data-status="shown.status"
          >
            <!-- The list numbers its items for assistive technology; the number on screen is only the look. -->
            <span class="step-no" aria-hidden="true">{{ index + 1 }}</span>
            <span class="step-name">{{ label('step', shown.step) }}</span>
            <span class="step-detail">{{ stepDetail(shown) }}</span>
            <StatusBadge class="step-status" :tone="stepTone(shown.status)">
              {{ label('stepStatus', shown.status) }}
            </StatusBadge>
            <span v-if="shown.error" class="step-error">{{ shown.error }}</span>
          </li>
        </ol>
      </section>

      <section v-if="run.error" class="part" aria-labelledby="error-title">
        <h2 id="error-title">Błąd przebiegu</h2>
        <p class="run-error">{{ run.error }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
/* The way back to the panel, small and above the title (the design's `.back`). */
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: 1.5rem;
  margin-top: var(--space-2);
  font-size: var(--font-size-sm);
}

.back-icon {
  --icon-size: 1rem;
}

.head {
  margin-top: var(--space-2);
}

/* The design's section heading. */
h2 {
  margin: 0;
  font-size: var(--font-size-4xl);
}

p {
  margin: 0;
}

/* The state of the run (loading, error, none) stands apart from the title above it. */
.view-state {
  margin-top: var(--space-4);
}

/* A part of a loaded run with nothing in it yet. */
.none {
  margin-top: var(--space-4);
}

/* Apart from the title above them; the page's selector outranks the message's own zero margin. */
.run-detail .attention,
.run-detail .refresh-error {
  margin-top: var(--space-4);
}

/* The run's details side by side, as many as fit: one under the other on a phone. */
.meta {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(11rem, 1fr));
  gap: var(--space-4) var(--space-5);
  margin: var(--space-5) 0 0;
}

.meta dt {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.meta dd {
  margin: 0;
  color: var(--text-h);
  overflow-wrap: anywhere;
}

/* Each part of the run stands apart from the one above it (the design's `.section`). */
.part {
  margin-top: var(--space-8);
}

.current-step,
.progress-label {
  margin-top: var(--space-4);
}

.progress-label {
  display: block;
  margin-bottom: var(--space-1);
  color: var(--text-h);
}

/* The native bar in the accent colour; it takes the width of the column. */
progress {
  display: block;
  width: 100%;
  margin-bottom: var(--space-3);
  accent-color: var(--accent);
}

.figures {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-4);
  margin: var(--space-3) 0 var(--space-2);
  padding: 0;
  list-style: none;
  font-size: var(--font-size-sm);
}

.figures strong,
.current-step strong {
  color: var(--text-h);
}

.activity {
  font-size: var(--font-size-sm);
}

/* The counters as a grid of figures (the design's `.stats`). */
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(8.5rem, 1fr));
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
}

.stat {
  display: grid;
  gap: var(--space-0);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--surface);
}

.stat dt {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.stat dd {
  margin: 0;
  color: var(--text-h);
  font-family: var(--font-display);
  font-size: var(--font-size-4xl);
  font-weight: var(--weight-medium);
  line-height: var(--leading-snug);
  font-variant-numeric: tabular-nums;
}

/* Failed or dead posts: the figure in the destructive tone; its name and number say it. */
.stat-alarm dd {
  color: var(--destructive);
}

/* Each source's result as a card, side by side, one under the other below 900 px. */
.source-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: var(--space-4);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.source-result {
  min-width: 0;
  padding: var(--space-5);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  background: var(--surface);
}

.source-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
}

.source-name {
  margin: 0;
  font-size: var(--font-size-3xl);
}

.source-meta {
  margin-top: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.source-error {
  margin-top: var(--space-2);
}

/* The steps in one panel, a numbered row each (the design's `.steps`). */
.steps {
  margin: var(--space-4) 0 0;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  background: var(--surface);
  list-style: none;
}

.step {
  display: grid;
  grid-template-columns: 2rem 10rem minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-1) var(--space-4);
  padding: var(--space-3) var(--space-5);
  border-bottom: 1px solid var(--line);
}

.step:last-child {
  border-bottom: 0;
}

.step-no {
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.step-name {
  color: var(--text-h);
  font-weight: var(--weight-medium);
}

.step-detail {
  color: var(--text);
  font-size: var(--font-size-sm);
}

.step-error {
  grid-column: 2 / -1;
}

.step-error,
.source-error,
.run-error {
  color: var(--destructive);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.run-error {
  margin-top: var(--space-4);
}

@media (max-width: 899px) {
  .source-list {
    grid-template-columns: minmax(0, 1fr);
  }

  /* The name with its badge on the first line, its detail and error below the name. */
  .step {
    grid-template-columns: 2rem minmax(0, 1fr) auto;
    padding: var(--space-3) var(--space-4);
  }

  .step-status {
    grid-row: 1;
    grid-column: 3;
  }

  .step-detail,
  .step-error {
    grid-column: 2 / -1;
  }
}
</style>
