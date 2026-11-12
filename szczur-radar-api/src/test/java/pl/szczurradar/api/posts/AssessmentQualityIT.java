package pl.szczurradar.api.posts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.text.Language;
import pl.szczurradar.api.user.SystemAccount;

/**
 * The quality windows of {@link AssessmentQuality} on Postgres
 * ({@link AssessmentRepository#countModelAttemptsBySourceAndClass}, {@link AssessmentRepository#findModelStatements}),
 * from rows written as the pipeline leaves them: attempts of the cron's and an admin's runs, the prefilter's
 * left out, Warsaw calendar days with their real length. Each test rolls back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
@Transactional
class AssessmentQualityIT {

	private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

	private static final String HASH = "'" + "0".repeat(64) + "'";

	private static final String POLISH = "Autor uważa, że kurs wzrośnie";

	private static final String ENGLISH = "The author thinks that the price will rise";

	/** No letter or function word of either language. */
	private static final String UNDECIDED = "CDR 120 zl target";

	@Autowired
	AssessmentQuality quality;

	@Autowired
	AssessmentRepository repository;

	@Autowired
	JdbcTemplate jdbc;

	private UUID cron;

	private UUID admin;

	private UUID wykop;

	private UUID reddit;

	@BeforeEach
	void seed() {
		UUID adminUser = jdbc.queryForObject("INSERT INTO users (email, role) VALUES (?, 'admin') RETURNING id",
				UUID.class, "quality-" + UUID.randomUUID() + "@example.com");
		cron = run("range", SystemAccount.SCHEDULER.id());
		admin = run("retry", adminUser);
		wykop = post("wykop");
		reddit = post("reddit");
	}

	/**
	 * The window of 25 October 2026 (19–25 October): the 25th has 25 hours in Warsaw (CEST → CET), so it ends
	 * at 23:00 UTC, not 22:00.
	 */
	@Test
	void theWindowCountsTheModelsAttemptsOfEveryRunPerSourceAndClassByWarsawDays() {
		// 00:00 on 19 October (CEST): the window's first instant.
		attempts(wykop, cron, null, "model", "2026-10-18T22:00:00Z", 1);
		// 23:59 on 18 October (CEST, D−7): only in the window of the day before.
		attempts(wykop, cron, FailureClass.PROVIDER_ERROR, "model", "2026-10-18T21:59:00Z", 1);
		attempts(wykop, cron, null, "model", "2026-10-20T10:00:00Z", 3);
		// The prefilter's attempts make no model call: never counted.
		attempts(wykop, cron, null, "prefilter", "2026-10-20T10:00:00Z", 5);
		attempts(wykop, cron, FailureClass.FABRICATED_EVIDENCE, "model", "2026-10-21T10:00:00Z", 1);
		attempts(wykop, admin, FailureClass.SCHEMA_VIOLATION, "model", "2026-10-23T12:00:00Z", 1);
		attempts(wykop, admin, null, "model", "2026-10-23T12:00:00Z", 1);
		// 23:30 on the 25th (CET): its 25th hour, still in the window.
		attempts(wykop, cron, FailureClass.TIMEOUT, "model", "2026-10-25T22:30:00Z", 1);
		// 00:00 on 26 October (CET): the next day.
		attempts(wykop, cron, null, "model", "2026-10-25T23:00:00Z", 1);
		attempts(reddit, cron, FailureClass.SCHEMA_VIOLATION, "model", "2026-10-22T09:00:00Z", 2);
		attempts(reddit, admin, FailureClass.DOMAIN_INVARIANT_VIOLATION, "model", "2026-10-24T09:00:00Z", 1);
		attempts(reddit, admin, null, "model", "2026-10-24T09:00:00Z", 1);

		QualityWindow window = quality.window(LocalDate.of(2026, 10, 25), WARSAW);

		assertThat(window.from()).isEqualTo(LocalDate.of(2026, 10, 19));
		assertThat(window.to()).isEqualTo(LocalDate.of(2026, 10, 25));
		assertThat(window.sources()).containsExactly(
				new SourceQuality(Source.WYKOP, 8, 3, Map.of(FailureClass.TIMEOUT, 1L, FailureClass.SCHEMA_VIOLATION, 1L,
						FailureClass.FABRICATED_EVIDENCE, 1L), 5, QualityStatus.NOT_ENOUGH_DATA),
				new SourceQuality(Source.REDDIT, 4, 3, Map.of(FailureClass.SCHEMA_VIOLATION, 2L,
						FailureClass.DOMAIN_INVARIANT_VIOLATION, 1L), 25, QualityStatus.NOT_ENOUGH_DATA));
		assertThat(window.sources().get(0).classes().keySet()).containsExactly(FailureClass.TIMEOUT,
				FailureClass.SCHEMA_VIOLATION, FailureClass.FABRICATED_EVIDENCE);

		// The day before (18–24 October): 23:59 on the 18th in, the 25th out.
		assertThat(quality.window(LocalDate.of(2026, 10, 24), WARSAW).sources()).containsExactly(
				new SourceQuality(Source.WYKOP, 8, 3, Map.of(FailureClass.PROVIDER_ERROR, 1L,
						FailureClass.SCHEMA_VIOLATION, 1L, FailureClass.FABRICATED_EVIDENCE, 1L), 5,
						QualityStatus.NOT_ENOUGH_DATA),
				new SourceQuality(Source.REDDIT, 4, 3, Map.of(FailureClass.SCHEMA_VIOLATION, 2L,
						FailureClass.DOMAIN_INVARIANT_VIOLATION, 1L), 25, QualityStatus.NOT_ENOUGH_DATA));
		assertThat(quality.window(LocalDate.of(2026, 10, 10), WARSAW).sources()).isEmpty();
	}

	/**
	 * The night of 8 November 2026, its summary at 01:45 Warsaw. An admin's run failed Wykop's attempts at
	 * 12:00 on the 7th, after the previous night's summary: the night before is cut at 01:45 on the 7th, so
	 * they count only tonight and Wykop crosses — the full 7th would have it above two nights in a row and
	 * never alarm. Reddit is above its threshold on both nights: no crossing.
	 */
	@Test
	void anAdminsFailedAttemptsOfTheDayBeforeCrossTonight() {
		attempts(wykop, cron, null, "model", "2026-11-03T10:00:00Z", 100);
		attempts(wykop, admin, FailureClass.FABRICATED_EVIDENCE, "model", "2026-11-07T11:00:00Z", 10);
		attempts(reddit, cron, null, "model", "2026-11-03T10:00:00Z", 70);
		attempts(reddit, cron, FailureClass.SCHEMA_VIOLATION, "model", "2026-11-03T10:00:00Z", 30);

		List<QualityCrossing> crossed = quality.crossed(quality.window(LocalDate.of(2026, 11, 8), WARSAW),
				Instant.parse("2026-11-08T00:45:00Z"), WARSAW);

		assertThat(crossed).containsExactly(new QualityCrossing(
				new SourceQuality(Source.WYKOP, 110, 10, Map.of(FailureClass.FABRICATED_EVIDENCE, 10L), 5,
						QualityStatus.ABOVE_THRESHOLD),
				new SourceQuality(Source.WYKOP, 100, 0, Map.of(), 5, QualityStatus.OK)));
		assertThat(quality.window(LocalDate.of(2026, 11, 7), WARSAW).sources())
			.extracting(SourceQuality::source, SourceQuality::status)
			.containsExactly(tuple(Source.WYKOP, QualityStatus.ABOVE_THRESHOLD),
					tuple(Source.REDDIT, QualityStatus.ABOVE_THRESHOLD));
	}

	/**
	 * The theses of the language window of 25 October 2026 (19–25 October, the 25th with 25 hours in Warsaw): only
	 * those of the {@code ok} attempts with a model call, each with its post's stored language — not its source's.
	 */
	@Test
	void theLanguageWindowReadsTheThesesOfTheModelsOkAttemptsWithTheirPostsLanguage() {
		UUID englishOnWykop = post("wykop", Language.EN);
		// 00:00 on 19 October (CEST): the window's first instant.
		attempt(wykop, "model", claims(POLISH, ENGLISH, UNDECIDED), "2026-10-18T22:00:00Z");
		// 23:59 on 18 October and 00:00 on 26 October: outside.
		attempt(wykop, "model", claims(ENGLISH), "2026-10-18T21:59:00Z");
		attempt(wykop, "model", claims(ENGLISH), "2026-10-25T23:00:00Z");
		// 23:30 on the 25th (CET): its 25th hour, still in.
		attempt(reddit, "model", claims(POLISH, ENGLISH), "2026-10-25T22:30:00Z");
		// The prefilter's attempts and the failed ones have no thesis of the model.
		attempt(wykop, "prefilter", claims(ENGLISH), "2026-10-20T10:00:00Z");
		attempts(wykop, cron, FailureClass.SCHEMA_VIOLATION, "model", "2026-10-20T10:00:00Z", 1);
		// No theses, claims that are not an array, a statement that is not a string: no row, no error.
		attempt(reddit, "model", "{}", "2026-10-20T10:00:00Z");
		attempt(reddit, "model", "{\"claims\": []}", "2026-10-20T10:00:00Z");
		attempt(reddit, "model", "{\"claims\": \"" + ENGLISH + "\"}", "2026-10-20T10:00:00Z");
		attempt(reddit, "model", "{\"claims\": {\"statement\": \"" + ENGLISH + "\"}}", "2026-10-20T10:00:00Z");
		attempt(reddit, "model", "{\"claims\": [{\"statement\": 7}, {\"statement\": null}, 3]}",
				"2026-10-20T10:00:00Z");
		// An English post on Wykop: its Polish thesis is in another language, its English one is not.
		attempt(englishOnWykop, "model", claims(POLISH, ENGLISH), "2026-10-21T10:00:00Z");

		assertThat(repository.findModelStatements(Instant.parse("2026-10-18T22:00:00Z"),
				Instant.parse("2026-10-25T23:00:00Z")))
			.containsExactlyInAnyOrder(new PostStatement(Source.WYKOP, Language.PL, POLISH),
					new PostStatement(Source.WYKOP, Language.PL, ENGLISH),
					new PostStatement(Source.WYKOP, Language.PL, UNDECIDED),
					new PostStatement(Source.WYKOP, Language.EN, POLISH),
					new PostStatement(Source.WYKOP, Language.EN, ENGLISH),
					new PostStatement(Source.REDDIT, Language.EN, POLISH),
					new PostStatement(Source.REDDIT, Language.EN, ENGLISH));

		LanguageWindow window = quality.languageWindow(LocalDate.of(2026, 10, 25), WARSAW);

		assertThat(window).isEqualTo(new LanguageWindow(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
				List.of(new SourceLanguage(Source.WYKOP, 5, 2, 2, QualityStatus.NOT_ENOUGH_DATA),
						new SourceLanguage(Source.REDDIT, 2, 1, 30, QualityStatus.NOT_ENOUGH_DATA))));
		assertThat(quality.languageWindow(LocalDate.of(2026, 10, 10), WARSAW).sources()).isEmpty();
	}

	/** A finished run of {@code kind} requested by {@code requestedBy}. */
	private UUID run(String kind, UUID requestedBy) {
		return jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at, started_at,"
				+ " finished_at, range_from, range_to, sources) VALUES (?, 'succeeded', ?, now(), now(), now(),"
				+ " CASE WHEN ? = 'range' THEN now() END, CASE WHEN ? = 'range' THEN now() END,"
				+ " CASE WHEN ? = 'range' THEN ARRAY['wykop', 'reddit'] END) RETURNING id", UUID.class, kind,
				requestedBy, kind, kind, kind);
	}

	/** A post of {@code source} in its source's language, first stored by the cron's run. */
	private UUID post(String source) {
		return post(source, Source.fromValue(source).language());
	}

	/** A post of {@code source} with {@code language} stored, first stored by the cron's run. */
	private UUID post(String source, Language language) {
		return jdbc.queryForObject("WITH raw AS (INSERT INTO raw_payloads (source, content_encoding, media_type,"
				+ " payload, sha256) VALUES (?, 'gzip', 'application/json', '\\x00', " + HASH + ") RETURNING id)"
				+ " INSERT INTO posts (source, item_type, source_item_id, channel, language, published_at, raw_payload_id,"
				+ " content_hash, stage, assessment_attempts, first_run_id)"
				+ " SELECT ?, 'post', ?, 'c', ?, now(), raw.id, " + HASH + ", 'assessed', 1, ? FROM raw RETURNING id",
				UUID.class, source, source, UUID.randomUUID().toString(), language.value(), cron);
	}

	/**
	 * {@code count} attempts of {@code post} in {@code run} at {@code assessedAt}, by {@code resultSource}:
	 * failed with {@code failureClass}, or {@code ok} when it is {@code null}.
	 */
	private void attempts(UUID post, UUID run, @Nullable FailureClass failureClass, String resultSource,
			String assessedAt, int count) {
		insert(post, run, failureClass, failureClass == null ? "{}" : null, resultSource, assessedAt, count);
	}

	/** One {@code ok} attempt of {@code post} in the cron's run at {@code assessedAt} with {@code output}. */
	private void attempt(UUID post, String resultSource, String output, String assessedAt) {
		insert(post, cron, null, output, resultSource, assessedAt, 1);
	}

	/** An output with one claim per thesis of {@code statements} (no quotes in them). */
	private static String claims(String... statements) {
		return Arrays.stream(statements)
			.map(statement -> "{\"id\": \"c\", \"statement\": \"" + statement + "\"}")
			.collect(Collectors.joining(", ", "{\"claims\": [", "]}"));
	}

	private void insert(UUID post, UUID run, @Nullable FailureClass failureClass, @Nullable String output,
			String resultSource, String assessedAt, int count) {
		boolean ok = failureClass == null;
		jdbc.update("INSERT INTO assessments (post_id, run_id, status, failure_class, input, input_hash, output,"
				+ " violations, result_source, flex_fallback, contract_version, assessed_at, duration_ms, attempts)"
				+ " SELECT CAST(? AS UUID), CAST(? AS UUID), CAST(? AS TEXT), CAST(? AS TEXT), '{}', " + HASH + ","
				+ " CAST(? AS JSONB), '[]', CAST(? AS TEXT), false, 'V2', CAST(? AS TIMESTAMPTZ), 0, 1"
				+ " FROM generate_series(1, ?)", post, run, ok ? "ok" : "failed", ok ? null : failureClass.value(),
				output, resultSource, assessedAt, count);
	}

}
