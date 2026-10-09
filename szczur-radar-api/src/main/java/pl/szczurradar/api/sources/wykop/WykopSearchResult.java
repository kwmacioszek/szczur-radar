package pl.szczurradar.api.sources.wykop;

import java.util.List;
import java.util.Objects;

import tools.jackson.databind.JsonNode;

/**
 * The elements a search returned, each once, and how many elements could not be read as a
 * {@link WykopItem} (unexpected types in the answer) and were skipped.
 */
public record WykopSearchResult(List<Element> elements, int skippedElements) {

	public WykopSearchResult {
		elements = List.copyOf(elements);
	}

	/**
	 * One element: the fields the assessment needs and the element exactly as the answer had it, with
	 * every field (votes, comments, media, …) — the raw payload the pipeline keeps apart.
	 */
	public record Element(WykopItem item, JsonNode raw) {

		public Element {
			Objects.requireNonNull(item, "item");
			Objects.requireNonNull(raw, "raw");
		}

	}

}
