<script setup lang="ts">
// The part "Nieudane posty" of the admin panel (/admin/posts): the failed, dead and on-hold posts, to retry or restore,
// a table of pages of FAILED_PAGE_SIZE (ui-design/prototypes/screens/admin-posts.html). The page is in the address
// (`?page=`, from 0; the first page without it), the selection covers the page on screen and is cleared by a page
// change, a retry and a restore. A restore reads the page again and asks the panel to read its count of failed posts
// again; a page past the last one (after a restore emptied it, or an old link) goes to the last page.
import { computed, nextTick, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type Run, retryPosts, startErrorMessage } from '../../api/pipeline'
import { FAILED_PAGE_SIZE, type FailedPost, fetchFailedPosts, restorePosts } from '../../api/posts'
import AppButton from '../../components/AppButton.vue'
import AppIcon from '../../components/AppIcon.vue'
import DataTable from '../../components/DataTable.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorState from '../../components/ErrorState.vue'
import LoadingState from '../../components/LoadingState.vue'
import PagerNav from '../../components/PagerNav.vue'
import StatusBadge, { type BadgeTone } from '../../components/StatusBadge.vue'
import StatusMessage from '../../components/StatusMessage.vue'
import { pageFromQuery } from '../../feedFilter'
import { restoreFocus } from '../../focus'
import { formatCount, formatDateTime, label, plural, postOrigin } from '../../labels'
import { useAdminPanel } from './adminPanel'
import { type Read, sectionFocus } from './sectionState'

const panel = useAdminPanel()
const route = useRoute()
const router = useRouter()

const failedHeading = useTemplateRef<HTMLElement>('failed-heading')
const failedErrorState = useTemplateRef<InstanceType<typeof ErrorState>>('failed-error-state')
const failedFocus = sectionFocus(failedHeading, failedErrorState)
const retryButton = useTemplateRef<InstanceType<typeof AppButton>>('retry-button')
const restoreButton = useTemplateRef<InstanceType<typeof AppButton>>('restore-button')
const outcome = useTemplateRef<InstanceType<typeof StatusMessage>>('outcome')

/** The page in the address (`?page=`, from 0). */
const page = computed(() => pageFromQuery(route.query))

const failed = ref<FailedPost[]>([])
/** All failed posts in the API, from the latest answer. */
const total = ref(0)
/** The page of the answer on screen. */
const shownPage = ref(0)
/** The page being read; null when no read is under way. */
const loadingPage = ref<number | null>(null)
/** Loading for the first time (and no error on screen); a later read keeps the list, or its empty state, on screen. */
const failedLoading = ref(true)
const failedError = ref(false)
const failedRetrying = ref(false)
let failedLoaded = false
/** The number of the latest read; the answer or error of any earlier one is dropped. */
let latestFailed = 0
/** One selection of the page, of both kinds: the dead posts in it are restored, the others retried. */
const selected = ref<string[]>([])
const retryingSelected = ref(false)
/** The runs a retry started; the message links the first. */
const retryRuns = ref<{ count: number; run: Run } | null>(null)
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

const pages = computed(() => Math.max(1, Math.ceil(total.value / FAILED_PAGE_SIZE)))
/** The pager's button towards the page being read, while the list stays on screen. */
const pagerBusy = computed<'previous' | 'next' | null>(() => {
  if (loadingPage.value === null || loadingPage.value === shownPage.value || failed.value.length === 0) return null
  return loadingPage.value > shownPage.value ? 'next' : 'previous'
})

/**
 * Reads page `target`. `after: 'page'`: a page change, so once it is on screen (or its error) the focus goes to the
 * section's heading, the pager's busy button having lost it.
 */
async function loadFailed(read: Read = {}, target = page.value, after: 'page' | 'none' = 'none'): Promise<void> {
  const current = ++latestFailed
  failedFocus.claim(read)
  failedRetrying.value = failedError.value
  failedLoading.value = !failedLoaded && !failedError.value
  loadingPage.value = target
  let ok = false
  let moved = false
  try {
    const answer = await fetchFailedPosts(target, FAILED_PAGE_SIZE)
    if (current !== latestFailed) return
    if (answer.items.length === 0 && answer.total > 0 && target > 0) {
      // Past the last page: that page is read instead, and its read settles this one's focus.
      moved = true
      void goTo(Math.ceil(answer.total / FAILED_PAGE_SIZE) - 1, 'replace')
      return
    }
    failed.value = answer.items
    total.value = answer.total
    shownPage.value = target
    selected.value = selected.value.filter((id) => stageOf.value.has(id))
    failedLoaded = true
    failedError.value = false
    ok = true
  } catch {
    if (current !== latestFailed) return
    failedError.value = true
  } finally {
    if (current === latestFailed && !moved) {
      loadingPage.value = null
      failedLoading.value = false
      failedRetrying.value = false
      await failedFocus.settle(ok)
      if (after === 'page') failedHeading.value?.focus()
      panel.sectionLoaded()
    }
  }
}

