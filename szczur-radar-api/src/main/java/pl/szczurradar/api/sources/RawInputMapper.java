package pl.szczurradar.api.sources;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;

/**
 * Rebuilds the assessment input of a stored post from its raw payload — one Spring bean per source.
 * The pipeline assesses every post (new or retried) through this, so it never knows a source's shape.
 */
public interface RawInputMapper {

	/** The source whose raw payloads this maps. */
	Source source();

	/**
	 * The input of the post stored as {@code raw}.
	 *
	 * @throws RuntimeException when the payload no longer maps (a bug, never a model failure)
	 */
	AssessmentInputV1 map(byte[] raw);

}
