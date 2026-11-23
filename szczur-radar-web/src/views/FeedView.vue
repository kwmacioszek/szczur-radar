<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { FEED_PAGE_SIZE, type Page, type PostFilter, type PostItem, fetchPosts } from '../api/posts'
import AppButton from '../components/AppButton.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import FeedFilter from '../components/FeedFilter.vue'
import LoadingState from '../components/LoadingState.vue'
import PageHeader from '../components/PageHeader.vue'
import PagerNav from '../components/PagerNav.vue'
import PostCard from '../components/PostCard.vue'
import { filterFromQuery, filterToQuery, isFilterActive, pageFromQuery, sameFilter } from '../feedFilter'
import { reportFeedLoaded } from '../router/scroll'

const route = useRoute()
const router = useRouter()
const header = useTemplateRef<InstanceType<typeof PageHeader>>('header')
const errorState = useTemplateRef<InstanceType<typeof ErrorState>>('error-state')

/**
 * The page on screen: the requested page number and filter with their answer. The result line, the
 * list and the pager are all read from it, never from the address, so they always belong to the same
 * answer.
 */
const shown = ref<{ page: number; filter: PostFilter; result: Page<PostItem> } | null>(null)

/**
 * How a load replaces what is on screen:
 * - `fresh`: the first entry, a filter change, or a page change with no list on screen: a skeleton;
 * - `page`: a page change at the same filter with the list on screen: the list stays, the pager's button towards the
 *   target page is busy;
 * - `retry`: "Spróbuj ponownie": the error stays, its button busy, until the answer.
 */
type LoadKind = 'fresh' | 'page' | 'retry'

/** The load in progress (its page from 0 and its kind); null when none is. */
const loading = ref<{ page: number; kind: LoadKind } | null>(null)
/** The last load that ended failed; its error stays on screen during a retry. */
const failed = ref(false)
/** The number of the latest load; the answer or error of any earlier one is dropped. */
let latestLoad = 0

/** The page in the address (`?page=`, from 0), so a refresh keeps it. */
const page = computed(() => pageFromQuery(route.query))
/** The filter in the address (`?q=&kind=&direction=`); unknown values are dropped. */
const filter = computed(() => filterFromQuery(route.query))
const pages = computed(() => (shown.value ? Math.max(1, Math.ceil(shown.value.result.total / FEED_PAGE_SIZE)) : 1))
/** The answer on screen has no post for its filter. */
const noMatch = computed(
  () => shown.value !== null && shown.value.result.total === 0 && isFilterActive(shown.value.filter),
)
/** The cards with their pager are on screen, so a page change at the same filter can leave them there. */
const listOnScreen = computed(() => !failed.value && shown.value !== null && shown.value.result.items.length > 0)

/** The error stays during its own retry; any other load replaces it. */
const showError = computed(() => failed.value && (loading.value === null || loading.value.kind === 'retry'))
/** The skeleton: a load whose answer replaces what is on screen. */
const showSkeleton = computed(() => loading.value !== null && loading.value.kind === 'fresh')

/** The pager's button towards the page being loaded, while the list stays. */
const pagerBusy = computed<'previous' | 'next' | null>(() => {
  if (loading.value?.kind !== 'page' || !shown.value) return null
  return loading.value.page > shown.value.page ? 'next' : 'previous'
})

/** The line over the list, announced by its status region; empty while a new list loads and after an error. */
const resultText = computed(() => {
  if (failed.value || !shown.value || (loading.value !== null && loading.value.kind !== 'page')) return ''
  const { total } = shown.value.result
  if (!isFilterActive(shown.value.filter)) return total > 0 ? `Ocenione posty: ${total}` : ''
  return total > 0 ? `Pasujące do filtra: ${total}` : 'Żaden post nie pasuje do filtra.'
})

/** What stayed after the error: the filter in the form, when there is one. */
const errorText = computed(() =>
  isFilterActive(filter.value) ? 'Serwer nie odpowiedział. Twoje filtry zostały.' : 'Serwer nie odpowiedział.',
)

/**
 * After the load, where the view goes:
 * - `top`: a page change: once the page (or its error) is on screen, to the top and the focus to the heading; never at
 *   the click, which would hide the busy button under the list;
 * - `heading`: the focus to the heading after a successful load (the empty result's "Wyczyść filtry" is gone);
 * - `retry`: the heading after a success, "Spróbuj ponownie" again after another failure (the busy button lost it);
 * - `none`: the first entry and a filter change: the focus stays in the form and the result line announces the count.
 */
type After = 'top' | 'heading' | 'retry' | 'none'

