package pl.szczurradar.api.posts.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.posts.AssessmentQuality;
import pl.szczurradar.api.posts.LanguageWindow;
import pl.szczurradar.api.posts.QualityStatus;
import pl.szczurradar.api.posts.QualityWindow;
import pl.szczurradar.api.posts.SourceLanguage;
import pl.szczurradar.api.posts.SourceQuality;
import pl.szczurradar.api.sources.Source;

/**
 * The admin's view of the assessments' quality (S-35, FR-033): the current window of
 * {@value AssessmentQuality#WINDOW_DAYS} Warsaw days up to today (from the injected clock), per source, the same
 * window the night's summary reports, with both measures: the failed attempts (S-35) and the theses in another
 * language than their post (S-36). Read only; the state against each threshold is the API's, the SPA only formats
 * the shares.
 *
 * <p>The attempts are read first; their failure is the route's 500. The theses are read after them, apart: any
 * exception there is an ERROR in the log and every source's {@code language} {@code null}, the attempts' measure
 * answered as usual. Neither read is in a transaction of the route's ({@code open-in-view} is off), so a failed
 * theses' statement cannot abort the attempts' read.
 */
@RestController
class AssessmentQualityController {

	private static final Logger log = LoggerFactory.getLogger(AssessmentQualityController.class);

	private final AssessmentQuality quality;

	private final Clock clock;

	AssessmentQualityController(AssessmentQuality quality, Clock clock) {
		this.quality = quality;
		this.clock = clock;
	}

	@GetMapping("/api/admin/assessment-quality")
	QualityView window() {
		LocalDate today = LocalDate.ofInstant(clock.instant(), PipelineRunService.ZONE);
		QualityWindow attempts = quality.window(today, PipelineRunService.ZONE);
		return QualityView.of(attempts, languages(today));
	}

	/** Each source's language measure of the window of {@code today}; {@code null} when the theses' read failed. */
	private @Nullable Map<Source, SourceLanguage> languages(LocalDate today) {
		try {
			LanguageWindow window = quality.languageWindow(today, PipelineRunService.ZONE);
			Map<Source, SourceLanguage> languages = new EnumMap<>(Source.class);
			window.sources().forEach(source -> languages.put(source.source(), source));
			return languages;
		}
		catch (RuntimeException ex) {
			log.error("The language window of {} could not be read; the assessment quality goes without it", today,
					ex);
			return null;
		}
	}

	/**
	 * The window {@code from}–{@code to} (both included) and its sources.
	 *
	 * @param windowDays how many days a window has ({@link AssessmentQuality#WINDOW_DAYS})
	 * @param minAttempts the fewest attempts for a source's threshold of failed attempts to be checked
	 *     ({@link AssessmentQuality#MIN_ATTEMPTS})
	 * @param minTheses the fewest theses for a source's threshold of theses in another language to be checked
	 *     ({@link AssessmentQuality#MIN_THESES})
	 * @param sources each source with at least one attempt in the window, in {@link Source} order; empty without
	 *     one
	 */
	record QualityView(LocalDate from, LocalDate to, int windowDays, int minAttempts, int minTheses,
			List<SourceQualityView> sources) {

		/**
		 * {@code window} with each source's language measure from {@code languages}; a source without a thesis gets
		 * none counted, below the minimum. {@code languages} is {@code null} when the theses could not be read.
		 */
		static QualityView of(QualityWindow window, @Nullable Map<Source, SourceLanguage> languages) {
			return new QualityView(window.from(), window.to(), AssessmentQuality.WINDOW_DAYS,
					AssessmentQuality.MIN_ATTEMPTS, AssessmentQuality.MIN_THESES,
					window.sources()
						.stream()
						.map(source -> SourceQualityView.of(source,
								languages == null ? null : LanguageView.of(source.source(), languages)))
						.toList());
		}

	}

	/**
	 * One source's attempts with a model call in the window.
	 *
	 * @param classes the failed attempts per failure class, only the classes with one, in {@link FailureClass} order
	 * @param thresholdPercent the source's threshold in percent ({@link Source#failureThresholdPercent()})
	 * @param language the source's theses in another language than their post; {@code null} only when the theses
	 *     could not be read
	 */
	record SourceQualityView(Source source, long attempts, long failed, List<ClassCountView> classes,
			int thresholdPercent, QualityStatus status, @Nullable LanguageView language) {

		static SourceQualityView of(SourceQuality quality, @Nullable LanguageView language) {
			return new SourceQualityView(quality.source(), quality.attempts(), quality.failed(),
					quality.classes()
						.entrySet()
						.stream()
						.map(entry -> new ClassCountView(entry.getKey(), entry.getValue()))
						.toList(),
					quality.thresholdPercent(), quality.status(), language);
		}

	}

	/**
	 * One source's theses of successful attempts with a model call in the window, as counts.
	 *
	 * @param otherLanguage the theses with evidence of another language than their post's
	 * @param thresholdPercent the source's threshold in percent ({@link Source#otherLanguageThresholdPercent()})
	 */
	record LanguageView(long theses, long otherLanguage, int thresholdPercent, QualityStatus status) {

		/** {@code source}'s row of {@code languages}, or no thesis at all ({@link QualityStatus#NOT_ENOUGH_DATA}). */
		static LanguageView of(Source source, Map<Source, SourceLanguage> languages) {
			SourceLanguage language = languages.get(source);
			if (language == null) {
				return new LanguageView(0, 0, source.otherLanguageThresholdPercent(), QualityStatus.NOT_ENOUGH_DATA);
			}
			return new LanguageView(language.theses(), language.otherLanguage(), language.thresholdPercent(),
					language.status());
		}

	}

	/** The failed attempts of one failure class. */
	record ClassCountView(FailureClass failureClass, long count) {
	}

}
