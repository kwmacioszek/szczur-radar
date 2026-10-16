<script setup lang="ts">
// The bell and its list of notifications, in every state, from props: NotificationBell feeds it from the API, the
// kitchen sink from fixed data. A disclosure (a button that shows a panel after it), not an ARIA menu: Tab goes from
// the button through the panel's links and buttons. Escape and a click outside close it; Escape, and a click that
// would leave the focus nowhere, put the focus back on the button.
// The panel is placed below the nearest positioned ancestor and aligned to its right edge (AppHeader's bar), so it
// fits at 375 px wherever the bell lands in the wrapped header.
import { onBeforeUnmount, useId, useTemplateRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { AdminNotification } from '../api/notifications'
import { formatRelative, label, plural } from '../labels'
import { runStatusLabel, runTone } from '../runs'
import AppButton from './AppButton.vue'
import StatusBadge from './StatusBadge.vue'
import StatusMessage from './StatusMessage.vue'

const props = withDefaults(
  defineProps<{
    /** `loading` until the first list (and on a retry after an error). */
    state: 'loading' | 'error' | 'loaded'
    /** Unread notifications of the admin, also beyond `items`; null until known. */
    unread: number | null
    /** The newest notifications, newest first. */
    items: AdminNotification[]
    /** The time the relative times count from (ms since the epoch). */
    now?: number
    /** "Oznacz wszystkie jako przeczytane" is under way. */
    markingAll?: boolean
    /** The last "Oznacz wszystkie jako przeczytane" failed. */
    markAllFailed?: boolean
  }>(),
  { now: () => Date.now(), markingAll: false, markAllFailed: false },
)

const emit = defineEmits<{
  /** A notification was chosen (plain click or Enter on its link); the caller opens its run. */
  select: [notification: AdminNotification]
  retry: []
  markAll: []
}>()

const open = defineModel<boolean>('open', { default: false })

const router = useRouter()
const root = useTemplateRef<HTMLElement>('root')
const toggle = useTemplateRef<InstanceType<typeof AppButton>>('toggle')
const id = useId()
const panelId = `${id}-panel`
const titleId = `${id}-title`

function unreadText(count: number): string {
  return `${count} ${plural(count, 'nieprzeczytane', 'nieprzeczytane', 'nieprzeczytanych')}`
}

function focusToggle(): void {
  ;(toggle.value?.$el as HTMLElement | undefined)?.focus()
}

function runHref(runId: string): string {
  return router.resolve({ name: 'admin-run', params: { id: runId } }).href
}

/** A new tab or window (modifier keys, middle button) follows the link without marking the notification. */
function choose(event: MouseEvent, notification: AdminNotification): void {
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) return
  event.preventDefault()
  emit('select', notification)
}

/** The retry button disappears while loading, so the focus goes to the bell first. */
function retry(): void {
  focusToggle()
  emit('retry')
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return
  open.value = false
  focusToggle()
}

function onDocumentClick(event: MouseEvent): void {
  if (root.value?.contains(event.target as Node)) return
  open.value = false
  // A click on another control keeps the focus there; one on the page itself would leave it nowhere.
  const active = document.activeElement
  if (active === null || active === document.body || root.value?.contains(active)) focusToggle()
}

/** Escape and a click outside are heard only while the panel is open. */
function listen(on: boolean): void {
  if (on) {
    document.addEventListener('keydown', onKeydown)
    document.addEventListener('click', onDocumentClick)
  } else {
    document.removeEventListener('keydown', onKeydown)
    document.removeEventListener('click', onDocumentClick)
  }
}

watch(open, listen, { immediate: true })
onBeforeUnmount(() => listen(false))

defineExpose({ focusToggle })
</script>

