package pl.szczurradar.api.pipeline.assess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import pl.szczurradar.api.llm.LlmClientException;
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
import pl.szczurradar.api.pipeline.state.RunInterruptedException;
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
 * lines. The tests wait for events, never for a time of their own: a test that hangs ends at its
 * {@link Timeout}, with a dump of every thread in the log ({@code junit-platform.properties}); a pool's
 * termination, which needs a bound, gets a day.
 */
@ExtendWith(OutputCaptureExtension.class)
@Timeout(60)
class ConcurrentAssessmentDispatcherTest {

	private static final int THREADS = 3;

	private static final UUID RUN = UUID.randomUUID();

	private static final String BOOM = "this post breaks the client";

	/** A post whose raw payload its source's mapper cannot map (a bug of ours, before any call). */
	private static final String UNMAPPABLE = "this post breaks the mapper";

	/**
	 * A post whose input cannot be built with an {@link IllegalStateException} (as {@code AssessmentInputs}
	 * without a mapper for its source), before any call: our code's error, but not the LLM client's.
	 */
	private static final String NO_MAPPER = "this post finds no mapper";

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
	 * Added to a post: its call leaves the client with an error it could not classify (also when it is
	 * interrupted while {@link #HELD}), as an unknown SDK error after an SDK change.
	 */
	private static final String CLIENT_ERROR = " fails in the client";

	/** The message of {@link #CLIENT_ERROR}'s exception, as {@code TransportException.unclassified} makes it. */
	private static final String CLIENT_ERROR_MESSAGE = "java.lang.RuntimeException: unexpected state";

	/** The message of a client error's processing error and technical failure. */
	private static final String CLIENT_FAILURE = "LlmClientException: " + CLIENT_ERROR_MESSAGE;

	/**
	 * Added to a post: while its call is in flight, a stopping instance interrupts the run and puts its
	 * posts back ({@code onClose} during a deploy), and the call ends finding the provider unavailable.
	 */
	private static final String DEPLOYED = " cut off by a deploy";

	/**
	 * Added to a post: the pool's own shutdown (the context closing) interrupts its call, which ends finding
	 * the provider unavailable — nothing marked the run's tasks cancelled.
	 */
	private static final String SHUT_DOWN_UNAVAILABLE = " cut off by the pool's shutdown as an outage";

	/** Added to a post: as {@link #SHUT_DOWN_UNAVAILABLE}, the call ending with an error of the client. */
	private static final String SHUT_DOWN_CLIENT_ERROR = " cut off by the pool's shutdown in the client";

	/**
	 * Added to a post: while its call is in flight, a stopping instance interrupts the run and puts its posts
	 * back, and the call still answers (valid), so the run can no longer store the result.
	 */
	private static final String LOST = " answered after its run lost it";

	/**
	 * Added to a post: an interrupt ends its call (as {@link #HELD}) without the interrupt flag kept, as a
	 * client that clears it would; the call ends with an error of our code.
	 */
	private static final String SWALLOWED = " ends its interrupt without keeping the flag";

	/** Added to a post: its call takes exactly {@link ConcurrentAssessmentDispatcher#PROGRESS_INTERVAL}. */
	private static final String ON_THE_MINUTE = " assessed in exactly the progress interval";

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

	/** Whether the check call waits on {@link #release} first, as a call in flight. */
	private final AtomicBoolean checkHeld = new AtomicBoolean();

	/** The dispatcher's clock for the progress lines, in nanoseconds; only a {@link #SLOW} call moves it. */
	private final AtomicLong nanoTime = new AtomicLong();

	/** A permit for each {@link #HELD} call that started waiting. */
	private final Semaphore holding = new Semaphore(0);

	/** Lets the {@link #HELD} calls answer. */
	private final CountDownLatch release = new CountDownLatch(1);

	/**
	 * When set, each model call first waits until this many calls have reached it, so that many are in flight
	 * at once, however fast the machine.
	 */
	private final AtomicReference<@Nullable CountDownLatch> overlap = new AtomicReference<>();

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
		// The first two calls wait for each other: at least two in flight, never more than the pool's threads.
		overlap.set(new CountDownLatch(2));

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
		Semaphore awaited = new Semaphore(0);
		ExecutorService twoThreads = awaitedPool(2, awaited);
		ExecutorService callers = Executors.newFixedThreadPool(2);
		ConcurrentAssessmentDispatcher dispatcher = dispatcher(twoThreads);
		try {
			Future<AssessedBatch> otherRun = callers.submit(() -> dispatcher.dispatch(other, List.of(going)));
			holding.acquire();
			Future<AssessedBatch> stoppedRun = callers.submit(() -> dispatcher.dispatch(RUN, List.of(stopped, notStarted)));
			holding.acquire();
			// Both steps wait for their posts: each has submitted every task, the stopped run both of its own.
			awaited.acquire(2);

			dispatcher.cancel(RUN);

			// The stopped run's step returns at once, with nothing taken or counted; its call saw the interrupt.
			assertThat(stoppedRun.get()).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
			assertThat(otherRun.isDone()).isFalse();
			release.countDown();
			assertThat(otherRun.get()).isEqualTo(new AssessedBatch(other, 1, 0, 0, List.of(), null));
			twoThreads.shutdown();
			assertThat(twoThreads.awaitTermination(1, TimeUnit.DAYS)).isTrue();
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
	void aDispatchWhoseThreadIsInterruptedCancelsItsTasksAsAStoppedRun(CapturedOutput output) throws Exception {
		UUID held = post(ContractFixtures.BODY + HELD);
		ConcurrentAssessmentDispatcher dispatcher = dispatcher();
		AtomicReference<@Nullable Throwable> thrown = new AtomicReference<>();
		// The run's thread (pipeline-run), waiting for its post while the call is in flight.
		Thread step = Thread.ofPlatform().start(() -> {
			try {
				dispatcher.dispatch(RUN, List.of(held));
			}
			catch (Throwable ex) {
				thrown.set(ex);
			}
		});
		holding.acquire();

		// The watchdog's cancel(true) on the run's task, or a stopping instance's shutdownNow().
		step.interrupt();

		step.join();
		assertThat(step.isAlive()).isFalse();
		assertThat(thrown.get()).isInstanceOf(IllegalStateException.class)
			.hasMessage("interrupted while assessing run " + RUN);
		// The call saw the interrupt and ended with the stopped run.
		executor.shutdown();
		assertThat(executor.awaitTermination(1, TimeUnit.DAYS)).isTrue();
		assertThat(interrupted).singleElement().satisfies(message -> assertThat(message).contains(HELD));
		// Marked cancelled before the interrupt reached the call: not the post's technical error, not counted.
		assertThat(stages.get(held)).isEqualTo(PostStage.STORED);
		assertThat(attempts.get(held)).isZero();
		assertThat(technicalFailures).isEmpty();
		verify(transitions, never()).releaseFailed(any(UUID.class), any(UUID.class), any(String.class));
		verify(recorder, never()).count(any(), any());
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR "))).isEmpty();
		assertThat(output.getAll()).contains("Assessing post " + held + " in run " + RUN + " ended with its stopped run");
	}

	@Test
	void aCheckInterruptedByTheRunsStopEndsTheStepAsAStoppedRun(CapturedOutput output) throws Exception {
		UUID stored = post(ContractFixtures.BODY);
		checkHeld.set(true);
		ConcurrentAssessmentDispatcher dispatcher = dispatcher();
		AtomicReference<@Nullable Throwable> thrown = new AtomicReference<>();
		// The run's thread (pipeline-run), inside the LLM check before the first post.
		Thread step = Thread.ofPlatform().start(() -> {
			try {
				dispatcher.dispatch(RUN, List.of(stored));
			}
			catch (Throwable ex) {
				thrown.set(ex);
			}
		});
		holding.acquire();

		// A stopping instance's shutdownNow(), or the watchdog's cancel(true), while the check is in flight.
		step.interrupt();

		step.join();
		assertThat(step.isAlive()).isFalse();
		// The stop, not the check's failure: execute logs it at INFO and the step stays as the stop closed it.
		assertThat(thrown.get()).isInstanceOf(RunInterruptedException.class)
			.hasMessage("the run's thread was interrupted during assess (run " + RUN + ")")
			// What the interrupted check ended with is kept.
			.cause()
			.isInstanceOf(LlmUnavailableException.class)
			.hasMessage("HTTP 503 after 3 attempts");
		assertThat(calls).hasValue(0);
		assertThat(stages.get(stored)).isEqualTo(PostStage.STORED);
		verify(transitions, never()).claim(any(UUID.class), any(UUID.class));
		verify(recorder, never()).count(any(), any());
		assertThat(output.getAll()).contains("Run " + RUN + " assess: the LLM check was interrupted by the run's stop");
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR "))).isEmpty();
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
		// Cancelled only once the step has registered every task (its first get()), as in the cancel test.
		Semaphore awaited = new Semaphore(0);
		ExecutorService pool = awaitedPool(THREADS, awaited);
		ConcurrentAssessmentDispatcher dispatcher = dispatcher(pool);
		ExecutorService caller = Executors.newSingleThreadExecutor();
		try {
			Future<AssessedBatch> step = caller.submit(() -> dispatcher.dispatch(RUN, ids));
			holding.acquire(THREADS);
			awaited.acquire();

			dispatcher.cancel(RUN);

			assertThat(step.get()).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
			// The interrupted calls end finding the provider "unavailable", as between two attempts.
			pool.shutdown();
			assertThat(pool.awaitTermination(1, TimeUnit.DAYS)).isTrue();
		}
		finally {
			pool.shutdownNow();
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
	void anOutageOfACallThePoolsShutdownInterruptedIsNotCounted(CapturedOutput output) {
		List<UUID> ids = List.of(post(ContractFixtures.BODY + SHUT_DOWN_UNAVAILABLE),
				post(ContractFixtures.BODY + SHUT_DOWN_UNAVAILABLE), post(ContractFixtures.BODY + SHUT_DOWN_UNAVAILABLE));
		UUID after = post(ContractFixtures.BODY);

		AssessedBatch batch = dispatchOnOneThread(List.of(ids.get(0), ids.get(1), ids.get(2), after));

		// Not outages of the provider: no streak (three would stop the step), nothing counted, the posts back
		// without an attempt; the post after them is assessed as usual.
		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 1, 0, 0, List.of(), null));
		assertThat(stages.get(after)).isEqualTo(PostStage.ASSESSED);
		ids.forEach(id -> {
			assertThat(stages.get(id)).isEqualTo(PostStage.STORED);
			assertThat(attempts.get(id)).isZero();
			verify(transitions).release(id, RUN);
		});
		assertThat(technicalFailures).isEmpty();
		verify(transitions, never()).releaseFailed(any(UUID.class), any(UUID.class), any(String.class));
		verify(transitions, never()).countTechnicalFailure(any(UUID.class), any(UUID.class), any(String.class));
		verify(recorder, never()).touch(RUN);
		verify(recorder, times(1)).count(eq(RUN), any(Counters.class));
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR "))).isEmpty();
		assertThat(output.getAll().lines().filter(line -> line.contains("ended with its stopped run")))
			.hasSize(3)
			.allSatisfy(line -> assertThat(line).contains("LlmUnavailableException"));
	}

	@Test
	void aClientErrorOfACallThePoolsShutdownInterruptedIsNotCounted(CapturedOutput output) {
		List<UUID> ids = List.of(post(ContractFixtures.BODY + SHUT_DOWN_CLIENT_ERROR),
				post(ContractFixtures.BODY + SHUT_DOWN_CLIENT_ERROR), post(ContractFixtures.BODY + SHUT_DOWN_CLIENT_ERROR));
		UUID after = post(ContractFixtures.BODY);

		AssessedBatch batch = dispatchOnOneThread(List.of(ids.get(0), ids.get(1), ids.get(2), after));

		// Not the client's errors: no streak (three would trip the client's breaker), no technical failure.
		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 1, 0, 0, List.of(), null));
		assertThat(stages.get(after)).isEqualTo(PostStage.ASSESSED);
		ids.forEach(id -> {
			assertThat(stages.get(id)).isEqualTo(PostStage.STORED);
			assertThat(attempts.get(id)).isZero();
		});
		assertThat(technicalFailures).isEmpty();
		verify(transitions, never()).releaseFailed(any(UUID.class), any(UUID.class), any(String.class));
		verify(recorder, times(1)).count(eq(RUN), any(Counters.class));
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR "))).isEmpty();
		assertThat(output.getAll().lines().filter(line -> line.contains("ended with its stopped run")))
			.hasSize(3)
			.allSatisfy(line -> assertThat(line).contains("LlmClientException"));
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

	@Test
	void threeClientErrorsInARowStopTheStepEachCountingATechnicalFailure(CapturedOutput output) {
		List<UUID> ids = List.of(post(ContractFixtures.BODY + CLIENT_ERROR), post(ContractFixtures.BODY + CLIENT_ERROR),
				post(ContractFixtures.BODY + CLIENT_ERROR), post(ContractFixtures.BODY), post(ContractFixtures.BODY));

		AssessedBatch batch = dispatchOnOneThread(ids);

		assertThat(batch.stoppedBy()).isEqualTo("the LLM client failed 3 calls in a row with an error that is not the"
				+ " provider's; the last: " + CLIENT_FAILURE);
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
		// The posts after the third error are neither taken nor called; nothing is stored.
		assertThat(calls).hasValue(3);
		verify(transitions, never()).claim(ids.get(3), RUN);
		verify(transitions, never()).claim(ids.get(4), RUN);
		assertThat(recorded).isEmpty();
		// Each of the three is an error of our code: back without an attempt, one more technical failure with its
		// message, a processing error of the run.
		assertThat(batch.processingErrors()).extracting(ProcessingError::postId, ProcessingError::message)
			.containsExactly(tuple(ids.get(0), CLIENT_FAILURE), tuple(ids.get(1), CLIENT_FAILURE),
					tuple(ids.get(2), CLIENT_FAILURE));
		ids.subList(0, 3).forEach(id -> verify(transitions).releaseFailed(id, RUN, CLIENT_FAILURE));
		assertThat(technicalFailures).containsOnly(entry(ids.get(0), 1), entry(ids.get(1), 1), entry(ids.get(2), 1));
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isEqualTo(PostStage.STORED));
		assertThat(attempts).allSatisfy((id, count) -> assertThat(count).isZero());
		verify(recorder, times(3)).count(RUN, Counters.processingError());
		verify(recorder, times(3)).count(eq(RUN), any(Counters.class));
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN "))).singleElement()
			.satisfies(line -> assertThat(line).contains("The LLM client failed 3 calls in a row in run " + RUN,
					"no further post is taken", CLIENT_ERROR_MESSAGE));
		assertThat(output.getAll().lines().filter(line -> line.contains(" ERROR "))).hasSize(3)
			.allSatisfy(line -> assertThat(line).contains("put back without an attempt, technical failure 1"));
	}

	@Test
	void aStoredResultEndsTheStreakOfClientErrors() {
		List<UUID> ids = List.of(post(ContractFixtures.BODY + CLIENT_ERROR), post(ContractFixtures.BODY + CLIENT_ERROR),
				post(ContractFixtures.BODY), post(ContractFixtures.BODY + CLIENT_ERROR),
				post(ContractFixtures.BODY + CLIENT_ERROR));

		AssessedBatch batch = dispatchOnOneThread(ids);

		// The model answered the third post: two errors, then two, never three in a row; the fourth error does
		// not stop the step.
		assertThat(batch.stoppedBy()).isNull();
		assertThat(calls).hasValue(5);
		assertThat(batch.ok()).isOne();
		assertThat(batch.processingErrors()).extracting(ProcessingError::postId)
			.containsExactly(ids.get(0), ids.get(1), ids.get(3), ids.get(4));
		assertThat(batch.status()).isEqualTo(RunStatus.PARTIAL);
		assertThat(technicalFailures).containsOnlyKeys(ids.get(0), ids.get(1), ids.get(3), ids.get(4));
	}

	@Test
	void anErrorOutsideTheClientNeitherAddsToNorEndsTheStreakOfClientErrors() {
		UUID noMapper = post(NO_MAPPER);
		UUID boom = post(BOOM);
		UUID after = post(ContractFixtures.BODY);
		List<UUID> ids = List.of(post(ContractFixtures.BODY + CLIENT_ERROR), post(ContractFixtures.BODY + CLIENT_ERROR),
				noMapper, boom, post(ContractFixtures.BODY + CLIENT_ERROR), after);

		AssessedBatch batch = dispatchOnOneThread(ids);

		// A plain IllegalStateException — building the input before the call, or one the client did not classify
		// as its own — is a technical error of its post, outside the streak: the step stops at the third
		// client error, the fifth post.
		assertThat(batch.stoppedBy()).contains("the LLM client failed 3 calls in a row");
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
		assertThat(calls).hasValue(4);
		verify(transitions, never()).claim(after, RUN);
		assertThat(batch.processingErrors()).extracting(ProcessingError::postId, ProcessingError::message)
			.containsExactly(tuple(ids.get(0), CLIENT_FAILURE), tuple(ids.get(1), CLIENT_FAILURE),
					tuple(noMapper, "IllegalStateException: " + NO_MAPPER),
					tuple(boom, "IllegalStateException: a bug in the client"), tuple(ids.get(4), CLIENT_FAILURE));
		assertThat(technicalFailures).containsOnlyKeys(ids.get(0), ids.get(1), noMapper, boom, ids.get(4));
	}

	@Test
	void anOutageNeitherAddsToNorEndsTheStreakOfClientErrors() {
		UUID outage = post(ContractFixtures.BODY + UNAVAILABLE);
		UUID after = post(ContractFixtures.BODY);
		List<UUID> ids = List.of(post(ContractFixtures.BODY + CLIENT_ERROR), post(ContractFixtures.BODY + CLIENT_ERROR),
				outage, post(ContractFixtures.BODY + CLIENT_ERROR), after);

		AssessedBatch batch = dispatchOnOneThread(ids);

		// The outage is the provider's, not the client's: the third client error stops the step.
		assertThat(batch.stoppedBy()).contains("the LLM client failed 3 calls in a row");
		assertThat(calls).hasValue(4);
		verify(transitions, never()).claim(after, RUN);
		// The step stopped, so the outage is not counted: its post is back without a technical failure.
		assertThat(batch.processingErrors()).extracting(ProcessingError::postId)
			.containsExactly(ids.get(0), ids.get(1), ids.get(3));
		assertThat(stages.get(outage)).isEqualTo(PostStage.STORED);
		verify(transitions, never()).countTechnicalFailure(any(UUID.class), any(UUID.class), any(String.class));
		assertThat(technicalFailures).containsOnlyKeys(ids.get(0), ids.get(1), ids.get(3));
	}

	@Test
	void aClientErrorOfACallTheWatchdogCancelledIsNotCounted(CapturedOutput output) throws Exception {
		List<UUID> ids = List.of(post(ContractFixtures.BODY + HELD + CLIENT_ERROR),
				post(ContractFixtures.BODY + HELD + CLIENT_ERROR), post(ContractFixtures.BODY + HELD + CLIENT_ERROR));
		// Cancelled only once the step has registered every task (its first get()), as in the cancel test.
		Semaphore awaited = new Semaphore(0);
		ExecutorService pool = awaitedPool(THREADS, awaited);
		ConcurrentAssessmentDispatcher dispatcher = dispatcher(pool);
		ExecutorService caller = Executors.newSingleThreadExecutor();
		try {
			Future<AssessedBatch> step = caller.submit(() -> dispatcher.dispatch(RUN, ids));
			holding.acquire(THREADS);
			awaited.acquire();

			dispatcher.cancel(RUN);

			assertThat(step.get()).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
			pool.shutdown();
			assertThat(pool.awaitTermination(1, TimeUnit.DAYS)).isTrue();
		}
		finally {
			pool.shutdownNow();
			caller.shutdownNow();
		}

		assertThat(interrupted).hasSize(3);
		// Not the client's errors: no streak, no stop, no technical failure, the posts back without an attempt.
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isEqualTo(PostStage.STORED));
		assertThat(attempts).allSatisfy((id, count) -> assertThat(count).isZero());
		assertThat(technicalFailures).isEmpty();
		verify(transitions, never()).releaseFailed(any(UUID.class), any(UUID.class), any(String.class));
		verify(recorder, never()).count(any(), any());
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR "))).isEmpty();
		assertThat(output.getAll().lines().filter(line -> line.contains("ended with its stopped run")))
			.hasSize(3)
			.allSatisfy(line -> assertThat(line).contains("LlmClientException"));
	}

