<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type Freshness, fetchFreshness } from '../api/freshness'
import { type Page, type PostFilter, type PostItem, fetchPosts } from '../api/posts'
import AppButton from '../components/AppButton.vue'
import AppHeader from '../components/AppHeader.vue'
import FeedFilter from '../components/FeedFilter.vue'
import PostCard from '../components/PostCard.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { filterFromQuery, filterToQuery, isFilterActive, sameFilter } from '../feedFilter'
import { formatDateTime } from '../labels'
import { reportFeedLoaded } from '../router/scroll'

const PAGE_SIZE = 50

const route = useRoute()
const router = useRouter()
const heading = useTemplateRef<HTMLHeadingElement>('feed-title')

/**
 * The page on screen: the requested page number and filter with their answer. The result line, the
 * list and the pager are all read from it, never from the address, so they always belong to the same
 * answer.
 */
const shown = ref<{ page: number; filter: PostFilter; result: Page<PostItem> } | null>(null)
/** The page being loaded (from 0); null when no load is in progress. */
const loadingPage = ref<number | null>(null)
const failed = ref(false)
/** The number of the latest load; the answer or error of any earlier one is dropped. */
let latestLoad = 0

/** The page in the address (`?page=`, from 0), so a refresh keeps it. */
const page = computed(() => {
  const value = Number.parseInt(String(route.query.page ?? '0'), 10)
  return Number.isFinite(value) && value > 0 ? value : 0
})
/** The filter in the address (`?q=&kind=&direction=`); unknown values are dropped. */
const filter = computed(() => filterFromQuery(route.query))
const pages = computed(() => (shown.value ? Math.max(1, Math.ceil(shown.value.result.total / PAGE_SIZE)) : 1))
/** The answer on screen has no post for its filter. */
const noMatch = computed(
  () => shown.value !== null && shown.value.result.total === 0 && isFilterActive(shown.value.filter),
)

const loadingText = computed(() =>
  shown.value === null && loadingPage.value === 0
    ? 'Wczytywanie…'
    : `Wczytywanie strony ${(loadingPage.value ?? 0) + 1}…`,
)

/** The line over the list, announced by its status region; empty while loading and after an error. */
const resultText = computed(() => {
  if (loadingPage.value !== null || failed.value || !shown.value) return ''
  const { total } = shown.value.result
  if (!isFilterActive(shown.value.filter)) return total > 0 ? `Ocenione posty: ${total}` : ''
  return total > 0 ? `Pasujące do filtra: ${total}` : 'Żaden post nie pasuje do filtra.'
})

/**
 * When the data was last updated, read once on entry alongside the first page, not on every page
 * change. Null while loading and after an error: the label is then left out and the list stays.
 * `freshnessSettled` resolves (never rejects) once the label is shown or left out.
 */
const freshness = ref<Freshness | null>(null)
const freshnessSettled = fetchFreshness()
  .then((result) => {
    freshness.value = result
  })
  .catch(() => {})

/**
 * `moveFocus`: after a successful load, focus the heading, the start of the new list. Each load
 * that is still the latest one reports how it ended, so a return from a post can restore the
 * position once the list is on screen (router/scroll.ts). A list is reported only after the
 * freshness label is settled: the label sits above the list, so the page then has the height the
 * saved position was taken at.
 */
async function load(target: number, conditions: PostFilter, { moveFocus = false } = {}): Promise<void> {
  const id = ++latestLoad
  loadingPage.value = target
  failed.value = false
  try {
    const result = await fetchPosts(target, PAGE_SIZE, conditions)
    if (id !== latestLoad) return
    shown.value = { page: target, filter: conditions, result }
    loadingPage.value = null
    if (moveFocus) heading.value?.focus()
    await freshnessSettled
    await nextTick()
    if (id !== latestLoad) return
    reportFeedLoaded('ok')
  } catch {
    if (id !== latestLoad) return
    failed.value = true
    loadingPage.value = null
    reportFeedLoaded('error')
  }
}

// A load that ends after the view is gone (e.g. forward to a post mid-load) is dropped like an
// overtaken one, so it never reports to a later return to the feed (router/scroll.ts).
onBeforeUnmount(() => {
  latestLoad++
})

/** The retry button disappears while loading, so the focus goes to the heading afterwards. */
function retry(): void {
  void load(page.value, filter.value, { moveFocus: true })
}

/** The filter keys stay in the address. */
function goTo(target: number): void {
  void router.push({ query: { ...route.query, page: target > 0 ? String(target) : undefined } })
}

/**
 * Set while the form's navigation is in flight: its return to the first page is a filter action, even
 * when the filter itself is unchanged, so it neither scrolls nor takes the focus.
 */
let fromForm = false

