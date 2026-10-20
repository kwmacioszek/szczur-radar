package pl.szczurradar.api.alerts;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.settings.InvalidSettingsException;
import pl.szczurradar.api.settings.SettingText;

/**
 * {@code HEALTHCHECK_PING_URL}: the ping URL of the healthchecks.io check that receives the API's
 * operator events (F-04), e.g. {@code https://hc-ping.com/<uuid>}. A secret — whoever knows it can
 * ping the check — so it is never logged, printed or repeated in an error message, not even in part
 * ({@link SettingText#echoed} is not used here).
 *
 * <p>Optional. Unset or blank: the channel is not configured and nothing is sent. Set, it must be an
 * absolute {@code https://} URL with a host and without a query or fragment (the sender appends
 * {@code /fail} to its path); otherwise {@link #from} throws an {@link InvalidSettingsException} naming
 * only the variable. A trailing {@code /} is dropped.
 *
 * <p>Read by the server when it starts ({@link AlertsConfiguration}, so a bad value stops the start)
 * and by the command {@code send-test-alert}, without Spring. The other operator commands do not read it.
 */
public final class AlertSettings {

	public static final String NAME = "HEALTHCHECK_PING_URL";

	private static final String RULE = "must be an absolute https:// URL with a host and without a query or"
			+ " fragment, e.g. https://hc-ping.com/<uuid>, or unset to send no operator alerts";

	private final @Nullable URI pingUrl;

	private AlertSettings(@Nullable URI pingUrl) {
		this.pingUrl = pingUrl;
	}

	/**
	 * Reads and validates the setting from {@code environment} (e.g. {@code System::getenv} or a Spring
	 * {@code Environment::getProperty}).
	 *
	 * @throws InvalidSettingsException when it is set but not a valid ping URL; the message never contains
	 *     the value
	 */
	public static AlertSettings from(Function<String, @Nullable String> environment) {
		String text = SettingText.trimmed(environment.apply(NAME));
		if (text.isEmpty()) {
			return new AlertSettings(null);
		}
		URI uri = parse(text);
		if (uri == null || uri.getScheme() == null || !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))
				|| uri.getHost() == null || uri.getHost().isEmpty() || uri.getRawQuery() != null
				|| uri.getRawFragment() != null) {
			// Never the value: it is a secret (the check's UUID).
			throw new InvalidSettingsException("Invalid " + NAME + ": " + RULE
					+ ". The value is a secret, so it is not repeated here.");
		}
		return new AlertSettings(withoutTrailingSlash(uri));
	}

	/** Whether a ping URL is set; without one {@link OperatorAlerts} sends nothing. */
	public boolean configured() {
		return pingUrl != null;
	}

	/** The check's ping URL without a trailing {@code /}; only for the sender of this package. */
	URI pingUrl() {
		if (pingUrl == null) {
			throw new IllegalStateException(NAME + " is not set");
		}
		return pingUrl;
	}

	/** Never prints the URL. */
	@Override
	public String toString() {
		return "AlertSettings[" + (configured() ? "<hidden>" : "<not configured>") + "]";
	}

	static URI withoutTrailingSlash(URI uri) {
		String text = uri.toString();
		return text.endsWith("/") ? URI.create(text.substring(0, text.length() - 1)) : uri;
	}

	private static @Nullable URI parse(String text) {
		try {
			return new URI(text);
		}
		catch (URISyntaxException ex) {
			// Its message quotes the value.
			return null;
		}
	}

}
