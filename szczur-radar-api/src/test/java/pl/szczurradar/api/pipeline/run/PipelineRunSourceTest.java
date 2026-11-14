package pl.szczurradar.api.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import pl.szczurradar.api.sources.Source;

/**
 * {@link PipelineRunSource}: a source's result reads back what it was given, and what only the database writes
 * (its id, its end), set as Hibernate's field access sets them.
 */
class PipelineRunSourceTest {

	@Test
	void aFailedSourceReadsBackItsCountsAndError() {
		UUID run = UUID.randomUUID();

		PipelineRunSource result = new PipelineRunSource(run, Source.REDDIT, SourceFetchStatus.FAILED, 7, 2,
				"IOException: connection reset");

		assertThat(result.getRunId()).isEqualTo(run);
		assertThat(result.getSource()).isEqualTo(Source.REDDIT);
		assertThat(result.getStatus()).isEqualTo(SourceFetchStatus.FAILED);
		assertThat(result.getFetched()).isEqualTo(7);
		assertThat(result.getSkipped()).isEqualTo(2);
		assertThat(result.getError()).isEqualTo("IOException: connection reset");
	}

	@Test
	void aSucceededSourceHasNoError() {
		PipelineRunSource result = new PipelineRunSource(UUID.randomUUID(), Source.WYKOP, SourceFetchStatus.SUCCEEDED,
				3, 0, null);

		assertThat(result.getError()).isNull();
	}

	@Test
	void aResultLoadedFromItsRowReadsBackItsIdAndEnd() {
		UUID id = UUID.randomUUID();
		Instant finished = Instant.parse("2026-10-02T00:31:12Z");
		PipelineRunSource result = new PipelineRunSource(UUID.randomUUID(), Source.WYKOP, SourceFetchStatus.EMPTY, 0,
				0, null);
		ReflectionTestUtils.setField(result, "id", id);
		ReflectionTestUtils.setField(result, "finishedAt", finished);

		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getFinishedAt()).isEqualTo(finished);
	}

}
