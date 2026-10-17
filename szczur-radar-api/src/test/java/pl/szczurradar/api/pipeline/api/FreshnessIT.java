package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.config.SessionConfig;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * {@code GET /api/freshness}: which runs count as a data update (FR-030, as decided for S-05) and who
 * may read it. Runs are stored through the entities and then ended as the pipeline would end them.
 * Each test rolls back.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FreshnessIT {

	private static final String FRESHNESS = "/api/freshness";

	private static final String PASSWORD = "correct horse battery";

	private static final String FIRST = "2026-10-11T00:41:07Z";

	private static final String LATER = "2026-10-12T00:39:52Z";

	/** The end of the range of {@link #run}'s range runs, unless a test passes another. */
	private static final String RANGE_TO = "2026-10-10T21:59:59Z";

	/** The end of a range a week older, as an admin's backfill. */
	private static final String OLDER_RANGE_TO = "2026-10-03T21:59:59Z";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PipelineRunRepository runs;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	private UUID admin;

	@BeforeEach
	void setUp() {
		// The other tests of this context share the database: within this test's transaction no earlier
		// run has ended, so only the runs of this test count.
		jdbc.update("UPDATE pipeline_runs SET finished_at = NULL");
		admin = users.saveAndFlush(new User("freshness-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN)).getId();
	}

	@Test
	void withoutRunsThereIsNoDate() {
		assertFreshness(null);
	}

	@Test
	void aSucceededRangeRunIsTheUpdate() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST);

		assertFreshness(FIRST);
	}

	@Test
	void aLaterPartialRangeRunIsTheUpdate() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST);
		run(RunKind.RANGE, RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), LATER);

		assertFreshness(LATER);
	}

	@ParameterizedTest
	@EnumSource(value = RunStatus.class, names = { "FAILED", "INTERRUPTED" })
	void aLaterRangeRunThatDidNotSucceedLeavesTheDate(RunStatus status) {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST);
		run(RunKind.RANGE, status, SystemAccount.SCHEDULER.id(), LATER);

		assertFreshness(FIRST);
	}

	@ParameterizedTest
	@EnumSource(value = RunKind.class, names = { "RETRY", "POSTS" })
	void aLaterRunOverLeftoversOrChosenPostsLeavesTheDate(RunKind kind) {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST);
		run(kind, RunStatus.SUCCEEDED, admin, LATER);

		assertFreshness(FIRST);
	}

	@Test
	void aLaterRangeRunStartedByAnAdminIsTheUpdate() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST);
		run(RunKind.RANGE, RunStatus.SUCCEEDED, admin, LATER);

		assertFreshness(LATER);
	}

	@Test
	void anAdminsLaterRunOverAnOlderRangeLeavesTheDate() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST);
		run(RunKind.RANGE, RunStatus.SUCCEEDED, admin, LATER, OLDER_RANGE_TO);

		assertFreshness(FIRST);
	}

	@Test
	void aRunOverANewerRangeIsTheUpdateEvenIfAnOlderRangeEndedLater() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, admin, LATER, OLDER_RANGE_TO);
		run(RunKind.RANGE, RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), FIRST);

		assertFreshness(FIRST);
	}

	@Test
	void everyLoggedInRoleMayReadItAndNobodyElse() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST);
		String email = "freshness-user-" + UUID.randomUUID() + "@example.com";
		users.saveAndFlush(new User(email, passwordEncoder.encode(PASSWORD), Role.USER));
		MvcTestResult login = mvc.post()
			.uri("/api/auth/login")
			.header("X-Real-IP", "192.0.2.30")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
			.exchange();
		assertThat(login).hasStatusOk();
		Cookie session = login.getResponse().getCookie(SessionConfig.COOKIE_NAME);
		assertThat(session).isNotNull();

		assertThat(mvc.get().uri(FRESHNESS).cookie(session)).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo(body(FIRST));
		assertThat(mvc.get().uri(FRESHNESS).with(withRole(Role.ADMIN))).hasStatusOk();
		assertThat(mvc.get().uri(FRESHNESS)).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"not_authenticated\"}");
	}

	/** A run of {@code kind} requested by {@code requestedBy}, ended {@code status} at {@code finishedAt}. */
	private void run(RunKind kind, RunStatus status, UUID requestedBy, String finishedAt) {
		run(kind, status, requestedBy, finishedAt, RANGE_TO);
	}

	/** As above; a range run covers the two days up to {@code rangeTo}. */
	private void run(RunKind kind, RunStatus status, UUID requestedBy, String finishedAt, String rangeTo) {
		Instant to = Instant.parse(rangeTo);
		PipelineRun run = switch (kind) {
			case RANGE ->
				PipelineRun.range(to.minusSeconds(2 * 86_400 - 1), to, List.of(Source.WYKOP), requestedBy, null);
			case RETRY -> PipelineRun.retry(requestedBy, null);
			case POSTS -> PipelineRun.posts(List.of(UUID.randomUUID()), requestedBy, null);
		};
		UUID id = runs.saveAndFlush(run).getId();
		jdbc.update("UPDATE pipeline_runs SET status = ?, finished_at = ?::timestamptz WHERE id = ?", status.value(),
				finishedAt, id);
	}

	/** The answer to a user, with only the date: nothing else about the runs. */
	private void assertFreshness(@Nullable String lastUpdatedAt) {
		assertThat(mvc.get().uri(FRESHNESS).with(withRole(Role.USER))).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo(body(lastUpdatedAt));
	}

	private static String body(@Nullable String lastUpdatedAt) {
		return lastUpdatedAt == null ? "{\"lastUpdatedAt\":null}" : "{\"lastUpdatedAt\":\"%s\"}".formatted(lastUpdatedAt);
	}

	private static RequestPostProcessor withRole(Role role) {
		return user(UUID.randomUUID().toString()).roles(role.name());
	}

}
