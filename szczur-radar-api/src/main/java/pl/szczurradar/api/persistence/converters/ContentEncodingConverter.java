package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.raw.ContentEncoding;

@Converter(autoApply = true)
class ContentEncodingConverter extends EnumValueConverter<ContentEncoding> {

	ContentEncodingConverter() {
		super(ContentEncoding.class, ContentEncoding::value);
	}

}
