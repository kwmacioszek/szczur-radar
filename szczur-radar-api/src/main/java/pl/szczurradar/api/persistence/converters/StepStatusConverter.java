package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.pipeline.run.StepStatus;

@Converter(autoApply = true)
class StepStatusConverter extends EnumValueConverter<StepStatus> {

	StepStatusConverter() {
		super(StepStatus.class, StepStatus::value);
	}

}
