package pl.szczurradar.api.pipeline;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class RunKindConverter extends EnumValueConverter<RunKind> {

	RunKindConverter() {
		super(RunKind.class, RunKind::value);
	}

}
