package pl.szczurradar.api.llm;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/** Reading a Spring AI {@link ChatResponse} the same way for every provider. */
final class ChatResponses {

	private ChatResponses() {
	}

	/** The text of all generations, without thought parts (Gemini marks them {@code isThought}). */
	static String text(ChatResponse response) {
		return response.getResults()
			.stream()
			.filter(generation -> !Boolean.TRUE.equals(generation.getOutput().getMetadata().get("isThought")))
			.map(generation -> generation.getOutput().getText())
			.filter(Objects::nonNull)
			.collect(Collectors.joining());
	}

	/** The finish reasons of all generations, lower case, e.g. {@code stop} or {@code content_filter}. */
	static String finishReasons(ChatResponse response) {
		return response.getResults()
			.stream()
			.map(Generation::getMetadata)
			.map(metadata -> String.valueOf(metadata.getFinishReason()).toLowerCase(Locale.ROOT))
			.collect(Collectors.joining(","));
	}

	static @Nullable String model(ChatResponse response) {
		String model = response.getMetadata().getModel();
		return model.isBlank() ? null : model;
	}

	static @Nullable Integer inputTokens(ChatResponse response) {
		return positiveOrNull(response.getMetadata().getUsage().getPromptTokens());
	}

	static @Nullable Integer outputTokens(ChatResponse response) {
		return positiveOrNull(response.getMetadata().getUsage().getCompletionTokens());
	}

	static @Nullable Integer cachedTokens(ChatResponse response) {
		Long cached = response.getMetadata().getUsage().getCacheReadInputTokens();
		return cached == null ? null : Math.toIntExact(cached);
	}

	/** Spring AI reports a missing count as 0; a real call always has prompt and completion tokens. */
	private static @Nullable Integer positiveOrNull(@Nullable Integer tokens) {
		return tokens == null || tokens <= 0 ? null : tokens;
	}

}
