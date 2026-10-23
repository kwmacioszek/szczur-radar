package pl.szczurradar.api.agents.assessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.claim;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.investment;
import static pl.szczurradar.api.agents.assessor.ContractFixtures.target;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import pl.szczurradar.api.agents.assessor.contract.Assertiveness;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.Attribution;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.Relevance;
import pl.szczurradar.api.agents.assessor.prompt.AssessmentPrompt;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.OutputParser;
import pl.szczurradar.api.llm.CallFailure;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmRequest;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.LlmUnavailableException;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ReasoningEffort;
import pl.szczurradar.api.llm.ServiceTier;

/** {@link Assessor} on a scripted {@link LlmClient}: classification, call count, provenance, message. */
class AssessorTest {

	private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private static final AssessmentInputV1 INPUT = ContractFixtures.validInput();

	/** The configured provider and model, recorded only when a call produced no answer. */
	private static final Provider PROVIDER = Provider.GEMINI;

	private static final String MODEL = "gemini-3.1-flash-lite";

	@Test
	void aValidAnswerIsOkWithItsProvenance() {
		String raw = json(ContractFixtures.validOutput());
		ScriptedClient client = ScriptedClient.answering(raw);

		AssessmentResult result = assessor(client).assess(INPUT);

		assertThat(client.requests).hasSize(1);
		assertThat(result.status()).isEqualTo(AssessmentResult.Status.OK);
		assertThat(result.output()).isEqualTo(ContractFixtures.validOutput());
		assertThat(result.failureClass()).isNull();
		assertThat(result.violations()).isEmpty();
		assertThat(result.rawText()).isEqualTo(raw);
		assertThat(result.provenance()).isEqualTo(new Provenance(Provenance.Source.MODEL, Provider.OPENROUTER,
				"openai/gpt-6-luna", Channel.OPENAI_FLEX, ServiceTier.FLEX, false, ReasoningEffort.LOW,
				OutputMode.JSON_SCHEMA, AssessmentPrompt.PROMPT_VERSION, "V2", NOW, 1200, 300, 1024, 0, 2));
	}

	@ParameterizedTest
	@EnumSource(value = CallFailure.class)
	void aFailedCallIsFailedWithItsClassAfterOneCall(CallFailure failure) {
		// A plain LlmCallException (neither unavailable nor a refused configuration) is the entry's failed result.
		ScriptedClient client = ScriptedClient.failing(new LlmCallException(failure, "HTTP 503 after 3 attempts", 3,
				ServiceTier.DEFAULT, true, null));

		AssessmentResult result = assessor(client).assess(INPUT);

		assertThat(client.requests).hasSize(1);
		assertThat(result.status()).isEqualTo(AssessmentResult.Status.FAILED);
		assertThat(result.failureClass()).isEqualTo(FailureClass.valueOf(failure.name()));
		assertThat(result.output()).isNull();
		assertThat(result.rawText()).isNull();
		assertThat(result.violations()).singleElement().satisfies(violation -> {
			assertThat(violation.failureClass()).isEqualTo(result.failureClass());
			assertThat(violation.rule()).isEqualTo("call");
			assertThat(violation.message()).isEqualTo("HTTP 503 after 3 attempts");
		});
		Provenance provenance = result.provenance();
		assertThat(provenance.source()).isEqualTo(Provenance.Source.MODEL);
		assertThat(provenance.attempts()).isEqualTo(3);
		assertThat(provenance.serviceTier()).isEqualTo(ServiceTier.DEFAULT);
		assertThat(provenance.flexFallback()).isTrue();
		assertThat(provenance.promptVersion()).isEqualTo(AssessmentPrompt.PROMPT_VERSION);
		// No answer, so the configured provider and model say whom the attempt was for (§15).
		assertThat(provenance.provider()).isEqualTo(PROVIDER);
		assertThat(provenance.model()).isEqualTo(MODEL);
		assertThat(provenance.channel()).isNull();
		assertThat(provenance.outputMode()).isNull();
		assertThat(provenance.inputTokens()).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = CallFailure.class)
	void aProviderUnavailableAfterTheRetriesIsThrownNotAFailedResult(CallFailure failure) {
		LlmUnavailableException outage = new LlmUnavailableException(failure, "HTTP 503 after 3 attempts", 3,
				ServiceTier.DEFAULT, true, null);
		ScriptedClient client = ScriptedClient.failing(outage);

		// It says nothing about the entry: no failed result, so no attempt for it.
		assertThatExceptionOfType(LlmUnavailableException.class).isThrownBy(() -> assessor(client).assess(INPUT))
			.isSameAs(outage);
		assertThat(client.requests).hasSize(1);
	}

