package pl.szczurradar.api.pipeline.assess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.llm.transport.TransportException;
import pl.szczurradar.api.pipeline.flow.RunFlow.AssessedBatch;
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
 * {@link RunRecorder} (the counters) and a test clock for the progress lines.
 */
@ExtendWith(OutputCaptureExtension.class)
class ConcurrentAssessmentDispatcherTest {

	private static final int THREADS = 3;

	private static final UUID RUN = UUID.randomUUID();

	private static final String BOOM = "this post breaks the client";

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

	private final ExecutorService executor = Executors.newFixedThreadPool(THREADS);

	/** Each post's body, by id; the raw payload is the body's bytes. */
	private final Map<UUID, String> bodies = new ConcurrentHashMap<>();

	private final Map<UUID, PostStage> stages = new ConcurrentHashMap<>();

	private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();

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

	private final RunRecorder recorder = recorder();

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
		// Each post is counted once, as it finishes: no total at the end of the step.
		verify(recorder, times(8)).count(RUN, Counters.assessedOk(0, false));
		verify(recorder).count(RUN, Counters.assessedOk(2, true));
		verify(recorder).count(RUN, Counters.processingError());
		verify(recorder, times(10)).count(eq(RUN), any(Counters.class));
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
			.containsExactly(run + "1/3 (ok 1, failed 0, dead 0, errors 0), in flight 1, retries 2, flex fallbacks 1",
					run + "3/3 (ok 2, failed 1, dead 0, errors 0), in flight 0, retries 3, flex fallbacks 1");
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
		assertThat(output.getAll().lines().filter(line -> line.contains(" ERROR "))).isEmpty();
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
				throw new IllegalStateException("the call was interrupted", ex);
			}
			finally {
				inFlight.decrementAndGet();
			}
		};
		return new ConcurrentAssessmentDispatcher(posts(), new AssessmentInputs(rawPayloads(), List.of(inputMapper())),
				new Assessor(llm, Provider.GEMINI, "gemini-3.1-flash-lite", clock), transitions(), recorder, llm,
				pool, nanoTime::get);
	}

	private static LlmResponse response(String raw) {
		return response(raw, 1, false);
	}

	/** An answer after {@code attempts} HTTP attempts; after a refusal on the flex tier when {@code flexFallback}. */
	private static LlmResponse response(String raw, int attempts, boolean flexFallback) {
		return new LlmResponse(raw, Provider.GEMINI, "gemini-3.1-flash-lite", Channel.GEMINI, ServiceTier.DEFAULT,
				flexFallback, null, OutputMode.JSON_SCHEMA, 100, 50, null, attempts);
	}

	private static LlmConfigurationException revoked() {
		return new LlmConfigurationException("HTTP 401: invalid api key", 1, ServiceTier.DEFAULT, false,
				new TransportException(TransportException.Kind.CONFIGURATION, "HTTP 401: invalid api key", null, null));
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
				return ContractFixtures.post(new String(raw, StandardCharsets.UTF_8));
			}

		};
	}

	/** The conditional updates, in memory: take stored/failed, store only while held, put back. */
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
		doAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			stages.replace(id, PostStage.ASSESSING, attempts.get(id) == 0 ? PostStage.STORED : PostStage.FAILED);
			return null;
		}).when(transitions).release(any(UUID.class), any(UUID.class));
		return transitions;
	}

	private static RunRecorder recorder() {
		RunRecorder recorder = mock(RunRecorder.class);
		when(recorder.isRunning(RUN)).thenReturn(true);
		return recorder;
	}

}
