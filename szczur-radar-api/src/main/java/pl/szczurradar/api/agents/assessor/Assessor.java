package pl.szczurradar.api.agents.assessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.prompt.AssessmentPrompt;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.OutputParser;
import pl.szczurradar.api.agents.assessor.validation.RuleBasedValidator;
import pl.szczurradar.api.agents.assessor.validation.ValidationResult;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmRequest;
import pl.szczurradar.api.llm.LlmResponse;

/**
 * Assesses one entry against contract V2: the no-content prefilter (§11), exactly one model call for
 * an assessable entry, then the strict parser and the domain validator (§12). Nothing is retried or
 * repaired here — the only retries are the client's transport retries.
 *
 * <p>{@link #prepare} and {@link #evaluate} are the two halves of {@link #assess}, kept apart so that
 * a batch mode (S-02) can send the prepared requests itself and judge the answers the same way.
 */
@Service
public class Assessor {

	private final LlmClient llmClient;

	private final Clock clock;

	private final OutputParser parser = new OutputParser();

	private final RuleBasedValidator validator = RuleBasedValidator.contractV2();

	/** The client is built on the first assessment, not at start-up ({@code llm/LlmConfiguration}). */
	@Autowired
	public Assessor(@Lazy LlmClient llmClient) {
		this(llmClient, Clock.systemUTC());
	}

	public Assessor(LlmClient llmClient, Clock clock) {
		this.llmClient = llmClient;
		this.clock = clock;
	}

	/** The model call for {@code input}, or the prefilter's result when the entry has no content. */
	public Preparation prepare(AssessmentInputV1 input) {
		if (!Prefilter.isAssessable(input)) {
			return new Preparation.Decided(AssessmentResult.ok(Prefilter.noContentOutput(),
					Provenance.prefilter(AssessmentOutputV2.CONTRACT_VERSION, clock.instant()), null));
		}
		LlmRequest request = new LlmRequest(AssessmentPrompt.systemPrompt(), AssessmentPrompt.userMessage(input),
				OutputParser.schemaText());
		return new Preparation.Prepared(new PreparedRequest(request, AssessmentPrompt.PROMPT_VERSION));
	}

	/**
	 * Judges the model's answer to {@code request}: parsing, shape and domain rules, in the order of
	 * §12.
	 *
	 * @param duration wall time of the call
	 * @param assessedAt when the answer arrived
	 */
	public AssessmentResult evaluate(AssessmentInputV1 input, PreparedRequest request, LlmResponse response,
			Duration duration, Instant assessedAt) {
		Provenance provenance = new Provenance(Provenance.Source.MODEL, response.provider(), response.model(),
				response.channel(), response.serviceTier(), response.flexFallback(), response.reasoningEffort(),
				response.outputMode(), request.promptVersion(), AssessmentOutputV2.CONTRACT_VERSION, assessedAt,
				response.inputTokens(), response.outputTokens(), response.cachedTokens(), duration.toMillis(),
				response.attempts());
		String rawText = response.rawText();
		return switch (parser.parse(rawText)) {
			case OutputParser.Rejected rejected ->
				AssessmentResult.failed(rejected.failureClass(), rejected.violations(), provenance, rawText);
			case OutputParser.Parsed parsed -> {
				ValidationResult validation = validator.validate(input, parsed.output());
				yield validation.failureClass()
					.map(failureClass -> AssessmentResult.failed(failureClass, validation.violations(), provenance,
							rawText))
					.orElseGet(() -> AssessmentResult.ok(parsed.output(), provenance, rawText));
			}
		};
	}

	/**
	 * Assesses {@code input}: no model call for an entry without content, exactly one
	 * {@link LlmClient#complete} otherwise. A failed call is a {@code failed} result with
	 * {@code provider_error} or {@code timeout}, never an exception.
	 */
	public AssessmentResult assess(AssessmentInputV1 input) {
		return switch (prepare(input)) {
			case Preparation.Decided decided -> decided.result();
			case Preparation.Prepared prepared -> call(input, prepared.request());
		};
	}

	private AssessmentResult call(AssessmentInputV1 input, PreparedRequest request) {
		Instant start = clock.instant();
		LlmResponse response;
		try {
			response = llmClient.complete(request.request());
		}
		catch (LlmCallException ex) {
			return callFailed(request, ex, Duration.between(start, clock.instant()));
		}
		Instant end = clock.instant();
		return evaluate(input, request, response, Duration.between(start, end), end);
	}

	private AssessmentResult callFailed(PreparedRequest request, LlmCallException ex, Duration duration) {
		FailureClass failureClass = switch (ex.failure()) {
			case PROVIDER_ERROR -> FailureClass.PROVIDER_ERROR;
			case TIMEOUT -> FailureClass.TIMEOUT;
		};
		Provenance provenance = new Provenance(Provenance.Source.MODEL, null, null, null, ex.serviceTier(),
				ex.flexFallback(), null, null, request.promptVersion(), AssessmentOutputV2.CONTRACT_VERSION,
				clock.instant(), null, null, null, duration.toMillis(), ex.attempts());
		String message = ex.getMessage() != null ? ex.getMessage() : failureClass.value();
		return AssessmentResult.failed(failureClass, List.of(new Violation(failureClass, "call", "$", message)),
				provenance, null);
	}

}
