package pl.szczurradar.api.pipeline;

import java.util.List;
import java.util.UUID;

/**
 * {@link PipelineRunService#ownRuns()} for the ITs outside this package. A run's terminal status is
 * committed before its queue thread notifies the admins and lets it go, so a test that waits only for
 * no {@code queued}/{@code running} row returns too early; it waits for this list to be empty too.
 */
public final class OwnRuns {

	private OwnRuns() {
	}

	/** The runs the service queued and its queue thread has not let go yet. */
	public static List<UUID> of(PipelineRunService service) {
		return service.ownRuns();
	}

}