	@Test
	void failedOnAPreparedRequestEqualsAssessOnAClientFailingIt() {
		LlmCallException failure = new LlmCallException(CallFailure.TIMEOUT, "timed out after 3 attempts", 3,
				ServiceTier.DEFAULT, false, null);
		Assessor assessor = assessor(ScriptedClient.failing(failure));
		PreparedRequest prepared = ((Preparation.Prepared) assessor.prepare(INPUT)).request();

		AssessmentResult failed = assessor.failed(prepared, failure, Duration.ZERO);

		assertThat(failed).isEqualTo(assessor.assess(INPUT));
		assertThat(failed.failureClass()).isEqualTo(FailureClass.TIMEOUT);
		assertThat(failed.provenance().provider()).isEqualTo(PROVIDER);
		assertThat(failed.provenance().model()).isEqualTo(MODEL);
	}

	@Test
	void textThatIsNotJsonIsInvalidStructuredOutput() {
		assertFailsAfterOneCall("```json\n" + json(ContractFixtures.validOutput()) + "\n```",
				FailureClass.INVALID_STRUCTURED_OUTPUT);
	}

	@Test
	void jsonOutsideTheSchemaIsASchemaViolation() {
		assertFailsAfterOneCall("{\"relevance\":\"unclear\"}", FailureClass.SCHEMA_VIOLATION);
	}

	@Test
	void aQuoteThatIsNotInThePostIsFabricatedEvidence() {
		assertFailsAfterOneCall(json(investment(List.of(target("t1", "XTB", Direction.BULLISH, Assertiveness.UNHEDGED, "c1")),
				List.of(claim("c1", Attribution.AUTHOR, "XTB poleci w kosmos", "t1")))), FailureClass.FABRICATED_EVIDENCE);
	}

	@Test
	void aStanceOnAMissingClaimIsAnInvalidReference() {
		assertFailsAfterOneCall(json(investment(List.of(target("t1", "XTB", Direction.BULLISH, Assertiveness.UNHEDGED, "c9")),
				List.of(claim("c1", Attribution.AUTHOR, "XTB po wynikach poleci w górę", "t1")))),
				FailureClass.INVALID_REFERENCE);
	}

	@Test
	void targetsInAnIrrelevantEntryAreADomainInvariantViolation() {
		AssessmentOutputV2 valid = ContractFixtures.validOutput();
		assertFailsAfterOneCall(json(new AssessmentOutputV2(Relevance.NOT_INVESTMENT_RELATED, List.of(), valid.targets(),
				valid.claims(), List.of())), FailureClass.DOMAIN_INVARIANT_VIOLATION);
	}

	@Test
	void anEntryWithoutContentIsDecidedByThePrefilterWithoutACall() {
		ScriptedClient client = ScriptedClient.answering("unused");

		AssessmentResult result = assessor(client).assess(ContractFixtures.post("#gielda @uzytkownik1 <url>"));

		assertThat(client.requests).isEmpty();
		assertThat(result.ok()).isTrue();
		assertThat(result.output()).isEqualTo(Prefilter.noContentOutput());
		assertThat(result.rawText()).isNull();
		assertThat(result.provenance()).isEqualTo(Provenance.prefilter("V2", NOW));
	}

