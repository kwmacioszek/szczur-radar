package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.text.Language;

@Converter(autoApply = true)
class LanguageConverter extends EnumValueConverter<Language> {

	LanguageConverter() {
		super(Language.class, Language::value);
	}

}
