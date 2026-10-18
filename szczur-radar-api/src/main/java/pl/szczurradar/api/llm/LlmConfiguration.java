package pl.szczurradar.api.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

/**
 * The application's {@link LlmClient}, from the same {@code LLM_*} settings as Embabel's model
 * registry. The client is lazy: the server, operator commands and tests start without building a
 * client or touching the network. The settings themselves are a bean (already validated at start-up
 * by {@code config/LlmConfigurationCheck}), so the assessor can record the configured provider and
 * model.
 */
@Configuration(proxyBeanMethods = false)
public class LlmConfiguration {

	@Bean
	LlmProviderProperties llmProviderProperties(Environment environment) {
		return LlmProviderProperties.from(environment);
	}

	@Bean
	@Lazy
	LlmClient llmClient(LlmProviderProperties properties) {
		return LlmClientFactory.create(properties);
	}

}
