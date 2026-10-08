package pl.szczurradar.api.pipeline;

import java.util.List;
import java.util.UUID;

/** A posts run named a dead post; reviving one is outside S-02. */
public class PostDeadException extends RuntimeException {

	private final List<UUID> deadPostIds;

	public PostDeadException(List<UUID> deadPostIds) {
		super("dead posts cannot be retried: " + deadPostIds);
		this.deadPostIds = List.copyOf(deadPostIds);
	}

	public List<UUID> deadPostIds() {
		return deadPostIds;
	}

}
