package pl.szczurradar.api.pipeline.assess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.OperatorAlertQueue;
import pl.szczurradar.api.llm.CallFailure;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.LlmUnavailableException;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.llm.transport.ProviderMessages;
import pl.szczurradar.api.llm.transport.TransportException;
import pl.szczurradar.api.pipeline.flow.RunFlow.AssessedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.ProcessingError;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.state.PostTransitions;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.pipeline.state.RunRecorder.Counters;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.RawInputMapper;
import pl.szczurradar.api.sources.Source;

/**
 * {@link ConcurrentAssessmentDispatcher} on its own pool of {@value #THREADS} threads, one scripted
 * {@link LlmClient}, the posts' stages kept in memory in place of the database, a mocked
 * {@link RunRecorder} (the counters), a mocked {@link OperatorAlertQueue} and a test clock for the progress
 * lines.
 */
@ExtendWith(OutputCaptureExtension.class)
class ConcurrentAssessmentDispatcherTest {

	private static final int THREADS = 3;

	private static final UUID RUN = UUID.randomUUID();

	private static final String BOOM = "this post breaks the client";

	/** A post whose raw payload its source's mapper cannot map (a bug of ours, before any call). */
	private static final String UNMAPPABLE = "this post breaks the mapper";

	/** A post whose call finds the key revoked (HTTP 401). */
	private static final String REVOKED = "this post finds the key revoked";

	/** Added to a post: its call took 3 HTTP attempts and fell back from flex to the default tier. */
	private static final String RETRIED = " retried";

	/** Added to a post: the model answers it with no JSON (a failed assessment), after 2 HTTP attempts. */
	private static final String INVALID = " answered invalid";

	/** Added to a post: its call takes longer than {@link ConcurrentAssessmentDispatcher#PROGRESS_INTERVAL}. */
	private static final String SLOW = " assessed slowly";

	/** Added to a post: its call waits for {@link #release} (or an interrupt), like an answer that does not come. */
	private static final String HELD = " held until released";

	/**
	 * Added to a post: its call finds the provider unavailable after its retries (also when it is
	 * interrupted while {@link #HELD}, as between two attempts).
	 */
	private static final String UNAVAILABLE = " finds the provider unavailable";

	/** Added to a post: the provider refuses its call (HTTP 400), a failed assessment after one attempt. */
	private static final String REFUSED = " refused by the provider";

	/**
	 * Added to a post: while its call is in flight, a stopping instance interrupts the run and puts its
	 * posts back ({@code onClose} during a deploy), and the call ends finding the provider unavailable.
	 */
	private static final String DEPLOYED = " cut off by a deploy";

	/** The message of an outage's processing error and technical failure. */
	private static final String OUTAGE = "LlmUnavailableException: HTTP 503 after 3 attempts";

	/** The configured LLM key, which no run error or log line may carry. */
	private static final String KEY = "synthetic-key";

	private final ExecutorService executor = Executors.newFixedThreadPool(THREADS);

	/** Each post's body, by id; the raw payload is the body's bytes. */
	private final Map<UUID, String> bodies = new ConcurrentHashMap<>();

	private final Map<UUID, PostStage> stages = new ConcurrentHashMap<>();

	private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();

	/** Each post's technical failures in a row and the last one's message, as {@code releaseFailed} counts them. */
	private final Map<UUID, Integer> technicalFailures = new ConcurrentHashMap<>();

	private final Map<UUID, String> lastTechnicalErrors = new ConcurrentHashMap<>();

	private final List<Assessment> recorded = new CopyOnWriteArrayList<>();

	private final AtomicInteger calls = new AtomicInteger();

	private final AtomicInteger inFlight = new AtomicInteger();

	private final AtomicInteger maxInFlight = new AtomicInteger();

	/** What the check call ({@link LlmClient#CHECK_REQUEST}) throws, if anything. */
	private final AtomicReference<@Nullable RuntimeException> checkFailure = new AtomicReference<>();

	/** The dispatcher's clock for the progress lines, in nanoseconds; only a {@link #SLOW} call moves it. */
	private final AtomicLong nanoTime = new AtomicLong();

	/** A permit for each {@link #HELD} call that started waiting. */
	private final Semaphore holding = new Semaphore(0);

	/** Lets the {@link #HELD} calls answer. */
	private final CountDownLatch release = new CountDownLatch(1);

	/** The user messages of the calls that were interrupted. */
	private final List<String> interrupted = new CopyOnWriteArrayList<>();

	/** Whether the run is still running, as {@code countTechnicalFailure}'s guard sees it. */
	private final AtomicBoolean running = new AtomicBoolean(true);

	private final RunRecorder recorder = recorder();

	private final PostTransitions transitions = transitions();

	private final OperatorAlertQueue alerts = mock(OperatorAlertQueue.class);

	@AfterEach
	void stop() {
		release.countDown();
		executor.shutdownNow();
	}

