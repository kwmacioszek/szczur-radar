<!--
  Dev only: router/index.ts adds /kitchen-sink when import.meta.env.DEV, so this view is not in the
  production build. Every colour role and shared component in every state on one page, for /10x-ui
  screenshots. Fixed data: no API calls, no session, no AppHeader.
-->
<script setup lang="ts">
import type { AssessmentOutput, Claim, ClaimKind, Direction, Flag, PostItem, Stance, Target } from '../api/posts'
import AppButton from '../components/AppButton.vue'
import PostAssessment from '../components/PostAssessment.vue'
import PostCard from '../components/PostCard.vue'
import StanceText from '../components/StanceText.vue'
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

/**
 * A company named in the body, as table §7.2 of the contract allows: assertiveness is null for mixed and
 * not_expressed; a bullish, bearish, neutral or mixed stance rests on claims (`claimIds`), not_expressed on none.
 */
function target(
  id: string,
  quote: string,
  direction: Direction,
  assertiveness: Target['stance']['assertiveness'],
  claimIds: string[] = [],
): Target {
  return {
    id,
    kind: 'company',
    anchor: { document: 'item', field: 'body', quote },
    stance: { direction, assertiveness, claim_ids: claimIds },
    reference: 'explicit',
  }
}

/** The author's claim about the targets, quoting the body; a directional stance rests on one of these. */
function authorClaim(id: string, kind: ClaimKind, targetIds: string[], evidence: string, statement: string): Claim {
  return { id, kind, attribution: 'author', target_ids: targetIds, evidence: quote(evidence), statement }
}

