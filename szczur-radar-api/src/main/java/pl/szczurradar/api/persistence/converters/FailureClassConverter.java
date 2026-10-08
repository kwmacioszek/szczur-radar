package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class FailureClassConverter extends EnumValueConverter<FailureClass> {

	FailureClassConverter() {
		super(FailureClass.class, FailureClass::value);
	}

}
