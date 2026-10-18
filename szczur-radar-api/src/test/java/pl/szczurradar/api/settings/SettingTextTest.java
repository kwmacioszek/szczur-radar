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
	void aValueUpToTheLimitIsRepeatedWhole() {
		assertThat(SettingText.echoed("")).isEmpty();
		assertThat(SettingText.echoed("openrouter")).isEqualTo("openrouter");
		assertThat(SettingText.echoed("1234567890123456")).isEqualTo("1234567890123456");
	}

	@Test
	void aLongerValueIsRepeatedOnlyInPart() {
		assertThat(SettingText.echoed("12345678901234567")).isEqualTo("1234567890123456…");
		assertThat(SettingText.echoed("sk-or-v1-0123456789abcdef0123456789abcdef")).isEqualTo("sk-or-v1-0123456…");
	}

	@Test
	void aCharacterOutsideTheBasicPlaneIsNeverSplit() {
		String value = "\uD83D\uDE00".repeat(20);

		assertThat(SettingText.echoed(value)).isEqualTo("\uD83D\uDE00".repeat(16) + "…");
	}

	@Test
	void onlyBothEndsAreStripped() {
		assertThat(SettingText.trimmed(" 300s\n")).isEqualTo("300s");
		assertThat(SettingText.trimmed("\t12 hours ")).isEqualTo("12 hours");
	}

}
