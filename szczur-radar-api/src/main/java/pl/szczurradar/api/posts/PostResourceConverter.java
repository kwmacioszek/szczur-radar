package pl.szczurradar.api.posts;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class PostResourceConverter extends EnumValueConverter<PostResource> {

	PostResourceConverter() {
		super(PostResource.class, PostResource::value);
	}

}
