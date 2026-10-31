package pl.szczurradar.api.pipeline.api;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.sources.Source;

/**
 * When each source's data was last updated, for the label above the feed (FR-030). Any logged-in user
 * (deny-by-default in {@code SecurityConfig}), so it says nothing else about the runs: no ids, counts,
 * errors or who started them — the run history is the admin's ({@code /api/admin/pipeline-runs}).
 *
 * <p>A source's update is the end of a range run, the daily one or an admin's, that succeeded or ended
 * partial with that source's result succeeded ({@link PipelineRunRepository#findLastUpdates()}): only such a
 * run brings the source's new posts in. FR-030 shows only the last successful update, never a failed one, so
 * a failed or interrupted run leaves every date as it was, and a source that failed or gave no post leaves
 * its own while another moves on (S-29). Of those runs the one over the newest days counts, so an admin's
 * run over older days leaves the date too. A source without an update is not listed: nothing here reads
 * which sources are configured.
 */
@RestController
class FreshnessController {

	private final PipelineRunRepository runs;

	FreshnessController(PipelineRunRepository runs) {
		this.runs = runs;
	}

	@GetMapping("/api/freshness")
	Freshness freshness() {
		return new Freshness(runs.findLastUpdates()
			.entrySet()
			.stream()
			.map(update -> new SourceUpdate(update.getKey(), update.getValue()))
			.toList());
	}

	/**
	 * The data's freshness.
	 *
	 * @param sources the sources with an update, in {@link Source} order; empty before the first one
	 */
	record Freshness(List<SourceUpdate> sources) {
	}

	/**
	 * One source's freshness.
	 *
	 * @param source the source
	 * @param lastUpdatedAt the end of its last update
	 */
	record SourceUpdate(Source source, Instant lastUpdatedAt) {
	}

}
