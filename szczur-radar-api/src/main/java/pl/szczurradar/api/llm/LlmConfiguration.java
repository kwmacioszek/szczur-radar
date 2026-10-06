package pl.szczurradar.api.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import pl.szczurradar.api.config.LlmProviderProperties;

/**
 * The application's {@link LlmClient}, from the same {@code LLM_*} settings as Embabel's model
 * registry. Lazy: the server, operator commands and tests start without building a client or
 * touching the network.
 */
@Configuration(proxyBeanMethods = false)
public class LlmConfiguration {

	@Bean
	@Lazy
	LlmClient llmClient(Environment environment) {
		return LlmClientFactory.create(LlmProviderProperties.from(environment));
	}

}
