package pl.szczurradar.api.posts;

import java.time.LocalDate;
import java.util.List;

/**
 * The quality of the assessments over {@value AssessmentQuality#WINDOW_DAYS} calendar days
 * ({@link AssessmentQuality#window}).
 *
 * @param from the window's first day
 * @param to the window's last day
 * @param sources each source with at least one attempt in the window, in {@link pl.szczurradar.api.sources.Source}
 *     order
 */
public record QualityWindow(LocalDate from, LocalDate to, List<SourceQuality> sources) {

	public QualityWindow {
		sources = List.copyOf(sources);
	}

}
