<script setup lang="ts">
// What a post's panel shows under its bar (ui-design.md, "Panel boczny"; prototype post.html): the post to read — its
// title (or "{Typ} użytkownika {autor}"), its author, the link to the original and its text — then its assessment under
// "Ocena", and at the end the source's raw data, fetched only once "Surowe dane" first opens them. The panel's frame and
// bar are SidePanel's and PostPanelBar's. The title is the `headingId` heading, focusable by script: the panel's name
// and where its focus goes once the post is there.
import { computed, ref, useId, useTemplateRef } from 'vue'
import { type PostItem, fetchPostRaw } from '../api/posts'
import { formatDateTime, label, postTitle } from '../labels'
import AppButton from './AppButton.vue'
import AppIcon from './AppIcon.vue'
import ErrorState from './ErrorState.vue'
import LoadingState from './LoadingState.vue'
import PostAssessment from './PostAssessment.vue'

const props = withDefaults(
  defineProps<{
    post: PostItem
    /** The id of the post's title, the name of the panel's dialog. */
    headingId?: string
  }>(),
  { headingId: undefined },
)

const id = useId()
const titleId = computed(() => props.headingId ?? `${id}-title`)
const assessmentId = `${id}-assessment`
const rawId = `${id}-raw`
const rawButton = useTemplateRef<InstanceType<typeof AppButton>>('raw-button')

const title = computed(() => postTitle(props.post))

/** The original on the source's site: only an http(s) address becomes a link. */
const original = computed(() => {
  const address = props.post.webUrl
  if (!address) return null
  try {
    const url = new URL(address)
    return url.protocol === 'https:' || url.protocol === 'http:' ? { href: url.href, host: url.hostname } : null
  } catch {
    return null
  }
})

/** "Post inwestycyjny. Oceniono 20.10.2026, 09:44." */
const assessmentMeta = computed(() => {
  const assessment = props.post.assessment
  if (!assessment) return null
  return `Post ${label('relevance', assessment.output.relevance)}. Oceniono ${formatDateTime(assessment.assessedAt)}.`
})

/** The source JSON, fetched on the first opening of its section. */
type RawState = 'idle' | 'loading' | 'failed' | 'loaded'

const rawOpen = ref(false)
const rawState = ref<RawState>('idle')
/** A retry of the source data is in progress: its error stays, with the button busy, until the answer. */
const rawRetrying = ref(false)
const raw = ref('')
/** The number of the latest load of the raw data; the answer or error of any earlier one is dropped. */
let latestRawLoad = 0

/** `retry`: from the error's own button; the error stays, with the button busy, until the answer. */
async function loadRaw({ retry = false } = {}): Promise<void> {
  const current = ++latestRawLoad
  rawRetrying.value = retry
  if (!retry) rawState.value = 'loading'
  try {
    const text = await fetchPostRaw(props.post.id)
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

/** The first opening fetches the source JSON; a later one shows what is there (retry is a button). */
function toggleRaw(): void {
  rawOpen.value = !rawOpen.value
  if (rawOpen.value && rawState.value === 'idle') void loadRaw()
}

/** The busy retry button loses the focus, so it moves to "Surowe dane" first. */
function retryRaw(): void {
  ;(rawButton.value?.$el as HTMLElement | undefined)?.focus()
  void loadRaw({ retry: true })
}
</script>

<template>
  <div class="post-inspector">
    <article class="reading">
      <h2 :id="titleId" class="reading-title" tabindex="-1">{{ title }}</h2>
      <p v-if="post.author || original" class="byline">
        <span v-if="post.author" class="author">{{ post.author }}</span>
        <a
          v-if="original"
          class="original"
          :href="original.href"
          target="_blank"
          rel="noopener noreferrer"
          :aria-label="`Otwórz oryginał na ${original.host} (otwiera się w nowej karcie)`"
          >Otwórz oryginał<AppIcon name="i-external-link"
        /></a>
      </p>
      <p v-if="post.body" class="body">{{ post.body }}</p>
    </article>

    <section class="assessment" :aria-labelledby="assessmentId">
      <div class="assessment-head">
        <h3 :id="assessmentId" class="assessment-title">Ocena</h3>
        <p v-if="assessmentMeta" class="assessment-meta">{{ assessmentMeta }}</p>
      </div>
      <PostAssessment v-if="post.assessment" :assessment="post.assessment" />
      <p v-else class="none">Brak oceny.</p>

      <div class="assessment-foot">
        <span>Ocena opisuje stanowisko autora posta. Nie jest prognozą ani rekomendacją.</span>
        <AppButton
          ref="raw-button"
          icon="i-braces"
          :aria-expanded="rawOpen ? 'true' : 'false'"
          :aria-controls="rawId"
          @click="toggleRaw"
        >
          Surowe dane
        </AppButton>
      </div>
      <div v-show="rawOpen" :id="rawId" class="raw-area">
        <LoadingState v-if="rawState === 'loading'" shape="lines" />
        <ErrorState
          v-else-if="rawState === 'failed'"
          title="Nie udało się wczytać danych źródła"
          :retrying="rawRetrying"
          @retry="retryRaw"
        >
          Ocena posta zostaje bez zmian.
        </ErrorState>
        <pre v-else-if="rawState === 'loaded'" class="raw" role="region" aria-label="Surowe dane posta" tabindex="0">{{ raw }}</pre>
      </div>
    </section>
  </div>
</template>

<style scoped>
p {
  margin: 0;
}

/* The post's own title, set as the design's reading title. A long word (a link) wraps instead of widening the panel. */
.reading-title {
  margin: 0;
  color: var(--text-h);
  font-family: var(--font-display);
  font-size: var(--font-size-6xl);
  font-weight: var(--weight-medium);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  overflow-wrap: anywhere;
}

/* The title takes the focus by script only (it is not a control, Tab never reaches it), for a screen reader to start
   reading there: no ring, as the design's panel. */
.reading-title:focus {
  outline: none;
}

.byline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-4);
  margin-top: var(--space-3);
  font-size: var(--font-size-sm);
}

.author {
  color: var(--text-h);
  font-weight: var(--weight-semibold);
  overflow-wrap: anywhere;
}

.original {
  --icon-size: 0.9375rem;

  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

/* What is read is set in the serif. The post's own line breaks; a long URL wraps instead of widening the panel. */
.body {
  max-width: var(--width-reading);
  margin-top: var(--space-5);
  color: var(--text-h);
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  line-height: var(--leading-relaxed);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.assessment {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-7);
}

.assessment-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-1) var(--space-4);
}

.assessment-title {
  margin: 0;
  font-size: var(--font-size-4xl);
}

.assessment-meta,
.none {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.assessment-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3) var(--space-5);
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

/* Wraps at any character: no horizontal scroll of the panel at 375 px. */
.raw {
  margin: 0;
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--surface-2);
  color: var(--text-h);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  line-height: var(--leading-relaxed);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 899px) {
  .reading-title {
    font-size: var(--font-size-5xl);
  }
}
</style>
