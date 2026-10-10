<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  type Run,
  type RunStatus,
  type SourceState,
  fetchRuns,
  fetchSources,
  retryPosts,
  startErrorMessage,
  startRange,
} from '../api/pipeline'
import { type FailedPost, fetchFailedPosts } from '../api/posts'
import AppButton from '../components/AppButton.vue'
import AppHeader from '../components/AppHeader.vue'
import StatusBadge, { type BadgeTone } from '../components/StatusBadge.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { formatDateTime, label } from '../labels'

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

// --- runs -------------------------------------------------------------------------------
const runs = ref<Run[]>([])
/** True until the first answer; a refresh keeps the list on screen. */
const runsLoading = ref(true)
const runsError = ref<string | null>(null)

/** The badge tone of a run status; a step uses the same tones. */
const runTone: Record<RunStatus, BadgeTone> = {
  queued: 'neutral',
  running: 'info',
  succeeded: 'success',
  partial: 'warning',
  interrupted: 'warning',
  failed: 'destructive',
}

async function loadRuns(): Promise<void> {
  runsError.value = null
  try {
    runs.value = (await fetchRuns()).items
  } catch {
    runsError.value = 'Nie udało się wczytać przebiegów.'
  } finally {
    runsLoading.value = false
  }
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
/** True until the first answer; a refresh keeps the list on screen. */
const failedLoading = ref(true)
const failedError = ref<string | null>(null)
const selected = ref<string[]>([])
const retrying = ref(false)
const retryMessage = ref<string | null>(null)
const retryError = ref<string | null>(null)
const retryable = computed(() => failed.value.filter((post) => post.stage !== 'dead'))

async function loadFailed(): Promise<void> {
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
  await Promise.all([loadRuns(), loadFailed(), sourcesError.value ? loadSources() : undefined])
}

onMounted(async () => {
  await Promise.all([loadSources(), loadRuns(), loadFailed()])
})
</script>

<template>
  <AppHeader />
  <main class="admin">
    <h1>Panel admina</h1>

    <section aria-labelledby="start-title">
      <h2 id="start-title">Nowy przebieg</h2>
      <form class="start" @submit.prevent="start">
        <label>Od <input v-model="from" type="date" required /></label>
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

    <section aria-labelledby="runs-title" :aria-busy="runsLoading ? 'true' : undefined">
      <div class="section-head">
        <h2 id="runs-title">Ostatnie przebiegi</h2>
        <AppButton @click="refresh">Odśwież</AppButton>
      </div>
      <p v-if="runsLoading" class="loading">Wczytywanie…</p>
      <StatusMessage v-else-if="runsError" tone="error">{{ runsError }}</StatusMessage>
      <p v-else-if="runs.length === 0" class="empty">Jeszcze nie było przebiegu.</p>
      <ul v-else class="runs">
        <li v-for="run in runs" :key="run.id" class="run" :data-status="run.status">
          <p class="run-head">
            <strong>{{ label('runKind', run.kind) }}</strong>
            <StatusBadge class="status" :tone="runTone[run.status]">{{ label('runStatus', run.status) }}</StatusBadge>
            <span>{{ formatDateTime(run.createdAt) }}</span>
            <span v-if="run.sources.length">{{ run.sources.map((s) => label('source', s)).join(', ') }}</span>
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
              <StatusBadge v-else :tone="runTone[step.status]">
                {{ label('step', step.step) }} ({{ label('runStatus', step.status) }})
              </StatusBadge>
            </template>
          </p>
          <p v-if="run.error" class="run-error">{{ run.error }}</p>
        </li>
      </ul>
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
              próby {{ post.assessmentAttempts }}
              <template v-if="post.lastAttempt?.failureClass">
                · {{ label('failureClass', post.lastAttempt.failureClass) }}
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
.failed-posts {
  margin: 0;
  padding: 0;
  list-style: none;
}

.run,
.failed-posts li {
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--border);
}

.run p {
  margin: var(--space-0) 0;
}

.run-head,
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

.counters,
.steps,
.attempt {
  font-size: var(--font-size-sm);
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
</style>