	@Test
	void threeRefusalsInARowStopTheStepAfterTheirAttemptsAreStored(CapturedOutput output) {
		List<UUID> ids = List.of(post(ContractFixtures.BODY + REFUSED), post(ContractFixtures.BODY + REFUSED),
				post(ContractFixtures.BODY + REFUSED), post(ContractFixtures.BODY), post(ContractFixtures.BODY));

		AssessedBatch batch = dispatchOnOneThread(ids);

		assertThat(batch.stoppedBy())
			.isEqualTo("the LLM provider refused 3 calls in a row; the last: HTTP 400: refused");
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
		assertThat(batch.failed()).isEqualTo(3);
		assertThat(batch.dead()).isZero();
		assertThat(batch.processingErrors()).isEmpty();
		// The posts after the third refusal are neither taken nor called.
		assertThat(calls).hasValue(3);
		verify(transitions, never()).claim(ids.get(3), RUN);
		verify(transitions, never()).claim(ids.get(4), RUN);
		// Each refusal is the post's failed result, stored as its first attempt, as before the breaker.
		assertThat(recorded).extracting(Assessment::getPostId).containsExactlyElementsOf(ids.subList(0, 3));
		assertThat(recorded).allSatisfy(assessment -> assertThat(assessment.getFailureClass())
			.isEqualTo(FailureClass.PROVIDER_ERROR));
		ids.subList(0, 3).forEach(id -> {
			verify(transitions).record(eq(id), eq(RUN), eq(0), any(Assessment.class));
			assertThat(stages.get(id)).isEqualTo(PostStage.FAILED);
		});
		// The rest never left their stage: no attempt.
		ids.subList(3, 5).forEach(id -> {
			verify(transitions, never()).record(eq(id), any(UUID.class), anyInt(), any(Assessment.class));
			assertThat(stages.get(id)).isEqualTo(PostStage.STORED);
			assertThat(attempts.get(id)).isZero();
		});
		// A refusal is the post's fault, never a technical one: the stop is the run's failure.
		verify(transitions, never()).releaseFailed(any(UUID.class), any(UUID.class), any(String.class));
		verify(transitions, never()).countTechnicalFailure(any(UUID.class), any(UUID.class), any(String.class));
		assertThat(technicalFailures).isEmpty();
		verify(recorder, times(3)).count(RUN, Counters.assessedFailed(false, 0, false));
		verify(recorder, times(3)).count(eq(RUN), any(Counters.class));
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN "))).singleElement()
			.satisfies(line -> assertThat(line).contains("The LLM provider refused 3 calls in a row in run " + RUN,
					"no further post is taken", "HTTP 400: refused"));
		assertThat(output.getAll().lines().filter(line -> line.contains(" ERROR "))).isEmpty();
	}

