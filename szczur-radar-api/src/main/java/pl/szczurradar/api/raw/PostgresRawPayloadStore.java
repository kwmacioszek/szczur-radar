package pl.szczurradar.api.raw;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.springframework.stereotype.Component;

import pl.szczurradar.api.persistence.Sha256;
import pl.szczurradar.api.sources.Source;

/**
 * {@link RawPayloadStore} in the {@code raw_payloads} table: gzip in the application (TOAST would not
 * compress a single element, which is mostly under its ~2 KB threshold) and a SHA-256 of the
 * uncompressed bytes, checked on every load.
 */
@Component
class PostgresRawPayloadStore implements RawPayloadStore {

	private final RawPayloadRepository repository;

	PostgresRawPayloadStore(RawPayloadRepository repository) {
		this.repository = repository;
	}

	@Override
	public RawPayloadRef store(Source source, byte[] json) {
		RawPayload saved = repository
			.save(new RawPayload(source, ContentEncoding.GZIP, RawMediaType.JSON, gzip(json), Sha256.hex(json)));
		return new RawPayloadRef(saved.getId());
	}

	@Override
	public byte[] load(RawPayloadRef ref) {
		RawPayload raw = repository.findById(ref.id())
			.orElseThrow(() -> new IllegalArgumentException("no raw payload " + ref.id()));
		byte[] json = switch (raw.getContentEncoding()) {
			case GZIP -> gunzip(raw.getPayload());
		};
		if (!Sha256.hex(json).equals(raw.getSha256())) {
			throw new IllegalStateException("raw payload " + ref.id() + " does not match its SHA-256");
		}
		return json;
	}

	private static byte[] gzip(byte[] bytes) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length / 2 + 32);
		try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
			gzip.write(bytes);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toByteArray();
	}

	private static byte[] gunzip(byte[] bytes) {
		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
			return gzip.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
