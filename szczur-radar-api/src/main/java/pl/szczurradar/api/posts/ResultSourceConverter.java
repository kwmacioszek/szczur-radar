package pl.szczurradar.api.posts;

import jakarta.persistence.Converter;

import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class ResultSourceConverter extends EnumValueConverter<Provenance.Source> {

	ResultSourceConverter() {
		super(Provenance.Source.class, Provenance.Source::value);
	}

}
