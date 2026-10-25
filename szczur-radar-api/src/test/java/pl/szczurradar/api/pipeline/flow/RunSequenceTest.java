package pl.szczurradar.api.pipeline.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;

import pl.szczurradar.api.pipeline.assess.AssessmentDispatcher;
import pl.szczurradar.api.pipeline.flow.RunFlow.AssessedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.PendingAssessments;
import pl.szczurradar.api.pipeline.flow.RunFlow.RangeTarget;
import pl.szczurradar.api.pipeline.flow.RunFlow.RunRequest;
import pl.szczurradar.api.pipeline.flow.RunFlow.RunSummary;
import pl.szczurradar.api.pipeline.flow.RunFlow.SelectionTarget;
import pl.szczurradar.api.pipeline.flow.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
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
 * services are mocks; the {@link RunRecorder} mock runs each step's work and writes down how the step
 * ended ({@code step:status:out[:error]}), its other writes, in one list of events.
 */
@ExtendWith(OutputCaptureExtension.class)
class RunSequenceTest {

	private static final UUID RUN = UUID.randomUUID();

	private static final Instant FROM = Instant.parse("2026-09-30T22:00:00Z");

	private static final Instant TO = Instant.parse("2026-10-01T21:59:59Z");

	private static final List<UUID> NEW_POSTS = List.of(UUID.randomUUID(), UUID.randomUUID());

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
		doAnswer(call -> events.add("error " + call.getArgument(1))).when(recorder).error(any(), anyString());
		when(recorder.finish(any(), any(), isNull())).thenAnswer(call -> {
			events.add("finish " + call.<RunStatus>getArgument(1).value());
			return true;
		});
		when(ingest.fetch(any())).thenReturn(new FetchedBatch(RUN, List.of(), 3, 1));
		when(store.store(any())).thenReturn(new StoredBatch(RUN, NEW_POSTS));
		when(selection.fromStored(any())).thenReturn(new PendingAssessments(RUN, NEW_POSTS));
		when(selection.fromTargets(any())).thenReturn(new PendingAssessments(RUN, NEW_POSTS));
		when(dispatcher.dispatch(any(), any())).thenReturn(new AssessedBatch(RUN, 2, 0, 0, List.of(), null));
	}

	@Test
	void aRangeRunFetchesStoresSelectsAssessesAndSummarizesInOrderEachOnce() {
		RunSummary summary = sequence.run(range());

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.SUCCEEDED));
		assertThat(events).containsExactly("fetch:succeeded:3", "count " + Counters.fetched(3, 1), "store:succeeded:2",
				"count " + Counters.stored(2), "select:succeeded:2", "assess:succeeded:2", "summarize:succeeded:1",
				"finish succeeded");
		verify(ingest).fetch(new RangeTarget(RUN, FROM, TO, List.of(Source.WYKOP)));
		verify(store).store(new FetchedBatch(RUN, List.of(), 3, 1));
		verify(selection).fromStored(new StoredBatch(RUN, NEW_POSTS));
		verify(dispatcher).dispatch(RUN, NEW_POSTS);
		verify(selection, never()).fromTargets(any());
	}

	@Test
	void aRetryRunSelectsTheLeftoversAssessesAndSummarizes() {
		RunSummary summary = sequence.run(new RunRequest(RUN, RunKind.RETRY, null, null, List.of(), null));

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.SUCCEEDED));
		assertThat(events).containsExactly("select:succeeded:2", "assess:succeeded:2", "summarize:succeeded:1",
				"finish succeeded");
		verify(selection).fromTargets(new SelectionTarget(RUN, RunKind.RETRY, List.of()));
		verify(ingest, never()).fetch(any());
		verify(store, never()).store(any());
	}

	@Test
	void aPostsRunSelectsTheGivenPostsAssessesAndSummarizes() {
		RunSummary summary = sequence.run(new RunRequest(RUN, RunKind.POSTS, null, null, List.of(), NEW_POSTS));

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.SUCCEEDED));
		assertThat(events).containsExactly("select:succeeded:2", "assess:succeeded:2", "summarize:succeeded:1",
				"finish succeeded");
		verify(selection).fromTargets(new SelectionTarget(RUN, RunKind.POSTS, NEW_POSTS));
		verify(ingest, never()).fetch(any());
	}

	@Test
	void aFetchWithoutPostsWritesItsCountersBeforeTheRunFails() {
		NoPostsFetchedException empty = new NoPostsFetchedException(
				List.of(new NoPostsFetchedException.EmptySource(Source.WYKOP, 5, 5)), 5, 5);
		when(ingest.fetch(any())).thenThrow(empty);

		assertThatThrownBy(() -> sequence.run(range())).isSameAs(empty);

		assertThat(events).containsExactly("fetch:failed:" + empty.getMessage(), "count " + Counters.fetched(5, 5));
		verify(store, never()).store(any());
	}

	@Test
	void aFailedCounterWriteBeforeTheFetchFailsIsAWarningSuppressedInTheReason(CapturedOutput output) {
		NoPostsFetchedException empty = new NoPostsFetchedException(
				List.of(new NoPostsFetchedException.EmptySource(Source.WYKOP, 0, 0)), 0, 0);
		CannotAcquireLockException countFailure = new CannotAcquireLockException("the database is gone");
		when(ingest.fetch(any())).thenThrow(empty);
		doAnswer(call -> {
			throw countFailure;
		}).when(recorder).count(any(), any());

		assertThatThrownBy(() -> sequence.run(range())).isSameAs(empty);

		assertThat(empty.getSuppressed()).containsExactly(countFailure);
		assertThat(output.getAll().lines().filter(line -> line.contains(" WARN "))).singleElement()
			.satisfies(line -> assertThat(line).contains("The counters of run " + RUN
					+ " could not be written before its fetch failed"));
	}

	@Test
	void anAssessStepTheDispatcherStoppedIsFailedWithItsReasonAndSetsTheRunsError() {
		String stoppedBy = "the LLM provider refused the configuration: HTTP 401";
		when(dispatcher.dispatch(any(), any())).thenReturn(new AssessedBatch(RUN, 1, 0, 0, List.of(), stoppedBy));

		RunSummary summary = sequence.run(new RunRequest(RUN, RunKind.RETRY, null, null, List.of(), null));

		assertThat(summary).isEqualTo(new RunSummary(RUN, RunStatus.FAILED));
		assertThat(events).containsExactly("select:succeeded:2", "assess:failed:1:" + stoppedBy,
				"error stopped, the remaining posts were put back: " + stoppedBy, "summarize:succeeded:1",
				"finish failed");
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
		assertThat(events).containsExactly("fetch:succeeded:3", "count " + Counters.fetched(3, 1),
				"store:failed:disk full");
		verify(ingest).fetch(any());
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

	private static RunRequest range() {
		return new RunRequest(RUN, RunKind.RANGE, FROM, TO, List.of(Source.WYKOP), null);
	}

	/** As {@link RunRecorder#step}: refused, or the work and how it ended. */
	private Object step(RunStep step, Supplier<?> work, Function<Object, StepOutcome> outcome) {
		if (refused.contains(step)) {
			throw new RunNoLongerRunningException(RUN, step);
		}
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
