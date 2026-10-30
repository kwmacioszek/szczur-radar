package pl.szczurradar.api.pipeline.step;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.run.RunStep;
import pl.szczurradar.api.pipeline.state.RunInterruptedException;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostKey;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourcePost;

/**
 * The store step: each new post with its raw payload, once per {@code (source, item type, source id)}
 * (FR-003). Posts already stored — by an earlier run, or by another run meanwhile (the unique key) —
 * are left as they are and are not in the {@link StoredBatch}.
 *
 * <p>New posts are stored in chunks of {@value #STORE_CHUNK}, one transaction each: first the chunk's
 * raw payloads, then its posts, so Hibernate sends each as JDBC batches
 * ({@code hibernate.jdbc.batch_size}) instead of a round trip per row. A chunk that hits the unique
 * key (a post stored meanwhile by another run) is stored again post by post, skipping that one.
 *
 * <p>Before each chunk the run's thread is checked for an interrupt ({@link RunInterruptedException}):
 * a run the watchdog or a stopping instance ended stores at most the chunk it was in.
 */
@Service
public class PostStore {

	/** How many source ids one key lookup sends (a search may return thousands). */
	static final int KEY_CHUNK = 1000;

	/** How many new posts one transaction stores. */
	static final int STORE_CHUNK = 500;

	private final PostRepository posts;

	private final RawPayloadStore rawPayloads;

	private final TransactionTemplate transaction;

	PostStore(PostRepository posts, RawPayloadStore rawPayloads, TransactionTemplate transaction) {
		this.posts = posts;
		this.rawPayloads = rawPayloads;
		this.transaction = transaction;
	}

	public StoredBatch store(FetchedBatch batch) {
		Set<SourceKey> stored = storedKeys(batch.posts());
		List<SourcePost> fresh = batch.posts().stream().filter(post -> stored.add(SourceKey.of(post))).toList();
		List<UUID> newPostIds = new ArrayList<>();
		for (int from = 0; from < fresh.size(); from += STORE_CHUNK) {
			RunInterruptedException.checkpoint(batch.runId(), RunStep.STORE);
			List<SourcePost> chunk = fresh.subList(from, Math.min(from + STORE_CHUNK, fresh.size()));
			try {
				newPostIds.addAll(transaction.execute(status -> save(chunk, batch.runId())));
			}
			catch (DataIntegrityViolationException ex) {
				for (SourcePost post : chunk) {
					try {
						newPostIds.addAll(transaction.execute(status -> save(List.of(post), batch.runId())));
					}
					catch (DataIntegrityViolationException duplicate) {
						// Stored meanwhile by another run (uq_posts_source_item): not new here.
					}
				}
			}
		}
		return new StoredBatch(batch.runId(), newPostIds);
	}

	/** The raw payloads of {@code chunk}, then its posts (in this order, so each is one JDBC batch). */
	private List<UUID> save(List<SourcePost> chunk, UUID runId) {
		List<RawPayloadRef> raws = chunk.stream().map(post -> rawPayloads.store(post.source(), post.raw())).toList();
		List<UUID> ids = new ArrayList<>(chunk.size());
		for (int i = 0; i < chunk.size(); i++) {
			SourcePost post = chunk.get(i);
			ids.add(posts.save(new Post(post.source(), post.itemType(), post.sourceItemId(), post.channel(),
					post.publishedAt(), post.title(), post.body(), post.author(), post.slug(), post.webUrl(), post.tags(),
					raws.get(i), runId))
				.getId());
		}
		return ids;
	}

	private Set<SourceKey> storedKeys(List<SourcePost> batch) {
		Map<Source, List<String>> idsBySource = batch.stream()
			.collect(Collectors.groupingBy(SourcePost::source, Collectors.mapping(SourcePost::sourceItemId, Collectors.toList())));
		Set<SourceKey> stored = new HashSet<>();
		idsBySource.forEach((source, ids) -> {
			List<String> distinct = ids.stream().distinct().toList();
			for (int from = 0; from < distinct.size(); from += KEY_CHUNK) {
				for (PostKey key : posts.findKeys(source, distinct.subList(from, Math.min(from + KEY_CHUNK, distinct.size())))) {
					stored.add(new SourceKey(source, key));
				}
			}
		});
		return stored;
	}

	private record SourceKey(Source source, PostKey key) {

		static SourceKey of(SourcePost post) {
			return new SourceKey(post.source(), new PostKey(post.itemType(), post.sourceItemId()));
		}

	}

}
