package pl.szczurradar.api.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pl.szczurradar.api.pipeline.SourceNotConfiguredException;
import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.RangeTarget;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceFetch;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.pipeline.state.RunInterruptedException;
import pl.szczurradar.api.sources.ScriptedSourceFetcher;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourceFetcher;
import pl.szczurradar.api.sources.SourcePost;

/**
 * {@link SourceIngest}: which sources a range run fetches, and the fetch over them — each source isolated
 * from the others, its result reported as soon as it ended, and the step failed only when no source gave
 * a post.
 */
@ExtendWith(OutputCaptureExtension.class)
class SourceIngestTest {

	private static final Instant FROM = Instant.parse("2026-09-30T22:00:00Z");

	private static final Instant TO = Instant.parse("2026-10-01T21:59:59Z");

	private static final UUID RUN = UUID.randomUUID();

	private final ScriptedSourceFetcher wykop = new ScriptedSourceFetcher(Source.WYKOP).configured(true);

	private final ScriptedSourceFetcher reddit = new ScriptedSourceFetcher(Source.REDDIT).configured(true);

	/** The results the step reported, in order. */
	private final List<SourceFetch> recorded = new ArrayList<>();

	@AfterEach
	void clearInterrupt() {
		// A test that interrupts its own thread must not leave the flag set for the next one.
		Thread.interrupted();
	}

	@Test
	void noRequestedSourcesMeansEveryConfiguredSource() {
		reddit.configured(false);

		assertThat(ingest().resolve(List.of())).containsExactly(Source.WYKOP);
		reddit.configured(true);
		assertThat(ingest().resolve(List.of())).containsExactly(Source.WYKOP, Source.REDDIT);
	}

	@Test
	void requestedSourcesAreUsedOnceEachInSourceOrder() {
		assertThat(ingest().resolve(List.of(Source.WYKOP, Source.WYKOP))).containsExactly(Source.WYKOP);
		assertThat(ingest().resolve(List.of(Source.REDDIT, Source.WYKOP))).containsExactly(Source.WYKOP, Source.REDDIT);
	}

	@Test
	void aRequestedSourceThatIsNotConfiguredIsRefused() {
		wykop.configured(false);

		assertThatExceptionOfType(SourceNotConfiguredException.class)
			.isThrownBy(() -> new SourceIngest(List.of(wykop)).resolve(List.of(Source.WYKOP)))
			.satisfies(ex -> assertThat(ex.sources()).containsExactly(Source.WYKOP));
		assertThatExceptionOfType(SourceNotConfiguredException.class)
			.isThrownBy(() -> new SourceIngest(List.of()).resolve(List.of(Source.WYKOP)));
	}

	@Test
	void withoutAnyConfiguredSourceNothingCanBeFetched() {
		wykop.configured(false);

		assertThatExceptionOfType(SourceNotConfiguredException.class)
			.isThrownBy(() -> new SourceIngest(List.of(wykop)).resolve(List.of()))
			.satisfies(ex -> assertThat(ex.sources()).isEmpty())
			.withMessage("no source is configured");
	}

