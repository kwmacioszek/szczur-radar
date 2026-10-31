package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.Cookie;
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
import pl.szczurradar.api.contract.ContractFiles;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.PipelineRunSource;
import pl.szczurradar.api.pipeline.run.PipelineRunSourceRepository;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * {@code GET /api/freshness}: which runs count as a data update of which source (FR-030, as decided for
 * S-05 and S-29) and who may read it. Runs and their sources' results are stored through the entities, and
 * the runs then ended as the pipeline would end them. Each test rolls back.
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

	private static final Result WYKOP_UPDATED = new Result(Source.WYKOP, SourceFetchStatus.SUCCEEDED);

	private static final Result REDDIT_UPDATED = new Result(Source.REDDIT, SourceFetchStatus.SUCCEEDED);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PipelineRunRepository runs;

	@Autowired
	PipelineRunSourceRepository runSources;

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
		assertFreshness();
	}

	@Test
	void aSucceededRangeRunIsTheUpdate() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED);

		assertFreshness(wykop(FIRST));
	}

	@Test
	void aLaterPartialRangeRunIsTheUpdate() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED);
		run(RunKind.RANGE, RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), LATER, WYKOP_UPDATED);

		assertFreshness(wykop(LATER));
	}

	@ParameterizedTest
	@EnumSource(value = RunStatus.class, names = { "FAILED", "INTERRUPTED" })
	void aLaterRangeRunThatDidNotSucceedLeavesTheDateThoughItsSourceSucceeded(RunStatus status) {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED);
		run(RunKind.RANGE, status, SystemAccount.SCHEDULER.id(), LATER, WYKOP_UPDATED);

		assertFreshness(wykop(FIRST));
	}

	@ParameterizedTest
	@EnumSource(value = RunKind.class, names = { "RETRY", "POSTS" })
	void aLaterRunOverLeftoversOrChosenPostsLeavesTheDate(RunKind kind) {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED);
		run(kind, RunStatus.SUCCEEDED, admin, LATER, WYKOP_UPDATED);

		assertFreshness(wykop(FIRST));
	}

	@Test
	void aLaterRangeRunStartedByAnAdminIsTheUpdate() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED);
		run(RunKind.RANGE, RunStatus.SUCCEEDED, admin, LATER, WYKOP_UPDATED);

		assertFreshness(wykop(LATER));
	}

	@Test
	void anAdminsLaterRunOverAnOlderRangeLeavesTheDate() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED);
		run(RunKind.RANGE, RunStatus.SUCCEEDED, admin, LATER, OLDER_RANGE_TO, WYKOP_UPDATED);

		assertFreshness(wykop(FIRST));
	}

	@Test
	void aRunOverANewerRangeIsTheUpdateEvenIfAnOlderRangeEndedLater() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, admin, LATER, OLDER_RANGE_TO, WYKOP_UPDATED);
		run(RunKind.RANGE, RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED);

		assertFreshness(wykop(FIRST));
	}

	@Test
	void aRunOfRedditAloneMovesRedditAndLeavesWykop() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED, REDDIT_UPDATED);
		run(RunKind.RANGE, RunStatus.SUCCEEDED, admin, LATER, REDDIT_UPDATED);

		assertFreshness(wykop(FIRST), reddit(LATER));
	}

	@ParameterizedTest
	@EnumSource(value = SourceFetchStatus.class, names = { "FAILED", "EMPTY" })
	void aPartialRunMovesOnlyTheSourceThatGavePosts(SourceFetchStatus result) {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED, REDDIT_UPDATED);
		run(RunKind.RANGE, RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), LATER, WYKOP_UPDATED,
				new Result(Source.REDDIT, result));

		assertFreshness(wykop(LATER), reddit(FIRST));
	}

	@Test
	void aSourceWithOnlyFailedAndEmptyResultsHasNoEntry() {
		run(RunKind.RANGE, RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED,
				new Result(Source.REDDIT, SourceFetchStatus.FAILED));
		run(RunKind.RANGE, RunStatus.PARTIAL, SystemAccount.SCHEDULER.id(), LATER, WYKOP_UPDATED,
				new Result(Source.REDDIT, SourceFetchStatus.EMPTY));

		assertFreshness(wykop(LATER));
	}

	@Test
	void anAdminsLaterRunOverAnOlderRangeLeavesTheDateOfItsSource() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED, REDDIT_UPDATED);
		run(RunKind.RANGE, RunStatus.SUCCEEDED, admin, LATER, OLDER_RANGE_TO, REDDIT_UPDATED);

		assertFreshness(wykop(FIRST), reddit(FIRST));
	}

	@Test
	void theSourcesComeInTheirOwnOrderWhateverTheRunsOrder() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, admin, FIRST, REDDIT_UPDATED);
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), LATER, WYKOP_UPDATED);

		assertFreshness(wykop(LATER), reddit(FIRST));
	}

	@Test
	void theAnswerIsTheSharedContract() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED, REDDIT_UPDATED);

		MvcTestResult result = mvc.get().uri(FRESHNESS).with(withRole(Role.USER)).exchange();

		assertThat(result).hasStatusOk();
		ContractFiles.assertMatches(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
				"freshness.json");
	}

	@Test
	void everyLoggedInRoleMayReadItAndNobodyElse() {
		run(RunKind.RANGE, RunStatus.SUCCEEDED, SystemAccount.SCHEDULER.id(), FIRST, WYKOP_UPDATED);
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
			.isStrictlyEqualTo(body(wykop(FIRST)));
		assertThat(mvc.get().uri(FRESHNESS).with(withRole(Role.ADMIN))).hasStatusOk();
		assertThat(mvc.get().uri(FRESHNESS)).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"not_authenticated\"}");
	}

	/**
	 * A run of {@code kind} requested by {@code requestedBy}, ended {@code status} at {@code finishedAt}, with
	 * the results of its sources.
	 */
	private void run(RunKind kind, RunStatus status, UUID requestedBy, String finishedAt, Result... results) {
		run(kind, status, requestedBy, finishedAt, RANGE_TO, results);
	}

	/**
	 * As above; a range run covers the two days up to {@code rangeTo}. Every kind and status gets its results,
	 * though only a range run ever has one: what leaves a run out must be the query's own rule on the run.
	 */
	private void run(RunKind kind, RunStatus status, UUID requestedBy, String finishedAt, String rangeTo,
			Result... results) {
		Instant to = Instant.parse(rangeTo);
		List<Source> sources = Arrays.stream(results).map(Result::source).toList();
		PipelineRun run = switch (kind) {
			case RANGE -> PipelineRun.range(to.minusSeconds(2 * 86_400 - 1), to, sources, requestedBy, null);
			case RETRY -> PipelineRun.retry(requestedBy, null);
			case POSTS -> PipelineRun.posts(List.of(UUID.randomUUID()), requestedBy, null);
		};
		UUID id = runs.saveAndFlush(run).getId();
		jdbc.update("UPDATE pipeline_runs SET status = ?, finished_at = ?::timestamptz WHERE id = ?", status.value(),
				finishedAt, id);
		for (Result result : results) {
			runSources.saveAndFlush(new PipelineRunSource(id, result.source(), result.status(),
					result.status() == SourceFetchStatus.SUCCEEDED ? 1 : 0, 0,
					result.status() == SourceFetchStatus.FAILED ? "IllegalStateException: 503" : null));
		}
	}

	/** The answer to a user, with only each source's date: nothing else about the runs. */
	private void assertFreshness(Update... updates) {
		assertThat(mvc.get().uri(FRESHNESS).with(withRole(Role.USER))).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo(body(updates));
	}

	private static Update wykop(String lastUpdatedAt) {
		return new Update(Source.WYKOP, lastUpdatedAt);
	}

	private static Update reddit(String lastUpdatedAt) {
		return new Update(Source.REDDIT, lastUpdatedAt);
	}

	private static String body(Update... updates) {
		return Arrays.stream(updates)
			.map(update -> "{\"source\":\"%s\",\"lastUpdatedAt\":\"%s\"}".formatted(update.source().value(),
					update.lastUpdatedAt()))
			.collect(Collectors.joining(",", "{\"sources\":[", "]}"));
	}

	private static RequestPostProcessor withRole(Role role) {
		return user(UUID.randomUUID().toString()).roles(role.name());
	}

	/** One source's result in a run. */
	private record Result(Source source, SourceFetchStatus status) {
	}

	/** One source's entry in the answer. */
	private record Update(Source source, String lastUpdatedAt) {
	}

}
