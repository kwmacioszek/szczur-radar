package pl.szczurradar.api.posts;

import pl.szczurradar.api.agents.assessor.contract.ItemType;

/** The identity of a post within one source: its type in the shared model and its source id. */
public record PostKey(ItemType itemType, String sourceItemId) {

}
