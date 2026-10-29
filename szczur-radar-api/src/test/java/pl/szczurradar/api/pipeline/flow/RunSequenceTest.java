package pl.szczurradar.api.pipeline.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.pipeline.assess.AssessmentDispatcher;
import pl.szczurradar.api.pipeline.flow.RunFlow.AssessedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.PendingAssessments;
import pl.szczurradar.api.pipeline.flow.RunFlow.ProcessingError;
import pl.szczurradar.api.pipeline.flow.RunFlow.RangeTarget;
import pl.szczurradar.api.pipeline.flow.RunFlow.RunRequest;
import pl.szczurradar.api.pipeline.flow.RunFlow.RunSummary;
import pl.szczurradar.api.pipeline.flow.RunFlow.SelectionTarget;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceFetch;
import pl.szczurradar.api.pipeline.flow.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.pipeline.run.StepStatus;
import pl.szczurradar.api.pipeline.state.RunNoLongerRunningException;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.pipeline.state.RunRecorder.Counters;
import pl.szczurradar.api.pipeline.state.RunRecorder.StepOutcome;
import pl.szczurradar.api.pipeline.step.AssessmentSelection;
import pl.szczurradar.api.pipeline.step.NoPostsFetchedException;
import pl.szczurradar.api.pipeline.step.PostStore;
import pl.szczurradar.api.pipeline.step.SourceIngest;
import pl.szczurradar.api.sources.Source;

/**
 * {@link RunSequence} without Spring: the steps of each kind of run in order and each once, what each
 * step writes, and that a step's exception ends the run as it was thrown, without a further step. The
 * services are mocks; the {@link RunRecorder} mock runs each step's work and writes down when the step
 * started ({@code step:started}) and how it ended ({@code step:status:out[:error]}), its other writes, in
 * one list of events.
 */
class RunSequenceTest {

	private static final UUID RUN = UUID.randomUUID();

	private static final Instant FROM = Instant.parse("2026-09-30T22:00:00Z");

	private static final Instant TO = Instant.parse("2026-10-01T21:59:59Z");

	private static final List<UUID> NEW_POSTS = List.of(UUID.randomUUID(), UUID.randomUUID());

