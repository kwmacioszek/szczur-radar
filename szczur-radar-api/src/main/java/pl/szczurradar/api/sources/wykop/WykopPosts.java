package pl.szczurradar.api.sources.wykop;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.sources.RawInputMapper;
import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourcePost;
import pl.szczurradar.api.sources.UnmappableItemException;

/**
 * Wykop elements as the pipeline's posts: a search element → {@link SourcePost}, and a stored raw
 * payload → the assessment input again. Both go through {@link WykopMapper}, so a retried post gets
 * exactly the input of its first attempt.
 */
@Component
public class WykopPosts implements RawInputMapper {

	/** The tag whose posts the pipeline reads (#gielda); also the input's {@code channels} and the post's channel. */
	public static final String TAG = "gielda";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Override
	public Source source() {
		return Source.WYKOP;
	}

	@Override
	public AssessmentInputV1 map(byte[] raw) {
		return WykopMapper.map(JSON.readValue(raw, WykopItem.class), TAG);
	}

	/**
	 * The post of a search element.
	 *
	 * @throws UnmappableItemException for an element without an id, of another resource type, without
	 *     text or without a valid {@code created_at}
	 */
	public static SourcePost post(WykopSearchResult.Element element) {
		WykopItem item = element.item();
		if (item.id() == null) {
			throw new UnmappableItemException("Wykop element without an id");
		}
		AssessmentInputV1 input = WykopMapper.map(item, TAG);
		return new SourcePost(Source.WYKOP, input.item().itemType(), String.valueOf(item.id()), TAG,
				input.item().publishedAt().toInstant(), input.item().title(), input.item().body(),
				item.author() == null ? null : item.author().username(), item.slug(), item.webUrl(),
				Objects.requireNonNullElse(item.tags(), List.of()), JSON.writeValueAsBytes(element.raw()));
	}

}
