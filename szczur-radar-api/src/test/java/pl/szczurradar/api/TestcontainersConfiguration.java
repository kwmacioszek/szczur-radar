package pl.szczurradar.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared PostgreSQL for integration tests ({@code *IT}). Same major version as Railway and
 * compose.local.yaml. {@code @ServiceConnection} replaces the PG* based datasource settings, so the
 * tests never depend on PG* environment variables. Each distinct Spring test context gets its own
 * container (a fresh, empty database).
 *
 * <p>Docker is required: without it the context fails to start and {@code ./mvnw verify} fails
 * (the tests are deliberately not skipped).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

}
