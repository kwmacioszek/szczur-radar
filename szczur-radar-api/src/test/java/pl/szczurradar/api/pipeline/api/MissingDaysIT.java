package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.pipeline.PipelineRunService;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.sources.ScriptedSourceConfiguration;
import pl.szczurradar.api.sources.ScriptedSourceFetcher;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.wykop.WykopConfiguration;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * {@code GET /api/admin/pipeline-runs/missing-days} on Postgres with a fixed clock (today is 24 October
 * 2026 in Warsaw, so the window is 9–22 October): which runs cover a day and the exact answer. Wykop is
 * configured with keys no call uses (no run executes here); the second source ({@link ScriptedSourceFetcher}
 * of {@link Source#REDDIT}) only in the scenario that switches it on. Runs are stored through the entities,
 * then ended as the pipeline would end them, with each source's result written as {@code RunRecorder.source}
 * writes it. Each test rolls back. Who may read it: {@code AuthorizationCompletenessIT} and
 * {@code AdminRoleIT} ({@code ADMIN_ROUTES}).
 */
@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY, WykopConfiguration.KEY + "=test-key",
		WykopConfiguration.SECRET + "=test-secret" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ScriptedSourceConfiguration.class, MissingDaysIT.FixedClock.class })
@Transactional
class MissingDaysIT {

	private static final String MISSING_DAYS = "/api/admin/pipeline-runs/missing-days";

	/** 12:00 in Warsaw on 24 October 2026. */
	private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

	/** The answer when 19 and 20 October are missing for Wykop. */
	private static final String GAP = """
			{"from":"2026-10-09","to":"2026-10-22","days":[\
			{"day":"2026-10-19","sources":["wykop"]},{"day":"2026-10-20","sources":["wykop"]}]}""";

	private static final String NO_GAP = "{\"from\":\"2026-10-09\",\"to\":\"2026-10-22\",\"days\":[]}";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PipelineRunRepository runs;

	@Autowired
	UserRepository users;

	@Autowired
	ScriptedSourceFetcher reddit;

	private UUID admin;

	@BeforeEach
	void setUp() {
		// The other tests of this context share the database: within this test's transaction no earlier
		// run covers a day, so only the runs of this test count, and none is queued or running, so this
		// test's running run never meets uq_pipeline_runs_one_running.
		jdbc.update("UPDATE pipeline_runs SET status = ? WHERE status IN (?, ?)", RunStatus.FAILED.value(),
				RunStatus.SUCCEEDED.value(), RunStatus.PARTIAL.value());
		jdbc.update("UPDATE pipeline_runs SET status = ? WHERE status IN (?, ?)", RunStatus.INTERRUPTED.value(),
				RunStatus.QUEUED.value(), RunStatus.RUNNING.value());
		admin = users.saveAndFlush(new User("missing-days-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN))
			.getId();
	}

	@AfterEach
	void tearDown() {
		reddit.reset();
	}

	@Test
	void theDaysNoSucceededRangeRunCoveredAreMissing() {
		nightlyRunsWithAGapOn19And20October();

		assertMissingDays(GAP);
	}

	@ParameterizedTest
	@EnumSource(value = RunStatus.class, names = { "FAILED", "INTERRUPTED", "QUEUED", "RUNNING" })
	void aRangeRunThatDidNotSucceedDoesNotCover(RunStatus status) {
		nightlyRunsWithAGapOn19And20October();
		range(status, admin, "2026-10-19", "2026-10-20");

		assertMissingDays(GAP);
	}

	@ParameterizedTest
	@EnumSource(value = RunKind.class, names = { "RETRY", "POSTS" })
	void aRunOverLeftoversOrChosenPostsDoesNotCover(RunKind kind) {
		nightlyRunsWithAGapOn19And20October();
		PipelineRun run = kind == RunKind.RETRY ? PipelineRun.retry(admin, null)
				: PipelineRun.posts(List.of(UUID.randomUUID()), admin, null);
		end(runs.saveAndFlush(run).getId(), RunStatus.SUCCEEDED);

		assertMissingDays(GAP);
	}

	@Test
	void aPartialRunCovers() {
		nightlyRunsWithAGapOn19And20October();
		range(RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), "2026-10-19", "2026-10-19");

		assertMissingDays("""
				{"from":"2026-10-09","to":"2026-10-22","days":[{"day":"2026-10-20","sources":["wykop"]}]}""");
	}

	@Test
	void anAdminsRunCovers() {
		nightlyRunsWithAGapOn19And20October();
		range(RunStatus.SUCCEEDED, admin, "2026-10-19", "2026-10-20");

		assertMissingDays(NO_GAP);
	}

	@Test
	void theWindowOfASourceStartsAtItsFirstCoveredDay() {
		range(RunStatus.FAILED, SystemAccount.SCHEDULER.id(), "2026-10-01", "2026-10-02");
		range(RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), "2026-10-19", "2026-10-20");

		assertMissingDays("""
				{"from":"2026-10-09","to":"2026-10-22","days":[\
				{"day":"2026-10-21","sources":["wykop"]},{"day":"2026-10-22","sources":["wykop"]}]}""");
	}

