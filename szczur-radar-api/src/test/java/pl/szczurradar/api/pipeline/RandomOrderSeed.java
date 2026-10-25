package pl.szczurradar.api.pipeline;

import org.junit.jupiter.api.MethodOrderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The seed of {@link MethodOrderer.Random} for the engine's ITs, logged at INFO when a class starts:
 * JUnit logs it only at CONFIG, which Failsafe does not show. Failsafe sets it from {@code it.seed}
 * ({@code pom.xml}, a number of the build's time by default), so {@code ./mvnw -B verify -Dit.seed=<n>}
 * repeats the order of a red run.
 */
final class RandomOrderSeed {

	/** JUnit's configuration parameter of the seed (also read from a system property). */
	static final String PROPERTY = "junit.jupiter.execution.order.random.seed";

	private static final Logger log = LoggerFactory.getLogger(RandomOrderSeed.class);

	private RandomOrderSeed() {
	}

	static void log(Class<?> testClass) {
		String seed = System.getProperty(PROPERTY);
		if (seed == null || seed.isBlank()) {
			log.info("{} runs its methods in a random order with JUnit's own seed (logged only at CONFIG);"
					+ " set -D{}=<n> to repeat an order", testClass.getSimpleName(), PROPERTY);
			return;
		}
		log.info("{} runs its methods in a random order, seed {}: repeat it with ./mvnw -B verify -Dit.seed={}",
				testClass.getSimpleName(), seed, seed);
	}

}
