<!--
  Dev only: router/index.ts adds /kitchen-sink when import.meta.env.DEV, so this view is not in the
  production build. Every colour role and shared component in every state on one page, for /10x-ui
  screenshots. Fixed data: no API calls, no session, no AppHeader.
-->
<script setup lang="ts">
import type { Direction, Flag, PostItem, Target } from '../api/posts'
import AppButton from '../components/AppButton.vue'
import PostCard from '../components/PostCard.vue'
import StatusBadge, { type BadgeTone } from '../components/StatusBadge.vue'
import StatusMessage from '../components/StatusMessage.vue'

/** The colour roles of :root in src/style.css, in the order of the plan's token table. */
const colourRoles = [
  '--bg',
  '--text',
  '--text-h',
  '--accent',
  '--on-accent',
  '--success',
  '--warning',
  '--destructive',
  '--border',
  '--ring',
] as const

type ColourRole = (typeof colourRoles)[number]

/** A tile filled with the role; --on-accent is a text colour, so it shows as text on --accent. */
function swatchStyle(role: ColourRole): Record<string, string> {
  return role === '--on-accent'
    ? { background: 'var(--accent)', color: 'var(--on-accent)' }
    : { background: `var(${role})` }
}

const variants = [
  { name: 'primary', title: 'Główny' },
  { name: 'secondary', title: 'Pomocniczy' },
] as const

/** Every tone with the run status the admin panel shows in it. */
const badges: { tone: BadgeTone; text: string }[] = [
  { tone: 'success', text: 'zakończony' },
  { tone: 'warning', text: 'częściowo' },
  { tone: 'destructive', text: 'błąd' },
  { tone: 'info', text: 'trwa' },
  { tone: 'neutral', text: 'w kolejce' },
]

/** A company named in the body; assertiveness is null for mixed and not_expressed (table §7.2 of the contract). */
function target(id: string, quote: string, direction: Direction, assertiveness: Target['stance']['assertiveness']): Target {
  return {
    id,
    kind: 'company',
    anchor: { document: 'item', field: 'body', quote },
    stance: { direction, assertiveness, claim_ids: [] },
    reference: 'explicit',
  }
}

function modelAssessment(targets: Target[], flags: Flag[]): PostItem['assessment'] {
  return {
    output: { relevance: 'investment_related', modifiers: [], targets, claims: [], flags },
    resultSource: 'model',
    assessedAt: '2026-10-12T21:00:00Z',
  }
}

/** Every variant of the feed's card, newest first. */
const posts: PostItem[] = [
  {
    id: 'model',
    itemType: 'post',
    publishedAt: '2026-10-12T19:45:00Z',
    title: null,
    body: 'CDR po wynikach -8% na otwarciu, ja ostrożnie dobieram, bo guidance na Q4 nie zmieniło się mimo opóźnienia premiery. Allegro za to sprzedałem w całości: marże lecą w dół, a „rekordowy kwartał” widać chyba tylko w prezentacji dla akcjonariuszy.',
    author: 'ostrozny_byk',
    webUrl: 'https://wykop.pl/wpis/81234567',
    assessment: modelAssessment(
      [target('t1', 'CDR', 'bullish', 'hedged'), target('t2', 'Allegro', 'bearish', 'unhedged')],
      ['possible_sarcasm', 'missing_context'],
    ),
  },
  {
    id: 'link',
    itemType: 'link',
    publishedAt: '2026-10-12T18:10:00Z',
    title: 'Orlen ogłasza buyback za 4 mld zł',
    body: 'PGE bez zmian, Enea z plusami i minusami, Tauron podał dziś wyniki, a Orlen powinien na skupie akcji zyskać.',
    author: 'dywidendowy_zbyszek',
    webUrl: 'https://wykop.pl/link/7712345',
    assessment: modelAssessment(
      [
        target('t1', 'PGE', 'neutral', 'unhedged'),
        target('t2', 'Enea', 'mixed', null),
        target('t3', 'Tauron', 'not_expressed', null),
        target('t4', 'Orlen', 'bullish', 'unhedged'),
      ],
      [],
    ),
  },
  {
    id: 'prefilter',
    itemType: 'comment',
    publishedAt: '2026-10-12T17:30:00Z',
    title: null,
    body: '@ostrozny_byk #gielda #orlen',
    author: 'wykresiarz',
    webUrl: 'https://wykop.pl/wpis/81234567#comment-81234601',
    assessment: {
      output: { relevance: 'unclear', modifiers: [], targets: [], claims: [], flags: ['insufficient_content'] },
      resultSource: 'prefilter',
      assessedAt: '2026-10-12T21:00:00Z',
    },
  },
  {
    id: 'no-assessment',
    itemType: 'comment',
    publishedAt: '2026-10-12T16:05:00Z',
    title: null,
    body: 'Ten wykres XTB to klasyczna głowa z ramionami, ja bym uważał.',
    author: 'wykresiarz',
    webUrl: 'https://wykop.pl/wpis/81230011#comment-81230042',
    assessment: null,
  },
  {
    id: 'long-url',
    itemType: 'post',
    publishedAt: '2026-10-12T15:20:00Z',
    title: null,
    body: 'Analiza skupu akcji Orlenu: https://www.bankier.pl/wiadomosc/Orlen-oglasza-skup-akcji-wlasnych-za-4-mld-zl-analitycy-licza-wplyw-na-dywidende-8812345.html?utm_source=wykop&utm_medium=social',
    author: 'dywidendowy_zbyszek',
    webUrl: 'https://wykop.pl/wpis/81229876',
    assessment: modelAssessment([target('t1', 'Orlen', 'not_expressed', null)], ['missing_context']),
  },
]
</script>

