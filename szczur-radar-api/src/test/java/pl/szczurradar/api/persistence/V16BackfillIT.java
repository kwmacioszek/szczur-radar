package pl.szczurradar.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.sources.Source;

/**
 * {@code V16__posts_language.sql} gives every post stored before it the language of its source: the database is
 * emptied and migrated to V15 (this context leaves Flyway off, as the server does), posts of both sources from
 * before V16 are inserted with SQL, then every migration is applied and the languages are read.
 * {@link pl.szczurradar.api.MigrationsIT} cannot show this: it migrates everything when its context starts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class V16BackfillIT {

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void everyPostStoredBeforeGetsItsSourcesLanguageAndNoOtherValue() {
		Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).target("15").load().migrate();
		UUID admin = jdbc.queryForObject("INSERT INTO users (email, role) VALUES ('language-backfill@example.com',"
				+ " 'admin') RETURNING id", UUID.class);
		UUID run = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by) VALUES ('retry',"
				+ " 'succeeded', ?) RETURNING id", UUID.class, admin);
		UUID wykop = post(Source.WYKOP, "gielda", "71601", run);
		UUID reddit = post(Source.REDDIT, "stocks", "1abc16", run);

		Flyway.configure().dataSource(dataSource).load().migrate();

		assertThat(language(wykop)).isEqualTo(Source.WYKOP.language().value()).isEqualTo("pl");
		assertThat(language(reddit)).isEqualTo(Source.REDDIT.language().value()).isEqualTo("en");
		assertThatThrownBy(() -> jdbc.update("UPDATE posts SET language = NULL WHERE id = ?", wykop))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("language");
		assertThatThrownBy(() -> jdbc.update("UPDATE posts SET language = 'de' WHERE id = ?", wykop))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_posts_language");
	}

	private String language(UUID post) {
		return jdbc.queryForObject("SELECT language FROM posts WHERE id = ?", String.class, post);
	}

	/** A post of {@code source} with its raw payload as V15 knew it: a channel, no language. */
	private UUID post(Source source, String channel, String sourceItemId, UUID run) {
		String hash = "'" + "0".repeat(64) + "'";
		return jdbc.queryForObject("WITH raw AS (INSERT INTO raw_payloads (source, content_encoding, media_type,"
				+ " payload, sha256) VALUES (?, 'gzip', 'application/json', '\\x00', " + hash + ") RETURNING id)"
				+ " INSERT INTO posts (source, item_type, source_item_id, channel, published_at, raw_payload_id,"
				+ " content_hash, first_run_id) SELECT ?, 'post', ?, ?, now(), raw.id, " + hash + ", ? FROM raw"
				+ " RETURNING id", UUID.class, source.value(), source.value(), sourceItemId, channel, run);
	}

}
