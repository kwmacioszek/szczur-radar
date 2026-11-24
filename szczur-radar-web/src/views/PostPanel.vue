<script setup lang="ts">
// The route of a post (`/posts/:id`), the list's child: the post in a SidePanel over the list, which stays mounted
// under it (FeedView renders this with the rows of its page). Where its post comes from is decided once, when it opens
// and when the id changes: a row of the page on screen (`items`) needs no request; any other post (a link to one off
// this page, a refresh while the list still loads) is read from the API, with its loading, error and "no such post"
// states in the panel, and a list that arrives later is ignored. The answer of an earlier request is dropped.
// Closing (its button, Escape, the scrim) goes back in history when the entry before is the same list, so the list
// stays where it was, and otherwise replaces the address with the list's; the browser's "back" closes it as well. As it
// goes it tells FeedView which post it showed (`closed`), and FeedView puts the focus back on that post's row.
import { computed, nextTick, onBeforeUnmount, ref, useId, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { type PostItem, fetchPost } from '../api/posts'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'
import PostInspector from '../components/PostInspector.vue'
import PostPanelBar from '../components/PostPanelBar.vue'
import SidePanel from '../components/SidePanel.vue'

const props = withDefaults(
  defineProps<{
    /** The rows of the list's page on screen; empty while the list loads or after its error. */
    items?: PostItem[]
  }>(),
  { items: () => [] },
)
const emit = defineEmits<{
  /** The panel of this post is gone (closed, or the address left it): the list takes the focus back. */
  closed: [id: string]
}>()

const route = useRoute()
const router = useRouter()
const panel = useTemplateRef<InstanceType<typeof SidePanel>>('panel')
const errorState = useTemplateRef<InstanceType<typeof ErrorState>>('error-state')

const id = useId()
const headingIds = {
  loaded: `${id}-title`,
  loading: `${id}-loading`,
  failed: `${id}-failed`,
  missing: `${id}-missing`,
} as const

/** `missing`: 404 or 400, an id that is not an assessed post; `failed`: any other error. */
type LoadState = keyof typeof headingIds

const state = ref<LoadState>('loading')
/** A retry of the post is in progress: its error stays, with the button busy, until the answer. */
const retrying = ref(false)
const post = ref<PostItem | null>(null)
/** The number of the latest opening; the answer or error of any earlier request is dropped. */
let latestLoad = 0
/** The id of the post this panel opened last: the address has already left it when the panel goes. */
let openedId: string | null = null

/** The id in the address; null while the router leaves this route. */
const postId = computed(() => (route.name === 'post' && typeof route.params.id === 'string' ? route.params.id : null))

/** The dialog is named by the post's title, or by the heading of the state shown instead. */
const labelledby = computed(() => headingIds[state.value])

/** A row of the page on screen at once; any other post from the API. */
function open(postToOpen: string): void {
  openedId = postToOpen
  const row = props.items.find((item) => item.id === postToOpen)
  if (!row) {
    void load(postToOpen)
    return
  }
  const current = ++latestLoad
  post.value = row
  state.value = 'loaded'
  retrying.value = false
  void nextTick(() => {
    if (current === latestLoad) panel.value?.focus()
  })
}

/**
 * `retry`: from the error's own button, whose error stays until the answer. The busy button loses the focus, so after
 * the answer it goes to the post's title, back to "Spróbuj ponownie" after another failure, or to the dialog.
 */
async function load(postToLoad: string, { retry = false } = {}): Promise<void> {
  const current = ++latestLoad
  retrying.value = retry
  if (!retry) state.value = 'loading'
  post.value = null
  try {
    const result = await fetchPost(postToLoad)
    if (current !== latestLoad) return
    post.value = result
    state.value = 'loaded'
    retrying.value = false
    await nextTick()
    if (current === latestLoad) panel.value?.focus()
  } catch (error) {
    if (current !== latestLoad) return
    const missing = error instanceof ApiError && (error.status === 404 || error.status === 400)
    state.value = missing ? 'missing' : 'failed'
    retrying.value = false
    if (!retry) return
    await nextTick()
    if (current !== latestLoad) return
    if (missing) panel.value?.focus()
    else errorState.value?.focus()
  }
}

function retry(): void {
  if (openedId !== null) void load(openedId, { retry: true })
}

/** Back to the list the post was opened from, at the same place; to the list with the same query otherwise. */
function close(): void {
  const list = router.resolve({ name: 'posts', query: route.query })
  const back = router.options.history.state.back
  if (typeof back === 'string') {
    const previous = router.resolve(back)
    if (previous.name === 'posts' && previous.fullPath === list.fullPath) {
      router.back()
      return
    }
  }
  void router.replace(list)
}

watch(
  postId,
  (next) => {
    if (next !== null && next !== openedId) open(next)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (openedId !== null) emit('closed', openedId)
})
</script>

<template>
  <SidePanel ref="panel" :labelledby="labelledby" @close="close">
    <template #bar>
      <PostPanelBar :post="state === 'loaded' ? post : null" @close="close" />
    </template>
    <div class="post-panel" :aria-busy="state === 'loading' || retrying ? 'true' : undefined">
      <template v-if="state === 'loading'">
        <h2 :id="headingIds.loading" class="visually-hidden" tabindex="-1">Wczytywanie posta</h2>
        <LoadingState shape="lines" :count="4" />
      </template>
      <ErrorState
        v-else-if="state === 'failed'"
        ref="error-state"
        title-tag="h2"
        :title-id="headingIds.failed"
        title="Nie udało się wczytać posta"
        :retrying="retrying"
        @retry="retry"
      >
        Serwer nie odpowiedział.
      </ErrorState>
      <EmptyState
        v-else-if="state === 'missing'"
        title-tag="h2"
        :title-id="headingIds.missing"
        icon="i-inbox"
        title="Nie ma takiego posta"
      >
        Post nie istnieje albo nie został jeszcze oceniony.
      </EmptyState>
      <PostInspector v-else-if="post" :key="post.id" :post="post" :heading-id="headingIds.loaded" />
    </div>
  </SidePanel>
</template>
