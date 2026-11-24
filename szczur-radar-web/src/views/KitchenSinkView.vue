<!--
  Dev only: router/index.ts adds /kitchen-sink when import.meta.env.DEV, so this view is not in the
  production build. Every colour role and shared component in every state on one page, for /10x-ui
  screenshots. Fixed data: no API calls, no session.
-->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { Freshness } from '../api/freshness'
import type { AdminNotification } from '../api/notifications'
import type {
  AssessmentOutput,
  Claim,
  ClaimKind,
  Direction,
  Flag,
  PostFilter,
  PostItem,
  Stance,
  Target,
  TargetKind,
} from '../api/posts'
import AppBar, { type NavItem } from '../components/AppBar.vue'
import AppBrand from '../components/AppBrand.vue'
import AppButton from '../components/AppButton.vue'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import FeedFilter from '../components/FeedFilter.vue'
import FormField from '../components/FormField.vue'
import { ICON_NAMES } from '../components/iconNames'
import LoadingState from '../components/LoadingState.vue'
import NotificationMenu from '../components/NotificationMenu.vue'
import PagerNav from '../components/PagerNav.vue'
import PageHeader from '../components/PageHeader.vue'
import PostAssessment from '../components/PostAssessment.vue'
import PostRow from '../components/PostRow.vue'
import StanceText from '../components/StanceText.vue'
import StatusBadge, { type BadgeTone } from '../components/StatusBadge.vue'
import StatusMessage from '../components/StatusMessage.vue'
import ThemeToggle from '../components/ThemeToggle.vue'
import { label } from '../labels'

/** The colour roles of :root in src/style.css, in the order of tokens.css (context/foundation/ui-design/). */
const colourRoles = [
  '--bg',
  '--bg-glow',
  '--surface',
  '--surface-2',
  '--selected',
  '--scrim',
  '--text',
  '--text-h',
  '--text-muted',
  '--accent',
  '--on-accent',
  '--ring',
  '--line',
  '--border',
  '--success',
  '--warning',
  '--destructive',
  '--stance-neutral',
  '--stance-mixed',
  '--success-soft',
  '--warning-soft',
  '--destructive-soft',
  '--neutral-soft',
  '--mixed-soft',
  '--highlight',
] as const

type ColourRole = (typeof colourRoles)[number]

/** A tile filled with the role; --on-accent is a text colour, so it shows as text on --accent, --highlight as a background under text. */
function swatchStyle(role: ColourRole): Record<string, string> {
  if (role === '--on-accent') return { background: 'var(--accent)', color: 'var(--on-accent)' }
  if (role === '--highlight') return { background: 'var(--highlight)', color: 'var(--text-h)' }
  return { background: `var(${role})` }
}

/** Every tone with the run statuses and markers the admin panel shows in it (src/runs.ts). */
const badges: { tone: BadgeTone; text: string }[] = [
  { tone: 'success', text: 'zakończony' },
  { tone: 'warning', text: 'zakończony — pominięte: 3' },
  { tone: 'warning', text: 'częściowo' },
  { tone: 'warning', text: 'przerwany' },
  { tone: 'warning', text: 'przerwany — nowe wdrożenie' },
  { tone: 'warning', text: 'przerwany — utracona instancja' },
  { tone: 'destructive', text: 'błąd' },
  { tone: 'destructive', text: 'wymaga uwagi' },
  { tone: 'info', text: 'trwa' },
  { tone: 'neutral', text: 'w kolejce' },
]

/** The time the bell's relative times count from: 2026-10-15, 10:00 in Warsaw. */
const BELL_NOW = Date.parse('2026-10-15T08:00:00Z')

/** One notification about a run that needs attention, as the API lists it. */
function notification(
  id: string,
  createdAt: string,
  readAt: string | null,
  run: Partial<NonNullable<AdminNotification['run']>> & Pick<NonNullable<AdminNotification['run']>, 'status' | 'attentionReasons'>,
): AdminNotification {
  return {
    id,
    kind: 'run_attention',
    createdAt,
    readAt,
    run: {
      id: `run-${id}`,
      kind: 'range',
      interruptionCause: null,
      finishedAt: createdAt,
      assessedFailed: 0,
      dead: 0,
      processingErrors: 0,
      skipped: 0,
      error: null,
      ...run,
    },
  }
}

