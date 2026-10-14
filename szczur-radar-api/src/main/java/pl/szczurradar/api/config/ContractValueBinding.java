package pl.szczurradar.api.config;

import java.beans.PropertyEditorSupport;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import pl.szczurradar.api.agents.assessor.contract.Direction;
import pl.szczurradar.api.agents.assessor.contract.TargetKind;

/**
 * Contract enums in request parameters take their contract value ({@code macro_topic},
 * {@code bullish}) — the same value the SPA reads in {@code assessment.output} — and nothing else: the
 * constant name ({@code BULLISH}) is unknown like any other value, which Spring answers with
 * {@code MethodArgumentTypeMismatchException} (400 {@code invalid_request}, {@link ApiExceptionHandler}).
 *
 * <p>A property editor, not a {@code Converter} in {@code addFormatters}: when a converter to an enum
 * fails, Spring's binder retries with {@code Enum.valueOf} and would accept {@code BULLISH}; a custom
 * editor is used instead of the conversion service and has no such fallback.
 */
@ControllerAdvice
class ContractValueBinding {

	@InitBinder
	void contractValues(WebDataBinder binder) {
		binder.registerCustomEditor(TargetKind.class, new ValueEditor<>(TargetKind.class, TargetKind::value));
		binder.registerCustomEditor(Direction.class, new ValueEditor<>(Direction.class, Direction::value));
	}

	/**
	 * An enum from its value, the web counterpart of {@code persistence/EnumValueConverter}. An empty
	 * parameter is no value ({@code null}), as with Spring's own enum binding.
	 */
	static final class ValueEditor<E extends Enum<E>> extends PropertyEditorSupport {

		private final Class<E> type;

		private final Map<String, E> byValue;

		ValueEditor(Class<E> type, Function<E, String> value) {
			this.type = type;
			this.byValue = Arrays.stream(type.getEnumConstants())
				.collect(Collectors.toUnmodifiableMap(value, constant -> constant));
		}

		@Override
		public void setAsText(String text) {
			if (text.isEmpty()) {
				setValue(null);
				return;
			}
			E constant = byValue.get(text);
			if (constant == null) {
				throw new IllegalArgumentException("unknown " + type.getSimpleName() + " value: " + text);
			}
			setValue(constant);
		}

	}

}
