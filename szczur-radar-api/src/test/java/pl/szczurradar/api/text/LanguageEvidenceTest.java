package pl.szczurradar.api.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The language a text shows evidence of ({@link LanguageEvidence}, S-36). */
class LanguageEvidenceTest {

	@Test
	void aPolishLetterIsPolishInEitherCase() {
		assertThat(LanguageEvidence.of("Kurs spadł")).contains(Language.PL);
		assertThat(LanguageEvidence.of("ŻABKA")).contains(Language.PL);
	}

	@Test
	void aDecomposedPolishLetterIsPolishToo() {
		// "Kupię CDR" with ę written as e + U+0328 (combining ogonek), as NFD text would carry it.
		assertThat(LanguageEvidence.of("Kupię CDR")).contains(Language.PL);
	}

	@Test
	void twoPolishFunctionWordsArePolishAndOneIsNoEvidence() {
		assertThat(LanguageEvidence.of("Autor nie kupuje CDR, jest sceptyczny")).contains(Language.PL);
		assertThat(LanguageEvidence.of("nie nie")).as("every occurrence counts").contains(Language.PL);
		assertThat(LanguageEvidence.of("Autor nie kupuje CDR")).isEmpty();
	}

	@Test
	void twoEnglishFunctionWordsAreEnglishAndOneIsNoEvidence() {
		assertThat(LanguageEvidence.of("The author expects NVDA to rise")).isEmpty();
		assertThat(LanguageEvidence.of("The author expects NVDA to rise in March")).contains(Language.EN);
		assertThat(LanguageEvidence.of("the the")).as("every occurrence counts").contains(Language.EN);
	}

	@Test
	void numbersAndTickersAreNoEvidence() {
		assertThat(LanguageEvidence.of("CDR 120 PLN")).isEmpty();
		assertThat(LanguageEvidence.of("NVDA +5% 2026")).isEmpty();
		assertThat(LanguageEvidence.of("123 456 %")).isEmpty();
		assertThat(LanguageEvidence.of("")).isEmpty();
	}

	@Test
	void polishGoesFirstSoAPolishTextQuotingEnglishStaysPolish() {
		assertThat(LanguageEvidence.of("Autor cytuje: \"the market is wrong and this will end\""))
			.as("no Polish evidence: the quote decides")
			.contains(Language.EN);
		assertThat(LanguageEvidence.of("Autor uważa, że \"the market is wrong and this will end\""))
			.contains(Language.PL);
		assertThat(LanguageEvidence.of("Autor nie wierzy, jest pewny: \"the market is wrong\"")).contains(Language.PL);
	}

	@Test
	void wordsBothLanguagesUseAreNoEvidence() {
		assertThat(LanguageEvidence.of("to on by do we a i no as was but")).isEmpty();
		assertThat(LanguageEvidence.of("Tesla to the moon")).isEmpty();
	}

	@Test
	void caseDoesNotMatter() {
		assertThat(LanguageEvidence.of("THE STOCK IS CHEAP")).contains(Language.EN);
		assertThat(LanguageEvidence.of("NIE JEST TANIO")).contains(Language.PL);
	}

	@Test
	void nullIsNoEvidence() {
		assertThat(LanguageEvidence.of(null)).isEmpty();
	}

	@Test
	void eachLanguageReadsBackFromItsStoredValue() {
		for (Language language : Language.values()) {
			assertThat(Language.fromValue(language.value())).isEqualTo(language);
		}
		assertThat(Language.PL.value()).isEqualTo("pl");
		assertThat(Language.EN.value()).isEqualTo("en");
	}

}
