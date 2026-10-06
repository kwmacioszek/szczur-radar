package pl.szczurradar.api.raw;

import pl.szczurradar.api.sources.Source;

/**
 * Storage of raw source payloads, apart from the application data (context/foundation/tech-stack-api.md,
 * "Raw source payload storage boundary"; lessons.md, RAW vs. operational data). The backend can change
 * (e.g. to a Railway Bucket) without changing the domain model or the API: callers keep only the
 * {@link RawPayloadRef}.
 *
 * <p>Lossless: {@link #load} returns exactly the bytes given to {@link #store}. In S-02 a Wykop
 * payload is the element as Jackson wrote it, with every field and value of the source's answer.
 */
public interface RawPayloadStore {

	/** Stores {@code json} (UTF-8 JSON bytes) of {@code source}; joins the caller's transaction. */
	RawPayloadRef store(Source source, byte[] json);

	/**
	 * The bytes stored under {@code ref}, exactly.
	 *
	 * @throws IllegalArgumentException when nothing is stored under {@code ref}
	 * @throws IllegalStateException when the stored bytes no longer match their hash
	 */
	byte[] load(RawPayloadRef ref);

}
