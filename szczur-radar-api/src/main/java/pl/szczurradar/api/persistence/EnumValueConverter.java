package pl.szczurradar.api.persistence;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import org.jspecify.annotations.Nullable;

/**
 * Stores an enum as its lower-case value (the column's {@code CHECK} set), not the constant name. A
 * concrete converter is one line: {@code @Converter(autoApply = true) class XConverter extends
 * EnumValueConverter<X> { XConverter() { super(X.class, X::value); } }}.
 */
public abstract class EnumValueConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

	private final Class<E> type;

	private final Function<E, String> value;

	private final Map<String, E> byValue;

	protected EnumValueConverter(Class<E> type, Function<E, String> value) {
		this.type = type;
		this.value = value;
		this.byValue = Arrays.stream(type.getEnumConstants()).collect(Collectors.toUnmodifiableMap(value, constant -> constant));
	}

	@Override
	public @Nullable String convertToDatabaseColumn(@Nullable E constant) {
		return constant == null ? null : value.apply(constant);
	}

	@Override
	public @Nullable E convertToEntityAttribute(@Nullable String stored) {
		if (stored == null) {
			return null;
		}
		E constant = byValue.get(stored);
		if (constant == null) {
			throw new IllegalArgumentException("unknown " + type.getSimpleName() + " value: " + stored);
		}
		return constant;
	}

}
