<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { type PostItem, fetchPost, fetchPostRaw } from '../api/posts'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'
import PageHeader from '../components/PageHeader.vue'
import PostAssessment from '../components/PostAssessment.vue'
import { formatDateTime, postOrigin, postType } from '../labels'

const route = useRoute()
const router = useRouter()
const header = useTemplateRef<InstanceType<typeof PageHeader>>('header')
const rawSummary = useTemplateRef<HTMLElement>('raw-summary')
const errorState = useTemplateRef<InstanceType<typeof ErrorState>>('error-state')

/** `missing`: 404 or 400, an id that is not an assessed post; `failed`: any other error. */
type LoadState = 'loading' | 'failed' | 'missing' | 'loaded'
/** The source JSON, fetched on the first opening of its section. */
type RawState = 'idle' | 'loading' | 'failed' | 'loaded'

const state = ref<LoadState>('loading')
/** A retry of the post is in progress: its error stays, with the button busy, until the answer. */
const retrying = ref(false)
const post = ref<PostItem | null>(null)
const rawState = ref<RawState>('idle')
/** A retry of the source data is in progress, as `retrying` for the post. */
const rawRetrying = ref(false)
const raw = ref('')
/** The number of the latest load (of the post, of its raw data); the answer or error of any earlier one is dropped. */
let latestLoad = 0
let latestRawLoad = 0

/** The id in the address; null while the router leaves this route. */
const postId = computed(() => (route.name === 'post' && typeof route.params.id === 'string' ? route.params.id : null))

/** The title; without one, "{Type} użytkownika {author}", or the type alone; "Post" until it is loaded. */
const title = computed(() => {
  const item = post.value
  if (!item) return 'Post'
  if (item.title) return item.title
  const type = postType(item.source, item.itemType)
  const capitalized = type.charAt(0).toUpperCase() + type.slice(1)
  return item.author ? `${capitalized} użytkownika ${item.author}` : capitalized
})

/** The original on the source's site: only an http(s) address becomes a link. */
const original = computed(() => {
  const address = post.value?.webUrl
  if (!address) return null
  try {
    const url = new URL(address)
    return url.protocol === 'https:' || url.protocol === 'http:' ? { href: url.href, host: url.hostname } : null
  } catch {
    return null
  }
})

/**
 * `retry`: from the error's own button, whose error stays until the answer. The busy button loses the focus, so after
 * the answer it goes to the heading, or back to "Spróbuj ponownie" after another failure.
 */
