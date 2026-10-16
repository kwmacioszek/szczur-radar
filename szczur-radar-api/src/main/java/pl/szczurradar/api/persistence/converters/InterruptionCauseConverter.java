package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.pipeline.run.InterruptionCause;

@Converter(autoApply = true)
class InterruptionCauseConverter extends EnumValueConverter<InterruptionCause> {

	InterruptionCauseConverter() {
		super(InterruptionCause.class, InterruptionCause::value);
	}

}
