package pl.szczurradar.api.agents.assessor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.validation.OutputParser;
import pl.szczurradar.api.agents.assessor.validation.RuleBasedValidator;

/**
 * The examples of {@code context/foundation/llm-assessment/examples.md} are test data: outputs of
 * examples 1–4 and 6–7 pass the parser and validator with no violation; example 5 is the prefilter.
 */
class ContractExamplesTest {

	private static final int PREFILTER_EXAMPLE = 5;

	private final OutputParser parser = new OutputParser();

	private final RuleBasedValidator validator = RuleBasedValidator.contractV2();

	static List<ContractExamples> modelExamples() {
		return ContractExamples.load().stream().filter(example -> example.number() != PREFILTER_EXAMPLE).toList();
	}

	@Test
	void examplesOneToSevenAreRead() {
		assertThat(ContractExamples.load()).extracting(ContractExamples::number).containsExactly(1, 2, 3, 4, 5, 6, 7);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("modelExamples")
	void outputPassesTheValidator(ContractExamples example) {
		AssessmentInputV1 input = ContractJson.MAPPER.readValue(example.inputJson(), AssessmentInputV1.class);
		assertThat(Prefilter.isAssessable(input)).isTrue();
		OutputParser.Result parsed = parser.parse(example.outputJson());
		assertThat(parsed).isInstanceOf(OutputParser.Parsed.class);
		assertThat(validator.validate(input, ((OutputParser.Parsed) parsed).output()).violations()).isEmpty();
	}

	@Test
	void exampleFiveIsAnsweredByThePrefilter() {
		ContractExamples example = ContractExamples.load()
			.stream()
			.filter(candidate -> candidate.number() == PREFILTER_EXAMPLE)
			.findFirst()
			.orElseThrow();
		AssessmentInputV1 input = ContractJson.MAPPER.readValue(example.inputJson(), AssessmentInputV1.class);
		assertThat(Prefilter.isAssessable(input)).isFalse();
		assertThat(parser.parse(example.outputJson())).isEqualTo(new OutputParser.Parsed(Prefilter.noContentOutput()));
	}

	@Test
	void exampleTwoKeepsTheHardSpaceThatTheQuoteDoesNotHave() {
		ContractExamples example = ContractExamples.load().get(1);
		assertThat(example.inputJson()).contains("myślałem że");
		assertThat(example.outputJson()).contains("myślałem że");
	}

}
