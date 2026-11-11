package pl.szczurradar.api.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.RejectedPost;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceFetch;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceStored;
import pl.szczurradar.api.pipeline.flow.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.pipeline.run.RunStatus;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourcePost;
import pl.szczurradar.api.user.SystemAccount;

/**
 * {@link PostStore} on Postgres (S-38): the exception of a chunk's commit is a
 * {@code DataIntegrityViolationException} whose cause names the violated constraint, also from a JDBC batch,
 * and a post the database rejects is told apart from one stored meanwhile by another run (the real
 * {@code uq_posts_source_item}). Not {@code @Transactional}: the step commits its own transactions. The run
 * that stores the posts first is a row inserted with SQL; each test deletes its posts, their raw payloads and
 * the run afterwards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
class PostStoreIT {

	/** A spy: it answers as usual except where a test makes the step's first key lookup miss a stored post. */
	@MockitoSpyBean
	PostRepository posts;

	@Autowired
	PostStore store;

	@Autowired
	JdbcTemplate jdbc;

	private @Nullable UUID runId;

	@BeforeEach
	void run() {
		runId = jdbc.queryForObject("INSERT INTO pipeline_runs (kind, status, requested_by) VALUES (?, ?, ?) RETURNING id",
				UUID.class, RunKind.RETRY.value(), RunStatus.QUEUED.value(), SystemAccount.SCHEDULER.id());
	}

	@AfterEach
	void deleteTheTestsRows() {
		if (runId == null) {
			return;
		}
		List<UUID> raws = jdbc.queryForList("SELECT raw_payload_id FROM posts WHERE first_run_id = ?", UUID.class, runId);
		jdbc.update("DELETE FROM posts WHERE first_run_id = ?", runId);
		raws.forEach(raw -> jdbc.update("DELETE FROM raw_payloads WHERE id = ?", raw));
		jdbc.update("DELETE FROM pipeline_runs WHERE id = ?", runId);
	}

	@Test
	void aPostWithAnEmptyChannelIsRejectedByItsConstraintAndTheRestOfItsChunkIsStoredOnce() {
		String first = id();
		String empty = id();
		String third = id();
		long rawsBefore = rawPayloads();

		StoredBatch batch = store.store(fetched(post(first, "gielda"), post(empty, ""), post(third, "gielda")));

		assertThat(batch.rejected()).containsExactly(new RejectedPost(Source.WYKOP, ItemType.POST, empty, "ck_posts_channel"));
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 3, 2, 0, 1));
		assertThat(batch.rejectedSummary())
			.contains("store rejected 1 post(s), counted as skipped: wykop post " + empty + " (ck_posts_channel)");
		assertThat(stored(first)).isEqualTo(1);
		assertThat(stored(empty)).isZero();
		assertThat(stored(third)).isEqualTo(1);
		assertThat(batch.newPostIds()).containsExactlyInAnyOrderElementsOf(
				jdbc.queryForList("SELECT id FROM posts WHERE first_run_id = ?", UUID.class, runId));
		// The rejected post's raw payload rolled back with it.
		assertThat(rawPayloads()).isEqualTo(rawsBefore + 2);
	}

	@Test
	void aPostStoredMeanwhileHitsTheUniqueKeyAndIsADuplicateNotARejection() {
		String meanwhile = id();
		String fresh = id();
		StoredBatch earlier = store.store(fetched(post(meanwhile, "gielda")));
		assertThat(earlier.sources()).containsExactly(new SourceStored(Source.WYKOP, 1, 1, 0, 0));
		long rawsBefore = rawPayloads();
		// The step's lookup does not see it (as when another run stores it after the lookup); its key read
		// again after the failed save does.
		Answer<?> real = mockingDetails(posts).getMockCreationSettings().getDefaultAnswer();
		AtomicBoolean first = new AtomicBoolean(true);
		doAnswer(invocation -> first.getAndSet(false) ? List.of() : real.answer(invocation)).when(posts)
			.findKeys(any(), anyCollection());

		StoredBatch batch = store.store(fetched(post(meanwhile, "gielda"), post(fresh, "gielda")));

		assertThat(batch.rejected()).isEmpty();
		assertThat(batch.rejectedSummary()).isEmpty();
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 2, 1, 1, 0));
		assertThat(batch.newPostIds()).singleElement()
			.isEqualTo(jdbc.queryForObject("SELECT id FROM posts WHERE source_item_id = ?", UUID.class, fresh));
		assertThat(stored(meanwhile)).isEqualTo(1);
		assertThat(stored(fresh)).isEqualTo(1);
		assertThat(rawPayloads()).isEqualTo(rawsBefore + 1);
	}

	private long stored(String sourceItemId) {
		return jdbc.queryForObject("SELECT count(*) FROM posts WHERE source = ? AND source_item_id = ?", Long.class,
				Source.WYKOP.value(), sourceItemId);
	}

	private long rawPayloads() {
		return jdbc.queryForObject("SELECT count(*) FROM raw_payloads", Long.class);
	}

	/** Wykop's fetch of {@code posts}, every element a post. */
	private FetchedBatch fetched(SourcePost... posts) {
		return new FetchedBatch(runId, List.of(posts), posts.length, 0,
				List.of(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, posts.length, 0, null)));
	}

	private static SourcePost post(String id, String channel) {
		return new SourcePost(Source.WYKOP, ItemType.POST, id, channel, Instant.parse("2026-10-01T08:00:00Z"), null,
				"treść " + id, "autor", null, null, List.of("gielda"),
				("{\"id\":\"" + id + "\"}").getBytes(StandardCharsets.UTF_8));
	}

	private static String id() {
		return UUID.randomUUID().toString();
	}

}
