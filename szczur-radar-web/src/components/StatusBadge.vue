<script setup lang="ts">
// A state marker. The text always says the state; the tone's colour only reinforces it.
export type BadgeTone = 'success' | 'warning' | 'destructive' | 'neutral' | 'info'

defineProps<{ tone: BadgeTone }>()
defineSlots<{ default(): unknown }>()
</script>

<template>
  <span class="status-badge" :data-tone="tone"><slot /></span>
</template>

<style scoped>
.status-badge {
  display: inline-block;
  padding: 0 var(--space-2);
  border: 1px solid var(--tone);
  border-radius: var(--radius-sm);
  color: var(--tone);
  font-size: var(--font-size-sm);
}

[data-tone='success'] {
  --tone: var(--success);
}

[data-tone='warning'] {
  --tone: var(--warning);
}

[data-tone='destructive'] {
  --tone: var(--destructive);
}

[data-tone='neutral'] {
  --tone: var(--text);
}

[data-tone='info'] {
  --tone: var(--accent);
}
</style>
