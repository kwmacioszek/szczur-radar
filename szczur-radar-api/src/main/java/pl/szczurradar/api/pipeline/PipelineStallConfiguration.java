package pl.szczurradar.api.pipeline;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import pl.szczurradar.api.llm.LlmProviderProperties;

/**
 * The server's one {@link PipelineStallSettings} ({@code PIPELINE_STALL_TIMEOUT}), read and validated
 * when the server starts: an invalid value fails it ({@code InvalidSettingsException}: the
 * {@code APPLICATION FAILED TO START} block). {@link RunWatchdog} stops this instance's runs by it, and
 * {@link PipelineRunService} takes another instance's runs without activity for that long as orphans.
 *
 * <p>Only in the server, like {@link RunWatchdog}: the operator commands ({@code migrate},
 * {@code create-admin}, …) start the same context without the web server, and neither need nor read
 * the setting, so a bad value never stops the pre-deploy {@code migrate}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
public class PipelineStallConfiguration {

	@Bean
	PipelineStallSettings pipelineStallSettings(Environment environment, LlmProviderProperties llm) {
		return PipelineStallSettings.from(environment, llm);
	}

}