/** The page `target` in the address, the first one without `page`; the other keys of the address stay. */
function goTo(target: number, how: 'push' | 'replace' = 'push') {
  const query = { ...route.query, page: target > 0 ? String(target) : undefined }
  return how === 'push' ? router.push({ query }) : router.replace({ query })
}

// A page change (the pager, back or forward, the move to the last page) reads that page and clears the selection.
// Leaving the part changes the route before the part unmounts: nothing to read then.
watch(page, (next) => {
  if (route.name !== 'admin-posts') return
  selected.value = []
  void loadFailed({}, next, 'page')
})

/** Only the outcome of the latest action on the list stays on screen. */
function clearFailedMessages(): void {
  retryRuns.value = null
  retryError.value = null
  restoreMessage.value = null
  restoreError.value = null
}

/**
 * After an action the focus goes back to its button while it is enabled (an error keeps the selection), else to the
 * outcome's message: the busy button lost it, and the cleared selection disabled it.
 */
async function focusAfter(button: InstanceType<typeof AppButton> | null): Promise<void> {
  // The button and the message as the outcome draws them.
  await nextTick()
  const element = button?.$el as HTMLButtonElement | undefined
  await restoreFocus(element && !element.disabled ? element : ((outcome.value?.$el as Element | undefined) ?? null))
}

async function retrySelected(): Promise<void> {
  const postIds = selectedRetryable.value
  if (retryingSelected.value || restoring.value || postIds.length === 0) return
  retryingSelected.value = true
  clearFailedMessages()
  try {
    const runs = await retryPosts(postIds)
    if (runs[0]) retryRuns.value = { count: postIds.length, run: runs[0] }
    selected.value = []
  } catch (err) {
    retryError.value = startErrorMessage(err)
  } finally {
    retryingSelected.value = false
  }
  await focusAfter(retryButton.value)
}

async function restore(): Promise<void> {
  const postIds = selectedDead.value
  if (restoring.value || retryingSelected.value || postIds.length === 0) return
  restoring.value = true
  clearFailedMessages()
  let done = false
  try {
    const { restored } = await restorePosts(postIds)
    restoreMessage.value =
      restored === 0
        ? 'Żaden z zaznaczonych postów nie był już martwy.'
        : `Przywrócono ${restored} post(ów) do oceny. Oceni je kolejny przebieg.`
    selected.value = []
    done = true
  } catch {
    restoreError.value = 'Nie udało się przywrócić postów.'
  }
  if (done) {
    // A restored post is no longer on the list, nor in the count of the panel's bar.
    panel.refreshFailedCount()
    await loadFailed({ by: 'action' })
  }
  restoring.value = false
  await focusAfter(restoreButton.value)
}

/** The title, or the start of the text of a post without one. */
function excerpt(post: FailedPost): string {
  const text = post.title ?? post.body ?? ''
  return text.length > 120 ? `${text.slice(0, 120)}…` : text
}

/** The post's state in words with its tone: dead (no attempt left), on hold (its technical errors) or to retry. */
function stateOf(post: FailedPost): { text: string; tone: BadgeTone } {
  if (post.stage === 'dead') return { text: 'martwy', tone: 'destructive' }
  if (post.onHold) return { text: 'wstrzymany', tone: 'warning' }
  return { text: 'do ponowienia', tone: 'neutral' }
}

/**
 * The last failure in words: the class of the last attempt (not of a stored post on hold, which has no stored attempt
 * since it was stored: a restored one keeps its older lastAttempt) and the technical errors in a row (not of a dead
 * post), e.g. `przekroczony czas · błędy techniczne: 1`.
 */
function lastErrorText(post: FailedPost): string {
  const parts: string[] = []
  if (post.stage !== 'stored' && post.lastAttempt?.failureClass) parts.push(label('failureClass', post.lastAttempt.failureClass))
  if (post.stage !== 'dead' && post.technicalFailures > 0) parts.push(`błędy techniczne: ${post.technicalFailures}`)
  return parts.join(' · ')
}

function technicalError(post: FailedPost): string | null {
  return post.stage !== 'dead' && post.technicalFailures > 0 ? post.lastTechnicalError : null
}

onMounted(async () => {
  await loadFailed()
})
</script>

