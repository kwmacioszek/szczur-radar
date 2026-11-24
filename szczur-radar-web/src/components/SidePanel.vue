<script setup lang="ts">
// The panel that slides in from the right over the page (ui-design.md, "Panel boczny"): a post's assessment now, a
// target's card later (S-58). It holds only the dialog's mechanics and its frame, never what it shows: the bar (`bar`:
// the metadata and the buttons, the close button among them) and the content below it, which scrolls on its own.
// The WAI-ARIA dialog pattern: `role="dialog"` with `aria-modal="true"`, named by the content's heading
// (`labelledby`). While it is mounted the rest of the page (`#app`) is inert under a scrim and does not scroll; both
// come back when it goes. It is moved to `body`, out of `#app`, so it is not inert itself, and it is no part of
// `main`. Tab and Shift+Tab go round inside it; Escape and a click on the scrim ask to close it (`close`): the caller
// closes it, so a panel with its own address goes back in history. On mount the focus moves into it (the dialog
// itself); `focus()` moves it to the heading once the content has one.
// `inline`: the same frame in its place in the page, without the dialog, the layers or the focus rules: the catalogue
// (/kitchen-sink) shows a panel's content in it.
// From 900 px the panel is --width-panel wide at the right edge; below it fills the screen. It slides in only where
// motion is welcome.
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'

const props = withDefaults(
  defineProps<{
    /** The id of the content's heading: the dialog's name, and where `focus()` puts the focus. */
    labelledby: string
    /** The frame in place, without the dialog (the catalogue). */
    inline?: boolean
  }>(),
  { inline: false },
)
const emit = defineEmits<{ close: [] }>()
defineSlots<{ bar?(): unknown; default?(): unknown }>()

const panel = useTemplateRef<HTMLElement>('panel')

/** What Tab can reach inside the panel, in the order of the document. */
const TABBABLE =
  'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'

function tabbables(): HTMLElement[] {
  return [...(panel.value?.querySelectorAll<HTMLElement>(TABBABLE) ?? [])]
}

/** Tab past the last control goes to the first, Shift+Tab before the first (or from the dialog itself) to the last. */
function onKeydown(event: KeyboardEvent): void {
  if (props.inline || event.key !== 'Tab') return
  const items = tabbables()
  const first = items[0]
  const last = items.at(-1)
  if (!first || !last) {
    event.preventDefault()
    return
  }
  const active = document.activeElement
  if (event.shiftKey && (active === first || active === panel.value)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

/** Escape anywhere on the page: the rest of the page is inert, so the focus is in the panel or nowhere. */
function onDocumentKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

/** The heading of the content when it is there and takes the focus, else the dialog itself. */
function focus(): void {
  const heading = document.getElementById(props.labelledby)
  if (heading && panel.value?.contains(heading) && heading.hasAttribute('tabindex')) heading.focus()
  else panel.value?.focus()
}

let app: HTMLElement | null = null
let bodyOverflow = ''

onMounted(() => {
  if (props.inline) return
  app = document.getElementById('app')
  app?.setAttribute('inert', '')
  bodyOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  document.addEventListener('keydown', onDocumentKeydown)
  panel.value?.focus()
})

// Before the panel leaves the page, so whatever gets the focus next (the list's row) is no longer inert.
onBeforeUnmount(() => {
  if (props.inline) return
  app?.removeAttribute('inert')
  document.body.style.overflow = bodyOverflow
  document.removeEventListener('keydown', onDocumentKeydown)
})

defineExpose({ focus })
</script>

<template>
  <Teleport to="body" :disabled="inline">
    <div v-if="!inline" class="scrim" @click="emit('close')"></div>
    <section
      v-if="!inline"
      ref="panel"
      class="side-panel modal"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="labelledby"
      tabindex="-1"
      @keydown="onKeydown"
    >
      <div class="bar"><slot name="bar" /></div>
      <div class="body"><slot /></div>
    </section>
    <div v-else class="side-panel">
      <div class="bar"><slot name="bar" /></div>
      <div class="body"><slot /></div>
    </div>
  </Teleport>
</template>

<style scoped>
/* The page under the panel stays visible, darkened. */
.scrim {
  position: fixed;
  inset: 0;
  z-index: var(--z-scrim);
  background: color-mix(in srgb, var(--scrim) 36%, transparent);
}

/* The bar on top, the content under it scrolls on its own. */
.side-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  grid-template-columns: minmax(0, 1fr);
  box-sizing: border-box;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius-2xl);
  background: var(--surface);
}

.modal {
  position: fixed;
  top: var(--space-3);
  right: var(--space-3);
  bottom: var(--space-3);
  z-index: var(--z-overlay);
  width: min(var(--width-panel), calc(100vw - 1.5rem));
  box-shadow: var(--shadow-overlay);
}

/* The dialog takes the focus by script only, for a screen reader to start in it: no ring around the whole panel. */
.modal:focus {
  outline: none;
}

.bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4) var(--space-3) var(--space-7);
  border-bottom: 1px solid var(--line);
}

.body {
  overflow-y: auto;
  padding: var(--space-6) var(--space-7) var(--space-7);
}

/* It slides in from the right, as an answer to the click that opened it. */
@media (prefers-reduced-motion: no-preference) {
  .modal {
    animation: slide-in var(--duration) var(--ease-out);
  }
}

@keyframes slide-in {
  from {
    transform: translateX(calc(100% + var(--space-3)));
  }
}

/* Below 900 px the panel is the whole screen; "wstecz" or its close button leaves it. */
@media (max-width: 899px) {
  .modal {
    inset: 0;
    width: auto;
    border: 0;
    border-radius: 0;
  }

  .bar {
    padding-left: var(--space-4);
  }

  .body {
    padding: var(--space-5) var(--space-4) var(--space-6);
  }
}
</style>