/** Two unread (newest), then five read; every reason the API notifies about. */
const notifications: AdminNotification[] = [
  notification('n1', '2026-10-15T07:59:30Z', null, {
    status: 'failed',
    attentionReasons: ['failed'],
    error: 'no progress for 39m 00s: the run was stopped',
  }),
  notification('n2', '2026-10-15T07:12:00Z', null, {
    status: 'interrupted',
    attentionReasons: ['interrupted'],
    kind: 'retry',
    interruptionCause: 'deploy',
    error: 'interrupted: a new deployment took over',
  }),
  // A source failed, the other's posts were assessed: partial, the failed source named in the run's error.
  notification('n7', '2026-10-15T01:30:00Z', '2026-10-15T06:00:00Z', {
    status: 'partial',
    attentionReasons: ['source_failed'],
    error: 'reddit failed: RedditApiException: Reddit GET /r/inwestowanie/new answered HTTP 503 after 3 attempts',
  }),
  notification('n3', '2026-10-15T01:05:00Z', '2026-10-15T06:00:00Z', {
    status: 'partial',
    attentionReasons: ['dead_posts'],
    dead: 1,
    assessedFailed: 7,
  }),
  notification('n4', '2026-10-14T00:52:00Z', '2026-10-14T06:00:00Z', {
    status: 'succeeded',
    attentionReasons: ['skipped'],
    skipped: 3,
  }),
  notification('n5', '2026-10-13T22:10:03Z', '2026-10-14T06:00:00Z', {
    status: 'failed',
    attentionReasons: ['failed'],
    error: 'NoPostsFetchedException: wykop gave no post to store (fetched 0, skipped 0)',
  }),
  notification('n6', '2026-10-13T00:58:00Z', '2026-10-13T07:00:00Z', {
    status: 'interrupted',
    attentionReasons: ['interrupted'],
    interruptionCause: 'restart',
    error: 'interrupted: the application restarted',
  }),
]

const readNotifications = notifications.map((item) => ({ ...item, readAt: item.readAt ?? '2026-10-15T08:00:00Z' }))

/** The bell in each state; `open` is each sample's own, so every one can be opened and closed. */
const bells: {
  id: string
  title: string
  state: 'loading' | 'error' | 'loaded'
  unread: number | null
  items: AdminNotification[]
}[] = [
  { id: 'closed-unread', title: 'Zamknięty, 2 nieprzeczytane', state: 'loaded', unread: 2, items: notifications },
  { id: 'closed-read', title: 'Zamknięty, bez nieprzeczytanych', state: 'loaded', unread: 0, items: readNotifications },
  { id: 'open-list', title: 'Otwarty z listą', state: 'loaded', unread: 2, items: notifications },
  { id: 'open-empty', title: 'Otwarty, pusty', state: 'loaded', unread: 0, items: [] },
  { id: 'open-error', title: 'Otwarty, błąd', state: 'error', unread: null, items: [] },
  { id: 'open-loading', title: 'Otwarty, ładowanie', state: 'loading', unread: null, items: [] },
]

const bellOpen = reactive<Record<string, boolean>>(
  Object.fromEntries(bells.map((bell) => [bell.id, bell.id.startsWith('open')])),
)

/** The two items of an admin's navigation; the user has the first. The page belongs to "Posty". */
const postsItem: NavItem = { label: 'Posty', to: { name: 'posts' }, current: true }
const adminItem: NavItem = { label: 'Panel admina', to: { name: 'admin' }, current: false }

/** One source per day and hour, the older one a few days back: the label shows the day, not only the hour. */
const barFreshness: Freshness = {
  sources: [
    { source: 'wykop', lastUpdatedAt: '2026-10-13T03:12:00Z' },
    { source: 'reddit', lastUpdatedAt: '2026-10-11T03:12:00Z' },
  ],
}

/** The bar's variants: the admin with the bell, a user, no update yet, and a date that could not be read. */
const bars: {
  id: string
  title: string
  items: NavItem[]
  freshness: Freshness | null
  email: string
  bell?: true
}[] = [
  {
    id: 'admin',
    title: 'Administrator z dzwonkiem, dwa źródła, zaznaczone Posty',
    items: [postsItem, adminItem],
    freshness: barFreshness,
    email: 'admin@szczur-radar.example',
    bell: true,
  },
  {
    id: 'user',
    title: 'Użytkownik, zaznaczone Posty',
    items: [postsItem],
    freshness: barFreshness,
    email: 'jan.kowalski@szczur-radar.example',
  },
  {
    id: 'none',
    title: 'Bez żadnej aktualizacji',
    items: [postsItem],
    freshness: { sources: [] },
    email: 'jan.kowalski@szczur-radar.example',
  },
  {
    id: 'error',
    title: 'Bez etykiety daty (błąd odczytu)',
    items: [postsItem],
    freshness: null,
    email: 'jan.kowalski@szczur-radar.example',
  },
]

/** The admin variant's bell can be opened, to see its panel under the bar. */
const barBellOpen = ref(false)

/**
 * A target named in the body (a company unless `kind` says otherwise), as table §7.2 of the contract allows:
 * assertiveness is null for mixed and not_expressed; a bullish, bearish, neutral or mixed stance rests on claims
 * (`claimIds`), not_expressed on none.
 */
function target(
  id: string,
  quote: string,
  direction: Direction,
  assertiveness: Target['stance']['assertiveness'],
  claimIds: string[] = [],
  kind: TargetKind = 'company',
): Target {
  return {
    id,
    kind,
    anchor: { document: 'item', field: 'body', quote },
    stance: { direction, assertiveness, claim_ids: claimIds },
    reference: 'explicit',
  }
}

