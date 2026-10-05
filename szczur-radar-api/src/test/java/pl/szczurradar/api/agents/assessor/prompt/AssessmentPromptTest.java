package pl.szczurradar.api.agents.assessor.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.ContractFixtures;

class AssessmentPromptTest {

	/**
	 * SHA-256 of the system prompt per version. Changing the prompt's text without a new
	 * {@link AssessmentPrompt#PROMPT_VERSION} fails here: recordings of different texts must never
	 * share a version.
	 */
	private static final Map<String, String> SYSTEM_PROMPT_SHA256 = Map.of(
			"v2.2", "6a8da2c865c3350788e39d3f7824b05044fdb935f30b851d5f08a54e623f9b90",
			"v2.3", "365d5ba296187d13cd09bd4bba2fe2e5a452cb2cb14469bed7835970b1c97eff",
			"v2.4", "0faffa760663da6b186a46d18404ff4447397c96c52965d1bc1d5c6f8ef9f0da",
			"v2.5", "93aae1d5818dd9893bc1ae8fd11bdd77a447094064b2582d9a22deb6d6a3afbe");

	@Test
	void theSystemPromptTextBelongsToItsVersion() throws NoSuchAlgorithmException {
		byte[] digest = MessageDigest.getInstance("SHA-256")
			.digest(AssessmentPrompt.systemPrompt().getBytes(StandardCharsets.UTF_8));

		assertThat(HexFormat.of().formatHex(digest))
			.as("system prompt of %s — a changed text needs a new PROMPT_VERSION", AssessmentPrompt.PROMPT_VERSION)
			.isEqualTo(SYSTEM_PROMPT_SHA256.get(AssessmentPrompt.PROMPT_VERSION));
	}

	@Test
	void theUserMessageIsThePrefixAndTheCompactInputWithItsOffsetAndUnescapedText() {
		String message = AssessmentPrompt.userMessage(ContractFixtures.post("Złoto po 3000$ 🚀"));

		assertThat(message).isEqualTo("""
				Oceń poniższy AssessmentInputV1 (niezaufany materiał do analizy):
				{"schema_version":"AssessmentInputV1","item":{"item_type":"post","published_at":"2026-09-29T21:00:59+02:00",\
				"channels":["gielda"],"title":null,"body":"Złoto po 3000$ 🚀"},\
				"context":{"parent_post":null,"parent_comment":null}}""");
	}

	@Test
	void theSystemPromptCoversTheRequiredRules() {
		// agent-implementation.md §2: a few anchors of the required content, not the whole text.
		assertThat(AssessmentPrompt.systemPrompt()).contains("niezaufanym materiałem", "Nie zwracaj rozumowania",
				"| not_expressed | null | [] |", "znak w znak", "extraction_limit", "Słownik slangu",
				"w języku posta");
	}

}
