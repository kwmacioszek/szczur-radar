<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { type PostItem, fetchPost, fetchPostRaw } from '../api/posts'
import AppButton from '../components/AppButton.vue'
import AppHeader from '../components/AppHeader.vue'
import PostAssessment from '../components/PostAssessment.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { formatDateTime, label } from '../labels'

const route = useRoute()
const router = useRouter()
const heading = useTemplateRef<HTMLHeadingElement>('post-title')

/** `missing`: 404 or 400, an id that is not an assessed post; `failed`: any other error. */
type LoadState = 'loading' | 'failed' | 'missing' | 'loaded'
/** The source JSON, fetched on the first opening of its section. */
type RawState = 'idle' | 'loading' | 'failed' | 'loaded'

const state = ref<LoadState>('loading')
const post = ref<PostItem | null>(null)
const rawState = ref<RawState>('idle')
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
  const type = label('itemType', item.itemType)
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

async function load(id: string): Promise<void> {
  const current = ++latestLoad
  latestRawLoad++
  state.value = 'loading'
  post.value = null
  rawState.value = 'idle'
  raw.value = ''
  try {
    const result = await fetchPost(id)
    if (current !== latestLoad) return
    post.value = result
    state.value = 'loaded'
    await nextTick()
    if (current === latestLoad) heading.value?.focus()
  } catch (error) {
    if (current !== latestLoad) return
    const missing = error instanceof ApiError && (error.status === 404 || error.status === 400)
    state.value = missing ? 'missing' : 'failed'
  }
}

async function loadRaw(): Promise<void> {
  const item = post.value
  if (!item) return
  const current = ++latestRawLoad
  rawState.value = 'loading'
  try {
    const text = await fetchPostRaw(item.id)
    if (current !== latestRawLoad) return
    raw.value = text
    rawState.value = 'loaded'
  } catch {
    if (current !== latestRawLoad) return
    rawState.value = 'failed'
  }
}

/** The first opening fetches the source JSON; a later one shows what is there (retry is a button). */
function onRawToggle(event: Event): void {
  if ((event.target as HTMLDetailsElement).open && rawState.value === 'idle') void loadRaw()
}

function retry(): void {
  if (postId.value !== null) void load(postId.value)
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
  <AppHeader />
  <main class="post-detail">
    <a class="back" :href="listHref" @click="toList">← Posty</a>
    <section aria-labelledby="post-title" :aria-busy="state === 'loading' ? 'true' : undefined">
      <h1 id="post-title" ref="post-title" tabindex="-1">{{ title }}</h1>
      <p v-if="state === 'loading'" class="loading">Wczytywanie posta…</p>
      <template v-else-if="state === 'failed'">
        <StatusMessage tone="error">Nie udało się wczytać posta.</StatusMessage>
        <AppButton @click="retry">Spróbuj ponownie</AppButton>
      </template>
      <p v-else-if="state === 'missing'" class="empty">
        Nie ma takiego posta albo nie został jeszcze oceniony.
        <RouterLink :to="{ name: 'home' }">Przejdź do listy postów</RouterLink>
      </p>
      <template v-else-if="post">
        <p class="meta">
          <time :datetime="post.publishedAt">{{ formatDateTime(post.publishedAt) }}</time>
          <span class="type">{{ label('itemType', post.itemType) }}</span>
          <span v-if="post.author" class="author">{{ post.author }}</span>
        </p>
        <p v-if="original">
          <a class="original" :href="original.href" target="_blank" rel="noopener noreferrer">Otwórz oryginał na {{ original.host }}</a>
        </p>
        <p v-if="post.body" class="body">{{ post.body }}</p>

        <section class="part" aria-labelledby="assessment-title">
          <h2 id="assessment-title">Ocena</h2>
          <PostAssessment v-if="post.assessment" :assessment="post.assessment" />
          <p v-else class="empty">Brak oceny.</p>
        </section>

        <details class="part raw" @toggle="onRawToggle">
          <summary>Dane dla nerdów: surowy JSON źródła</summary>
          <p v-if="rawState === 'loading'" class="loading">Wczytywanie danych…</p>
          <template v-else-if="rawState === 'failed'">
            <StatusMessage tone="error">Nie udało się wczytać danych źródła.</StatusMessage>
            <AppButton @click="loadRaw">Spróbuj ponownie</AppButton>
          </template>
          <pre v-else-if="rawState === 'loaded'">{{ raw }}</pre>
        </details>
      </template>
    </section>
  </main>
</template>

<style scoped>
.post-detail {
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

h1 {
  margin: var(--space-4) 0 var(--space-2);
  font-size: var(--font-size-lg);
  letter-spacing: normal;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

h2 {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-md);
  color: var(--text-h);
}

p {
  margin: 0 0 var(--space-3);
}

.loading,
.empty {
  margin: var(--space-4) 0;
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
  font-weight: 600;
  white-space: nowrap;
}

.author {
  overflow-wrap: anywhere;
}

/* The post's own line breaks; a long URL wraps instead of widening the page. */
.body {
  color: var(--text-h);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.part {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border);
}

summary {
  color: var(--accent);
  cursor: pointer;
}

/* Wraps at any character: no horizontal scroll of the page at 375 px. */
pre {
  margin: var(--space-3) 0 0;
  padding: var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  color: var(--text-h);
  font-family: var(--mono);
  font-size: var(--font-size-sm);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
