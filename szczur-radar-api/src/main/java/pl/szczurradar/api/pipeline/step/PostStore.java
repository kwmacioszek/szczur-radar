package pl.szczurradar.api.pipeline.step;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pl.szczurradar.api.pipeline.flow.RunFlow.FetchedBatch;
import pl.szczurradar.api.pipeline.flow.RunFlow.RejectedPost;
import pl.szczurradar.api.pipeline.flow.RunFlow.SourceStored;
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
 * (FR-003). Each post the step gets ends as one of three (S-38), counted per source in the
 * {@link StoredBatch} ({@link SourceStored}: {@code posts = new + duplicates + rejected}):
 *
 * <ul>
 * <li><b>new</b> — stored by this run, in {@link StoredBatch#newPostIds()};
 * <li><b>duplicate</b> — already stored by an earlier run, repeated in the same fetch, or stored meanwhile by
 * another run: its save failed on the unique key, and reading its key again finds it stored; left as it is;
 * <li><b>rejected</b> — its save failed with a {@link DataIntegrityViolationException} and its key is still not
 * stored: the database refused the row (a {@code CHECK}, {@code NOT NULL}, a foreign key, a data error). Its raw
 * payload rolled back with it, the rest of its chunk is stored; it is a {@link RejectedPost} with its cause
 * (the constraint's name, otherwise {@code SQLState <code>}, otherwise {@code unknown}) and a WARN line,
 * which the run counts as skipped after the step.
 * </ul>
 *
 * <p>New posts are stored in chunks of {@value #STORE_CHUNK}, one transaction each: first the chunk's
 * raw payloads, then its posts, so Hibernate sends each as JDBC batches
 * ({@code hibernate.jdbc.batch_size}) instead of a round trip per row. The {@code INSERT}s go at the commit,
 * so a chunk's failure comes from it. A chunk that fails with a {@link DataIntegrityViolationException} is
 * stored again post by post, each in its own transaction; a post whose save fails there has its key read
 * again, after its transaction rolled back, to tell a duplicate from a rejection. Any other exception — of a
 * chunk, a single post or that read — ends the step.
 *
 * <p>No line names the exception's message: the database's message holds the row's values, the post's text.
 *
 * <p>Before each chunk the run's thread is checked for an interrupt ({@link RunInterruptedException}):
 * a run the watchdog or a stopping instance ended stores at most the chunk it was in.
 */
@Service
public class PostStore {

	private static final Logger log = LoggerFactory.getLogger(PostStore.class);

	/** How many source ids one key lookup sends (a search may return thousands). */
	static final int KEY_CHUNK = 1000;

	/** How many new posts one transaction stores. */
	static final int STORE_CHUNK = 500;

	/** The cause of a rejection whose exception names neither a constraint nor an SQLState. */
	static final String UNKNOWN_CAUSE = "unknown";

	private final PostRepository posts;

	private final RawPayloadStore rawPayloads;

	private final TransactionTemplate transaction;

	PostStore(PostRepository posts, RawPayloadStore rawPayloads, TransactionTemplate transaction) {
		this.posts = posts;
		this.rawPayloads = rawPayloads;
		this.transaction = transaction;
	}

	public StoredBatch store(FetchedBatch batch) {
		UUID runId = batch.runId();
		Set<SourceKey> stored = storedKeys(batch.posts());
		Map<Source, Tally> tallies = new EnumMap<>(Source.class);
		List<SourcePost> fresh = new ArrayList<>();
		for (SourcePost post : batch.posts()) {
			Tally tally = tallies.computeIfAbsent(post.source(), source -> new Tally());
			tally.posts++;
			if (stored.add(SourceKey.of(post))) {
				fresh.add(post);
			}
			else {
				// Stored by an earlier run, or repeated in this fetch.
				tally.duplicates++;
			}
		}
		List<UUID> newPostIds = new ArrayList<>();
		List<RejectedPost> rejected = new ArrayList<>();
		for (int from = 0; from < fresh.size(); from += STORE_CHUNK) {
			RunInterruptedException.checkpoint(runId, RunStep.STORE);
			List<SourcePost> chunk = fresh.subList(from, Math.min(from + STORE_CHUNK, fresh.size()));
			try {
				newPostIds.addAll(transaction.execute(status -> save(chunk, runId)));
				chunk.forEach(post -> tallies.get(post.source()).newPosts++);
			}
			catch (DataIntegrityViolationException chunkFailed) {
				for (SourcePost post : chunk) {
					Tally tally = tallies.get(post.source());
					try {
						newPostIds.addAll(transaction.execute(status -> save(List.of(post), runId)));
						tally.newPosts++;
					}
					catch (DataIntegrityViolationException failed) {
						if (isStored(post)) {
							// Stored meanwhile by another run (uq_posts_source_item): not new here.
							tally.duplicates++;
						}
						else {
							rejected.add(reject(runId, post, failed));
							tally.rejected++;
						}
					}
				}
			}
		}
		List<SourceStored> sources = new ArrayList<>();
		tallies.forEach((source, tally) -> {
			sources.add(new SourceStored(source, tally.posts, tally.newPosts, tally.duplicates, tally.rejected));
			log.info("Run {} store {}: {} post(s), new {}, duplicates {}, rejected {}", runId, source.value(),
					tally.posts, tally.newPosts, tally.duplicates, tally.rejected);
		});
		return new StoredBatch(runId, newPostIds, sources, rejected);
	}

	/** Whether {@code post}'s key is stored, read in a transaction of its own (the post's rolled back). */
	private boolean isStored(SourcePost post) {
		return posts.findKeys(post.source(), List.of(post.sourceItemId()))
			.contains(new PostKey(post.itemType(), post.sourceItemId()));
	}

	/** The rejected {@code post}, logged without the exception (its message holds the row's values). */
	private static RejectedPost reject(UUID runId, SourcePost post, DataIntegrityViolationException failed) {
		String cause = cause(failed);
		log.warn("Run {}: the database rejected {} {} {} at store ({}, {}); counted as skipped", runId,
				post.source().value(), post.itemType().value(), post.sourceItemId(), cause,
				failed.getClass().getSimpleName());
		return new RejectedPost(post.source(), post.itemType(), post.sourceItemId(), cause);
	}

	/**
	 * Why the database refused a row: the name of the violated constraint, as Hibernate read it from the
	 * database's message ({@link ConstraintViolationException#getConstraintName()}), otherwise the first
	 * non-empty {@code SQLState <code>}, otherwise {@value #UNKNOWN_CAUSE}. Searched in the cause chain and
	 * each {@link SQLException}'s next exceptions (a JDBC batch reports its row's error there).
	 */
	static String cause(Throwable failure) {
		List<Throwable> chain = chain(failure);
		for (Throwable throwable : chain) {
			if (throwable instanceof ConstraintViolationException violation && violation.getConstraintName() != null
					&& !violation.getConstraintName().isBlank()) {
				return violation.getConstraintName();
			}
		}
		for (Throwable throwable : chain) {
			if (throwable instanceof SQLException sql && sql.getSQLState() != null && !sql.getSQLState().isBlank()) {
				return "SQLState " + sql.getSQLState();
			}
		}
		return UNKNOWN_CAUSE;
	}

	/** {@code failure}, its causes and each {@link SQLException}'s next exceptions, nearest first, each once. */
	private static List<Throwable> chain(Throwable failure) {
		List<Throwable> chain = new ArrayList<>();
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Deque<Throwable> pending = new ArrayDeque<>(List.of(failure));
		while (!pending.isEmpty()) {
			Throwable throwable = pending.removeFirst();
			if (!seen.add(throwable)) {
				continue;
			}
			chain.add(throwable);
			if (throwable.getCause() != null) {
				pending.addLast(throwable.getCause());
			}
			if (throwable instanceof SQLException sql && sql.getNextException() != null) {
				pending.addLast(sql.getNextException());
			}
		}
		return chain;
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

	/** What happened to one source's posts so far. */
	private static final class Tally {

		int posts;

		int newPosts;

		int duplicates;

		int rejected;

	}

	private record SourceKey(Source source, PostKey key) {

		static SourceKey of(SourcePost post) {
			return new SourceKey(post.source(), new PostKey(post.itemType(), post.sourceItemId()));
		}

	}

}
