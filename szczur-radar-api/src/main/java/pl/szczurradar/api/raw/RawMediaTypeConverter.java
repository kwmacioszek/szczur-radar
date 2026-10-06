package pl.szczurradar.api.raw;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class RawMediaTypeConverter extends EnumValueConverter<RawMediaType> {

	RawMediaTypeConverter() {
		super(RawMediaType.class, RawMediaType::value);
	}

}
