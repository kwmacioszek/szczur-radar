package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

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
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.wykop.WykopConfiguration;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * {@code GET /api/admin/pipeline-runs/missing-days} on Postgres with a fixed clock (today is 24 October
 * 2026 in Warsaw, so the window is 9–22 October): which runs cover a day and the exact answer. Wykop is
 * configured with keys no call uses (no run executes here). Runs are stored through the entities and
 * then ended as the pipeline would end them. Each test rolls back. Who may read it:
 * {@code AuthorizationCompletenessIT} and {@code AdminRoleIT} ({@code ADMIN_ROUTES}).
 */
@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY, WykopConfiguration.KEY + "=test-key",
		WykopConfiguration.SECRET + "=test-secret" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MissingDaysIT.FixedClock.class })
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

	private UUID admin;

	@BeforeEach
	void setUp() {
		// The other tests of this context share the database: within this test's transaction no earlier
		// run covers a day, so only the runs of this test count.
		jdbc.update("UPDATE pipeline_runs SET status = ? WHERE status IN (?, ?)", RunStatus.FAILED.value(),
				RunStatus.SUCCEEDED.value(), RunStatus.PARTIAL.value());
		admin = users.saveAndFlush(new User("missing-days-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN))
			.getId();
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
	 * ended {@code status}.
	 */
	private void range(RunStatus status, UUID requestedBy, String from, String to) {
		PipelineRun run = PipelineRun.range(LocalDate.parse(from).atStartOfDay(PipelineRunService.ZONE).toInstant(),
				LocalDate.parse(to).atTime(LocalTime.of(23, 59, 59)).atZone(PipelineRunService.ZONE).toInstant(),
				List.of(Source.WYKOP), requestedBy, null);
		end(runs.saveAndFlush(run).getId(), status);
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
