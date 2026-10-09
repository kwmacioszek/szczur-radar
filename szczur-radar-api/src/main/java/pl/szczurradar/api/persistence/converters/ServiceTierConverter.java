package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.config.LlmProviderProperties.ServiceTier;
import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class ServiceTierConverter extends EnumValueConverter<ServiceTier> {

	ServiceTierConverter() {
		super(ServiceTier.class, ServiceTier::id);
	}

}
