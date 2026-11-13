package pl.szczurradar.api.posts;

import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.text.Language;

/**
 * One thesis ({@code claims[].statement}) of a successful assessment with a model call, with its post's source
 * and language: a row of {@link AssessmentRepository#findModelStatements}; {@link AssessmentQuality} classifies it.
 *
 * @param source the post's source
 * @param postLanguage the post's language ({@code posts.language})
 * @param statement the thesis as the model wrote it
 */
public record PostStatement(Source source, Language postLanguage, String statement) {
}