	@Test
	void aContractViolationBetweenRefusalsEndsTheStreakOfRefusals() {
		List<UUID> ids = List.of(post(ContractFixtures.BODY + REFUSED), post(ContractFixtures.BODY + REFUSED),
				post(ContractFixtures.BODY + INVALID), post(ContractFixtures.BODY + REFUSED),
				post(ContractFixtures.BODY + REFUSED));

		AssessedBatch batch = dispatchOnOneThread(ids);

		// The model answered the third post, though it broke the contract: two refusals, then two, never
		// three in a row.
		assertThat(batch.stoppedBy()).isNull();
		assertThat(batch.status()).isEqualTo(RunStatus.PARTIAL);
		assertThat(calls).hasValue(5);
		assertThat(batch.failed()).isEqualTo(5);
		assertThat(recorded).extracting(Assessment::getPostId).containsExactlyElementsOf(ids);
		assertThat(recorded).extracting(Assessment::getFailureClass).containsExactly(FailureClass.PROVIDER_ERROR,
				FailureClass.PROVIDER_ERROR, FailureClass.INVALID_STRUCTURED_OUTPUT, FailureClass.PROVIDER_ERROR,
				FailureClass.PROVIDER_ERROR);
		// Every post used its first attempt: its failed result stored.
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isEqualTo(PostStage.FAILED));
		assertThat(batch.processingErrors()).isEmpty();
	}

	@Test
	void anAnswerOfTheModelBetweenRefusalsEndsTheStreakOfRefusals() {
		UUID answered = post(ContractFixtures.BODY);
		List<UUID> ids = List.of(post(ContractFixtures.BODY + REFUSED), post(ContractFixtures.BODY + REFUSED), answered,
				post(ContractFixtures.BODY + REFUSED), post(ContractFixtures.BODY + REFUSED));

		AssessedBatch batch = dispatchOnOneThread(ids);

		// A valid answer in between: two refusals, then two, never three in a row.
		assertThat(batch.stoppedBy()).isNull();
		assertThat(batch.status()).isEqualTo(RunStatus.PARTIAL);
		assertThat(calls).hasValue(5);
		assertThat(batch.ok()).isOne();
		assertThat(batch.failed()).isEqualTo(4);
		assertThat(stages.get(answered)).isEqualTo(PostStage.ASSESSED);
		assertThat(recorded).extracting(Assessment::getPostId).containsExactlyElementsOf(ids);
		assertThat(batch.processingErrors()).isEmpty();
	}

	@Test
	void aPostDecidedByThePrefilterDoesNotEndTheStreakOfRefusals() {
		UUID prefiltered = post("#gielda <url>");
		UUID after = post(ContractFixtures.BODY);
		List<UUID> ids = List.of(post(ContractFixtures.BODY + REFUSED), post(ContractFixtures.BODY + REFUSED),
				prefiltered, post(ContractFixtures.BODY + REFUSED), after);

		AssessedBatch batch = dispatchOnOneThread(ids);

		// No call between the refusals: the third one stops the step.
		assertThat(batch.stoppedBy()).contains("the LLM provider refused 3 calls in a row");
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
		assertThat(calls).hasValue(3);
		assertThat(recorded).extracting(Assessment::getPostId).containsExactly(ids.get(0), ids.get(1), prefiltered,
				ids.get(3));
		assertThat(stages.get(prefiltered)).isEqualTo(PostStage.ASSESSED);
		verify(transitions, never()).claim(after, RUN);
		assertThat(stages.get(after)).isEqualTo(PostStage.STORED);
		assertThat(attempts.get(after)).isZero();
		assertThat(batch.processingErrors()).isEmpty();
		assertThat(technicalFailures).isEmpty();
	}

	@Test
	void anOutageAndAnErrorOfOurCodeNeitherAddToNorEndTheStreakOfRefusals() {
		UUID outage = post(ContractFixtures.BODY + UNAVAILABLE);
		UUID boom = post(BOOM);
		UUID after = post(ContractFixtures.BODY);
		List<UUID> ids = List.of(post(ContractFixtures.BODY + REFUSED), outage, post(ContractFixtures.BODY + REFUSED),
				boom, post(ContractFixtures.BODY + REFUSED), after);

		AssessedBatch batch = dispatchOnOneThread(ids);

		// Neither the outage nor the client's bug is an answer of the model: the third refusal, the fifth
		// post, stops the step.
		assertThat(batch.stoppedBy()).contains("the LLM provider refused 3 calls in a row");
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
		assertThat(calls).hasValue(5);
		verify(transitions, never()).claim(after, RUN);
		assertThat(recorded).extracting(Assessment::getPostId).containsExactly(ids.get(0), ids.get(2), ids.get(4));
		assertThat(recorded).allSatisfy(assessment -> assertThat(assessment.getFailureClass())
			.isEqualTo(FailureClass.PROVIDER_ERROR));
		// The bug is its post's technical error; the single outage is not counted, as the step stopped.
		assertThat(batch.processingErrors()).extracting(ProcessingError::postId, ProcessingError::message)
			.containsExactly(tuple(boom, "IllegalStateException: a bug in the client"));
		assertThat(stages.get(outage)).isEqualTo(PostStage.STORED);
		assertThat(attempts.get(outage)).isZero();
		verify(transitions, never()).countTechnicalFailure(any(UUID.class), any(UUID.class), any(String.class));
		assertThat(technicalFailures).containsOnlyKeys(boom);
	}

	@Test
	void aRefusalEndsTheStreakOfClientErrors() {
		UUID refused = post(ContractFixtures.BODY + REFUSED);
		List<UUID> ids = List.of(post(ContractFixtures.BODY + CLIENT_ERROR), post(ContractFixtures.BODY + CLIENT_ERROR),
				refused, post(ContractFixtures.BODY + CLIENT_ERROR), post(ContractFixtures.BODY + CLIENT_ERROR));

		AssessedBatch batch = dispatchOnOneThread(ids);

		// The provider answered the refused call: two client errors, then two, never three in a row.
		assertThat(batch.stoppedBy()).isNull();
		assertThat(batch.status()).isEqualTo(RunStatus.PARTIAL);
		assertThat(calls).hasValue(5);
		assertThat(batch.failed()).isOne();
		assertThat(recorded).singleElement().satisfies(assessment -> {
			assertThat(assessment.getPostId()).isEqualTo(refused);
			assertThat(assessment.getFailureClass()).isEqualTo(FailureClass.PROVIDER_ERROR);
		});
		assertThat(batch.processingErrors()).extracting(ProcessingError::postId)
			.containsExactly(ids.get(0), ids.get(1), ids.get(3), ids.get(4));
		assertThat(technicalFailures).containsOnlyKeys(ids.get(0), ids.get(1), ids.get(3), ids.get(4));
	}

	@Test
	void aStepWithoutPostsMakesNoCallNotEvenTheCheck(CapturedOutput output) {
		// A revoked key would fail the check: a step with nothing to assess must not fail the run for it.
		checkFailure.set(revoked());

		AssessedBatch batch = dispatcher().dispatch(RUN, List.of());

		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
		assertThat(batch.status()).isEqualTo(RunStatus.SUCCEEDED);
		assertThat(output.getAll()).doesNotContain("Run " + RUN + " assess:", "The LLM check");
		verifyNoInteractions(transitions);
	}

	@Test
	void aRunNoLongerRunningLeavesItsRemainingPostsUntaken() {
		// The run was stopped (the watchdog, a newer instance's recovery) before its first post was taken.
		running.set(false);
		when(recorder.isRunning(RUN)).thenReturn(false);
		doReturn(Optional.empty()).when(transitions).claim(any(UUID.class), any(UUID.class));
		List<UUID> ids = List.of(post(ContractFixtures.BODY), post(ContractFixtures.BODY), post(ContractFixtures.BODY));

		AssessedBatch batch = dispatchOnOneThread(ids);

		// Asked once: the posts after the first one are not even tried.
		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
		verify(transitions).claim(ids.getFirst(), RUN);
		verify(transitions, times(1)).claim(any(UUID.class), any(UUID.class));
		assertThat(calls).hasValue(0);
	}

	@Test
	void aPostAnotherRunHoldsDoesNotStopTheStep() {
		UUID held = post(ContractFixtures.BODY);
		// Taken meanwhile by another run (or assessed already): not this run's, but the run still runs.
		stages.put(held, PostStage.ASSESSING);
		UUID next = post(ContractFixtures.BODY);

		AssessedBatch batch = dispatchOnOneThread(List.of(held, next));

		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 1, 0, 0, List.of(), null));
		assertThat(stages.get(next)).isEqualTo(PostStage.ASSESSED);
		assertThat(stages.get(held)).isEqualTo(PostStage.ASSESSING);
	}

	@Test
	void aResultTheRunCanNoLongerStoreCountsNothing() {
		UUID lost = post(ContractFixtures.BODY + LOST);

		AssessedBatch batch = dispatchOnOneThread(List.of(lost));

		// The stopping instance put the post back while the call was in flight: the answer is not stored and
		// the post is neither this run's result nor its technical error.
		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
		assertThat(calls).hasValue(1);
		assertThat(recorded).isEmpty();
		assertThat(stages.get(lost)).isEqualTo(PostStage.STORED);
		assertThat(attempts.get(lost)).isZero();
		assertThat(technicalFailures).isEmpty();
		verify(recorder, never()).count(any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = {REFUSED, UNAVAILABLE, CLIENT_ERROR, REVOKED})
	void aBreakerTrippedByCallsEndingTogetherIsLoggedOnce(String marker, CapturedOutput output) {
		// Four calls in flight at once, each ending the same way: the streak passes its limit more than once (or,
		// for a refused configuration, every call stops the step), yet the stop is one WARN line.
		AssessedBatch batch = dispatchAllAtOnce(4, marker);

		assertThat(batch.stoppedBy()).isNotNull();
		assertThat(batch.status()).isEqualTo(RunStatus.FAILED);
		assertThat(calls).hasValue(4);
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN "))).singleElement()
			.satisfies(line -> assertThat(line).contains("in run " + RUN, "no further post is taken"));
	}

	@Test
	void aPostsLastChanceMakesItDeadOnlyWhenItFails(CapturedOutput output) {
		UUID fails = post(ContractFixtures.BODY + INVALID);
		UUID answered = post(ContractFixtures.BODY);
		for (UUID id : List.of(fails, answered)) {
			stages.put(id, PostStage.FAILED);
			attempts.put(id, Post.MAX_ASSESSMENT_ATTEMPTS - 1);
		}
		// Every count fails, so the operator's alert carries the step's own dead count.
		doThrow(new IllegalStateException("the database is gone for a moment")).when(recorder)
			.count(eq(RUN), any(Counters.class));

		AssessedBatch batch = dispatchOnOneThread(List.of(fails, answered));

		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 1, 1, 1, List.of(), null));
		assertThat(stages.get(fails)).isEqualTo(PostStage.FAILED);
		assertThat(stages.get(answered)).isEqualTo(PostStage.ASSESSED);
		verify(recorder).count(RUN, Counters.assessedFailed(true, 1, false));
		verify(recorder).count(RUN, Counters.assessedOk(0, false));
		verify(alerts).raise(eq(AlertEvent.RUN_COUNTERS_LOST), eq(RUN),
				argThat(detail -> detail.contains("2 post result(s) of pipeline run " + RUN)
						&& detail.contains("(1 dead, 0 technical error(s))")));
		String run = "Run " + RUN + " assess: ";
		assertThat(output.getAll().lines().filter(line -> line.contains(run)).map(line -> line.substring(line.indexOf(run))))
			.containsExactly(run + "2/2 (ok 1, failed 1, dead 1, errors 0, unavailable 0), in flight 0, retries 1,"
					+ " flex fallbacks 0");
		// A stored post is never put back: its result is its stage.
		verify(transitions, never()).release(any(UUID.class), any(UUID.class));
		verify(transitions, never()).releaseFailed(any(UUID.class), any(UUID.class), any(String.class));
	}

	@Test
	void aProgressLineIsDueOnceExactlyTheIntervalHasPassed(CapturedOutput output) {
		List<UUID> ids = List.of(post(ContractFixtures.BODY + ON_THE_MINUTE), post(ContractFixtures.BODY));

		dispatchOnOneThread(ids);

		String run = "Run " + RUN + " assess: ";
		assertThat(output.getAll().lines().filter(line -> line.contains(run)).map(line -> line.substring(line.indexOf(run))))
			.containsExactly(
					run + "1/2 (ok 1, failed 0, dead 0, errors 0, unavailable 0), in flight 0, retries 0, flex fallbacks 0",
					run + "2/2 (ok 2, failed 0, dead 0, errors 0, unavailable 0), in flight 0, retries 0, flex fallbacks 0");
	}

	@Test
	void aTaskFailingBeforeItsPostIsTakenIsATechnicalErrorCountedOnce(CapturedOutput output) {
		UUID broken = post(ContractFixtures.BODY);
		UUID fine = post(ContractFixtures.BODY);
		// Taking the post fails (the database for a moment): the task fails outside what assess() catches.
		doThrow(new IllegalStateException("the database is gone for a moment")).when(transitions).claim(broken, RUN);

		AssessedBatch batch = dispatchOnOneThread(List.of(broken, fine));

		assertThat(batch.ok()).isOne();
		assertThat(batch.processingErrors()).singleElement().satisfies(error -> {
			assertThat(error.postId()).isEqualTo(broken);
			assertThat(error.message()).contains("IllegalStateException: the database is gone for a moment");
		});
		assertThat(batch.status()).isEqualTo(RunStatus.PARTIAL);
		// Never taken, so nothing to put back; counted once in the run's counters and in the progress line.
		assertThat(stages.get(broken)).isEqualTo(PostStage.STORED);
		verify(recorder).count(RUN, Counters.processingError());
		verify(recorder).count(RUN, Counters.assessedOk(0, false));
		verify(recorder, times(2)).count(eq(RUN), any(Counters.class));
		String run = "Run " + RUN + " assess: ";
		assertThat(output.getAll().lines().filter(line -> line.contains(run)).map(line -> line.substring(line.indexOf(run))))
			.containsExactly(run + "2/2 (ok 1, failed 0, dead 0, errors 1, unavailable 0), in flight 0, retries 0,"
					+ " flex fallbacks 0");
	}

	@Test
	void aSingleOutageWhoseTechnicalFailureCannotBeWrittenIsNotAProcessingError(CapturedOutput output) {
		UUID outage = post(ContractFixtures.BODY + UNAVAILABLE);
		UUID answered = post(ContractFixtures.BODY);
		doThrow(new IllegalStateException("the database is gone for a moment")).when(transitions)
			.countTechnicalFailure(any(UUID.class), any(UUID.class), any(String.class));

		AssessedBatch batch = dispatchOnOneThread(List.of(outage, answered));

		// Not counted on the post, so not counted in the run either: it counts like a post not taken.
		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 1, 0, 0, List.of(), null));
		assertThat(stages.get(outage)).isEqualTo(PostStage.STORED);
		verify(recorder, never()).count(RUN, Counters.processingError());
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN "))).singleElement()
			.satisfies(line -> assertThat(line).contains("The outage of post " + outage + " in run " + RUN,
					"could not be counted as its technical failure"));
	}

	@Test
	void cancellingARunCountsOnlyItsTasksNotFinished(CapturedOutput output) throws Exception {
		UUID done = post(ContractFixtures.BODY + " (finished before the stop)");
		UUID held = post(ContractFixtures.BODY + HELD);
		Semaphore awaited = new Semaphore(0);
		ExecutorService twoThreads = awaitedPool(2, awaited);
		ConcurrentAssessmentDispatcher dispatcher = dispatcher(twoThreads);
		ExecutorService caller = Executors.newSingleThreadExecutor();
		try {
			Future<AssessedBatch> step = caller.submit(() -> dispatcher.dispatch(RUN, List.of(done, held)));
			holding.acquire();
			// The step waits for its second post: its first one has finished.
			awaited.acquire(2);

			dispatcher.cancel(RUN);

			assertThat(step.get()).isEqualTo(new AssessedBatch(RUN, 1, 0, 0, List.of(), null));
			twoThreads.shutdown();
			assertThat(twoThreads.awaitTermination(1, TimeUnit.DAYS)).isTrue();
		}
		finally {
			twoThreads.shutdownNow();
			caller.shutdownNow();
		}

		assertThat(output.getAll()).contains("Run " + RUN + " assess: stopped, 1 post task(s) not finished were cancelled");
		assertThat(stages.get(done)).isEqualTo(PostStage.ASSESSED);
		assertThat(stages.get(held)).isEqualTo(PostStage.STORED);
	}

	@Test
	void afterCancelAnOutageWithoutAnInterruptAndAPostNotStartedCountNothing(CapturedOutput output) throws Exception {
		UUID held = post(ContractFixtures.BODY + HELD + UNAVAILABLE);
		UUID waiting = post(ContractFixtures.BODY);
		Semaphore awaited = new Semaphore(0);

		// One thread: the second post waits for it; cancel() reaches neither task, only the run's mark.
		AssessedBatch batch = cancelWhileHeld(tooLatePool(1, awaited), awaited, List.of(held, waiting));

		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
		assertThat(interrupted).isEmpty();
		// The held call's outage is the stopped run's, not the provider's: no activity, no technical failure.
		verify(recorder, never()).touch(RUN);
		verify(transitions, never()).countTechnicalFailure(any(UUID.class), any(UUID.class), any(String.class));
		assertThat(stages.get(held)).isEqualTo(PostStage.STORED);
		// The post not started yet is never taken.
		verify(transitions, never()).claim(waiting, RUN);
		assertThat(stages.get(waiting)).isEqualTo(PostStage.STORED);
		assertThat(calls).hasValue(1);
		verify(recorder, never()).count(any(), any());
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR "))).isEmpty();
	}

	@Test
	void afterCancelAClientErrorWithoutAnInterruptCountsNothing(CapturedOutput output) throws Exception {
		UUID held = post(ContractFixtures.BODY + HELD + CLIENT_ERROR);
		Semaphore awaited = new Semaphore(0);

		AssessedBatch batch = cancelWhileHeld(tooLatePool(1, awaited), awaited, List.of(held));

		assertThat(batch).isEqualTo(new AssessedBatch(RUN, 0, 0, 0, List.of(), null));
		assertThat(interrupted).isEmpty();
		// Not the post's technical error: back without one.
		assertThat(stages.get(held)).isEqualTo(PostStage.STORED);
		assertThat(technicalFailures).isEmpty();
		verify(transitions, never()).releaseFailed(any(UUID.class), any(UUID.class), any(String.class));
		verify(recorder, never()).count(any(), any());
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR "))).isEmpty();
	}

	@Test
	void anInterruptedDispatchMarksItsTasksCancelledEvenForACallThatDropsTheInterrupt(CapturedOutput output)
			throws Exception {
		UUID held = post(ContractFixtures.BODY + HELD + SWALLOWED);
		ConcurrentAssessmentDispatcher dispatcher = dispatcher();
		AtomicReference<@Nullable Throwable> thrown = new AtomicReference<>();
		AtomicBoolean flagKept = new AtomicBoolean();
		Thread step = Thread.ofPlatform().start(() -> {
			try {
				dispatcher.dispatch(RUN, List.of(held));
			}
			catch (Throwable ex) {
				thrown.set(ex);
				flagKept.set(Thread.currentThread().isInterrupted());
			}
		});
		holding.acquire();

		step.interrupt();

		step.join();
		assertThat(thrown.get()).isInstanceOf(IllegalStateException.class)
			.hasMessage("interrupted while assessing run " + RUN);
		// The run's thread keeps its interrupt for whoever runs it (PipelineRunService.execute).
		assertThat(flagKept).isTrue();
		executor.shutdown();
		assertThat(executor.awaitTermination(1, TimeUnit.DAYS)).isTrue();
		assertThat(interrupted).singleElement().satisfies(message -> assertThat(message).contains(SWALLOWED));
		// The call ended without the flag, with an error of our code: the mark alone keeps it from being the
		// post's technical error.
		assertThat(stages.get(held)).isEqualTo(PostStage.STORED);
		assertThat(technicalFailures).isEmpty();
		verify(transitions, never()).releaseFailed(any(UUID.class), any(UUID.class), any(String.class));
		verify(recorder, never()).count(any(), any());
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR "))).isEmpty();
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

	/**
	 * A pool of {@code threads} threads, as {@link Executors#newFixedThreadPool}, whose tasks release a permit
	 * of {@code awaited} each time someone waits for their result ({@link Future#get()}): a step waits for its
	 * first post only once it has submitted every one.
	 */
	private static ExecutorService awaitedPool(int threads, Semaphore awaited) {
		return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {

			@Override
			protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
				return new FutureTask<>(callable) {

					@Override
					public T get() throws InterruptedException, ExecutionException {
						awaited.release();
						return super.get();
					}

				};
			}

		};
	}

	/**
	 * As {@link #awaitedPool}, but every {@link Future#cancel} comes too late: it neither stops a task nor
	 * interrupts its thread, as for a task a thread took just before the stop, whose call then ends without
	 * seeing an interrupt. Only the dispatcher's own mark of the stop ({@code cancel}) is left to decide.
	 */
	private static ExecutorService tooLatePool(int threads, Semaphore awaited) {
		return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {

			@Override
			protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
				return new FutureTask<>(callable) {

					@Override
					public T get() throws InterruptedException, ExecutionException {
						awaited.release();
						return super.get();
					}

					@Override
					public boolean cancel(boolean mayInterruptIfRunning) {
						return false;
					}

				};
			}

		};
	}

	/**
	 * Dispatches {@code ids} on {@code pool} from a thread of its own, cancels the run once the held call is
	 * in flight and the step waits for its posts, lets the held calls answer, and returns the step's result.
	 */
	private AssessedBatch cancelWhileHeld(ExecutorService pool, Semaphore awaited, List<UUID> ids) throws Exception {
		ConcurrentAssessmentDispatcher dispatcher = dispatcher(pool);
		ExecutorService caller = Executors.newSingleThreadExecutor();
		try {
			Future<AssessedBatch> step = caller.submit(() -> dispatcher.dispatch(RUN, ids));
			holding.acquire();
			awaited.acquire();

			dispatcher.cancel(RUN);
			release.countDown();

			return step.get();
		}
		finally {
			pool.shutdownNow();
			caller.shutdownNow();
		}
	}

	/** {@code count} posts whose calls are all in flight at once on as many threads, each ending as {@code marker} says. */
	private AssessedBatch dispatchAllAtOnce(int count, String marker) {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ids.add(post(ContractFixtures.BODY + marker));
		}
		overlap.set(new CountDownLatch(count));
		ExecutorService pool = Executors.newFixedThreadPool(count);
		try {
			return dispatcher(pool).dispatch(RUN, ids);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private ConcurrentAssessmentDispatcher dispatcher(ExecutorService pool) {
		Clock clock = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC);
		LlmClient llm = request -> {
			if (request.equals(LlmClient.CHECK_REQUEST)) {
				if (checkHeld.get()) {
					holding.release();
					try {
						release.await();
					}
					catch (InterruptedException ex) {
						// As TransportRetry between attempts: the interrupt ends the check, the flag kept.
						Thread.currentThread().interrupt();
						throw unavailable();
					}
				}
				RuntimeException failure = checkFailure.get();
				if (failure != null) {
					throw failure;
				}
				return response("{\"ok\":true}");
			}
			calls.incrementAndGet();
			maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
			try {
				CountDownLatch meeting = overlap.get();
				if (meeting != null) {
					meeting.countDown();
					meeting.await();
				}
				if (request.userMessage().contains(HELD)) {
					holding.release();
					release.await();
				}
				Thread.sleep(50);
				if (request.userMessage().contains(SHUT_DOWN_UNAVAILABLE)) {
					// AssessmentPool.destroy()'s shutdownNow(): the interrupt ends the call, the flag kept.
					Thread.currentThread().interrupt();
					throw unavailable();
				}
				if (request.userMessage().contains(SHUT_DOWN_CLIENT_ERROR)) {
					Thread.currentThread().interrupt();
					throw clientError();
				}
				if (request.userMessage().contains(DEPLOYED)) {
					// onClose of a stopping instance: the run interrupted and its posts put back, the call cut off.
					running.set(false);
					stages.keySet().forEach(this::putBack);
					throw unavailable();
				}
				if (request.userMessage().contains(LOST)) {
					// onClose of a stopping instance while the call is in flight; the call still answers.
					running.set(false);
					stages.keySet().forEach(this::putBack);
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
				if (request.userMessage().contains(CLIENT_ERROR)) {
					throw clientError();
				}
				if (request.userMessage().contains(REVOKED)) {
					throw revoked();
				}
				if (request.userMessage().contains(SLOW)) {
					nanoTime.addAndGet(ConcurrentAssessmentDispatcher.PROGRESS_INTERVAL.plusSeconds(1).toNanos());
				}
				if (request.userMessage().contains(ON_THE_MINUTE)) {
					nanoTime.addAndGet(ConcurrentAssessmentDispatcher.PROGRESS_INTERVAL.toNanos());
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
				if (!request.userMessage().contains(SWALLOWED)) {
					Thread.currentThread().interrupt();
				}
				if (request.userMessage().contains(UNAVAILABLE)) {
					// As TransportRetry, interrupted between two attempts.
					throw unavailable();
				}
				if (request.userMessage().contains(CLIENT_ERROR)) {
					throw clientError();
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

	private static LlmClientException clientError() {
		return new LlmClientException(CLIENT_ERROR_MESSAGE, new RuntimeException("unexpected state"));
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
			Post post = new Post(Source.WYKOP, ItemType.POST, id.toString(), "gielda", Instant.now(), null,
					bodies.get(id), null, null, null, List.of(), new RawPayloadRef(id), RUN);
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
				if (body.contains(NO_MAPPER)) {
					throw new IllegalStateException(body);
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
