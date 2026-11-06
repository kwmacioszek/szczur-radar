package pl.szczurradar.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.AttributeConverter;
import org.junit.jupiter.api.Test;

import pl.szczurradar.api.persistence.ClosedSetsGuard.LiteralPair;

class ClosedSetsGuardTest {

	private enum Colour {

		DARK_RED("dark-red"), GREEN("green");

		private final String value;

		Colour(String value) {
			this.value = value;
		}

		String value() {
			return value;
		}

	}

	private static final Set<String> CHECK = Set.of("dark-red", "green");

	private static final class GoodConverter extends EnumValueConverter<Colour> {

		GoodConverter() {
			super(Colour.class, Colour::value);
		}

	}

	private static final class NameConverter extends EnumValueConverter<Colour> {

		NameConverter() {
			super(Colour.class, Colour::name);
		}

	}

	@SuppressWarnings("unchecked")
	private static Map<Class<?>, AttributeConverter<Object, String>> converters(AttributeConverter<?, String> converter) {
		return Map.of(Colour.class, (AttributeConverter<Object, String>) converter);
	}

	@Test
	void aConverterWritingTheConstantNameInsteadOfTheValueIsReported() {
		assertThat(ClosedSetsGuard.problems("shades.colour", Colour.class, converters(new NameConverter()), CHECK))
			.singleElement()
			.satisfies(problem -> assertThat(problem).contains("shades.colour", "DARK_RED", "dark-red"));
	}

	@Test
	void anEnumWithoutAConverterIsReported() {
		assertThat(ClosedSetsGuard.problems("shades.colour", Colour.class, Map.of(), CHECK)).singleElement()
			.satisfies(problem -> assertThat(problem).contains("shades.colour", "no @Converter"));
	}

	@Test
	void aConverterWritingTheValuesOfTheCheckIsNotReported() {
		assertThat(ClosedSetsGuard.problems("shades.colour", Colour.class, converters(new GoodConverter()), CHECK))
			.isEmpty();
	}

	@Test
	void theRealConvertersAreFoundByTheEnumTheyConvert() {
		assertThat(ClosedSetsGuard.convertersByEnum()).hasSize(21)
			.containsKeys(pl.szczurradar.api.pipeline.run.RunKind.class, pl.szczurradar.api.user.Role.class);
	}


	// The definitions below are pg_get_constraintdef's real output on the migrated schema.

	private static final String INTERRUPTION_STATUS = "CHECK (((interruption_cause IS NULL) OR (status = 'interrupted'::text)))";

	private static final String REQUESTED_POSTS = "CHECK (((kind = 'posts'::text) = (requested_post_ids IS NOT NULL)))";

	private static final String RANGE = "CHECK ((((kind = 'range'::text) = (range_from IS NOT NULL))"
			+ " AND ((range_from IS NULL) = (range_to IS NULL)) AND ((range_from IS NULL) OR (range_from <= range_to))))";

	private static final String ASSESSING_OWNER = "CHECK (((stage = 'assessing'::text) = (assessing_run_id IS NOT NULL)))";

	private static final String STAGE_ATTEMPTS = "CHECK ((((assessment_attempts >= 0) AND (assessment_attempts <= 3))"
			+ " AND ((stage <> 'stored'::text) OR (assessment_attempts = 0))"
			+ " AND ((stage <> 'failed'::text) OR ((assessment_attempts >= 1) AND (assessment_attempts <= 2)))"
			+ " AND ((stage <> 'dead'::text) OR (assessment_attempts = 3))"
			+ " AND ((stage <> ALL (ARRAY['assessing'::text, 'assessed'::text])) OR (assessment_attempts <= 2))))";

	private static final String OUTCOME = "CHECK ((((status = 'ok'::text) AND (output IS NOT NULL) AND (failure_class IS NULL))"
			+ " OR ((status = 'failed'::text) AND (output IS NULL) AND (failure_class IS NOT NULL))))";

	private static final String NOTIFICATIONS_RUN = "CHECK (((kind <> 'run_attention'::text) OR (run_id IS NOT NULL)))";

	private static final String SOURCES_ERROR = "CHECK (((status = 'failed'::text) = (error IS NOT NULL)))";

	private static final String HASH = "CHECK ((content_hash ~ '^[0-9a-f]{64}$'::text))";

	private static final Map<String, Set<String>> WRITTEN = Map.of(
			"pipeline_runs.status", Set.of("queued", "running", "succeeded", "partial", "failed", "interrupted"),
			"pipeline_runs.kind", Set.of("range", "posts"),
			"posts.stage", Set.of("stored", "assessing", "assessed", "failed", "dead"),
			"assessments.status", Set.of("ok", "failed"),
			"notifications.kind", Set.of("run_attention"),
			"pipeline_run_sources.status", Set.of("succeeded", "empty", "failed"));

	private static List<String> unknown(String table, String definition) {
		return ClosedSetsGuard.unknownLiterals("ck_test", table, definition, WRITTEN);
	}

