package pl.szczurradar.api.pipeline;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class StepStatusConverter extends EnumValueConverter<StepStatus> {

	StepStatusConverter() {
		super(StepStatus.class, StepStatus::value);
	}

}
