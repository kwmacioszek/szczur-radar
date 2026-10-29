package pl.szczurradar.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.TestcontainersConfiguration;

/**
 * {@code V12__pipeline_run_sources.sql} fills the history from each range run's last {@code fetch} step:
 * the database is migrated to V11 (this context leaves Flyway off, as the server does), runs from before
 * V12 are inserted with SQL, then every migration is applied and the rows and counters are read.
 * {@link pl.szczurradar.api.MigrationsIT} cannot show this: it migrates everything when its context starts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class V12BackfillIT {

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void eachRangeRunGetsItsSourcesResultFromItsLastFetchStep() {
		Flyway.configure().dataSource(dataSource).target("11").load().migrate();
		UUID admin = jdbc.queryForObject("INSERT INTO users (email, role) VALUES ('backfill@example.com', 'admin')"
				+ " RETURNING id", UUID.class);
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

	/** An ended range run of Wykop over one day with its counters, as V11 knew it. */
	private UUID range(UUID admin, String status, int fetched, int skipped) {
		return jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, range_from, range_to,"
				+ " sources, started_at, finished_at, fetched, skipped) VALUES ('range', ?, ?, '2026-09-30T22:00:00Z',"
				+ " '2026-10-01T21:59:59Z', ARRAY['wykop'], now(), now(), ?, ?) RETURNING id", UUID.class, status,
				admin, fetched, skipped);
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