async function load(id: string, { retry = false } = {}): Promise<void> {
  const current = ++latestLoad
  latestRawLoad++
  retrying.value = retry
  if (!retry) state.value = 'loading'
  post.value = null
  rawState.value = 'idle'
  rawRetrying.value = false
  raw.value = ''
  try {
    const result = await fetchPost(id)
    if (current !== latestLoad) return
    post.value = result
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

/** `retry`: from the error's own button; the error stays, with the button busy, until the answer. */
async function loadRaw({ retry = false } = {}): Promise<void> {
  const item = post.value
  if (!item) return
  const current = ++latestRawLoad
  rawRetrying.value = retry
  if (!retry) rawState.value = 'loading'
  try {
    const text = await fetchPostRaw(item.id)
    if (current !== latestRawLoad) return
    raw.value = text
    rawState.value = 'loaded'
  } catch {
    if (current !== latestRawLoad) return
    rawState.value = 'failed'
  } finally {
    if (current === latestRawLoad) rawRetrying.value = false
  }
}

/** The busy retry button loses the focus, so it moves to the section's summary first. */
function retryRaw(): void {
  rawSummary.value?.focus()
  void loadRaw({ retry: true })
}

/** The first opening fetches the source JSON; a later one shows what is there (retry is a button). */
function onRawToggle(event: Event): void {
  if ((event.target as HTMLDetailsElement).open && rawState.value === 'idle') void loadRaw()
}

function retry(): void {
  if (postId.value !== null) void load(postId.value, { retry: true })
}

/** The previous history entry when it is the feed (e.g. `/?page=2`); null after a deep link. */
function previousFeed(): string | null {
  const back = router.options.history.state.back
  return typeof back === 'string' && router.resolve(back).name === 'home' ? back : null
}

/** Re-read on every navigation: the history entry behind this one changes with it. */
const listHref = computed(() => {
  void route.fullPath
  return previousFeed() ?? router.resolve({ name: 'home' }).href
})

/** Back to the same place of the list when the user came from it; to the list's start otherwise. */
function toList(event: MouseEvent): void {
  // A new tab or window (modifier keys, middle button) follows the href.
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) return
  event.preventDefault()
  if (previousFeed() !== null) router.back()
  else void router.push({ name: 'home' })
}

watch(
  postId,
  (id) => {
    if (id !== null) void load(id)
  },
  { immediate: true },
)
</script>

<template>
  <div class="post-detail">
    <a class="back" :href="listHref" @click="toList"><span aria-hidden="true">←</span> Posty</a>
    <section aria-labelledby="post-title" :aria-busy="state === 'loading' || retrying ? 'true' : undefined">
      <PageHeader ref="header" class="head" :title="title" heading-id="post-title" size="reading" />
      <LoadingState v-if="state === 'loading'" class="view-state" shape="lines" :count="4" />
      <ErrorState
        v-else-if="state === 'failed'"
        ref="error-state"
        class="view-state"
        title-tag="h2"
        title="Nie udało się wczytać posta"
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
        title="Nie ma takiego posta"
      >
        Post nie istnieje albo nie został jeszcze oceniony.
        <template #action>
          <RouterLink :to="{ name: 'home' }">Przejdź do listy postów</RouterLink>
        </template>
      </EmptyState>
      <template v-else-if="post">
        <p class="meta">
          <time :datetime="post.publishedAt">{{ formatDateTime(post.publishedAt) }}</time>
          <span class="type">{{ postType(post.source, post.itemType) }}</span>
          <!-- API and web deploy independently: an older API has no source or channel, so the origin is left out. -->
          <span v-if="post.source" class="origin">{{ postOrigin(post.source, post.channel) }}</span>
          <span v-if="post.author" class="author">{{ post.author }}</span>
        </p>
        <p v-if="original">
          <a
            class="original"
            :href="original.href"
            target="_blank"
            rel="noopener noreferrer"
            :aria-label="`Otwórz oryginał na ${original.host} (otwiera się w nowej karcie)`"
            >Otwórz oryginał na {{ original.host }}</a
          >
        </p>
        <p v-if="post.body" class="body">{{ post.body }}</p>

        <section class="part" aria-labelledby="assessment-title">
          <h2 id="assessment-title">Ocena</h2>
          <PostAssessment v-if="post.assessment" :assessment="post.assessment" />
          <p v-else class="none">Brak oceny.</p>
        </section>

        <details class="part raw" @toggle="onRawToggle">
          <summary ref="raw-summary">Dane dla nerdów: surowy JSON źródła</summary>
          <LoadingState v-if="rawState === 'loading'" class="raw-state" shape="lines" />
          <ErrorState
            v-else-if="rawState === 'failed'"
            class="raw-state"
            title="Nie udało się wczytać danych źródła"
            :retrying="rawRetrying"
            @retry="retryRaw"
          >
            Ocena posta zostaje bez zmian.
          </ErrorState>
          <pre v-else-if="rawState === 'loaded'">{{ raw }}</pre>
        </details>
      </template>
    </section>
  </div>
</template>

<style scoped>
.back {
  display: inline-block;
  margin-top: var(--space-2);
}

/* The title stands apart from the link back above it and from the post below it. */
.head {
  margin: var(--space-4) 0 var(--space-2);
}

h2 {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-md);
  color: var(--text-h);
}

p {
  margin: 0 0 var(--space-3);
}

/* The state of the post (loading, error, none) stands apart from the title above it. */
.view-state {
  margin-top: var(--space-4);
}

/* "Brak oceny." */
.none {
  margin: var(--space-4) 0;
}

/* Under the summary, as the data. */
.raw-state {
  margin-top: var(--space-3);
}

/* Date and time stay on one line; the rest wraps. */
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-3);
  font-size: var(--font-size-sm);
}

.meta time {
  color: var(--text-h);
  font-weight: var(--weight-medium);
  white-space: nowrap;
}

.origin,
.author {
  overflow-wrap: anywhere;
}

/* What is read is set in the serif. The post's own line breaks; a long URL wraps instead of widening the page. */
.body {
  color: var(--text-h);
  font-family: var(--font-display);
  line-height: var(--leading-relaxed);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.part {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--line);
}

summary {
  color: var(--accent);
  cursor: pointer;
}

/* Wraps at any character: no horizontal scroll of the page at 375 px. */
pre {
  margin: var(--space-3) 0 0;
  padding: var(--space-3);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--text-h);
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
