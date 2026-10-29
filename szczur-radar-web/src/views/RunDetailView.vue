<script setup lang="ts">
// One pipeline run (admin), also one beyond the list in /admin: status with its cause, why it needs attention,
// who started it, its range, times, counters, steps and error; while it is queued or running, its progress, read
// again every 10 s until it ends.
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '../api/client'
import { type Run, fetchRun } from '../api/pipeline'
import AppButton from '../components/AppButton.vue'
import AppHeader from '../components/AppHeader.vue'
import StatusBadge from '../components/StatusBadge.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { formatDateTime, formatDuration, formatRelative, label } from '../labels'
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
  stepDuration,
  stepTone,
} from '../runs'

/** How often a queued or running run is read again. */
const REFRESH_MS = 10 * 1000

const route = useRoute()
const heading = useTemplateRef<HTMLHeadingElement>('run-title')

/** `missing`: 404 or 400, an id that is not a run; `failed`: any other error. */
type LoadState = 'loading' | 'failed' | 'missing' | 'loaded'

const state = ref<LoadState>('loading')
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

const title = computed(() => (run.value ? `Przebieg: ${label('runKind', run.value.kind)}` : 'Przebieg'))
const range = computed(() => (run.value ? runRange(run.value) : null))
const progress = computed(() => (run.value ? assessProgress(run.value) : null))
/** The step running now; `w kolejce` before the first one. */
const stepNow = computed(() => {
  if (!run.value) return '—'
  const step = currentStep(run.value)
  if (step) return label('step', step.step)
  return run.value.status === 'queued' ? 'w kolejce' : '—'
})

/** A figure with its name, as shown in a list of counters. */
type Figure = { name: string; value: number }

