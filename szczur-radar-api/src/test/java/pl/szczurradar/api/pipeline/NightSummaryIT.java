package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.pipeline.NightSummary.Night;
import pl.szczurradar.api.user.SystemAccount;

/**
 * The night's summary on Postgres, from rows written as the pipeline leaves them. Two nights of the cron
 * and an admin's runs around them:
 *
 * <pre>
 * 11-05 12:00  X0  admin's retry run           — kills D0 (wykop): before the first night
 * 11-06 00:30  N1  cron's range, succeeded     — the first night; stores D2 and L1
 * 11-07 10:00  X1  admin's retry run           — kills D1 (reddit); D2's second failed attempt
 * 11-08 00:30  R   cron's retry, partial       — D2's third attempt (dead, wykop), L1 ok (reddit)
 * 11-08 00:30  G   cron's range, still running — wykop succeeded (3 new posts), reddit failed
 * </pre>
 *
 * The clock stands at 02:00 Warsaw on 8 November 2026.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@Import({ TestcontainersConfiguration.class, NightSummaryIT.TestBeans.class })
class NightSummaryIT {

	private static final String HASH = "'" + "0".repeat(64) + "'";

	@Autowired
	NightSummary summary;

	@Autowired
	JdbcTemplate jdbc;

	private UUID admin;

	private Nights nights;

	/** Each scenario on the rows of the class comment alone: the tables emptied, then seeded once. */
	@BeforeEach
	void seed() {
		jdbc.execute("TRUNCATE TABLE assessments, posts, raw_payloads, pipeline_run_sources, pipeline_run_steps,"
				+ " notifications, pipeline_runs");
		admin = jdbc.queryForObject("INSERT INTO users (email, role) VALUES (?, 'admin') RETURNING id", UUID.class,
				"night-summary-" + UUID.randomUUID() + "@example.com");
		nights = rows();
	}

	@Test
	void theNightsTextFromTwoSourcesARetryAndARangeStillRunning() {
		Night night = summary.of(List.of(nights.range, nights.retry)).orElseThrow();

		assertThat(night.alertRunId()).isEqualTo(nights.range);
		assertThat(night.text()).isEqualTo("""
				Night 2026-11-08 (range 2026-11-06…2026-11-07): retry partial, range running; errors 1, skipped 1
				wykop: fetched 10, skipped 1, new 3, leftovers 1; assessed 2, failed 2, dead 1; tokens in 150 (cached 20), out 15
				reddit: failed: RedditApiException: HTTP 503 %s…, new 0, leftovers 1; assessed 1, failed 0, dead 0; tokens in 200 (cached 100), out 30
				Dead in admin runs since the previous night: reddit 1
				/admin""".formatted("x".repeat(NightSummary.MAX_SOURCE_ERROR - 1 - "RedditApiException: HTTP 503 ".length())));
	}

	@Test
	void theFirstNightCountsTheAdminsDeadPostsFromTheBeginningAndNotThoseOfALaterRun() {
		Night night = summary.of(List.of(nights.firstNight)).orElseThrow();

		// D0 (X0, before any cron run) counts; D1 (X1, after this night) is the next night's.
		assertThat(night.text()).isEqualTo("""
				Night 2026-11-08 (range 2026-11-04…2026-11-05): range succeeded; errors 0, skipped 0
				wykop: fetched 5, skipped 0, new 2, leftovers 0; assessed 0, failed 0, dead 0; tokens in 0 (cached 0), out 0
				Dead in admin runs since the previous night: wykop 1
				/admin""");
	}

	@Test
	void anUnknownRunIsNoNightAndARepeatedIdCountsOnce() {
		assertThat(summary.of(List.of(nights.range, UUID.randomUUID()))).isEmpty();
		assertThat(summary.of(List.of(nights.retry, nights.retry)).orElseThrow().runs()).hasSize(1);
	}

	/** The rows of the class comment. */
	private Nights rows() {
		UUID x0 = run("retry", "succeeded", admin, "2026-11-05T12:00:00Z", null, null, 0, 0, 0);
		UUID n1 = run("range", "succeeded", SystemAccount.SCHEDULER.id(), "2026-11-06T00:30:00Z", "2026-11-03T23:00:00Z",
				"2026-11-05T22:59:59Z", 0, 0, 0);
		sourceResult(n1, "wykop", "succeeded", 5, 0, null);
		UUID x1 = run("retry", "partial", admin, "2026-11-07T10:00:00Z", null, null, 0, 0, 1);
		UUID retry = run("retry", "partial", SystemAccount.SCHEDULER.id(), "2026-11-08T00:30:00.000000Z", null, null, 1,
				0, 1);
		UUID range = run("range", "running", SystemAccount.SCHEDULER.id(), "2026-11-08T00:30:00.000001Z",
				"2026-11-05T23:00:00Z", "2026-11-07T22:59:59Z", 0, 1, 0);
		sourceResult(range, "wykop", "succeeded", 10, 1, null);
		sourceResult(range, "reddit", "failed", 0, 0, "RedditApiException: HTTP 503 " + "x".repeat(300));

		// D0: dead before the first night (X0 killed it).
		UUID d0 = post("wykop", "dead", 3, x0);
		assessment(d0, x0, false, "2026-11-05T12:01:00Z", 10, 0, 1);
		// D1: dead between the nights (X1 killed it).
		UUID d1 = post("reddit", "dead", 3, x1);
		assessment(d1, x1, false, "2026-11-07T10:01:00Z", 10, 0, 1);
		// D2: stored in the first night, failed in X1, killed by this night's retry.
		UUID d2 = post("wykop", "dead", 3, n1);
		assessment(d2, x1, false, "2026-11-07T10:02:00Z", 10, 0, 1);
		assessment(d2, retry, false, "2026-11-08T00:40:00Z", 50, 0, 5);
		// L1: a leftover of the first night, assessed in this night's retry.
		UUID l1 = post("reddit", "assessed", 1, n1);
		assessment(l1, retry, true, "2026-11-08T00:41:00Z", 200, 100, 30);
		// The first night's other new post, never assessed.
		post("wykop", "stored", 0, n1);
		// This night's new posts: ok, failed, and the prefilter's (no tokens).
		UUID p1 = post("wykop", "assessed", 1, range);
		assessment(p1, range, true, "2026-11-08T01:00:00Z", 100, 20, 10);
		UUID p2 = post("wykop", "failed", 1, range);
		assessment(p2, range, false, "2026-11-08T01:01:00Z", null, null, null);
		UUID p3 = post("wykop", "assessed", 1, range);
		assessment(p3, range, true, "2026-11-08T01:02:00Z", null, null, null);
		return new Nights(n1, retry, range);
	}

	private record Nights(UUID firstNight, UUID retry, UUID range) {
	}

	private UUID run(String kind, String status, UUID requestedBy, String createdAt, @Nullable String rangeFrom,
			@Nullable String rangeTo, int processingErrors, int skipped, int dead) {
		return jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at, started_at,"
				+ " finished_at, range_from, range_to, sources, processing_errors, skipped, dead)"
				+ " VALUES (?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ),"
				+ " CASE WHEN ? IN ('queued', 'running') THEN NULL ELSE CAST(? AS TIMESTAMPTZ) END,"
				+ " CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ),"
				+ " CASE WHEN ? = 'range' THEN ARRAY['wykop', 'reddit'] END, ?, ?, ?) RETURNING id", UUID.class, kind,
				status, requestedBy, createdAt, createdAt, status, createdAt, rangeFrom, rangeTo, kind, processingErrors,
				skipped, dead);
	}

	private void sourceResult(UUID run, String source, String status, int fetched, int skipped,
			@Nullable String error) {
		jdbc.update("INSERT INTO pipeline_run_sources (run_id, source, status, fetched, skipped, error)"
				+ " VALUES (?, ?, ?, ?, ?, ?)", run, source, status, fetched, skipped, error);
	}

	/** A post of {@code source} in {@code stage} with {@code attempts} failed attempts, first stored by {@code firstRun}. */
	private UUID post(String source, String stage, int attempts, UUID firstRun) {
		return jdbc.queryForObject("WITH raw AS (INSERT INTO raw_payloads (source, content_encoding, media_type,"
				+ " payload, sha256) VALUES (?, 'gzip', 'application/json', '\\x00', " + HASH + ") RETURNING id)"
				+ " INSERT INTO posts (source, item_type, source_item_id, channel, published_at, raw_payload_id,"
				+ " content_hash, stage, assessment_attempts, first_run_id)"
				+ " SELECT ?, 'post', ?, 'c', now(), raw.id, " + HASH + ", ?, ?, ? FROM raw RETURNING id", UUID.class,
				source, source, UUID.randomUUID().toString(), stage, attempts, firstRun);
	}

	/** An assessment of {@code post} in {@code run}; {@code null} tokens as the prefilter leaves them. */
	private void assessment(UUID post, UUID run, boolean ok, String assessedAt, @Nullable Integer inputTokens,
			@Nullable Integer cachedTokens, @Nullable Integer outputTokens) {
		jdbc.update("INSERT INTO assessments (post_id, run_id, status, failure_class, input, input_hash, output,"
				+ " violations, result_source, flex_fallback, contract_version, assessed_at, duration_ms, attempts,"
				+ " input_tokens, cached_tokens, output_tokens) VALUES (?, ?, ?, ?, '{}', " + HASH + ", CAST(? AS JSONB),"
				+ " '[]', 'model', false, 'V2', CAST(? AS TIMESTAMPTZ), 0, 1, ?, ?, ?)", post, run, ok ? "ok" : "failed",
				ok ? null : "timeout", ok ? "{}" : null, assessedAt, inputTokens, cachedTokens, outputTokens);
	}

	/** 02:00 in Warsaw on 8 November 2026. */
	@TestConfiguration(proxyBeanMethods = false)
	static class TestBeans {

		@Bean
		@Primary
		Clock testClock() {
			return Clock.fixed(Instant.parse("2026-11-08T01:00:00Z"), ZoneOffset.UTC);
		}

	}

}
