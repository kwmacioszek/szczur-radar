package pl.szczurradar.api.sources;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class SourceConverter extends EnumValueConverter<Source> {

	SourceConverter() {
		super(Source.class, Source::value);
	}

}
