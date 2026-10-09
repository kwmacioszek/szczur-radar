package pl.szczurradar.api.posts;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.pipeline.run.PipelineRun;
import pl.szczurradar.api.pipeline.run.PipelineRunRepository;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * Posts in a given stage with their attempts, for the API's integration tests: stored through the
 * entities, the stage set as the pipeline's conditional updates would (then the persistence context is
 * cleared, so the next read sees the database, not a stale entity). Their first run is a finished one,
 * so it never makes the queue busy.
 */
public final class PostSeeds {

	/** A real, anonymized Wykop entry ({@code fixtures/wykop/entry.json}): the raw payload of every seeded post. */
	private static final byte[] ENTRY = entry();

	private final PostRepository posts;

	private final AssessmentRepository assessments;

	private final RawPayloadStore rawPayloads;

	private final JdbcTemplate jdbc;

	private final EntityManager entityManager;

	private final UUID runId;

	public PostSeeds(PostRepository posts, AssessmentRepository assessments, RawPayloadStore rawPayloads,
			PipelineRunRepository runs, UserRepository users, JdbcTemplate jdbc, EntityManager entityManager) {
		this.posts = posts;
		this.assessments = assessments;
		this.rawPayloads = rawPayloads;
		this.jdbc = jdbc;
		this.entityManager = entityManager;
		UUID admin = users.save(new User("seed-" + UUID.randomUUID() + "@example.com", null, Role.ADMIN)).getId();
		this.runId = runs.saveAndFlush(PipelineRun.retry(admin)).getId();
		jdbc.update("UPDATE pipeline_runs SET status = 'succeeded' WHERE id = ?", runId);
	}

	/** A post in {@code stage} with {@code attempts} failed attempts, published at {@code publishedAt}. */
	public UUID post(PostStage stage, int attempts, String publishedAt) {
		String id = UUID.randomUUID().toString();
		Post post = posts.save(new Post(Source.WYKOP, ItemType.POST, id, Instant.parse(publishedAt), null, "treść " + id,
				"uzytkownik1", null, "https://wykop.pl/wpis/" + id, List.of("gielda"),
				rawPayloads.store(Source.WYKOP, ENTRY), runId));
		posts.flush();
		jdbc.update("UPDATE posts SET stage = ?, assessment_attempts = ? WHERE id = ?", stage.value(), attempts,
				post.getId());
		entityManager.clear();
		return post.getId();
	}

	/** An {@code ok} attempt of {@code postId} at {@code assessedAt}. */
	public Assessment ok(UUID postId, String assessedAt) {
		return assessments.saveAndFlush(new Assessment(postId, runId, ContractFixtures.validInput(),
				new AssessmentResult(AssessmentResult.Status.OK, ContractFixtures.validOutput(), null, List.of(),
						Provenance.prefilter("V2", Instant.parse(assessedAt)), null)));
	}

	/** A failed attempt of {@code postId} at {@code assessedAt}, with {@code failureClass}. */
	public Assessment failed(UUID postId, String assessedAt, FailureClass failureClass) {
		return assessments.saveAndFlush(new Assessment(postId, runId, ContractFixtures.validInput(),
				new AssessmentResult(AssessmentResult.Status.FAILED, null, failureClass,
						List.of(new Violation(failureClass, "rule", "$", "why")),
						new Provenance(Provenance.Source.MODEL, Provider.GEMINI, "gemini-3.1-flash-lite", null,
								ServiceTier.DEFAULT, false, null, null, "p1", "V2", Instant.parse(assessedAt), null, null, null,
								1_000, 1),
						"{}")));
	}

	private static byte[] entry() {
		try (InputStream in = PostSeeds.class.getResourceAsStream("/fixtures/wykop/entry.json")) {
			return in.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** The greater of two ids in Postgres' order (their hex text), the winner of an {@code id DESC} tie. */
	public static UUID greater(UUID a, UUID b) {
		return a.toString().compareTo(b.toString()) > 0 ? a : b;
	}

}
