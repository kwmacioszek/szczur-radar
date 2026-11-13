package pl.szczurradar.api.contract;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * Walking the response types of {@link ContractRegistry}: the one place that knows which shapes a contract
 * type may have ({@code List<X>}, a record, an enum or a scalar), shared by {@code ContractVariantsTest} and
 * {@code SpaRulesTest} so that a new shape of type turns both red together, not one of them.
 */
final class ContractTypes {

	private ContractTypes() {
	}

	/** {@code List<X>} → {@code X}; anything else → {@code null}. */
	static @Nullable Type listElement(Type type) {
		if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == List.class) {
			return parameterized.getActualTypeArguments()[0];
		}
		return null;
	}

	/** A value without inner structure: what the walk does not descend into. */
	static boolean leaf(Type type) {
		if (!(type instanceof Class<?> scalar)) {
			return false;
		}
		return scalar.isEnum() || scalar.isPrimitive() || scalar == String.class || scalar == UUID.class
				|| scalar == Instant.class || scalar == LocalDate.class || scalar == Integer.class || scalar == Long.class
				|| scalar == Boolean.class;
	}

	/**
	 * Visits the records reachable from {@code type} (through {@code List<X>} and the components of records),
	 * each one {@code firstVisit} lets through, {@code onRecord} before its components.
	 */
	static void reach(Type type, Predicate<Class<?>> firstVisit, Consumer<Class<?>> onRecord) {
		Type element = listElement(type);
		if (element != null) {
			reach(element, firstVisit, onRecord);
			return;
		}
		if (!(type instanceof Class<?> record) || !record.isRecord() || !firstVisit.test(record)) {
			return;
		}
		onRecord.accept(record);
		for (RecordComponent component : record.getRecordComponents()) {
			reach(component.getGenericType(), firstVisit, onRecord);
		}
	}

	/**
	 * The enums reachable from {@code types}, each with the first {@code <Record>.<component>} that holds it.
	 * Keyed by class, as two enums may share a simple name. A component of any other shape than {@code List<X>},
	 * a record or a {@link #leaf leaf} ({@code Optional}, {@code Set}, {@code Map}, an array, an interface) throws,
	 * so an enum held that way is never skipped in silence.
	 */
	static Map<Class<?>, String> reachableEnums(Iterable<? extends Type> types) {
		Map<Class<?>, String> enums = new LinkedHashMap<>();
		Set<Class<?>> visited = new LinkedHashSet<>();
		for (Type type : types) {
			reach(type, visited::add, record -> {
				for (RecordComponent component : record.getRecordComponents()) {
					String at = record.getSimpleName() + "." + component.getName();
					Type held = component.getGenericType();
					for (Type element = listElement(held); element != null; element = listElement(held)) {
						held = element;
					}
					if (!leaf(held) && !(held instanceof Class<?> nested && nested.isRecord())) {
						throw new IllegalStateException(at + " is a " + held.getTypeName()
								+ ", a shape the contract walk does not know: List<X>, a record or a leaf only");
					}
					if (held instanceof Class<?> heldType && heldType.isEnum()) {
						enums.putIfAbsent(heldType, at);
					}
				}
			});
		}
		return enums;
	}

}
