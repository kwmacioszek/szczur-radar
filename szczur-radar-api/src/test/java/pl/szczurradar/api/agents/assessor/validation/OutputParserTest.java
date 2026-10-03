package pl.szczurradar.api.agents.assessor.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Attribution;
import pl.szczurradar.api.agents.assessor.contract.Claim;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.Target;

/** Steps 1–2 of contract-v2.md §12: strict JSON, then the V2 schema; nothing is repaired. */
class OutputParserTest {

	private static final String VALID = ContractJson.MAPPER.writeValueAsString(ContractFixtures.validOutput());

	private final OutputParser parser = new OutputParser();

	@Test
	void validJsonBecomesTheOutput() {
		assertThat(parser.parse(VALID)).isEqualTo(new OutputParser.Parsed(ContractFixtures.validOutput()));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   \n" })
	void noTextIsInvalidStructuredOutput(String raw) {
		assertRejected(raw, FailureClass.INVALID_STRUCTURED_OUTPUT);
		assertRejected(null, FailureClass.INVALID_STRUCTURED_OUTPUT);
	}

	@Test
	void markdownFencesAreNotStripped() {
		assertRejected("```json\n" + VALID + "\n```", FailureClass.INVALID_STRUCTURED_OUTPUT);
	}

	@Test
	void trailingTokensAreRejected() {
		assertRejected(VALID + " {}", FailureClass.INVALID_STRUCTURED_OUTPUT);
		assertRejected(VALID + " Mam nadzieję, że pomogłem.", FailureClass.INVALID_STRUCTURED_OUTPUT);
	}

	@Test
	void duplicateKeysAreRejected() {
		assertRejected(VALID.replaceFirst("\\{\"relevance\":\"investment_related\"",
				"{\"relevance\":\"unclear\",\"relevance\":\"investment_related\""), FailureClass.INVALID_STRUCTURED_OUTPUT);
	}

	@Test
	void truncatedJsonIsRejected() {
		assertRejected(VALID.substring(0, VALID.length() - 2), FailureClass.INVALID_STRUCTURED_OUTPUT);
	}

	@Test
	void numberInsteadOfEnumIsSchemaViolation() {
		Violation violation = assertRejected(VALID.replace("\"relevance\":\"investment_related\"", "\"relevance\":1"),
				FailureClass.SCHEMA_VIOLATION);
		assertThat(violation.path()).isEqualTo("$.relevance");
	}

	@Test
	void valueOutsideEnumIsSchemaViolation() {
		Violation violation = assertRejected(VALID.replace("\"direction\":\"bullish\"", "\"direction\":\"very_bullish\""),
				FailureClass.SCHEMA_VIOLATION);
		assertThat(violation.path()).isEqualTo("$.targets[0].stance.direction");
		assertThat(violation.rule()).isEqualTo("schema:enum");
	}

	@Test
	void unknownFieldIsSchemaViolation() {
		assertRejected(VALID.replace("{\"relevance\"", "{\"is_signal\":true,\"relevance\""),
				FailureClass.SCHEMA_VIOLATION);
		Violation violation = assertRejected(VALID.replace("\"id\":\"c1\"", "\"id\":\"c1\",\"supported_by_claim_ids\":[]"),
				FailureClass.SCHEMA_VIOLATION);
		assertThat(violation.path()).isEqualTo("$.claims[0]");
	}

	@Test
	void antiExampleWithScoresIsSchemaViolation() {
		assertRejected("{\"sentiment\": 0.72, \"quality\": 8.1, \"is_valuable\": true}", FailureClass.SCHEMA_VIOLATION);
	}

	@Test
	void missingFieldIsSchemaViolation() {
		assertRejected(VALID.replace(",\"flags\":[]", ""), FailureClass.SCHEMA_VIOLATION);
		assertRejected(VALID.replace("\"assertiveness\":\"unhedged\",", ""), FailureClass.SCHEMA_VIOLATION);
	}

	@Test
	void nullOnlyWhereTheContractAllowsIt() {
		assertThat(parser.parse(VALID)).isInstanceOf(OutputParser.Parsed.class);
		assertRejected(VALID.replace("\"kind\":\"company\"", "\"kind\":null"), FailureClass.SCHEMA_VIOLATION);
		assertRejected(VALID.replace("\"modifiers\":[]", "\"modifiers\":null"), FailureClass.SCHEMA_VIOLATION);
	}

	@Test
	void topLevelArrayIsSchemaViolation() {
		assertRejected("[" + VALID + "]", FailureClass.SCHEMA_VIOLATION);
	}

	@Test
	void repeatedModifierOrFlagIsSchemaViolation() {
		assertRejected(VALID.replace("\"modifiers\":[]", "\"modifiers\":[\"humor\",\"humor\"]"),
				FailureClass.SCHEMA_VIOLATION);
		assertRejected(VALID.replace("\"flags\":[]", "\"flags\":[\"missing_context\",\"missing_context\"]"),
				FailureClass.SCHEMA_VIOLATION);
	}

	@Test
	void emptyQuoteOrIdIsSchemaViolation() {
		assertRejected(VALID.replace("\"quote\":\"XTB\"", "\"quote\":\"\""), FailureClass.SCHEMA_VIOLATION);
		assertRejected(VALID.replace("\"id\":\"t2\"", "\"id\":\"\""), FailureClass.SCHEMA_VIOLATION);
	}

	@Test
	void lengthLimitsCountCodePoints() {
		// 🚀 is one code point but two UTF-16 chars: a UTF-16 count would reject the limit itself.
		assertThat(parser.parse(withAnchor("🚀".repeat(100)))).isInstanceOf(OutputParser.Parsed.class);
		Violation violation = assertRejected(withAnchor("🚀".repeat(101)), FailureClass.SCHEMA_VIOLATION);
		assertThat(violation.path()).isEqualTo("$.targets[0].anchor.quote");
		assertThat(violation.rule()).isEqualTo("schema:maxLength");
		assertThat(parser.parse(withEvidence("🚀".repeat(320)))).isInstanceOf(OutputParser.Parsed.class);
		assertRejected(withEvidence("🚀".repeat(321)), FailureClass.SCHEMA_VIOLATION);
		assertThat(parser.parse(withStatement("🚀".repeat(240)))).isInstanceOf(OutputParser.Parsed.class);
		assertRejected(withStatement("🚀".repeat(241)), FailureClass.SCHEMA_VIOLATION);
	}

	@Test
	void countLimitsAreSchemaViolations() {
		Target target = ContractFixtures.target("t", "XTB", Direction.NOT_EXPRESSED, null);
		Claim claim = ContractFixtures.claim("c", Attribution.AUTHOR, "XTB");
		assertRejected(json(ContractFixtures.investment(Collections.nCopies(13, target), List.of())),
				FailureClass.SCHEMA_VIOLATION);
		assertThat(parser.parse(json(ContractFixtures.investment(Collections.nCopies(12, target),
				List.of())))).isInstanceOf(OutputParser.Parsed.class);
		assertRejected(json(ContractFixtures.investment(List.of(), Collections.nCopies(25, claim))),
				FailureClass.SCHEMA_VIOLATION);
		assertRejected(json(ContractFixtures.investment(List.of(ContractFixtures.target("t1", "XTB",
				Direction.BULLISH, Assertiveness.HEDGED, "c1", "c2", "c3", "c4", "c5")), List.of())),
				FailureClass.SCHEMA_VIOLATION);
	}

	private String withAnchor(String quote) {
		return VALID.replace("\"quote\":\"XTB\"", "\"quote\":\"" + quote + "\"");
	}

	private String withEvidence(String quote) {
		return VALID.replace("\"quote\":\"XTB po wynikach poleci w górę\"", "\"quote\":\"" + quote + "\"");
	}

	private String withStatement(String statement) {
		return VALID.replaceFirst("\"statement\":\"Teza testowa.\"", "\"statement\":\"" + statement + "\"");
	}

	private static String json(AssessmentOutputV2 output) {
		return ContractJson.MAPPER.writeValueAsString(output);
	}

	private Violation assertRejected(String raw, FailureClass failureClass) {
		OutputParser.Result result = parser.parse(raw);
		assertThat(result).isInstanceOf(OutputParser.Rejected.class);
		OutputParser.Rejected rejected = (OutputParser.Rejected) result;
		assertThat(rejected.violations()).allSatisfy(v -> assertThat(v.failureClass()).isEqualTo(failureClass));
		assertThat(rejected.failureClass()).isEqualTo(failureClass);
		return rejected.violations().getFirst();
	}

}
