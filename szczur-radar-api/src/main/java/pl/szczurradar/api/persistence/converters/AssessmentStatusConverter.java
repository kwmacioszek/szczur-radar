package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class AssessmentStatusConverter extends EnumValueConverter<AssessmentResult.Status> {

	AssessmentStatusConverter() {
		super(AssessmentResult.Status.class, AssessmentResult.Status::value);
	}

}
