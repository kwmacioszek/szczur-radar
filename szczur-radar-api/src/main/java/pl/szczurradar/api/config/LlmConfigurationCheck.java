package pl.szczurradar.api.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import pl.szczurradar.api.llm.LlmProviderProperties;

/**
 * Fails every start of the application context — server, operator commands and tests alike — on
 * an invalid {@link LlmProviderProperties}, before any bean exists. Without it the first error would
 * come from deep inside Embabel ("Default LLM ... not found") instead of naming the missing setting.
 *
 * <p>Registered in {@code META-INF/spring.factories}: the environment is prepared before the
 * context, so this cannot be a bean.
 */
public class LlmConfigurationCheck implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		LlmProviderProperties.from(event.getEnvironment());
	}

}
