package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.raw.RawMediaType;

@Converter(autoApply = true)
class RawMediaTypeConverter extends EnumValueConverter<RawMediaType> {

	RawMediaTypeConverter() {
		super(RawMediaType.class, RawMediaType::value);
	}

}
