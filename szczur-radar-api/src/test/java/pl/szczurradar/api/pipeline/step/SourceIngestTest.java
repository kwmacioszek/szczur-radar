package pl.szczurradar.api.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.pipeline.SourceNotConfiguredException;
import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.RangeTarget;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourceFetcher;
import pl.szczurradar.api.sources.SourcePost;

/** {@link SourceIngest}: which sources a range run fetches, and the fetch over them. */
class SourceIngestTest {

	private static final Instant FROM = Instant.parse("2026-09-30T22:00:00Z");

	private static final Instant TO = Instant.parse("2026-10-01T21:59:59Z");

	/** One post of three elements, two of them skipped. */
	private static final SourceFetcher.Fetched ONE_POST = new SourceFetcher.Fetched(List.of(post("1")), 3, 2);

	@Test
	void noRequestedSourcesMeansEveryConfiguredSource() {
		assertThat(new SourceIngest(List.of(fetcher(true, ONE_POST))).resolve(List.of())).containsExactly(Source.WYKOP);
	}

	@Test
	void requestedSourcesAreUsedOnceEach() {
		assertThat(new SourceIngest(List.of(fetcher(true, ONE_POST))).resolve(List.of(Source.WYKOP, Source.WYKOP)))
			.containsExactly(Source.WYKOP);
	}

	@Test
	void aRequestedSourceThatIsNotConfiguredIsRefused() {
		assertThatExceptionOfType(SourceNotConfiguredException.class)
			.isThrownBy(() -> new SourceIngest(List.of(fetcher(false, ONE_POST))).resolve(List.of(Source.WYKOP)))
			.satisfies(ex -> assertThat(ex.sources()).containsExactly(Source.WYKOP));
		assertThatExceptionOfType(SourceNotConfiguredException.class)
			.isThrownBy(() -> new SourceIngest(List.of()).resolve(List.of(Source.WYKOP)));
	}

	@Test
	void withoutAnyConfiguredSourceNothingCanBeFetched() {
		assertThatExceptionOfType(SourceNotConfiguredException.class)
			.isThrownBy(() -> new SourceIngest(List.of(fetcher(false, ONE_POST))).resolve(List.of()))
			.satisfies(ex -> assertThat(ex.sources()).isEmpty())
			.withMessage("no source is configured");
	}

	@Test
	void theFetchAddsUpTheNamedSources() {
		UUID run = UUID.randomUUID();

		FetchedBatch batch = new SourceIngest(List.of(fetcher(true, ONE_POST)))
			.fetch(new RangeTarget(run, FROM, TO, List.of(Source.WYKOP)));

		assertThat(batch.runId()).isEqualTo(run);
		assertThat(batch.posts()).extracting(SourcePost::sourceItemId).containsExactly("1");
		assertThat(batch.fetched()).isEqualTo(3);
		assertThat(batch.skipped()).isEqualTo(2);
	}

	@Test
	void aSourceWithoutAnyElementFailsTheFetch() {
		UUID run = UUID.randomUUID();
		SourceIngest ingest = new SourceIngest(List.of(fetcher(true, new SourceFetcher.Fetched(List.of(), 0, 0))));

		assertThatExceptionOfType(NoPostsFetchedException.class)
			.isThrownBy(() -> ingest.fetch(new RangeTarget(run, FROM, TO, List.of(Source.WYKOP))))
			.withMessage("wykop gave no post to store (fetched 0, skipped 0)")
			.satisfies(ex -> {
				assertThat(ex.sources()).containsExactly(Source.WYKOP);
				assertThat(ex.fetched()).isZero();
				assertThat(ex.skipped()).isZero();
			});
	}

	@Test
	void aSourceWhoseElementsWereAllSkippedFailsTheFetch() {
		UUID run = UUID.randomUUID();
		SourceIngest ingest = new SourceIngest(List.of(fetcher(true, new SourceFetcher.Fetched(List.of(), 5, 5))));

		assertThatExceptionOfType(NoPostsFetchedException.class)
			.isThrownBy(() -> ingest.fetch(new RangeTarget(run, FROM, TO, List.of(Source.WYKOP))))
			.withMessage("wykop gave no post to store (fetched 5, skipped 5)")
			.satisfies(ex -> {
				assertThat(ex.sources()).containsExactly(Source.WYKOP);
				assertThat(ex.fetched()).isEqualTo(5);
				assertThat(ex.skipped()).isEqualTo(5);
			});
	}

	@Test
	void aSourceWithSkippedElementsAndAPostGivesItsBatch() {
		UUID run = UUID.randomUUID();
		SourceIngest ingest = new SourceIngest(
				List.of(fetcher(true, new SourceFetcher.Fetched(List.of(post("7")), 10, 9))));

		FetchedBatch batch = ingest.fetch(new RangeTarget(run, FROM, TO, List.of(Source.WYKOP)));

		assertThat(batch.posts()).extracting(SourcePost::sourceItemId).containsExactly("7");
		assertThat(batch.fetched()).isEqualTo(10);
		assertThat(batch.skipped()).isEqualTo(9);
	}

	private static SourcePost post(String id) {
		return new SourcePost(Source.WYKOP, ItemType.POST, id, FROM, null, "treść", null, null, null, List.of(),
				new byte[0]);
	}

	/** Wykop's fetcher answering {@code fetched} for the test's range. */
	private static SourceFetcher fetcher(boolean configured, SourceFetcher.Fetched fetched) {
		return new SourceFetcher() {

			@Override
			public Source source() {
				return Source.WYKOP;
			}

			@Override
			public boolean configured() {
				return configured;
			}

			@Override
			public Fetched fetch(Instant from, Instant to) {
				assertThat(from).isEqualTo(FROM);
				assertThat(to).isEqualTo(TO);
				return fetched;
			}

		};
	}

}