/**
 * Each load that is still the latest one reports how it ended, so a return from a post can restore the position once
 * the list is on screen (router/scroll.ts).
 */
async function load(target: number, conditions: PostFilter, kind: LoadKind, after: After): Promise<void> {
  const id = ++latestLoad
  loading.value = { page: target, kind }
  // A retry keeps its error until the answer; any other load replaces it.
  if (kind !== 'retry') failed.value = false
  try {
    const result = await fetchPosts(target, FEED_PAGE_SIZE, conditions)
    if (id !== latestLoad) return
    shown.value = { page: target, filter: conditions, result }
    failed.value = false
    loading.value = null
    await nextTick()
    if (id !== latestLoad) return
    if (after === 'top') window.scrollTo({ top: 0 })
    if (after !== 'none') header.value?.focus()
    reportFeedLoaded('ok')
  } catch {
    if (id !== latestLoad) return
    failed.value = true
    loading.value = null
    await nextTick()
    if (id !== latestLoad) return
    if (after === 'top') {
      window.scrollTo({ top: 0 })
      header.value?.focus()
    } else if (after === 'retry') {
      errorState.value?.focus()
    }
    reportFeedLoaded('error')
  }
}

// A load that ends after the view is gone (e.g. forward to a post mid-load) is dropped like an
// overtaken one, so it never reports to a later return to the feed (router/scroll.ts).
onBeforeUnmount(() => {
  latestLoad++
})

/** The same page and filter again; the error stays with its busy button until the answer. */
function retry(): void {
  void load(page.value, filter.value, 'retry', 'retry')
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
// (pager, back/forward) goes to the top and focuses the heading once the page is loaded. A filter
// change does neither: the focus stays in the form and the result line announces the count.
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
    const after: After = pageOnly ? 'top' : focusAfterClear ? 'heading' : 'none'
    focusAfterClear = false
    void load(nextPage, nextFilter, pageOnly && listOnScreen.value ? 'page' : 'fresh', after)
  },
  { immediate: true },
)
</script>

<template>
  <div class="feed">
    <section aria-labelledby="feed-title">
      <PageHeader ref="header" class="head" title="Posty" heading-id="feed-title" />
      <FeedFilter :filter="filter" @apply="applyFilter" @clear="clearFilter" />
      <!-- In the document from the start, so a screen reader announces each new count. -->
      <p class="result" role="status">{{ resultText }}</p>
      <!-- Only the list is busy while a page loads: the heading, the label, the form and the count do not change. -->
      <div class="posts" :aria-busy="loading !== null ? 'true' : undefined">
        <ErrorState
          v-if="showError"
          ref="error-state"
          title-tag="h2"
          title="Nie udało się wczytać postów"
          :retrying="loading !== null"
          @retry="retry"
        >
          {{ errorText }}
        </ErrorState>
        <LoadingState v-else-if="showSkeleton" shape="rows" />
        <!-- The result line says that nothing matches; the way out stays next to it. -->
        <EmptyState v-else-if="noMatch" title-tag="h2" icon="i-search" title="Brak postów dla tych filtrów">
          Zmień filtr albo wyczyść go, żeby zobaczyć wszystkie posty.
          <template #action>
            <AppButton @click="clearFromEmptyResult">Wyczyść filtry</AppButton>
          </template>
        </EmptyState>
        <EmptyState
          v-else-if="shown && shown.result.total === 0"
          title-tag="h2"
          icon="i-inbox"
          title="Brak ocenionych postów"
        >
          Pojawią się po najbliższej aktualizacji danych.
        </EmptyState>
        <!-- An old link past the last page: the API answers with no items and the real total. -->
        <EmptyState
          v-else-if="shown && shown.result.items.length === 0"
          title-tag="h2"
          icon="i-inbox"
          :title="`Nie ma strony ${shown.page + 1}`"
        >
          Posty kończą się na stronie {{ pages }}.
          <template #action>
            <AppButton @click="goTo(pages - 1)">Przejdź do strony {{ pages }}</AppButton>
          </template>
        </EmptyState>
        <template v-else-if="shown">
          <PostCard v-for="post in shown.result.items" :key="post.id" :post="post" />
          <PagerNav
            class="list-pager"
            :page="shown.page"
            :pages="pages"
            :busy="pagerBusy"
            @previous="goTo(shown.page - 1)"
            @next="goTo(shown.page + 1)"
          />
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* The page's width is the shell's column's; the title stands apart from the filter below it. */
.head {
  margin-bottom: var(--space-4);
}

.result {
  margin: var(--space-4) 0;
  color: var(--text-h);
}

/* The cards keep their own spacing; the pager stands apart under the last one. */
.list-pager {
  margin-top: var(--space-5);
}
</style>
