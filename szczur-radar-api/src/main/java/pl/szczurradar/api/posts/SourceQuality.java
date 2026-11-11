package pl.szczurradar.api.posts;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.sources.Source;

/**
 * One source's assessment attempts with a model call over a quality window ({@link AssessmentQuality}).
 *
 * @param source the posts' source
 * @param attempts the attempts with a model call, retries included, the prefilter's left out
 * @param failed the failed ones among them
 * @param classes the failed ones per failure class: only the classes with an attempt, in {@link FailureClass}
 *     order
 * @param thresholdPercent the source's threshold ({@link Source#failureThresholdPercent()})
 * @param status the window's state against the threshold
 */
public record SourceQuality(Source source, long attempts, long failed, Map<FailureClass, Long> classes,
		int thresholdPercent, QualityStatus status) {

	public SourceQuality {
		Map<FailureClass, Long> ordered = new EnumMap<>(FailureClass.class);
		ordered.putAll(classes);
		classes = Collections.unmodifiableMap(ordered);
	}

}
