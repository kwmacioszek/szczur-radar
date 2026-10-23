package pl.szczurradar.api.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.settings.InvalidSettingsException;

/**
 * {@code HEALTHCHECK_PING_URL}: optional, an absolute {@code https://} URL when set, and a secret — a
 * refused value is never repeated, not even in part.
 */
class AlertSettingsTest {

	private static final String UUID = "5bf66975-d4c7-4bf5-bcc8-b8d8a82ea278";

	@Test
	void unsetOrBlankIsNotConfigured() {
		assertThat(AlertSettings.from(name -> null).configured()).isFalse();
		assertThat(settings("").configured()).isFalse();
		assertThat(settings(" \t ").configured()).isFalse();
		assertThat(settings("").toString()).isEqualTo("AlertSettings[<not configured>]");
	}

	@Test
	void anHttpsUrlIsConfiguredAndHidden() {
		AlertSettings settings = settings("  https://hc-ping.com/" + UUID + "/ ");

		assertThat(settings.configured()).isTrue();
		assertThat(settings.pingUrl()).isEqualTo(URI.create("https://hc-ping.com/" + UUID));
		assertThat(settings).hasToString("AlertSettings[<hidden>]");
		assertThat(settings("HTTPS://healthchecks.example.org/ping/" + UUID).configured()).isTrue();
	}

	@Test
	void readsOnlyItsOwnVariable() {
		Map<String, String> environment = Map.of(AlertSettings.NAME, "https://hc-ping.com/" + UUID);

		assertThat(AlertSettings.from(environment::get).configured()).isTrue();
		assertThat(AlertSettings.from(Map.of("OTHER", "https://hc-ping.com/" + UUID)::get).configured()).isFalse();
	}

	@Test
	void anythingButAnAbsoluteHttpsUrlIsRefusedWithoutRepeatingTheValue() {
		for (String value : new String[] { "http://hc-ping.com/" + UUID, "hc-ping.com/" + UUID, UUID,
				"https:///" + UUID, "https://hc-ping.com/" + UUID + "?x=1", "https://hc-ping.com/" + UUID + "#x",
				"https://hc-ping.com/ " + UUID, "ftp://hc-ping.com/" + UUID, "garbage " + UUID + " \\ {}" }) {
			assertThatExceptionOfType(InvalidSettingsException.class).as(value)
				.isThrownBy(() -> settings(value))
				.satisfies(ex -> assertThat(ex.getMessage()).contains(AlertSettings.NAME, "https://")
					.doesNotContain(UUID, UUID.substring(0, 8), "hc-ping.com/" + UUID.substring(0, 1))
					.doesNotContain("garbage"));
		}
	}

	private static AlertSettings settings(String value) {
		return AlertSettings.from(Map.of(AlertSettings.NAME, value)::get);
	}

}
