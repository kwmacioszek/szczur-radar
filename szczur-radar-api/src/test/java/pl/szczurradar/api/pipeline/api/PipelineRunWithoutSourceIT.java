package pl.szczurradar.api.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * Without Wykop's keys (the usual CI and PR env state until they are set) no source is configured: a
 * range request is refused with 503 {@code source_not_configured} and creates no run.
 */
@SpringBootTest(properties = { MigrateCommand.ENABLE_FLYWAY, "WYKOP_KEY=", "WYKOP_SECRET=" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PipelineRunWithoutSourceIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void aRangeWithoutAConfiguredSourceIsUnavailable() {
		UUID admin = users.save(new User("no-source-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN)).getId();
		int before = jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class);

		for (String body : new String[] { "{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\"}",
				"{\"from\":\"2026-10-01\",\"to\":\"2026-10-01\",\"sources\":[\"wykop\"]}" }) {
			assertThat(mvc.post()
				.uri("/api/admin/pipeline-runs")
				.with(user(admin.toString()).roles(Role.ADMIN.name()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)).as(body).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"source_not_configured\"}");
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline_runs", Integer.class)).isEqualTo(before);
		assertThat(mvc.get().uri("/api/admin/pipeline-runs/sources").with(user(admin.toString()).roles(Role.ADMIN.name())))
			.bodyJson()
			.isStrictlyEqualTo("[{\"source\":\"wykop\",\"configured\":false}]");
	}

}
