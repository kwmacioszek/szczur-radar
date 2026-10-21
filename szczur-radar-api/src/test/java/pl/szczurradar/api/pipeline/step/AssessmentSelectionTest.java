package pl.szczurradar.api.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.pipeline.flow.RunFlow.SelectionTarget;
import pl.szczurradar.api.pipeline.flow.RunFlow.StoredBatch;
import pl.szczurradar.api.pipeline.run.RunKind;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.sources.Source;

/** {@link AssessmentSelection}: one attempt per post and request. */
class AssessmentSelectionTest {

	private static final UUID RUN = UUID.randomUUID();

	private final PostRepository posts = mock(PostRepository.class);

	private final AssessmentSelection selection = new AssessmentSelection(posts);

	@Test
	void aRangeRunAssessesThePostsItStoredFirstThatAreStillStored() {
		UUID mine = UUID.randomUUID();
		UUID alreadyAssessed = UUID.randomUUID();
		// Only "mine" is still stored among the posts this run stored first (the query's job).
		when(posts.findIdsFirstStoredBy(RUN, PostStage.STORED)).thenReturn(List.of(mine));

		assertThat(selection.fromStored(new StoredBatch(RUN, List.of(mine, alreadyAssessed))).postIds())
			.containsExactly(mine);
	}

	@Test
	void aRangeRunSkipsAStoredPostOfItsOwnThatIsNotInItsBatch() {
		UUID inBatch = UUID.randomUUID();
		UUID notInBatch = UUID.randomUUID();
		when(posts.findIdsFirstStoredBy(RUN, PostStage.STORED)).thenReturn(List.of(notInBatch, inBatch));

		assertThat(selection.fromStored(new StoredBatch(RUN, List.of(inBatch))).postIds()).containsExactly(inBatch);
	}

	@Test
	void aRetryRunTakesEveryLeftover() {
		List<UUID> leftovers = List.of(UUID.randomUUID(), UUID.randomUUID());
		when(posts.findLeftoverIds()).thenReturn(leftovers);

		assertThat(selection.fromTargets(new SelectionTarget(RUN, RunKind.RETRY, List.of())).postIds())
			.containsExactlyElementsOf(leftovers);
	}

	@Test
	void aPostsRunSkipsAssessedDeadAssessingAndUnknownPosts() {
		Post stored = post(PostStage.STORED);
		Post failed = post(PostStage.FAILED);
		Post assessed = post(PostStage.ASSESSED);
		Post dead = post(PostStage.DEAD);
		Post assessing = post(PostStage.ASSESSING);
		UUID unknown = UUID.randomUUID();
		List<UUID> asked = List.of(failed.getId(), assessed.getId(), unknown, dead.getId(), stored.getId(),
				assessing.getId(), failed.getId());
		when(posts.findAllById(asked)).thenReturn(List.of(stored, failed, assessed, dead, assessing));

		assertThat(selection.fromTargets(new SelectionTarget(RUN, RunKind.POSTS, asked)).postIds())
			.containsExactly(failed.getId(), stored.getId());
	}

	@Test
	void aPostsRunTakesPostsOnHold() {
		// On hold only keeps a post out of the RETRY run (findLeftoverIds); an admin's chosen posts take it.
		Post storedOnHold = post(PostStage.STORED);
		Post failedOnHold = post(PostStage.FAILED);
		set(storedOnHold, "technicalFailures", Post.MAX_TECHNICAL_FAILURES);
		set(failedOnHold, "technicalFailures", Post.MAX_TECHNICAL_FAILURES);
		List<UUID> asked = List.of(storedOnHold.getId(), failedOnHold.getId());
		when(posts.findAllById(asked)).thenReturn(List.of(storedOnHold, failedOnHold));

		assertThat(selection.fromTargets(new SelectionTarget(RUN, RunKind.POSTS, asked)).postIds())
			.containsExactly(storedOnHold.getId(), failedOnHold.getId());
	}

	private static Post post(PostStage stage) {
		Post post = new Post(Source.WYKOP, ItemType.POST, UUID.randomUUID().toString(), Instant.now(), null, "treść", null,
				null, null, List.of(), new RawPayloadRef(UUID.randomUUID()), UUID.randomUUID());
		set(post, "id", UUID.randomUUID());
		set(post, "stage", stage);
		return post;
	}

	private static void set(Post post, String field, Object value) {
		try {
			Field declared = Post.class.getDeclaredField(field);
			declared.setAccessible(true);
			declared.set(post, value);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
