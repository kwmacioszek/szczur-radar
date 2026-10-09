package pl.szczurradar.api.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The white space set and normalization of contract-v2.md §10. */
class WhitespaceTest {

	/** The §10 set, listed independently of the implementation. */
	static IntStream contractWhitespace() {
		return IntStream.of(0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x001C, 0x001D, 0x001E, 0x001F, 0x0020, 0x0085,
				0x00A0, 0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A,
				0x2028, 0x2029, 0x202F, 0x205F, 0x3000);
	}

	@ParameterizedTest(name = "U+{0}")
	@MethodSource("contractWhitespace")
	void everyContractWhitespaceCharacterBecomesOneSpace(int codePoint) {
		assertThat(Whitespace.isWhitespace(codePoint)).isTrue();
		String text = "a" + Character.toString(codePoint) + "b";
		assertThat(Whitespace.normalize(text)).isEqualTo("a b");
		assertThat(Whitespace.normalize(Character.toString(codePoint))).isEmpty();
	}

	@Test
	void theSetHasExactlyTheContractCharacters() {
		long count = IntStream.rangeClosed(0, Character.MAX_CODE_POINT).filter(Whitespace::isWhitespace).count();
		assertThat(count).isEqualTo(contractWhitespace().count());
	}

	@Test
	void zeroWidthSpaceIsNotWhitespace() {
		assertThat(Whitespace.isWhitespace(0x200B)).isFalse();
		assertThat(Whitespace.normalize("a​b")).isEqualTo("a​b");
	}

	@Test
	void collapsesRunsAndTrimsEnds() {
		assertThat(Whitespace.normalize(" \n to koniec  dla\r\n\tpamięciorzy \n#gielda ")).isEqualTo(
				"to koniec dla pamięciorzy #gielda");
	}

	@Test
	void changesNothingElse() {
		String text = "**Banka** na „cyber” security — ( ͡° ͜ʖ ͡°) 🚀";
		assertThat(Whitespace.normalize(text)).isEqualTo(text);
	}

	@Test
	void stripTrimsOnlyTheEnds() {
		assertThat(Whitespace.strip("　 a  b \n")).isEqualTo("a  b");
		assertThat(Whitespace.strip("   ")).isEmpty();
	}

	@Test
	void regexClassMatchesTheSameSet() {
		Pattern pattern = Pattern.compile("[" + Whitespace.regexClass() + "]");
		IntStream.rangeClosed(0, 0x3100)
			.forEach(codePoint -> assertThat(pattern.matcher(Character.toString(codePoint)).matches())
				.as("U+%04X", codePoint)
				.isEqualTo(Whitespace.isWhitespace(codePoint)));
	}

}
