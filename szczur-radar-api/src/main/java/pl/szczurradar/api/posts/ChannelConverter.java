package pl.szczurradar.api.posts;

import jakarta.persistence.Converter;

import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class ChannelConverter extends EnumValueConverter<Channel> {

	ChannelConverter() {
		super(Channel.class, Channel::value);
	}

}
