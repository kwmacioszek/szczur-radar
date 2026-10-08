package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.sources.Source;

@Converter(autoApply = true)
class SourceConverter extends EnumValueConverter<Source> {

	SourceConverter() {
		super(Source.class, Source::value);
	}

}
