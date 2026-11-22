<script setup lang="ts">
// The page of a public screen (login, activation) after the design's `.auth-page` and `.auth`: a centred column with the
// brand, the card and the theme switch in the top right corner (outside <main>, as in the prototype). The card is one
// section named by its h1; what the view puts in the slot (a message, a paragraph, a form) is spaced by the card, and the
// submit button of a form directly in it takes the whole width, so a view repeats none of it.
import { useId } from 'vue'
import AppBrand from './AppBrand.vue'
import ThemeToggle from './ThemeToggle.vue'

defineProps<{
  title: string
  /** Something is loading: the card is marked aria-busy. */
  busy?: boolean
}>()

const titleId = useId()
</script>

<template>
  <div class="auth-page">
    <div class="tools"><ThemeToggle /></div>
    <main>
      <AppBrand />
      <section class="auth" :aria-labelledby="titleId" :aria-busy="busy ? 'true' : undefined">
        <h1 :id="titleId">{{ title }}</h1>
        <slot />
      </section>
    </main>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  grid-template-rows: auto 1fr;
  justify-items: center;
  min-height: 100svh;
  padding: var(--space-4) var(--space-3) var(--space-8);
}

.tools {
  justify-self: end;
}

main {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: var(--space-5);
  width: 100%;
}

.auth {
  display: grid;
  gap: var(--space-4);
  width: 100%;
  max-width: 26rem;
  box-sizing: border-box;
  padding: var(--space-6);
  border: 1px solid var(--line);
  border-radius: var(--radius-2xl);
  background: var(--surface);
}

@media (max-width: 899px) {
  .auth {
    padding: var(--space-5);
  }
}

.auth h1 {
  margin: 0;
  font-size: var(--font-size-5xl);
}

.auth :deep(form) {
  display: grid;
  gap: var(--space-4);
}

.auth :deep(form > button[type='submit']) {
  width: 100%;
}
</style>
