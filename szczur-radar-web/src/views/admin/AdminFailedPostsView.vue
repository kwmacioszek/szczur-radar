<script setup lang="ts">
// The part "Nieudane posty" of the admin panel (/admin/posts): the failed, dead and on-hold posts, to retry or restore.
// A restore reads the list again and asks the panel to read its count of failed posts again.
import { computed, onMounted, ref, useTemplateRef } from 'vue'
import { retryPosts, startErrorMessage } from '../../api/pipeline'
import { type FailedPost, fetchFailedPosts, restorePosts } from '../../api/posts'
import AppButton from '../../components/AppButton.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import LoadingState from '../../components/LoadingState.vue'
import StatusMessage from '../../components/StatusMessage.vue'
import { label } from '../../labels'
import { useAdminPanel } from './adminPanel'
import { type Read, sectionFocus } from './sectionState'

const panel = useAdminPanel()

const failedHeading = useTemplateRef<HTMLElement>('failed-heading')
const failedErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('failed-error-state')
const failedFocus = sectionFocus(failedHeading, failedErrorState)

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
      panel.sectionLoaded()
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
    // A restored post is no longer on the list, nor in the count of the panel's bar.
    panel.refreshFailedCount()
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

onMounted(async () => {
  await loadFailed()
})
</script>

<template>
  <div class="admin-part">
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
h2 {
  margin: var(--space-5) 0 var(--space-2);
  font-size: var(--font-size-2xl);
  color: var(--text-h);
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

.failed-posts {
  margin: 0;
  padding: 0;
  list-style: none;
}

.failed-posts li {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--line);
}

.alarm {
  color: var(--destructive);
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
  font-size: var(--font-size-sm);
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
