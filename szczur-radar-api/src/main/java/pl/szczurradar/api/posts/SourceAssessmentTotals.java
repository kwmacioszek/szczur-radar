package pl.szczurradar.api.posts;

import pl.szczurradar.api.sources.Source;

/**
 * The assessments of one source's posts in a set of runs, summed ({@link AssessmentRepository#sumBySource}):
 * how many ended {@code ok} and {@code failed}, and their tokens (a missing count — the prefilter makes
 * no call — counts as 0). The night's summary reads them ({@code pipeline/NightSummary}).
 *
 * @param source the posts' source
 * @param ok the {@code ok} assessments
 * @param failed the {@code failed} assessments
 * @param inputTokens the sum of {@code input_tokens}
 * @param cachedTokens the sum of {@code cached_tokens}
 * @param outputTokens the sum of {@code output_tokens}
 */
public record SourceAssessmentTotals(Source source, Long ok, Long failed, Long inputTokens, Long cachedTokens,
		Long outputTokens) {
}
