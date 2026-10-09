package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class OutputModeConverter extends EnumValueConverter<OutputMode> {

	OutputModeConverter() {
		super(OutputMode.class, OutputMode::value);
	}

}
