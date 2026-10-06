package pl.szczurradar.api.agents.assessor.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;

import pl.szczurradar.api.agents.assessor.ContractFixtures;

/** Enum values and input rules of contract-v2.md §2–§3, pinned. */
class ContractModelTest {

	@Test
	void enumValuesAreTheContractVocabulary() {
		assertThat(values(ItemType.values())).containsExactly("post", "link", "comment");
		assertThat(values(Relevance.values())).containsExactly("investment_related", "not_investment_related",
				"unclear");
		assertThat(values(Modifier.values())).containsExactly("humor", "promotion");
		assertThat(values(TargetKind.values())).containsExactly("company", "asset", "sector", "macro_topic", "other",
				"unknown");
		assertThat(values(Reference.values())).containsExactly("explicit", "contextual", "ambiguous", "unresolved");
		assertThat(values(Direction.values())).containsExactly("bullish", "bearish", "neutral", "mixed",
				"not_expressed", "unclear");
		assertThat(values(Assertiveness.values())).containsExactly("hedged", "unhedged");
		assertThat(values(ClaimKind.values())).containsExactly("factual_claim", "opinion", "investment_thesis",
				"question", "trade_action");
		assertThat(values(Attribution.values())).containsExactly("author", "reported", "unclear");
		assertThat(values(Document.values())).containsExactly("item", "parent_post", "parent_comment");
		assertThat(values(Field.values())).containsExactly("title", "body");
		assertThat(values(Flag.values())).containsExactly("missing_context", "possible_sarcasm",
				"insufficient_content", "extraction_limit");
		assertThat(AssessmentOutputV2.CONTRACT_VERSION).isEqualTo("V2");
	}

	@Test
	void inputJsonRoundTripKeepsTheOffsetAndText() {
		String json = """
				{"schema_version":"AssessmentInputV1","item":{"item_type":"post","published_at":"2026-09-28T16:36:16+02:00",\
				"channels":["gielda"],"title":null,"body":"to koniec dla pamięciorzy \\n#gielda "},\
				"context":{"parent_post":null,"parent_comment":null}}""";
		AssessmentInputV1 input = ContractJson.MAPPER.readValue(json, AssessmentInputV1.class);
		assertThat(input.item().publishedAt().getOffset().getTotalSeconds()).isEqualTo(7200);
		assertThat(input.item().body()).isEqualTo("to koniec dla pamięciorzy \n#gielda ");
		assertThat(ContractJson.MAPPER.writeValueAsString(input)).isEqualTo(json);
	}

	@Test
	void inputWithoutTitleAndBodyIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> ContractFixtures.post(null, null))
			.withMessageContaining("title or a body");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", " \n", " 　" })
	void emptyOrWhitespaceOnlyTextMustBeNull(String text) {
		assertThatIllegalArgumentException().isThrownBy(() -> ContractFixtures.post(text))
			.withMessageContaining("item.body must be null");
		assertThatIllegalArgumentException().isThrownBy(() -> ContractFixtures.post(text, "treść"))
			.withMessageContaining("item.title must be null");
		assertThatIllegalArgumentException().isThrownBy(() -> new Parent(text, null))
			.withMessageContaining("parent.title must be null");
	}

	@Test
	void inputJsonWithoutOffsetOrWithUnknownFieldIsRejected() {
		String withoutOffset = """
				{"schema_version":"AssessmentInputV1","item":{"item_type":"post","published_at":"2026-09-28T16:36:16",\
				"channels":[],"title":null,"body":"treść"},"context":{"parent_post":null,"parent_comment":null}}""";
		assertThatThrownBy(() -> ContractJson.MAPPER.readValue(withoutOffset, AssessmentInputV1.class))
			.isInstanceOf(JacksonException.class);
		String unknownField = withoutOffset.replace("2026-09-28T16:36:16", "2026-09-28T16:36:16Z")
			.replace("\"channels\"", "\"author\":\"x\",\"channels\"");
		assertThatThrownBy(() -> ContractJson.MAPPER.readValue(unknownField, AssessmentInputV1.class))
			.isInstanceOf(JacksonException.class);
	}

	@Test
	void textReturnsTheQuotedFieldOrNull() {
		AssessmentInputV1 input = AssessmentInputV1.of(ContractFixtures.post("tytuł", "treść").item(),
				new Context(new Parent(null, "rodzic"), null));
		assertThat(input.text(Document.ITEM, Field.TITLE)).isEqualTo("tytuł");
		assertThat(input.text(Document.PARENT_POST, Field.BODY)).isEqualTo("rodzic");
		assertThat(input.text(Document.PARENT_POST, Field.TITLE)).isNull();
		assertThat(input.text(Document.PARENT_COMMENT, Field.BODY)).isNull();
	}

	private static List<String> values(Enum<?>[] constants) {
		return Arrays.stream(constants).map(constant -> ContractJson.MAPPER.writeValueAsString(constant)
			.replace("\"", "")).toList();
	}

}
