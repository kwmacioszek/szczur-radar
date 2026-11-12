package pl.szczurradar.api.posts.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.posts.AssessmentQuality;
import pl.szczurradar.api.posts.QualityStatus;
import pl.szczurradar.api.posts.QualityWindow;
import pl.szczurradar.api.posts.SourceQuality;
import pl.szczurradar.api.sources.Source;

/**
 * The admin's view of the assessments' quality (S-35, FR-033): the current window of
 * {@value AssessmentQuality#WINDOW_DAYS} Warsaw days up to today (from the injected clock), per source, the same
 * window the night's summary reports. Read only; the state against the threshold is the API's, the SPA only
 * formats the shares.
 */
@RestController
class AssessmentQualityController {

	private final AssessmentQuality quality;

	private final Clock clock;

	AssessmentQualityController(AssessmentQuality quality, Clock clock) {
		this.quality = quality;
		this.clock = clock;
	}

	@GetMapping("/api/admin/assessment-quality")
	QualityView window() {
		LocalDate today = LocalDate.ofInstant(clock.instant(), PipelineRunService.ZONE);
		return QualityView.of(quality.window(today, PipelineRunService.ZONE));
	}

	/**
	 * The window {@code from}–{@code to} (both included) and its sources.
	 *
	 * @param windowDays how many days a window has ({@link AssessmentQuality#WINDOW_DAYS})
	 * @param minAttempts the fewest attempts for a source's threshold to be checked
	 *     ({@link AssessmentQuality#MIN_ATTEMPTS})
	 * @param sources each source with at least one attempt in the window, in {@link Source} order; empty without
	 *     one
	 */
	record QualityView(LocalDate from, LocalDate to, int windowDays, int minAttempts, List<SourceQualityView> sources) {

		static QualityView of(QualityWindow window) {
			return new QualityView(window.from(), window.to(), AssessmentQuality.WINDOW_DAYS,
					AssessmentQuality.MIN_ATTEMPTS, window.sources().stream().map(SourceQualityView::of).toList());
		}

	}

	/**
	 * One source's attempts with a model call in the window.
	 *
	 * @param classes the failed attempts per failure class, only the classes with one, in {@link FailureClass} order
	 * @param thresholdPercent the source's threshold in percent ({@link Source#failureThresholdPercent()})
	 */
	record SourceQualityView(Source source, long attempts, long failed, List<ClassCountView> classes,
			int thresholdPercent, QualityStatus status) {

		static SourceQualityView of(SourceQuality quality) {
			return new SourceQualityView(quality.source(), quality.attempts(), quality.failed(),
					quality.classes()
						.entrySet()
						.stream()
						.map(entry -> new ClassCountView(entry.getKey(), entry.getValue()))
						.toList(),
					quality.thresholdPercent(), quality.status());
		}

	}

	/** The failed attempts of one failure class. */
	record ClassCountView(FailureClass failureClass, long count) {
	}

}
