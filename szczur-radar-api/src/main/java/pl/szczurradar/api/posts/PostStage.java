package pl.szczurradar.api.posts;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A post's progress: stored, being assessed by a run, assessed (latest attempt ok), failed (1–2
 * failed attempts) or dead (3 failed attempts, never retried); {@link #value()} is the stored and
 * API value.
 */
public enum PostStage {

	STORED, ASSESSING, ASSESSED, FAILED, DEAD;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
