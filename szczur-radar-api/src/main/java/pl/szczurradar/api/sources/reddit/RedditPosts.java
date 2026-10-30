package pl.szczurradar.api.sources.reddit;

import java.util.List;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.sources.RawInputMapper;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourcePost;
import pl.szczurradar.api.sources.UnmappableItemException;

/**
 * Reddit posts as the pipeline's posts: a search element → {@link SourcePost}, and a stored raw payload →
 * the assessment input again. Both go through {@link RedditMapper}, so a retried post gets exactly the
 * input of its first attempt. The post's channel is its subreddit, as the payload spells it: the same as
 * the input's {@code channels[0]}.
 */
@Component
public class RedditPosts implements RawInputMapper {

	/** The author Reddit shows for a deleted account: no author. */
	static final String DELETED_AUTHOR = "[deleted]";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Override
	public Source source() {
		return Source.REDDIT;
	}

	@Override
	public AssessmentInputV1 map(byte[] raw) {
		return RedditMapper.map(JSON.readValue(raw, RedditItem.class));
	}

	/**
	 * The post of a search element.
	 *
	 * @throws UnmappableItemException for an element without an id, without text, without a subreddit or
	 *     without a valid {@code created_utc}, or a removed one
	 */
	public static SourcePost post(RedditSearchResult.Element element) {
		RedditItem item = element.item();
		if (item.id() == null) {
			throw new UnmappableItemException("Reddit post without an id");
		}
		AssessmentInputV1 input = RedditMapper.map(item);
		String subreddit = item.subreddit();
		String author = DELETED_AUTHOR.equals(item.author()) ? null : item.author();
		return new SourcePost(Source.REDDIT, input.item().itemType(), item.id(), subreddit,
				input.item().publishedAt().toInstant(), input.item().title(), input.item().body(), author, null,
				item.webUrl(), List.of(subreddit), JSON.writeValueAsBytes(element.raw()));
	}

}
