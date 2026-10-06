package pl.szczurradar.api.sources.wykop;

import java.util.List;

/**
 * The elements a search returned, each once, and how many elements could not be read as a
 * {@link WykopItem} (unexpected types in the answer) and were skipped.
 */
public record WykopSearchResult(List<WykopItem> items, int skippedElements) {

	public WykopSearchResult {
		items = List.copyOf(items);
	}

}
