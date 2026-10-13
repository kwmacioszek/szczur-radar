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
