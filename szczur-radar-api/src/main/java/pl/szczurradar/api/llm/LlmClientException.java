package pl.szczurradar.api.llm;

/**
 * An error a provider client could not classify — a bug in our code or an unknown SDK error (e.g. after an
 * SDK change), neither the provider's refusal nor its outage — as it leaves {@link LlmClient#complete}
 * ({@code transport/TransportException.unclassified}). Outside {@link LlmCallException} on purpose: it is
 * never retried and never recorded as a provider error. Its own type lets a caller tell it from an error
 * of its own code without reading the message: the pipeline stops a run's assess step after a series of
 * them ({@code pipeline/assess/ConcurrentAssessmentDispatcher}). Still an {@link IllegalStateException}.
 * The message is cleaned of the key, and the cause is a cleaned copy of the original's chain
 * ({@code transport/ProviderMessages}).
 */
public class LlmClientException extends IllegalStateException {

	public LlmClientException(String message, Throwable cleanedCause) {
		super(message, cleanedCause);
	}

}