<template>
  <main class="kitchen-sink">
    <h1>Kitchen sink</h1>
    <p>Tylko w trybie dev: role kolorów i komponenty we wszystkich stanach, do zrzutów ekranu.</p>

    <section aria-labelledby="tokens-title">
      <h2 id="tokens-title">Tokeny</h2>
      <ul class="swatches">
        <li v-for="role in colourRoles" :key="role" class="swatch">
          <span class="tile" :style="swatchStyle(role)" aria-hidden="true">
            <template v-if="role === '--on-accent'">Aa</template>
          </span>
          <code>{{ role }}</code>
        </li>
      </ul>
    </section>

    <section aria-labelledby="buttons-title">
      <h2 id="buttons-title">Przyciski</h2>
      <div v-for="variant in variants" :key="variant.name" class="row">
        <span class="row-title">{{ variant.title }}</span>
        <AppButton :variant="variant.name">Domyślny</AppButton>
        <AppButton :variant="variant.name" disabled>Wyłączony</AppButton>
        <AppButton :variant="variant.name" busy busy-label="W toku…">W toku</AppButton>
      </div>
    </section>

    <section aria-labelledby="messages-title">
      <h2 id="messages-title">Komunikaty</h2>
      <StatusMessage tone="ok">Uruchomiono: zakres dat.</StatusMessage>
      <StatusMessage tone="error">Przebieg już trwa. Poczekaj, aż się zakończy, i spróbuj ponownie.</StatusMessage>
    </section>

    <section aria-labelledby="badges-title">
      <h2 id="badges-title">Znaczniki</h2>
      <div class="row">
        <StatusBadge v-for="badge in badges" :key="badge.tone" :tone="badge.tone">{{ badge.text }}</StatusBadge>
      </div>
    </section>

    <section aria-labelledby="fields-title">
      <h2 id="fields-title">Pola</h2>
      <div class="row">
        <label>Od <input type="date" value="2026-10-09" /></label>
        <label><input type="checkbox" /> Niezaznaczone</label>
        <label><input type="checkbox" checked /> Zaznaczone</label>
        <label><input type="checkbox" disabled /> Wyłączone</label>
      </div>
    </section>

    <section aria-labelledby="posts-title">
      <h2 id="posts-title">Posty</h2>
      <PostCard v-for="post in posts" :key="post.id" :post="post" />
    </section>
  </main>
</template>

<style scoped>
.kitchen-sink {
  max-width: 56rem;
  margin: 0 auto;
  padding: var(--space-4);
  box-sizing: border-box;
}

h1 {
  margin: var(--space-2) 0 var(--space-4);
}

h2 {
  margin: var(--space-5) 0 var(--space-2);
  font-size: var(--font-size-lg);
  color: var(--text-h);
}

.row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.row-title {
  min-width: 7rem;
  color: var(--text-h);
}

.swatches {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(11rem, 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.swatch {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-h);
}

/* Outlined, so --bg and --border stay visible on the page background. */
.tile {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 2.5rem;
  height: 2.5rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-weight: 600;
}
</style>
