package pl.szczurradar.api.llm.transport;

import org.jspecify.annotations.Nullable;

/**
 * One element of a provider client's cause chain without the key ({@link ProviderMessages#cleanCause}):
 * the message is the original's class name and its cleaned message, the stack trace is the original's,
 * the cause is the cleaned copy of the original's cause. It carries no suppressed exceptions. A log that
 * prints the whole chain still shows where the error came from, but never the key the provider quoted.
 */
public final class CleanedProviderException extends RuntimeException {

	CleanedProviderException(String message, @Nullable Throwable cause, StackTraceElement[] stackTrace) {
		super(message, cause, false, true);
		setStackTrace(stackTrace);
	}

}
