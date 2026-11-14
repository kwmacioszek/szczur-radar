package pl.szczurradar.api.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.SystemAccount;

/**
 * Why runs need an admin's attention ({@link PipelineRun#attentionReasons()}, and so
 * {@link PipelineRun#needsAttention()}), for every status and in the rule's order, and which the daily cron
 * requested ({@link PipelineRun#scheduled()}). What each kind of new run holds and refuses, and that a run
 * loaded from its row reads back the columns only the database writes (set as Hibernate's field access sets
 * them).
 */
class PipelineRunTest {

	private static final Instant FROM = Instant.parse("2026-09-30T22:00:00Z");

	private static final Instant TO = Instant.parse("2026-10-01T21:59:59Z");

	@Test
	void aFailedRunHasOnlyItsOwnReasonWhateverItsCounters() {
		assertThat(PipelineRun.attentionReasons(RunStatus.FAILED, 0, 0, 0, 0, 0))
			.containsExactly(AttentionReason.FAILED);
		assertThat(PipelineRun.attentionReasons(RunStatus.FAILED, 1, 1, 1, 1, 1))
			.containsExactly(AttentionReason.FAILED);
	}

	@Test
	void anInterruptedRunHasOnlyItsOwnReasonWhateverItsCounters() {
		assertThat(PipelineRun.attentionReasons(RunStatus.INTERRUPTED, 0, 0, 0, 0, 0))
			.containsExactly(AttentionReason.INTERRUPTED);
		assertThat(PipelineRun.attentionReasons(RunStatus.INTERRUPTED, 1, 1, 1, 1, 1))
			.containsExactly(AttentionReason.INTERRUPTED);
	}

