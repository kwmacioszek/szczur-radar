package pl.szczurradar.api.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import pl.szczurradar.api.settings.InvalidSettingsException;

/**
 * {@code NTFY_URL}: optional, an address matching {@link AlertSettings#PING_PATTERN} when set (the one rule
 * of the secret https addresses, shared with the crons' check-in addresses), and a secret — a refused
 * value is never repeated, not even in part. The cases of the rule are the table
 * {@code tools/healthchecks/fixtures/ping-urls.txt}, which the shell copies are tested on too.
 * {@code NTFY_TOKEN}: optional, printable ASCII without white space, only with the address, and a secret too.
 */
class AlertSettingsTest {

	/** Relative to the module directory, where Maven runs the tests. */
	private static final Path TABLE = Path.of("../tools/healthchecks/fixtures/ping-urls.txt");

	private static final String TOPIC = "szczur-radar-5bf66975d4c74bf5bcc8b8d8a82ea278";

	private static final String TOKEN = "tk_r4nd0mT0kenNeverRepeated";

	@Test
	void unsetOrBlankIsNotConfigured() {
		assertThat(AlertSettings.from(name -> null).configured()).isFalse();
		assertThat(settings("").configured()).isFalse();
		assertThat(settings(" \t ").configured()).isFalse();
		assertThat(settings("").toString()).isEqualTo("AlertSettings[<not configured>]");
		assertThat(settings("").token()).isNull();
	}

	@Test
	void anHttpsUrlIsConfiguredAndHidden() {
		AlertSettings settings = settings("  https://ntfy.sh/" + TOPIC + "/ ");

		assertThat(settings.configured()).isTrue();
		assertThat(settings.topicUrl()).isEqualTo(URI.create("https://ntfy.sh/" + TOPIC));
		assertThat(settings.token()).isNull();
		assertThat(settings).hasToString("AlertSettings[<hidden>]");
		assertThat(settings("HTTPS://ntfy.example.org/" + TOPIC).configured()).isTrue();
	}

	@Test
	void readsOnlyItsOwnVariables() {
		Map<String, String> environment = Map.of(AlertSettings.NAME, "https://ntfy.sh/" + TOPIC);

		assertThat(AlertSettings.from(environment::get).configured()).isTrue();
		assertThat(AlertSettings.from(Map.of("OTHER", "https://ntfy.sh/" + TOPIC)::get).configured()).isFalse();
		assertThat(AlertSettings.from(Map.of(AlertSettings.NAME, "https://ntfy.sh/" + TOPIC, "OTHER_TOKEN", TOKEN)::get)
			.token()).isNull();
	}

	@Test
	void aTokenIsReadTrimmedAndHidden() {
		AlertSettings settings = settings("https://ntfy.sh/" + TOPIC, " " + TOKEN + "\t");

		assertThat(settings.configured()).isTrue();
		assertThat(settings.token()).isEqualTo(TOKEN);
		assertThat(settings).hasToString("AlertSettings[<hidden>, token <hidden>]");
		assertThat(settings("https://ntfy.sh/" + TOPIC, "a").token()).isEqualTo("a");
		assertThat(settings("https://ntfy.sh/" + TOPIC, "!~".repeat(128)).token()).hasSize(256);
	}

