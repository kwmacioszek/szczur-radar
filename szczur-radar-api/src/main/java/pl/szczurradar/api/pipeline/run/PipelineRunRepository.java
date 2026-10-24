package pl.szczurradar.api.pipeline.run;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pl.szczurradar.api.sources.Source;

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

	/**
	 * Whether a queued or running run showed activity within {@code threshold}: its last activity, else
	 * its start, else its creation is no older, by the database's clock
	 * (a run's creation is written by the application's clock when it is saved; against the timeout the difference is negligible) — or, while one is queued or
	 * running, any run ended within {@code threshold} (a queue that just ended a run is alive, though the
	 * run behind it shows only its old creation until it starts). The same criterion keeps
	 * {@code RunRecorder.interruptOrphans} from taking the queue's runs as orphans; a run older than that
	 * is dead (no instance executes it) and the next request to start runs interrupts it.
	 */
	default boolean existsLiveActive(Duration threshold) {
		return existsWithActivityWithin(List.of(RunStatus.QUEUED.value(), RunStatus.RUNNING.value()),
				threshold.toMillis());
	}

	/** {@link #existsLiveActive}, with the statuses bound as stored; call that instead. */
	@Query(value = "SELECT EXISTS (SELECT 1 FROM pipeline_runs WHERE status IN (:statuses)"
			+ " AND (COALESCE(last_activity_at, started_at, created_at)"
			+ " >= now() - CAST(:millis AS BIGINT) * INTERVAL '1 millisecond'"
			+ " OR EXISTS (SELECT 1 FROM pipeline_runs f"
			+ " WHERE f.finished_at >= now() - CAST(:millis AS BIGINT) * INTERVAL '1 millisecond')))",
			nativeQuery = true)
	boolean existsWithActivityWithin(Collection<String> statuses, long millis);

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

	/**
	 * The runs that cover their days ({@link MissingDays}): {@link RunKind#RANGE} runs, the daily one or an
	 * admin's, that {@link RunStatus#SUCCEEDED succeeded} or ended {@link RunStatus#PARTIAL partial} (as
	 * {@link #findLastUpdate}), whose range ends at or after {@code since}.
	 */
	default List<PipelineRun> findCoveringSince(Instant since) {
		return findRangesEndingSince(RunKind.RANGE, List.of(RunStatus.SUCCEEDED, RunStatus.PARTIAL), since);
	}

	@Query("""
			select r from PipelineRun r
			where r.kind = :kind and r.status in :statuses and r.rangeTo >= :since""")
	List<PipelineRun> findRangesEndingSince(RunKind kind, Collection<RunStatus> statuses, Instant since);

	/**
	 * The start of the earliest covering run ({@link #findCoveringSince}) of each of {@code sources} in the
	 * whole history; a source without one is missing.
	 */
	default Map<Source, Instant> findFirstCoveredFrom(Collection<Source> sources) {
		Map<Source, Instant> first = new EnumMap<>(Source.class);
		for (Source source : sources) {
			findEarliestRangeFrom(RunKind.RANGE, List.of(RunStatus.SUCCEEDED, RunStatus.PARTIAL), source.value())
				.ifPresent(from -> first.put(source, from));
		}
		return first;
	}

	// array_position, not array_contains: Hibernate renders that as @> with a varchar[], which Postgres
	// refuses on the text[] column.
	@Query("""
			select min(r.rangeFrom) from PipelineRun r
			where r.kind = :kind and r.status in :statuses and array_position(r.sources, :source) is not null""")
	Optional<Instant> findEarliestRangeFrom(RunKind kind, Collection<RunStatus> statuses, String source);

}
