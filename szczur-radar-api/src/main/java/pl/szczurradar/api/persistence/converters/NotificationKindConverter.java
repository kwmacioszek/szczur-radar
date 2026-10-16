package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.notifications.NotificationKind;
import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class NotificationKindConverter extends EnumValueConverter<NotificationKind> {

	NotificationKindConverter() {
		super(NotificationKind.class, NotificationKind::value);
	}

}
