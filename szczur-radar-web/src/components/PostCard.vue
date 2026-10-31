<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { PostItem } from '../api/posts'
import { formatDateTime, label, postOrigin } from '../labels'
import StanceText from './StanceText.vue'
import StatusBadge from './StatusBadge.vue'

const props = defineProps<{ post: PostItem }>()

/** The title, or the start of the text when there is none. */
const heading = computed(() => {
  const text = props.post.title ?? props.post.body ?? ''
  return text.length > 200 ? `${text.slice(0, 200)}…` : text
})

const output = computed(() => props.post.assessment?.output)
</script>

<template>
  <article class="post-card">
    <div class="meta">
      <time class="date" :datetime="post.publishedAt">{{ formatDateTime(post.publishedAt) }}</time>
      <!-- API and web deploy independently: an older API has no source or channel, so the origin is left out. -->
      <span v-if="post.source" class="origin">{{ postOrigin(post.source, post.channel) }}</span>
      <span class="type">{{ label('itemType', post.itemType) }}</span>
      <StatusBadge v-if="output" class="relevance" tone="neutral">{{ label('relevance', output.relevance) }}</StatusBadge>
      <span v-if="post.assessment?.resultSource === 'prefilter'" class="note">bez treści, bez oceny modelu</span>
    </div>
    <div class="content">
      <!-- The card's one link: the heading leads to the post's page; the card itself is not clickable. -->
      <h2 class="heading"><RouterLink :to="{ name: 'post', params: { id: post.id } }">{{ heading }}</RouterLink></h2>
      <template v-if="output">
        <dl v-if="output.targets.length" class="targets">
          <template v-for="target in output.targets" :key="target.id">
            <dt>{{ target.anchor.quote }}<span class="kind"> · {{ label('targetKind', target.kind) }}</span></dt>
            <StanceText tag="dd" :stance="target.stance" />
          </template>
        </dl>
        <p v-if="output.flags.length" class="flags">
          <StatusBadge v-for="flag in output.flags" :key="flag" tone="neutral">{{ label('flag', flag) }}</StatusBadge>
        </p>
      </template>
    </div>
  </article>
</template>

<style scoped>
/* Not `.card`: style.css has a global one (the centred login card) that a scoped style does not undo. */
.post-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-2) var(--space-5);
  margin-bottom: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

/* Narrow screens: the metadata in one wrapping line above the content. */
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-3);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--border);
  font-size: var(--font-size-sm);
}

/* Date and time stay on one line. */
.date {
  color: var(--text-h);
  font-weight: 600;
  white-space: nowrap;
}

/* A long channel name wraps instead of widening the metadata's column. */
.origin {
  overflow-wrap: anywhere;
}

.content {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
}

.heading {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: 600;
  line-height: 1.45;
  color: var(--text-h);
  overflow-wrap: anywhere;
}

/* The accent marks the link as a control; the focus ring is the global :focus-visible. */
.heading a {
  color: var(--accent);
  text-decoration: none;
}

.heading a:hover {
  text-decoration: underline;
}

/* A target (quote · kind) and its stance per row; a long quote wraps at half the width instead of overflowing. */
.targets {
  display: grid;
  grid-template-columns: fit-content(50%) minmax(0, 1fr);
  gap: var(--space-1) var(--space-4);
  margin: 0;
  font-size: var(--font-size-md);
}

.targets dt {
  font-weight: 600;
  color: var(--text-h);
  overflow-wrap: anywhere;
}

/* The target's kind: secondary to the quote it follows. */
.targets .kind {
  font-weight: 400;
  color: var(--text);
}

.targets dd {
  margin: 0;
}

.flags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: 0;
}

/* From 40rem: the metadata in their own column beside the content. */
@media (min-width: 40rem) {
  .post-card {
    grid-template-columns: 9rem minmax(0, 1fr);
  }

  .meta {
    flex-direction: column;
    flex-wrap: nowrap;
    align-items: flex-start;
    gap: var(--space-1);
    padding: 0 var(--space-4) 0 0;
    border-bottom: none;
    border-right: 1px solid var(--border);
  }
}
</style>
