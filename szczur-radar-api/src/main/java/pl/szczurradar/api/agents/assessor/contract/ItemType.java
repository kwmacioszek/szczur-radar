package pl.szczurradar.api.agents.assessor.contract;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** {@code item.item_type} (contract-v2.md §2). In the MVP only {@code post} and {@code link} are assessed. */
public enum ItemType {

	POST, LINK, COMMENT;

	/** The contract value: the lower-case constant name. */
	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

}
