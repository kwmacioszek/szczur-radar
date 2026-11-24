<script setup lang="ts">
// The bar of a post's panel (SidePanel's `bar` slot; prototype post.html): where the post comes from, its type in its
// source's words and its full date, then the button that closes the panel. Without a post (while it loads, after an
// error, for an unknown id) the bar has only the button.
import type { PostItem } from '../api/posts'
import { formatDateTime, postOrigin, postType } from '../labels'
import AppButton from './AppButton.vue'

defineProps<{ post: PostItem | null }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <p v-if="post" class="meta">
    <!-- API and web deploy independently: an older API has no source or channel, so the origin is left out. -->
    <span v-if="post.source" class="origin">{{ postOrigin(post.source, post.channel) }}</span>
    <span class="type">{{ postType(post.source, post.itemType) }}</span>
    <time :datetime="post.publishedAt">{{ formatDateTime(post.publishedAt) }}</time>
  </p>
  <AppButton class="close" icon="i-x" icon-only aria-label="Zamknij post" @click="emit('close')" />
</template>

<style scoped>
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0 var(--space-2);
  min-width: 0;
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.origin {
  color: var(--text-h);
  font-weight: var(--weight-semibold);
  overflow-wrap: anywhere;
}

.meta time {
  margin-left: var(--space-2);
  white-space: nowrap;
}

/* Alone in the bar (no post yet), the button still stands at its right end. */
.close {
  margin-left: auto;
}
</style>
