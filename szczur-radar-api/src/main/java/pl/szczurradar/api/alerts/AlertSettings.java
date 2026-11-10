package pl.szczurradar.api.alerts;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.settings.InvalidSettingsException;
import pl.szczurradar.api.settings.SettingText;

/**
 * {@code NTFY_URL}: the address of the ntfy topic that receives the API's operator events (F-04, S-34),
 * e.g. {@code https://ntfy.sh/<topic>}. A secret — whoever knows it can read the topic and publish to
 * it — so it is never logged, printed or repeated in an error message, not even in part
 * ({@link SettingText#echoed} is not used here). With it the optional {@code NTFY_TOKEN}: the access
 * token of an ntfy account, sent as {@code Authorization: Bearer}, so that ntfy.sh counts the limits per
 * account instead of per the shared egress IP of Railway. A secret too.
 *
 * <p>Both optional. {@code NTFY_URL} unset or blank: the channel is not configured and nothing is sent.
 * Set, the value follows the one rule of the secret https addresses, shared with the crons' check-in
 * addresses (S-25): at most {@link #PING_MAX_LENGTH} characters (without a trailing {@code /}) and the
 * whole of it must match {@link #PING_PATTERN}: {@code https} in any case, a host name (no user info, no
 * IP address: the last label starts with a letter), an optional port of at most 5 digits, and a path
 * without {@code ?} or {@code #} in which {@code %} only starts a {@code %XX} escape, all in ASCII.
 * Otherwise {@link #from} throws an {@link InvalidSettingsException} naming only the variable. A
 * trailing {@code /} is dropped.
 *
 * <p>{@code NTFY_TOKEN} unset or blank: no {@code Authorization} header. Set, it is 1 to
 * {@link #TOKEN_MAX_LENGTH} printable ASCII characters without white space (the shape {@code tk_…} is
 * not required); otherwise, and when it is set without {@code NTFY_URL}, {@link #from} throws an
 * {@link InvalidSettingsException} naming only the variable.
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

	public static final String NAME = "NTFY_URL";

	public static final String TOKEN_NAME = "NTFY_TOKEN";

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

	/** The longest token taken. */
	static final int TOKEN_MAX_LENGTH = 256;

	private static final Pattern PING = Pattern.compile(PING_PATTERN);

	/** Printable ASCII without white space ({@code !} to {@code ~}), 1 to {@link #TOKEN_MAX_LENGTH} of them. */
	private static final Pattern TOKEN = Pattern.compile("^[!-~]{1," + TOKEN_MAX_LENGTH + "}$");

	private static final String RULE = "must be an https:// URL (the scheme in any case) of at most "
			+ PING_MAX_LENGTH + " ASCII characters with a host name (no user info, no IP address), an optional"
			+ " port of at most 5 digits and a path without ? or # in which % only starts a %XX escape,"
			+ " e.g. https://ntfy.sh/<topic>, or unset to send no operator alerts";

	private static final String TOKEN_RULE = "must be 1 to " + TOKEN_MAX_LENGTH
			+ " printable ASCII characters without white space (an ntfy access token), or unset";

	private final @Nullable URI topicUrl;

	private final @Nullable String token;

	private AlertSettings(@Nullable URI topicUrl, @Nullable String token) {
		this.topicUrl = topicUrl;
		this.token = token;
	}

	/**
	 * Reads and validates the settings from {@code environment} (e.g. {@code System::getenv} or a Spring
	 * {@code Environment::getProperty}).
	 *
	 * @throws InvalidSettingsException when {@code NTFY_URL} is set but not a valid topic address, when
	 *     {@code NTFY_TOKEN} is set but not a valid token, or when the token is set without the address; the
	 *     message never contains a value
	 */
	public static AlertSettings from(Function<String, @Nullable String> environment) {
		String text = SettingText.trimmed(environment.apply(NAME));
		String token = SettingText.trimmed(environment.apply(TOKEN_NAME));
		if (text.isEmpty()) {
			if (!token.isEmpty()) {
				throw new InvalidSettingsException("Invalid " + TOKEN_NAME + ": it needs " + NAME
						+ ", which is not set. The value is a secret, so it is not repeated here.");
			}
			return new AlertSettings(null, null);
		}
		// The cap first (the matcher recurses), counted without the trailing / that is dropped, as the crons
		// count it; then the pattern decides. The URI is only built from a value it accepted, and must have a
		// host (a second guard: the port of at most 5 digits already keeps URI from reading the authority as
		// registry-based, without a host).
		int length = text.endsWith("/") ? text.length() - 1 : text.length();
		URI uri = length <= PING_MAX_LENGTH && PING.matcher(text).matches() ? parse(text) : null;
		if (uri == null || uri.getHost() == null) {
			// Never the value: it is a secret (the topic's name).
			throw new InvalidSettingsException("Invalid " + NAME + ": " + RULE
					+ ". The value is a secret, so it is not repeated here.");
		}
		if (!token.isEmpty() && !TOKEN.matcher(token).matches()) {
			// Never the value: it is a secret (the account's token).
			throw new InvalidSettingsException("Invalid " + TOKEN_NAME + ": " + TOKEN_RULE
					+ ". The value is a secret, so it is not repeated here.");
		}
		return new AlertSettings(withoutTrailingSlash(uri), token.isEmpty() ? null : token);
	}

	/** Whether a topic address is set; without one {@link OperatorAlerts} sends nothing. */
	public boolean configured() {
		return topicUrl != null;
	}

	/** The topic's address without a trailing {@code /}; only for the sender of this package. */
	URI topicUrl() {
		if (topicUrl == null) {
			throw new IllegalStateException(NAME + " is not set");
		}
		return topicUrl;
	}

	/** The access token for {@code Authorization: Bearer}, or {@code null} without one; only for the sender. */
	@Nullable String token() {
		return token;
	}

	/** Never prints the address or the token. */
	@Override
	public String toString() {
		return "AlertSettings[" + (configured() ? "<hidden>" + (token == null ? "" : ", token <hidden>")
				: "<not configured>") + "]";
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
