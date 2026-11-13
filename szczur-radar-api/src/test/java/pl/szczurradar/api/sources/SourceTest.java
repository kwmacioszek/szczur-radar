package pl.szczurradar.api.sources;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.text.Language;

/**
 * The thresholds each source declares: of failed assessment attempts (S-35) and of theses in another language
 * (S-36), with the language of its posts; evidence in each change folder.
 */
class SourceTest {

	@Test
	void eachSourceHasItsFailureThreshold() {
		assertThat(Source.WYKOP.failureThresholdPercent()).isEqualTo(5);
		assertThat(Source.REDDIT.failureThresholdPercent()).isEqualTo(25);
	}

	@Test
	void eachSourceHasItsLanguageAndItsOtherLanguageThreshold() {
		assertThat(Source.WYKOP.language()).isEqualTo(Language.PL);
		assertThat(Source.WYKOP.otherLanguageThresholdPercent()).isEqualTo(2);
		assertThat(Source.REDDIT.language()).isEqualTo(Language.EN);
		assertThat(Source.REDDIT.otherLanguageThresholdPercent()).isEqualTo(30);
	}

}
