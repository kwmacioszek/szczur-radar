package pl.szczurradar.api.sources;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.sources.wykop.WykopItem;
import pl.szczurradar.api.sources.wykop.WykopPosts;
import pl.szczurradar.api.sources.wykop.WykopSearchResult;

/**
 * A second source for the engine's integration tests, imported by those that use it: a
 * {@link ScriptedSourceFetcher} of {@link Source#REDDIT} (not configured until a scenario says so) and its
 * {@link RawInputMapper}, which maps a stored payload with the production {@link WykopPosts} bean. Its
 * post ({@link #post()}) is the real, anonymized Wykop entry {@code fixtures/wykop/entry.json} made by
 * {@link WykopPosts#post}, with only {@link SourcePost#source()} changed: a post of the second source goes
 * through the production mapping of its raw payload and the assessment, with no parser of the test's own.
 *
 * <p>This isolates the engine under a second source's key; it proves nothing about Reddit's format. The
 * real Reddit source (S-27) is tested with its own mapper on its own fixtures.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ScriptedSourceConfiguration {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Bean
	ScriptedSourceFetcher redditFetcher() {
		return new ScriptedSourceFetcher(Source.REDDIT);
	}

	@Bean
	RawInputMapper redditInputs(WykopPosts wykop) {
		return new RawInputMapper() {

			@Override
			public Source source() {
				return Source.REDDIT;
			}

			@Override
			public AssessmentInputV1 map(byte[] raw) {
				return wykop.map(raw);
			}

		};
	}

	/** The bytes of {@code fixtures/wykop/entry.json}: the raw payload of {@link #post()}. */
	public static byte[] entry() {
		try (InputStream in = ScriptedSourceConfiguration.class.getResourceAsStream("/fixtures/wykop/entry.json")) {
			return in.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** The entry as {@link WykopPosts#post} makes it, of {@link Source#REDDIT}: every other field unchanged. */
	public static SourcePost post() {
		JsonNode raw = JSON.readTree(entry());
		SourcePost post = WykopPosts.post(new WykopSearchResult.Element(JSON.treeToValue(raw, WykopItem.class), raw));
		return new SourcePost(Source.REDDIT, post.itemType(), post.sourceItemId(), post.publishedAt(), post.title(),
				post.body(), post.author(), post.slug(), post.webUrl(), post.tags(), post.raw());
	}

}
