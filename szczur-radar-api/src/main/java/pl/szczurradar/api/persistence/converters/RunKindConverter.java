package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.pipeline.run.RunKind;

@Converter(autoApply = true)
class RunKindConverter extends EnumValueConverter<RunKind> {

	RunKindConverter() {
		super(RunKind.class, RunKind::value);
	}

}