	@Test
	void aPartialRunNeedsAttentionOnlyWithADeadPostATechnicalErrorOrSkippedElementsInThatOrder() {
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 1, 0, 0, 0, 0)).as("a dead post")
			.containsExactly(AttentionReason.DEAD_POSTS);
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 0, 1, 0, 0, 0)).as("a technical error")
			.containsExactly(AttentionReason.PROCESSING_ERRORS);
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 0, 0, 1, 0, 0)).as("a skipped element")
			.containsExactly(AttentionReason.SKIPPED);
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 2, 3, 4, 0, 0))
			.containsExactly(AttentionReason.DEAD_POSTS, AttentionReason.PROCESSING_ERRORS, AttentionReason.SKIPPED);
		// Only failed posts with attempts left: the next run retries them.
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 0, 0, 0, 0, 0)).isEmpty();
	}

	@Test
	void aSucceededRunNeedsAttentionOnlyWithSkippedElements() {
		assertThat(PipelineRun.attentionReasons(RunStatus.SUCCEEDED, 0, 0, 1, 0, 0)).as("a skipped element")
			.containsExactly(AttentionReason.SKIPPED);
		assertThat(PipelineRun.attentionReasons(RunStatus.SUCCEEDED, 0, 0, 0, 0, 0)).isEmpty();
		// A succeeded run has neither, but they alone would not make it need attention.
		assertThat(PipelineRun.attentionReasons(RunStatus.SUCCEEDED, 1, 1, 0, 0, 0)).isEmpty();
	}

	@Test
	void aPartialRunsFailedOrEmptySourcesComeFirstInTheRulesOrder() {
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 0, 0, 0, 1, 0)).as("a failed source")
			.containsExactly(AttentionReason.SOURCE_FAILED);
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 0, 0, 0, 0, 1)).as("an empty source")
			.containsExactly(AttentionReason.SOURCE_EMPTY);
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 0, 0, 0, 1, 1))
			.containsExactly(AttentionReason.SOURCE_FAILED, AttentionReason.SOURCE_EMPTY);
		assertThat(PipelineRun.attentionReasons(RunStatus.PARTIAL, 2, 3, 4, 1, 1)).containsExactly(
				AttentionReason.SOURCE_FAILED, AttentionReason.SOURCE_EMPTY, AttentionReason.DEAD_POSTS,
				AttentionReason.PROCESSING_ERRORS, AttentionReason.SKIPPED);
	}

	@Test
	void aSucceededRunsFailedOrEmptySourceNeedsAttention() {
		assertThat(PipelineRun.attentionReasons(RunStatus.SUCCEEDED, 0, 0, 0, 0, 1))
			.containsExactly(AttentionReason.SOURCE_EMPTY);
		assertThat(PipelineRun.attentionReasons(RunStatus.SUCCEEDED, 0, 0, 2, 1, 0))
			.containsExactly(AttentionReason.SOURCE_FAILED, AttentionReason.SKIPPED);
	}

	@ParameterizedTest
	@EnumSource(value = RunStatus.class, names = { "QUEUED", "RUNNING" })
	void anUnfinishedRunNeedsNoAttentionWhateverItsCounters(RunStatus status) {
		assertThat(PipelineRun.attentionReasons(status, 0, 0, 0, 0, 0)).isEmpty();
		assertThat(PipelineRun.attentionReasons(status, 1, 1, 1, 1, 1)).isEmpty();
	}

	@Test
	void onlyARunRequestedByTheSchedulerIsScheduled() {
		assertThat(PipelineRun.retry(SystemAccount.SCHEDULER.id(), null).scheduled()).isTrue();
		assertThat(PipelineRun.retry(UUID.randomUUID(), null).scheduled()).as("an admin's").isFalse();
	}

	@Test
	void aNewRunIsQueuedAndNeedsNoAttention() {
		PipelineRun run = PipelineRun.retry(UUID.randomUUID(), null);

		assertThat(run.attentionReasons()).isEmpty();
		assertThat(run.needsAttention()).isFalse();
	}

	@Test
	void aNewRangeRunHoldsItsRangeSourcesRequesterAndDeployment() {
		UUID admin = UUID.randomUUID();

		PipelineRun run = PipelineRun.range(FROM, TO, List.of(Source.REDDIT, Source.WYKOP), admin, "deployment-7");

		assertThat(run.getKind()).isEqualTo(RunKind.RANGE);
		assertThat(run.getStatus()).isEqualTo(RunStatus.QUEUED);
		assertThat(run.getRangeFrom()).isEqualTo(FROM);
		assertThat(run.getRangeTo()).isEqualTo(TO);
		assertThat(run.getSources()).containsExactly(Source.REDDIT, Source.WYKOP);
		assertThat(run.getRequestedPostIds()).isNull();
		assertThat(run.getRequestedBy()).isEqualTo(admin);
		assertThat(run.getDeploymentId()).isEqualTo("deployment-7");
	}

	@Test
	void aRangeOfOneInstantIsARange() {
		assertThat(PipelineRun.range(FROM, FROM, List.of(Source.WYKOP), UUID.randomUUID(), null).getRangeTo())
			.isEqualTo(FROM);
	}

	@Test
	void aRangeEndingBeforeItStartsOrWithoutASourceIsRefused() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> PipelineRun.range(TO, FROM, List.of(Source.WYKOP), UUID.randomUUID(), null))
			.withMessageContaining("is after");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> PipelineRun.range(FROM, TO, List.of(), UUID.randomUUID(), null))
			.withMessageContaining("at least one source");
	}

	@Test
	void aNewPostsRunHoldsItsPostsAndNoSources() {
		List<UUID> posts = List.of(UUID.randomUUID(), UUID.randomUUID());

		PipelineRun run = PipelineRun.posts(posts, UUID.randomUUID(), null);

		assertThat(run.getKind()).isEqualTo(RunKind.POSTS);
		assertThat(run.getRequestedPostIds()).isEqualTo(posts);
		assertThat(run.getSources()).isEmpty();
		assertThat(run.getRangeFrom()).isNull();
		assertThat(run.getRangeTo()).isNull();
		assertThat(run.getDeploymentId()).isNull();
	}

	@Test
	void aPostsRunWithoutAPostIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> PipelineRun.posts(List.of(), UUID.randomUUID(), null))
			.withMessageContaining("at least one post");
	}

	@Test
	void aRetryRunHasNoSourcesRangeOrPosts() {
		PipelineRun run = PipelineRun.retry(UUID.randomUUID(), null);

		assertThat(run.getKind()).isEqualTo(RunKind.RETRY);
		assertThat(run.getSources()).isEmpty();
		assertThat(run.getRequestedPostIds()).isNull();
	}

	@Test
	void aRunLoadedFromItsRowReadsBackWhatOnlyTheDatabaseWrites() {
		PipelineRun run = PipelineRun.retry(UUID.randomUUID(), null);
		Instant created = Instant.parse("2026-10-02T00:30:00Z");
		Instant started = Instant.parse("2026-10-02T00:30:01Z");
		Instant activity = Instant.parse("2026-10-02T00:40:00Z");
		Instant finished = Instant.parse("2026-10-02T00:41:00Z");
		ReflectionTestUtils.setField(run, "createdAt", created);
		ReflectionTestUtils.setField(run, "startedAt", started);
		ReflectionTestUtils.setField(run, "lastActivityAt", activity);
		ReflectionTestUtils.setField(run, "finishedAt", finished);
		ReflectionTestUtils.setField(run, "fetched", 11);
		ReflectionTestUtils.setField(run, "newPosts", 10);
		ReflectionTestUtils.setField(run, "assessedOk", 8);
		ReflectionTestUtils.setField(run, "assessedFailed", 2);
		ReflectionTestUtils.setField(run, "skipped", 1);
		ReflectionTestUtils.setField(run, "llmRetries", 3);
		ReflectionTestUtils.setField(run, "flexFallbacks", 4);
		ReflectionTestUtils.setField(run, "sourcesFailed", 1);
		ReflectionTestUtils.setField(run, "sourcesEmpty", 2);
		ReflectionTestUtils.setField(run, "interruptionCause", InterruptionCause.LOST);

		assertThat(run.getCreatedAt()).isEqualTo(created);
		assertThat(run.getStartedAt()).isEqualTo(started);
		assertThat(run.getLastActivityAt()).isEqualTo(activity);
		assertThat(run.getFinishedAt()).isEqualTo(finished);
		assertThat(run.getFetched()).isEqualTo(11);
		assertThat(run.getNewPosts()).isEqualTo(10);
		assertThat(run.getAssessedOk()).isEqualTo(8);
		assertThat(run.getAssessedFailed()).isEqualTo(2);
		assertThat(run.getSkipped()).isEqualTo(1);
		assertThat(run.getLlmRetries()).isEqualTo(3);
		assertThat(run.getFlexFallbacks()).isEqualTo(4);
		assertThat(run.getSourcesFailed()).isEqualTo(1);
		assertThat(run.getSourcesEmpty()).isEqualTo(2);
		assertThat(run.getInterruptionCause()).isEqualTo(InterruptionCause.LOST);
	}

}
