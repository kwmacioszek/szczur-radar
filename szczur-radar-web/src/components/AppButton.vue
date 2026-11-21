<script setup lang="ts">
// The shared button. Secondary is the look of the global `button` in style.css; primary is the filled main action
// (one per view). It can carry an icon before its text, be large, or be icon-only (a name for assistive technology
// is then required: `aria-label`). Never a submit button unless asked: inside a form it would send it.
import { useAttrs } from 'vue'
import AppIcon from './AppIcon.vue'
import type { IconName } from './iconNames'

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary'
    type?: 'button' | 'submit'
    size?: 'md' | 'lg'
    /** An icon before the text. */
    icon?: IconName
    /** Only the icon (needs `icon` and an `aria-label`). A pressed or expanded one is filled with --selected. */
    iconOnly?: boolean
    disabled?: boolean
    /** Work started by the button is in progress: disabled, marked aria-busy and led by the work mark. */
    busy?: boolean
    /** The text while busy; without it the button keeps its own. */
    busyLabel?: string
  }>(),
  { variant: 'secondary', type: 'button', size: 'md', icon: undefined, busyLabel: undefined },
)

const attrs = useAttrs()
// A name of white space only is no name.
const named = (value: unknown) => typeof value === 'string' && value.trim() !== ''
if (import.meta.env.DEV && props.iconOnly && !named(attrs['aria-label']) && !named(attrs['aria-labelledby'])) {
  console.warn('AppButton: an icon-only button needs an aria-label')
}
</script>

<template>
  <button
    :type="type"
    :class="[variant, { 'icon-only': iconOnly, large: size === 'lg' }]"
    :disabled="disabled || busy"
    :aria-busy="busy ? 'true' : undefined"
  >
    <span v-if="busy" class="spinner" aria-hidden="true"></span>
    <AppIcon v-else-if="icon" :name="icon" />
    <template v-if="!iconOnly">
      <template v-if="busy && busyLabel">{{ busyLabel }}</template>
      <slot v-else />
    </template>
  </button>
</template>

<style scoped>
.primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--on-accent);
  font-weight: var(--weight-semibold);
}

/* Darker in the light theme, lighter in the dark one: --text-h is the far end of each. */
.primary:hover:not(:disabled, .icon-only) {
  background: color-mix(in srgb, var(--accent) 86%, var(--text-h));
}

/* Next to a large search field. */
.large {
  --icon-size: 1.25rem;

  min-height: var(--control-height-lg);
  padding-inline: var(--space-5);
  border-radius: var(--radius-lg);
}

.secondary.large {
  background: var(--surface);
}

.secondary.large:hover:not(:disabled, .icon-only) {
  background: var(--surface-2);
}

/* Inside a normal button an icon is a little smaller than the page's. */
button:not(.large, .icon-only) {
  --icon-size: 1.125rem;
}

.icon-only {
  display: inline-grid;
  place-items: center;
  width: var(--control-height);
  height: var(--control-height);
  padding: 0;
  border: 0;
  background: none;
  color: var(--text);
}

/* Keep the hover less specific than the pressed and expanded states below. */
.icon-only:where(:hover:not(:disabled)) {
  background: var(--surface-2);
  color: var(--text-h);
}

.icon-only[aria-pressed='true'],
.icon-only[aria-expanded='true'] {
  background: var(--selected);
  color: var(--accent);
}

/* The mark of work in progress: a ring with a gap; it turns only where motion is welcome, else it stays still. */
.spinner {
  flex: none;
  width: 1rem;
  height: 1rem;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: var(--radius-full);
}

@media (prefers-reduced-motion: no-preference) {
  .spinner {
    animation: spin 0.9s linear infinite;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
