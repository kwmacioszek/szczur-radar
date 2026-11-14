package pl.szczurradar.api.pipeline.assess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.RawInputMapper;
import pl.szczurradar.api.sources.Source;

/** {@link AssessmentInputs}: a post's input from its raw payload by its own source's mapper, and no other. */
class AssessmentInputsTest {

	private final RawPayloadStore rawPayloads = mock(RawPayloadStore.class);

	@Test
	void aPostsInputIsItsRawPayloadMappedByItsSourcesMapper() {
		Post post = post(Source.WYKOP);
		when(rawPayloads.load(post.getRawPayload())).thenReturn("the raw entry".getBytes(StandardCharsets.UTF_8));
		AssessmentInputs inputs = new AssessmentInputs(rawPayloads, List.of(mapper(Source.REDDIT), mapper(Source.WYKOP)));

		AssessmentInputV1 input = inputs.of(post);

		assertThat(input.item().body()).isEqualTo("wykop: the raw entry");
	}

	@Test
	void aPostWhoseSourceHasNoMapperIsRefusedNamingTheSource() {
		AssessmentInputs inputs = new AssessmentInputs(rawPayloads, List.of(mapper(Source.WYKOP)));

		// Its message is the post's technical error the admin reads on the failed list.
		assertThatThrownBy(() -> inputs.of(post(Source.REDDIT))).isInstanceOf(IllegalStateException.class)
			.hasMessage("no RawInputMapper bean for source reddit");
	}

	private static Post post(Source source) {
		return new Post(source, ItemType.POST, "1", "gielda", Instant.parse("2026-10-01T10:00:00Z"), null, "body", null,
				null, null, List.of(), new RawPayloadRef(UUID.randomUUID()), UUID.randomUUID());
	}

	/** A mapper of {@code source} whose input's body names the source and the raw payload. */
	private static RawInputMapper mapper(Source source) {
		return new RawInputMapper() {

			@Override
			public Source source() {
				return source;
			}

			@Override
			public AssessmentInputV1 map(byte[] raw) {
				return ContractFixtures.post(source.value() + ": " + new String(raw, StandardCharsets.UTF_8));
			}

		};
	}

}
