package pl.szczurradar.api.pipeline.flow;

import java.util.List;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.core.ActionRetryPolicy;

import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.pipeline.assess.AssessmentDispatcher;
import pl.szczurradar.api.pipeline.flow.RunFlow.AssessedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.PendingAssessments;
import pl.szczurradar.api.pipeline.flow.RunFlow.RangeTarget;
import pl.szczurradar.api.pipeline.flow.RunFlow.RouteRun;
import pl.szczurradar.api.pipeline.flow.RunFlow.RunRequest;
import pl.szczurradar.api.pipeline.flow.RunFlow.RunSummary;
import pl.szczurradar.api.pipeline.flow.RunFlow.SelectionTarget;
import pl.szczurradar.api.pipeline.flow.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.state.RunRecorder;
import pl.szczurradar.api.pipeline.step.AssessmentSelection;
import pl.szczurradar.api.pipeline.step.PostStore;
import pl.szczurradar.api.pipeline.step.SourceIngest;

/**
 * One pipeline run on the Embabel platform, planned deterministically (GOAP) from the types in
 * {@link RunFlow}: no LLM decides anything here. Every action is a few lines — a call to a plain
 * service and its recorded step — so the logic is tested without Embabel, and the graph once
 * ({@code PipelineAgentIT}). For any state of the blackboard exactly one action can run.
 *
 * <p>Started by {@link PipelineRunService} through
 * {@code AgentInvocation.create(agentPlatform, RunSummary.class)}; the run's durable state is in the
 * database, not in Embabel.
 *
 * <p>Every action fires once ({@link ActionRetryPolicy#FIRE_ONCE}): Embabel's default policy retries
 * an action after most exceptions, with growing pauses, which would repeat a failed fetch, store or
 * assessment step and record it again. A failed step fails the run; retries of a call live only
 * below it (e.g. {@code llm/transport/TransportRetry}).
 */
@Agent(description = "Runs one Szczur Radar pipeline run: fetch and store a range of the sources' posts, or pick"
		+ " leftovers or given posts, then assess them and summarize the run")
public class PipelineAgent {

	private final SourceIngest ingest;

	private final PostStore store;

	private final AssessmentSelection selection;

	private final AssessmentDispatcher dispatcher;

	private final RunRecorder recorder;

	PipelineAgent(SourceIngest ingest, PostStore store, AssessmentSelection selection, AssessmentDispatcher dispatcher,
			RunRecorder recorder) {
		this.ingest = ingest;
		this.store = store;
		this.selection = selection;
		this.dispatcher = dispatcher;
		this.recorder = recorder;
	}

	@Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
	public RouteRun route(RunRequest request) {
		return switch (request.kind()) {
			case RANGE -> new RouteRun(
					new RangeTarget(request.runId(), request.rangeFrom(), request.rangeTo(), request.sources()), null);
			case RETRY -> new RouteRun(null, new SelectionTarget(request.runId(), RunKind.RETRY, List.of()));
			case POSTS -> new RouteRun(null, new SelectionTarget(request.runId(), RunKind.POSTS, request.postIds()));
		};
	}

	@Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
	public FetchedBatch fetch(RangeTarget target) {
		FetchedBatch batch = recorder.step(target.runId(), RunStep.FETCH, null, () -> ingest.fetch(target),
				FetchedBatch::fetched);
		recorder.count(target.runId(), RunRecorder.Counters.fetched(batch.fetched(), batch.skipped()));
		return batch;
	}

	@Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
	public StoredBatch store(FetchedBatch batch) {
		StoredBatch stored = recorder.step(batch.runId(), RunStep.STORE, batch.posts().size(), () -> store.store(batch),
				result -> result.newPostIds().size());
		recorder.count(batch.runId(), RunRecorder.Counters.stored(stored.newPostIds().size()));
		return stored;
	}

	@Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
	public PendingAssessments selectFromStored(StoredBatch batch) {
		return recorder.step(batch.runId(), RunStep.SELECT, batch.newPostIds().size(), () -> selection.fromStored(batch),
				pending -> pending.postIds().size());
	}

	@Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
	public PendingAssessments selectFromTargets(SelectionTarget target) {
		return recorder.step(target.runId(), RunStep.SELECT, null, () -> selection.fromTargets(target),
				pending -> pending.postIds().size());
	}

	/** The dispatcher counts each post as it finishes (the run's progress), so the step counts nothing itself. */
	@Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
	public AssessedBatch assess(PendingAssessments pending) {
		AssessedBatch assessed = recorder.step(pending.runId(), RunStep.ASSESS, pending.postIds().size(),
				() -> dispatcher.dispatch(pending.runId(), pending.postIds()),
				batch -> batch.ok() + batch.failed());
		assessed.errorSummary().ifPresent(summary -> recorder.error(pending.runId(), summary));
		return assessed;
	}

	/**
	 * The step closes before the run's final status is written, so whoever sees the run ended (the
	 * run page, the list, the cron) sees every step closed too.
	 */
	@Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
	@AchievesGoal(description = "The run was summarized and its final status written")
	public RunSummary summarize(AssessedBatch assessed) {
		RunStatus status = recorder.step(assessed.runId(), RunStep.SUMMARIZE, null, assessed::status, summary -> 1);
		if (!recorder.finish(assessed.runId(), status, null)) {
			// Interrupted meanwhile: the interruption stays.
			return new RunSummary(assessed.runId(), RunStatus.INTERRUPTED);
		}
		return new RunSummary(assessed.runId(), status);
	}

}