	@Test
	void theRequestCarriesThePromptTheInputFieldsOnlyAndTheFullSchema() {
		ScriptedClient client = ScriptedClient.answering(json(ContractFixtures.validOutput()));

		assessor(client).assess(INPUT);

		LlmRequest request = client.requests.getFirst();
		assertThat(request.systemPrompt()).isEqualTo(AssessmentPrompt.systemPrompt());
		assertThat(request.outputSchema()).isEqualTo(OutputParser.schemaText());
		assertThat(request.userMessage()).startsWith(AssessmentPrompt.USER_MESSAGE_PREFIX);
		JsonNode sent = ContractJson.MAPPER
			.readTree(request.userMessage().substring(AssessmentPrompt.USER_MESSAGE_PREFIX.length()));
		assertThat(sent).isEqualTo(ContractJson.MAPPER.valueToTree(INPUT));
		assertThat(ContractJson.MAPPER.treeToValue(sent, AssessmentInputV1.class)).isEqualTo(INPUT);
	}

	@ParameterizedTest
	@ValueSource(strings = { "ok", "not json", "{\"relevance\":\"unclear\"}", "fabricated" })
	void evaluateOnTheRawAnswerEqualsAssessOnAClientReturningIt(String answer) {
		String raw = switch (answer) {
			case "ok" -> json(ContractFixtures.validOutput());
			case "fabricated" -> json(investment(List.of(), List.of(claim("c1", Attribution.AUTHOR, "nie ma tego w poście"))));
			default -> answer;
		};
		Assessor assessor = assessor(ScriptedClient.answering(raw));
		PreparedRequest prepared = ((Preparation.Prepared) assessor.prepare(INPUT)).request();

		AssessmentResult evaluated = assessor.evaluate(INPUT, prepared, response(raw), Duration.ZERO, NOW);

		assertThat(evaluated).isEqualTo(assessor.assess(INPUT));
	}

	@Test
	void prepareDecidesAnEntryWithoutContentAndPreparesAnAssessableOne() {
		Assessor assessor = assessor(ScriptedClient.answering("unused"));

		assertThat(assessor.prepare(ContractFixtures.post("#gielda"))).isInstanceOf(Preparation.Decided.class);
		assertThat(assessor.prepare(INPUT)).isInstanceOfSatisfying(Preparation.Prepared.class,
				prepared -> assertThat(prepared.request().promptVersion()).isEqualTo(AssessmentPrompt.PROMPT_VERSION));
	}

	private static void assertFailsAfterOneCall(String raw, FailureClass expected) {
		ScriptedClient client = ScriptedClient.answering(raw);

		AssessmentResult result = assessor(client).assess(INPUT);

		assertThat(client.requests).hasSize(1);
		assertThat(result.status()).isEqualTo(AssessmentResult.Status.FAILED);
		assertThat(result.failureClass()).isEqualTo(expected);
		assertThat(result.violations()).isNotEmpty();
		assertThat(result.output()).isNull();
		assertThat(result.rawText()).isEqualTo(raw);
		assertThat(result.provenance().provider()).isEqualTo(Provider.OPENROUTER);
	}

	private static Assessor assessor(LlmClient client) {
		return new Assessor(client, PROVIDER, MODEL, CLOCK);
	}

	private static String json(AssessmentOutputV2 output) {
		return ContractJson.MAPPER.writeValueAsString(output);
	}

	private static LlmResponse response(String raw) {
		return new LlmResponse(raw, Provider.OPENROUTER, "openai/gpt-6-luna", Channel.OPENAI_FLEX, ServiceTier.FLEX,
				false, ReasoningEffort.LOW, OutputMode.JSON_SCHEMA, 1200, 300, 1024, 2);
	}

	/** Records every request and answers each with the same script. */
	private static final class ScriptedClient implements LlmClient {

		final List<LlmRequest> requests = new ArrayList<>();

		private final Function<LlmRequest, LlmResponse> script;

		private ScriptedClient(Function<LlmRequest, LlmResponse> script) {
			this.script = script;
		}

		static ScriptedClient answering(String raw) {
			return new ScriptedClient(request -> response(raw));
		}

		static ScriptedClient failing(LlmCallException failure) {
			return new ScriptedClient(request -> {
				throw failure;
			});
		}

		@Override
		public LlmResponse complete(LlmRequest request) {
			requests.add(request);
			return script.apply(request);
		}

	}

}