/** A new filter starts at the first page: `page` leaves the address. */
async function applyFilter(next: PostFilter): Promise<void> {
  fromForm = true
  try {
    await router.push({ query: filterToQuery(next) })
  } finally {
    fromForm = false
  }
}

function clearFilter(): void {
  void router.push({ query: {} })
}

/** Set by the empty result's own button: it disappears while loading, like the retry button. */
let focusAfterClear = false

function clearFromEmptyResult(): void {
  focusAfterClear = true
  clearFilter()
}

// The first entry neither scrolls nor takes the focus (e.g. from the header link). A page change
// (pager, back/forward) goes to the top at once and focuses the heading once the page is loaded. A
// filter change does neither: the focus stays in the form and the result line announces the count.
watch(
  [page, filter],
  ([nextPage, nextFilter], [previousPage, previousFilter]) => {
    // The first call has no previous values.
    const first = previousPage === undefined || previousFilter === undefined
    const filterChanged = !first && !sameFilter(nextFilter, previousFilter)
    const pageChanged = !first && nextPage !== previousPage
    // Another query key changed (or none: the filter is recomputed for every change of the address).
    if (!first && !filterChanged && !pageChanged) return
    const pageOnly = pageChanged && !filterChanged && !fromForm
    if (pageOnly) window.scrollTo({ top: 0 })
    const moveFocus = pageOnly || focusAfterClear
    focusAfterClear = false
    void load(nextPage, nextFilter, { moveFocus })
  },
  { immediate: true },
)
</script>

<template>
  <AppHeader />
  <main class="feed">
    <section aria-labelledby="feed-title">
      <h1 id="feed-title" ref="feed-title" tabindex="-1">Posty</h1>
      <p v-if="freshness" class="freshness">
        <template v-if="freshness.lastUpdatedAt">
          Dane zaktualizowane:
          <time :datetime="freshness.lastUpdatedAt">{{ formatDateTime(freshness.lastUpdatedAt) }}</time>
        </template>
        <template v-else>Dane nie były jeszcze aktualizowane.</template>
      </p>
      <FeedFilter :filter="filter" @apply="applyFilter" @clear="clearFilter" />
      <!-- In the document from the start, so a screen reader announces each new count. -->
      <p class="result" role="status">{{ resultText }}</p>
      <!-- Only the list is busy while a page loads: the heading, the label, the form and the count do not change. -->
      <div class="posts" :aria-busy="loadingPage !== null ? 'true' : undefined">
        <p v-if="loadingPage !== null" class="loading">{{ loadingText }}</p>
        <template v-else-if="failed">
          <StatusMessage tone="error">Nie udało się wczytać postów.</StatusMessage>
          <AppButton @click="retry">Spróbuj ponownie</AppButton>
        </template>
        <!-- The result line says that nothing matches; the way out stays next to it. -->
        <AppButton v-else-if="noMatch" @click="clearFromEmptyResult">Wyczyść filtry</AppButton>
        <p v-else-if="shown && shown.result.total === 0" class="empty">
          Brak ocenionych postów. Pojawią się po najbliższej aktualizacji danych.
        </p>
        <!-- An old link past the last page: the API answers with no items and the real total. -->
        <template v-else-if="shown && shown.result.items.length === 0">
          <p class="empty">Nie ma strony {{ shown.page + 1 }}. Posty kończą się na stronie {{ pages }}.</p>
          <AppButton @click="goTo(pages - 1)">Przejdź do strony {{ pages }}</AppButton>
        </template>
        <template v-else-if="shown">
          <PostCard v-for="post in shown.result.items" :key="post.id" :post="post" />
          <nav class="pager" aria-label="Strony">
            <AppButton :disabled="shown.page === 0" @click="goTo(shown.page - 1)">Nowsze</AppButton>
            <span class="position">Strona {{ shown.page + 1 }} z {{ pages }}</span>
            <AppButton :disabled="shown.page + 1 >= pages" @click="goTo(shown.page + 1)">Starsze</AppButton>
          </nav>
        </template>
      </div>
    </section>
  </main>
</template>

<style scoped>
.feed {
  max-width: 48rem;
  margin: 0 auto;
  padding: var(--space-4);
  box-sizing: border-box;
}

h1 {
  margin: var(--space-2) 0 var(--space-4);
}

/* Closer to the heading than to the list it describes. */
h1:has(+ .freshness) {
  margin-bottom: var(--space-2);
}

.freshness {
  margin: 0 0 var(--space-4);
  font-size: var(--font-size-sm);
  color: var(--text);
}

.result {
  margin: var(--space-4) 0;
  color: var(--text-h);
}

.loading,
.empty {
  margin: var(--space-4) 0;
}

/* The cards keep their own spacing; the pager sits under the last one. */
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) 0;
}
</style>
