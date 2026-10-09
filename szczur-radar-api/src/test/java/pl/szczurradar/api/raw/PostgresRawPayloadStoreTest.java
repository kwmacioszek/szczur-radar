package pl.szczurradar.api.raw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.persistence.Sha256;
import pl.szczurradar.api.sources.Source;

/**
 * {@link PostgresRawPayloadStore} on an in-memory repository: what is stored (gzip, hash) and that a
 * load returns exactly the stored bytes. The table itself is covered by {@code JpaSchemaIT}.
 */
class PostgresRawPayloadStoreTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final Map<UUID, RawPayload> rows = new HashMap<>();

	private final PostgresRawPayloadStore store = new PostgresRawPayloadStore(repository());

	@Test
	void loadReturnsExactlyTheStoredBytes() {
		// Not a canonical serialization: spacing, key order and an escaped character must survive.
		byte[] json = "{ \"b\":1,\"a\" : \"za\\u017cółć\\n \" }".getBytes(StandardCharsets.UTF_8);

		RawPayloadRef ref = store.store(Source.WYKOP, json);

		assertThat(store.load(ref)).isEqualTo(json);
		RawPayload row = rows.get(ref.id());
		assertThat(row.getContentEncoding()).isEqualTo(ContentEncoding.GZIP);
		assertThat(row.getSha256()).isEqualTo(Sha256.hex(json));
	}

	@Test
	void aWykopElementKeepsEveryFieldAndValue() {
		// The element as the pipeline stores it: the answer's JsonNode written by Jackson.
		JsonNode element = MAPPER.readTree(fixture("search-entries-page.json")).get("data").get(0);
		byte[] json = MAPPER.writeValueAsBytes(element);

		JsonNode loaded = MAPPER.readTree(store.load(store.store(Source.WYKOP, json)));

		assertThat(loaded).isEqualTo(element);
		assertThat(loaded.propertyNames()).containsExactlyElementsOf(element.propertyNames());
	}

	@Test
	void thePayloadIsGzipped() {
		byte[] json = fixture("search-entries-page.json").getBytes(StandardCharsets.UTF_8);

		RawPayload row = rows.get(store.store(Source.WYKOP, json).id());

		assertThat(row.getPayload()).startsWith((byte) 0x1f, (byte) 0x8b);
		assertThat(row.getPayload().length).isLessThan(json.length / 2);
	}

	@Test
	void bytesThatNoLongerMatchTheirHashAreRefused() {
		RawPayloadRef ref = store.store(Source.WYKOP, "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
		RawPayload other = rows.get(store.store(Source.WYKOP, "{\"a\":2}".getBytes(StandardCharsets.UTF_8)).id());
		set(rows.get(ref.id()), "payload", other.getPayload());

		assertThatIllegalStateException().isThrownBy(() -> store.load(ref)).withMessageContaining("SHA-256");
	}

	@Test
	void anUnknownReferenceIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> store.load(new RawPayloadRef(UUID.randomUUID())));
	}

	private RawPayloadRepository repository() {
		RawPayloadRepository repository = mock(RawPayloadRepository.class);
		when(repository.save(any(RawPayload.class))).thenAnswer(invocation -> {
			RawPayload raw = invocation.getArgument(0);
			set(raw, "id", UUID.randomUUID());
			rows.put(raw.getId(), raw);
			return raw;
		});
		when(repository.findById(any(UUID.class)))
			.thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.<UUID>getArgument(0))));
		return repository;
	}

	/** Sets what JPA would set (the generated id) or what a damaged row would hold. */
	private static void set(RawPayload raw, String field, Object value) {
		try {
			Field declared = RawPayload.class.getDeclaredField(field);
			declared.setAccessible(true);
			declared.set(raw, value);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String fixture(String name) {
		try (InputStream in = PostgresRawPayloadStoreTest.class.getResourceAsStream("/fixtures/wykop/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
