package pl.szczurradar.api.agents.assessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

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
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.LlmRequest;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.Provider;

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

	private final Provider provider;

	private final String model;

	private final Clock clock;

	private final OutputParser parser = new OutputParser();

	private final RuleBasedValidator validator = RuleBasedValidator.contractV2();

	/** The client is built on the first assessment, not at start-up ({@code llm/LlmConfiguration}). */
	@Autowired
	public Assessor(@Lazy LlmClient llmClient, LlmProviderProperties properties) {
		this(llmClient, properties.provider(), properties.model(), Clock.systemUTC());
	}

	/**
	 * @param provider the configured provider, recorded for a call that produced no answer
	 * @param model the configured model id, recorded for a call that produced no answer
	 */
	public Assessor(LlmClient llmClient, Provider provider, String model, Clock clock) {
		this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
		this.provider = Objects.requireNonNull(provider, "provider");
		this.model = Objects.requireNonNull(model, "model");
		this.clock = Objects.requireNonNull(clock, "clock");
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
				yield validation.valid() ? AssessmentResult.ok(parsed.output(), provenance, rawText)
						: AssessmentResult.failed(validation.failureClass().orElseThrow(), validation.violations(),
								provenance, rawText);
			}
		};
	}

	/**
	 * Assesses {@code input}: no model call for an entry without content, exactly one
	 * {@link LlmClient#complete} otherwise. A failed call is a {@code failed} result with
	 * {@code provider_error} or {@code timeout}, never an exception — except an
	 * {@link LlmConfigurationException}: a refused key, credit or model says nothing about this entry,
	 * so it is thrown and no attempt is recorded.
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
		catch (LlmConfigurationException ex) {
			throw ex;
		}
		catch (LlmCallException ex) {
			return failed(request, ex, Duration.between(start, clock.instant()));
		}
		Instant end = clock.instant();
		return evaluate(input, request, response, Duration.between(start, end), end);
	}

	/**
	 * The {@code failed} result of a call to {@code request} that produced no answer:
	 * {@code provider_error} or {@code timeout}, with the configured provider and model (§15) and
	 * the call fields the client reported. The only place such a result is built — {@link #assess}
	 * and callers sending prepared requests themselves both use it.
	 *
	 * @param duration wall time of the call
	 */
	public AssessmentResult failed(PreparedRequest request, LlmCallException ex, Duration duration) {
		FailureClass failureClass = switch (ex.failure()) {
			case PROVIDER_ERROR -> FailureClass.PROVIDER_ERROR;
			case TIMEOUT -> FailureClass.TIMEOUT;
		};
		Provenance provenance = new Provenance(Provenance.Source.MODEL, provider, model, null, ex.serviceTier(),
				ex.flexFallback(), null, null, request.promptVersion(), AssessmentOutputV2.CONTRACT_VERSION,
				clock.instant(), null, null, null, duration.toMillis(), ex.attempts());
		String message = ex.getMessage() != null ? ex.getMessage() : failureClass.value();
		return AssessmentResult.failed(failureClass, List.of(new Violation(failureClass, "call", "$", message)),
				provenance, null);
	}

}