	/** A fetch of Wykop alone, which gave posts (none in the batch: the store step is a mock). */
	private static final FetchedBatch WYKOP_ONLY = new FetchedBatch(RUN, List.of(), 3, 1,
			List.of(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 3, 1, null)));

	/** Wykop gave posts, Reddit failed. */
	private static final FetchedBatch REDDIT_FAILED = new FetchedBatch(RUN, List.of(), 3, 1,
			List.of(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 3, 1, null),
					new SourceFetch(Source.REDDIT, SourceFetchStatus.FAILED, 0, 0, "IllegalStateException: HTTP 503")));

	private final SourceIngest ingest = mock(SourceIngest.class);

	private final PostStore store = mock(PostStore.class);

	private final AssessmentSelection selection = mock(AssessmentSelection.class);

	private final AssessmentDispatcher dispatcher = mock(AssessmentDispatcher.class);

	private final RunRecorder recorder = mock(RunRecorder.class);

	/** The steps as they ended and the recorder's other writes, in order. */
	private final List<String> events = new CopyOnWriteArrayList<>();

	/** The steps the run is no longer running for: their step is refused before its work. */
	private final Set<RunStep> refused = EnumSet.noneOf(RunStep.class);

	private final RunSequence sequence = new RunSequence(ingest, store, selection, dispatcher, recorder);

	@SuppressWarnings("unchecked")
	RunSequenceTest() {
		doAnswer(call -> {
			ToIntFunction<Object> itemsOut = call.getArgument(4);
			return step(call.getArgument(1), call.getArgument(3),
					result -> new StepOutcome(StepStatus.SUCCEEDED, itemsOut.applyAsInt(result), null));
		}).when(recorder).step(any(), any(), any(), any(), any(ToIntFunction.class));
		doAnswer(call -> step(call.getArgument(1), call.getArgument(3), call.getArgument(4))).when(recorder)
			.step(any(), any(), any(), any(), any(Function.class));
		doAnswer(call -> events.add("count " + call.getArgument(1))).when(recorder).count(any(), any());
		doAnswer(call -> events.add("source " + call.<Source>getArgument(1).value() + " "
				+ call.<SourceFetchStatus>getArgument(2).value() + " " + call.getArgument(3) + " " + call.getArgument(4)
				+ (call.getArgument(5) == null ? "" : " " + call.getArgument(5))))
			.when(recorder)
			.source(any(), any(), any(), anyInt(), anyInt(), any());
		doAnswer(call -> events.add("error " + call.getArgument(1))).when(recorder).error(any(), anyString());
		when(recorder.finish(any(), any(), isNull())).thenAnswer(call -> {
			events.add("finish " + call.<RunStatus>getArgument(1).value());
			return true;
		});
		fetches(WYKOP_ONLY);
		when(store.store(any())).thenReturn(new StoredBatch(RUN, NEW_POSTS));
		when(selection.fromStored(any())).thenReturn(new PendingAssessments(RUN, NEW_POSTS));
		when(selection.fromTargets(any())).thenReturn(new PendingAssessments(RUN, NEW_POSTS));
		when(dispatcher.dispatch(any(), any())).thenReturn(new AssessedBatch(RUN, 2, 0, 0, List.of(), null));
	}

	@Test
	void aRangeRunFetchesStoresSelectsAssessesAndSummarizesInOrderEachOnce() {
		RunSummary summary = sequence.run(range());

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.SUCCEEDED));
		assertThat(events).containsExactly("fetch:started", "source wykop succeeded 3 1", "fetch:succeeded:3",
				"store:started", "store:succeeded:2", "count " + Counters.stored(2), "select:started",
				"select:succeeded:2", "assess:started", "assess:succeeded:2", "summarize:started",
				"summarize:succeeded:1", "finish succeeded");
		verify(ingest).fetch(eq(new RangeTarget(RUN, FROM, TO, List.of(Source.WYKOP))), any());
		verify(store).store(WYKOP_ONLY);
		verify(selection).fromStored(new StoredBatch(RUN, NEW_POSTS));
		verify(dispatcher).dispatch(RUN, NEW_POSTS);
		verify(selection, never()).fromTargets(any());
	}

	@Test
	void aRetryRunSelectsTheLeftoversAssessesAndSummarizes() {
		RunSummary summary = sequence.run(new RunRequest(RUN, RunKind.RETRY, null, null, List.of(), null));

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.SUCCEEDED));
		assertThat(events).containsExactly("select:started", "select:succeeded:2", "assess:started",
				"assess:succeeded:2", "summarize:started", "summarize:succeeded:1", "finish succeeded");
		verify(selection).fromTargets(new SelectionTarget(RUN, RunKind.RETRY, List.of()));
		verify(ingest, never()).fetch(any(), any());
		verify(store, never()).store(any());
	}

	@Test
	void aPostsRunSelectsTheGivenPostsAssessesAndSummarizes() {
		RunSummary summary = sequence.run(new RunRequest(RUN, RunKind.POSTS, null, null, List.of(), NEW_POSTS));

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.SUCCEEDED));
		assertThat(events).containsExactly("select:started", "select:succeeded:2", "assess:started",
				"assess:succeeded:2", "summarize:started", "summarize:succeeded:1", "finish succeeded");
		verify(selection).fromTargets(new SelectionTarget(RUN, RunKind.POSTS, NEW_POSTS));
		verify(ingest, never()).fetch(any(), any());
	}

	@Test
	void eachSourcesResultIsWrittenInsideTheFetchStepInTheOrderTheSourcesEnded() {
		FetchedBatch both = new FetchedBatch(RUN, List.of(), 5, 1,
				List.of(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 3, 1, null),
						new SourceFetch(Source.REDDIT, SourceFetchStatus.SUCCEEDED, 2, 0, null)));
		fetches(both);

		RunSummary summary = sequence.run(range(Source.WYKOP, Source.REDDIT));

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.SUCCEEDED));
		assertThat(events.subList(0, 4)).containsExactly("fetch:started", "source wykop succeeded 3 1",
				"source reddit succeeded 2 0", "fetch:succeeded:5");
		// The step writes no sums of its own: the sources' results are the run's fetch counters.
		assertThat(events).filteredOn(event -> event.startsWith("count ")).containsExactly("count " + Counters.stored(2));
		verify(recorder, never()).error(any(), anyString());
	}

	@Test
	void aFailedSourceNextToACleanAssessmentMakesTheRunPartialWithTheSourcesSummary() {
		fetches(REDDIT_FAILED);

		RunSummary summary = sequence.run(range(Source.WYKOP, Source.REDDIT));

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.PARTIAL));
		assertThat(events).containsExactly("fetch:started", "source wykop succeeded 3 1",
				"source reddit failed 0 0 IllegalStateException: HTTP 503", "fetch:succeeded:3", "store:started",
				"store:succeeded:2", "count " + Counters.stored(2), "select:started", "select:succeeded:2",
				"assess:started", "assess:succeeded:2", "error reddit failed: IllegalStateException: HTTP 503",
				"summarize:started", "summarize:succeeded:1", "finish partial");
	}

	@Test
	void aCleanFetchLeavesThePartialAssessmentAsItWas() {
		ProcessingError error = new ProcessingError(NEW_POSTS.getFirst(), "IllegalStateException: boom");
		AssessedBatch assessed = new AssessedBatch(RUN, 1, 0, 0, List.of(error), null);
		when(dispatcher.dispatch(any(), any())).thenReturn(assessed);

		RunSummary summary = sequence.run(range());

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.PARTIAL));
		assertThat(events).contains("error " + assessed.errorSummary().orElseThrow(), "finish partial");
	}

	@Test
	void theSourcesSummaryAndTheAssessmentsErrorAreJoined() {
		fetches(REDDIT_FAILED);
		ProcessingError error = new ProcessingError(NEW_POSTS.getFirst(), "IllegalStateException: boom");
		AssessedBatch assessed = new AssessedBatch(RUN, 1, 0, 0, List.of(error), null);
		when(dispatcher.dispatch(any(), any())).thenReturn(assessed);

		RunSummary summary = sequence.run(range(Source.WYKOP, Source.REDDIT));

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.PARTIAL));
		assertThat(events).contains("error reddit failed: IllegalStateException: HTTP 503 | "
				+ assessed.errorSummary().orElseThrow(), "finish partial");
	}

	@Test
	void aFetchWithoutPostsFailsItsStepWithNoFurtherStep() {
		List<SourceFetch> outcomes = List.of(new SourceFetch(Source.WYKOP, SourceFetchStatus.EMPTY, 5, 5, null),
				new SourceFetch(Source.REDDIT, SourceFetchStatus.FAILED, 0, 0, "IllegalStateException: HTTP 503"));
		NoPostsFetchedException empty = new NoPostsFetchedException(outcomes);
		doAnswer(call -> {
			outcomes.forEach(call.<Consumer<SourceFetch>>getArgument(1));
			throw empty;
		}).when(ingest).fetch(any(), any());

		assertThatThrownBy(() -> sequence.run(range(Source.WYKOP, Source.REDDIT))).isSameAs(empty);

		// The sources' results are written as they ended; no sums before the run fails.
		assertThat(events).containsExactly("fetch:started", "source wykop empty 5 5",
				"source reddit failed 0 0 IllegalStateException: HTTP 503", "fetch:failed:" + empty.getMessage());
		verify(recorder, never()).count(any(), any());
		verify(store, never()).store(any());
	}

	@Test
	void anAssessStepTheDispatcherStoppedIsFailedWithItsReasonAndSetsTheRunsError() {
		String stoppedBy = "the LLM provider refused the configuration: HTTP 401";
		when(dispatcher.dispatch(any(), any())).thenReturn(new AssessedBatch(RUN, 1, 0, 0, List.of(), stoppedBy));

		RunSummary summary = sequence.run(new RunRequest(RUN, RunKind.RETRY, null, null, List.of(), null));

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.FAILED));
		assertThat(events).containsExactly("select:started", "select:succeeded:2", "assess:started",
				"assess:failed:1:" + stoppedBy, "error stopped, the remaining posts were put back: " + stoppedBy,
				"summarize:started", "summarize:succeeded:1", "finish failed");
	}

	@Test
	void theSummaryStepClosesBeforeTheFinishAndARunInterruptedMeanwhileStaysInterrupted() {
		doAnswer(call -> {
			events.add("finish refused");
			return false;
		}).when(recorder).finish(any(), any(), isNull());

		RunSummary summary = sequence.run(range());

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.INTERRUPTED));
		assertThat(events.subList(events.size() - 2, events.size())).containsExactly("summarize:succeeded:1",
				"finish refused");
	}

	@Test
	void aStepThatThrowsEndsTheRunWithItsExceptionAndNoFurtherStep() {
		IllegalStateException failure = new IllegalStateException("disk full");
		when(store.store(any())).thenThrow(failure);

		assertThatThrownBy(() -> sequence.run(range())).isSameAs(failure);

		// Each step once: the failed one is not repeated, and nothing after it starts.
		assertThat(events).containsExactly("fetch:started", "source wykop succeeded 3 1", "fetch:succeeded:3",
				"store:started", "store:failed:disk full");
		verify(ingest).fetch(any(), any());
		verify(store).store(any());
		verify(selection, never()).fromStored(any());
		verify(dispatcher, never()).dispatch(any(), any());
		verify(recorder, never()).finish(any(), any(), any());
	}

	@Test
	void aStepRefusedBecauseTheRunNoLongerRunsEndsTheSequence() {
		refused.add(RunStep.ASSESS);

		assertThatThrownBy(() -> sequence.run(range())).isInstanceOf(RunNoLongerRunningException.class);

		assertThat(events).last().isEqualTo("select:succeeded:2");
		verify(dispatcher, never()).dispatch(any(), any());
		verify(recorder, never()).finish(any(), any(), any());
	}

	private static RunRequest range(Source... sources) {
		List<Source> named = sources.length == 0 ? List.of(Source.WYKOP) : List.of(sources);
		return new RunRequest(RUN, RunKind.RANGE, FROM, TO, named, null);
	}

	/** The fetch reports each source's result of {@code batch} as {@link SourceIngest} does, then returns it. */
	private void fetches(FetchedBatch batch) {
		// doAnswer: when(…) would call the answer stubbed before with no consumer.
		doAnswer(call -> {
			batch.sources().forEach(call.<Consumer<SourceFetch>>getArgument(1));
			return batch;
		}).when(ingest).fetch(any(), any());
	}

	/** As {@link RunRecorder#step}: refused, or the work and how it ended. */
	private Object step(RunStep step, Supplier<?> work, Function<Object, StepOutcome> outcome) {
		if (refused.contains(step)) {
			throw new RunNoLongerRunningException(RUN, step);
		}
		events.add(step.value() + ":started");
		Object result;
		try {
			result = work.get();
		}
		catch (RuntimeException ex) {
			events.add(step.value() + ":failed:" + ex.getMessage());
			throw ex;
		}
		StepOutcome end = outcome.apply(result);
		events.add(step.value() + ":" + end.status().value() + ":" + end.itemsOut()
				+ (end.error() == null ? "" : ":" + end.error()));
		return result;
	}

}
