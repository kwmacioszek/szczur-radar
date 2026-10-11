<script setup lang="ts">
// The outcome of an action: `ok` is announced politely (role status), `error` at once (role alert).
defineProps<{ tone: 'ok' | 'error' }>()
</script>

<template>
  <p class="status-message" :class="tone" :role="tone === 'ok' ? 'status' : 'alert'">
    <slot />
  </p>
</template>

<style scoped>
/* --text-h on the tinted background keeps the contrast of --text-h on --bg in both themes. */
.status-message {
  margin: var(--space-3) 0;
  padding: var(--space-2) var(--space-3);
  border-left: 4px solid var(--tone);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--tone) 12%, var(--bg));
  color: var(--text-h);
}

.ok {
  --tone: var(--success);
}

.error {
  --tone: var(--destructive);
}
</style>
