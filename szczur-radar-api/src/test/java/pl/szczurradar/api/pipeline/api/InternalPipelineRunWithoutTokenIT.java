package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.internal.PipelineTriggerToken;
import pl.szczurradar.api.web.MappedRoutes;
import pl.szczurradar.probe.InternalProbeController;

/**
 * Without {@code PIPELINE_TRIGGER_TOKEN} on the server (PR envs until it is set) the internal routes
 * are closed: every request to every mapped {@code /internal/**} route (computed from the mappings) is 401,
 * whatever token it brings, and creates no run.
 */
@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY, PipelineTriggerToken.NAME + "=" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, InternalProbeController.class })
class InternalPipelineRunWithoutTokenIT {

	private static final String NOT_AUTHENTICATED = "{\"detail\":\"not_authenticated\"}";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@ParameterizedTest
	@ValueSource(strings = { "Bearer ", "Bearer anything",
			"Bearer 9f2c4e6a8b0d1f3e5a7c9e1b3d5f7a9c0e2b4d6f8a1c3e5b7d9f0a2c4e6b8d0f" })
	void everyInternalRouteIs401(String authorization) throws Exception {
		int before = jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class);

		List<MappedRoutes.Route> routes = MappedRoutes.routesUnder(handlerMapping, MappedRoutes.INTERNAL_PATHS);
		assertThat(routes.stream().map(MappedRoutes.Route::key)).contains("GET /internal/probe",
				"POST /internal/pipeline-runs/daily");
		List<String> failures = new ArrayList<>();
		for (MappedRoutes.Route route : routes) {
			MvcTestResult result = mvc.method(HttpMethod.valueOf(route.method()))
				.uri(route.concretePath())
				.header(HttpHeaders.AUTHORIZATION, authorization)
				.exchange();
			int status = result.getResponse().getStatus();
			String body = result.getResponse().getContentAsString();
			if (result.getUnresolvedException() != null) {
				failures.add(route.key() + " -> " + result.getUnresolvedException());
			}
			else if (status != HttpStatus.UNAUTHORIZED.value() || !NOT_AUTHENTICATED.equals(body)) {
				failures.add(route.key() + " -> " + status + " " + body);
			}
		}
		assertThat(failures).as("internal routes that did not answer 401 not_authenticated").isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class)).isEqualTo(before);
	}

}
