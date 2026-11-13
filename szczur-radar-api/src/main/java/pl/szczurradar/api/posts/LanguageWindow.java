package pl.szczurradar.api.posts;

import java.time.LocalDate;
import java.util.List;

/**
 * The language of the theses over {@value AssessmentQuality#WINDOW_DAYS} calendar days
 * ({@link AssessmentQuality#languageWindow}).
 *
 * @param from the window's first day
 * @param to the window's last day
 * @param sources each source with at least one thesis in the window, in {@link pl.szczurradar.api.sources.Source}
 *     order
 */
public record LanguageWindow(LocalDate from, LocalDate to, List<SourceLanguage> sources) {

	public LanguageWindow {
		sources = List.copyOf(sources);
	}

}
