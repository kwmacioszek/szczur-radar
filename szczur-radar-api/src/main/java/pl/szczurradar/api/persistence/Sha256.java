package pl.szczurradar.api.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The hex SHA-256 stored in the hash columns ({@code CHECK (… ~ '^[0-9a-f]{64}$')}). */
public final class Sha256 {

	private Sha256() {
	}

	public static String hex(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is part of every Java platform", ex);
		}
	}

	/** Of the UTF-8 bytes of {@code text}. */
	public static String hex(String text) {
		return hex(text.getBytes(StandardCharsets.UTF_8));
	}

}