	@Test
	void anEqualityWithALiteralIsReadAsAPair() {
		assertThat(ClosedSetsGuard.literalPairs(INTERRUPTION_STATUS)).containsExactly(new LiteralPair("status", "interrupted"));
		assertThat(ClosedSetsGuard.literalPairs(REQUESTED_POSTS)).containsExactly(new LiteralPair("kind", "posts"));
		assertThat(ClosedSetsGuard.literalPairs(ASSESSING_OWNER)).containsExactly(new LiteralPair("stage", "assessing"));
		assertThat(ClosedSetsGuard.literalPairs(SOURCES_ERROR)).containsExactly(new LiteralPair("status", "failed"));
		assertThat(unknown("pipeline_runs", INTERRUPTION_STATUS)).isEmpty();
		assertThat(unknown("pipeline_runs", REQUESTED_POSTS)).isEmpty();
		assertThat(unknown("posts", ASSESSING_OWNER)).isEmpty();
		assertThat(unknown("pipeline_run_sources", SOURCES_ERROR)).isEmpty();
	}

	@Test
	void aDifferenceWithALiteralIsReadAsAPair() {
		assertThat(ClosedSetsGuard.literalPairs(NOTIFICATIONS_RUN)).containsExactly(new LiteralPair("kind", "run_attention"));
		assertThat(unknown("notifications", NOTIFICATIONS_RUN)).isEmpty();
	}

	@Test
	void aLiteralNextToAColumnToColumnComparisonIsReadAsAPair() {
		assertThat(ClosedSetsGuard.literalPairs(RANGE)).containsExactly(new LiteralPair("kind", "range"));
		assertThat(unknown("pipeline_runs", RANGE)).isEmpty();
	}

	@Test
	void literalsJoinedByAndOrAndIsNullAreReadAsPairs() {
		assertThat(ClosedSetsGuard.literalPairs(OUTCOME)).containsExactly(new LiteralPair("status", "ok"),
				new LiteralPair("status", "failed"));
		assertThat(unknown("assessments", OUTCOME)).isEmpty();
	}

	@Test
	void everyLiteralOfAnArrayAndOfDifferencesIsReadAsAPair() {
		assertThat(ClosedSetsGuard.literalPairs(STAGE_ATTEMPTS)).containsExactlyInAnyOrder(
				new LiteralPair("stage", "stored"), new LiteralPair("stage", "failed"), new LiteralPair("stage", "dead"),
				new LiteralPair("stage", "assessing"), new LiteralPair("stage", "assessed"));
		assertThat(unknown("posts", STAGE_ATTEMPTS)).isEmpty();
	}

	@Test
	void aTypoInALiteralIsReported() {
		assertThat(unknown("pipeline_runs", INTERRUPTION_STATUS.replace("interrupted", "interupted"))).singleElement()
			.satisfies(problem -> assertThat(problem).contains("pipeline_runs.status", "'interupted'", "interrupted"));
	}

	@Test
	void aLiteralOfAnotherColumnIsReported() {
		assertThat(unknown("pipeline_runs", INTERRUPTION_STATUS.replace("status", "kind"))).singleElement()
			.satisfies(problem -> assertThat(problem).contains("'interrupted'", "pipeline_runs.kind", "[posts, range]"));
	}

	@Test
	void aLiteralOnAColumnWithoutARegisteredEnumIsReported() {
		assertThat(unknown("pipeline_runs", INTERRUPTION_STATUS.replace("status", "mood"))).singleElement()
			.satisfies(problem -> assertThat(problem).contains("mood", "no registered enum"));
	}

	@Test
	void aLiteralInAFormTheParserCannotReadIsReported() {
		assertThat(unknown("pipeline_runs", "CHECK ((status ~~ 'inter%'::text))")).singleElement()
			.satisfies(problem -> assertThat(problem).contains("1 literals", "0 read"));
	}

	@Test
	void aPatternHasNoPairOfLiterals() {
		assertThat(ClosedSetsGuard.literalPairs(HASH)).isEmpty();
		assertThat(ClosedSetsGuard.literalCount(HASH)).isEqualTo(1);
	}

	@Test
	void anExceptionWithoutAReasonOrWithoutACheckOrWithoutALiteralOrReadAsAListIsReported() {
		Map<String, String> definitions = Map.of("ck_hash", HASH, "ck_plain", "CHECK ((attempts >= 0))", "ck_list",
				"CHECK ((x = ANY (ARRAY['a'::text, 'b'::text])))");
		Map<String, String> exceptions = Map.of("ck_hash", "a pattern", "ck_blank", "", "ck_plain", "was a pattern",
				"ck_list", "was a rule", "ck_gone", "was a pattern");
		assertThat(ClosedSetsGuard.exceptionProblems(exceptions, Map.of(), name -> false)).hasSize(exceptions.size() + 1)
			.contains("ck_blank: the exception has no reason");
		assertThat(ClosedSetsGuard.exceptionProblems(exceptions, definitions, definition -> definition.contains("ARRAY")))
			.containsExactlyInAnyOrder("ck_blank: the exception has no reason",
					"ck_blank: the schema no longer has this CHECK", "ck_gone: the schema no longer has this CHECK",
					"ck_plain: the CHECK has no literal any more, drop the exception",
					"ck_list: the CHECK reads as a list of values now, drop the exception");
		assertThat(ClosedSetsGuard.exceptionProblems(Map.of("ck_hash", "a pattern"), definitions, definition -> false))
			.isEmpty();
	}

}
