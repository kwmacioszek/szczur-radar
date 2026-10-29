package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.pipeline.run.SourceFetchStatus;

@Converter(autoApply = true)
class SourceFetchStatusConverter extends EnumValueConverter<SourceFetchStatus> {

	SourceFetchStatusConverter() {
		super(SourceFetchStatus.class, SourceFetchStatus::value);
	}

}
