package pl.szczurradar.api.pipeline.run;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Why a run needs an admin's attention ({@link PipelineRun#attentionReasons()}). {@link #value()} is the
 * API value; nothing stores it. The order of the constants is the order of a run's reasons in the
 * response. A new reason also gets its text in {@code szczur-radar-web/src/runs.ts}: without it the SPA
 * shows the code as it came.
 */
public enum AttentionReason {

	/** The run {@link RunStatus#FAILED failed}; its only reason, whatever its counters. */
	FAILED("failed"),

	/** The run was {@link RunStatus#INTERRUPTED interrupted}, whatever the cause; its only reason. */
	INTERRUPTED("interrupted"),

	/**
	 * A source of a {@link RunStatus#SUCCEEDED succeeded} or {@link RunStatus#PARTIAL partial} range run
	 * failed ({@link SourceFetchStatus#FAILED}): the other sources' posts were stored and assessed, its own
	 * were not. The run's error and its sources' rows ({@link PipelineRunSource}) name it.
	 */
	SOURCE_FAILED("source_failed"),

	/**
	 * A source of a {@link RunStatus#SUCCEEDED succeeded} or {@link RunStatus#PARTIAL partial} range run
	 * gave no post ({@link SourceFetchStatus#EMPTY}: no element in the range, or only skipped ones).
	 */
	SOURCE_EMPTY("source_empty"),

	/** A {@link RunStatus#PARTIAL partial} run made a post dead (its last attempt failed). */
	DEAD_POSTS("dead_posts"),

	/** A {@link RunStatus#PARTIAL partial} run had a post with a technical error and no stored assessment. */
	PROCESSING_ERRORS("processing_errors"),

	/**
	 * A {@link RunStatus#SUCCEEDED succeeded} or {@link RunStatus#PARTIAL partial} run skipped elements:
	 * fetched, but they could not become a post.
	 */
	SKIPPED("skipped");

	private final String value;

	AttentionReason(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

}
