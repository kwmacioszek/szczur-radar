package pl.szczurradar.api.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** U+0000 handling before the database ({@link Nul}). */
class NulTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Test
	void sourceTextLosesNulAndModelTextGetsTheReplacementCharacter() {
		assertThat(Nul.removed("a\u0000b\u0000")).isEqualTo("ab");
		assertThat(Nul.replaced("a\u0000b")).isEqualTo("a�b");
		assertThat(Nul.removed(null)).isNull();
		assertThat(Nul.replaced(null)).isNull();
	}

	@Test
	void jsonOfATextWithNulReadsBackWithTheReplacementCharacter() {
		String json = MAPPER.writeValueAsString(List.of("a\u0000b", "żółw "));

		String stored = Nul.replacedInJson(json);

		assertThat(stored).doesNotContain("\\u0000");
		assertThat(MAPPER.readValue(stored, String[].class)).containsExactly("a�b", "żółw ");
	}

}
