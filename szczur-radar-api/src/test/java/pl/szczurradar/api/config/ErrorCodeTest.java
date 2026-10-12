package pl.szczurradar.api.config;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** The error codes are part of the API contract with the SPA: pinned here, with their statuses. */
class ErrorCodeTest {

	@Test
	void codesAndStatusesMatchTheContract() {
		Map<String, Integer> contract = Arrays.stream(ErrorCode.values())
			.collect(Collectors.toMap(ErrorCode::code, code -> code.status().value()));

		assertThat(contract).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
				entry("invalid_credentials", 401),
				entry("not_authenticated", 401),
				entry("forbidden", 403),
				entry("too_many_attempts", 429),
				entry("cross_origin", 403),
				entry("invalid_request", 400),
				entry("unsupported_media_type", 415),
				entry("activation_link_invalid", 400),
				entry("password_policy", 400),
				entry("pipeline_busy", 409),
				entry("post_dead", 400),
				entry("source_not_configured", 503)));
	}

}
