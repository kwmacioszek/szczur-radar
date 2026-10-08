package pl.szczurradar.api.pipeline;

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

import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostKey;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourcePost;

/**
 * The store step: each new post with its raw payload, once per {@code (source, item type, source id)}
 * (FR-003). Posts already stored — by an earlier run, or by another run meanwhile (the unique key) —
 * are left as they are and are not in the {@link RunFlow.StoredBatch}.
 */
@Service
public class PostStore {

	/** How many source ids one key lookup sends (a search may return thousands). */
	static final int KEY_CHUNK = 1000;

	private final PostRepository posts;

	private final RawPayloadStore rawPayloads;

	private final TransactionTemplate transaction;

	PostStore(PostRepository posts, RawPayloadStore rawPayloads, TransactionTemplate transaction) {
		this.posts = posts;
		this.rawPayloads = rawPayloads;
		this.transaction = transaction;
	}

	public RunFlow.StoredBatch store(RunFlow.FetchedBatch batch) {
		Set<SourceKey> stored = storedKeys(batch.posts());
		List<UUID> newPostIds = new ArrayList<>();
		for (SourcePost post : batch.posts()) {
			if (!stored.add(SourceKey.of(post))) {
				continue;
			}
			try {
				newPostIds.add(transaction.execute(status -> posts.save(new Post(post.source(), post.itemType(),
						post.sourceItemId(), post.publishedAt(), post.title(), post.body(), post.author(), post.slug(),
						post.webUrl(), post.tags(), rawPayloads.store(post.source(), post.raw()), batch.runId()))
					.getId()));
			}
			catch (DataIntegrityViolationException ex) {
				// Stored meanwhile by another run (uq_posts_source_item): not new here.
			}
		}
		return new RunFlow.StoredBatch(batch.runId(), newPostIds);
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
