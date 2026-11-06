package pl.szczurradar.api.pipeline.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Every way a run ends tells {@code RunNotifications.runEnded} (R-23). Only {@link RunRecorder} writes a run's
 * final state, so each of its public methods is classified here: it ends a run (then {@link #ENDS} names the
 * tests that check the run's notification — {@code runEnded} or the notification it creates) or it does not
 * ({@link #NOT_ENDING}, with the reason). A new public method of {@code RunRecorder} fails the first test until
 * it is classified; a method that ends a run fails the second until it has tests that exist.
 *
 * <p>The guard proves the named tests exist, not what they check, and it does not see a new caller of an
 * existing ending method or a write of a run's end outside {@code RunRecorder}: those are for the review.
 * A test is named {@code package.Class#method}; to extend the guard add the method's entry here.
 */
class RunEndingTest {

	private static final String SERVICE = "pl.szczurradar.api.pipeline.PipelineRunServiceTest#";

	private static final String RUN_IT = "pl.szczurradar.api.pipeline.PipelineRunIT#";

	/** Methods that end a run → the tests that check that the ending is reported. */
	static final Map<String, List<String>> ENDS = Map.of(
			"finish", List.of(
					SERVICE + "aRunThatEndsWithItsSummaryLogsNoError",
					RUN_IT + "aRangeWithSomeSkippedElementsEndsAsBeforeButNeedsAttention"),
			"fail", List.of(
					SERVICE + "aStepsOwnExceptionFailsTheRunWithItsClassAndMessage",
					RUN_IT + "aFailingSourceFailsTheRunAndTheQueueTakesTheNextRequest"),
			"interrupt", List.of(
					SERVICE + "onCloseReportsARunOfItsOwnThatHadAlreadyEnded",
					RUN_IT + "aStoppingInstanceInterruptsItsRunAndPutsItsPostsBack"),
			"stall", List.of(
					SERVICE + "theWatchdogsStopWritesFirstThenInterruptsTheRunsThreadAndItsPostTasksThenNotifies",
					"pl.szczurradar.api.pipeline.RunWatchdogIT#aRunWaitingForACallThatDoesNotEndIsStoppedAndTheRunBehindItGoesOn"),
			"interruptAll", List.of(
					"pl.szczurradar.api.pipeline.PipelineStartupRecoveryIT#everyInterruptedRunNotifiesTheAdminButNotTheScheduler"),
			"interruptOrphans", List.of(
					"pl.szczurradar.api.pipeline.OrphanedRunIT#deadRunsOfAnotherInstanceAreInterruptedAsLostAndTheRequestGoesOn"));

	/** Methods that do not end a run → why. */
	static final Map<String, String> NOT_ENDING = Map.ofEntries(
			Map.entry("start", "queued to running: the run begins"),
			Map.entry("isRunning", "a read of the run's status"),
			Map.entry("count", "adds to the counters of a running run"),
			Map.entry("source", "writes a source's result while the run runs"),
			Map.entry("touch", "moves the last activity of a running run"),
			Map.entry("error", "sets the error text while the run runs; the status stays"),
			Map.entry("inFlight", "a read of the posts the run holds in assessment"),
			Map.entry("step", "records a step of a running run (both overloads); the run's status stays"));

	@Test
	void everyPublicMethodIsClassified() {
		assertThat(RunEndingGuard.unclassified(RunRecorder.class, ENDS.keySet(), NOT_ENDING))
			.withFailMessage("%s", "RunRecorder's public methods and RunEndingTest disagree: "
					+ RunEndingGuard.unclassified(RunRecorder.class, ENDS.keySet(), NOT_ENDING))
			.isEmpty();
	}

	@Test
	void everyEndingMethodHasExistingTests() {
		assertThat(RunEndingGuard.missingCoverage(ENDS))
			.withFailMessage("%s", "Methods that end a run without tests that exist: "
					+ RunEndingGuard.missingCoverage(ENDS))
			.isEmpty();
	}

}
