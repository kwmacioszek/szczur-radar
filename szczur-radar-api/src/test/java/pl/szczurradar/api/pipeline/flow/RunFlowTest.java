package pl.szczurradar.api.pipeline.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.pipeline.flow.RunFlow.RejectedPost;
import pl.szczurradar.api.pipeline.flow.RunFlow.RunRequest;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceFetch;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceStored;
import pl.szczurradar.api.pipeline.flow.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.sources.Source;

/**
 * The records {@link RunFlow} hands between the steps: what a stored run becomes for {@link RunSequence}, and the
 * checks of a source's result and of the store's counts. {@code RunSequenceTest} covers how the steps use them.
 */
class RunFlowTest {

	private static final Instant FROM = Instant.parse("2026-09-30T22:00:00Z");

	private static final Instant TO = Instant.parse("2026-10-01T21:59:59Z");

	@Test
	void aRunRequestIsTheStoredRunAsItIs() {
		PipelineRun range = PipelineRun.range(FROM, TO, List.of(Source.REDDIT, Source.WYKOP), UUID.randomUUID(), null);
		UUID rangeId = withId(range);
		List<UUID> postIds = List.of(UUID.randomUUID(), UUID.randomUUID());
		PipelineRun posts = PipelineRun.posts(postIds, UUID.randomUUID(), "deployment-7");
		UUID postsId = withId(posts);

		assertThat(RunRequest.of(range))
			.isEqualTo(new RunRequest(rangeId, RunKind.RANGE, FROM, TO, List.of(Source.REDDIT, Source.WYKOP), null));
		assertThat(RunRequest.of(posts)).isEqualTo(new RunRequest(postsId, RunKind.POSTS, null, null, List.of(), postIds));
	}

	@Test
	void aSourceHasAnErrorExactlyWhenItFailed() {
		assertThatThrownBy(() -> new SourceFetch(Source.REDDIT, SourceFetchStatus.FAILED, 0, 0, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("a failed source has an error");
		assertThatThrownBy(() -> new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 3, 0, "HTTP 503"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SourceFetch(Source.WYKOP, SourceFetchStatus.EMPTY, 0, 0, "HTTP 503"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatCode(() -> new SourceFetch(Source.REDDIT, SourceFetchStatus.FAILED, 0, 0, "HTTP 503"))
			.doesNotThrowAnyException();
		assertThatCode(() -> new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 3, 1, null))
			.doesNotThrowAnyException();
		assertThatCode(() -> new SourceFetch(Source.WYKOP, SourceFetchStatus.EMPTY, 2, 2, null))
			.doesNotThrowAnyException();
	}

	@Test
	void aSourcesStoreCountsAreRefusedWhenOneIsNegativeThoughTheyAddUp() {
		// Each sum holds (posts = new + duplicates + rejected): only the sign refuses them.
		assertThatThrownBy(() -> new SourceStored(Source.WYKOP, -1, -1, 0, 0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("negative counts of wykop");
		assertThatThrownBy(() -> new SourceStored(Source.WYKOP, 0, -1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SourceStored(Source.WYKOP, 0, 1, -1, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SourceStored(Source.WYKOP, 0, 1, 0, -1)).isInstanceOf(IllegalArgumentException.class);
		// A negative number of posts is refused for its sign, before its sum is checked.
		assertThatThrownBy(() -> new SourceStored(Source.WYKOP, -1, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("negative counts of wykop: posts -1");
	}

	@Test
	void aSourcesStoreCountsMayEachBeZero() {
		assertThatCode(() -> new SourceStored(Source.WYKOP, 0, 0, 0, 0)).doesNotThrowAnyException();
		assertThatCode(() -> new SourceStored(Source.WYKOP, 1, 1, 0, 0)).doesNotThrowAnyException();
		assertThatCode(() -> new SourceStored(Source.WYKOP, 1, 0, 1, 0)).doesNotThrowAnyException();
		assertThatCode(() -> new SourceStored(Source.WYKOP, 1, 0, 0, 1)).doesNotThrowAnyException();
	}

	@Test
	void exactlyTheListedNumberOfRejectedPostsAreNamedWithoutAMoreSuffix() {
		List<RejectedPost> rejected = IntStream.range(0, StoredBatch.REJECTED_LISTED)
			.mapToObj(i -> new RejectedPost(Source.WYKOP, ItemType.POST, String.valueOf(i), "ck_posts_channel"))
			.toList();
		StoredBatch batch = new StoredBatch(UUID.randomUUID(), List.of(),
				List.of(new SourceStored(Source.WYKOP, rejected.size(), 0, 0, rejected.size())), rejected);

		assertThat(batch.rejectedSummary()).hasValueSatisfying(summary -> assertThat(summary)
			.startsWith("store rejected 10 post(s), counted as skipped: wykop post 0 (ck_posts_channel); ")
			.endsWith("; wykop post 9 (ck_posts_channel)")
			.doesNotContain("more"));
	}

	private static UUID withId(PipelineRun run) {
		UUID id = UUID.randomUUID();
		ReflectionTestUtils.setField(run, "id", id);
		return id;
	}

}