	@Test
	void theFetchAddsUpTheNamedSources() {
		wykop.answer(new SourceFetcher.Fetched(List.of(wykop.post("1")), 3, 2));

		FetchedBatch batch = new SourceIngest(List.of(wykop)).fetch(target(Source.WYKOP), recorded::add);

		assertThat(batch.runId()).isEqualTo(RUN);
		assertThat(batch.posts()).extracting(SourcePost::sourceItemId).containsExactly("1");
		assertThat(batch.fetched()).isEqualTo(3);
		assertThat(batch.skipped()).isEqualTo(2);
		assertThat(batch.sources())
			.containsExactly(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 3, 2, null));
		assertThat(batch.degraded()).isFalse();
		assertThat(batch.summary()).isEmpty();
		assertThat(recorded).isEqualTo(batch.sources());
		assertThat(wykop.lastRange()).isEqualTo(new ScriptedSourceFetcher.Range(FROM, TO));
	}

	@Test
	void aSourceWithoutAnyElementFailsTheFetch() {
		wykop.answer(new SourceFetcher.Fetched(List.of(), 0, 0));

		assertThatExceptionOfType(NoPostsFetchedException.class)
			.isThrownBy(() -> new SourceIngest(List.of(wykop)).fetch(target(Source.WYKOP), recorded::add))
			.withMessage("wykop gave no post to store (fetched 0, skipped 0)")
			.satisfies(ex -> {
				assertThat(ex.sources()).containsExactly(Source.WYKOP);
				assertThat(ex.fetched()).isZero();
				assertThat(ex.skipped()).isZero();
			});
		assertThat(recorded).containsExactly(new SourceFetch(Source.WYKOP, SourceFetchStatus.EMPTY, 0, 0, null));
	}

	@Test
	void aSourceWhoseElementsWereAllSkippedFailsTheFetch() {
		wykop.answerEmpty(5);

		assertThatExceptionOfType(NoPostsFetchedException.class)
			.isThrownBy(() -> new SourceIngest(List.of(wykop)).fetch(target(Source.WYKOP), recorded::add))
			.withMessage("wykop gave no post to store (fetched 5, skipped 5)")
			.satisfies(ex -> {
				assertThat(ex.sources()).containsExactly(Source.WYKOP);
				assertThat(ex.fetched()).isEqualTo(5);
				assertThat(ex.skipped()).isEqualTo(5);
			});
	}

	@Test
	void aSourceWithSkippedElementsAndAPostGivesItsBatch(CapturedOutput output) {
		wykop.answer(new SourceFetcher.Fetched(List.of(wykop.post("7")), 10, 9));

		FetchedBatch batch = new SourceIngest(List.of(wykop)).fetch(target(Source.WYKOP), recorded::add);

		assertThat(batch.posts()).extracting(SourcePost::sourceItemId).containsExactly("7");
		assertThat(batch.fetched()).isEqualTo(10);
		assertThat(batch.skipped()).isEqualTo(9);
		assertThat(output.getAll()).contains("Run " + RUN + ": wykop skipped 9 of 10 elements");
	}

	@Test
	void twoSourcesWithPostsGiveOneBatchAndAreReportedInSourceOrder() {
		List<SourcePost> wykopPosts = wykop.posts(2);
		List<SourcePost> redditPosts = reddit.posts(1);
		wykop.answer(new SourceFetcher.Fetched(wykopPosts, 3, 1));
		reddit.answer(new SourceFetcher.Fetched(redditPosts, 1, 0));

		FetchedBatch batch = ingest().fetch(target(Source.WYKOP, Source.REDDIT), recorded::add);

		assertThat(batch.posts()).containsExactly(wykopPosts.get(0), wykopPosts.get(1), redditPosts.get(0));
		assertThat(batch.fetched()).isEqualTo(4);
		assertThat(batch.skipped()).isEqualTo(1);
		assertThat(recorded).containsExactly(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 3, 1, null),
				new SourceFetch(Source.REDDIT, SourceFetchStatus.SUCCEEDED, 1, 0, null));
		assertThat(batch.sources()).isEqualTo(recorded);
		assertThat(batch.degraded()).isFalse();
	}

	@Test
	void anEmptySourceLeavesTheOtherSourcesPostsWithItsOwnResult() {
		wykop.answerEmpty(4);
		List<SourcePost> redditPosts = reddit.posts(2);
		reddit.answer(new SourceFetcher.Fetched(redditPosts, 2, 0));

		FetchedBatch batch = ingest().fetch(target(Source.WYKOP, Source.REDDIT), recorded::add);

		assertThat(batch.posts()).isEqualTo(redditPosts);
		// The sums of both sources.
		assertThat(batch.fetched()).isEqualTo(6);
		assertThat(batch.skipped()).isEqualTo(4);
		assertThat(recorded).containsExactly(new SourceFetch(Source.WYKOP, SourceFetchStatus.EMPTY, 4, 4, null),
				new SourceFetch(Source.REDDIT, SourceFetchStatus.SUCCEEDED, 2, 0, null));
		assertThat(batch.degraded()).isTrue();
		assertThat(batch.summary()).isEqualTo("wykop gave no post to store (fetched 4, skipped 4)");
	}

	@Test
	void aSourceThatThrowsLeavesTheOtherSourcesPostsAndIsAWarning(CapturedOutput output) {
		List<SourcePost> wykopPosts = wykop.posts(1);
		wykop.answer(new SourceFetcher.Fetched(wykopPosts, 1, 0));
		reddit.fail(new IllegalStateException("HTTP 503"));

		FetchedBatch batch = ingest().fetch(target(Source.WYKOP, Source.REDDIT), recorded::add);

		assertThat(batch.posts()).isEqualTo(wykopPosts);
		assertThat(recorded).containsExactly(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 1, 0, null),
				new SourceFetch(Source.REDDIT, SourceFetchStatus.FAILED, 0, 0, "IllegalStateException: HTTP 503"));
		assertThat(batch.summary()).isEqualTo("reddit failed: IllegalStateException: HTTP 503");
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN "))).singleElement()
			.satisfies(line -> assertThat(line).contains("Run " + RUN + ": reddit failed: HTTP 503"));
		// With the exception.
		assertThat(output.getAll()).contains("java.lang.IllegalStateException: HTTP 503");
	}

	@Test
	void anEmptySourceAndOneThatThrowsFailTheFetchNamingBothAfterReportingBoth() {
		wykop.answerEmpty(0);
		reddit.fail(new IllegalStateException("HTTP 503"));

		assertThatExceptionOfType(NoPostsFetchedException.class)
			.isThrownBy(() -> ingest().fetch(target(Source.WYKOP, Source.REDDIT), recorded::add))
			.withMessage("wykop gave no post to store (fetched 0, skipped 0);"
					+ " reddit failed: IllegalStateException: HTTP 503")
			.satisfies(ex -> assertThat(ex.sources()).containsExactly(Source.WYKOP, Source.REDDIT));
		assertThat(recorded).extracting(SourceFetch::status)
			.containsExactly(SourceFetchStatus.EMPTY, SourceFetchStatus.FAILED);
	}

	@Test
	void aSourceNotConfiguredAnyMoreWhenFetchedIsItsFailure() {
		List<SourcePost> wykopPosts = wykop.posts(1);
		wykop.answer(new SourceFetcher.Fetched(wykopPosts, 1, 0));
		SourceIngest ingest = ingest();
		reddit.configured(false);

		FetchedBatch batch = ingest.fetch(target(Source.WYKOP, Source.REDDIT), recorded::add);

		assertThat(batch.posts()).isEqualTo(wykopPosts);
		assertThat(recorded.getLast()).isEqualTo(new SourceFetch(Source.REDDIT, SourceFetchStatus.FAILED, 0, 0,
				"SourceNotConfiguredException: not configured: reddit"));
		assertThat(reddit.calls()).isZero();
	}

	@Test
	void anInterruptBeforeTheNextSourceEndsTheStepWithoutFetchingIt() {
		List<SourcePost> wykopPosts = wykop.posts(1);
		wykop.answer(new SourceFetcher.Fetched(wykopPosts, 1, 0));
		reddit.answerPosts(1);

		// The run is stopped while the first source answers (its client keeps the flag).
		assertThatExceptionOfType(RunInterruptedException.class)
			.isThrownBy(() -> ingest().fetch(target(Source.WYKOP, Source.REDDIT), outcome -> {
				recorded.add(outcome);
				Thread.currentThread().interrupt();
			}));

		assertThat(recorded).containsExactly(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 1, 0, null));
		assertThat(reddit.calls()).isZero();
		assertThat(Thread.currentThread().isInterrupted()).as("the flag is kept").isTrue();
	}

	private SourceIngest ingest() {
		return new SourceIngest(List.of(wykop, reddit));
	}

	private static RangeTarget target(Source... sources) {
		return new RangeTarget(RUN, FROM, TO, List.of(sources));
	}

}
