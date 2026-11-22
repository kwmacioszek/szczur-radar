<script setup lang="ts">
// The brand mark of the design (ui-design.md, "Marka"): the radar dial in a round conic glow and the name in the
// display face. One component for the public screens (AuthLayout) and the app bar. It is not a link: a place that
// wants one wraps it. The name is the visible text; the icon is hidden (AppIcon).
import AppIcon from './AppIcon.vue'
</script>

<template>
  <span class="brand">
    <span class="mark"><AppIcon name="i-brand" /></span>
    Szczur Radar
  </span>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-h);
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--weight-medium);
  line-height: 1;
  letter-spacing: var(--tracking-snug);
  white-space: nowrap;
}

@media (max-width: 899px) {
  .brand {
    font-size: var(--font-size-2xl);
  }
}

/* The dial fills the padded square, which is the glow's disc. */
.mark {
  display: inline-flex;
  box-sizing: border-box;
  width: 2.125rem;
  height: 2.125rem;
  padding: var(--space-1);
  border-radius: var(--radius-full);
  background: conic-gradient(from 20deg, color-mix(in srgb, var(--accent) 40%, transparent), transparent 32%);
  color: var(--accent);
  --icon-size: 100%;
}
</style>
