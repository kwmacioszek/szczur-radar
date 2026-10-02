package pl.szczurradar.api.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link Role} as its lower-case value ({@code ck_users_role}), not the constant name. */
@Converter(autoApply = true)
class RoleConverter implements AttributeConverter<Role, String> {

	@Override
	public String convertToDatabaseColumn(Role role) {
		return role == null ? null : role.value();
	}

	@Override
	public Role convertToEntityAttribute(String value) {
		return value == null ? null : Role.fromValue(value);
	}

}
