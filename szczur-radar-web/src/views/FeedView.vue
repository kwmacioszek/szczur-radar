<script setup lang="ts">
import { computed, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type Freshness, fetchFreshness } from '../api/freshness'
import { type Page, type PostItem, fetchPosts } from '../api/posts'
import AppButton from '../components/AppButton.vue'
import AppHeader from '../components/AppHeader.vue'
import PostCard from '../components/PostCard.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { formatDateTime } from '../labels'

const PAGE_SIZE = 50

const route = useRoute()
const router = useRouter()
const heading = useTemplateRef<HTMLHeadingElement>('feed-title')

/**
 * The page on screen: the requested page number with its answer. The label, the list and the
 * pager are all read from it, never from the address, so they always belong to the same answer.
 */
const shown = ref<{ page: number; result: Page<PostItem> } | null>(null)
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
const pages = computed(() => (shown.value ? Math.max(1, Math.ceil(shown.value.result.total / PAGE_SIZE)) : 1))

const loadingText = computed(() =>
  shown.value === null && loadingPage.value === 0
    ? 'Wczytywanie…'
    : `Wczytywanie strony ${(loadingPage.value ?? 0) + 1}…`,
)

/**
 * When the data was last updated, read once on entry alongside the first page, not on every page
 * change. Null while loading and after an error: the label is then left out and the list stays.
 */
const freshness = ref<Freshness | null>(null)
fetchFreshness()
  .then((result) => {
    freshness.value = result
  })
  .catch(() => {})

/** `moveFocus`: after a successful load, focus the heading, the start of the new list. */
async function load(target: number, { moveFocus = false } = {}): Promise<void> {
  const id = ++latestLoad
  loadingPage.value = target
  failed.value = false
  try {
    const result = await fetchPosts(target, PAGE_SIZE)
    if (id !== latestLoad) return
    shown.value = { page: target, result }
    loadingPage.value = null
    if (moveFocus) heading.value?.focus()
  } catch {
    if (id !== latestLoad) return
    failed.value = true
    loadingPage.value = null
  }
}

/** The retry button disappears while loading, so the focus goes to the heading afterwards. */
function retry(): void {
  void load(page.value, { moveFocus: true })
}

function goTo(target: number): void {
  void router.push({ query: { ...route.query, page: target > 0 ? String(target) : undefined } })
}

// The first entry neither scrolls nor takes the focus (e.g. from the header link); a page change
// (pager, back/forward) goes to the top at once and focuses the heading once the page is loaded.
watch(
  page,
  (next, previous) => {
    const changed = previous !== undefined
    if (changed) window.scrollTo({ top: 0 })
    void load(next, { moveFocus: changed })
  },
  { immediate: true },
)
</script>

<template>
  <AppHeader />
  <main class="feed">
    <!-- The heading and the label sit outside the list's busy region: paging does not change them. -->
    <h1 id="feed-title" ref="feed-title" tabindex="-1">Posty</h1>
    <p v-if="freshness" class="freshness">
      <template v-if="freshness.lastUpdatedAt">
        Dane zaktualizowane:
        <time :datetime="freshness.lastUpdatedAt">{{ formatDateTime(freshness.lastUpdatedAt) }}</time>
      </template>
      <template v-else>Dane nie były jeszcze aktualizowane.</template>
    </p>
    <section aria-labelledby="feed-title" :aria-busy="loadingPage !== null ? 'true' : undefined">
      <p v-if="loadingPage !== null" class="loading">{{ loadingText }}</p>
      <template v-else-if="failed">
        <StatusMessage tone="error">Nie udało się wczytać postów.</StatusMessage>
        <AppButton @click="retry">Spróbuj ponownie</AppButton>
      </template>
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
