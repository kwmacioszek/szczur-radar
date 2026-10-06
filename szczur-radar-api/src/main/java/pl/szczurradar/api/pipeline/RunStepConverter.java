package pl.szczurradar.api.pipeline;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class RunStepConverter extends EnumValueConverter<RunStep> {

	RunStepConverter() {
		super(RunStep.class, RunStep::value);
	}

}
