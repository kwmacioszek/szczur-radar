package pl.szczurradar.api.pipeline.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import pl.szczurradar.api.pipeline.PipelineRunService;
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
import pl.szczurradar.api.pipeline.state.RunRecorder.StepOutcome;
import pl.szczurradar.api.pipeline.step.AssessmentSelection;
import pl.szczurradar.api.pipeline.step.NoPostsFetchedException;
import pl.szczurradar.api.pipeline.step.PostStore;
import pl.szczurradar.api.pipeline.step.SourceIngest;

/**
 * One pipeline run, its steps one after another on the caller's thread ({@link PipelineRunService}'s
 * queue thread {@code pipeline-run}), so interrupting that thread reaches the step it is in:
 *
 * <pre>
 * RANGE:         fetch → store → select (the posts it stored first) → assess → summarize
 * RETRY / POSTS: select (the leftovers / the given posts)           → assess → summarize
 * </pre>
 *
 * Every step is a call to a plain service, recorded as a step ({@link RunRecorder#step}); the logic
 * lives in those services. A step with side effects runs once: a step's exception leaves {@link #run}
 * as it is, without a further step, and retries of a call live only below the step (e.g.
 * {@code llm/transport/TransportRetry}, a source's client). A step of a run that is no longer running
 * is not started ({@link RunNoLongerRunningException}).
 *
 * <p>A range run's fetch isolates its sources: a source that failed or gave no post is its own result,
 * written as soon as it ended, and the other sources' posts are stored and assessed. The fetch step fails
 * only when no source gave a post ({@link NoPostsFetchedException}, the run {@code failed}); otherwise the
 * run's status is the assessment's, but {@code partial} when the assessment succeeded and a source did not.
 */
@Component
public class RunSequence {

	private final SourceIngest ingest;

	private final PostStore store;

	private final AssessmentSelection selection;

	private final AssessmentDispatcher dispatcher;

	private final RunRecorder recorder;

	RunSequence(SourceIngest ingest, PostStore store, AssessmentSelection selection, AssessmentDispatcher dispatcher,
			RunRecorder recorder) {
		this.ingest = ingest;
		this.store = store;
		this.selection = selection;
		this.dispatcher = dispatcher;
		this.recorder = recorder;
	}

	/**
	 * Runs {@code request}'s steps in order and returns its final status.
	 *
	 * @throws RuntimeException the exception of the step that failed, as it was thrown (not wrapped); no
	 *     step after it was started
	 */
	public RunSummary run(RunRequest request) {
		return switch (request.kind()) {
			case RANGE -> range(
					new RangeTarget(request.runId(), request.rangeFrom(), request.rangeTo(), request.sources()));
			case RETRY -> chosen(new SelectionTarget(request.runId(), RunKind.RETRY, List.of()));
			case POSTS -> chosen(new SelectionTarget(request.runId(), RunKind.POSTS, request.postIds()));
		};
	}

	/** The fetch's result goes on to the run's error and status. */
	private RunSummary range(RangeTarget target) {
		FetchedBatch fetched = fetch(target);
		return summarize(assess(selectFromStored(store(fetched)), fetched), fetched);
	}

	private RunSummary chosen(SelectionTarget target) {
		return summarize(assess(selectFromTargets(target), null), null);
	}

	/**
	 * Each source's result is written as soon as the source ended ({@link RunRecorder#source}: the run's
	 * counters, its activity and the source's row), inside the step, so a fetch over several sources shows
	 * progress after each and a fetch that fails has written what its sources gave. The step fails only
	 * when no source gave a post ({@link NoPostsFetchedException}).
	 */
	private FetchedBatch fetch(RangeTarget target) {
		UUID runId = target.runId();
		return recorder.step(runId, RunStep.FETCH, null,
				() -> ingest.fetch(target, outcome -> recorder.source(runId, outcome.source(), outcome.status(),
						outcome.fetched(), outcome.skipped(), outcome.error())),
				FetchedBatch::fetched);
	}

	private StoredBatch store(FetchedBatch batch) {
		StoredBatch stored = recorder.step(batch.runId(), RunStep.STORE, batch.posts().size(), () -> store.store(batch),
				(StoredBatch result) -> result.newPostIds().size());
		recorder.count(batch.runId(), RunRecorder.Counters.stored(stored.newPostIds().size()));
		return stored;
	}

	private PendingAssessments selectFromStored(StoredBatch batch) {
		return recorder.step(batch.runId(), RunStep.SELECT, batch.newPostIds().size(), () -> selection.fromStored(batch),
				RunSequence::selected);
	}

	private PendingAssessments selectFromTargets(SelectionTarget target) {
		return recorder.step(target.runId(), RunStep.SELECT, null, () -> selection.fromTargets(target),
				RunSequence::selected);
	}

	/**
	 * The dispatcher counts each post as it finishes (the run's progress), so the step counts nothing
	 * itself; a dispatch the dispatcher stopped ({@link AssessedBatch#stoppedBy}) closes the step
	 * {@code failed} with that reason, though it returned. The run's error is the fetch's summary of the
	 * sources that did not succeed and the assessment's, joined with {@code " | "}, when either has one.
	 *
	 * @param fetched the range run's fetch; {@code null} for a run that fetches nothing
	 */
	private AssessedBatch assess(PendingAssessments pending, @Nullable FetchedBatch fetched) {
		AssessedBatch assessed = recorder.step(pending.runId(), RunStep.ASSESS, pending.postIds().size(),
				() -> dispatcher.dispatch(pending.runId(), pending.postIds()), RunSequence::assessed);
		List<String> errors = new ArrayList<>(2);
		if (fetched != null && fetched.degraded()) {
			errors.add(fetched.summary());
		}
		assessed.errorSummary().ifPresent(errors::add);
		if (!errors.isEmpty()) {
			recorder.error(pending.runId(), String.join(" | ", errors));
		}
		return assessed;
	}

	/**
	 * The run's status is the assessment's, except that a range run whose assessment succeeded while a
	 * source failed or gave no post ({@link FetchedBatch#degraded}) is {@code partial}. The step closes
	 * before the run's final status is written, so whoever sees the run ended (the run page, the list, the
	 * cron) sees every step closed too.
	 *
	 * @param fetched the range run's fetch; {@code null} for a run that fetches nothing
	 */
	private RunSummary summarize(AssessedBatch assessed, @Nullable FetchedBatch fetched) {
		RunStatus status = recorder.step(assessed.runId(), RunStep.SUMMARIZE, null, () -> status(assessed, fetched),
				(RunStatus summary) -> 1);
		if (!recorder.finish(assessed.runId(), status, null)) {
			// Interrupted meanwhile: the interruption stays.
			return new RunSummary(assessed.runId(), RunStatus.INTERRUPTED);
		}
		return new RunSummary(assessed.runId(), status);
	}

	private static RunStatus status(AssessedBatch assessed, @Nullable FetchedBatch fetched) {
		RunStatus status = assessed.status();
		return status == RunStatus.SUCCEEDED && fetched != null && fetched.degraded() ? RunStatus.PARTIAL : status;
	}

	private static int selected(PendingAssessments pending) {
		return pending.postIds().size();
	}

	/** The assess step's end: {@code failed} with the reason the dispatcher stopped, else {@code succeeded}. */
	private static StepOutcome assessed(AssessedBatch batch) {
		int itemsOut = batch.ok() + batch.failed();
		return batch.stoppedBy() == null ? new StepOutcome(StepStatus.SUCCEEDED, itemsOut, null)
				: new StepOutcome(StepStatus.FAILED, itemsOut, batch.stoppedBy());
	}

}
