package pl.szczurradar.api.alerts;

/**
 * Reports an event that needs the operator outside the application (F-04): to a healthchecks.io check,
 * which alerts in Telegram. Synchronous, one attempt, short time limits; it never throws, so a caller
 * on an error path can report and carry on whatever happens to the channel.
 *
 * <p>In the server the bean is {@link HealthchecksAlerts} when {@code HEALTHCHECK_PING_URL} is set and
 * {@link NoOperatorAlerts} otherwise ({@link AlertsConfiguration}); operator commands have no bean.
 */
public interface OperatorAlerts {

	/** What happened to one report. */
	enum Delivery {

		/** The check took the event and is back up, so the next event alerts again. */
		DELIVERED,

		/** The channel did not take it (an error status, no answer, a time-out, an interrupt); logged at WARN. */
		FAILED,

		/** {@code HEALTHCHECK_PING_URL} is not set: nothing was sent. */
		NOT_CONFIGURED

	}

	/**
	 * Reports {@code event} with {@code detail} (what happened, for a human; never a secret). Never throws.
	 */
	Delivery report(AlertEvent event, String detail);

}
