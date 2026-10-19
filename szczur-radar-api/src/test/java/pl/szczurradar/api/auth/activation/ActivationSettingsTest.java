package pl.szczurradar.api.auth.activation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import pl.szczurradar.api.auth.activation.ActivationSettings.Result;

/** {@code WEB_PUBLIC_URL} and {@code ACTIVATION_LINK_TTL}: parsing, bounds and the link's shape. */
class ActivationSettingsTest {

	private static final String WEB = "https://szczur-radar-web-production.up.railway.app";

	private final Map<String, String> environment = new HashMap<>(Map.of(ActivationSettings.WEB_PUBLIC_URL, WEB));

	// --- ACTIVATION_LINK_TTL ----------------------------------------------------------------------

	@Test
	void theLinkIsValidFor12HoursByDefault() {
		assertThat(valid().linkTtl()).isEqualTo(Duration.ofHours(12)).isEqualTo(ActivationSettings.DEFAULT_LINK_TTL);

		environment.put(ActivationSettings.LINK_TTL, "  ");
		assertThat(valid().linkTtl()).isEqualTo(Duration.ofHours(12));
	}

	@Test
	void aTtlWithAUnitIsRead() {
		environment.put(ActivationSettings.LINK_TTL, "90m");
		assertThat(valid().linkTtl()).isEqualTo(Duration.ofMinutes(90));

		environment.put(ActivationSettings.LINK_TTL, " 24h ");
		assertThat(valid().linkTtl()).isEqualTo(Duration.ofHours(24));
	}

	@ParameterizedTest
	@ValueSource(strings = { "720", "43200000", "+5" })
	void aBareNumberIsRefusedBecauseSpringWouldReadMilliseconds(String ttl) {
		environment.put(ActivationSettings.LINK_TTL, ttl);

		assertThat(problems()).singleElement()
			.asString()
			.contains("ACTIVATION_LINK_TTL needs a unit", "'" + ttl + "'", "milliseconds");
	}

	@ParameterizedTest
	@ValueSource(strings = { "1m", "60s", "7d", "168h" })
	void theBoundsAreIncluded(String ttl) {
		environment.put(ActivationSettings.LINK_TTL, ttl);

		assertThat(valid().linkTtl()).isBetween(ActivationSettings.MIN_LINK_TTL, ActivationSettings.MAX_LINK_TTL);
	}

	@ParameterizedTest
	@ValueSource(strings = { "59s", "0m", "-5m", "8d", "169h" })
	void aTtlOutsideOneMinuteToSevenDaysIsRefused(String ttl) {
		environment.put(ActivationSettings.LINK_TTL, ttl);

		assertThat(problems()).singleElement().asString().contains("ACTIVATION_LINK_TTL must be from 1m to 7d");
	}

	@ParameterizedTest
	@ValueSource(strings = { "soon", "12 hours", "1.5h", "12x", "PT12H", "12H" })
	void aTtlThatIsNotADurationIsRefused(String ttl) {
		environment.put(ActivationSettings.LINK_TTL, ttl);

		assertThat(problems()).singleElement().asString().contains("ACTIVATION_LINK_TTL must be a duration");
	}

	// --- WEB_PUBLIC_URL ---------------------------------------------------------------------------

	@Test
	void aMissingWebPublicUrlIsRefused() {
		environment.remove(ActivationSettings.WEB_PUBLIC_URL);
		assertThat(problems()).singleElement().asString().contains("WEB_PUBLIC_URL is not set");

		environment.put(ActivationSettings.WEB_PUBLIC_URL, " ");
		assertThat(problems()).singleElement().asString().contains("WEB_PUBLIC_URL is not set");
	}

	/** {@code https://} alone: the web service has no public domain to reference. */
	@ParameterizedTest
	@ValueSource(strings = { "https://", "https:///", "http://:5173", "szczur-radar-web-production.up.railway.app",
			"ftp://szczur-radar-web-production.up.railway.app", "localhost:5173", "https://bad host.example" })
	void aWebPublicUrlWithoutAnHttpSchemeAndAHostIsRefused(String url) {
		environment.put(ActivationSettings.WEB_PUBLIC_URL, url);

		assertThat(problems()).singleElement()
			.asString()
			.contains("WEB_PUBLIC_URL must be an absolute http(s) URL with a host", "'" + url + "'");
	}

	@ParameterizedTest
	@ValueSource(strings = { WEB + "/app", WEB + "/activate/", WEB + "//", WEB + "?ref=cli", WEB + "/?ref=cli",
			WEB + "#activate", "https://operator@szczur-radar-web-production.up.railway.app" })
	void aWebPublicUrlWithAPathQueryFragmentOrUserIsRefused(String url) {
		environment.put(ActivationSettings.WEB_PUBLIC_URL, url);

		assertThat(problems()).singleElement()
			.asString()
			.contains("WEB_PUBLIC_URL must be only the scheme, host and port", "'" + url + "'");
	}

	@Test
	void aTrailingSlashIsDropped() {
		environment.put(ActivationSettings.WEB_PUBLIC_URL, " " + WEB + "/ ");

		assertThat(valid().webPublicUrl()).isEqualTo(WEB);
	}

	@Test
	void everyProblemIsReported() {
		environment.put(ActivationSettings.WEB_PUBLIC_URL, "https://");
		environment.put(ActivationSettings.LINK_TTL, "720");

		assertThat(problems()).hasSize(2);
	}

	// --- link -------------------------------------------------------------------------------------

	@Test
	void theLinkCarriesTheTokenInTheFragmentOfTheActivateRoute() {
		environment.put(ActivationSettings.WEB_PUBLIC_URL, WEB + "/");

		assertThat(valid().link("abc_DEF-123")).isEqualTo(WEB + "/activate#abc_DEF-123");
	}

	@Test
	void aLocalAddressWithAPortWorks() {
		environment.put(ActivationSettings.WEB_PUBLIC_URL, "http://localhost:5173");

		assertThat(valid().link("token")).isEqualTo("http://localhost:5173/activate#token");
	}

	private ActivationSettings valid() {
		Result result = ActivationSettings.from(environment::get);
		assertThat(result).isInstanceOf(Result.Valid.class);
		return ((Result.Valid) result).settings();
	}

	private List<String> problems() {
		Result result = ActivationSettings.from(environment::get);
		assertThat(result).isInstanceOf(Result.Invalid.class);
		return ((Result.Invalid) result).problems();
	}

}
