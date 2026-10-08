package pl.szczurradar.api.pipeline.assess;

import java.util.List;
import java.util.UUID;

import pl.szczurradar.api.pipeline.RunFlow;

/**
 * Sends the assessments of a run and stores each result as it arrives. The assess step talks only to
 * this, so a provider's Batch API can replace the concurrent implementation without changing the
 * steps.
 */
public interface AssessmentDispatcher {

	/**
	 * Assesses {@code postIds} for {@code runId}: each post is taken for the run, assessed and its
	 * result stored in its own transaction, or put back unchanged on a technical error.
	 */
	RunFlow.AssessedBatch dispatch(UUID runId, List<UUID> postIds);

}