/** The author's claim about the targets, quoting the body; a directional stance rests on one of these. */
function authorClaim(id: string, kind: ClaimKind, targetIds: string[], evidence: string, statement: string): Claim {
  return { id, kind, attribution: 'author', target_ids: targetIds, evidence: quote(evidence), statement }
}

/** The row shows no claims; they are here so that each stance has the claims it rests on. */
function modelAssessment(targets: Target[], flags: Flag[], claims: Claim[] = []): PostItem['assessment'] {
  return {
    output: { relevance: 'investment_related', modifiers: [], targets, claims, flags },
    resultSource: 'model',
    assessedAt: '2026-10-12T21:00:00Z',
  }
}

/** The feed filter as applied from the address, every field set. */
const activeFilter: PostFilter = { q: 'orlen', kind: 'company', direction: 'bullish' }

/**
 * Every variant of the list's row, newest first: one, two, three and five targets of four kinds, flags, a post the model
 * found no target in, one not about investing, the prefilter's, one without an assessment and Reddit's.
 */
const posts: PostItem[] = [
  {
    id: 'model',
    source: 'wykop',
    channel: 'gielda',
    itemType: 'post',
    publishedAt: '2026-10-12T19:45:00Z',
    title: null,
    body: 'CDR po wynikach -8% na otwarciu, ja ostrożnie dobieram, bo guidance na Q4 nie zmieniło się mimo opóźnienia premiery. Allegro za to sprzedałem w całości: marże lecą w dół, a „rekordowy kwartał” widać chyba tylko w prezentacji dla akcjonariuszy.',
    author: 'ostrozny_byk',
    webUrl: 'https://wykop.pl/wpis/81234567',
    assessment: modelAssessment(
      [
        target('t1', 'CDR', 'bullish', 'hedged', ['c1']),
        target('t2', 'Allegro', 'bearish', 'unhedged', ['c2']),
        target('t3', 'guidance na Q4', 'unclear', null, [], 'macro_topic'),
      ],
      ['possible_sarcasm', 'missing_context'],
      [
        authorClaim('c1', 'trade_action', ['t1'], 'ja ostrożnie dobieram', 'Autor ostrożnie dokupuje akcje CDR po spadku.'),
        authorClaim('c2', 'trade_action', ['t2'], 'Allegro za to sprzedałem w całości', 'Autor sprzedał wszystkie akcje Allegro.'),
      ],
    ),
  },
  {
    id: 'link',
    source: 'wykop',
    channel: 'gielda',
    itemType: 'link',
    publishedAt: '2026-10-12T18:10:00Z',
    title: 'Orlen ogłasza buyback za 4 mld zł',
    body: 'PGE bez zmian, Enea z plusami i minusami, Tauron podał dziś wyniki, a Orlen powinien na skupie akcji zyskać. Cała energetyka może to odczuć.',
    author: 'dywidendowy_zbyszek',
    webUrl: 'https://wykop.pl/link/7712345',
    assessment: modelAssessment(
      [
        target('t1', 'PGE', 'neutral', 'unhedged', ['c1']),
        target('t2', 'Enea', 'mixed', null, ['c2']),
        target('t3', 'Tauron', 'not_expressed', null),
        target('t4', 'Orlen', 'bullish', 'unhedged', ['c3']),
        target('t5', 'energetyka', 'bullish', 'hedged', ['c4'], 'sector'),
      ],
      [],
      [
        authorClaim('c1', 'opinion', ['t1'], 'PGE bez zmian', 'PGE nie zmienia się.'),
        authorClaim('c2', 'opinion', ['t2'], 'Enea z plusami i minusami', 'Enea ma zarówno zalety, jak i wady.'),
        authorClaim('c3', 'investment_thesis', ['t4'], 'Orlen powinien na skupie akcji zyskać', 'Skup akcji powinien podnieść kurs Orlenu.'),
        authorClaim('c4', 'opinion', ['t5'], 'Cała energetyka może to odczuć', 'Skup akcji Orlenu może odczuć cały sektor energetyczny.'),
      ],
    ),
  },
  {
    id: 'prefilter',
    source: 'wykop',
    channel: 'gielda',
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
    id: 'no-targets',
    source: 'wykop',
    channel: 'gielda',
    itemType: 'link',
    publishedAt: '2026-10-12T17:10:00Z',
    title: 'KNF ostrzega przed nowym schematem piramidy finansowej',
    body: 'Komisja ostrzega przed platformą obiecującą 30% miesięcznie.',
    author: 'dywidendowy_zbyszek',
    webUrl: 'https://wykop.pl/link/7712399',
    assessment: modelAssessment([], ['missing_context']),
  },
  {
    id: 'not-investment',
    source: 'wykop',
    channel: 'gielda',
    itemType: 'post',
    publishedAt: '2026-10-12T16:40:00Z',
    title: null,
    body: 'Ktoś poleci dobrą kawiarnię w okolicy GPW? Po sesji trzeba gdzieś odpocząć.',
    author: 'wykresiarz',
    webUrl: 'https://wykop.pl/wpis/81230007',
    assessment: {
      output: { relevance: 'not_investment_related', modifiers: ['humor'], targets: [], claims: [], flags: [] },
      resultSource: 'model',
      assessedAt: '2026-10-12T21:00:00Z',
    },
  },
  {
    id: 'no-assessment',
    source: 'wykop',
    channel: 'gielda',
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
    source: 'wykop',
    channel: 'gielda',
    itemType: 'post',
    publishedAt: '2026-10-12T15:20:00Z',
    title: null,
    body: 'Analiza skupu akcji Orlenu przy spadku ceny ropy: https://www.bankier.pl/wiadomosc/Orlen-oglasza-skup-akcji-wlasnych-za-4-mld-zl-analitycy-licza-wplyw-na-dywidende-8812345.html?utm_source=wykop&utm_medium=social',
    author: 'dywidendowy_zbyszek',
    webUrl: 'https://wykop.pl/wpis/81229876',
    assessment: modelAssessment(
      [target('t1', 'Orlen', 'not_expressed', null), target('t2', 'ropy', 'bearish', 'hedged', ['c1'], 'asset')],
      ['missing_context'],
      [authorClaim('c1', 'factual_claim', ['t2'], 'przy spadku ceny ropy', 'Cena ropy spada.')],
    ),
  },
  {
    id: 'reddit',
    source: 'reddit',
    channel: 'ValueInvesting',
    itemType: 'post',
    publishedAt: '2026-10-12T14:40:00Z',
    title: 'Is Berkshire sitting on too much cash?',
    body: 'Berkshire keeps adding to its cash pile and I am still holding. The market looks expensive to me.',
    author: 'cierpliwy_kapital',
    webUrl: 'https://www.reddit.com/r/ValueInvesting/comments/1xgabc1/is_berkshire_sitting_on_too_much_cash/',
    assessment: modelAssessment(
      [target('t1', 'Berkshire', 'bullish', 'hedged', ['c1'])],
      [],
      [authorClaim('c1', 'trade_action', ['t1'], 'I am still holding', 'Autor nadal trzyma akcje Berkshire.')],
    ),
  },
  {
    id: 'reddit-link',
    source: 'reddit',
    channel: 'stocks',
    itemType: 'link',
    publishedAt: '2026-10-12T15:10:00Z',
    title: 'Berkshire 13F: cash pile at a record high',
    body: 'Berkshire keeps adding to its cash pile. The filing shows no new large positions.',
    author: 'cierpliwy_kapital',
    webUrl: 'https://www.reddit.com/r/stocks/comments/1xgabc2/berkshire_13f_cash_pile_at_a_record_high/',
    assessment: modelAssessment(
      [target('t1', 'Berkshire', 'bullish', 'hedged', ['c1'])],
      [],
      [authorClaim('c1', 'trade_action', ['t1'], 'keeps adding to its cash pile', 'Autor opisuje rosnącą gotówkę Berkshire.')],
    ),
  },
]

