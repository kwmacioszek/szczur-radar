package pl.szczurradar.api.agents.assessor;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.core.ActionRetryPolicy;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;

/**
 * The assessment step for S-02 on the Embabel platform: one entry in, one {@link AssessmentResult}
 * out. The logic lives in {@link Assessor}, a plain service; the model is called through
 * {@code llm/LlmClient}, never through Embabel's {@code createObject} (lenient parsing, semantic
 * retries).
 *
 * <p>The action fires once ({@link ActionRetryPolicy#FIRE_ONCE}): {@link Assessor#assess} throws when the
 * provider is unavailable or refuses the configuration, and Embabel's default policy would repeat the
 * paid call after such an exception. Retries live only in the client's transport.
 *
 * <p>Code here that runs work in parallel must pass its own executor: on Java 25 the container's
 * CPU limit may leave {@code availableProcessors() == 1} (see szczur-radar-api/README.md).
 */
@Agent(description = "Assesses one investment forum entry against the LLM assessment contract V2")
public class AssessorAgent {

	private final Assessor assessor;

	public AssessorAgent(Assessor assessor) {
		this.assessor = assessor;
	}

	@Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
	@AchievesGoal(description = "The entry was assessed: a valid V2 output or a classified failure")
	public AssessmentResult assess(AssessmentInputV1 input) {
		return assessor.assess(input);
	}

}
