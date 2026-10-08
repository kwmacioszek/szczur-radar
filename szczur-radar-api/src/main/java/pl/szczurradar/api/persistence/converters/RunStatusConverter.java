package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.pipeline.run.RunStatus;

@Converter(autoApply = true)
class RunStatusConverter extends EnumValueConverter<RunStatus> {

	RunStatusConverter() {
		super(RunStatus.class, RunStatus::value);
	}

}
