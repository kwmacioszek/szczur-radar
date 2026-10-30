package pl.szczurradar.api.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceFetch;
import pl.szczurradar.api.pipeline.flow.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostKey;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourcePost;

/** {@link PostStore}: new posts once per (source, item type, id), with their raw payload. */
class PostStoreTest {

	private static final UUID RUN = UUID.randomUUID();

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
	}

	@Test
	void aPostStoredMeanwhileByAnotherRunIsNotNew() {
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenReturn(List.of());
		when(posts.save(any(Post.class))).thenThrow(new DataIntegrityViolationException("uq_posts_source_item"));

		StoredBatch batch = store.store(fetched(List.of(post(ItemType.POST, "7"))));

		assertThat(batch.newPostIds()).isEmpty();
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
	}

	@Test
	void aChunkThatHitsAPostStoredMeanwhileIsStoredPostByPost() {
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenReturn(List.of());
		when(posts.save(any(Post.class))).thenAnswer(invocation -> {
			Post post = invocation.getArgument(0);
			if (post.getSourceItemId().equals("7")) {
				throw new DataIntegrityViolationException("uq_posts_source_item");
			}
			setId(post);
			saved.add(post);
			return post;
		});

		StoredBatch batch = store.store(
				fetched(List.of(post(ItemType.POST, "6"), post(ItemType.POST, "7"), post(ItemType.POST, "8"))));

		// The chunk's transaction rolled back (its "6" with it); stored one by one, "7" is not new.
		assertThat(transactions).hasSize(4);
		assertThat(batch.newPostIds()).hasSize(2)
			.containsExactlyElementsOf(saved.subList(saved.size() - 2, saved.size()).stream().map(Post::getId).toList());
		assertThat(saved.subList(saved.size() - 2, saved.size())).extracting(Post::getSourceItemId)
			.containsExactly("6", "8");
	}

	@Test
	void keysAreLookedUpInChunks() {
		List<List<String>> lookups = new ArrayList<>();
		when(posts.findKeys(eq(Source.WYKOP), anyCollection())).thenAnswer(invocation -> {
			lookups.add(List.copyOf(invocation.<Collection<String>>getArgument(1)));
			return List.of();
		});
		List<SourcePost> many = IntStream.range(0, 2_500).mapToObj(i -> post(ItemType.POST, String.valueOf(i))).toList();

		store.store(fetched(many));

		assertThat(lookups).extracting(List::size).containsExactly(1000, 1000, 500);
	}

	/** Wykop's fetch of {@code posts}, every element a post. */
	private static FetchedBatch fetched(List<SourcePost> posts) {
		return new FetchedBatch(RUN, posts, posts.size(), 0,
				List.of(new SourceFetch(Source.WYKOP, SourceFetchStatus.SUCCEEDED, posts.size(), 0, null)));
	}

	private static SourcePost post(ItemType type, String id) {
		return new SourcePost(Source.WYKOP, type, id, "gielda", Instant.parse("2026-10-01T08:00:00Z"), null,
				"treść " + id, "autor", null, null, List.of("gielda"), ("{\"id\":" + id + "}").getBytes());
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
