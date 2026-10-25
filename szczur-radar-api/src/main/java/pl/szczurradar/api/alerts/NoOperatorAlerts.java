package pl.szczurradar.api.alerts;

import org.jspecify.annotations.Nullable;

/** {@link OperatorAlerts} without {@code HEALTHCHECK_PING_URL}: PR envs, local runs and tests. Sends nothing. */
public final class NoOperatorAlerts implements OperatorAlerts {

	@Override
	public Delivery report(AlertEvent event, @Nullable String detail) {
		return Delivery.NOT_CONFIGURED;
	}

	@Override
	public String toString() {
		return "NoOperatorAlerts";
	}

}
