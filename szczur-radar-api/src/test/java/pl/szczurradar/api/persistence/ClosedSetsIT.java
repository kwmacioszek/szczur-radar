package pl.szczurradar.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ReasoningEffort;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.notifications.NotificationKind;
import pl.szczurradar.api.pipeline.run.InterruptionCause;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.pipeline.run.StepStatus;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.ContentEncoding;
import pl.szczurradar.api.raw.RawMediaType;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.Role;

/**
 * Every enum stored in the database has a {@code CHECK} on its column that allows exactly its values:
 * a constant without a migration (or a value in a {@code CHECK} without a constant) is red here, not a
 * {@code CHECK} violation in a production run. {@link #REGISTRY} names each enum with its column; it is
 * itself checked against the JPA metamodel (every enum attribute of an entity) and against the
 * migrated schema (every {@code CHECK} with a list of values of one column), so a new enum or a new
 * closed-set column cannot pass unregistered; a {@code CHECK} with literals in any other form must be a
 * known rule ({@link #NOT_A_VALUE_LIST}).
 *
 * <p>The values come from {@code pg_get_constraintdef}, which normalizes {@code x IN (...)} to
 * {@code x = ANY (ARRAY[...])}, a single value to {@code x = '...'}, and keeps an array column's
 * {@code x <@ ARRAY[...]} (inside the compound {@code ck_pipeline_runs_sources}). Other compound
 * {@code CHECK}s that mention enum values ({@code ck_posts_stage_attempts}, {@code ck_assessments_outcome},
 * ...) are not lists of values and are not read.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
class ClosedSetsIT {

	private static final String HINT = "A new value needs a Flyway migration widening the column's CHECK;"
			+ " a stored constant is never renamed or removed (append-only).";

	/**
	 * Each stored enum with the value it is written as (the method reference of its converter, or of the
	 * write for {@code pipeline_runs.sources}), its column and the entity attribute that holds it.
	 */
	private static final List<StoredEnum> REGISTRY = List.of(
			StoredEnum.of(PostStage.class, PostStage::value, "posts", "stage", "Post", "stage"),
			StoredEnum.of(ItemType.class, ItemType::value, "posts", "item_type", "Post", "itemType"),
			StoredEnum.of(Source.class, Source::value, "posts", "source", "Post", "source"),
			StoredEnum.of(Source.class, Source::value, "raw_payloads", "source", "RawPayload", "source"),
			StoredEnum.of(ContentEncoding.class, ContentEncoding::value, "raw_payloads", "content_encoding",
					"RawPayload", "contentEncoding"),
			StoredEnum.of(RawMediaType.class, RawMediaType::value, "raw_payloads", "media_type", "RawPayload",
					"mediaType"),
			// A List<String> of Source::value (PipelineRun's constructor), no converter and no enum attribute.
			StoredEnum.of(Source.class, Source::value, "pipeline_runs", "sources", null, null),
			StoredEnum.of(RunStatus.class, RunStatus::value, "pipeline_runs", "status", "PipelineRun", "status"),
			StoredEnum.of(RunKind.class, RunKind::value, "pipeline_runs", "kind", "PipelineRun", "kind"),
			StoredEnum.of(InterruptionCause.class, InterruptionCause::value, "pipeline_runs", "interruption_cause",
					"PipelineRun", "interruptionCause"),
			StoredEnum.of(StepStatus.class, StepStatus::value, "pipeline_run_steps", "status", "PipelineRunStep",
					"status"),
			StoredEnum.of(RunStep.class, RunStep::value, "pipeline_run_steps", "step", "PipelineRunStep", "step"),
			StoredEnum.of(Source.class, Source::value, "pipeline_run_sources", "source", "PipelineRunSource", "source"),
			StoredEnum.of(SourceFetchStatus.class, SourceFetchStatus::value, "pipeline_run_sources", "status",
					"PipelineRunSource", "status"),
			StoredEnum.of(AssessmentResult.Status.class, AssessmentResult.Status::value, "assessments", "status",
					"Assessment", "status"),
			StoredEnum.of(FailureClass.class, FailureClass::value, "assessments", "failure_class", "Assessment",
					"failureClass"),
			StoredEnum.of(Provenance.Source.class, Provenance.Source::value, "assessments", "result_source",
					"Assessment", "resultSource"),
			StoredEnum.of(Provider.class, Provider::id, "assessments", "provider", "Assessment", "provider"),
			StoredEnum.of(Channel.class, Channel::value, "assessments", "channel", "Assessment", "channel"),
			StoredEnum.of(ServiceTier.class, ServiceTier::id, "assessments", "service_tier", "Assessment",
					"serviceTier"),
			StoredEnum.of(ReasoningEffort.class, ReasoningEffort::id, "assessments", "reasoning_effort",
					"Assessment", "reasoningEffort"),
			StoredEnum.of(OutputMode.class, OutputMode::value, "assessments", "output_mode", "Assessment",
					"outputMode"),
			StoredEnum.of(NotificationKind.class, NotificationKind::value, "notifications", "kind", "Notification",
					"kind"),
			StoredEnum.of(Role.class, Role::value, "users", "role", "User", "role"));

	/**
	 * The {@code CHECK}s with string literals that are not a list of values: rules across columns,
	 * patterns and the non-empty value of an open set ({@code ck_posts_channel}: any subreddit). Any other
	 * {@code CHECK} with a literal must read as a list of values, so a closed set written in another form (a
	 * {@code VARCHAR} column, {@code x IS NULL OR x IN (...)}) cannot pass unseen.
	 */
	private static final Set<String> NOT_A_VALUE_LIST = Set.of("ck_activation_tokens_token_hash",
			"ck_assessments_input_hash", "ck_assessments_outcome", "ck_notifications_run",
			"ck_pipeline_run_sources_error", "ck_pipeline_runs_interruption_status", "ck_pipeline_runs_range",
			"ck_pipeline_runs_requested_posts", "ck_posts_assessing_owner", "ck_posts_channel", "ck_posts_content_hash",
			"ck_posts_stage_attempts", "ck_raw_payloads_sha256");

	private static final Pattern ANY_LITERAL = Pattern.compile("'(?:[^']|'')*'::");

	private static final String LITERAL = "'(?:[^']|'')*'::text";

	private static final String LITERALS = LITERAL + "(?:, " + LITERAL + ")*";

	/** The whole definition: {@code CHECK ((x = ANY (ARRAY['a'::text, 'b'::text])))}. */
	private static final Pattern ANY_OF = Pattern
		.compile("CHECK \\(\\((\\w+) = ANY \\(ARRAY\\[(" + LITERALS + ")\\]\\)\\)\\)");

	/** The whole definition: {@code CHECK ((x = 'a'::text))}. */
	private static final Pattern ONE_OF = Pattern.compile("CHECK \\(\\((\\w+) = (" + LITERAL + ")\\)\\)");

	/** Anywhere in the definition: {@code (x <@ ARRAY['a'::text])}, an array column's values. */
	private static final Pattern CONTAINED_IN = Pattern.compile("\\((\\w+) <@ ARRAY\\[(" + LITERALS + ")\\]\\)");

	private static final Pattern VALUE = Pattern.compile("'((?:[^']|'')*)'::text");

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Test
	void everyStoredEnumEqualsTheCheckOfItsColumn() {
		Map<String, List<ValueListCheck>> checksByColumn = valueListChecks().stream()
			.collect(Collectors.groupingBy(ValueListCheck::where));
		List<String> mismatches = new ArrayList<>();
		for (StoredEnum stored : REGISTRY) {
			List<ValueListCheck> checks = checksByColumn.getOrDefault(stored.where(), List.of());
			if (checks.size() != 1) {
				mismatches.add(stored.where() + " (" + stored.enumName() + "): expected one CHECK with its list of"
						+ " values, found " + checks.stream().map(ValueListCheck::name).toList());
				continue;
			}
			Set<String> allowed = checks.getFirst().values();
			Set<String> missingFromCheck = difference(stored.values(), allowed);
			Set<String> missingFromEnum = difference(allowed, stored.values());
			if (!missingFromCheck.isEmpty() || !missingFromEnum.isEmpty()) {
				mismatches.add(stored.where() + " (" + stored.enumName() + ", " + checks.getFirst().name()
						+ "): missing from the CHECK: " + missingFromCheck + ", missing from the enum: "
						+ missingFromEnum);
			}
		}

		assertThat(mismatches).withFailMessage("%s",
				"Stored enums whose column's CHECK differs:\n  " + String.join("\n  ", mismatches) + "\n" + HINT)
			.isEmpty();
	}

	@Test
	void everyEnumAttributeOfAnEntityIsInTheRegistry() {
		Set<String> enumAttributes = entityManagerFactory.getMetamodel()
			.getEntities()
			.stream()
			.flatMap(entity -> entity.getAttributes()
				.stream()
				.filter(ClosedSetsIT::holdsAnEnum)
				.map(attribute -> entity.getName() + "." + attribute.getName()))
			.collect(Collectors.toCollection(TreeSet::new));
		Set<String> registered = REGISTRY.stream()
			.filter(stored -> stored.entity() != null)
			.map(stored -> stored.entity() + "." + stored.attribute())
			.collect(Collectors.toCollection(TreeSet::new));

		Set<String> unregistered = difference(enumAttributes, registered);
		Set<String> unknown = difference(registered, enumAttributes);
		assertThat(unregistered).withFailMessage("%s", "Enum attributes of entities missing from ClosedSetsIT's"
				+ " registry: " + unregistered + " — add an entry with the enum, its stored value and its column")
			.isEmpty();
		assertThat(unknown).withFailMessage("%s", "Registry entries with no such enum attribute: " + unknown)
			.isEmpty();
	}

	@Test
	void everyValueListCheckIsInTheRegistry() {
		Set<String> registered = REGISTRY.stream()
			.map(StoredEnum::where)
			.collect(Collectors.toCollection(TreeSet::new));
		List<ValueListCheck> checks = valueListChecks();

		List<String> unregistered = checks.stream()
			.filter(check -> !registered.contains(check.where()))
			.map(check -> check.name() + " on " + check.where() + " " + check.values())
			.toList();
		Set<String> withoutCheck = difference(registered,
				checks.stream().map(ValueListCheck::where).collect(Collectors.toSet()));
		assertThat(unregistered).withFailMessage("%s", "CHECKs with a list of values missing from ClosedSetsIT's"
				+ " registry: " + unregistered + " — a closed-set column must have an enum (lesson: closed value"
				+ " sets live in enums); add the enum and its entry here")
			.isEmpty();
		assertThat(withoutCheck).withFailMessage("%s",
				"Registry entries whose column has no CHECK with a list of values: " + withoutCheck)
			.isEmpty();
	}

	@Test
	void everyCheckWithLiteralsIsAValueListOrKnown() {
		List<Check> checks = checks();
		List<String> unread = checks.stream()
			.filter(check -> ANY_LITERAL.matcher(check.definition()).find())
			.filter(check -> valueList(check.definition()).isEmpty() && !NOT_A_VALUE_LIST.contains(check.name()))
			.map(check -> check.name() + " on " + check.table() + ": " + check.definition())
			.toList();
		Set<String> gone = difference(NOT_A_VALUE_LIST,
				checks.stream().map(Check::name).collect(Collectors.toSet()));

		assertThat(unread).withFailMessage("%s", "CHECKs with literals that read neither as a list of values nor"
				+ " as a known rule: " + unread + " — a closed set is a TEXT column with CHECK (x IN (...)) and an"
				+ " enum in the registry; a rule across columns or a pattern goes into NOT_A_VALUE_LIST")
			.isEmpty();
		assertThat(gone).withFailMessage("%s", "NOT_A_VALUE_LIST names CHECKs the schema no longer has: " + gone)
			.isEmpty();
	}

	/** Today's three forms, so a change in Postgres's output is red here, never an empty set read as green. */
	@Test
	void theCheckParserReadsEveryFormOfAListOfValues() {
		assertThat(valueList(definition("posts", "ck_posts_stage"))).hasValueSatisfying(list -> {
			assertThat(list.column()).isEqualTo("stage");
			assertThat(list.values()).contains("stored", "assessing", "assessed", "failed", "dead");
		});
		assertThat(valueList(definition("posts", "ck_posts_source"))).hasValueSatisfying(list -> {
			assertThat(list.column()).isEqualTo("source");
			assertThat(list.values()).contains("wykop");
		});
		assertThat(valueList(definition("pipeline_runs", "ck_pipeline_runs_sources"))).hasValueSatisfying(list -> {
			assertThat(list.column()).isEqualTo("sources");
			assertThat(list.values()).contains("wykop");
		});
		// Compound CHECKs with enum values and other single-column CHECKs are not lists of values.
		assertThat(valueList(definition("posts", "ck_posts_assessing_owner"))).isEmpty();
		assertThat(valueList(definition("posts", "ck_posts_stage_attempts"))).isEmpty();
		assertThat(valueList(definition("posts", "ck_posts_content_hash"))).isEmpty();
		assertThat(valueList(definition("users", "ck_users_email_lower"))).isEmpty();
	}

	/** Every {@code CHECK} of the application's tables that is a list of values of one column. */
	private List<ValueListCheck> valueListChecks() {
		List<ValueListCheck> lists = new ArrayList<>();
		for (Check check : checks()) {
			valueList(check.definition()).ifPresent(
					list -> lists.add(new ValueListCheck(check.name(), check.table(), list.column(), list.values())));
		}
		return lists;
	}

	/** Every {@code CHECK} of the application's tables with its definition. */
	private List<Check> checks() {
		return jdbc.query("SELECT t.relname, c.conname, pg_get_constraintdef(c.oid) FROM pg_constraint c"
				+ " JOIN pg_class t ON t.oid = c.conrelid JOIN pg_namespace n ON n.oid = t.relnamespace"
				+ " WHERE c.contype = 'c' AND n.nspname = 'public' ORDER BY t.relname, c.conname",
				(row, index) -> new Check(row.getString(1), row.getString(2), row.getString(3)));
	}

	private String definition(String table, String constraint) {
		return jdbc.queryForObject(
				"SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = ?::regclass AND conname = ?",
				String.class, table, constraint);
	}

	/** The column and its allowed values when {@code definition} is a list of values; otherwise empty. */
	private static Optional<ValueList> valueList(String definition) {
		for (Pattern whole : List.of(ANY_OF, ONE_OF)) {
			Matcher matcher = whole.matcher(definition);
			if (matcher.matches()) {
				return Optional.of(new ValueList(matcher.group(1), values(matcher.group(2))));
			}
		}
		Matcher contained = CONTAINED_IN.matcher(definition);
		return contained.find() ? Optional.of(new ValueList(contained.group(1), values(contained.group(2))))
				: Optional.empty();
	}

	private static Set<String> values(String literals) {
		Set<String> values = new TreeSet<>();
		Matcher matcher = VALUE.matcher(literals);
		while (matcher.find()) {
			values.add(matcher.group(1).replace("''", "'"));
		}
		return values;
	}

	private static boolean holdsAnEnum(Attribute<?, ?> attribute) {
		return attribute.getJavaType().isEnum() || attribute instanceof PluralAttribute<?, ?, ?> plural
				&& plural.getElementType().getJavaType().isEnum();
	}

	private static Set<String> difference(Set<String> from, Set<String> without) {
		return from.stream().filter(value -> !without.contains(value)).collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * An enum stored in {@code table.column}, as its {@code values}; {@code entity} and {@code attribute}
	 * are its JPA entity name and attribute ({@code null} when no enum attribute holds it).
	 */
	private record StoredEnum(Class<?> type, Set<String> values, String table, String column,
			@Nullable String entity, @Nullable String attribute) {

		static <E extends Enum<E>> StoredEnum of(Class<E> type, Function<E, String> value, String table,
				String column, @Nullable String entity, @Nullable String attribute) {
			Set<String> values = Arrays.stream(type.getEnumConstants())
				.map(value)
				.collect(Collectors.toCollection(TreeSet::new));
			return new StoredEnum(type, values, table, column, entity, attribute);
		}

		String where() {
			return table + "." + column;
		}

		String enumName() {
			return type.getName().substring("pl.szczurradar.api.".length()).replace('$', '.');
		}

	}

	private record ValueList(String column, Set<String> values) {
	}

	private record Check(String table, String name, String definition) {
	}

	private record ValueListCheck(String name, String table, String column, Set<String> values) {

		String where() {
			return table + "." + column;
		}

	}

}
