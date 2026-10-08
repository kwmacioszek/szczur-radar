package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.pipeline.run.RunStep;

@Converter(autoApply = true)
class RunStepConverter extends EnumValueConverter<RunStep> {

	RunStepConverter() {
		super(RunStep.class, RunStep::value);
	}

}
