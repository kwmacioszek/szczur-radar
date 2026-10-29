package pl.szczurradar.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.pipeline.run.MissingDays;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.sources.Source;

/**
 * {@code V12__pipeline_run_sources.sql} fills the history from each range run's last {@code fetch} step:
 * the database is emptied and migrated to V11 (this context leaves Flyway off, as the server does), runs
 * from before V12 are inserted with SQL, then every migration is applied and the rows and counters are
 * read — and the days without data, through the production queries ({@link PipelineRunRepository}) and
 * {@link MissingDays}, assembled as the {@code missing-days} route assembles them.
 * {@link pl.szczurradar.api.MigrationsIT} cannot show this: it migrates everything when its context starts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class V12BackfillIT {

	/** Today for the days without data: the window is 3–16 October 2026. */
	private static final LocalDate TODAY = LocalDate.parse("2026-10-18");

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PipelineRunRepository runs;

	private UUID admin;

	@BeforeEach
	void migrateAnEmptyDatabaseToV11() {
		// Each test starts from an empty database: V12 runs once per database.
		Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).target("11").load().migrate();
		admin = jdbc.queryForObject("INSERT INTO users (email, role) VALUES ('backfill@example.com', 'admin')"
				+ " RETURNING id", UUID.class);
	}

	@Test
	void eachRangeRunGetsItsSourcesResultFromItsLastFetchStep() {
		UUID withPosts = range(admin, "succeeded", 16, 2);
		fetch(withPosts, "succeeded", null, "2026-10-02T00:31:00Z");
		// Before S-08 a day without a post ended succeeded.
		UUID emptyBeforeS08 = range(admin, "succeeded", 0, 0);
		fetch(emptyBeforeS08, "succeeded", null, "2026-10-03T00:31:00Z");
		UUID emptyAfterS08 = range(admin, "failed", 5, 5);
		fetch(emptyAfterS08, "failed", "wykop gave no post to store (fetched 5, skipped 5)", "2026-10-04T00:31:00Z");
		UUID sourceDown = range(admin, "failed", 0, 0);
		fetch(sourceDown, "failed", "WykopApiException: Wykop answered 503", "2026-10-05T00:31:00Z");
		UUID interrupted = range(admin, "interrupted", 0, 0);
		fetch(interrupted, "interrupted", null, "2026-10-06T00:31:00Z");
		UUID withoutStep = range(admin, "failed", 0, 0);
		UUID retry = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, finished_at)"
				+ " VALUES ('retry', 'succeeded', ?, now()) RETURNING id", UUID.class, admin);

		Flyway.configure().dataSource(dataSource).load().migrate();

		assertThat(result(withPosts)).isEqualTo("wykop:succeeded:16:2::2026-10-02T00:31:00Z");
		assertThat(result(emptyBeforeS08)).isEqualTo("wykop:empty:0:0::2026-10-03T00:31:00Z");
		assertThat(result(emptyAfterS08)).isEqualTo("wykop:empty:5:5::2026-10-04T00:31:00Z");
		assertThat(result(sourceDown))
			.isEqualTo("wykop:failed:0:0:WykopApiException: Wykop answered 503:2026-10-05T00:31:00Z");
		for (UUID run : new UUID[] { interrupted, withoutStep, retry }) {
			assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline_run_sources WHERE run_id = ?", Integer.class,
					run)).as("rows of run %s", run).isZero();
		}
		assertThat(counters(withPosts)).isEqualTo(Map.of("sources_failed", 0, "sources_empty", 0));
		assertThat(counters(emptyBeforeS08)).isEqualTo(Map.of("sources_failed", 0, "sources_empty", 1));
		assertThat(counters(emptyAfterS08)).isEqualTo(Map.of("sources_failed", 0, "sources_empty", 1));
		assertThat(counters(sourceDown)).isEqualTo(Map.of("sources_failed", 1, "sources_empty", 0));
		for (UUID run : new UUID[] { interrupted, withoutStep, retry }) {
			assertThat(counters(run)).as("counters of run %s", run)
				.isEqualTo(Map.of("sources_failed", 0, "sources_empty", 0));
		}
	}

	@Test
	void theHistoryCoversOnlyTheDaysWhoseFetchGavePosts() {
		night("2026-10-14", 0, 0);
		night("2026-10-15", 16, 2);
		night("2026-10-16", 0, 0);

		Flyway.configure().dataSource(dataSource).load().migrate();

		assertThat(runs.findFirstCoveredFrom(List.of(Source.WYKOP)))
			.isEqualTo(Map.of(Source.WYKOP, warsawStart(LocalDate.parse("2026-10-15"))));
		assertThat(missingDays().days())
			.containsExactly(new MissingDays.Day(LocalDate.parse("2026-10-16"), List.of(Source.WYKOP)));
	}

	@Test
	void anotherFetchWithPostsCoversAnEmptyDay() {
		night("2026-10-14", 0, 0);
		night("2026-10-15", 16, 2);
		night("2026-10-16", 0, 0);
		night("2026-10-16", 9, 1);

		Flyway.configure().dataSource(dataSource).load().migrate();

		assertThat(missingDays().days()).isEmpty();
	}

	@Test
	void onlyEmptyFetchesCoverNothingAndStartNoWindow() {
		night("2026-10-14", 0, 0);
		night("2026-10-15", 3, 3);
		night("2026-10-16", 0, 0);

		Flyway.configure().dataSource(dataSource).load().migrate();

		assertThat(runs.findFirstCoveredFrom(List.of(Source.WYKOP))).isEmpty();
		assertThat(missingDays().days()).isEmpty();
	}

	/** An ended range run of Wykop over 1 October 2026 with its counters, as V11 knew it. */
	private UUID range(UUID admin, String status, int fetched, int skipped) {
		return range(admin, LocalDate.parse("2026-10-01"), status, fetched, skipped);
	}

	/** An ended range run of Wykop over the Warsaw day {@code day} with its counters, as V11 knew it. */
	private UUID range(UUID admin, LocalDate day, String status, int fetched, int skipped) {
		return jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, range_from, range_to,"
				+ " sources, started_at, finished_at, fetched, skipped) VALUES ('range', ?, ?, CAST(? AS TIMESTAMPTZ),"
				+ " CAST(? AS TIMESTAMPTZ), ARRAY['wykop'], now(), now(), ?, ?) RETURNING id", UUID.class, status,
				admin, warsawStart(day).toString(), warsawStart(day.plusDays(1)).minusSeconds(1).toString(), fetched,
				skipped);
	}

	/**
	 * A succeeded run of the night over the Warsaw day {@code day} with a succeeded {@code fetch} step, as V11
	 * knew it (before S-08 a day without a post ended succeeded too).
	 */
	private void night(String day, int fetched, int skipped) {
		LocalDate date = LocalDate.parse(day);
		UUID run = range(admin, date, "succeeded", fetched, skipped);
		fetch(run, "succeeded", null, warsawStart(date.plusDays(2)).toString());
	}

	/** The days without data of {@link #TODAY} for Wykop, as the {@code missing-days} route assembles them. */
	private MissingDays missingDays() {
		List<MissingDays.Coverage> coverages = runs.findCoveringSince(warsawStart(MissingDays.windowStart(TODAY)))
			.stream()
			.map(range -> MissingDays.Coverage.of(range, PipelineRunService.ZONE))
			.toList();
		Map<Source, LocalDate> firstCovered = new EnumMap<>(Source.class);
		runs.findFirstCoveredFrom(List.of(Source.WYKOP))
			.forEach((source, from) -> firstCovered.put(source, LocalDate.ofInstant(from, PipelineRunService.ZONE)));
		return MissingDays.of(TODAY, List.of(Source.WYKOP), coverages, firstCovered);
	}

	private static Instant warsawStart(LocalDate day) {
		return day.atStartOfDay(PipelineRunService.ZONE).toInstant();
	}

	/** The run's fetch step in {@code status}, finished at {@code finishedAt} (an ISO instant). */
	private void fetch(UUID runId, String status, @Nullable String error, String finishedAt) {
		jdbc.update("INSERT INTO pipeline_run_steps (run_id, step, status, started_at, finished_at, error) VALUES"
				+ " (?, 'fetch', ?, CAST(? AS TIMESTAMPTZ) - interval '1 minute', CAST(? AS TIMESTAMPTZ), ?)", runId,
				status, finishedAt, finishedAt, error);
	}

	/** The run's only source result as {@code source:status:fetched:skipped:error:finished_at} (UTC). */
	private String result(UUID runId) {
		return jdbc.queryForObject("SELECT source || ':' || status || ':' || fetched || ':' || skipped || ':'"
				+ " || COALESCE(error, '') || ':' || to_char(finished_at AT TIME ZONE 'UTC',"
				+ " 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM pipeline_run_sources WHERE run_id = ?", String.class, runId);
	}

	private Map<String, Object> counters(UUID runId) {
		return jdbc.queryForMap("SELECT sources_failed, sources_empty FROM pipeline_runs WHERE id = ?", runId);
	}

}
