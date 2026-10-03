package pl.szczurradar.api.agents.assessor.contract;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The strict JSON mapper for contract types, independent of Boot's mapper (whose settings serve the
 * REST API and may change): duplicate keys, trailing tokens, unknown and missing properties fail,
 * and {@code published_at} keeps its own offset instead of being shifted to UTC.
 */
public final class ContractJson {

	/** Immutable and thread-safe. */
	public static final JsonMapper MAPPER = JsonMapper.builder()
		.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
		.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
		.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
		.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
		.disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
		.build();

	private ContractJson() {
	}

}
