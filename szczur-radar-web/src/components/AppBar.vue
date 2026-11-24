<script setup lang="ts">
// The bar of every logged-in page, from props: AppShell feeds it from the session, the router and /api/freshness, the
// kitchen sink from fixed data. The brand (AppBrand, the same as on the public pages) leads to the posts, the main
// navigation marks the page's section with aria-current (the caller decides which: a run's page is still "Panel admina"), the date
// of the data says per source when it was last updated, and the session's controls come last. The bell is the caller's, in the `notifications`
// slot: its panel opens below the nearest positioned ancestor, which is this header, aligned to its right edge, so
// nothing between the header and the bell is positioned.
// Below 900 px the controls are the first row, the navigation the second and the date of the data the third, the
// e-mail is left out (ui-design.md, "Powłoka i nawigacja").
import type { RouteLocationRaw } from 'vue-router'
import { RouterLink } from 'vue-router'
import type { Freshness } from '../api/freshness'
import { formatDateTime, label } from '../labels'
import AppButton from './AppButton.vue'
import AppBrand from './AppBrand.vue'
import ThemeToggle from './ThemeToggle.vue'

export interface NavItem {
  label: string
  to: RouteLocationRaw
  /** The page belongs to this item's section. */
  current: boolean
}

defineProps<{
  items: NavItem[]
  /** `null`: no label (the date could not be read); no `sources`: no update yet. */
  freshness: Freshness | null
  email: string | null
  /** Signing out is under way. */
  loggingOut: boolean
}>()

const emit = defineEmits<{ logout: [] }>()
defineSlots<{ notifications?(): unknown }>()
</script>

<template>
  <header class="app-bar">
    <RouterLink v-slot="{ href, navigate }" :to="{ name: 'posts' }" custom>
      <a class="brand" :href="href" @click="navigate">
        <AppBrand />
      </a>
    </RouterLink>
    <nav class="nav" aria-label="Główna">
      <!-- Not RouterLink's own link: it marks only the exact address, a section needs its own aria-current. -->
      <RouterLink v-for="item in items" :key="item.label" v-slot="{ href, navigate }" :to="item.to" custom>
        <a :href="href" :aria-current="item.current ? 'page' : undefined" @click="navigate">{{ item.label }}</a>
      </RouterLink>
    </nav>
    <div class="session">
      <p v-if="freshness" class="freshness">
        <template v-if="freshness.sources.length === 0">Dane nie były jeszcze aktualizowane.</template>
        <template v-else>
          <span>Dane:</span>
          <span class="freshness-list">
            <span v-for="source in freshness.sources" :key="source.source">
              {{ label('source', source.source) }}
              <time :datetime="source.lastUpdatedAt">{{ formatDateTime(source.lastUpdatedAt) }}</time>
            </span>
          </span>
        </template>
      </p>
      <div class="actions">
        <slot name="notifications" />
        <ThemeToggle />
        <span v-if="email" class="who">{{ email }}</span>
        <AppButton :busy="loggingOut" @click="emit('logout')">Wyloguj</AppButton>
      </div>
    </div>
  </header>
</template>

<style scoped>
/* Positioned: the bell's panel opens below the bar, and the radar rings behind the mark sit in its stacking context. */
.app-bar {
  position: relative;
  z-index: var(--z-bar);
  display: flex;
  align-items: center;
  gap: var(--space-6);
  min-height: 4.5rem;
  padding: 0 var(--space-6);
}

/* The rings behind the mark: a decoration without content, under everything the bar holds. */
.app-bar::before {
  content: '';
  position: absolute;
  z-index: -1;
  inset: 0 auto 0 0;
  width: min(24rem, 100%);
  background: repeating-radial-gradient(
    circle at 3.0625rem 2.25rem,
    transparent 0 2.5rem,
    color-mix(in srgb, var(--accent) 22%, transparent) 2.5rem calc(2.5rem + 1px)
  );
  -webkit-mask-image: linear-gradient(100deg, black 0%, transparent 62%);
  mask-image: linear-gradient(100deg, black 0%, transparent 62%);
  pointer-events: none;
}

.brand {
  display: flex;
  text-decoration: none;
}

.nav {
  display: flex;
  gap: var(--space-1);
}

.nav a {
  display: flex;
  align-items: center;
  height: var(--control-height);
  padding: 0 var(--space-4);
  border-radius: var(--radius-full);
  color: var(--text);
  text-decoration: none;
  white-space: nowrap;
}

.nav a:hover {
  background: var(--surface-2);
  color: var(--text-h);
}

.nav a[aria-current='page'] {
  background: var(--surface);
  box-shadow: inset 0 0 0 1px var(--line);
  color: var(--text-h);
  font-weight: var(--weight-semibold);
}

/* Not positioned: it must not take the header's place as the bell panel's anchor. */
.session {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin-left: auto;
}

.freshness,
.who {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

/* One source in one line, so the bar fits at 1280 px. */
.freshness {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0 var(--space-3) 0 0;
  line-height: var(--leading-snug);
}

.freshness-list {
  display: grid;
  white-space: nowrap;
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.who {
  margin: 0 var(--space-2);
  white-space: nowrap;
}

@media (max-width: 899px) {
  .app-bar {
    flex-wrap: wrap;
    gap: var(--space-1) var(--space-2);
    min-height: 0;
    padding: var(--space-3) var(--space-3) var(--space-2);
  }

  /* The session's parts become the bar's own: the controls in the first row (pushed right), the navigation in the
     second, the date of the data in the third. */
  .session {
    display: contents;
  }

  .actions {
    order: 1;
    margin-left: auto;
  }

  .nav {
    order: 3;
    flex: 0 0 100%;
    flex-wrap: wrap;
  }

  .nav a {
    padding: 0 var(--space-3);
  }

  /* border-box: with `flex: 0 0 100%` the padding would otherwise add to the bar's width and the page would scroll
     sideways by the bar's own gutter (found in the PR env, where the label reads "Dane nie były jeszcze …"). */
  .freshness {
    order: 4;
    flex: 0 0 100%;
    box-sizing: border-box;
    margin: 0;
    padding: 0 var(--space-3);
  }

  .freshness-list {
    display: flex;
    flex-wrap: wrap;
    gap: 0 var(--space-3);
  }

  .who {
    display: none;
  }
}
</style>
