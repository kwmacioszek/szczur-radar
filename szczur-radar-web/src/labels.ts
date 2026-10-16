// Polish labels for the API's closed value sets; an unknown value shows as is.
const labels: Record<string, Record<string, string>> = {
  itemType: { post: 'wpis', link: 'znalezisko', comment: 'komentarz' },
  relevance: {
    investment_related: 'inwestycyjny',
    not_investment_related: 'nie inwestycyjny',
    unclear: 'niejasny',
  },
  direction: {
    bullish: 'wzrostowo',
    bearish: 'spadkowo',
    neutral: 'neutralnie',
    mixed: 'mieszanie',
    not_expressed: 'bez stanowiska',
    unclear: 'niejasno',
  },
  flag: {
    missing_context: 'brak kontekstu',
    possible_sarcasm: 'możliwy sarkazm',
    insufficient_content: 'za mało treści',
    extraction_limit: 'limit ekstrakcji',
  },
  modifier: { humor: 'humor', promotion: 'promocja' },
  claimKind: {
    factual_claim: 'informacja',
    opinion: 'opinia',
    investment_thesis: 'teza inwestycyjna',
    question: 'pytanie',
    trade_action: 'działanie',
  },
  attribution: { author: 'autor', reported: 'przytoczone', unclear: 'nie wiadomo czyje' },
  targetKind: {
    company: 'spółka',
    asset: 'aktywo',
    sector: 'sektor',
    macro_topic: 'makro i rynek',
    other: 'inne',
    unknown: 'nieokreślony',
  },
  reference: {
    explicit: 'nazwany wprost',
    contextual: 'z kontekstu',
    ambiguous: 'niejednoznaczny',
    unresolved: 'nierozpoznany',
  },
  runKind: { range: 'zakres dat', retry: 'ponowienie resztek', posts: 'wybrane posty' },
  runStatus: {
    queued: 'w kolejce',
    running: 'trwa',
    succeeded: 'zakończony',
    partial: 'częściowo',
    failed: 'błąd',
    interrupted: 'przerwany',
  },
  interruptionCause: { deploy: 'nowe wdrożenie', restart: 'restart aplikacji', shutdown: 'zatrzymanie aplikacji' },
  stepStatus: { running: 'trwa', succeeded: 'zakończony', failed: 'błąd', interrupted: 'przerwany' },
  step: { fetch: 'pobranie', store: 'zapis', select: 'wybór', assess: 'ocena', summarize: 'podsumowanie' },
  failureClass: {
    provider_error: 'błąd dostawcy',
    timeout: 'przekroczony czas',
    invalid_structured_output: 'niepoprawna struktura odpowiedzi',
    schema_violation: 'niezgodność ze schematem',
    invalid_reference: 'błędne odwołanie',
    fabricated_evidence: 'zmyślony cytat',
    domain_invariant_violation: 'naruszenie reguł kontraktu',
  },
  source: { wykop: 'Wykop' },
}

export function label(set: keyof typeof labels, value: string): string {
  return labels[set]?.[value] ?? value
}

const dateTime = new Intl.DateTimeFormat('pl-PL', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'Europe/Warsaw',
})

/** An instant as Warsaw date and time, e.g. `1.10.2026, 09:15`. */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso))
}

const date = new Intl.DateTimeFormat('pl-PL', { dateStyle: 'short', timeZone: 'Europe/Warsaw' })

/** The Warsaw calendar day of an instant, e.g. `1.10.2026`. */
export function formatDate(iso: string): string {
  return date.format(new Date(iso))
}

const pluralRules = new Intl.PluralRules('pl-PL')

/** The Polish form for `count`: `one` (1), `few` (2–4, 22–24, …) or `many` (0, 5–21, …). */
export function plural(count: number, one: string, few: string, many: string): string {
  switch (pluralRules.select(count)) {
    case 'one':
      return one
    case 'few':
      return few
    default:
      return many
  }
}

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * How long ago `iso` was at `now` (ms since the epoch): `przed chwilą` under a minute (also for a time slightly
 * ahead of the browser's clock), `N min temu` under an hour, `N h temu` under a day, then the date and time.
 */
export function formatRelative(iso: string, now: number = Date.now()): string {
  const ago = now - new Date(iso).getTime()
  if (ago < MINUTE) return 'przed chwilą'
  if (ago < HOUR) return `${Math.floor(ago / MINUTE)} min temu`
  if (ago < DAY) return `${Math.floor(ago / HOUR)} h temu`
  return formatDateTime(iso)
}

/** A duration in ms, whole seconds down: `45 s`, `3 min 20 s`, `3 min`, `1 h 5 min`, `2 h`; below zero is `0 s`. */
export function formatDuration(ms: number): string {
  const seconds = Math.max(0, Math.floor(ms / 1000))
  if (seconds < 60) return `${seconds} s`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return seconds % 60 === 0 ? `${minutes} min` : `${minutes} min ${seconds % 60} s`
  const hours = Math.floor(minutes / 60)
  return minutes % 60 === 0 ? `${hours} h` : `${hours} h ${minutes % 60} min`
}
