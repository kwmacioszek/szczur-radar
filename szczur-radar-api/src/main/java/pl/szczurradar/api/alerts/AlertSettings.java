package pl.szczurradar.api.alerts;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.settings.InvalidSettingsException;
import pl.szczurradar.api.settings.SettingText;

/**
 * {@code HEALTHCHECK_PING_URL}: the ping URL of the healthchecks.io check that receives the API's
 * operator events (F-04), e.g. {@code https://hc-ping.com/<uuid>}. A secret — whoever knows it can
 * ping the check — so it is never logged, printed or repeated in an error message, not even in part
 * ({@link SettingText#echoed} is not used here).
 *
 * <p>Optional. Unset or blank: the channel is not configured and nothing is sent. Set, the value has at
 * most {@link #PING_MAX_LENGTH} characters (without a trailing {@code /}) and the whole of it must match
 * {@link #PING_PATTERN} (the sender appends {@code /fail} to its path): {@code https} in any case, a host
 * name (no user info, no IP address: the last label starts with a letter), an optional port of at most 5
 * digits, and a path without {@code ?} or {@code #} in which {@code %} only starts a {@code %XX} escape,
 * all in ASCII.
 * Otherwise {@link #from} throws an {@link InvalidSettingsException} naming only the variable. A
 * trailing {@code /} is dropped.
 *
 * <p>ASCII only needs no code here: without flags a {@link Pattern} character class such as
 * {@code [A-Za-z]} matches only those ASCII letters, so any other character is refused. The shell copies
 * refuse it explicitly ({@code *[![:ascii:]]*}), because bash's {@code [A-Za-z]} also matches non-ASCII
 * letters under a UTF-8 locale of glibc (the {@code postgres:18} image of {@code db-backup}).
 *
 * <p>The same rule, as the same literal, is in {@code tools/db-backup/backup.sh},
 * {@code tools/pipeline-trigger/trigger.sh} and {@code tools/healthchecks/checks.sh} (S-25); step 9 of
 * {@code tools/healthchecks/test.sh} fails when a copy differs, and its cases in
 * {@code tools/healthchecks/fixtures/ping-urls.txt} are this class's test table too.
 *
 * <p>Read by the server when it starts ({@link AlertsConfiguration}, so a bad value stops the start)
 * and by the command {@code send-test-alert}, without Spring. The other operator commands do not read it.
 */
public final class AlertSettings {

	public static final String NAME = "HEALTHCHECK_PING_URL";

	/**
	 * The rule as one line (step 9 of {@code tools/healthchecks/test.sh} reads it from here): no backslash, so
	 * the Java literal is the shell literal byte for byte.
	 */
	static final String PING_PATTERN = "^[Hh][Tt][Tt][Pp][Ss]://([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?[.])*[A-Za-z]([A-Za-z0-9-]*[A-Za-z0-9])?(:[0-9]{1,5})?(/[A-Za-z0-9._~:/@!$&()*+,;=-]*(%[0-9A-Fa-f]{2}[A-Za-z0-9._~:/@!$&()*+,;=-]*)*)?$";

	/**
	 * The longest value taken, checked before the pattern: the matcher recurses on the repeated groups, and a
	 * much longer value would end the start with a {@link StackOverflowError} instead of the settings message.
	 * Kept on one line: step 9 of {@code tools/healthchecks/test.sh} reads the number from it and compares it
	 * with the shell copies' cap.
	 */
	static final int PING_MAX_LENGTH = 512;

	private static final Pattern PING = Pattern.compile(PING_PATTERN);

	private static final String RULE = "must be an https:// URL (the scheme in any case) of at most "
			+ PING_MAX_LENGTH + " ASCII characters with a host name (no user info, no IP address), an optional"
			+ " port of at most 5 digits and a path without ? or # in which % only starts a %XX escape,"
			+ " e.g. https://hc-ping.com/<uuid>, or unset to send no operator alerts";

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
		// The cap first (the matcher recurses), counted without the trailing / that is dropped, as the crons
		// count it; then the pattern decides. The URI is only built from a value it accepted, and must have a
		// host (a second guard: the port of at most 5 digits already keeps URI from reading the authority as
		// registry-based, without a host).
		int length = text.endsWith("/") ? text.length() - 1 : text.length();
		URI uri = length <= PING_MAX_LENGTH && PING.matcher(text).matches() ? parse(text) : null;
		if (uri == null || uri.getHost() == null) {
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
