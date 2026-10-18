package pl.szczurradar.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;

import pl.szczurradar.api.settings.InvalidSettingsException;

/** An invalid setting becomes Boot's failure report, thrown directly (before the context) or from a bean. */
class InvalidSettingsFailureAnalyzerTest {

	private static final String MESSAGE = "Invalid LLM configuration: LLM_TIMEOUT needs a unit, e.g. 300s or 5m"
			+ " (got '120'; a bare number would be milliseconds). See szczur-radar-api/README.md (LLM provider).";

	private final InvalidSettingsFailureAnalyzer analyzer = new InvalidSettingsFailureAnalyzer();

	@Test
	void describesAnInvalidSettingThrownBeforeTheContext() {
		InvalidSettingsException failure = new InvalidSettingsException(MESSAGE);

		FailureAnalysis analysis = analyzer.analyze(failure);

		assertThat(analysis).isNotNull();
		assertThat(analysis.getDescription()).isEqualTo(MESSAGE);
		assertThat(analysis.getAction())
			.isEqualTo("Fix the named setting(s) and start again; see szczur-radar-api/README.md.");
		assertThat(analysis.getCause()).isSameAs(failure);
	}

	@Test
	void findsAnInvalidSettingThrownFromABean() {
		InvalidSettingsException invalid = new InvalidSettingsException(
				"Invalid configuration: PIPELINE_STALL_TIMEOUT needs a unit, e.g. 300s or 5m (got '15'; a bare"
						+ " number would be milliseconds), or unset for the default (15m 00s).");
		BeanCreationException failure = new BeanCreationException("runWatchdog", "Failed to instantiate", invalid);

		FailureAnalysis analysis = analyzer.analyze(failure);

		assertThat(analysis).isNotNull();
		assertThat(analysis.getDescription()).isEqualTo(invalid.getMessage());
		assertThat(analysis.getCause()).isSameAs(invalid);
	}

	@Test
	void ignoresAnyOtherFailure() {
		assertThat(analyzer.analyze(new IllegalStateException("Migration V2 failed"))).isNull();
		assertThat(analyzer.analyze(new BeanCreationException("dataSource", "Connection refused"))).isNull();
	}

}
