<script setup lang="ts">
// The shared button. Secondary is the look of the global `button` in style.css; primary is the
// filled main action. Never a submit button unless asked: inside a form it would send it.
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary'
    type?: 'button' | 'submit'
    disabled?: boolean
    /** Work started by the button is in progress: disabled and marked aria-busy. */
    busy?: boolean
    /** The text while busy; without it the button keeps its own. */
    busyLabel?: string
  }>(),
  { variant: 'secondary', type: 'button', busyLabel: undefined },
)
</script>

<template>
  <button
    :type="type"
    :class="variant"
    :disabled="disabled || busy"
    :aria-busy="busy ? 'true' : undefined"
  >
    <template v-if="busy && busyLabel">{{ busyLabel }}</template>
    <slot v-else />
  </button>
</template>

<style scoped>
.primary {
  background: var(--accent);
  color: var(--on-accent);
}

/* Darker in the light theme, lighter in the dark one: --text-h is the far end of each. */
.primary:hover:not(:disabled) {
  background: color-mix(in srgb, var(--accent) 85%, var(--text-h));
}
</style>
