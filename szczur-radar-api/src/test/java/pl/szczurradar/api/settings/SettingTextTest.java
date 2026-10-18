package pl.szczurradar.api.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The text of an environment setting: unset and blank both read as empty. */
class SettingTextTest {

	@Test
	void unsetAndBlankAreEmpty() {
		assertThat(SettingText.trimmed(null)).isEmpty();
		assertThat(SettingText.trimmed("")).isEmpty();
		assertThat(SettingText.trimmed(" \t\n ")).isEmpty();
	}

	@Test
	void onlyBothEndsAreStripped() {
		assertThat(SettingText.trimmed(" 300s\n")).isEqualTo("300s");
		assertThat(SettingText.trimmed("\t12 hours ")).isEqualTo("12 hours");
	}

}
