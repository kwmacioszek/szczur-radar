package pl.szczurradar.api.pipeline;

import java.util.List;
import java.util.stream.Collectors;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;

import pl.szczurradar.api.pipeline.RunFlow.AssessedBatch;
import pl.szczurradar.api.pipeline.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.RunFlow.PendingAssessments;
import pl.szczurradar.api.pipeline.RunFlow.RangeTarget;
import pl.szczurradar.api.pipeline.RunFlow.RouteRun;
import pl.szczurradar.api.pipeline.RunFlow.RunRequest;
import pl.szczurradar.api.pipeline.RunFlow.RunSummary;
import pl.szczurradar.api.pipeline.RunFlow.SelectionTarget;
import pl.szczurradar.api.pipeline.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.assess.AssessmentDispatcher;

/**
 * One pipeline run on the Embabel platform, planned deterministically (GOAP) from the types in
 * {@link RunFlow}: no LLM decides anything here. Every action is a few lines — a call to a plain
 * service and its recorded step — so the logic is tested without Embabel, and the graph once
 * ({@code PipelineAgentIT}). For any state of the blackboard exactly one action can run.
 *
 * <p>Started by {@link PipelineRunService} through
 * {@code AgentInvocation.create(agentPlatform, RunSummary.class)}; the run's durable state is in the
 * database, not in Embabel.
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

	@Action
	public RouteRun route(RunRequest request) {
		return switch (request.kind()) {
			case RANGE -> new RouteRun(
					new RangeTarget(request.runId(), request.rangeFrom(), request.rangeTo(), request.sources()), null);
			case RETRY -> new RouteRun(null, new SelectionTarget(request.runId(), RunKind.RETRY, List.of()));
			case POSTS -> new RouteRun(null, new SelectionTarget(request.runId(), RunKind.POSTS, request.postIds()));
		};
	}

	@Action
	public FetchedBatch fetch(RangeTarget target) {
		FetchedBatch batch = recorder.step(target.runId(), RunStep.FETCH, null, () -> ingest.fetch(target),
				FetchedBatch::fetched);
		recorder.count(target.runId(), RunRecorder.Counters.fetched(batch.fetched(), batch.skipped()));
		return batch;
	}

	@Action
	public StoredBatch store(FetchedBatch batch) {
		StoredBatch stored = recorder.step(batch.runId(), RunStep.STORE, batch.posts().size(), () -> store.store(batch),
				result -> result.newPostIds().size());
		recorder.count(batch.runId(), RunRecorder.Counters.stored(stored.newPostIds().size()));
		return stored;
	}

	@Action
	public PendingAssessments selectFromStored(StoredBatch batch) {
		return recorder.step(batch.runId(), RunStep.SELECT, batch.newPostIds().size(), () -> selection.fromStored(batch),
				pending -> pending.postIds().size());
	}

	@Action
	public PendingAssessments selectFromTargets(SelectionTarget target) {
		return recorder.step(target.runId(), RunStep.SELECT, null, () -> selection.fromTargets(target),
				pending -> pending.postIds().size());
	}

	@Action
	public AssessedBatch assess(PendingAssessments pending) {
		AssessedBatch assessed = recorder.step(pending.runId(), RunStep.ASSESS, pending.postIds().size(),
				() -> dispatcher.dispatch(pending.runId(), pending.postIds()),
				batch -> batch.ok() + batch.failed());
		recorder.count(pending.runId(), RunRecorder.Counters.assessed(assessed));
		if (!assessed.processingErrors().isEmpty()) {
			recorder.error(pending.runId(), assessed.processingErrors().size() + " post(s) with a technical error, no"
					+ " assessment stored: " + assessed.processingErrors()
						.stream()
						.map(error -> error.postId() + " (" + error.message() + ")")
						.collect(Collectors.joining("; ")));
		}
		return assessed;
	}

	@Action
	@AchievesGoal(description = "The run was summarized and its final status written")
	public RunSummary summarize(AssessedBatch assessed) {
		return recorder.step(assessed.runId(), RunStep.SUMMARIZE, null, () -> {
			RunStatus status = assessed.status();
			if (!recorder.finish(assessed.runId(), status, null)) {
				// Interrupted meanwhile: the interruption stays.
				return new RunSummary(assessed.runId(), RunStatus.INTERRUPTED);
			}
			return new RunSummary(assessed.runId(), status);
		}, summary -> 1);
	}

}
