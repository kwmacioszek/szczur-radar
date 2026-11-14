package pl.szczurradar.api.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.SystemAccount;

/**
 * The default methods of {@link PipelineRunRepository}: the rule each one adds to its query (the statuses, the
 * kind, the source's result, the scheduler's account, the order), on a mock whose queries answer only the
 * arguments the rule names (anything else gets the mock's empty answer). The queries themselves run on a real
 * database in the ITs ({@code PipelineRunIT}, {@code MissingDaysIT}, {@code FreshnessIT}, {@code NightSummaryIT}).
 */
class PipelineRunRepositoryTest {

	private static final Instant SINCE = Instant.parse("2026-09-20T22:00:00Z");

	private final PipelineRunRepository runs = mock(PipelineRunRepository.class);

	@Test
	void aRunIsActiveWhileOneIsQueuedOrRunning() {
		when(runs.existsActive()).thenCallRealMethod();
		when(runs.existsByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING))).thenReturn(true);

		assertThat(runs.existsActive()).isTrue();

		when(runs.existsByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING))).thenReturn(false);
		assertThat(runs.existsActive()).isFalse();
	}

	@Test
	void aRunIsRunningOnlyWithTheRunningStatus() {
		when(runs.existsRunning()).thenCallRealMethod();
		when(runs.existsByStatusIn(List.of(RunStatus.RUNNING))).thenReturn(true);

		assertThat(runs.existsRunning()).isTrue();

		when(runs.existsByStatusIn(List.of(RunStatus.RUNNING))).thenReturn(false);
		assertThat(runs.existsRunning()).isFalse();
	}

	@Test
	void aLiveActiveRunIsAskedForWithTheStoredStatusesAndTheThresholdInMilliseconds() {
		when(runs.existsLiveActive(any())).thenCallRealMethod();
		when(runs.existsWithActivityWithin(List.of("queued", "running"), 2_340_000L)).thenReturn(true);

		assertThat(runs.existsLiveActive(Duration.ofMinutes(39))).isTrue();
		assertThat(runs.existsLiveActive(Duration.ofMinutes(38))).as("another threshold").isFalse();

		when(runs.existsWithActivityWithin(any(), anyLong())).thenReturn(false);
		assertThat(runs.existsLiveActive(Duration.ofMinutes(39))).isFalse();
	}

	@Test
	void aPageIsNewestFirstWithTheIdBreakingTies() {
		Page<PipelineRun> page = new PageImpl<>(List.of(PipelineRun.retry(UUID.randomUUID(), null)));
		when(runs.findPage(2, 25)).thenCallRealMethod();
		when(runs.findAll(PageRequest.of(2, 25, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))))
			.thenReturn(page);

		assertThat(runs.findPage(2, 25)).isSameAs(page);
	}

	@Test
	void eachSourcesLastUpdateIsItsNewestSucceededRangeRunsEndAndASourceWithoutOneIsMissing() {
		Instant wykop = Instant.parse("2026-10-02T00:41:00Z");
		when(runs.findLastUpdates()).thenCallRealMethod();
		when(runs.findNewestStoredRangeEnd(RunKind.RANGE, List.of(RunStatus.SUCCEEDED, RunStatus.PARTIAL),
				SourceFetchStatus.SUCCEEDED, Source.WYKOP)).thenReturn(Optional.of(wykop));
		when(runs.findNewestStoredRangeEnd(RunKind.RANGE, List.of(RunStatus.SUCCEEDED, RunStatus.PARTIAL),
				SourceFetchStatus.SUCCEEDED, Source.REDDIT)).thenReturn(Optional.empty());

		assertThat(runs.findLastUpdates()).containsExactly(entry(Source.WYKOP, wykop));
	}

	@Test
	void theCoveringRangesAreThoseOfStoredRangeRunsWithASucceededSource() {
		List<CoveredRange> covered = List.of(new CoveredRange(SINCE, SINCE.plusSeconds(86_399), Source.REDDIT));
		when(runs.findCoveringSince(SINCE)).thenCallRealMethod();
		when(runs.findRangesEndingSince(RunKind.RANGE, List.of(RunStatus.SUCCEEDED, RunStatus.PARTIAL),
				SourceFetchStatus.SUCCEEDED, SINCE)).thenReturn(covered);

		assertThat(runs.findCoveringSince(SINCE)).isEqualTo(covered);
	}

	@Test
	void eachAskedSourcesFirstCoveredDayIsItsEarliestRangeAndASourceWithoutOneIsMissing() {
		when(runs.findFirstCoveredFrom(any())).thenCallRealMethod();
		when(runs.findEarliestCoveredFrom(RunKind.RANGE, List.of(RunStatus.SUCCEEDED, RunStatus.PARTIAL),
				SourceFetchStatus.SUCCEEDED, Source.REDDIT)).thenReturn(Optional.of(SINCE));
		when(runs.findEarliestCoveredFrom(RunKind.RANGE, List.of(RunStatus.SUCCEEDED, RunStatus.PARTIAL),
				SourceFetchStatus.SUCCEEDED, Source.WYKOP)).thenReturn(Optional.empty());

		assertThat(runs.findFirstCoveredFrom(List.of(Source.WYKOP, Source.REDDIT)))
			.containsExactly(entry(Source.REDDIT, SINCE));
	}

	@Test
	void thePreviousNightIsTheLastCreationOfARunOfTheScheduler() {
		Instant night = Instant.parse("2026-10-01T00:30:00Z");
		when(runs.findLastScheduledCreationBefore(SINCE)).thenCallRealMethod();
		when(runs.findLastCreationBefore(SystemAccount.SCHEDULER.id(), SINCE)).thenReturn(Optional.of(night));

		assertThat(runs.findLastScheduledCreationBefore(SINCE)).contains(night);
	}

	@Test
	void theAdminRunsBetweenTwoNightsAreThoseNotRequestedByTheScheduler() {
		Instant before = SINCE.plusSeconds(86_400);
		List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
		when(runs.findAdminRunIdsCreatedBetween(SINCE, before)).thenCallRealMethod();
		when(runs.findIdsCreatedBetweenNotRequestedBy(SystemAccount.SCHEDULER.id(), SINCE, before)).thenReturn(ids);

		assertThat(runs.findAdminRunIdsCreatedBetween(SINCE, before)).isEqualTo(ids);
	}

}
