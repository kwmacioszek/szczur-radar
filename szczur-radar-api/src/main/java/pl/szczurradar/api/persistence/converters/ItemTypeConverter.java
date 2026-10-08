package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.agents.assessor.contract.ItemType;
import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class ItemTypeConverter extends EnumValueConverter<ItemType> {

	ItemTypeConverter() {
		super(ItemType.class, ItemType::value);
	}

}