/**
 * Each direction as table §7.2 of the contract allows: assertiveness `unhedged` for bullish, bearish and neutral,
 * `null` for mixed, unclear and not_expressed; claim_ids 1–4 for the directional ones, none for not_expressed.
 */
const stances: Stance[] = [
  { direction: 'bullish', assertiveness: 'unhedged', claim_ids: ['c1'] },
  { direction: 'bearish', assertiveness: 'unhedged', claim_ids: ['c1'] },
  { direction: 'neutral', assertiveness: 'unhedged', claim_ids: ['c1'] },
  { direction: 'mixed', assertiveness: null, claim_ids: ['c1', 'c2'] },
  { direction: 'not_expressed', assertiveness: null, claim_ids: [] },
  { direction: 'unclear', assertiveness: null, claim_ids: [] },
]

/** The three directions that can be hedged ("(ostrożnie)"): `hedged` is theirs only (table §7.2). */
const hedgedStances: Stance[] = [
  { direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1'] },
  { direction: 'bearish', assertiveness: 'hedged', claim_ids: ['c1'] },
  { direction: 'neutral', assertiveness: 'hedged', claim_ids: ['c1'] },
]

/** A target with its stance: in a list of posts (name and stance), with its kind, and a name too long for its box. */
const targetStances: { id: string; title: string; items: { target: string; kind?: string; stance: Stance }[] }[] = [
  {
    id: 'name',
    title: 'W liście postów: nazwa i stanowisko',
    items: [
      { target: 'KGHM', stance: stances[0] },
      { target: 'CD Projekt', stance: hedgedStances[1] },
      { target: 'złoto', stance: stances[2] },
    ],
  },
  {
    id: 'kind',
    title: 'Z rodzajem celu',
    items: [
      { target: 'KGHM', kind: label('targetKind', 'company'), stance: stances[0] },
      { target: 'stopy procentowe', kind: label('targetKind', 'macro_topic'), stance: stances[3] },
      { target: 'banki', kind: label('targetKind', 'sector'), stance: stances[4] },
    ],
  },
]

/** The name of a target that does not fit its box: the name is cut short, the stance stays. */
const longTarget = 'Powszechny Zakład Ubezpieczeń Spółka Akcyjna'

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
            <template v-if="role === '--on-accent' || role === '--highlight'">Aa</template>
          </span>
          <code>{{ role }}</code>
        </li>
      </ul>
    </section>

    <section aria-labelledby="buttons-title">
      <h2 id="buttons-title">Przycisk</h2>
      <div class="row">
        <span class="row-title">Główny</span>
        <AppButton variant="primary">Uruchom</AppButton>
        <AppButton variant="primary" disabled>Zapisz</AppButton>
        <AppButton variant="primary" busy busy-label="Uruchamianie…">Uruchom przebieg</AppButton>
      </div>
      <div class="row">
        <span class="row-title">Zwykły</span>
        <AppButton>Wyczyść filtry</AppButton>
        <AppButton disabled>Wyloguj</AppButton>
        <AppButton busy busy-label="Wylogowywanie…">Wyloguj się</AppButton>
      </div>
      <div class="row">
        <span class="row-title">Z ikoną</span>
        <AppButton icon="i-refresh-cw">Spróbuj ponownie</AppButton>
        <AppButton icon="i-rotate-ccw">Przywróć martwe</AppButton>
        <AppButton variant="primary" icon="i-play">Uruchom przebieg</AppButton>
      </div>
      <div class="row">
        <span class="row-title">Ikona na końcu</span>
        <AppButton icon="i-chevron-right" icon-position="end">Starsze</AppButton>
        <AppButton icon="i-chevron-right" icon-position="end" disabled>Następne</AppButton>
        <AppButton icon="i-chevron-right" icon-position="end" busy busy-label="Wczytywanie…">Starsze</AppButton>
      </div>
      <div class="row">
        <span class="row-title">Duży</span>
        <AppButton size="lg" icon="i-list-filter">Filtry</AppButton>
      </div>
      <div class="row">
        <span class="row-title">Sama ikona</span>
        <AppButton icon="i-chevron-left" icon-only aria-label="Poprzedni post" />
        <AppButton icon="i-chevron-right" icon-only aria-label="Następny post" />
        <AppButton icon="i-x" icon-only aria-label="Zamknij" />
        <AppButton icon="i-chevron-left" icon-only disabled aria-label="Poprzedni dzień" />
        <AppButton icon="i-braces" icon-only aria-pressed="true" aria-label="Surowe dane" />
      </div>
    </section>

    <section aria-labelledby="links-title">
      <h2 id="links-title">Odnośnik</h2>
      <div class="row">
        <span class="row-title">Wewnętrzny</span>
        <RouterLink :to="{ name: 'posts' }">Wszystkie posty o KGHM</RouterLink>
      </div>
      <div class="row">
        <span class="row-title">Zewnętrzny</span>
        <a
          class="ext"
          href="https://wykop.pl/"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Oryginał na wykop.pl (otwiera się w nowej karcie)"
        >
          Oryginał na wykop.pl
          <AppIcon name="i-external-link" />
        </a>
      </div>
    </section>

    <section aria-labelledby="fields-title">
      <h2 id="fields-title">Pola</h2>
      <div class="fields">
        <FormField label="E-mail">
          <template #default="{ fieldProps }">
            <input v-bind="fieldProps" type="email" value="jan@example.pl" />
          </template>
        </FormField>
        <FormField label="E-mail (puste)">
          <template #default="{ fieldProps }">
            <input v-bind="fieldProps" type="email" />
          </template>
        </FormField>
        <FormField label="E-mail (wyłączone)">
          <template #default="{ fieldProps }">
            <input v-bind="fieldProps" type="email" value="jan@example.pl" disabled />
          </template>
        </FormField>
        <FormField class="wide" label="Hasło" error="Hasło musi mieć co najmniej 12 znaków.">
          <template #default="{ fieldProps }">
            <input v-bind="fieldProps" type="password" value="haslo-probne" />
          </template>
        </FormField>
        <FormField class="wide" label="Powtórz hasło" hint="Te same znaki co wyżej.">
          <template #default="{ fieldProps }">
            <input v-bind="fieldProps" type="password" />
          </template>
        </FormField>
        <FormField label="Puste">
          <template #default="{ fieldProps }">
            <span class="input-wrap">
              <AppIcon name="i-search" />
              <input v-bind="fieldProps" type="search" />
            </span>
          </template>
        </FormField>
        <FormField label="Wypełnione">
          <template #default="{ fieldProps }">
            <span class="input-wrap">
              <AppIcon name="i-search" />
              <input v-bind="fieldProps" type="search" value="KGHM" />
            </span>
          </template>
        </FormField>
        <FormField label="Dzień">
          <template #default="{ fieldProps }">
            <input v-bind="fieldProps" type="date" value="2026-10-20" />
          </template>
        </FormField>
        <FormField label="Rodzaj celu">
          <template #default="{ fieldProps }">
            <select v-bind="fieldProps">
              <option>dowolny</option>
              <option>spółka</option>
              <option>aktywo</option>
              <option>sektor</option>
              <option>makro i rynek</option>
            </select>
          </template>
        </FormField>
        <FormField label="Stanowisko">
          <template #default="{ fieldProps }">
            <select v-bind="fieldProps" disabled>
              <option>dowolne</option>
            </select>
          </template>
        </FormField>
      </div>
      <div class="row">
        <label class="check"><input type="checkbox" checked /> Podświetl cytaty</label>
        <label class="check"><input type="checkbox" /> Tylko ostrożne</label>
        <label class="check"><input type="checkbox" disabled /> Wyłączone</label>
      </div>
      <div class="input-wrap input-lg">
        <AppIcon name="i-search" />
        <input type="search" aria-label="Szukaj w postach" placeholder="Szukaj w tytule i treści" />
      </div>
    </section>

    <section aria-labelledby="messages-title">
      <h2 id="messages-title">Komunikat</h2>
      <div class="stack">
        <StatusMessage tone="ok">Hasło ustawione. Zaloguj się.</StatusMessage>
        <StatusMessage tone="error">Przebieg już trwa. Poczekaj, aż się zakończy, i spróbuj ponownie.</StatusMessage>
        <StatusMessage tone="warning">Przebieg trwa. Nowy stanie w kolejce.</StatusMessage>
      </div>
    </section>

    <section aria-labelledby="pager-title">
      <h2 id="pager-title">Stronicowanie</h2>
      <p>Pod listą: nowsze i starsze posty oraz numer strony. Na pierwszej stronie „Nowsze” jest wyłączone.</p>
      <ul class="samples">
        <li class="variant">
          <p class="sample-title">Pierwsza strona</p>
          <PagerNav :page="0" :pages="7" />
        </li>
        <li class="variant">
          <p class="sample-title">W środku, podczas wczytywania następnej strony</p>
          <PagerNav :page="2" :pages="7" busy="next" />
        </li>
        <li class="variant">
          <p class="sample-title">Ostatnia strona</p>
          <PagerNav :page="6" :pages="7" />
        </li>
        <li class="variant">
          <p class="sample-title">Poprzednie i następne (lista w ustalonej kolejności), pierwsza strona</p>
          <PagerNav :page="0" :pages="5" labels="order" />
        </li>
        <li class="variant">
          <p class="sample-title">Jedna strona</p>
          <PagerNav :page="0" :pages="1" />
          <p>Przy jednej stronie stronicowania nie ma.</p>
        </li>
      </ul>
    </section>

    <section aria-labelledby="loading-title">
      <h2 id="loading-title">Ładowanie</h2>
      <p>Zanim przyjdą dane, widok pokazuje swój kształt. Czytnik ekranu słyszy „Wczytywanie…”.</p>
      <ul class="samples">
        <li class="variant">
          <p class="sample-title">Wiersze listy</p>
          <div aria-busy="true"><LoadingState shape="rows" :count="3" /></div>
        </li>
        <li class="variant">
          <p class="sample-title">Linie karty</p>
          <div aria-busy="true"><LoadingState shape="lines" /></div>
        </li>
        <li class="variant">
          <p class="sample-title">Formularz</p>
          <div aria-busy="true"><LoadingState shape="form" /></div>
        </li>
      </ul>
    </section>

    <section aria-labelledby="empty-title">
      <h2 id="empty-title">Pusta lista</h2>
      <p>Pusty widok mówi, czego nie ma i dlaczego, i daje jeden następny krok.</p>
      <ul class="samples">
        <li class="variant">
          <p class="sample-title">Filtry niczego nie znalazły</p>
          <EmptyState icon="i-search" title="Brak postów dla tych filtrów">
            Zmień filtr albo wyczyść go, żeby zobaczyć wszystkie posty.
            <template #action><AppButton>Wyczyść filtry</AppButton></template>
          </EmptyState>
        </li>
        <li class="variant">
          <p class="sample-title">Brak danych</p>
          <EmptyState icon="i-inbox" title="Brak ocenionych postów">
            Pojawią się po najbliższej aktualizacji danych.
          </EmptyState>
        </li>
        <li class="variant">
          <p class="sample-title">Nic do zrobienia</p>
          <EmptyState icon="i-circle-check" title="Brak nieudanych postów">
            Żaden post nie czeka na ponowienie ani nie jest martwy.
          </EmptyState>
        </li>
      </ul>
    </section>

    <section aria-labelledby="error-state-title">
      <h2 id="error-state-title">Błąd z ponowieniem</h2>
      <p>Gdy widok się nie wczytał: co się stało i jeden przycisk, który próbuje jeszcze raz.</p>
      <ul class="samples">
        <li class="variant">
          <p class="sample-title">Domyślny</p>
          <ErrorState title="Nie udało się wczytać postów">Serwer nie odpowiedział. Twoje filtry zostały.</ErrorState>
        </li>
        <li class="variant">
          <p class="sample-title">Podczas ponawiania</p>
          <ErrorState title="Nie udało się wczytać postów" retrying>
            Serwer nie odpowiedział. Twoje filtry zostały.
          </ErrorState>
        </li>
      </ul>
    </section>

    <section aria-labelledby="badges-title">
      <h2 id="badges-title">Znacznik stanu</h2>
      <div class="row">
        <StatusBadge v-for="badge in badges" :key="badge.text" :tone="badge.tone">{{ badge.text }}</StatusBadge>
      </div>
      <div class="row">
        <span class="row-title">Z ikoną</span>
        <StatusBadge tone="warning" icon>zakończony — pominięte: 3</StatusBadge>
        <StatusBadge tone="destructive" icon>wymaga uwagi</StatusBadge>
      </div>
    </section>

    <section aria-labelledby="stances-title">
      <h2 id="stances-title">Stanowisko</h2>
      <ul class="samples">
        <li v-for="stance in stances" :key="stance.direction" class="row">
          <code class="row-title">{{ stance.direction }}</code>
          <StanceText :stance="stance" />
        </li>
        <li v-for="stance in hedgedStances" :key="`hedged-${stance.direction}`" class="row">
          <code class="row-title">{{ stance.direction }}, ostrożnie</code>
          <StanceText :stance="stance" />
        </li>
      </ul>
    </section>

    <section aria-labelledby="targets-title">
      <h2 id="targets-title">Cel ze stanowiskiem</h2>
      <div v-for="sample in targetStances" :key="sample.id" class="row">
        <span class="row-title">{{ sample.title }}</span>
        <StanceText
          v-for="item in sample.items"
          :key="item.target"
          :stance="item.stance"
          :target="item.target"
          :kind="item.kind"
        />
      </div>
      <div class="row">
        <span class="row-title">Za długa nazwa</span>
        <div class="narrow">
          <StanceText :stance="stances[0]" :target="longTarget" :kind="label('targetKind', 'company')" />
        </div>
      </div>
    </section>

    <section aria-labelledby="theme-title">
      <h2 id="theme-title">Motyw</h2>
      <div class="row">
        <ThemeToggle />
      </div>
    </section>

    <section aria-labelledby="icons-title">
      <h2 id="icons-title">Ikony</h2>
      <ul class="icons">
        <li v-for="name in ICON_NAMES" :key="name" class="icon-sample">
          <AppIcon :name="name" />
          <code>{{ name }}</code>
        </li>
      </ul>
    </section>

    <section aria-labelledby="bell-title">
      <h2 id="bell-title">Dzwoneczek</h2>
      <ul class="samples">
        <li v-for="bell in bells" :key="bell.id" class="bell-sample" :class="{ open: bellOpen[bell.id], list: bell.items.length > 0 }">
          <p class="sample-title">{{ bell.title }}</p>
          <div class="bell-bar">
            <NotificationMenu
              v-model:open="bellOpen[bell.id]"
              :state="bell.state"
              :unread="bell.unread"
              :items="bell.items"
              :now="BELL_NOW"
            />
          </div>
        </li>
      </ul>
    </section>

    <section aria-labelledby="filter-title">
      <h2 id="filter-title">Filtr</h2>
      <h3>Bez filtra</h3>
      <FeedFilter :filter="{}" />
      <h3>Aktywny filtr</h3>
      <FeedFilter :filter="activeFilter" />
    </section>

    <section aria-labelledby="post-row-title">
      <h2 id="post-row-title">Wiersz posta</h2>
      <p>Wiersze jednego dnia w jego ramce; wiersz posta otwartego w panelu jest wybrany (tu: post z Reddita).</p>
      <ol class="rows">
        <PostRow
          v-for="post in posts"
          :key="post.id"
          :post="post"
          :to="{ name: 'post', params: { id: post.id } }"
          :selected="post.id === 'reddit'"
        />
      </ol>
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

    <section aria-labelledby="brand-title">
      <h2 id="brand-title">Marka</h2>
      <p>Rozmiar nazwy zależy od szerokości okna (poniżej 900 px mniejszy); zrzut katalogu jest przy 375 px.</p>
      <div class="row">
        <AppBrand />
      </div>
    </section>

    <section aria-labelledby="app-bar-title">
      <h2 id="app-bar-title">Pasek aplikacji</h2>
      <ul class="samples">
        <li v-for="bar in bars" :key="bar.id" class="bar-sample" :class="{ open: bar.bell && barBellOpen }">
          <p class="sample-title">{{ bar.title }}</p>
          <div class="bar-frame">
            <AppBar :items="bar.items" :freshness="bar.freshness" :email="bar.email" :logging-out="false">
              <template v-if="bar.bell" #notifications>
                <NotificationMenu v-model:open="barBellOpen" state="loaded" :unread="2" :items="notifications" :now="BELL_NOW" />
              </template>
            </AppBar>
          </div>
        </li>
      </ul>
    </section>

    <section aria-labelledby="page-header-title">
      <h2 id="page-header-title">Nagłówek strony</h2>
      <ul class="samples">
        <li class="sample">
          <p class="sample-title">Sam tytuł</p>
          <PageHeader title="Posty" />
        </li>
        <li class="sample">
          <p class="sample-title">Z podtytułem</p>
          <PageHeader title="Przegląd dnia" subtitle="wtorek, 20 października 2026" />
        </li>
        <li class="sample">
          <p class="sample-title">Ze stanem przy tytule</p>
          <PageHeader title="Przebieg z 19.10.2026, 02:30">
            <template #status>
              <StatusBadge tone="warning">częściowo</StatusBadge>
            </template>
          </PageHeader>
        </li>
        <li class="sample">
          <p class="sample-title">Tytuł do czytania (post)</p>
          <PageHeader title="Orlen ogłasza buyback za 4 mld zł" size="reading" />
        </li>
        <li class="sample">
          <p class="sample-title">Z narzędziami widoku po prawej</p>
          <PageHeader title="Posty" subtitle="wtorek, 20 października 2026">
            <template #tools>
              <AppButton icon="i-chevron-left" icon-only aria-label="Poprzedni dzień" />
              <AppButton icon="i-chevron-right" icon-only aria-label="Następny dzień" />
            </template>
          </PageHeader>
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
  font-size: var(--font-size-2xl);
  color: var(--text-h);
}

