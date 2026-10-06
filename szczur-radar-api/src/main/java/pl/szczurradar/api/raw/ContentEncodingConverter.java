package pl.szczurradar.api.raw;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class ContentEncodingConverter extends EnumValueConverter<ContentEncoding> {

	ContentEncodingConverter() {
		super(ContentEncoding.class, ContentEncoding::value);
	}

}
