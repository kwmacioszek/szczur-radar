package pl.szczurradar.api.alerts;

import org.jspecify.annotations.Nullable;

/**
 * Reports an event that needs the operator outside the application (F-04): as a message to an ntfy
 * topic, which the operator's phone subscribes to (S-34). Synchronous, one attempt, short time limits;
 * it never throws, so a caller on an error path can report and carry on whatever happens to the channel.
 *
 * <p>In the server the bean is {@link NtfyAlerts} when {@code NTFY_URL} is set and
 * {@link NoOperatorAlerts} otherwise ({@link AlertsConfiguration}); operator commands have no bean.
 * Error paths do not call it directly: they raise through {@link OperatorAlertQueue}, which sends on its
 * own thread, within the channel's limits.
 */
public interface OperatorAlerts {

	/** What happened to one report. */
	enum Delivery {

		/** The topic took the message (a 2xx), so the operator's subscribers get it. */
		DELIVERED,

		/** The channel did not take it (an error status, no answer, a time-out, an interrupt); logged at WARN. */
		FAILED,

		/** {@code NTFY_URL} is not set: nothing was sent. */
		NOT_CONFIGURED

	}

	/**
	 * Reports {@code event} with {@code detail} (what happened, for a human; never a secret; {@code null}
	 * for none). Never throws.
	 */
	Delivery report(AlertEvent event, @Nullable String detail);

}
