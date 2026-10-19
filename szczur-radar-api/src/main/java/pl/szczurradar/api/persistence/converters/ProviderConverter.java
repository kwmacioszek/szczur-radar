package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class ProviderConverter extends EnumValueConverter<Provider> {

	ProviderConverter() {
		super(Provider.class, Provider::id);
	}

}