h3 {
  margin: var(--space-4) 0 var(--space-2);
  font-size: var(--font-size-md);
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

/* A shared state or the pager in each variant: the sample's title above it, the component draws its own frame. */
.variant {
  margin-bottom: var(--space-4);
}

/* Each variant of the assessment framed like a card, so the variants stay apart. */
.sample {
  margin-bottom: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.sample-title {
  margin: 0 0 var(--space-3);
  color: var(--text-h);
  font-size: var(--font-size-sm);
  font-weight: var(--weight-semibold);
}

/* An open bell keeps room below its bar for the panel, which overlays the page. */
.bell-sample {
  margin-bottom: var(--space-4);
}

.bell-sample.open {
  min-height: 24rem;
}

/* Seven notifications wrap to about 41rem at 375 px. */
.bell-sample.open.list {
  min-height: 43rem;
}

/* A bar like the application's: positioned, so the panel opens below it, aligned to its right edge. */
.bell-bar {
  position: relative;
  display: flex;
  justify-content: flex-end;
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

/* The bar stands in a frame; the frame is not positioned, so the open bell's panel is placed by the bar's header. */
.bar-sample {
  margin-bottom: var(--space-4);
}

/* An open bell keeps room below the bar for the panel, which overlays the page. */
.bar-sample.open {
  min-height: 43rem;
}

.bar-frame {
  border: 1px solid var(--line);
  border-radius: var(--radius);
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
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-weight: var(--weight-semibold);
}

/* Fields as a view lays them out: the FormField component does the label, hint and error; the look is the global one. */
.fields {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

/* A field with a hint or an error below it takes the whole row, so the sentence does not wrap. */
.wide {
  grid-column: 1 / -1;
}

/* A search field with its icon inside, on the left. */
.input-wrap {
  position: relative;
  display: block;
}

.input-wrap .app-icon {
  --icon-size: 1.125rem;

  position: absolute;
  top: 50%;
  left: var(--space-3);
  color: var(--text-muted);
  transform: translateY(-50%);
  pointer-events: none;
}

.input-wrap input {
  width: 100%;
  padding-left: var(--space-7);
}

/* The large search above a list of posts. */
.input-lg .app-icon {
  --icon-size: 1.1875rem;

  left: var(--space-4);
}

.input-lg input {
  height: var(--control-height-lg);
  padding-left: var(--space-8);
  border-radius: var(--radius-lg);
  font-size: var(--font-size-lg);
}

.check {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-h);
  cursor: pointer;
}

.stack {
  display: grid;
  gap: var(--space-3);
}

/* A link to a page outside the app: the icon follows the text. */
.ext {
  --icon-size: 0.9375rem;

  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

/* The frame of a day's rows, as on the list of posts (FeedView). */
.rows {
  margin: 0;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  background: var(--surface);
  list-style: none;
}

/* A box narrower than the marker's content, to show the name being cut short. */
.narrow {
  display: flex;
  max-width: 14rem;
}

.icons {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(11rem, 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.icon-sample {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-h);
}
</style>
