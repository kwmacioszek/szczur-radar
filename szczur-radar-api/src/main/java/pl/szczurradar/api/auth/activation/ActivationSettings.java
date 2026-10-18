package pl.szczurradar.api.auth.activation;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.settings.DurationSetting;
import pl.szczurradar.api.settings.SettingText;

/**
 * Where an activation link points and how long it is valid, from two environment settings of the
 * API service:
 *
 * <ul>
 *   <li>{@code WEB_PUBLIC_URL} (required): the web app's public address, scheme, host and optional
 *       port only, e.g. {@code https://szczur-radar-web-production.up.railway.app}; a trailing
 *       {@code /} is dropped;
 *   <li>{@code ACTIVATION_LINK_TTL} (optional, default {@code 12h}): with a unit, e.g. {@code 12h}
 *       or {@code 90m}, from {@code 1m} to {@code 7d}. A bare number is refused: Spring would read it
 *       as milliseconds.
 * </ul>
 *
 * <p>Only the operator commands that issue links read them, before their Spring context starts; the
 * server issues no links, so a bad value never stops the API.
 */
public final class ActivationSettings {

	public static final String WEB_PUBLIC_URL = "WEB_PUBLIC_URL";

	public static final String LINK_TTL = "ACTIVATION_LINK_TTL";

	public static final Duration DEFAULT_LINK_TTL = Duration.ofHours(12);

	public static final Duration MIN_LINK_TTL = Duration.ofMinutes(1);

	public static final Duration MAX_LINK_TTL = Duration.ofDays(7);

	/** The SPA route that opens a link; the token follows in the fragment, which never reaches a server. */
	public static final String ACTIVATE_PATH = "/activate";

	private static final Set<String> SCHEMES = Set.of("http", "https");

	private final String webPublicUrl;

	private final Duration linkTtl;

	private ActivationSettings(String webPublicUrl, Duration linkTtl) {
		this.webPublicUrl = webPublicUrl;
		this.linkTtl = linkTtl;
	}

	/** The outcome of {@link #from}: the settings, or every problem found. */
	public sealed interface Result {

		record Valid(ActivationSettings settings) implements Result {
		}

		/** Each problem names its setting and the value it got. */
		record Invalid(List<String> problems) implements Result {

			public Invalid {
				problems = List.copyOf(problems);
			}

		}

	}

	/** Reads and validates both settings from {@code environment} (e.g. {@code System::getenv}). */
	public static Result from(Function<String, @Nullable String> environment) {
		List<String> problems = new ArrayList<>();
		String webPublicUrl = webPublicUrl(SettingText.trimmed(environment.apply(WEB_PUBLIC_URL)), problems);
		String ttlText = SettingText.trimmed(environment.apply(LINK_TTL));
		Duration linkTtl = ttlText.isEmpty() ? DEFAULT_LINK_TTL
				: DurationSetting.parse(LINK_TTL, ttlText, MIN_LINK_TTL, MAX_LINK_TTL, problems);
		if (!problems.isEmpty()) {
			return new Result.Invalid(problems);
		}
		return new Result.Valid(new ActivationSettings(webPublicUrl, linkTtl));
	}

	/** The web app's address without a trailing {@code /}. */
	public String webPublicUrl() {
		return webPublicUrl;
	}

	public Duration linkTtl() {
		return linkTtl;
	}

	/** The link that opens {@code token}: {@code <WEB_PUBLIC_URL>/activate#<token>}. A secret: never log it. */
	public String link(String token) {
		return webPublicUrl + ACTIVATE_PATH + "#" + token;
	}

	@Override
	public String toString() {
		return "ActivationSettings[webPublicUrl=" + webPublicUrl + ", linkTtl=" + linkTtl + "]";
	}

	private static @Nullable String webPublicUrl(String text, List<String> problems) {
		if (text.isEmpty()) {
			problems.add(WEB_PUBLIC_URL + " is not set (the web app's public address, e.g."
					+ " https://szczur-radar-web-production.up.railway.app)");
			return null;
		}
		URI uri = parse(text);
		if (uri == null || uri.getScheme() == null || !SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
				|| uri.getHost() == null || uri.getHost().isEmpty()) {
			problems.add(WEB_PUBLIC_URL + " must be an absolute http(s) URL with a host, e.g."
					+ " https://szczur-radar-web-production.up.railway.app (got '" + text + "')");
			return null;
		}
		boolean originOnly = uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
				&& (uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"));
		if (!originOnly) {
			problems.add(WEB_PUBLIC_URL + " must be only the scheme, host and port, without a path, query or fragment"
					+ " (got '" + text + "')");
			return null;
		}
		return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
	}

	private static @Nullable URI parse(String text) {
		try {
			return new URI(text);
		}
		catch (URISyntaxException ex) {
			return null;
		}
	}

}
