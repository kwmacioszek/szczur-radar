package pl.szczurradar.api.posts;

import pl.szczurradar.api.sources.Source;

/**
 * One source's theses of successful model assessments over a language window ({@link AssessmentQuality#languageWindow}).
 *
 * @param source the posts' source
 * @param theses the theses counted
 * @param otherLanguage those among them with evidence of another language than their post's
 * @param thresholdPercent the source's threshold ({@link Source#otherLanguageThresholdPercent()})
 * @param status the window's state against the threshold
 */
public record SourceLanguage(Source source, long theses, long otherLanguage, int thresholdPercent,
		QualityStatus status) {
}