	@Test
	void eachPostIsAssessedOnceWithinTheThreadLimitAndATechnicalErrorPutsThePostBack() {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			// One post decided by the prefilter, one that breaks the client, the rest assessed by the model
			// (one of them after retries and a flex fallback).
			ids.add(post(i == 0 ? "#gielda <url>" : i == 5 ? BOOM : i == 7 ? ContractFixtures.BODY + RETRIED
					: ContractFixtures.BODY));
		}
		UUID broken = ids.get(5);

		AssessedBatch batch = dispatcher().dispatch(RUN, ids);

		assertThat(recorded).extracting(Assessment::getPostId).doesNotHaveDuplicates()
			.containsExactlyInAnyOrderElementsOf(ids.stream().filter(id -> !id.equals(broken)).toList());
		assertThat(recorded).filteredOn(assessment -> assessment.getProvenance().source() == Provenance.Source.PREFILTER)
			.singleElement()
			.satisfies(assessment -> assertThat(assessment.getPostId()).isEqualTo(ids.getFirst()));
		assertThat(calls).hasValue(9);
		assertThat(maxInFlight.get()).isBetween(2, THREADS);
		assertThat(batch.ok()).isEqualTo(9);
		assertThat(batch.failed()).isZero();
		assertThat(batch.processingErrors()).singleElement().satisfies(error -> {
			assertThat(error.postId()).isEqualTo(broken);
			assertThat(error.message()).contains("IllegalStateException");
		});
		assertThat(batch.status()).isEqualTo(RunStatus.PARTIAL);
		// Put back where it was, without an attempt; every other post assessed.
		assertThat(stages.get(broken)).isEqualTo(PostStage.STORED);
		assertThat(attempts.get(broken)).isZero();
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isIn(PostStage.ASSESSED, PostStage.STORED));
		// The broken post counts a technical failure, with the same message as the run's processing error.
		assertThat(technicalFailures).containsOnlyKeys(broken).containsEntry(broken, 1);
		assertThat(lastTechnicalErrors.get(broken)).isEqualTo(batch.processingErrors().getFirst().message());
		verify(transitions, never()).release(broken, RUN);
		// Each post is counted once, as it finishes: no total at the end of the step.
		verify(recorder, times(8)).count(RUN, Counters.assessedOk(0, false));
		verify(recorder).count(RUN, Counters.assessedOk(2, true));
		verify(recorder).count(RUN, Counters.processingError());
		verify(recorder, times(10)).count(eq(RUN), any(Counters.class));
		// Every count was written: nothing for the operator.
		verifyNoInteractions(alerts);
	}

	@Test
	void aBugBeforeTheCallPutsThePostBackCountingATechnicalFailure(CapturedOutput output) {
		UUID unmappable = post(UNMAPPABLE);
		stages.put(unmappable, PostStage.FAILED);
		attempts.put(unmappable, 1);
		technicalFailures.put(unmappable, 1);
		UUID fine = post(ContractFixtures.BODY);

		AssessedBatch batch = dispatcher().dispatch(RUN, List.of(unmappable, fine));

		// No call and no stored attempt for it: back in its stage, its attempts as they were.
		assertThat(calls).hasValue(1);
		assertThat(recorded).extracting(Assessment::getPostId).containsExactly(fine);
		assertThat(stages.get(unmappable)).isEqualTo(PostStage.FAILED);
		assertThat(attempts.get(unmappable)).isEqualTo(1);
		assertThat(batch.processingErrors()).singleElement().satisfies(error -> {
			assertThat(error.postId()).isEqualTo(unmappable);
			assertThat(error.message()).isEqualTo("IllegalArgumentException: " + UNMAPPABLE);
		});
		assertThat(batch.status()).isEqualTo(RunStatus.PARTIAL);
		verify(transitions).releaseFailed(unmappable, RUN, "IllegalArgumentException: " + UNMAPPABLE);
		verify(transitions, never()).release(unmappable, RUN);
		assertThat(technicalFailures).containsEntry(unmappable, 2);
		verify(recorder).count(RUN, Counters.processingError());
		assertThat(output.getAll().lines().filter(line -> line.contains(" ERROR "))).singleElement()
			.satisfies(line -> assertThat(line).contains("post " + unmappable + " in run " + RUN,
					"put back without an attempt, technical failure 2"));
	}

	@Test
	void aPostDecidedWithoutACallCountsNoRetryAndAFailedOneCountsItsAttemptAndDeath() {
		UUID prefiltered = post("#gielda <url>");
		UUID failing = post(ContractFixtures.BODY + INVALID);
		UUID lastChance = post(ContractFixtures.BODY + INVALID);
		stages.put(lastChance, PostStage.FAILED);
		attempts.put(lastChance, 2);

		AssessedBatch batch = dispatcher().dispatch(RUN, List.of(prefiltered, failing, lastChance));

		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 1, 2, 1, List.of(), null));
		// The prefilter makes no call (attempts 0): no retry, never -1.
		verify(recorder).count(RUN, Counters.assessedOk(0, false));
		verify(recorder).count(RUN, Counters.assessedFailed(false, 1, false));
		verify(recorder).count(RUN, Counters.assessedFailed(true, 1, false));
		verify(recorder, times(3)).count(eq(RUN), any(Counters.class));
	}

	@Test
	void theLogShowsTheProgressAtMostOnceAMinuteAsPostsFinishAndAfterTheLast(CapturedOutput output) {
		// One thread: the posts finish in order; only the first one's call moves the clock past a minute.
		ExecutorService oneThread = Executors.newSingleThreadExecutor();
		when(recorder.inFlight(RUN)).thenReturn(1, 0);
		List<UUID> ids = List.of(post(ContractFixtures.BODY + RETRIED + SLOW), post(ContractFixtures.BODY),
				post(ContractFixtures.BODY + INVALID));
		try {
			dispatcher(oneThread).dispatch(RUN, ids);
		}
		finally {
			oneThread.shutdownNow();
		}

		String run = "Run " + RUN + " assess: ";
		assertThat(output.getAll().lines().filter(line -> line.contains(run)).map(line -> line.substring(line.indexOf(run))))
			.containsExactly(
					run + "1/3 (ok 1, failed 0, dead 0, errors 0, unavailable 0), in flight 1, retries 2, flex fallbacks 1",
					run + "3/3 (ok 2, failed 1, dead 0, errors 0, unavailable 0), in flight 0, retries 3, flex fallbacks 1");
	}

	@Test
	void aPostThatCannotBeTakenIsLeftAlone() {
		UUID assessed = post(ContractFixtures.validInput().item().body());
		stages.put(assessed, PostStage.ASSESSED);

		AssessedBatch batch = dispatcher().dispatch(RUN, List.of(assessed));

		assertThat(recorded).isEmpty();
		assertThat(calls).hasValue(0);
		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
		verify(recorder, never()).count(any(), any());
	}

	@Test
	void aFailedCheckTakesNoPostAndStopsTheStep() {
		List<UUID> ids = List.of(post(ContractFixtures.validInput().item().body()), post(BOOM));
		checkFailure.set(revoked());

		AssessedBatch batch = dispatcher().dispatch(RUN, ids);

		assertThat(calls).hasValue(0);
		assertThat(recorded).isEmpty();
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isEqualTo(PostStage.STORED));
		assertThat(batch.stoppedBy()).contains("the LLM check failed", "HTTP 401");
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
	}

	@Test
	void aCheckFailingWithAnErrorThatIsNotTheProvidersTakesNoPostAndStopsTheStep() {
		List<UUID> ids = List.of(post(ContractFixtures.validInput().item().body()));
		// e.g. an unknown SDK error, which the clients leave unclassified
		checkFailure.set(new IllegalStateException("java.lang.RuntimeException: unexpected state"));

		AssessedBatch batch = dispatcher().dispatch(RUN, ids);

		assertThat(calls).hasValue(0);
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isEqualTo(PostStage.STORED));
		assertThat(batch.stoppedBy()).isEqualTo("the LLM check failed: java.lang.RuntimeException: unexpected state");
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
	}

	@Test
	void aCheckFailingOutsideTheClientsLeavesTheKeyOutOfTheRunsErrorAndTheLog(CapturedOutput output) {
		List<UUID> ids = List.of(post(ContractFixtures.validInput().item().body()));
		// e.g. building the client: neither the message nor a cause went through a client's cleaning
		checkFailure.set(new IllegalStateException("no client for the key " + KEY,
				new IllegalArgumentException("bad key " + KEY)));

		AssessedBatch batch = dispatcher().dispatch(RUN, ids);

		assertThat(batch.stoppedBy()).isEqualTo("the LLM check failed: no client for the key <hidden>");
		assertThat(output).doesNotContain(KEY).contains(IllegalArgumentException.class.getName(), "bad key <hidden>");
	}

	@Test
	void aRefusedConfigurationStopsTheStepWithoutAnAttempt(CapturedOutput output) {
		List<UUID> ids = new ArrayList<>();
		ids.add(post(REVOKED));
		for (int i = 0; i < 9; i++) {
			ids.add(post(ContractFixtures.validInput().item().body()));
		}

		AssessedBatch batch = dispatcher().dispatch(RUN, ids);

		// Posts already in flight may finish; the refused one and every post not yet taken stay stored.
		assertThat(recorded).extracting(Assessment::getPostId).doesNotContain(ids.getFirst());
		assertThat(stages.get(ids.getFirst())).isEqualTo(PostStage.STORED);
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isIn(PostStage.ASSESSED, PostStage.STORED));
		assertThat(attempts).allSatisfy((id, count) -> assertThat(count).isZero());
		assertThat(recorded).hasSizeLessThan(ids.size() - 1);
		assertThat(batch.processingErrors()).isEmpty();
		assertThat(batch.stoppedBy()).contains("refused the configuration", "HTTP 401");
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
		// Only the posts stored are counted; the refusal is not a technical error, but it is logged.
		verify(recorder, times(recorded.size())).count(eq(RUN), any(Counters.class));
		verify(recorder, never()).count(RUN, Counters.processingError());
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN ")))
			.singleElement()
			.satisfies(line -> assertThat(line).contains("refused the configuration in run " + RUN, "HTTP 401"));
	}

	@Test
	void aFailedCountLeavesAStoredPostStored(CapturedOutput output) {
		List<UUID> ids = List.of(post(ContractFixtures.BODY), post(ContractFixtures.BODY));
		doThrow(new IllegalStateException("the database is gone for a moment")).when(recorder)
			.count(eq(RUN), any(Counters.class));

		AssessedBatch batch = dispatcher().dispatch(RUN, ids);

		// Both are assessed and stored; the counters missed them, which is logged, not a technical error.
		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 2, 0, 0, List.of(), null));
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isEqualTo(PostStage.ASSESSED));
		verify(recorder, times(2)).count(RUN, Counters.assessedOk(0, false));
		verify(recorder, never()).count(RUN, Counters.processingError());
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN "))).hasSize(2)
			.allSatisfy(line -> assertThat(line).contains("could not be added to the counters of run " + RUN))
			.anySatisfy(line -> assertThat(line).contains("post " + ids.get(0)))
			.anySatisfy(line -> assertThat(line).contains("post " + ids.get(1)));
		// Once the step has ended: one ERROR line for the run's understated counters, never one per post.
		assertThat(output.getAll().lines().filter(line -> line.contains(" ERROR "))).singleElement()
			.satisfies(line -> assertThat(line).contains("2 post result(s) of run " + RUN,
					"(0 dead, 0 technical error(s))", "needsAttention may be false"));
	}

	@Test
	void resultsLostToFailedCountsAreRaisedOnceWithTheirNumbers(CapturedOutput output) {
		UUID ok = post(ContractFixtures.BODY);
		UUID lastChance = post(ContractFixtures.BODY + INVALID);
		stages.put(lastChance, PostStage.FAILED);
		attempts.put(lastChance, 2);
		UUID counted = post(ContractFixtures.BODY + RETRIED);
		// The ok post's and the dead post's counts fail; the retried post's is written.
		doThrow(new IllegalStateException("the database is gone for a moment")).when(recorder)
			.count(RUN, Counters.assessedOk(0, false));
		doThrow(new IllegalStateException("the database is gone for a moment")).when(recorder)
			.count(RUN, Counters.assessedFailed(true, 1, false));

		AssessedBatch batch = dispatcher().dispatch(RUN, List.of(ok, lastChance, counted));

		// The step's own result is whole; only the run's counters miss two posts, one of them dead.
		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 2, 1, 1, List.of(), null));
		verify(recorder).count(RUN, Counters.assessedOk(2, true));
		verify(alerts).raise(eq(AlertEvent.RUN_COUNTERS_LOST), eq(RUN),
				argThat(detail -> detail.contains("2 post result(s) of pipeline run " + RUN)
						&& detail.contains("(1 dead, 0 technical error(s))") && detail.contains("/admin/runs/" + RUN)
						&& !detail.contains("the database is gone")));
		verify(alerts, times(1)).raise(any(), any(), any());
		assertThat(output.getAll().lines().filter(line -> line.contains(" ERROR "))).singleElement()
			.satisfies(line -> assertThat(line).contains("2 post result(s) of run " + RUN, "(1 dead, 0 technical"));
	}

	@Test
	void cancellingARunInterruptsItsCallInFlightAndLeavesAnotherRunAlone(CapturedOutput output) throws Exception {
		UUID other = UUID.randomUUID();
		UUID going = post(ContractFixtures.BODY + HELD + " (the other run's)");
		UUID stopped = post(ContractFixtures.BODY + HELD + " (the stopped run's)");
		UUID notStarted = post(ContractFixtures.BODY + " (the stopped run's, never started)");
		// Two threads: the other run's call holds one, the stopped run's call the other, and the stopped run's
		// second post waits for a thread.
		ExecutorService twoThreads = Executors.newFixedThreadPool(2);
		ExecutorService callers = Executors.newFixedThreadPool(2);
		ConcurrentAssessmentDispatcher dispatcher = dispatcher(twoThreads);
		try {
			Future<AssessedBatch> otherRun = callers.submit(() -> dispatcher.dispatch(other, List.of(going)));
			assertThat(holding.tryAcquire(10, TimeUnit.SECONDS)).isTrue();
			Future<AssessedBatch> stoppedRun = callers.submit(() -> dispatcher.dispatch(RUN, List.of(stopped, notStarted)));
			assertThat(holding.tryAcquire(10, TimeUnit.SECONDS)).isTrue();

			dispatcher.cancel(RUN);

			// The stopped run's step returns at once, with nothing taken or counted; its call saw the interrupt.
			assertThat(stoppedRun.get(10, TimeUnit.SECONDS)).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
			assertThat(otherRun.isDone()).isFalse();
			release.countDown();
			assertThat(otherRun.get(10, TimeUnit.SECONDS)).isEqualTo(new AssessedBatch(other, 1, 0, 0, List.of(), null));
			twoThreads.shutdown();
			assertThat(twoThreads.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			twoThreads.shutdownNow();
			callers.shutdownNow();
		}

		assertThat(interrupted).singleElement().satisfies(message -> assertThat(message).contains("the stopped run's"));
		// Its posts are back without an attempt, and the one not started was never taken.
		assertThat(stages.get(stopped)).isEqualTo(PostStage.STORED);
		assertThat(stages.get(notStarted)).isEqualTo(PostStage.STORED);
		assertThat(attempts.get(stopped)).isZero();
		assertThat(recorded).extracting(Assessment::getPostId).containsExactly(going);
		verify(recorder, never()).count(eq(RUN), any(Counters.class));
		verify(recorder).count(other, Counters.assessedOk(0, false));
		assertThat(output.getAll()).contains("Run " + RUN + " assess: stopped, 2 post task(s) not finished were cancelled");
		assertThat(output.getAll().lines().filter(line -> line.contains(" ERROR "))).isEmpty();
	}

	@Test
	void cancellingARunThatIsNotDispatchedChangesNothingForItsLaterStep() {
		ConcurrentAssessmentDispatcher dispatcher = dispatcher();
		dispatcher.cancel(RUN);

		AssessedBatch batch = dispatcher.dispatch(RUN, List.of(post(ContractFixtures.BODY)));

		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 1, 0, 0, List.of(), null));
	}

	@Test
	void threeOutagesInARowStopTheStepWithoutAnAttemptOrATechnicalFailure(CapturedOutput output) {
		List<UUID> ids = List.of(post(ContractFixtures.BODY + UNAVAILABLE), post(ContractFixtures.BODY + UNAVAILABLE),
				post(ContractFixtures.BODY + UNAVAILABLE), post(ContractFixtures.BODY), post(ContractFixtures.BODY));

		AssessedBatch batch = dispatchOnOneThread(ids);

		assertThat(batch.stoppedBy()).isEqualTo("the LLM provider is unavailable: 3 calls in a row failed after their"
				+ " retries; the last: HTTP 503 after 3 attempts");
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
		assertThat(batch.processingErrors()).isEmpty();
		// The posts after the third outage are not taken; every post stays stored, without an attempt.
		assertThat(calls).hasValue(3);
		assertThat(recorded).isEmpty();
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isEqualTo(PostStage.STORED));
		assertThat(attempts).allSatisfy((id, count) -> assertThat(count).isZero());
		ids.subList(0, 3).forEach(id -> verify(transitions).release(id, RUN));
		verify(transitions, never()).claim(ids.get(3), RUN);
		// Nothing is counted on the posts or in the run: the stop is the run's failure.
		verify(transitions, never()).countTechnicalFailure(any(UUID.class), any(UUID.class), any(String.class));
		assertThat(technicalFailures).isEmpty();
		verify(recorder, never()).count(any(), any());
		// Yet each outage moved the run's last activity: the watchdog never stops a run before the breaker.
		verify(recorder, times(3)).touch(RUN);
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN "))).singleElement()
			.satisfies(line -> assertThat(line).contains("LLM provider is unavailable in run " + RUN,
					"3 calls in a row", "HTTP 503 after 3 attempts"));
		assertThat(output.getAll().lines().filter(line -> line.contains(" ERROR "))).isEmpty();
	}

	@Test
	void anOutagesPostIsPutBackEvenWhenTheFirstWriteFails() {
		UUID id = post(ContractFixtures.BODY + UNAVAILABLE);
		AtomicInteger writes = new AtomicInteger();
		doAnswer(invocation -> {
			if (writes.getAndIncrement() == 0) {
				throw new IllegalStateException("the database is gone for a moment");
			}
			return putBack(invocation.getArgument(0));
		}).when(transitions).release(any(UUID.class), any(UUID.class));

		AssessedBatch batch = dispatchOnOneThread(List.of(id));

		// Tried again on the way out: the post is back, without an attempt; the failed write is the
		// post's technical error, as any failed write.
		verify(transitions, times(2)).release(id, RUN);
		assertThat(stages.get(id)).isEqualTo(PostStage.STORED);
		assertThat(attempts.get(id)).isZero();
		assertThat(batch.processingErrors()).singleElement()
			.satisfies(error -> assertThat(error.message()).contains("the database is gone for a moment"));
	}

	@Test
	void singleOutagesAreTechnicalFailuresCountedOnceTheStepHasEnded(CapturedOutput output) {
		UUID first = post(ContractFixtures.BODY + UNAVAILABLE);
		UUID answered = post(ContractFixtures.BODY);
		UUID second = post(ContractFixtures.BODY + UNAVAILABLE);
		UUID last = post(ContractFixtures.BODY);

		AssessedBatch batch = dispatchOnOneThread(List.of(first, answered, second, last));

		// Each answer ends the streak: the step goes on.
		assertThat(batch.stoppedBy()).isNull();
		assertThat(batch.ok()).isEqualTo(2);
		assertThat(batch.processingErrors()).extracting(ProcessingError::postId, ProcessingError::message)
			.containsExactly(tuple(first, OUTAGE), tuple(second, OUTAGE));
		assertThat(batch.status()).isEqualTo(RunStatus.PARTIAL);
		// Back without an attempt, each counting a technical failure with its message.
		assertThat(stages.get(first)).isEqualTo(PostStage.STORED);
		assertThat(stages.get(second)).isEqualTo(PostStage.STORED);
		assertThat(attempts.get(first)).isZero();
		assertThat(technicalFailures).containsOnly(entry(first, 1), entry(second, 1));
		assertThat(lastTechnicalErrors).containsEntry(first, OUTAGE).containsEntry(second, OUTAGE);
		verify(recorder, times(2)).count(RUN, Counters.processingError());
		verify(recorder, times(2)).count(RUN, Counters.assessedOk(0, false));
		verify(recorder, times(2)).touch(RUN);
		// Counted only after the last post was stored, never as the outage's post was put back.
		InOrder order = inOrder(transitions);
		order.verify(transitions).record(eq(last), eq(RUN), anyInt(), any(Assessment.class));
		order.verify(transitions).countTechnicalFailure(first, RUN, OUTAGE);
		order.verify(transitions).countTechnicalFailure(second, RUN, OUTAGE);
		verify(transitions, never()).releaseFailed(any(UUID.class), any(UUID.class), any(String.class));
		String run = "Run " + RUN + " assess: ";
		assertThat(output.getAll().lines().filter(line -> line.contains(run)).map(line -> line.substring(line.indexOf(run))))
			.containsExactly(run + "4/4 (ok 2, failed 0, dead 0, errors 0, unavailable 2), in flight 0, retries 0,"
					+ " flex fallbacks 0");
	}

	@Test
	void aRefusedCallEndsTheStreakOfOutages() {
		UUID refused = post(ContractFixtures.BODY + REFUSED);
		List<UUID> ids = List.of(post(ContractFixtures.BODY + UNAVAILABLE), post(ContractFixtures.BODY + UNAVAILABLE),
				refused, post(ContractFixtures.BODY + UNAVAILABLE));

		AssessedBatch batch = dispatchOnOneThread(ids);

		// The provider answered the refused call: two outages, then one, never three in a row.
		assertThat(batch.stoppedBy()).isNull();
		assertThat(batch.failed()).isOne();
		assertThat(recorded).singleElement().satisfies(assessment -> {
			assertThat(assessment.getPostId()).isEqualTo(refused);
			assertThat(assessment.getFailureClass()).isEqualTo(FailureClass.PROVIDER_ERROR);
		});
		assertThat(batch.processingErrors()).extracting(ProcessingError::postId)
			.containsExactly(ids.get(0), ids.get(1), ids.get(3));
		assertThat(batch.status()).isEqualTo(RunStatus.PARTIAL);
		assertThat(technicalFailures).containsOnlyKeys(ids.get(0), ids.get(1), ids.get(3));
	}

	@Test
	void aPostDecidedByThePrefilterDoesNotEndTheStreakOfOutages() {
		UUID prefiltered = post("#gielda <url>");
		UUID after = post(ContractFixtures.BODY);
		List<UUID> ids = List.of(post(ContractFixtures.BODY + UNAVAILABLE), post(ContractFixtures.BODY + UNAVAILABLE),
				prefiltered, post(ContractFixtures.BODY + UNAVAILABLE), after);

		AssessedBatch batch = dispatchOnOneThread(ids);

		// No call between the outages: the third one stops the step.
		assertThat(batch.stoppedBy()).contains("the LLM provider is unavailable: 3 calls in a row");
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
		assertThat(recorded).extracting(Assessment::getPostId).containsExactly(prefiltered);
		assertThat(stages.get(after)).isEqualTo(PostStage.STORED);
		assertThat(calls).hasValue(3);
		assertThat(batch.processingErrors()).isEmpty();
		assertThat(technicalFailures).isEmpty();
		verify(recorder, never()).count(RUN, Counters.processingError());
	}

	@Test
	void anOutageOfACallTheWatchdogCancelledIsNotCounted(CapturedOutput output) throws Exception {
		List<UUID> ids = List.of(post(ContractFixtures.BODY + HELD + UNAVAILABLE),
				post(ContractFixtures.BODY + HELD + UNAVAILABLE), post(ContractFixtures.BODY + HELD + UNAVAILABLE));
		ConcurrentAssessmentDispatcher dispatcher = dispatcher();
		ExecutorService caller = Executors.newSingleThreadExecutor();
		try {
			Future<AssessedBatch> step = caller.submit(() -> dispatcher.dispatch(RUN, ids));
			assertThat(holding.tryAcquire(THREADS, 10, TimeUnit.SECONDS)).isTrue();

			dispatcher.cancel(RUN);

			assertThat(step.get(10, TimeUnit.SECONDS)).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
			// The interrupted calls end finding the provider "unavailable", as between two attempts.
			executor.shutdown();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			caller.shutdownNow();
		}

		assertThat(interrupted).hasSize(3);
		// Not outages of the provider: no streak, no stop, nothing counted, the posts back without an attempt.
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isEqualTo(PostStage.STORED));
		assertThat(attempts).allSatisfy((id, count) -> assertThat(count).isZero());
		verify(transitions, never()).countTechnicalFailure(any(UUID.class), any(UUID.class), any(String.class));
		verify(recorder, never()).count(any(), any());
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR "))).isEmpty();
		assertThat(output.getAll().lines().filter(line -> line.contains("ended with its stopped run")))
			.hasSize(3)
			.allSatisfy(line -> assertThat(line).contains("LlmUnavailableException"));
	}

	@Test
	void anOutageOfARunNoLongerRunningIsNotCounted() {
		UUID first = post(ContractFixtures.BODY + UNAVAILABLE);
		UUID cutOff = post(ContractFixtures.BODY + DEPLOYED);

		AssessedBatch batch = dispatchOnOneThread(List.of(first, cutOff));

		// A deploy interrupted the run after the first outage: the step still ends without a stop.
		assertThat(batch.stoppedBy()).isNull();
		assertThat(batch.processingErrors()).isEmpty();
		// The first post's count is refused by the run's guard; the cut-off post was put back by the
		// stopping instance, not by this task, so it is not even tried.
		verify(transitions).countTechnicalFailure(first, RUN, OUTAGE);
		verify(transitions, never()).countTechnicalFailure(eq(cutOff), any(UUID.class), any(String.class));
		assertThat(technicalFailures).isEmpty();
		verify(recorder, never()).count(RUN, Counters.processingError());
		assertThat(stages.get(first)).isEqualTo(PostStage.STORED);
		assertThat(stages.get(cutOff)).isEqualTo(PostStage.STORED);
		assertThat(attempts).allSatisfy((id, count) -> assertThat(count).isZero());
	}

	private UUID post(String body) {
		UUID id = UUID.randomUUID();
		bodies.put(id, body);
		stages.put(id, PostStage.STORED);
		attempts.put(id, 0);
		return id;
	}

	private ConcurrentAssessmentDispatcher dispatcher() {
		return dispatcher(executor);
	}

	/** {@link ConcurrentAssessmentDispatcher#dispatch} on one thread: the posts end in their order. */
	private AssessedBatch dispatchOnOneThread(List<UUID> ids) {
		ExecutorService oneThread = Executors.newSingleThreadExecutor();
		try {
			return dispatcher(oneThread).dispatch(RUN, ids);
		}
		finally {
			oneThread.shutdownNow();
		}
	}

	private ConcurrentAssessmentDispatcher dispatcher(ExecutorService pool) {
		Clock clock = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC);
		LlmClient llm = request -> {
			if (request.equals(LlmClient.CHECK_REQUEST)) {
				RuntimeException failure = checkFailure.get();
				if (failure != null) {
					throw failure;
				}
				return response("{\"ok\":true}");
			}
			calls.incrementAndGet();
			maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
			try {
				if (request.userMessage().contains(HELD)) {
					holding.release();
					release.await();
				}
				Thread.sleep(50);
				if (request.userMessage().contains(DEPLOYED)) {
					// onClose of a stopping instance: the run interrupted and its posts put back, the call cut off.
					running.set(false);
					stages.keySet().forEach(this::putBack);
					throw unavailable();
				}
				if (request.userMessage().contains(UNAVAILABLE)) {
					throw unavailable();
				}
				if (request.userMessage().contains(REFUSED)) {
					throw new LlmCallException(CallFailure.PROVIDER_ERROR, "HTTP 400: refused", 1, ServiceTier.DEFAULT,
							false, null);
				}
				if (request.userMessage().contains(BOOM)) {
					throw new IllegalStateException("a bug in the client");
				}
				if (request.userMessage().contains(REVOKED)) {
					throw revoked();
				}
				if (request.userMessage().contains(SLOW)) {
					nanoTime.addAndGet(ConcurrentAssessmentDispatcher.PROGRESS_INTERVAL.plusSeconds(1).toNanos());
				}
				if (request.userMessage().contains(INVALID)) {
					return response("no JSON here", 2, false);
				}
				String valid = ContractJson.MAPPER.writeValueAsString(ContractFixtures.validOutput());
				return request.userMessage().contains(RETRIED) ? response(valid, 3, true) : response(valid);
			}
			catch (InterruptedException ex) {
				// As the HTTP client of a call in flight: an interrupt ends the call.
				interrupted.add(request.userMessage());
				Thread.currentThread().interrupt();
				if (request.userMessage().contains(UNAVAILABLE)) {
					// As TransportRetry, interrupted between two attempts.
					throw unavailable();
				}
				throw new IllegalStateException("the call was interrupted", ex);
			}
			finally {
				inFlight.decrementAndGet();
			}
		};
		return new ConcurrentAssessmentDispatcher(posts(), new AssessmentInputs(rawPayloads(), List.of(inputMapper())),
				new Assessor(llm, Provider.GEMINI, "gemini-3.1-flash-lite", clock), transitions, recorder, llm,
				pool, nanoTime::get, alertQueue(), new ProviderMessages(KEY));
	}

	private static LlmResponse response(String raw) {
		return response(raw, 1, false);
	}

	/** An answer after {@code attempts} HTTP attempts; after a refusal on the flex tier when {@code flexFallback}. */
	private static LlmResponse response(String raw, int attempts, boolean flexFallback) {
		return new LlmResponse(raw, Provider.GEMINI, "gemini-3.1-flash-lite", Channel.GEMINI, ServiceTier.DEFAULT,
				flexFallback, null, OutputMode.JSON_SCHEMA, 100, 50, null, attempts);
	}

	private static LlmUnavailableException unavailable() {
		return new LlmUnavailableException(CallFailure.PROVIDER_ERROR, "HTTP 503 after 3 attempts", 3,
				ServiceTier.DEFAULT, false, null);
	}

	private static LlmConfigurationException revoked() {
		return new LlmConfigurationException("HTTP 401: invalid api key", 1, ServiceTier.DEFAULT, false,
				new TransportException(TransportException.Kind.CONFIGURATION, "HTTP 401: invalid api key", null, null));
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<OperatorAlertQueue> alertQueue() {
		ObjectProvider<OperatorAlertQueue> queue = mock(ObjectProvider.class);
		when(queue.getIfAvailable()).thenReturn(alerts);
		return queue;
	}

	private PostRepository posts() {
		PostRepository posts = mock(PostRepository.class);
		when(posts.findById(any(UUID.class))).thenAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			Post post = new Post(Source.WYKOP, ItemType.POST, id.toString(), Instant.now(), null, bodies.get(id), null,
					null, null, List.of(), new RawPayloadRef(id), RUN);
			return Optional.of(post);
		});
		return posts;
	}

	private RawPayloadStore rawPayloads() {
		RawPayloadStore raw = mock(RawPayloadStore.class);
		when(raw.load(any(RawPayloadRef.class)))
			.thenAnswer(invocation -> bodies.get(invocation.<RawPayloadRef>getArgument(0).id()).getBytes(StandardCharsets.UTF_8));
		return raw;
	}

	private static RawInputMapper inputMapper() {
		return new RawInputMapper() {

			@Override
			public Source source() {
				return Source.WYKOP;
			}

			@Override
			public AssessmentInputV1 map(byte[] raw) {
				String body = new String(raw, StandardCharsets.UTF_8);
				if (body.contains(UNMAPPABLE)) {
					throw new IllegalArgumentException(body);
				}
				return ContractFixtures.post(body);
			}

		};
	}

	/**
	 * The conditional updates, in memory: take stored/failed, store only while held, put back (also
	 * counting a technical failure), count a technical failure of a post put back while the run is
	 * {@link #running}.
	 */
	private PostTransitions transitions() {
		PostTransitions transitions = mock(PostTransitions.class);
		when(transitions.claim(any(UUID.class), any(UUID.class))).thenAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			boolean taken = stages.replace(id, PostStage.STORED, PostStage.ASSESSING)
					|| stages.replace(id, PostStage.FAILED, PostStage.ASSESSING);
			return taken ? Optional.of(attempts.get(id)) : Optional.empty();
		});
		when(transitions.record(any(UUID.class), any(UUID.class), anyInt(), any(Assessment.class))).thenAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			Assessment assessment = invocation.getArgument(3);
			if (!stages.replace(id, PostStage.ASSESSING, assessment.getFailureClass() == null ? PostStage.ASSESSED
					: PostStage.FAILED)) {
				return false;
			}
			recorded.add(assessment);
			return true;
		});
		doAnswer(invocation -> putBack(invocation.getArgument(0))).when(transitions)
			.release(any(UUID.class), any(UUID.class));
		doAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			if (!putBack(id)) {
				return Optional.empty();
			}
			lastTechnicalErrors.put(id, invocation.getArgument(2));
			return Optional.of(technicalFailures.merge(id, 1, Integer::sum));
		}).when(transitions).releaseFailed(any(UUID.class), any(UUID.class), any(String.class));
		doAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			PostStage stage = stages.get(id);
			if (!running.get() || stage != PostStage.STORED && stage != PostStage.FAILED) {
				return false;
			}
			lastTechnicalErrors.put(id, invocation.getArgument(2));
			technicalFailures.merge(id, 1, Integer::sum);
			return true;
		}).when(transitions).countTechnicalFailure(any(UUID.class), any(UUID.class), any(String.class));
		return transitions;
	}

	private boolean putBack(UUID id) {
		return stages.replace(id, PostStage.ASSESSING, attempts.get(id) == 0 ? PostStage.STORED : PostStage.FAILED);
	}

	private static RunRecorder recorder() {
		RunRecorder recorder = mock(RunRecorder.class);
		when(recorder.isRunning(RUN)).thenReturn(true);
		return recorder;
	}

}
