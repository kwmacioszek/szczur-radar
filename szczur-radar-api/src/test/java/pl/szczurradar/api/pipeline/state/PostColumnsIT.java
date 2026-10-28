package pl.szczurradar.api.pipeline.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * Every column of {@code posts} is either set once by the {@code INSERT} ({@link #SET_ONCE}) or part of
 * the post's assessment state ({@link #STATE}), and the conditional writes of {@link PostTransitions}
 * are meant to be the only {@code UPDATE}s of it. A new column cannot pass unclassified, and a state column cannot
 * pass without its value after every transition: {@link #SCENARIOS} gives, for each public method of
 * {@link PostTransitions}, a post in a "dirty" state (technical failures and their message set, failed
 * attempts for the release's {@code CASE}) and the value of every state column after it, as
 * {@code pipeline/CLAUDE.md} describes the transition ({@code state/PostTransitions}, Rules: "Attempts:
 * three faults", "On hold"). Each scenario also checks that every other column is unchanged and that
 * {@code updated_at} moved.
 *
 * <p>Not {@code @Transactional}: {@code now()} is the start of the transaction, so the transition must
 * run in its own. Each scenario stores its own user, run, raw payload and post, sets {@code updated_at}
 * a day back, and deletes them afterwards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
class PostColumnsIT {

	private static final String UPDATED_AT = "updated_at";

	/** Written by the {@code INSERT} only ({@code step/PostStore}); no transition may change them. */
	private static final Set<String> SET_ONCE = Set.of("id", "source", "item_type", "source_item_id", "published_at",
			"title", "body", "author", "slug", "web_url", "tags", "raw_payload_id", "content_hash", "first_run_id",
			"created_at");

	/** The post's assessment state: what {@link PostTransitions} writes. */
	private static final Set<String> STATE = Set.of("stage", "assessment_attempts", "assessing_run_id",
			"technical_failures", "last_technical_error", UPDATED_AT);

	private static final String EARLIER_ERROR = "IllegalStateException: an earlier bug";

	private static final String ERROR = "LlmUnavailableException: the provider is down";

	private static final Instant ASSESSED_AT = Instant.parse("2026-10-06T10:00:00Z");

	/**
	 * Each transition with the post's state before it and every state column (but {@code updated_at})
	 * after it; {@code run} is the scenario's run.
	 */
	private static final List<Scenario> SCENARIOS = List.of(
			new Scenario("claim", "claim", Given.free(PostStage.STORED, 0, RunStatus.RUNNING),
					(transitions, post, run) -> transitions.claim(post, run).isPresent(),
					run -> row(PostStage.ASSESSING, 0, run, 2, EARLIER_ERROR)),
			new Scenario("record ok", "record", Given.held(1, RunStatus.RUNNING),
					(transitions, post, run) -> transitions.record(post, run, 1,
							new Assessment(post, run, ContractFixtures.validInput(), ok())),
					run -> row(PostStage.ASSESSED, 1, null, 0, null)),
			new Scenario("record failed", "record", Given.held(1, RunStatus.RUNNING),
					(transitions, post, run) -> transitions.record(post, run, 1,
							new Assessment(post, run, ContractFixtures.validInput(), failed())),
					run -> row(PostStage.FAILED, 2, null, 0, null)),
			new Scenario("record failed third (dead)", "record", Given.held(2, RunStatus.RUNNING),
					(transitions, post, run) -> transitions.record(post, run, 2,
							new Assessment(post, run, ContractFixtures.validInput(), failed())),
					run -> row(PostStage.DEAD, 3, null, 0, null)),
			new Scenario("release", "release", Given.held(1, RunStatus.RUNNING),
					(transitions, post, run) -> transitions.release(post, run),
					run -> row(PostStage.FAILED, 1, null, 2, EARLIER_ERROR)),
			new Scenario("release never failed", "release", Given.held(0, RunStatus.RUNNING),
					(transitions, post, run) -> transitions.release(post, run),
					run -> row(PostStage.STORED, 0, null, 2, EARLIER_ERROR)),
			new Scenario("releaseFailed", "releaseFailed", Given.held(1, RunStatus.RUNNING),
					(transitions, post, run) -> transitions.releaseFailed(post, run, ERROR).isPresent(),
					run -> row(PostStage.FAILED, 1, null, 3, ERROR)),
			new Scenario("countTechnicalFailure", "countTechnicalFailure",
					Given.free(PostStage.FAILED, 1, RunStatus.RUNNING),
					(transitions, post, run) -> transitions.countTechnicalFailure(post, run, ERROR),
					run -> row(PostStage.FAILED, 1, null, 3, ERROR)),
			new Scenario("releaseAll", "releaseAll", Given.held(1, RunStatus.RUNNING),
					(transitions, post, run) -> transitions.releaseAll(run) > 0,
					run -> row(PostStage.FAILED, 1, null, 2, EARLIER_ERROR)),
			// The run ended without putting its post back (an instance stopped mid-claim).
			new Scenario("releaseOrphans", "releaseOrphans", Given.held(1, RunStatus.FAILED),
					(transitions, post, run) -> transitions.releaseOrphans() > 0,
					run -> row(PostStage.FAILED, 1, null, 2, EARLIER_ERROR)));

	@Autowired
	PostTransitions transitions;

	@Autowired
	PostRepository posts;

	@Autowired
	PipelineRunRepository runs;

	@Autowired
	UserRepository users;

	@Autowired
	RawPayloadStore rawPayloads;

	@Autowired
	JdbcTemplate jdbc;

	/** What the current scenario stored so far, for {@link #deleteTheScenariosRows()}. */
	private final Rows stored = new Rows();

	@Test
	void everyPostColumnIsClassified() {
		Set<String> columns = new TreeSet<>(jdbc.queryForList("SELECT column_name FROM information_schema.columns"
				+ " WHERE table_schema = 'public' AND table_name = 'posts'", String.class));
		Set<String> classified = Stream.concat(SET_ONCE.stream(), STATE.stream())
			.collect(Collectors.toCollection(TreeSet::new));

		Set<String> both = SET_ONCE.stream().filter(STATE::contains).collect(Collectors.toCollection(TreeSet::new));
		assertThat(both).withFailMessage("%s", "Columns both SET_ONCE and STATE in PostColumnsIT: " + both
				+ " — a column is either set once by the INSERT or part of the post's state")
			.isEmpty();
		Set<String> unclassified = difference(columns, classified);
		assertThat(unclassified).withFailMessage("%s", "Columns of posts not classified in PostColumnsIT: "
				+ unclassified + " — classify each here: SET_ONCE when only the INSERT writes it, STATE when it is"
				+ " the post's assessment state; a state column also needs its value after every transition in"
				+ " SCENARIOS and its handling in PostTransitions")
			.isEmpty();
		Set<String> gone = difference(classified, columns);
		assertThat(gone).withFailMessage("%s", "Columns classified in PostColumnsIT that posts no longer has: "
				+ gone + " — remove them here")
			.isEmpty();
	}

	@Test
	void everyTransitionHasAScenario() {
		Set<String> transitionMethods = Arrays.stream(PostTransitions.class.getDeclaredMethods())
			.filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
			.map(Method::getName)
			.collect(Collectors.toCollection(TreeSet::new));
		Set<String> covered = SCENARIOS.stream().map(Scenario::method).collect(Collectors.toCollection(TreeSet::new));

		Set<String> uncovered = difference(transitionMethods, covered);
		assertThat(uncovered).withFailMessage("%s", "Public methods of PostTransitions without a scenario in"
				+ " PostColumnsIT: " + uncovered + " — add one to SCENARIOS with the post's state before it and"
				+ " every state column's value after it")
			.isEmpty();
		Set<String> unknown = difference(covered, transitionMethods);
		assertThat(unknown).withFailMessage("%s", "Scenarios naming no public method of PostTransitions: " + unknown)
			.isEmpty();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("scenarios")
	void aTransitionLeavesTheExpectedRow(Scenario scenario) {
		Ids ids = given(scenario.given());
		Map<String, @Nullable Object> expected = scenario.expected().apply(ids.run());
		Set<String> stateColumns = difference(STATE, Set.of(UPDATED_AT));
		Set<String> missing = difference(stateColumns, expected.keySet());
		assertThat(missing).withFailMessage("%s", "Scenario '" + scenario + "' has no expected value for the"
				+ " state columns " + missing + " — add each one's value after the transition (and handle the"
				+ " column in PostTransitions)")
			.isEmpty();
		assertThat(difference(expected.keySet(), stateColumns)).as("%s: expected columns that are no state", scenario)
			.isEmpty();
		String setOnceBefore = unchangeable(ids.post());
		Instant updatedBefore = updatedAt(ids.post());

		boolean changed = scenario.transition().apply(transitions, ids.post(), ids.run());

		assertThat(changed).as("%s: the transition changed the post", scenario).isTrue();
		assertThat(state(ids.post(), stateColumns)).as("%s: the state columns after it", scenario)
			.isEqualTo(new TreeMap<>(expected));
		assertThat(unchangeable(ids.post())).as("%s: every column set once is unchanged", scenario)
			.isEqualTo(setOnceBefore);
		assertThat(updatedAt(ids.post())).as("%s: updated_at moved", scenario).isAfter(updatedBefore);
	}

	static List<Scenario> scenarios() {
		return SCENARIOS;
	}

	/** Deletes what the scenario stored, also when {@link #given} failed halfway. */
	@AfterEach
	void deleteTheScenariosRows() {
		if (stored.post != null) {
			jdbc.update("DELETE FROM assessments WHERE post_id = ?", stored.post);
			jdbc.update("DELETE FROM posts WHERE id = ?", stored.post);
		}
		if (stored.rawPayload != null) {
			jdbc.update("DELETE FROM raw_payloads WHERE id = ?", stored.rawPayload);
		}
		if (stored.run != null) {
			jdbc.update("DELETE FROM pipeline_runs WHERE id = ?", stored.run);
		}
		if (stored.user != null) {
			jdbc.update("DELETE FROM users WHERE id = ?", stored.user);
		}
	}

	/**
	 * Stores a user, a run in {@code given.run()} and a post in the given state, {@code updated_at} a day
	 * back; each id is kept as soon as its row exists, so the clean-up finds it.
	 */
	private Ids given(Given given) {
		UUID user = users.save(new User("post-columns-" + UUID.randomUUID() + "@example.com", null,
				Role.ADMIN)).getId();
		stored.user = user;
		UUID run = runs.save(PipelineRun.retry(user, null)).getId();
		stored.run = run;
		String sourceItemId = UUID.randomUUID().toString();
		RawPayloadRef raw = rawPayloads.store(Source.WYKOP,
				("{\"id\":\"" + sourceItemId + "\"}").getBytes(StandardCharsets.UTF_8));
		stored.rawPayload = raw.id();
		UUID post = posts.save(new Post(Source.WYKOP, ItemType.POST, sourceItemId,
				Instant.parse("2026-10-01T08:00:00Z"), "tytuł", "treść", "autor", "slug",
				"https://wykop.pl/wpis/" + sourceItemId, List.of("gielda", "akcje"), raw, run)).getId();
		stored.post = post;

		if (given.run() == RunStatus.RUNNING) {
			// At most one run runs (uq_pipeline_runs_one_running): one another test left running is interrupted.
			jdbc.update("UPDATE pipeline_runs SET status = ?, finished_at = now() WHERE status = ?",
					RunStatus.INTERRUPTED.value(), RunStatus.RUNNING.value());
		}
		jdbc.update("UPDATE pipeline_runs SET status = ?, started_at = now(), finished_at = ? WHERE id = ?",
				given.run().value(), given.run() == RunStatus.RUNNING ? null : Timestamp.from(Instant.now()), run);
		jdbc.update("UPDATE posts SET stage = ?, assessment_attempts = ?, assessing_run_id = ?, technical_failures = 2,"
				+ " last_technical_error = ?, updated_at = now() - interval '1 day' WHERE id = ?",
				given.stage().value(), given.attempts(), given.held() ? run : null, EARLIER_ERROR, post);
		return new Ids(run, post);
	}

	/** The state columns but {@code updated_at}, read from the table. */
	private Map<String, @Nullable Object> state(UUID post, Set<String> stateColumns) {
		return new TreeMap<>(jdbc.queryForMap("SELECT " + String.join(", ", stateColumns) + " FROM posts WHERE id = ?",
				post));
	}

	/**
	 * Every column of the post but its state, as the database's JSON of the whole row (not chosen fields;
	 * the driver's text of an array is not stable between reads).
	 */
	private String unchangeable(UUID post) {
		return jdbc.queryForObject("SELECT (to_jsonb(p) - ?::text[])::text FROM posts p WHERE id = ?", String.class,
				STATE.toArray(String[]::new), post);
	}

	private Instant updatedAt(UUID post) {
		return jdbc.queryForObject("SELECT updated_at FROM posts WHERE id = ?", Timestamp.class, post).toInstant();
	}

	/** The state columns but {@code updated_at} after a transition. */
	private static Map<String, @Nullable Object> row(PostStage stage, int attempts, @Nullable UUID owner,
			int technicalFailures, @Nullable String lastTechnicalError) {
		Map<String, @Nullable Object> row = new HashMap<>();
		row.put("stage", stage.value());
		row.put("assessment_attempts", attempts);
		row.put("assessing_run_id", owner);
		row.put("technical_failures", technicalFailures);
		row.put("last_technical_error", lastTechnicalError);
		return row;
	}

	private static Set<String> difference(Set<String> from, Set<String> without) {
		return from.stream().filter(value -> !without.contains(value)).collect(Collectors.toCollection(TreeSet::new));
	}

	private static AssessmentResult ok() {
		return new AssessmentResult(AssessmentResult.Status.OK, ContractFixtures.validOutput(), null, List.of(),
				Provenance.prefilter("V2", ASSESSED_AT), null);
	}

	private static AssessmentResult failed() {
		return new AssessmentResult(AssessmentResult.Status.FAILED, null, FailureClass.SCHEMA_VIOLATION,
				List.of(new Violation(FailureClass.SCHEMA_VIOLATION, "schema:type", "$", "bad")),
				new Provenance(Provenance.Source.MODEL, Provider.GEMINI, "gemini-3.1-flash-lite", null, ServiceTier.DEFAULT,
						false, null, null, "p1", "V2", ASSESSED_AT, null, null, null, 1_000, 1),
				"{}");
	}

	/** A call of one {@link PostTransitions} method; returns whether it says it changed the post. */
	@FunctionalInterface
	interface Transition {

		boolean apply(PostTransitions transitions, UUID post, UUID run);

	}

	/**
	 * The post before a transition: its stage and failed attempts, held by the scenario's run or not, with
	 * two technical failures and {@link PostColumnsIT#EARLIER_ERROR}; the run in {@code run}.
	 */
	record Given(PostStage stage, int attempts, boolean held, RunStatus run) {

		/** Assessing, held by the run. */
		static Given held(int attempts, RunStatus run) {
			return new Given(PostStage.ASSESSING, attempts, true, run);
		}

		/** Not held by any run. */
		static Given free(PostStage stage, int attempts, RunStatus run) {
			return new Given(stage, attempts, false, run);
		}

	}

	/** One row of the matrix: {@code method} is the {@link PostTransitions} method it calls. */
	record Scenario(String name, String method, Given given, Transition transition,
			Function<UUID, Map<String, @Nullable Object>> expected) {

		@Override
		public String toString() {
			return name;
		}

	}

	/** The scenario's run and post. */
	private record Ids(UUID run, UUID post) {
	}

	/** The rows a scenario stored so far ({@code null} until stored). */
	private static final class Rows {

		@Nullable UUID user;

		@Nullable UUID run;

		@Nullable UUID rawPayload;

		@Nullable UUID post;

	}

}
