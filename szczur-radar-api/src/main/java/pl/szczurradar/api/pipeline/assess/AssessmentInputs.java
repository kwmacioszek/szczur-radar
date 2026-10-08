package pl.szczurradar.api.pipeline.assess;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.RawInputMapper;
import pl.szczurradar.api.sources.Source;

/**
 * The assessment input of a stored post, rebuilt from its raw payload by its source's
 * {@link RawInputMapper} — the same for a new and a retried post.
 */
@Component
public class AssessmentInputs {

	private final RawPayloadStore rawPayloads;

	private final Map<Source, RawInputMapper> mappers;

	public AssessmentInputs(RawPayloadStore rawPayloads, List<RawInputMapper> mappers) {
		this.rawPayloads = rawPayloads;
		this.mappers = mappers.stream().collect(Collectors.toUnmodifiableMap(RawInputMapper::source, Function.identity()));
	}

	public AssessmentInputV1 of(Post post) {
		RawInputMapper mapper = mappers.get(post.getSource());
		if (mapper == null) {
			throw new IllegalStateException("no RawInputMapper bean for source " + post.getSource().value());
		}
		return mapper.map(rawPayloads.load(post.getRawPayload()));
	}

}
