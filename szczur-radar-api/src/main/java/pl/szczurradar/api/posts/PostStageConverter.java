package pl.szczurradar.api.posts;

import jakarta.persistence.Converter;

import pl.szczurradar.api.persistence.EnumValueConverter;

@Converter(autoApply = true)
class PostStageConverter extends EnumValueConverter<PostStage> {

	PostStageConverter() {
		super(PostStage.class, PostStage::value);
	}

}
