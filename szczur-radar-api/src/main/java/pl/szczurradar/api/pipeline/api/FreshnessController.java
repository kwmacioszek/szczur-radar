package pl.szczurradar.api.pipeline.api;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.pipeline.run.PipelineRunRepository;

/**
 * When the data was last updated, for the label above the feed (FR-030). Any logged-in user
 * (deny-by-default in {@code SecurityConfig}), so it says nothing else about the runs: no ids, counts,
 * errors or who started them — the run history is the admin's ({@code /api/admin/pipeline-runs}).
 *
 * <p>An update is the end of a range run, the daily one or an admin's, that succeeded or ended
 * partial ({@link PipelineRunRepository#findLastUpdate()}): only such a run brings new posts in.
 * FR-030 shows only the last successful update, never a failed one, so a failed or interrupted run
 * leaves the date as it was. Of those runs the one over the newest days counts, so an admin's run over
 * older days leaves it too.
 */
@RestController
class FreshnessController {

	private final PipelineRunRepository runs;

	FreshnessController(PipelineRunRepository runs) {
		this.runs = runs;
	}

	@GetMapping("/api/freshness")
	Freshness freshness() {
		return new Freshness(runs.findLastUpdate().orElse(null));
	}

	/**
	 * The data's freshness.
	 *
	 * @param lastUpdatedAt the end of the last update; {@code null} before the first one
	 */
	record Freshness(@Nullable Instant lastUpdatedAt) {
	}

}
