package pl.szczurradar.api.raw;

import java.util.Objects;
import java.util.UUID;

/** A stable reference to a stored raw payload; what the application model keeps instead of the payload. */
public record RawPayloadRef(UUID id) {

	public RawPayloadRef {
		Objects.requireNonNull(id, "id");
	}

}
