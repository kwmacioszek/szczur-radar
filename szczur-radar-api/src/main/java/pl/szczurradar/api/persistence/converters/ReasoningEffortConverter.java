package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.llm.ReasoningEffort;
import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class ReasoningEffortConverter extends EnumValueConverter<ReasoningEffort> {

	ReasoningEffortConverter() {
		super(ReasoningEffort.class, ReasoningEffort::id);
	}

}