/** The card shows no claims; they are here so that each stance has the claims it rests on. */
function modelAssessment(targets: Target[], flags: Flag[], claims: Claim[] = []): PostItem['assessment'] {
  return {
    output: { relevance: 'investment_related', modifiers: [], targets, claims, flags },
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
      [target('t1', 'CDR', 'bullish', 'hedged', ['c1']), target('t2', 'Allegro', 'bearish', 'unhedged', ['c2'])],
      ['possible_sarcasm', 'missing_context'],
      [
        authorClaim('c1', 'trade_action', ['t1'], 'ja ostrożnie dobieram', 'Autor ostrożnie dokupuje akcje CDR po spadku.'),
        authorClaim('c2', 'trade_action', ['t2'], 'Allegro za to sprzedałem w całości', 'Autor sprzedał wszystkie akcje Allegro.'),
      ],
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
        target('t1', 'PGE', 'neutral', 'unhedged', ['c1']),
        target('t2', 'Enea', 'mixed', null, ['c2']),
        target('t3', 'Tauron', 'not_expressed', null),
        target('t4', 'Orlen', 'bullish', 'unhedged', ['c3']),
      ],
      [],
      [
        authorClaim('c1', 'opinion', ['t1'], 'PGE bez zmian', 'PGE nie zmienia się.'),
        authorClaim('c2', 'opinion', ['t2'], 'Enea z plusami i minusami', 'Enea ma zarówno zalety, jak i wady.'),
        authorClaim('c3', 'investment_thesis', ['t4'], 'Orlen powinien na skupie akcji zyskać', 'Skup akcji powinien podnieść kurs Orlenu.'),
      ],
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

/** Each direction with an assertiveness and claim_ids allowed by table §7.2 of the contract. */
const stances: Stance[] = [
  { direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1'] },
  { direction: 'bearish', assertiveness: 'unhedged', claim_ids: ['c1'] },
  { direction: 'neutral', assertiveness: 'unhedged', claim_ids: ['c1'] },
  { direction: 'mixed', assertiveness: null, claim_ids: ['c1', 'c2'] },
  { direction: 'not_expressed', assertiveness: null, claim_ids: [] },
  { direction: 'unclear', assertiveness: null, claim_ids: [] },
]

/** A quote from the post's body. */
function quote(text: string): Claim['evidence'] {
  return { document: 'item', field: 'body', quote: text }
}

function assessed(output: AssessmentOutput): NonNullable<PostItem['assessment']> {
  return { output, resultSource: 'model', assessedAt: '2026-10-12T21:00:00Z' }
}

/**
 * Every variant of the full assessment. The data satisfy contract V2: each stance.claim_ids entry
 * is a claim with that target in target_ids, and a directional stance rests on an author's claim.
 */
const assessments: { id: string; title: string; assessment: NonNullable<PostItem['assessment']> }[] = [
  {
    id: 'targets',
    title: 'Cele z tezami, teza o dwóch celach i tezy ogólne',
    assessment: assessed({
      relevance: 'investment_related',
      modifiers: [],
      targets: [
        {
          id: 't1',
          kind: 'company',
          anchor: quote('CDR'),
          stance: { direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1', 'c2'] },
          reference: 'explicit',
        },
        {
          id: 't2',
          kind: 'company',
          anchor: quote('Allegro'),
          stance: { direction: 'bearish', assertiveness: 'unhedged', claim_ids: ['c3', 'c5'] },
          reference: 'explicit',
        },
      ],
      claims: [
        {
          id: 'c1',
          kind: 'trade_action',
          attribution: 'author',
          target_ids: ['t1'],
          evidence: quote('ja ostrożnie dobieram'),
          statement: 'Autor ostrożnie dokupuje akcje CDR po spadku.',
        },
        {
          id: 'c2',
          kind: 'factual_claim',
          attribution: 'author',
          target_ids: ['t1'],
          evidence: quote('guidance na Q4 nie zmieniło się mimo opóźnienia premiery'),
          statement: 'Prognoza CDR na Q4 nie zmieniła się mimo opóźnienia premiery.',
        },
        {
          id: 'c3',
          kind: 'trade_action',
          attribution: 'author',
          target_ids: ['t2'],
          evidence: quote('Allegro za to sprzedałem w całości'),
          statement: 'Autor sprzedał wszystkie akcje Allegro.',
        },
        {
          id: 'c4',
          kind: 'factual_claim',
          attribution: 'author',
          target_ids: ['t1', 't2'],
          evidence: quote('Obie spółki i tak przegrywają z WIG20'),
          statement: 'CDR i Allegro radzą sobie gorzej niż WIG20.',
        },
        {
          id: 'c5',
          kind: 'opinion',
          attribution: 'author',
          target_ids: ['t2'],
          evidence: quote('marże lecą w dół'),
          statement: 'Marże Allegro spadają.',
        },
        {
          id: 'c6',
          kind: 'opinion',
          attribution: 'author',
          target_ids: [],
          evidence: quote('GPW to dziś kasyno dla cierpliwych'),
          statement: 'Polska giełda nagradza dziś tylko cierpliwych.',
        },
      ],
      flags: [],
    }),
  },
  {
    id: 'modifiers',
    title: 'Modyfikatory i flagi, cel nieokreślony i nierozpoznany',
    assessment: assessed({
      relevance: 'investment_related',
      modifiers: ['humor', 'promotion'],
      targets: [
        {
          id: 't1',
          kind: 'company',
          anchor: quote('XTB'),
          stance: { direction: 'unclear', assertiveness: null, claim_ids: ['c1'] },
          reference: 'explicit',
        },
        {
          id: 't2',
          kind: 'unknown',
          anchor: quote('Ta druga spółka'),
          stance: { direction: 'not_expressed', assertiveness: null, claim_ids: [] },
          reference: 'unresolved',
        },
      ],
      claims: [
        {
          id: 'c1',
          kind: 'opinion',
          attribution: 'author',
          target_ids: ['t1'],
          evidence: quote('Kupujcie XTB z mojego linku polecającego'),
          statement: 'Autor zachęca do otwarcia konta w XTB z jego linku polecającego.',
        },
        {
          id: 'c2',
          kind: 'opinion',
          attribution: 'author',
          target_ids: [],
          evidence: quote('i tak wszyscy na tym stracimy'),
          statement: 'Wszyscy inwestorzy i tak na tym stracą.',
        },
      ],
      flags: ['possible_sarcasm', 'missing_context'],
    }),
  },
  {
    id: 'attribution',
    title: 'Tezy przytoczone i nie wiadomo czyje',
    assessment: assessed({
      relevance: 'investment_related',
      modifiers: [],
      targets: [
        {
          id: 't1',
          kind: 'company',
          anchor: quote('PKO'),
          stance: { direction: 'neutral', assertiveness: 'unhedged', claim_ids: ['c3'] },
          reference: 'explicit',
        },
      ],
      claims: [
        {
          id: 'c1',
          kind: 'factual_claim',
          attribution: 'reported',
          target_ids: ['t1'],
          evidence: quote('Analitycy mBanku podnieśli cenę docelową PKO do 90 zł'),
          statement: 'Analitycy mBanku podnieśli cenę docelową PKO do 90 zł.',
        },
        {
          id: 'c2',
          kind: 'opinion',
          attribution: 'unclear',
          target_ids: ['t1'],
          evidence: quote('Ktoś pisał na mirko, że to pod emisję'),
          statement: 'Podwyżka ceny docelowej PKO ma być przygotowaniem pod emisję.',
        },
        {
          id: 'c3',
          kind: 'trade_action',
          attribution: 'author',
          target_ids: ['t1'],
          evidence: quote('Ja tam trzymam i nic nie robię'),
          statement: 'Autor trzyma akcje PKO i nic nie zmienia.',
        },
        {
          id: 'c4',
          kind: 'question',
          attribution: 'author',
          target_ids: ['t1'],
          evidence: quote('Ktoś wie, kiedy dywidenda?'),
          statement: 'Kiedy PKO wypłaci dywidendę?',
        },
      ],
      flags: [],
    }),
  },
  {
    id: 'long-quote',
    title: 'Cytat dowodu o długości 320 znaków',
    assessment: assessed({
      relevance: 'investment_related',
      modifiers: [],
      targets: [
        {
          id: 't1',
          kind: 'macro_topic',
          anchor: quote('giełdę'),
          stance: { direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1'] },
          reference: 'explicit',
        },
        {
          id: 't2',
          kind: 'sector',
          anchor: quote('banki'),
          stance: { direction: 'bearish', assertiveness: 'hedged', claim_ids: ['c1'] },
          reference: 'explicit',
        },
      ],
      claims: [
        {
          id: 'c1',
          kind: 'investment_thesis',
          attribution: 'author',
          target_ids: ['t1', 't2'],
          evidence: quote(
            'RPP pewnie jeszcze w tym roku zetnie stopy o kolejne 50 punktów bazowych, bo inflacja bazowa spada szybciej, niż zakładali ekonomiści, a złoty trzyma się mocno, więc lokaty i obligacje też przestaną się opłacać i kasa z banków popłynie na giełdę, tylko że najpierw dostaną po głowie same banki, którym skurczy się marża.',
          ),
          statement: 'Obniżki stóp skierują pieniądze z lokat na giełdę, ale najpierw obniżą marże banków.',
        },
      ],
      flags: [],
    }),
  },
  {
    id: 'empty',
    title: 'Ocena modelu bez celów i tez',
    assessment: assessed({ relevance: 'not_investment_related', modifiers: ['humor'], targets: [], claims: [], flags: [] }),
  },
  {
    id: 'prefilter',
    title: 'Prefiltr',
    assessment: {
      output: { relevance: 'unclear', modifiers: [], targets: [], claims: [], flags: ['insufficient_content'] },
      resultSource: 'prefilter',
      assessedAt: '2026-10-12T21:00:00Z',
    },
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

    <section aria-labelledby="stances-title">
      <h2 id="stances-title">Stanowisko</h2>
      <ul class="samples">
        <li v-for="stance in stances" :key="stance.direction" class="row">
          <code class="row-title">{{ stance.direction }}</code>
          <StanceText :stance="stance" />
        </li>
      </ul>
    </section>

    <section aria-labelledby="assessments-title">
      <h2 id="assessments-title">Ocena posta</h2>
      <ul class="samples">
        <li v-for="sample in assessments" :key="sample.id" class="sample">
          <p class="sample-title">{{ sample.title }}</p>
          <PostAssessment :assessment="sample.assessment" />
        </li>
      </ul>
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

.samples {
  margin: 0;
  padding: 0;
  list-style: none;
}

/* Each variant of the assessment framed like a card, so the variants stay apart. */
.sample {
  margin-bottom: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.sample-title {
  margin: 0 0 var(--space-3);
  color: var(--text-h);
  font-size: var(--font-size-sm);
  font-weight: 600;
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
