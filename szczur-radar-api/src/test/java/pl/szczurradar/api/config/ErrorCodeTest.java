package pl.szczurradar.api.config;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
				entry("post_not_found", 404),
				entry("source_not_configured", 503),
				entry("not_found", 404),
				entry("internal_error", 500)));
	}

	@ParameterizedTest
	@CsvSource({ "400, invalid_request", "401, not_authenticated", "403, forbidden", "404, not_found",
			"405, invalid_request", "415, unsupported_media_type", "500, internal_error", "503, internal_error" })
	void forStatusMapsAContainerStatusToACode(int status, String code) {
		assertThat(ErrorCode.forStatus(status).code()).isEqualTo(code);
	}

}
