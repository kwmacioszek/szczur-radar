package pl.szczurradar.api.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.prompt.AssessmentPrompt;
import pl.szczurradar.api.agents.assessor.validation.OutputParser;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmClient;
import pl.szczurradar.api.llm.LlmClientFactory;
import pl.szczurradar.api.llm.LlmProviderProperties;
import pl.szczurradar.api.llm.LlmRequest;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.scenario.RecordedDays.Page;
import pl.szczurradar.api.scenario.RecordedDays.Post;
import pl.szczurradar.api.sources.LocalApiServer;
import pl.szczurradar.api.text.Whitespace;

/**
 * The recordings of the fixture days are complete: a changed source fixture or mapper without a new recording
 * fails here, with the files named, rather than in a scenario. And the scenarios' provider answers the
 * production OpenRouter client with them.
 */
class RecordedDaysTest {

	private static final String RERECORD = " — record again with ScenarioDaysRecorder"
			+ " (src/test/java/pl/szczurradar/api/measurement/README.md)";

	private static final RecordedProvider PROVIDER = new RecordedProvider();

	private static final LocalApiServer SERVER = new LocalApiServer(RecordedProvider.BASE_PATH, PROVIDER);

	private static final LlmClient CLIENT = LlmClientFactory.create(new LlmProviderProperties(Provider.OPENROUTER,
			"openai/gpt-6-luna", "synthetic-key", ServiceTier.DEFAULT, null, Duration.ofSeconds(10)),
			RecordedProvider.baseUrl(SERVER), wait -> {
			});

	@BeforeEach
	void clear() {
		PROVIDER.clearUnmatched();
	}

	@AfterAll
	static void close() {
		SERVER.close();
	}

	@Test
	void everyFixturePostThatGoesToTheModelHasItsRecordingAndNoRecordingIsLeftOver() throws IOException {
		List<Post> posts = RecordedDays.posts();
		assertThat(posts).hasSize(RecordedDays.EXPECTED);
		assertThat(posts.stream().collect(Collectors.groupingBy(Post::page, Collectors.counting())))
			.containsExactlyInAnyOrderEntriesOf(Map.of(Page.WYKOP_ENTRIES, 9L, Page.WYKOP_LINKS, 4L,
					Page.REDDIT_INWESTOWANIE, 14L, Page.REDDIT_STOCKS, 6L));
		assertThat(posts).extracting(Post::userMessage).as("each post is known by its user message").doesNotHaveDuplicates();
		List<String> expected = posts.stream().map(Post::file).toList();
		List<String> recorded;
		try (Stream<Path> files = Files.list(RecordedDays.DIR)) {
			recorded = files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".json")).toList();
		}
		assertThat(expected).as("posts without a recording" + RERECORD).isSubsetOf(recorded);
		assertThat(recorded).as("recordings without a post" + RERECORD).isSubsetOf(expected);
	}

	@Test
	void theProductionClientGetsTheModelsTextForEveryPostAndTheCheck() {
		Map<String, String> texts = RecordedDays.posts()
			.stream()
			.collect(Collectors.toMap(Post::file, post -> CLIENT
				.complete(new LlmRequest(AssessmentPrompt.systemPrompt(), post.userMessage(), OutputParser.schemaText()))
				.rawText()));
		assertThat(texts).hasSize(RecordedDays.EXPECTED)
			.allSatisfy((file, text) -> assertThat(Whitespace.strip(text)).as(file).isNotEmpty());
		assertThat(CLIENT.check().rawText()).isNotEmpty();
		assertThat(PROVIDER.unmatched()).isEmpty();
	}

	@Test
	void anUnknownMessageIsRefusedAndKept() {
		assertThatExceptionOfType(LlmCallException.class).isThrownBy(() -> CLIENT
			.complete(new LlmRequest(AssessmentPrompt.systemPrompt(), "no such post", OutputParser.schemaText())));
		assertThat(PROVIDER.unmatched()).singleElement()
			.extracting(request -> RecordedProvider.userMessage(request.body()))
			.isEqualTo("no such post");
	}

}