<template>
  <div class="admin-part">
    <section
      class="part-section"
      aria-labelledby="failed-title"
      :aria-busy="failedLoading || failedRetrying || loadingPage !== null ? 'true' : undefined"
    >
      <div class="section-head">
        <h2 id="failed-title" ref="failed-heading" class="section-title" tabindex="-1">Nieudane posty</h2>
        <span v-if="!failedError && total > 0" class="note total">
          {{ formatCount(total) }} {{ plural(total, 'post', 'posty', 'postów') }}
        </span>
      </div>
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
        <template #action>
          <RouterLink class="show-runs" :to="{ name: 'admin' }">Pokaż przebiegi</RouterLink>
        </template>
      </EmptyState>
      <template v-else>
        <DataTable class="section-state" caption="Posty, których ocena się nie udała">
          <thead>
            <tr>
              <th scope="col"><span class="visually-hidden">Zaznacz</span></th>
              <th scope="col">Post</th>
              <th scope="col">Źródło</th>
              <th scope="col">Stan</th>
              <th scope="col" class="num">Próby</th>
              <th scope="col">Ostatni błąd</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="post in failed"
              :key="post.id"
              class="failed-post"
              :class="{ 'is-selected': selected.includes(post.id) }"
              :data-stage="post.stage"
            >
              <td>
                <input v-model="selected" type="checkbox" :value="post.id" :aria-label="`Zaznacz post: ${excerpt(post)}`" />
              </td>
              <th scope="row" class="post-cell" :class="{ untitled: post.title === null }">
                <a
                  v-if="post.webUrl"
                  class="excerpt"
                  :href="post.webUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  :aria-label="`${excerpt(post)} (otwiera się w nowej karcie)`"
                >
                  {{ excerpt(post) }}<AppIcon class="external" name="i-external-link" />
                </a>
                <span v-else class="excerpt">{{ excerpt(post) }}</span>
              </th>
              <td class="origin">{{ postOrigin(post.source, post.channel) }}</td>
              <td>
                <StatusBadge class="state" :tone="stateOf(post).tone">{{ stateOf(post).text }}</StatusBadge>
              </td>
              <td class="num attempts" :class="{ zero: post.assessmentAttempts === 0 }">
                {{ formatCount(post.assessmentAttempts) }}
              </td>
              <td class="last-error">
                {{ lastErrorText(post) }}
                <span v-if="technicalError(post)" class="technical-error" :title="technicalError(post)!">
                  {{ technicalError(post) }}
                </span>
              </td>
            </tr>
          </tbody>
        </DataTable>
        <div class="actions">
          <span class="note chosen">Zaznaczone: {{ selected.length }}</span>
          <AppButton
            ref="retry-button"
            icon="i-refresh-cw"
            :disabled="selectedRetryable.length === 0 || restoring"
            :busy="retryingSelected"
            busy-label="Ponawianie…"
            @click="retrySelected"
          >
            Ponów zaznaczone ({{ selectedRetryable.length }})
          </AppButton>
          <AppButton
            ref="restore-button"
            icon="i-rotate-ccw"
            :disabled="selectedDead.length === 0 || retryingSelected"
            :busy="restoring"
            busy-label="Przywracanie…"
            @click="restore"
          >
            Przywróć martwe ({{ selectedDead.length }})
          </AppButton>
        </div>
      </template>
      <StatusMessage v-if="retryRuns" ref="outcome" tone="ok" class="message" tabindex="-1">
        Uruchomiono ponowienie: {{ retryRuns.count }} {{ plural(retryRuns.count, 'post', 'posty', 'postów') }}.
        <RouterLink :to="{ name: 'admin-run', params: { id: retryRuns.run.id } }">
          Szczegóły przebiegu z {{ formatDateTime(retryRuns.run.createdAt) }}
        </RouterLink>
      </StatusMessage>
      <StatusMessage v-if="retryError" tone="error" class="message">{{ retryError }}</StatusMessage>
      <StatusMessage v-if="restoreMessage" ref="outcome" tone="ok" class="message" tabindex="-1">{{ restoreMessage }}</StatusMessage>
      <StatusMessage v-if="restoreError" tone="error" class="message">{{ restoreError }}</StatusMessage>
      <PagerNav
        v-if="!failedError && failed.length > 0"
        class="pager"
        :page="shownPage"
        :pages="pages"
        labels="order"
        :busy="pagerBusy"
        @previous="goTo(shownPage - 1)"
        @next="goTo(shownPage + 1)"
      />
    </section>
  </div>
</template>

<style scoped>
/* The part starts apart from the bar of the parts (the design's `.section`). */
.part-section {
  margin-top: var(--space-8);
}

.section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-4);
}

.section-title {
  margin: 0;
  font-size: var(--font-size-4xl);
}

.note {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

/* The state of a section (loading, error, empty) and the table stand apart from the heading above them. */
.section-state {
  margin-top: var(--space-4);
}

/* A message standing alone in its section, apart from what is above it; the part's selector outranks the message's
   own zero margin. */
.admin-part .message {
  margin-block: var(--space-3) 0;
}

/* The pager stands apart from the actions and messages above it. */
.pager {
  margin-top: var(--space-5);
}

/* The post's title in the table's row header: the strong cell of the design, a long one wraps within its width. */
.post-cell {
  min-width: 14rem;
  max-width: 28rem;
  color: var(--text-h);
  font-weight: var(--weight-medium);
  white-space: normal;
}

/* A post without a title shows the start of its text, which does not pose as a title. */
.post-cell.untitled {
  color: var(--text);
  font-weight: var(--weight-regular);
}

.excerpt {
  overflow-wrap: anywhere;
}

.external {
  --icon-size: 0.9375rem;

  margin-inline-start: var(--space-1);
  vertical-align: middle;
}

.last-error {
  color: var(--text);
}

/* One line, cut with an ellipsis; the full text is in its title (and in the text, for a screen reader). */
.technical-error {
  display: block;
  max-width: 18rem;
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  margin-top: var(--space-4);
}

.actions .note {
  margin-right: auto;
}
</style>
