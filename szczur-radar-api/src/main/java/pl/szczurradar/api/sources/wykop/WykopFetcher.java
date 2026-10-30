package pl.szczurradar.api.sources.wykop;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import pl.szczurradar.api.sources.Source;
import pl.szczurradar.api.sources.SourceFetcher;
import pl.szczurradar.api.sources.SourcePost;
import pl.szczurradar.api.sources.UnmappableItemException;

/**
 * {@link SourceFetcher} for Wykop: #gielda entries and links added in the range ({@link WykopPosts#TAG}),
 * searched by Wykop's own clock (Warsaw local time). Configured when {@code WYKOP_KEY} and
 * {@code WYKOP_SECRET} are set ({@link WykopConfiguration}).
 */
@Component
public class WykopFetcher implements SourceFetcher {

	private final Optional<WykopRepository> repository;

	WykopFetcher(Optional<WykopRepository> repository) {
		this.repository = repository;
	}

	@Override
	public Source source() {
		return Source.WYKOP;
	}

	@Override
	public boolean configured() {
		return repository.isPresent();
	}

	@Override
	public Fetched fetch(Instant from, Instant to) {
		WykopRepository wykop = repository
			.orElseThrow(() -> new IllegalStateException("Wykop is not configured (WYKOP_KEY, WYKOP_SECRET)"));
		WykopSearchResult result = wykop.itemsAddedBetween(WykopPosts.TAG,
				LocalDateTime.ofInstant(from, WykopMapper.WYKOP_ZONE), LocalDateTime.ofInstant(to, WykopMapper.WYKOP_ZONE));
		List<SourcePost> posts = new ArrayList<>();
		int skipped = result.skippedElements();
		for (WykopSearchResult.Element element : result.elements()) {
			try {
				posts.add(WykopPosts.post(element));
			}
			catch (UnmappableItemException ex) {
				skipped++;
			}
		}
		return new Fetched(posts, result.elements().size() + result.skippedElements(), skipped);
	}

}
