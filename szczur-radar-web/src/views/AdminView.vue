<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  type Run,
  type SourceState,
  fetchRuns,
  fetchSources,
  retryPosts,
  startErrorMessage,
  startRange,
} from '../api/pipeline'
import { type FailedPost, fetchFailedPosts } from '../api/posts'
import AppHeader from '../components/AppHeader.vue'
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
const chosenSources = ref<string[]>([])
const starting = ref(false)
const startMessage = ref<string | null>(null)
const startError = ref<string | null>(null)

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
const runsError = ref<string | null>(null)

async function loadRuns(): Promise<void> {
  runsError.value = null
  try {
    runs.value = (await fetchRuns()).items
  } catch {
    runsError.value = 'Nie udało się wczytać przebiegów.'
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
  await Promise.all([loadRuns(), loadFailed()])
}

onMounted(async () => {
  try {
    sources.value = await fetchSources()
    chosenSources.value = sources.value.filter((s) => s.configured).map((s) => s.source)
  } catch {
    startError.value = 'Nie udało się wczytać listy źródeł.'
  }
  await refresh()
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
        <button type="submit" :disabled="starting || chosenSources.length === 0">Uruchom</button>
      </form>
      <p v-if="startMessage" class="ok" role="status">{{ startMessage }}</p>
      <p v-if="startError" class="error" role="alert">{{ startError }}</p>
    </section>

    <section aria-labelledby="runs-title">
      <div class="section-head">
        <h2 id="runs-title">Ostatnie przebiegi</h2>
        <button type="button" @click="refresh">Odśwież</button>
      </div>
      <p v-if="runsError" class="error" role="alert">{{ runsError }}</p>
      <p v-else-if="runs.length === 0" class="empty">Jeszcze nie było przebiegu.</p>
      <ul v-else class="runs">
        <li v-for="run in runs" :key="run.id" class="run" :data-status="run.status">
          <p class="run-head">
            <strong>{{ label('runKind', run.kind) }}</strong>
            <span class="status" :class="run.status">{{ label('runStatus', run.status) }}</span>
            <span>{{ formatDateTime(run.createdAt) }}</span>
            <span v-if="run.sources.length">{{ run.sources.map((s) => label('source', s)).join(', ') }}</span>
          </p>
          <p class="counters">
            {{ counters(run) }}
            · <span :class="{ alarm: run.processingErrors > 0 }">błędy techniczne {{ run.processingErrors }}</span>
          </p>
          <p v-if="run.steps.length" class="steps">
            <span v-for="step in run.steps" :key="step.step + step.startedAt" :class="step.status">
              {{ label('step', step.step) }}<template v-if="step.itemsOut !== null"> ({{ step.itemsOut }})</template>
            </span>
          </p>
          <p v-if="run.error" class="run-error">{{ run.error }}</p>
        </li>
      </ul>
    </section>

    <section aria-labelledby="failed-title">
      <h2 id="failed-title">Nieudane posty</h2>
      <p v-if="failedError" class="error" role="alert">{{ failedError }}</p>
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
              <template v-if="post.lastAttempt?.failureClass"> · {{ post.lastAttempt.failureClass }}</template>
            </span>
          </li>
        </ul>
        <button type="button" :disabled="retrying || selected.length === 0" @click="retry">
          Ponów zaznaczone
        </button>
      </template>
      <p v-if="retryMessage" class="ok" role="status">{{ retryMessage }}</p>
      <p v-if="retryError" class="error" role="alert">{{ retryError }}</p>
    </section>
  </main>
</template>

<style scoped>
.admin {
  max-width: 56rem;
  margin: 0 auto;
  padding: 16px;
  box-sizing: border-box;
}

h1 {
  margin: 8px 0 16px;
  font-size: 32px;
}

h2 {
  margin: 24px 0 8px;
  font-size: 20px;
  color: var(--text-h);
}

.start {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
}

.sources {
  display: flex;
  gap: 12px;
  margin: 0;
  padding: 4px 8px;
  border: 1px solid color-mix(in srgb, var(--text) 30%, transparent);
  border-radius: 6px;
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
  padding: 8px 0;
  border-bottom: 1px solid color-mix(in srgb, var(--text) 20%, transparent);
}

.run p {
  margin: 2px 0;
}

.run-head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.status.succeeded {
  color: #188038;
}

.status.partial,
.status.interrupted {
  color: #b06000;
}

.status.failed,
.alarm,
.run-error,
.error {
  color: #d93025;
}

.counters,
.steps,
.attempt {
  font-size: 14px;
}

.steps span {
  margin-right: 8px;
}

.steps .failed {
  color: #d93025;
}

.run-error {
  font-size: 14px;
  overflow-wrap: anywhere;
}

.failed-posts li {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4px 12px;
}

.excerpt {
  overflow-wrap: anywhere;
}

.ok {
  color: #188038;
}
</style>
