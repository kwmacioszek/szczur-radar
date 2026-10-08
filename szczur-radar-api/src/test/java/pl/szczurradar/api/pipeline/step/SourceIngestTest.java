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

	@Test
	void noRequestedSourcesMeansEveryConfiguredSource() {
		assertThat(new SourceIngest(List.of(fetcher(true))).resolve(List.of())).containsExactly(Source.WYKOP);
	}

	@Test
	void requestedSourcesAreUsedOnceEach() {
		assertThat(new SourceIngest(List.of(fetcher(true))).resolve(List.of(Source.WYKOP, Source.WYKOP)))
			.containsExactly(Source.WYKOP);
	}

	@Test
	void aRequestedSourceThatIsNotConfiguredIsRefused() {
		assertThatExceptionOfType(SourceNotConfiguredException.class)
			.isThrownBy(() -> new SourceIngest(List.of(fetcher(false))).resolve(List.of(Source.WYKOP)))
			.satisfies(ex -> assertThat(ex.sources()).containsExactly(Source.WYKOP));
		assertThatExceptionOfType(SourceNotConfiguredException.class)
			.isThrownBy(() -> new SourceIngest(List.of()).resolve(List.of(Source.WYKOP)));
	}

	@Test
	void withoutAnyConfiguredSourceNothingCanBeFetched() {
		assertThatExceptionOfType(SourceNotConfiguredException.class)
			.isThrownBy(() -> new SourceIngest(List.of(fetcher(false))).resolve(List.of()))
			.satisfies(ex -> assertThat(ex.sources()).isEmpty())
			.withMessage("no source is configured");
	}

	@Test
	void theFetchAddsUpTheNamedSources() {
		UUID run = UUID.randomUUID();

		FetchedBatch batch = new SourceIngest(List.of(fetcher(true)))
			.fetch(new RangeTarget(run, FROM, TO, List.of(Source.WYKOP)));

		assertThat(batch.runId()).isEqualTo(run);
		assertThat(batch.posts()).extracting(SourcePost::sourceItemId).containsExactly("1");
		assertThat(batch.fetched()).isEqualTo(3);
		assertThat(batch.skipped()).isEqualTo(2);
	}

	private static SourceFetcher fetcher(boolean configured) {
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
				return new Fetched(List.of(new SourcePost(Source.WYKOP, ItemType.POST, "1", FROM, null, "treść", null, null,
						null, List.of(), new byte[0])), 3, 2);
			}

		};
	}

}
