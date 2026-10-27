package pl.szczurradar.api.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import pl.szczurradar.api.llm.LlmProviderProperties;

/**
 * Fails every start of the application context — server, operator commands and tests alike — on
 * an invalid {@link LlmProviderProperties}, before any bean exists or anything runs: a bad
 * {@code LLM_*} stops the server and every operator command (also the pre-deploy {@code migrate}) at
 * once, naming the setting, instead of surfacing later at the first model call.
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
