package pl.szczurradar.api.pipeline.assess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.ContractFixtures;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.config.LlmProviderProperties.Provider;
import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmResponse;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.pipeline.PostTransitions;
import pl.szczurradar.api.pipeline.RunFlow;
import pl.szczurradar.api.pipeline.RunRecorder;
import pl.szczurradar.api.pipeline.RunStatus;
import pl.szczurradar.api.posts.Assessment;
import pl.szczurradar.api.posts.Post;
import pl.szczurradar.api.posts.PostRepository;
import pl.szczurradar.api.posts.PostStage;
import pl.szczurradar.api.raw.RawPayloadRef;
import pl.szczurradar.api.raw.RawPayloadStore;
import pl.szczurradar.api.sources.RawInputMapper;
import pl.szczurradar.api.sources.Source;

/**
 * {@link ConcurrentAssessmentDispatcher} on its own pool of {@value #THREADS} threads, one scripted
 * {@link LlmClient} and the posts' stages kept in memory in place of the database.
 */
class ConcurrentAssessmentDispatcherTest {

	private static final int THREADS = 3;

	private static final UUID RUN = UUID.randomUUID();

	private static final String BOOM = "this post breaks the client";

	private final ExecutorService executor = Executors.newFixedThreadPool(THREADS);

	/** Each post's body, by id; the raw payload is the body's bytes. */
	private final Map<UUID, String> bodies = new ConcurrentHashMap<>();

	private final Map<UUID, PostStage> stages = new ConcurrentHashMap<>();

	private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();

	private final List<Assessment> recorded = new CopyOnWriteArrayList<>();

	private final AtomicInteger calls = new AtomicInteger();

	private final AtomicInteger inFlight = new AtomicInteger();

	private final AtomicInteger maxInFlight = new AtomicInteger();

	@AfterEach
	void stop() {
		executor.shutdownNow();
	}

	@Test
	void eachPostIsAssessedOnceWithinTheThreadLimitAndATechnicalErrorPutsThePostBack() {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			// One post decided by the prefilter, one that breaks the client, the rest assessed by the model.
			ids.add(post(i == 0 ? "#gielda <url>" : i == 5 ? BOOM : ContractFixtures.validInput().item().body()));
		}
		UUID broken = ids.get(5);

		RunFlow.AssessedBatch batch = dispatcher().dispatch(RUN, ids);

