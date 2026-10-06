package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import pl.szczurradar.api.measurement.AnchorMatcher.Pair;

class AnchorMatcherTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "CD Projekt|akcje CD Projekt", "#XTB|xtb", "XTB|(XTB)", "XTB|$XTB",
			"pamięciorze|Pamięciorze", "XTB|XTB S.A.", "##wittchen|Wittchen" })
	void wholeWordContainmentMatchesInBothOrders(String a, String b) {
		assertThat(AnchorMatcher.matches(a, b)).isTrue();
		assertThat(AnchorMatcher.matches(b, a)).isTrue();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "AI|Taiwan", "XTB|XTBR", "CDR|CD Projekt", "ropa|ropy", "XTB|XTB_PL" })
	void partsOfWordsDoNotMatchInEitherOrder(String a, String b) {
		assertThat(AnchorMatcher.matches(a, b)).isFalse();
		assertThat(AnchorMatcher.matches(b, a)).isFalse();
	}

	@Test
	void anEmptyAnchorMatchesNothing() {
		assertThat(AnchorMatcher.matches("", "")).isFalse();
		assertThat(AnchorMatcher.matches(" \n", "XTB")).isFalse();
		assertThat(AnchorMatcher.matches("#", "XTB")).isFalse();
		assertThat(AnchorMatcher.matches("XTB", "#")).isFalse();
	}

	@Test
	void theMatchingIsOneToOneAndAsLargeAsPossible() {
		// Greedy would give "XTB" the first model anchor and leave "XTB akcje" without a pair.
		assertThat(AnchorMatcher.match(List.of("XTB", "XTB akcje"), List.of("XTB akcje", "XTB")))
			.containsExactly(new Pair(0, 1), new Pair(1, 0));
	}

	@Test
	void tiesGoByGoldOrderThenModelOrder() {
		assertThat(AnchorMatcher.match(List.of("akcje CD Projekt", "CD Projekt"), List.of("CD Projekt")))
			.containsExactly(new Pair(0, 0));
		assertThat(AnchorMatcher.match(List.of("CD Projekt"), List.of("akcje CD Projekt", "CD Projekt")))
			.containsExactly(new Pair(0, 0));
	}

}