	@Test
	void aBlankTokenIsNoToken() {
		assertThat(settings("https://ntfy.sh/" + TOPIC, "").token()).isNull();
		assertThat(settings("https://ntfy.sh/" + TOPIC, " \t ").token()).isNull();
		assertThat(settings("", "  ").configured()).isFalse();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "tk_with space", "tk_tab\tinside", "tk_new\nline", "tk_éaccent", "tk_€",
			"tk_ nbsp" })
	void aTokenWithWhiteSpaceOrANonAsciiCharacterIsRefusedWithoutRepeatingIt(String token) {
		assertTokenRefused("https://ntfy.sh/" + TOPIC, token);
	}

	@Test
	void aTokenLongerThanTheCapIsRefusedWithoutRepeatingIt() {
		assertThat(AlertSettings.TOKEN_MAX_LENGTH).isEqualTo(256);

		assertTokenRefused("https://ntfy.sh/" + TOPIC, "t".repeat(AlertSettings.TOKEN_MAX_LENGTH + 1));
	}

	@Test
	void aTokenWithoutTheAddressIsRefusedWithoutRepeatingIt() {
		for (String address : new String[] { "", "  " }) {
			assertThatExceptionOfType(InvalidSettingsException.class).isThrownBy(() -> settings(address, TOKEN))
				.satisfies(ex -> assertThat(ex.getMessage()).contains(AlertSettings.TOKEN_NAME, AlertSettings.NAME)
					.doesNotContain(TOKEN, TOKEN.substring(0, 8)));
		}
		assertThatExceptionOfType(InvalidSettingsException.class).isThrownBy(() -> AlertSettings
			.from(Map.of(AlertSettings.TOKEN_NAME, TOKEN)::get));
	}

	@Test
	void aBadAddressIsRefusedBeforeTheTokenIsLookedAt() {
		assertThatExceptionOfType(InvalidSettingsException.class)
			.isThrownBy(() -> settings("http://ntfy.sh/" + TOPIC, "bad token"))
			.satisfies(ex -> assertThat(ex.getMessage()).contains("Invalid " + AlertSettings.NAME)
				.doesNotContain(TOPIC, "bad token"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("accepted")
	void anAddressOfTheTableIsAccepted(PingCase row) {
		AlertSettings settings = settings(row.address());

		assertThat(settings.configured()).isTrue();
		assertThat(settings.topicUrl().getHost()).isNotNull();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("refused")
	void anAddressOfTheTableIsRefusedWithoutRepeatingAnyPartOfIt(PingCase row) {
		assertRefusedWithoutRepeatingAnyPartOf(row.address());
	}

	/**
	 * The cap is not a table row: the addresses are generated, of the length given and of the pattern's shape
	 * (a host name, then a path of hex digits).
	 */
	@Test
	void anAddressOfAtMostTheCapIsAcceptedAndALongerOneRefused() {
		assertThat(AlertSettings.PING_MAX_LENGTH).isEqualTo(512);
		String atTheCap = validAddressOfLength(AlertSettings.PING_MAX_LENGTH);

		assertThat(settings(atTheCap).configured()).isTrue();
		// The trailing / is dropped, so it does not count, as in the crons.
		assertThat(settings(atTheCap + "/").topicUrl()).hasToString(atTheCap);
		assertRefusedWithoutRepeatingAnyPartOf(validAddressOfLength(AlertSettings.PING_MAX_LENGTH + 1));
	}

	@Test
	void aVeryLongAddressIsRefusedWithTheSettingsMessageNotAStackOverflow() {
		assertRefusedWithoutRepeatingAnyPartOf(validAddressOfLength(10_000));
	}

	/**
	 * The matcher recurses once per host label and per {@code %XX} escape (a plain path does not recurse):
	 * at the cap, the deepest shapes are still decided, not ended with a {@link StackOverflowError} (S-25
	 * review, F4: a cap of 2048 overflowed on a CI runner).
	 */
	@Test
	void theDeepestShapesAtTheCapAreDecided() {
		String labels = "https://" + "a.".repeat((AlertSettings.PING_MAX_LENGTH - "https://hc".length()) / 2) + "hc";
		String escapes = "https://ntfy.example/"
				+ "%41".repeat((AlertSettings.PING_MAX_LENGTH - "https://ntfy.example/".length()) / 3);

		assertThat(labels).hasSizeLessThanOrEqualTo(AlertSettings.PING_MAX_LENGTH);
		assertThat(escapes).hasSizeLessThanOrEqualTo(AlertSettings.PING_MAX_LENGTH);
		assertThat(settings(labels).configured()).isTrue();
		assertThat(settings(escapes).configured()).isTrue();
	}

	private static String validAddressOfLength(int length) {
		StringBuilder address = new StringBuilder("https://ntfy.example/");
		while (address.length() < length) {
			address.append("0123456789abcdef");
		}
		address.setLength(length);
		return address.toString();
	}

	private static void assertRefusedWithoutRepeatingAnyPartOf(String address) {
		String rest = address.replaceFirst("^[^:/]*://", "");
		int slash = rest.indexOf('/');
		String host = slash < 0 ? rest : rest.substring(0, slash);
		String path = slash < 0 ? "" : rest.substring(slash);
		String last = address.substring(address.lastIndexOf('/') + 1);
		List<String> parts = new ArrayList<>();
		for (String part : new String[] { address, host, path.length() > 1 ? path : "",
				last.substring(0, Math.min(8, last.length())) }) {
			if (!part.isEmpty()) {
				parts.add(part);
			}
		}

		assertThatExceptionOfType(InvalidSettingsException.class).isThrownBy(() -> settings(address))
			.satisfies(ex -> assertThat(ex.getMessage()).contains(AlertSettings.NAME, "https://")
				.doesNotContain(parts));
	}

	private static void assertTokenRefused(String address, String token) {
		assertThatExceptionOfType(InvalidSettingsException.class).isThrownBy(() -> settings(address, token))
			.satisfies(ex -> assertThat(ex.getMessage()).contains("Invalid " + AlertSettings.TOKEN_NAME)
				.doesNotContain(token, token.strip(), token.substring(0, Math.min(6, token.length())), TOPIC));
	}

	@Test
	void theTableHasCasesOfBothKinds() {
		List<PingCase> table = PingCase.load(TABLE);

		assertThat(table).anyMatch(PingCase::accept).anyMatch(row -> !row.accept());
		assertThat(table).extracting(PingCase::address)
			.contains("https://hc-ping.com/00000000-0000-4000-8000-000000000001",
					// Read as UTF-8: the non-ASCII rows come through as written.
					"https://hé.example/00000000-0000-4000-8000-000000000030");
	}

	@Test
	void aMissingTableIsAnErrorNotZeroCases() {
		Path missing = TABLE.resolveSibling("no-such-table.txt");

		assertThatIllegalStateException().isThrownBy(() -> PingCase.load(missing))
			.withMessageContaining("no-such-table.txt");
	}

	static List<PingCase> accepted() {
		return PingCase.load(TABLE).stream().filter(PingCase::accept).toList();
	}

	static List<PingCase> refused() {
		return PingCase.load(TABLE).stream().filter(row -> !row.accept()).toList();
	}

	private static AlertSettings settings(String value) {
		return AlertSettings.from(Map.of(AlertSettings.NAME, value)::get);
	}

	private static AlertSettings settings(String value, String token) {
		Map<String, String> environment = new HashMap<>();
		environment.put(AlertSettings.NAME, value);
		environment.put(AlertSettings.TOKEN_NAME, token);
		return AlertSettings.from(environment::get);
	}

	/** One line {@code accept <address>} or {@code refuse <address>} of the table. */
	record PingCase(boolean accept, String address) {

		@Override
		public String toString() {
			return (accept ? "accept " : "refuse ") + address;
		}

		static List<PingCase> load(Path file) {
			List<String> lines;
			try {
				lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				throw new IllegalStateException("cannot read " + file.toAbsolutePath(), ex);
			}
			List<PingCase> cases = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				int space = line.indexOf(' ');
				String verdict = space < 0 ? line : line.substring(0, space);
				String address = space < 0 ? "" : line.substring(space + 1);
				if ((!verdict.equals("accept") && !verdict.equals("refuse")) || address.isEmpty()
						|| !address.equals(address.strip())) {
					throw new IllegalStateException(file + " line " + (i + 1) + ": not 'accept <address>' or"
							+ " 'refuse <address>' without white space at the address's ends");
				}
				cases.add(new PingCase(verdict.equals("accept"), address));
			}
			return cases;
		}

	}

}
