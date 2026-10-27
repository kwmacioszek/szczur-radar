package pl.szczurradar.api.pipeline.flow;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Component
public class RunSequence {

	private static final Logger log = LoggerFactory.getLogger(RunSequence.class);

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
		PendingAssessments pending = switch (request.kind()) {
			case RANGE -> selectFromStored(store(fetch(
					new RangeTarget(request.runId(), request.rangeFrom(), request.rangeTo(), request.sources()))));
			case RETRY -> selectFromTargets(new SelectionTarget(request.runId(), RunKind.RETRY, List.of()));
			case POSTS -> selectFromTargets(new SelectionTarget(request.runId(), RunKind.POSTS, request.postIds()));
		};
		return summarize(assess(pending));
	}

	/**
	 * A source without a post fails the step; its counters are written before the run fails, so the
	 * run's page shows what was fetched and skipped ({@link RunRecorder#count} needs a running run).
	 */
	private FetchedBatch fetch(RangeTarget target) {
		FetchedBatch batch;
		try {
			batch = recorder.step(target.runId(), RunStep.FETCH, null, () -> ingest.fetch(target), FetchedBatch::fetched);
		}
		catch (NoPostsFetchedException ex) {
			// A failed counter write must not replace the reason the run fails.
			try {
				recorder.count(target.runId(), RunRecorder.Counters.fetched(ex.fetched(), ex.skipped()));
			}
			catch (RuntimeException countFailure) {
				log.warn("The counters of run {} could not be written before its fetch failed", target.runId(),
						countFailure);
				ex.addSuppressed(countFailure);
			}
			throw ex;
		}
		recorder.count(target.runId(), RunRecorder.Counters.fetched(batch.fetched(), batch.skipped()));
		return batch;
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
	 * {@code failed} with that reason, though it returned.
	 */
	private AssessedBatch assess(PendingAssessments pending) {
		AssessedBatch assessed = recorder.step(pending.runId(), RunStep.ASSESS, pending.postIds().size(),
				() -> dispatcher.dispatch(pending.runId(), pending.postIds()), RunSequence::assessed);
		assessed.errorSummary().ifPresent(summary -> recorder.error(pending.runId(), summary));
		return assessed;
	}

	/**
	 * The step closes before the run's final status is written, so whoever sees the run ended (the
	 * run page, the list, the cron) sees every step closed too.
	 */
	private RunSummary summarize(AssessedBatch assessed) {
		RunStatus status = recorder.step(assessed.runId(), RunStep.SUMMARIZE, null, assessed::status,
				(RunStatus summary) -> 1);
		if (!recorder.finish(assessed.runId(), status, null)) {
			// Interrupted meanwhile: the interruption stays.
			return new RunSummary(assessed.runId(), RunStatus.INTERRUPTED);
		}
		return new RunSummary(assessed.runId(), status);
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
