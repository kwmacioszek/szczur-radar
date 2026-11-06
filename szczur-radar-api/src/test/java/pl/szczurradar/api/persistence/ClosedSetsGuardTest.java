package pl.szczurradar.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import jakarta.persistence.AttributeConverter;
import org.junit.jupiter.api.Test;

class ClosedSetsGuardTest {

	private enum Colour {

		DARK_RED("dark-red"), GREEN("green");

		private final String value;

		Colour(String value) {
			this.value = value;
		}

		String value() {
			return value;
		}

	}

	private static final Set<String> CHECK = Set.of("dark-red", "green");

	private static final class GoodConverter extends EnumValueConverter<Colour> {

		GoodConverter() {
			super(Colour.class, Colour::value);
		}

	}

	private static final class NameConverter extends EnumValueConverter<Colour> {

		NameConverter() {
			super(Colour.class, Colour::name);
		}

	}

	@SuppressWarnings("unchecked")
	private static Map<Class<?>, AttributeConverter<Object, String>> converters(AttributeConverter<?, String> converter) {
		return Map.of(Colour.class, (AttributeConverter<Object, String>) converter);
	}

	@Test
	void aConverterWritingTheConstantNameInsteadOfTheValueIsReported() {
		assertThat(ClosedSetsGuard.problems("shades.colour", Colour.class, converters(new NameConverter()), CHECK))
			.singleElement()
			.satisfies(problem -> assertThat(problem).contains("shades.colour", "DARK_RED", "dark-red"));
	}

	@Test
	void anEnumWithoutAConverterIsReported() {
		assertThat(ClosedSetsGuard.problems("shades.colour", Colour.class, Map.of(), CHECK)).singleElement()
			.satisfies(problem -> assertThat(problem).contains("shades.colour", "no @Converter"));
	}

	@Test
	void aConverterWritingTheValuesOfTheCheckIsNotReported() {
		assertThat(ClosedSetsGuard.problems("shades.colour", Colour.class, converters(new GoodConverter()), CHECK))
			.isEmpty();
	}

	@Test
	void theRealConvertersAreFoundByTheEnumTheyConvert() {
		assertThat(ClosedSetsGuard.convertersByEnum()).hasSize(21)
			.containsKeys(pl.szczurradar.api.pipeline.run.RunKind.class, pl.szczurradar.api.user.Role.class);
	}

}
