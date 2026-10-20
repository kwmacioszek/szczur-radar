package pl.szczurradar.api.alerts;

/** {@link OperatorAlerts} without {@code HEALTHCHECK_PING_URL}: PR envs, local runs and tests. Sends nothing. */
public final class NoOperatorAlerts implements OperatorAlerts {

	@Override
	public Delivery report(AlertEvent event, String detail) {
		return Delivery.NOT_CONFIGURED;
	}

	@Override
	public String toString() {
		return "NoOperatorAlerts";
	}

}
