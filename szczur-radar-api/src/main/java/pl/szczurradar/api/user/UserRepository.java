package pl.szczurradar.api.user;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Access to {@code users}. E-mails passed in must already be normalized ({@link Emails}). */
@Repository
public class UserRepository {

	private static final String COLUMNS = "id, email, password_hash, role, created_at";

	private static final RowMapper<User> ROW = (rs, rowNum) -> new User(
			rs.getObject("id", UUID.class),
			rs.getString("email"),
			rs.getString("password_hash"),
			rs.getString("role"),
			rs.getObject("created_at", OffsetDateTime.class).toInstant());

	private final JdbcClient jdbc;

	public UserRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<User> findByEmail(String email) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE email = ?").param(email).query(ROW).optional();
	}

	public Optional<User> findById(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = ?").param(id).query(ROW).optional();
	}

	public boolean existsByRole(String role) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM users WHERE role = ?)").param(role).query(Boolean.class).single();
	}

	public boolean existsByEmail(String email) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM users WHERE email = ?)").param(email).query(Boolean.class).single();
	}

	/** Inserts an account and returns its generated id. */
	public UUID insert(String email, @Nullable String passwordHash, String role) {
		return jdbc.sql("INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?) RETURNING id")
				.params(email, passwordHash, role)
				.query(UUID.class)
				.single();
	}

}
