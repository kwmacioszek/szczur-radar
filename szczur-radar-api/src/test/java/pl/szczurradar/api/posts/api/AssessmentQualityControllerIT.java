package pl.szczurradar.api.posts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.contract.ContractFiles;
import pl.szczurradar.api.posts.AssessmentRepository;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;

/**
 * {@code GET /api/admin/assessment-quality} on Postgres with a fixed clock (today is 12 March 2025 in Warsaw, so
 * the window is 6–12 March): who may read it and the exact answer, compared with the shared contract files, with
 * both measures (S-35's failed attempts, S-36's theses in another language than their post) and the language
 * measure missing when the theses' query throws (a spied {@link AssessmentRepository}). Attempts are stored as the
 * pipeline leaves them; each test rolls back. Every route's session rule:
 * {@code AuthorizationCompletenessIT} and {@code AdminRoleIT} (every mapped route under {@code /api/admin/}, S-40).
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, AssessmentQualityControllerIT.FixedClock.class })
@Transactional
class AssessmentQualityControllerIT {

	private static final String QUALITY = "/api/admin/assessment-quality";

	/** 11:00 in Warsaw on 12 March 2025. */
	private static final Instant NOW = Instant.parse("2025-03-12T10:00:00Z");

	private static final String HASH = "'" + "0".repeat(64) + "'";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** A thesis with evidence of Polish (a Polish letter). */
	private static final String PL = "Spółka zyska na obniżkach stóp";

	/** A thesis with evidence of English (two function words and more). */
	private static final String EN = "The company will beat the estimates";

	/** A thesis without evidence of either language: no violation, still a thesis. */
	private static final String UNDECIDED = "CDR 2025";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	AssessmentRepository assessments;

	private UUID cron;

	private UUID wykop;

	private UUID reddit;

	@BeforeEach
	void seed() {
		cron =jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by, created_at, started_at,"
				+ " finished_at, range_from, range_to, sources) VALUES ('range', 'succeeded', ?, now(), now(), now(),"
				+ " now(), now(), ARRAY['wykop', 'reddit']) RETURNING id", UUID.class, SystemAccount.SCHEDULER.id());
		wykop = post("wykop");
		reddit = post("reddit");
	}

	@Test
	void withoutASessionIs401() {
		assertThat(mvc.get().uri(QUALITY)).hasStatus(401);
	}

	@Test
	void aUserWithoutTheAdminRoleIs403() {
		assertThat(mvc.get().uri(QUALITY).with(user(UUID.randomUUID().toString()).roles(Role.USER.name())))
			.hasStatus(403);
	}

	/**
	 * Failed attempts: Wykop at 5.0 % of 200, its threshold exactly: {@code ok}; Reddit at 33.3 % of 120 against
	 * 25 %: {@code above_threshold}. The prefilter's attempts and those of 5 March (the day before the window) are
	 * not counted. Theses in another language: Wykop at 2.0 % of 500 (English ones on Polish posts), its threshold
	 * exactly: {@code ok}; Reddit at 30.1 % of 501 (Polish ones on English posts) against 30 %:
	 * {@code above_threshold}; an undecided thesis is counted, never as another language.
	 */
	@Test
	void theWindowMatchesTheSharedContract() {
		attempts(wykop, null, "model", "2025-03-06T10:00:00Z", 50);
		theses(wykop, "2025-03-06T10:00:00Z", 100, PL, PL, PL, UNDECIDED);
		attempts(wykop, null, "model", "2025-03-12T09:00:00Z", 30);
		theses(wykop, "2025-03-12T09:00:00Z", 10, EN, PL, PL, PL, PL, PL, PL, PL, PL, UNDECIDED);
		attempts(wykop, FailureClass.SCHEMA_VIOLATION, "model", "2025-03-08T10:00:00Z", 4);
		attempts(wykop, FailureClass.FABRICATED_EVIDENCE, "model", "2025-03-11T10:00:00Z", 6);
		attempts(wykop, null, "prefilter", "2025-03-08T10:00:00Z", 30);
		attempts(wykop, FailureClass.TIMEOUT, "model", "2025-03-05T22:59:00Z", 50);
		attempts(reddit, null, "model", "2025-03-07T10:00:00Z", 29);
		theses(reddit, "2025-03-07T10:00:00Z", 50, PL, PL, PL, EN, EN, EN, EN, EN, EN, UNDECIDED);
		theses(reddit, "2025-03-07T10:00:00Z", 1, PL);
		attempts(reddit, FailureClass.SCHEMA_VIOLATION, "model", "2025-03-07T10:00:00Z", 20);
		attempts(reddit, FailureClass.FABRICATED_EVIDENCE, "model", "2025-03-09T10:00:00Z", 15);
		attempts(reddit, FailureClass.DOMAIN_INVARIANT_VIOLATION, "model", "2025-03-10T10:00:00Z", 5);

		assertMatches("assessment-quality.json");
	}

	/**
	 * Below {@code minAttempts} and {@code minTheses} the thresholds are not checked: Wykop with no failed attempt
	 * and 46 theses, one in English; Reddit with one failed attempt and no thesis at all (its language counted as
	 * none, still below the minimum).
	 */
	@Test
	void fewAttemptsMatchTheSharedContract() {
		theses(wykop, "2025-03-10T10:00:00Z", 45, PL);
		theses(wykop, "2025-03-10T10:00:00Z", 1, EN);
		attempts(reddit, null, "model", "2025-03-10T10:00:00Z", 2);
		attempts(reddit, FailureClass.PROVIDER_ERROR, "model", "2025-03-11T10:00:00Z", 1);

		assertMatches("assessment-quality-few-attempts.json");
	}

	/** The theses' query throws: every source's {@code language} is {@code null}, the failed attempts as usual. */
	@Test
	void withoutTheThesesTheFailedAttemptsStillMatchTheSharedContract() {
		theses(wykop, "2025-03-10T10:00:00Z", 120, PL);
		attempts(wykop, FailureClass.SCHEMA_VIOLATION, "model", "2025-03-11T10:00:00Z", 3);
		attempts(reddit, null, "model", "2025-03-10T10:00:00Z", 2);
		doThrow(new QueryTimeoutException("test: the theses' query timed out")).when(assessments)
			.findModelStatements(any(), any());

		assertMatches("assessment-quality-language-unavailable.json");
	}

	@Test
	void withoutAnAttemptInTheWindowThereIsNoSource() {
		attempts(wykop, null, "prefilter", "2025-03-10T10:00:00Z", 5);

		assertThat(mvc.get().uri(QUALITY).with(admin())).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("""
					{"from":"2025-03-06","to":"2025-03-12","windowDays":7,"minAttempts":100,"minTheses":500,
					"sources":[]}""");
	}

	private void assertMatches(String file) {
		MvcTestResult result = mvc.get().uri(QUALITY).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		ContractFiles.assertMatches(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
				file);
	}

	private static RequestPostProcessor admin() {
		return user(UUID.randomUUID().toString()).roles(Role.ADMIN.name());
	}

	/** A post of {@code source}, first stored by the cron's run. */
	private UUID post(String source) {
		return jdbc.queryForObject("WITH raw AS (INSERT INTO raw_payloads (source, content_encoding, media_type,"
				+ " payload, sha256) VALUES (?, 'gzip', 'application/json', '\\x00', " + HASH + ") RETURNING id)"
				+ " INSERT INTO posts (source, item_type, source_item_id, channel, language, published_at, raw_payload_id,"
				+ " content_hash, stage, assessment_attempts, first_run_id)"
				+ " SELECT ?, 'post', ?, 'c', ?, now(), raw.id, " + HASH + ", 'assessed', 1, ? FROM raw RETURNING id",
				UUID.class, source, source, UUID.randomUUID().toString(), Source.fromValue(source).language().value(),
				cron);
	}

	/**
	 * {@code count} attempts of {@code post} in the cron's run at {@code assessedAt}, by {@code resultSource}:
	 * failed with {@code failureClass}, or {@code ok} when it is {@code null}.
	 */
	private void attempts(UUID post, @Nullable FailureClass failureClass, String resultSource, String assessedAt,
			int count) {
		insert(post, failureClass, resultSource, assessedAt, count, failureClass == null ? "{}" : null);
	}

	/**
	 * {@code count} successful attempts of {@code post} with a model call at {@code assessedAt}, each with
	 * {@code statements} as its theses.
	 */
	private void theses(UUID post, String assessedAt, int count, String... statements) {
		String output = JSON.writeValueAsString(
				Map.of("claims", Arrays.stream(statements).map(statement -> Map.of("statement", statement)).toList()));
		insert(post, null, "model", assessedAt, count, output);
	}

	private void insert(UUID post, @Nullable FailureClass failureClass, String resultSource, String assessedAt,
			int count, @Nullable String output) {
		boolean ok = failureClass == null;
		jdbc.update("INSERT INTO assessments (post_id, run_id, status, failure_class, input, input_hash, output,"
				+ " violations, result_source, flex_fallback, contract_version, assessed_at, duration_ms, attempts)"
				+ " SELECT CAST(? AS UUID), CAST(? AS UUID), CAST(? AS TEXT), CAST(? AS TEXT), '{}', " + HASH + ","
				+ " CAST(? AS JSONB), '[]', CAST(? AS TEXT), false, 'V2', CAST(? AS TIMESTAMPTZ), 0, 1"
				+ " FROM generate_series(1, ?)", post, cron, ok ? "ok" : "failed", ok ? null : failureClass.value(),
				output, resultSource, assessedAt, count);
	}

	/** Today is {@link #NOW}; UTC like the application's clock. */
	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClock {

		@Bean
		@Primary
		Clock testClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

	}

}
