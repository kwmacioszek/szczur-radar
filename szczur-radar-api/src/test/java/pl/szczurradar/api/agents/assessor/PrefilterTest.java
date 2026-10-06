package pl.szczurradar.api.agents.assessor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.Flag;
import pl.szczurradar.api.agents.assessor.contract.Relevance;

/** The no-content prefilter of contract-v2.md §11, on real anonymized posts and edge cases. */
class PrefilterTest {

	/** A real #gielda post from the pilot ({@code fixtures/assessor/real_posts.json}). */
	record RealPost(String id, String note, boolean assessable, AssessmentInputV1 input) {

		@Override
		public String toString() {
			return id + " (" + note + ")";
		}

	}

	static List<RealPost> realPosts() throws IOException {
		try (InputStream in = PrefilterTest.class.getResourceAsStream("/fixtures/assessor/real_posts.json")) {
			return ContractJson.MAPPER.readValue(in, new TypeReference<>() {
			});
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("realPosts")
	void realPosts(RealPost post) {
		assertThat(Prefilter.isAssessable(post.input())).isEqualTo(post.assessable());
	}

	@ParameterizedTest(name = "[{0}] -> {1}")
	@CsvSource(delimiter = '|', value = {
			"#gielda | false",
			"#gielda <url> | false",
			"@uzytkownik1 | false",
			"@uzytkownik1 #gielda https://wykop.pl/wpis/1#comment-2 | false",
			"#gielda http://example.com/a?b=c xd | false",
			"#gielda xd! | true",
			"#gielda 🚀🚀🚀 | true",
			"#gielda 🚀🚀 | false",
			"#cdprojektred #wittchen | false",
			"#gielda a  b | true",
			"#gielda  ab  | false",
			"#gieldaxtb_2026 ab | false",
			"#gielda ( ͡° ͜ʖ ͡°) | true",
			"email: a@b | true" })
	void bodyCases(String body, boolean assessable) {
		assertThat(Prefilter.isAssessable(ContractFixtures.post(body))).isEqualTo(assessable);
	}

	@Test
	void titleAndBodyAreStrippedSeparatelyAndJoined() {
		assertThat(Prefilter.isAssessable(ContractFixtures.post("ab", "#gielda"))).isFalse();
		assertThat(Prefilter.isAssessable(ContractFixtures.post("ab", "#gielda c"))).isTrue();
		assertThat(Prefilter.isAssessable(ContractFixtures.post("Wyniki XTB", null))).isTrue();
	}

	@Test
	void urlIsRemovedUpToContractWhitespace() {
		// A hard space ends the URL (§10 set), so the text after it stays.
		assertThat(Prefilter.isAssessable(ContractFixtures.post("https://x.pl/a hossa"))).isTrue();
	}

	@Test
	void noContentOutputIsSection11() {
		var output = Prefilter.noContentOutput();
		assertThat(output.relevance()).isEqualTo(Relevance.UNCLEAR);
		assertThat(output.modifiers()).isEmpty();
		assertThat(output.targets()).isEmpty();
		assertThat(output.claims()).isEmpty();
		assertThat(output.flags()).containsExactly(Flag.INSUFFICIENT_CONTENT);
	}

}
