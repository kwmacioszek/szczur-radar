package pl.szczurradar.api.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.RejectedPost;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceFetch;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceStored;
import pl.szczurradar.api.pipeline.flow.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.pipeline.state.RunInterruptedException;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostKey;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourcePost;

/**
 * {@link PostStore}: new posts once per (source, item type, id), with their raw payload; each post new, a
 * duplicate or rejected by the database (S-38), and each source's posts adding up
 * ({@link SourceStored}: posts = new + duplicates + rejected, checked in every test). The failures are
 * built as the commit's are: a {@link DataIntegrityViolationException} whose cause is Hibernate's
 * exception with the constraint's name and the SQLState.
 */
@ExtendWith(OutputCaptureExtension.class)
class PostStoreTest {

	private static final UUID RUN = UUID.randomUUID();

	/** What the database's message holds: the row's values, the post's text. Never in a log line. */
	private static final String ROW_VALUES = "Failing row contains (treść tajna)";

	private final PostRepository posts = mock(PostRepository.class);

	private final RawPayloadStore raw = mock(RawPayloadStore.class);

	private final List<Post> saved = new ArrayList<>();

	/** Each transaction, with what was stored in it in order ("raw" or the post's source id). */
	private final List<List<String>> transactions = new ArrayList<>();

	private final PostStore store = new PostStore(posts, raw, transaction());

	PostStoreTest() {
		when(raw.store(any(), any())).thenAnswer(invocation -> {
			transactions.getLast().add("raw");
			return new RawPayloadRef(UUID.randomUUID());
		});
		when(posts.save(any(Post.class))).thenAnswer(invocation -> {
			Post post = invocation.getArgument(0);
			setId(post);
			saved.add(post);
			transactions.getLast().add(post.getSourceItemId());
			return post;
		});
	}

