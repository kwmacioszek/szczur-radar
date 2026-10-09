package pl.szczurradar.api.persistence.converters;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;
import pl.szczurradar.api.posts.PostStage;

@Converter(autoApply = true)
class PostStageConverter extends EnumValueConverter<PostStage> {

	PostStageConverter() {
		super(PostStage.class, PostStage::value);
	}

}
