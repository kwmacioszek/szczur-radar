package pl.szczurradar.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.Provider;

/**
 * One check of the watchdog, without its thread: nothing but an in-memory look while this instance has
 * no run, every run of its own handed to the stop, a failure logged, an {@link Error} too; and on its
 * thread, the checks going on after an {@link Error}.
 */
@ExtendWith(OutputCaptureExtension.class)
class RunWatchdogTest {

	private static final Duration TIMEOUT = Duration.ofMinutes(20);

	private final PipelineRunService service = mock(PipelineRunService.class);

	private final RunWatchdog watchdog = new RunWatchdog(service, PipelineStallSettings.from(
			new MockEnvironment().withProperty(PipelineStallSettings.NAME, "20m"),
			new LlmProviderProperties(Provider.GEMINI, "model", "key")));

	@AfterEach
	void stop() {
		watchdog.destroy();
	}

	@Test
	void withoutARunOfItsOwnACheckAsksNothingOfTheDatabase() {
		when(service.ownRuns()).thenReturn(List.of());

		watchdog.check();

		// The run list is in memory; stopStalled is the only database call.
		verify(service).ownRuns();
		verifyNoMoreInteractions(service);
	}

	@Test
	void eachRunOfItsOwnIsHandedToTheStopWithTheTimeout() {
		UUID running = UUID.randomUUID();
		UUID queued = UUID.randomUUID();
		when(service.ownRuns()).thenReturn(List.of(running, queued));

		watchdog.check();

		verify(service).stopStalled(running, TIMEOUT);
		verify(service).stopStalled(queued, TIMEOUT);
	}

	@Test
	void aFailedCheckIsLoggedAndTheOtherRunsAreStillChecked(CapturedOutput output) {
		UUID failing = UUID.randomUUID();
		UUID next = UUID.randomUUID();
		when(service.ownRuns()).thenReturn(List.of(failing, next));
		when(service.stopStalled(failing, TIMEOUT)).thenThrow(new IllegalStateException("the database is gone"));

		watchdog.check();

		verify(service).stopStalled(next, TIMEOUT);
		assertThat(output).contains("ERROR", "could not check run " + failing, "the database is gone");
	}

	@Test
	void anErrorIsLoggedAndTheOtherRunsAreStillChecked(CapturedOutput output) {
		UUID failing = UUID.randomUUID();
		UUID next = UUID.randomUUID();
		when(service.ownRuns()).thenReturn(List.of(failing, next));
		when(service.stopStalled(failing, TIMEOUT)).thenThrow(new NoClassDefFoundError("pl/szczurradar/Missing"));

		watchdog.check();

		verify(service).stopStalled(next, TIMEOUT);
		assertThat(output).contains("ERROR", "could not check run " + failing, "NoClassDefFoundError");
	}

	@Test
	void anErrorInOneCheckDoesNotEndTheLaterChecks() {
		UUID run = UUID.randomUUID();
		Duration timeout = Duration.ofMillis(500);
		when(service.ownRuns()).thenReturn(List.of(run));
		when(service.stopStalled(run, timeout)).thenThrow(new NoClassDefFoundError("pl/szczurradar/Missing"))
			.thenReturn(false);
		// Checked every 100 ms on its own thread.
		RunWatchdog scheduled = new RunWatchdog(service, PipelineStallSettings.from(
				new MockEnvironment().withProperty(PipelineStallSettings.NAME, "500ms"),
				new LlmProviderProperties(Provider.GEMINI, "model", "key")));
		try {
			scheduled.afterPropertiesSet();

			verify(service, timeout(5_000).atLeast(3)).stopStalled(run, timeout);
		}
		finally {
			scheduled.destroy();
		}
	}

	@Test
	void theCheckIntervalIsAFifthOfTheTimeoutAtMostAMinute() {
		assertThat(RunWatchdog.checkInterval(Duration.ofSeconds(2))).isEqualTo(Duration.ofMillis(400));
		assertThat(RunWatchdog.checkInterval(Duration.ofMinutes(4))).isEqualTo(Duration.ofSeconds(48));
		assertThat(RunWatchdog.checkInterval(Duration.ofMinutes(15))).isEqualTo(Duration.ofMinutes(1));
		assertThat(RunWatchdog.checkInterval(Duration.ofMinutes(39))).isEqualTo(Duration.ofMinutes(1));
		// Never a busy loop, whatever the timeout.
		assertThat(RunWatchdog.checkInterval(Duration.ofMillis(1))).isEqualTo(RunWatchdog.MIN_CHECK_INTERVAL);
	}

}
