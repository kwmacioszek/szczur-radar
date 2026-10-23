package pl.szczurradar.api.pipeline.step;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import pl.szczurradar.api.pipeline.flow.RunFlow.PendingAssessments;
import pl.szczurradar.api.pipeline.flow.RunFlow.SelectionTarget;
import pl.szczurradar.api.pipeline.flow.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;

/**
 * The select step: which posts a run assesses. Each post gets at most one attempt per request: a
 * range run takes only the posts it stored first itself (a leftover of the same day is the retry
 * run's), a retry run every leftover regardless of its date except posts on hold, a posts run the
 * given posts that can still be assessed, on hold or not.
 */
@Service
public class AssessmentSelection {

	private final PostRepository posts;

	AssessmentSelection(PostRepository posts) {
		this.posts = posts;
	}

	/** The posts of {@code batch} still stored and first stored by its run. */
	public PendingAssessments fromStored(StoredBatch batch) {
		Set<UUID> stored = new HashSet<>(batch.newPostIds());
		List<UUID> pending = posts.findIdsFirstStoredBy(batch.runId(), PostStage.STORED)
			.stream()
			.filter(stored::contains)
			.toList();
		return new PendingAssessments(batch.runId(), pending);
	}

	/**
	 * {@link RunKind#RETRY}: every leftover (stored, or failed with
	 * attempts left) except posts on hold ({@link PostRepository#findLeftoverIds}), oldest first — no
	 * cap in S-02 (a day has ~270 posts);
	 * {@link RunKind#POSTS}: the given posts in their order, on hold or not, except those
	 * assessed, dead or being assessed.
	 */
	public PendingAssessments fromTargets(SelectionTarget target) {
		List<UUID> pending = switch (target.kind()) {
			case RETRY -> posts.findLeftoverIds();
			case POSTS -> {
				Map<UUID, Post> found = posts.findAllById(target.postIds())
					.stream()
					.collect(Collectors.toMap(Post::getId, Function.identity()));
				yield target.postIds()
					.stream()
					.distinct()
					.filter(id -> found.containsKey(id)
							&& Set.of(PostStage.STORED, PostStage.FAILED).contains(found.get(id).getStage()))
					.toList();
			}
			case RANGE -> throw new IllegalArgumentException("a range run selects from its stored batch");
		};
		return new PendingAssessments(target.runId(), pending);
	}

}
