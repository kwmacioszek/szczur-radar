package pl.szczurradar.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** The error codes are part of the API contract with the SPA: pinned here, with their statuses. */
class ErrorCodeTest {

	@Test
	void codesAndStatusesMatchTheContract() {
		Map<String, Integer> contract = Arrays.stream(ErrorCode.values())
			.collect(Collectors.toMap(ErrorCode::code, code -> code.status().value()));

		assertThat(contract).containsExactlyInAnyOrderEntriesOf(Map.of(
				"invalid_credentials", 401,
				"not_authenticated", 401,
				"forbidden", 403,
				"too_many_attempts", 429,
				"cross_origin", 403,
				"invalid_request", 400,
				"unsupported_media_type", 415));
	}

}
