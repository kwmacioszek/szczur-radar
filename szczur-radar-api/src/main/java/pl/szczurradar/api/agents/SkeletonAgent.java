package pl.szczurradar.api.agents;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;

/**
 * Skeleton agent: proves that the Embabel platform starts inside this application and runs a typed
 * action chain. It never calls an LLM. F-02 replaces it with the assessment agent.
 *
 * <p>Code here that runs work in parallel must pass its own executor: on Java 25 the container's
 * CPU limit may leave {@code availableProcessors() == 1} (see szczur-radar-api/README.md).
 */
@Agent(description = "Echoes a ping as a pong without calling an LLM (platform smoke check)")
public class SkeletonAgent {

	/** Input of the skeleton action. */
	public record Ping(String message) {
	}

	/** Output of the skeleton action. */
	public record Pong(String message) {
	}

	@Action
	@AchievesGoal(description = "A ping was answered with a pong")
	public Pong answer(Ping ping) {
		return new Pong("pong: " + ping.message());
	}

}
