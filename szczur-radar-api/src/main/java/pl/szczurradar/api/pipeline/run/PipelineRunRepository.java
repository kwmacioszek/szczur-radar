package pl.szczurradar.api.pipeline.run;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Pipeline runs. Status and counters change only through conditional updates, never a blind save. */
public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

	/** Whether a run is queued or running (then a new request is refused). */
	default boolean existsActive() {
		return existsByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING));
	}

	/** Whether a run is running. */
	default boolean existsRunning() {
		return existsByStatusIn(List.of(RunStatus.RUNNING));
	}

	boolean existsByStatusIn(Collection<RunStatus> statuses);

	/** A page of runs, newest first; equal creation times are ordered by id (index {@code ix_pipeline_runs_created}). */
	default Page<PipelineRun> findPage(int page, int size) {
		return findAll(PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
	}

	/**
	 * When the newest data was last updated: among the {@link RunKind#RANGE} runs, the daily one or an
	 * admin's, that {@link RunStatus#SUCCEEDED succeeded} or ended {@link RunStatus#PARTIAL partial}
	 * (they fetched and stored their range; only some posts were not assessed), the end of the one whose
	 * range reaches furthest, and of those the latest. An admin's run over an older range therefore
	 * leaves the date as it was, while a run over the same newest days moves it. Runs over leftovers or
	 * chosen posts fetch nothing new, so they never count; neither does a failed, interrupted or
	 * unfinished run. Empty before the first such run.
	 */
	default Optional<Instant> findLastUpdate() {
		return findFinishedAtOfNewestRange(RunKind.RANGE, List.of(RunStatus.SUCCEEDED, RunStatus.PARTIAL));
	}

	@Query("""
			select r.finishedAt from PipelineRun r
			where r.kind = :kind and r.status in :statuses and r.finishedAt is not null
			order by r.rangeTo desc, r.finishedAt desc
			limit 1""")
	Optional<Instant> findFinishedAtOfNewestRange(RunKind kind, Collection<RunStatus> statuses);

}