	@Test
	void aRunEndingBeforeTheWindowOnlyStartsIt() {
		range(RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), "2026-10-07", "2026-10-08");
		range(RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), "2026-10-10", "2026-10-22");

		assertMissingDays("""
				{"from":"2026-10-09","to":"2026-10-22","days":[{"day":"2026-10-09","sources":["wykop"]}]}""");
	}

	@Test
	void aPartialRunCoversOnlyTheSourcesThatSucceeded() {
		reddit.configured(true);
		Map<Source, SourceFetchStatus> redditDown = results(SourceFetchStatus.SUCCEEDED, SourceFetchStatus.FAILED);
		Map<Source, SourceFetchStatus> bothUp = results(SourceFetchStatus.SUCCEEDED, SourceFetchStatus.SUCCEEDED);
		// Reddit gave its first post on 13 October: the run before it, though it requested Reddit, does not
		// start Reddit's window.
		range(RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), "2026-10-07", "2026-10-12", redditDown);
		range(RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), "2026-10-13", "2026-10-18", bothUp);
		range(RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), "2026-10-19", "2026-10-20", redditDown);
		range(RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), "2026-10-21", "2026-10-22", bothUp);

		assertMissingDays("""
				{"from":"2026-10-09","to":"2026-10-22","days":[\
				{"day":"2026-10-19","sources":["reddit"]},{"day":"2026-10-20","sources":["reddit"]}]}""");
	}

	@Test
	void anEmptySourceDoesNotCover() {
		nightlyRunsWithAGapOn19And20October();
		range(RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), "2026-10-19", "2026-10-20",
				Map.of(Source.WYKOP, SourceFetchStatus.EMPTY));

		assertMissingDays(GAP);
	}

	@Test
	void aSourceWithoutACoveringRunHasNoMissingDay() {
		range(RunStatus.FAILED, SystemAccount.SCHEDULER.id(), "2026-10-09", "2026-10-10");

		assertMissingDays(NO_GAP);
	}

	/** The nights' runs over 7–18 and 21–22 October, two days each. */
	private void nightlyRunsWithAGapOn19And20October() {
		for (LocalDate day = LocalDate.parse("2026-10-07"); day.isBefore(LocalDate.parse("2026-10-18")); day = day
			.plusDays(2)) {
			range(RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), day.toString(), day.plusDays(1).toString());
		}
		range(RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), "2026-10-21", "2026-10-22");
	}

	/**
	 * A Wykop range run over the Warsaw days {@code from}–{@code to}, stored as the service stores it, then
	 * ended {@code status}, with Wykop's result {@code succeeded}.
	 */
	private void range(RunStatus status, UUID requestedBy, String from, String to) {
		range(status, requestedBy, from, to, Map.of(Source.WYKOP, SourceFetchStatus.SUCCEEDED));
	}

	/**
	 * A range run of the sources of {@code results} over the Warsaw days {@code from}–{@code to}, stored as the
	 * service stores it, then ended {@code status}, with each source's result.
	 */
	private void range(RunStatus status, UUID requestedBy, String from, String to,
			Map<Source, SourceFetchStatus> results) {
		Map<Source, SourceFetchStatus> ordered = new EnumMap<>(results);
		PipelineRun run = PipelineRun.range(LocalDate.parse(from).atStartOfDay(PipelineRunService.ZONE).toInstant(),
				LocalDate.parse(to).atTime(LocalTime.of(23, 59, 59)).atZone(PipelineRunService.ZONE).toInstant(),
				List.copyOf(ordered.keySet()), requestedBy, null);
		UUID id = runs.saveAndFlush(run).getId();
		ordered.forEach((source, result) -> jdbc.update(
				"INSERT INTO pipeline_run_sources (run_id, source, status, fetched, skipped, error)"
						+ " VALUES (?, ?, ?, ?, 0, ?)",
				id, source.value(), result.value(), result == SourceFetchStatus.SUCCEEDED ? 3 : 0,
				result == SourceFetchStatus.FAILED ? "IllegalStateException: " + source.value() + " is down" : null));
		end(id, status);
	}

	/** Wykop's result {@code wykop}, Reddit's {@code reddit}. */
	private static Map<Source, SourceFetchStatus> results(SourceFetchStatus wykop, SourceFetchStatus reddit) {
		return Map.of(Source.WYKOP, wykop, Source.REDDIT, reddit);
	}

	private void end(UUID id, RunStatus status) {
		jdbc.update("UPDATE pipeline_runs SET status = ? WHERE id = ?", status.value(), id);
	}

	private void assertMissingDays(String body) {
		assertThat(mvc.get().uri(MISSING_DAYS).with(user(admin.toString()).roles(Role.ADMIN.name()))).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo(body);
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