function figures(shown: Run): Figure[] {
  return [
    { name: 'pobrane', value: shown.fetched },
    { name: 'nowe', value: shown.newPosts },
    { name: 'ocenione', value: shown.assessedOk },
    { name: 'nieudane', value: shown.assessedFailed },
    { name: 'martwe', value: shown.dead },
    { name: 'pominięte', value: shown.skipped },
    { name: 'błędy techniczne', value: shown.processingErrors },
    { name: 'ponowienia LLM', value: shown.llmRetries },
    { name: 'powroty z flex', value: shown.flexFallbacks },
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

async function load(id: string): Promise<void> {
  const current = ++latestLoad
  state.value = 'loading'
  run.value = null
  refreshFailed.value = false
  try {
    const result = await fetchRun(id)
    if (current !== latestLoad) return
    run.value = result
    now.value = Date.now()
    state.value = 'loaded'
    await nextTick()
    if (current === latestLoad) heading.value?.focus()
  } catch (error) {
    if (current !== latestLoad) return
    const missing = error instanceof ApiError && (error.status === 404 || error.status === 400)
    state.value = missing ? 'missing' : 'failed'
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
  if (runId.value !== null) void load(runId.value)
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
  <AppHeader />
  <main class="run-detail">
    <RouterLink class="back" :to="{ name: 'admin' }"><span aria-hidden="true">←</span> Panel admina</RouterLink>
    <section aria-labelledby="run-title" :aria-busy="state === 'loading' ? 'true' : undefined">
      <div class="title-row">
        <h1 id="run-title" ref="run-title" tabindex="-1">{{ title }}</h1>
        <StatusBadge v-if="run" class="status" :tone="runTone(run)">{{ runStatusLabel(run) }}</StatusBadge>
      </div>
      <p v-if="state === 'loading'" class="loading">Wczytywanie przebiegu…</p>
      <template v-else-if="state === 'failed'">
        <StatusMessage tone="error">Nie udało się wczytać przebiegu.</StatusMessage>
        <AppButton @click="retry">Spróbuj ponownie</AppButton>
      </template>
      <p v-else-if="state === 'missing'" class="empty">
        Nie ma takiego przebiegu.
        <RouterLink :to="{ name: 'admin' }">Wróć do panelu admina</RouterLink>
      </p>
      <template v-else-if="run">
        <StatusMessage v-if="run.needsAttention" tone="error" class="attention">
          <strong>Wymaga uwagi:</strong> {{ attentionReasons(run).join('; ') }}
        </StatusMessage>
        <StatusMessage v-if="refreshFailed" tone="error" class="refresh-error">
          Nie udało się odświeżyć przebiegu. Kolejna próba za 10 s.
        </StatusMessage>

        <dl class="meta">
          <dt>Uruchomił</dt>
          <dd class="requester">{{ requester(run) }}</dd>
          <template v-if="range">
            <dt>Zakres</dt>
            <dd class="range">{{ range }}</dd>
          </template>
          <dt>Źródła</dt>
          <dd>{{ run.sources.length ? run.sources.map((source) => label('source', source)).join(', ') : '—' }}</dd>
          <dt>Utworzony</dt>
          <dd><time :datetime="run.createdAt">{{ formatDateTime(run.createdAt) }}</time></dd>
          <dt>Start</dt>
          <dd>
            <time v-if="run.startedAt" :datetime="run.startedAt">{{ formatDateTime(run.startedAt) }}</time>
            <template v-else>—</template>
          </dd>
          <dt>Koniec</dt>
          <dd>
            <time v-if="run.finishedAt" :datetime="run.finishedAt">{{ formatDateTime(run.finishedAt) }}</time>
            <template v-else>—</template>
          </dd>
          <dt>Czas trwania</dt>
          <dd class="duration">{{ duration(runDuration(run, now)) }}</dd>
        </dl>

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
          <ul class="figures counters">
            <li v-for="figure in figures(run)" :key="figure.name">
              {{ figure.name }} <strong>{{ figure.value }}</strong>
            </li>
          </ul>
        </section>

        <section class="part" aria-labelledby="steps-title">
          <h2 id="steps-title">Kroki</h2>
          <p v-if="run.steps.length === 0" class="empty">Żaden krok się jeszcze nie zaczął.</p>
          <ol v-else class="steps">
            <li v-for="step in run.steps" :key="step.step + step.startedAt" class="step" :data-status="step.status">
              <p class="step-head">
                <strong>{{ label('step', step.step) }}</strong>
                <StatusBadge :tone="stepTone(step.status)">
                  {{ label('stepStatus', step.status) }}
                </StatusBadge>
              </p>
              <p class="step-meta">
                start <time :datetime="step.startedAt">{{ formatDateTime(step.startedAt) }}</time>
                · czas {{ duration(stepDuration(step, now)) }}
                · wejście {{ step.itemsIn ?? '—' }}
                · wyjście {{ step.itemsOut ?? '—' }}
              </p>
              <p v-if="step.error" class="step-error">{{ step.error }}</p>
            </li>
          </ol>
        </section>

        <section v-if="run.error" class="part" aria-labelledby="error-title">
          <h2 id="error-title">Błąd przebiegu</h2>
          <p class="run-error">{{ run.error }}</p>
        </section>
      </template>
    </section>
  </main>
</template>

<style scoped>
.run-detail {
  max-width: 48rem;
  margin: 0 auto;
  padding: var(--space-4);
  box-sizing: border-box;
}

a {
  color: var(--accent);
  text-decoration: none;
}

a:hover {
  text-decoration: underline;
}

.back {
  display: inline-block;
  margin-top: var(--space-2);
}

.title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2) var(--space-3);
  margin: var(--space-4) 0 var(--space-2);
}

h1 {
  margin: 0;
  font-size: var(--font-size-lg);
  letter-spacing: normal;
  line-height: 1.45;
}

h2 {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-md);
  color: var(--text-h);
}

p {
  margin: 0 0 var(--space-2);
}

.loading,
.empty {
  margin: var(--space-4) 0;
}

.meta {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: var(--space-1) var(--space-4);
  margin: var(--space-4) 0 0;
  font-size: var(--font-size-sm);
}

.meta dt {
  color: var(--text);
}

.meta dd {
  margin: 0;
  color: var(--text-h);
  overflow-wrap: anywhere;
}

.part {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border);
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
  margin: 0 0 var(--space-2);
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

.steps {
  margin: 0;
  padding: 0;
  list-style: none;
}

/* A line between steps only: the section below ("Błąd przebiegu") draws its own. */
.step {
  padding: var(--space-2) 0;
}

.step + .step {
  border-top: 1px solid var(--border);
}

.step p {
  margin: var(--space-0) 0;
}

.step-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-3);
}

.step-meta {
  font-size: var(--font-size-sm);
}

.step-error,
.run-error {
  color: var(--destructive);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}
</style>
