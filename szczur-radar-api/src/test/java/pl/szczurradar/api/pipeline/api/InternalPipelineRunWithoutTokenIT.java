package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.internal.PipelineTriggerToken;

/**
 * Without {@code PIPELINE_TRIGGER_TOKEN} on the server (PR envs until it is set) the internal routes
 * are closed: every request is 401, whatever token it brings, and creates no run.
 */
@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY, PipelineTriggerToken.NAME + "=" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InternalPipelineRunWithoutTokenIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@ParameterizedTest
	@ValueSource(strings = { "Bearer ", "Bearer anything",
			"Bearer 9f2c4e6a8b0d1f3e5a7c9e1b3d5f7a9c0e2b4d6f8a1c3e5b7d9f0a2c4e6b8d0f" })
	void everyInternalRouteIs401(String authorization) {
		int before = jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class);

		assertThat(mvc.post().uri("/internal/pipeline-runs/daily").header(HttpHeaders.AUTHORIZATION, authorization))
			.hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"not_authenticated\"}");
		assertThat(mvc.get()
			.uri("/internal/pipeline-runs/" + UUID.randomUUID())
			.header(HttpHeaders.AUTHORIZATION, authorization)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class)).isEqualTo(before);
	}

}