<template>
  <div ref="root" class="notification-menu">
    <AppButton
      ref="toggle"
      class="toggle"
      :aria-label="props.unread === null ? 'Powiadomienia' : `Powiadomienia, ${unreadText(props.unread)}`"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="panelId"
      @click="open = !open"
    >
      <svg
        class="icon"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
        focusable="false"
      >
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.7 21a2 2 0 0 1-3.4 0" />
      </svg>
      <span v-if="props.unread" class="count" aria-hidden="true">{{ props.unread }}</span>
    </AppButton>
    <div
      v-show="open"
      :id="panelId"
      class="panel"
      role="region"
      :aria-labelledby="titleId"
      :aria-busy="props.state === 'loading' ? 'true' : undefined"
    >
      <div class="panel-head">
        <p :id="titleId" class="panel-title">Powiadomienia</p>
        <AppButton
          v-if="props.state === 'loaded' && (props.unread ?? 0) > 0"
          class="mark-all"
          :busy="props.markingAll"
          busy-label="Oznaczanie…"
          @click="emit('markAll')"
        >
          Oznacz wszystkie jako przeczytane
        </AppButton>
      </div>
      <StatusMessage v-if="props.markAllFailed" tone="error">
        Nie udało się oznaczyć powiadomień jako przeczytanych.
      </StatusMessage>
      <p v-if="props.state === 'loading'" class="loading">Wczytywanie…</p>
      <template v-else-if="props.state === 'error'">
        <StatusMessage tone="error">Nie udało się wczytać powiadomień.</StatusMessage>
        <AppButton @click="retry">Spróbuj ponownie</AppButton>
      </template>
      <p v-else-if="props.items.length === 0" class="empty">Brak powiadomień.</p>
      <ul v-else class="items">
        <li v-for="item in props.items" :key="item.id" :class="{ unread: !item.readAt }">
          <a v-if="item.run" class="item" :href="runHref(item.run.id)" @click="choose($event, item)">
            <span class="item-head">
              <strong v-if="!item.readAt" class="new">nowe</strong>
              <StatusBadge :tone="runTone(item.run)">{{ runStatusLabel(item.run) }}</StatusBadge>
            </span>
            <span class="item-meta">
              {{ label('runKind', item.run.kind) }} ·
              <time :datetime="item.createdAt">{{ formatRelative(item.createdAt, props.now) }}</time>
            </span>
          </a>
          <span v-else class="item">
            <span class="item-head">Przebieg niedostępny</span>
            <span class="item-meta">
              <time :datetime="item.createdAt">{{ formatRelative(item.createdAt, props.now) }}</time>
            </span>
          </span>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.icon {
  width: 1.25em;
  height: 1.25em;
  flex: none;
}

/* Text on --accent is always --on-accent. */
.count {
  min-width: 1.25em;
  padding: 0 var(--space-1);
  border-radius: var(--radius-sm);
  background: var(--accent);
  color: var(--on-accent);
  font-size: var(--font-size-sm);
  font-weight: 600;
  line-height: 1.45;
  text-align: center;
}

.panel {
  position: absolute;
  top: 100%;
  right: var(--space-4);
  z-index: 1;
  box-sizing: border-box;
  width: min(24rem, calc(100% - 2 * var(--space-4)));
  max-height: 70vh;
  overflow-y: auto;
  padding: var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--bg);
  box-shadow: 0 var(--space-1) var(--space-3) color-mix(in srgb, var(--text-h) 15%, transparent);
}

.panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.panel-title {
  margin: 0;
  color: var(--text-h);
  font-weight: 600;
}

.loading,
.empty {
  margin: var(--space-2) 0;
}

.items {
  margin: 0;
  padding: 0;
  list-style: none;
}

.items li + li {
  border-top: 1px solid var(--border);
}

.item {
  display: flex;
  flex-direction: column;
  gap: var(--space-0);
  padding: var(--space-2) var(--space-1);
  border-radius: var(--radius-sm);
  color: var(--text-h);
  text-decoration: none;
  overflow-wrap: anywhere;
}

a.item:hover {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-2);
}

.new {
  color: var(--accent);
  font-size: var(--font-size-sm);
}

.item-meta {
  color: var(--text);
  font-size: var(--font-size-sm);
}
</style>
