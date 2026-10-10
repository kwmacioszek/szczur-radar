<script setup lang="ts">
import { computed } from 'vue'
import type { PostItem } from '../api/posts'
import { formatDateTime, label } from '../labels'

const props = defineProps<{ post: PostItem }>()

/** The title, or the start of the text when there is none. */
const heading = computed(() => {
  const text = props.post.title ?? props.post.body ?? ''
  return text.length > 200 ? `${text.slice(0, 200)}…` : text
})

const output = computed(() => props.post.assessment?.output)
</script>

<template>
  <article class="post">
    <p class="meta">
      <time :datetime="post.publishedAt">{{ formatDateTime(post.publishedAt) }}</time>
      <span class="type">{{ label('itemType', post.itemType) }}</span>
    </p>
    <h2 class="heading">{{ heading }}</h2>
    <template v-if="output">
      <p class="relevance">
        Trafność: <strong>{{ label('relevance', output.relevance) }}</strong>
        <span v-if="post.assessment?.resultSource === 'prefilter'" class="note">(bez treści, bez oceny modelu)</span>
      </p>
      <ul v-if="output.targets.length" class="targets">
        <li v-for="target in output.targets" :key="target.id">
          <span class="target">{{ target.anchor.quote }}</span>
          <span class="stance" :class="target.stance.direction">
            {{ label('direction', target.stance.direction) }}<template v-if="target.stance.assertiveness === 'hedged'"> (ostrożnie)</template>
          </span>
        </li>
      </ul>
      <p v-if="output.flags.length" class="flags">
        <span v-for="flag in output.flags" :key="flag" class="flag">{{ label('flag', flag) }}</span>
      </p>
    </template>
  </article>
</template>

<style scoped>
.post {
  padding: 16px 0;
  border-bottom: 1px solid color-mix(in srgb, var(--text) 20%, transparent);
}

.meta {
  display: flex;
  gap: 12px;
  margin: 0 0 4px;
  font-size: 14px;
}

.type {
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--accent);
}

.heading {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 500;
  color: var(--text-h);
  overflow-wrap: anywhere;
}

.relevance,
.flags {
  margin: 4px 0;
  font-size: 15px;
}

.note {
  margin-left: 6px;
  font-size: 13px;
}

.targets {
  margin: 4px 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 15px;
}

.target {
  color: var(--text-h);
  margin-right: 4px;
}

.stance.bullish {
  color: var(--success);
}

.stance.bearish {
  color: var(--destructive);
}

.flag {
  display: inline-block;
  margin-right: 6px;
  padding: 0 6px;
  border: 1px solid var(--text);
  border-radius: 6px;
  font-size: 13px;
}
</style>