	@Test
	void storedKeysAreSkippedAndTheSameIdAsAnotherItemTypeIsANewPost() {
		when(posts.findKeys(eq(Source.WYKOP), anyCollection()))
			.thenReturn(List.of(new PostKey(ItemType.POST, "1")));

		StoredBatch batch = store.store(
				fetched(List.of(post(ItemType.POST, "1"), post(ItemType.LINK, "1"), post(ItemType.POST, "2"))));

		assertThat(saved).extracting(Post::getItemType, Post::getSourceItemId)
			.containsExactly(tuple(ItemType.LINK, "1"), tuple(ItemType.POST, "2"));
		assertThat(batch.newPostIds()).containsExactlyElementsOf(saved.stream().map(Post::getId).toList());
		assertThat(saved).allSatisfy(post -> assertThat(post.getFirstRunId()).isEqualTo(RUN));
		verify(raw, times(2)).store(eq(Source.WYKOP), any());
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 3, 2, 1, 0));
		assertThat(batch.rejected()).isEmpty();
	}

	@Test
	void aPostStoredMeanwhileByAnotherRunIsNotNew(CapturedOutput output) {
		// Not stored when the step looked; stored when its key is read again after its save failed.
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenReturn(List.of())
			.thenReturn(List.of(new PostKey(ItemType.POST, "7")));
		when(posts.save(any(Post.class))).thenThrow(violation("uq_posts_source_item", "23505"));

		StoredBatch batch = store.store(fetched(List.of(post(ItemType.POST, "7"))));

		assertThat(batch.newPostIds()).isEmpty();
		assertThat(batch.rejected()).isEmpty();
		assertThat(batch.rejectedSummary()).isEmpty();
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 1, 0, 1, 0));
		// The step's lookup, then the key read again after the failed save.
		verify(posts, times(2)).findKeys(Source.WYKOP, List.of("7"));
		assertThat(warnings(output)).isEmpty();
	}

	@Test
	void aPostTheDatabaseRejectsIsRejectedWithItsCauseAndAWarningWithoutTheExceptionsMessage(CapturedOutput output) {
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenReturn(List.of());
		when(posts.save(any(Post.class))).thenThrow(violation("ck_posts_channel", "23514"));

		StoredBatch batch = store.store(fetched(List.of(post(ItemType.LINK, "7"))));

		assertThat(batch.newPostIds()).isEmpty();
		assertThat(batch.rejected()).containsExactly(new RejectedPost(Source.WYKOP, ItemType.LINK, "7", "ck_posts_channel"));
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 1, 0, 0, 1));
		assertThat(batch.rejectedBySource()).containsExactly(entry(Source.WYKOP, 1));
		assertThat(batch.rejectedSummary())
			.contains("store rejected 1 post(s), counted as skipped: wykop link 7 (ck_posts_channel)");
		assertThat(warnings(output)).singleElement()
			.satisfies(line -> assertThat(line).endsWith("Run " + RUN + ": the database rejected wykop link 7 at store"
					+ " (ck_posts_channel, DataIntegrityViolationException); counted as skipped"));
		// Neither the exception's message nor its stack trace: they hold the post's text.
		assertThat(output.getAll()).doesNotContain(ROW_VALUES).doesNotContain("treść").doesNotContain("Exception:");
		assertThat(output.getAll()).contains("Run " + RUN + " store wykop: 1 post(s), new 0, duplicates 0, rejected 1");
	}

	@Test
	void aChunkThatHitsAPostStoredMeanwhileOrARejectedOneIsStoredPostByPost(CapturedOutput output) {
		// The step's lookup finds nothing; read again, "7" was stored meanwhile and "9" is still not stored.
		AtomicInteger lookups = new AtomicInteger();
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenAnswer(invocation -> {
			Collection<String> ids = invocation.getArgument(1);
			return lookups.getAndIncrement() > 0 && ids.contains("7") ? List.of(new PostKey(ItemType.POST, "7"))
					: List.of();
		});
		when(posts.save(any(Post.class))).thenAnswer(invocation -> {
			Post post = invocation.getArgument(0);
			if (post.getSourceItemId().equals("7")) {
				throw violation("uq_posts_source_item", "23505");
			}
			if (post.getSourceItemId().equals("9")) {
				throw violation("ck_posts_channel", "23514");
			}
			setId(post);
			saved.add(post);
			return post;
		});

		StoredBatch batch = store.store(fetched(List.of(post(ItemType.POST, "6"), post(ItemType.POST, "7"),
				post(ItemType.POST, "8"), post(ItemType.POST, "9"))));

		// The chunk's transaction rolled back (its "6" with it); stored one by one, "7" is a duplicate and "9"
		// rejected, the others stored once each.
		assertThat(transactions).hasSize(5);
		assertThat(saved.subList(1, saved.size())).extracting(Post::getSourceItemId).containsExactly("6", "8");
		assertThat(batch.newPostIds()).hasSize(2)
			.containsExactlyElementsOf(saved.subList(1, saved.size()).stream().map(Post::getId).toList());
		assertThat(batch.rejected()).containsExactly(new RejectedPost(Source.WYKOP, ItemType.POST, "9", "ck_posts_channel"));
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 4, 2, 1, 1));
		assertThat(warnings(output)).singleElement()
			.satisfies(line -> assertThat(line).contains("the database rejected wykop post 9 at store"));
		assertThat(output.getAll()).contains("Run " + RUN + " store wykop: 4 post(s), new 2, duplicates 1, rejected 1");
	}

	@Test
	void aPostRepeatedInTheFetchIsADuplicate() {
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenReturn(List.of());

		StoredBatch batch = store.store(
				fetched(List.of(post(ItemType.POST, "1"), post(ItemType.POST, "1"), post(ItemType.POST, "2"))));

		assertThat(saved).extracting(Post::getSourceItemId).containsExactly("1", "2");
		assertThat(batch.newPostIds()).hasSize(2);
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 3, 2, 1, 0));
	}

	@Test
	void eachSourcesPostsAreCountedInSourceOrder(CapturedOutput output) {
		when(posts.findKeys(any(), anyCollection())).thenReturn(List.of());
		when(posts.findKeys(eq(Source.REDDIT), anyCollection())).thenReturn(List.of(new PostKey(ItemType.POST, "r1")));
		List<SourcePost> both = List.of(post(Source.REDDIT, ItemType.POST, "r1"), post(Source.REDDIT, ItemType.POST, "r2"),
				post(Source.WYKOP, ItemType.POST, "w1"));

		StoredBatch batch = store.store(new FetchedBatch(RUN, both, 3, 0,
				List.of(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, 1, 0, null),
						new SourceFetch(Source.REDDIT, SourceFetchStatus.SUCCEEDED, 2, 0, null))));

		assertThat(batch.newPostIds()).hasSize(2);
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 1, 1, 0, 0),
				new SourceStored(Source.REDDIT, 2, 1, 1, 0));
		assertThat(batch.rejectedBySource()).isEmpty();
		assertThat(output.getAll()).contains("Run " + RUN + " store wykop: 1 post(s), new 1, duplicates 0, rejected 0")
			.contains("Run " + RUN + " store reddit: 2 post(s), new 1, duplicates 1, rejected 0");
	}

	@Test
	void theCauseIsTheConstraintOtherwiseTheSqlStateOtherwiseUnknown() {
		assertThat(rejectedBy(violation("ck_posts_source", "23514"))).isEqualTo("ck_posts_source");
		// A data error (SQLSTATE class 22): Hibernate names no constraint.
		assertThat(rejectedBy(new DataIntegrityViolationException("could not execute batch",
				new DataException(ROW_VALUES, new SQLException(ROW_VALUES, "22021"))))).isEqualTo("SQLState 22021");
		// A NOT NULL: Hibernate's PostgreSQL extractor names the column (its template `column "…"`).
		assertThat(rejectedBy(new DataIntegrityViolationException("could not execute batch",
				new ConstraintViolationException(ROW_VALUES, new SQLException(ROW_VALUES, "23502"), "channel"))))
			.isEqualTo("channel");
		// A JDBC batch reports its row's error as the batch exception's next exception.
		BatchUpdateException batchFailure = new BatchUpdateException(ROW_VALUES, null, 0, new int[0]);
		batchFailure.setNextException(new SQLException(ROW_VALUES, "22021"));
		assertThat(rejectedBy(new DataIntegrityViolationException("could not execute batch", batchFailure)))
			.isEqualTo("SQLState 22021");
		// A constraint's name wins over an SQLState nearer the top.
		BatchUpdateException named = new BatchUpdateException(ROW_VALUES, "23503", 0, new int[0], null);
		named.setNextException(new SQLException(ROW_VALUES, "23503"));
		assertThat(rejectedBy(new DataIntegrityViolationException("x",
				new ConstraintViolationException(ROW_VALUES, named, "fk_posts_first_run")))).isEqualTo("fk_posts_first_run");
		assertThat(rejectedBy(new DataIntegrityViolationException(ROW_VALUES))).isEqualTo("unknown");
	}

	@Test
	void aConstraintWithoutANameOrWithABlankOneGivesWayToTheSqlState() {
		assertThat(rejectedBy(new DataIntegrityViolationException("x",
				new ConstraintViolationException(ROW_VALUES, new SQLException(ROW_VALUES, "23502"), null))))
			.isEqualTo("SQLState 23502");
		assertThat(rejectedBy(new DataIntegrityViolationException("x",
				new ConstraintViolationException(ROW_VALUES, new SQLException(ROW_VALUES, "23502"), " "))))
			.isEqualTo("SQLState 23502");
	}

	@Test
	void anSqlExceptionWithoutAStateOrWithABlankOneIsUnknown() {
		assertThat(rejectedBy(new DataIntegrityViolationException("x", new SQLException(ROW_VALUES))))
			.isEqualTo("unknown");
		assertThat(rejectedBy(new DataIntegrityViolationException("x", new SQLException(ROW_VALUES, " "))))
			.isEqualTo("unknown");
	}

	@Test
	void aCauseChainThatLoopsBackEndsItsSearch() {
		// A batch's next exceptions pointing back at the batch: each is searched once.
		SQLException batch = new SQLException(ROW_VALUES);
		SQLException row = new SQLException(ROW_VALUES);
		batch.setNextException(row);
		row.setNextException(batch);

		assertThat(rejectedBy(new DataIntegrityViolationException("x", batch))).isEqualTo("unknown");
	}

	@Test
	void theRejectedSummaryNamesTenPostsAndCountsTheRest() {
		List<RejectedPost> rejected = new ArrayList<>();
		IntStream.rangeClosed(1, 10).forEach(i -> rejected.add(new RejectedPost(Source.WYKOP, ItemType.POST, "w" + i,
				"ck_posts_channel")));
		rejected.add(new RejectedPost(Source.REDDIT, ItemType.LINK, "r1", "SQLState 22021"));
		rejected.add(new RejectedPost(Source.REDDIT, ItemType.POST, "r2", "unknown"));
		StoredBatch batch = new StoredBatch(RUN, List.of(), List.of(new SourceStored(Source.WYKOP, 10, 0, 0, 10),
				new SourceStored(Source.REDDIT, 2, 0, 0, 2)), rejected);

		assertThat(batch.rejectedSummary()).contains("store rejected 12 post(s), counted as skipped: "
				+ String.join("; ", IntStream.rangeClosed(1, 10).mapToObj(i -> "wykop post w" + i + " (ck_posts_channel)")
					.toList())
				+ "; and 2 more");
		assertThat(batch.rejectedBySource()).containsExactly(entry(Source.WYKOP, 10), entry(Source.REDDIT, 2));
		assertThat(new StoredBatch(RUN, List.of(), List.of(), rejected.subList(10, 12)).rejectedSummary())
			.contains("store rejected 2 post(s), counted as skipped: reddit link r1 (SQLState 22021); reddit post r2"
					+ " (unknown)");
	}

	@Test
	void aSourcesCountsThatDoNotAddUpAreRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SourceStored(Source.WYKOP, 3, 1, 1, 0))
			.withMessageContaining("do not add up");
		assertThatIllegalArgumentException().isThrownBy(() -> new SourceStored(Source.WYKOP, 3, 1, 1, 2));
		assertThatIllegalArgumentException().isThrownBy(() -> new SourceStored(Source.WYKOP, 0, 1, -1, 0))
			.withMessageContaining("negative");
		assertThat(new SourceStored(Source.WYKOP, 4, 1, 2, 1).posts()).isEqualTo(4);
	}

	@Test
	void newPostsAreStoredInChunksRawPayloadsFirst() {
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenReturn(List.of());
		List<SourcePost> many = IntStream.range(0, PostStore.STORE_CHUNK + 2)
			.mapToObj(i -> post(ItemType.POST, String.valueOf(i)))
			.toList();

		StoredBatch batch = store.store(fetched(many));

		assertThat(batch.newPostIds()).hasSize(many.size());
		assertThat(transactions).hasSize(2);
		assertThat(transactions.getLast()).containsExactly("raw", "raw", String.valueOf(PostStore.STORE_CHUNK),
				String.valueOf(PostStore.STORE_CHUNK + 1));
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, many.size(), many.size(), 0, 0));
	}

	@Test
	void exactlyOneChunkOfNewPostsIsOneTransaction() {
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenReturn(List.of());
		List<SourcePost> chunk = IntStream.range(0, PostStore.STORE_CHUNK)
			.mapToObj(i -> post(ItemType.POST, String.valueOf(i)))
			.toList();

		StoredBatch batch = store.store(fetched(chunk));

		assertThat(transactions).hasSize(1);
		assertThat(batch.newPostIds()).hasSize(PostStore.STORE_CHUNK);
	}

	@Test
	void aFetchOfOnlyStoredPostsOpensNoTransaction() {
		when(posts.findKeys(eq(Source.WYKOP), anyCollection()))
			.thenReturn(List.of(new PostKey(ItemType.POST, "1"), new PostKey(ItemType.POST, "2")));

		StoredBatch batch = store.store(fetched(List.of(post(ItemType.POST, "1"), post(ItemType.POST, "2"))));

		assertThat(transactions).isEmpty();
		assertThat(batch.newPostIds()).isEmpty();
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 2, 0, 2, 0));
	}

	@Test
	void anInterruptStoresNoFurtherChunk() {
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenReturn(List.of());
		// The run is stopped while its first chunk is stored.
		when(posts.save(any(Post.class))).thenAnswer(invocation -> {
			Thread.currentThread().interrupt();
			Post post = invocation.getArgument(0);
			setId(post);
			saved.add(post);
			return post;
		});
		List<SourcePost> many = IntStream.range(0, PostStore.STORE_CHUNK + 1)
			.mapToObj(i -> post(ItemType.POST, String.valueOf(i)))
			.toList();

		try {
			assertThatExceptionOfType(RunInterruptedException.class).isThrownBy(() -> store.store(fetched(many)));
		}
		finally {
			Thread.interrupted();
		}

		assertThat(transactions).hasSize(1);
		assertThat(saved).hasSize(PostStore.STORE_CHUNK);
	}

	@Test
	void keysAreLookedUpInChunks() {
		List<List<String>> lookups = new ArrayList<>();
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenAnswer(invocation -> {
			lookups.add(List.copyOf(invocation.<Collection<String>>getArgument(1)));
			return List.of();
		});
		List<SourcePost> many = IntStream.range(0, 2_500).mapToObj(i -> post(ItemType.POST, String.valueOf(i))).toList();

		StoredBatch batch = store.store(fetched(many));

		assertThat(lookups).extracting(List::size).containsExactly(1000, 1000, 500);
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 2_500, 2_500, 0, 0));
	}

	@Test
	void keysOfExactlyOneLookupChunkAreLookedUpOnce() {
		List<List<String>> lookups = new ArrayList<>();
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenAnswer(invocation -> {
			lookups.add(List.copyOf(invocation.<Collection<String>>getArgument(1)));
			return List.of();
		});
		List<SourcePost> many = IntStream.range(0, 2 * PostStore.KEY_CHUNK)
			.mapToObj(i -> post(ItemType.POST, String.valueOf(i)))
			.toList();

		store.store(fetched(many));

		assertThat(lookups).extracting(List::size).containsExactly(PostStore.KEY_CHUNK, PostStore.KEY_CHUNK);
	}

	/** The cause {@link PostStore} names for a single post whose save fails with {@code failure}. */
	private String rejectedBy(DataIntegrityViolationException failure) {
		PostRepository failing = mock(PostRepository.class);
		when(failing.findKeys(any(), anyCollection())).thenReturn(List.of());
		when(failing.save(any(Post.class))).thenThrow(failure);
		StoredBatch batch = new PostStore(failing, raw, transaction()).store(fetched(List.of(post(ItemType.POST, "1"))));
		assertThat(batch.sources()).containsExactly(new SourceStored(Source.WYKOP, 1, 0, 0, 1));
		return batch.rejected().getFirst().cause();
	}

	/** A violated constraint as the commit reports it: Spring's exception around Hibernate's, with the name. */
	private static DataIntegrityViolationException violation(String constraint, String sqlState) {
		return new DataIntegrityViolationException("could not execute batch; " + ROW_VALUES,
				new ConstraintViolationException(ROW_VALUES, new SQLException(ROW_VALUES, sqlState), constraint));
	}

	private static List<String> warnings(CapturedOutput output) {
		return output.getAll().lines().filter(line -> line.contains(" WARN ")).toList();
	}

	/** Wykop's fetch of {@code posts}, every element a post. */
	private static FetchedBatch fetched(List<SourcePost> posts) {
		return new FetchedBatch(RUN, posts, posts.size(), 0,
				List.of(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, posts.size(), 0, null)));
	}

	private static SourcePost post(ItemType type, String id) {
		return post(Source.WYKOP, type, id);
	}

	private static SourcePost post(Source source, ItemType type, String id) {
		return new SourcePost(source, type, id, "gielda", Instant.parse("2026-10-01T08:00:00Z"), null,
				"treść " + id, "autor", null, null, List.of("gielda"), ("{\"id\":\"" + id + "\"}").getBytes());
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate() {

			@Override
			public <T> T execute(TransactionCallback<T> action) {
				transactions.add(new ArrayList<>());
				return action.doInTransaction(new SimpleTransactionStatus());
			}

		};
	}

	private static void setId(Post post) {
		try {
			Field id = Post.class.getDeclaredField("id");
			id.setAccessible(true);
			id.set(post, UUID.randomUUID());
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
