package pl.szczurradar.api.pipeline;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class RunStatusConverter extends EnumValueConverter<RunStatus> {

	RunStatusConverter() {
		super(RunStatus.class, RunStatus::value);
	}

}
