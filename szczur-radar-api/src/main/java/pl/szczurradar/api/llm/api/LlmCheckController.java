package pl.szczurradar.api.llm.api;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.config.LlmProviderProperties;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.llm.LlmResponse;

/**
 * The admin's check of the LLM settings before a run (admin only: {@code /api/admin/**}): one tiny,
 * real call with the configured key and model ({@link LlmClient#check()}, a few dozen tokens) — the
 * same check a pipeline run makes before it assesses.
 */
@RestController
@RequestMapping("/api/admin/llm")
class LlmCheckController {

	private final LlmClient llm;

	private final LlmProviderProperties properties;

	LlmCheckController(@Lazy LlmClient llm, LlmProviderProperties properties) {
		this.llm = llm;
		this.properties = properties;
	}

	/** 200 either way: {@code ok}, or why not (the provider's message, without the key). */
	@GetMapping("/check")
	LlmCheck check() {
		try {
			LlmResponse response = llm.check();
			return new LlmCheck(properties.provider(), properties.model(), true, false, response.model(), null);
		}
		catch (LlmCallException ex) {
			return new LlmCheck(properties.provider(), properties.model(), false,
					ex instanceof LlmConfigurationException, null, ex.getMessage());
		}
	}

	/**
	 * @param refused the provider refused the key, the credit or the model (not a passing outage)
	 * @param answeredBy the model the provider reports for the answer
	 */
	record LlmCheck(Provider provider, String model, boolean ok, boolean refused, @Nullable String answeredBy,
			@Nullable String error) {
	}

}