		assertThat(recorded).extracting(Assessment::getPostId).doesNotHaveDuplicates()
			.containsExactlyInAnyOrderElementsOf(ids.stream().filter(id -> !id.equals(broken)).toList());
		assertThat(recorded).filteredOn(assessment -> assessment.getProvenance().source() == Provenance.Source.PREFILTER)
			.singleElement()
			.satisfies(assessment -> assertThat(assessment.getPostId()).isEqualTo(ids.getFirst()));
		assertThat(calls).hasValue(9);
		assertThat(maxInFlight.get()).isBetween(2, THREADS);
		assertThat(batch.ok()).isEqualTo(9);
		assertThat(batch.failed()).isZero();
		assertThat(batch.processingErrors()).singleElement().satisfies(error -> {
			assertThat(error.postId()).isEqualTo(broken);
			assertThat(error.message()).contains("IllegalStateException");
		});
		assertThat(batch.status()).isEqualTo(RunStatus.PARTIAL);
		// Put back where it was, without an attempt; every other post assessed.
		assertThat(stages.get(broken)).isEqualTo(PostStage.STORED);
		assertThat(attempts.get(broken)).isZero();
		assertThat(stages).allSatisfy((id, stage) -> assertThat(stage).isIn(PostStage.ASSESSED, PostStage.STORED));
	}

	@Test
	void aPostThatCannotBeTakenIsLeftAlone() {
		UUID assessed = post(ContractFixtures.validInput().item().body());
		stages.put(assessed, PostStage.ASSESSED);

		RunFlow.AssessedBatch batch = dispatcher().dispatch(RUN, List.of(assessed));

		assertThat(recorded).isEmpty();
		assertThat(calls).hasValue(0);
		assertThat(batch).isEqualTo(new RunFlow.AssessedBatch(RUN, 0, 0, 0, List.of()));
	}

	private UUID post(String body) {
		UUID id = UUID.randomUUID();
		bodies.put(id, body);
		stages.put(id, PostStage.STORED);
		attempts.put(id, 0);
		return id;
	}

	private ConcurrentAssessmentDispatcher dispatcher() {
		Clock clock = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC);
		LlmClient llm = request -> {
			calls.incrementAndGet();
			maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
			try {
				Thread.sleep(50);
				if (request.userMessage().contains(BOOM)) {
					throw new IllegalStateException("a bug in the client");
				}
				return new LlmResponse(ContractJson.MAPPER.writeValueAsString(ContractFixtures.validOutput()),
						Provider.GEMINI, "gemini-3.1-flash-lite", Channel.GEMINI, ServiceTier.DEFAULT, false, null,
						OutputMode.JSON_SCHEMA, 100, 50, null, 1);
			}
			catch (InterruptedException ex) {
				throw new IllegalStateException(ex);
			}
			finally {
				inFlight.decrementAndGet();
			}
		};
		return new ConcurrentAssessmentDispatcher(posts(), rawPayloads(), List.of(inputMapper()),
				new Assessor(llm, Provider.GEMINI, "gemini-3.1-flash-lite", clock), llm, transitions(), recorder(), executor,
				clock);
	}

	private PostRepository posts() {
		PostRepository posts = mock(PostRepository.class);
		when(posts.findById(any(UUID.class))).thenAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			Post post = new Post(Source.WYKOP, ItemType.POST, id.toString(), Instant.now(), null, bodies.get(id), null,
					null, null, List.of(), new RawPayloadRef(id), RUN);
			return Optional.of(post);
		});
		return posts;
	}

	private RawPayloadStore rawPayloads() {
		RawPayloadStore raw = mock(RawPayloadStore.class);
		when(raw.load(any(RawPayloadRef.class)))
			.thenAnswer(invocation -> bodies.get(invocation.<RawPayloadRef>getArgument(0).id()).getBytes(StandardCharsets.UTF_8));
		return raw;
	}

	private static RawInputMapper inputMapper() {
		return new RawInputMapper() {

			@Override
			public Source source() {
				return Source.WYKOP;
			}

			@Override
			public AssessmentInputV1 map(byte[] raw) {
				return ContractFixtures.post(new String(raw, StandardCharsets.UTF_8));
			}

		};
	}

	/** The conditional updates, in memory: take stored/failed, store only while held, put back. */
	private PostTransitions transitions() {
		PostTransitions transitions = mock(PostTransitions.class);
		when(transitions.claim(any(UUID.class), any(UUID.class))).thenAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			boolean taken = stages.replace(id, PostStage.STORED, PostStage.ASSESSING)
					|| stages.replace(id, PostStage.FAILED, PostStage.ASSESSING);
			return taken ? Optional.of(attempts.get(id)) : Optional.empty();
		});
		when(transitions.record(any(UUID.class), any(UUID.class), anyInt(), any(Assessment.class))).thenAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			Assessment assessment = invocation.getArgument(3);
			if (!stages.replace(id, PostStage.ASSESSING, assessment.getFailureClass() == null ? PostStage.ASSESSED
					: PostStage.FAILED)) {
				return false;
			}
			recorded.add(assessment);
			return true;
		});
		doAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			stages.replace(id, PostStage.ASSESSING, attempts.get(id) == 0 ? PostStage.STORED : PostStage.FAILED);
			return null;
		}).when(transitions).release(any(UUID.class), any(UUID.class));
		return transitions;
	}

	private static RunRecorder recorder() {
		RunRecorder recorder = mock(RunRecorder.class);
		when(recorder.isRunning(RUN)).thenReturn(true);
		return recorder;
	}

}
