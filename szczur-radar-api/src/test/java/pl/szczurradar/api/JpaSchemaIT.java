package pl.szczurradar.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * The JPA mapping matches the schema built by the Flyway migrations: Hibernate validates every
 * entity against the migrated database when this context starts (the server itself never touches
 * the database at boot and never changes the schema). A mismatch fails the context, so this test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
		MigrateCommand.ENABLE_FLYWAY, "spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true" })
@Import(TestcontainersConfiguration.class)
class JpaSchemaIT {

	@Autowired
	UserRepository users;

	@Test
	void entitiesMatchTheMigratedSchemaAndRoundTrip() {
		User saved = users.save(new User("schema@example.com", null, Role.ADMIN));

		User loaded = users.findById(saved.getId()).orElseThrow();

		assertThat(loaded.getEmail()).isEqualTo("schema@example.com");
		assertThat(loaded.getPasswordHash()).isNull();
		assertThat(loaded.getRole()).isEqualTo(Role.ADMIN);
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(users.existsByRole(Role.ADMIN)).isTrue();
		assertThat(users.existsByEmail("schema@example.com")).isTrue();
	}

}
