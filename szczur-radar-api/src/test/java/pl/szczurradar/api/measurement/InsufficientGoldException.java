package pl.szczurradar.api.measurement;

/**
 * The gold set gives a metric nothing to measure (an empty denominator on the gold side). This is a
 * measurement error, never a passed threshold.
 */
public class InsufficientGoldException extends RuntimeException {

	public InsufficientGoldException(String message) {
		super(message);
	}

}
