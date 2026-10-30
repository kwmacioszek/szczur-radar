package pl.szczurradar.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.sources.wykop.WykopPosts;

/**
 * {@code V13__posts_channel.sql} gives every post stored before it the channel Wykop read it from: the
 * database is emptied and migrated to V12 (this context leaves Flyway off, as the server does), Wykop posts
 * from before V13 are inserted with SQL, then every migration is applied and the channels are read.
 * {@link pl.szczurradar.api.MigrationsIT} cannot show this: it migrates everything when its context starts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class V13BackfillIT {

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void everyWykopPostStoredBeforeGetsItsTagAsTheChannel() {
		Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).target("12").load().migrate();
		UUID admin = jdbc.queryForObject("INSERT INTO users (email, role) VALUES ('channel-backfill@example.com',"
				+ " 'admin') RETURNING id", UUID.class);
		UUID run = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by) VALUES ('retry',"
				+ " 'succeeded', ?) RETURNING id", UUID.class, admin);
		UUID entry = wykopPost("post", "70601", run);
		UUID link = wykopPost("link", "70602", run);

		Flyway.configure().dataSource(dataSource).load().migrate();

		for (UUID post : new UUID[] { entry, link }) {
			assertThat(jdbc.queryForObject("SELECT channel FROM posts WHERE id = ?", String.class, post))
				.as("channel of post %s", post)
				.isEqualTo(WykopPosts.TAG);
		}
	}

	/** A Wykop post with its raw payload as V12 knew it: no channel. */
	private UUID wykopPost(String itemType, String sourceItemId, UUID run) {
		String hash = "'" + "0".repeat(64) + "'";
		return jdbc.queryForObject("WITH raw AS (INSERT INTO raw_payloads (source, content_encoding, media_type,"
				+ " payload, sha256) VALUES ('wykop', 'gzip', 'application/json', '\\x00', " + hash + ") RETURNING id)"
				+ " INSERT INTO posts (source, item_type, source_item_id, published_at, raw_payload_id, content_hash,"
				+ " first_run_id) SELECT 'wykop', ?, ?, now(), raw.id, " + hash + ", ? FROM raw RETURNING id",
				UUID.class, itemType, sourceItemId, run);
	}

}
