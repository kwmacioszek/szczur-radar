package pl.szczurradar.api.llm.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import pl.szczurradar.api.llm.CallFailure;
import pl.szczurradar.api.llm.LlmCallException;
import pl.szczurradar.api.llm.LlmConfigurationException;
import pl.szczurradar.api.llm.LlmUnavailableException;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.llm.transport.TransportException.Kind;

/**
 * The retry loop on scripted attempts: counts, waits, classes (unavailable, refused, configuration) and
 * the flex fallback.
 */
class TransportRetryTest {

	private final List<Duration> sleeps = new ArrayList<>();

	private final List<ServiceTier> tiers = new ArrayList<>();

	private final TransportRetry retry = new TransportRetry(sleeps::add);

	@Test
	void firstSuccessIsOneAttempt() {
		TransportRetry.Outcome<String> outcome = retry.run(ServiceTier.DEFAULT, script("ok"));
		assertThat(outcome).isEqualTo(new TransportRetry.Outcome<>("ok", 1, ServiceTier.DEFAULT, false));
		assertThat(sleeps).isEmpty();
	}

	@Test
	void rateLimitThenSuccessIsTwoAttempts() {
		TransportRetry.Outcome<String> outcome = retry.run(ServiceTier.DEFAULT, script(failure(Kind.RATE_LIMITED), "ok"));
		assertThat(outcome.attempts()).isEqualTo(2);
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
	}

	@Test
	void threeServerErrorsAreAProviderErrorAfterThreeAttempts() {
		assertThatExceptionOfType(LlmUnavailableException.class)
			.isThrownBy(() -> retry.run(ServiceTier.DEFAULT, script(failure(Kind.SERVER_ERROR),
					failure(Kind.SERVER_ERROR), failure(Kind.SERVER_ERROR), "never")))
			.satisfies(ex -> {
				assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
				assertThat(ex.attempts()).isEqualTo(3);
			});
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

	@Test
	void lastAttemptTimingOutIsATimeout() {
		assertThatExceptionOfType(LlmUnavailableException.class)
			.isThrownBy(() -> retry.run(ServiceTier.DEFAULT,
					script(failure(Kind.CONNECTION), failure(Kind.SERVER_ERROR), failure(Kind.TIMEOUT))))
			.satisfies(ex -> {
				assertThat(ex.failure()).isEqualTo(CallFailure.TIMEOUT);
				assertThat(ex.attempts()).isEqualTo(3);
			});
	}

	@Test
	void rejectedFailsAtOnceWithoutWaiting() {
		assertThatExceptionOfType(LlmCallException.class)
			.isThrownBy(() -> retry.run(ServiceTier.DEFAULT, script(failure(Kind.REJECTED), "never")))
			.satisfies(ex -> {
				// the provider refused this request: not an outage
				assertThat(ex).isNotInstanceOf(LlmUnavailableException.class)
					.isNotInstanceOf(LlmConfigurationException.class);
				assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
				assertThat(ex.attempts()).isEqualTo(1);
			});
		assertThat(sleeps).isEmpty();
	}

	@Test
	void rejectedAfterRetryableFailuresIsStillNotUnavailable() {
		assertThatExceptionOfType(LlmCallException.class)
			.isThrownBy(() -> retry.run(ServiceTier.DEFAULT,
					script(failure(Kind.SERVER_ERROR), failure(Kind.REJECTED), "never")))
			.satisfies(ex -> {
				assertThat(ex).isNotInstanceOf(LlmUnavailableException.class);
				assertThat(ex.attempts()).isEqualTo(2);
			});
	}

	@Test
	void anInterruptionBetweenAttemptsIsUnavailable() {
		TransportRetry interrupted = new TransportRetry(duration -> {
			throw new InterruptedException("shutdown");
		});
		try {
			assertThatExceptionOfType(LlmUnavailableException.class)
				.isThrownBy(() -> interrupted.run(ServiceTier.DEFAULT, script(failure(Kind.SERVER_ERROR), "never")))
				.satisfies(ex -> {
					assertThat(ex.failure()).isEqualTo(CallFailure.PROVIDER_ERROR);
					assertThat(ex.attempts()).isEqualTo(1);
					assertThat(ex).hasMessageContaining("interrupted").hasCauseInstanceOf(InterruptedException.class);
				});
			// the interrupt is kept for the caller
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		}
		finally {
			Thread.interrupted();
		}
	}

	@Test
	void anExceptionThatIsNotATransportFailurePassesThroughWithoutARetry() {
		IllegalStateException bug = new IllegalStateException("a bug in the client");
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> retry.run(ServiceTier.FLEX, tier -> {
			tiers.add(tier);
			throw bug;
		})).isSameAs(bug);
		assertThat(tiers).containsExactly(ServiceTier.FLEX);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void aRefusedConfigurationFailsAtOnceAsItsOwnException() {
		assertThatExceptionOfType(LlmConfigurationException.class)
			.isThrownBy(() -> retry.run(ServiceTier.DEFAULT, script(failure(Kind.CONFIGURATION), "never")))
			.satisfies(ex -> assertThat(ex.attempts()).isEqualTo(1));
		assertThat(sleeps).isEmpty();
	}

	@Test
	void keyCreditAndModelStatusesAreConfigurationOthersAreRejected() {
		assertThat(List.of(401, 402, 404)).allSatisfy(status -> assertThat(TransportException.kindOf(status))
			.isEqualTo(Kind.CONFIGURATION));
		// 403 may refuse one request's content (e.g. OpenRouter moderation): not the configuration.
		assertThat(List.of(400, 403, 422)).allSatisfy(status -> assertThat(TransportException.kindOf(status))
			.isEqualTo(Kind.REJECTED));
	}

	@Test
	void retryAfterIsHonouredAndCappedAt60Seconds() {
		retry.run(ServiceTier.DEFAULT, script(failure(Kind.RATE_LIMITED, Duration.ofSeconds(7)),
				failure(Kind.RATE_LIMITED, Duration.ofSeconds(600)), "ok"));
		assertThat(sleeps).containsExactly(Duration.ofSeconds(7), Duration.ofSeconds(60));
	}

	@Test
	void backoffIsCappedAt30Seconds() {
		assertThat(TransportRetry.delay(failure(Kind.SERVER_ERROR), 6)).isEqualTo(Duration.ofSeconds(30));
		assertThat(TransportRetry.delay(failure(Kind.SERVER_ERROR), 3)).isEqualTo(Duration.ofSeconds(4));
	}

	@Test
	void threeFlexRefusalsFallBackToTheDefaultTier() {
		TransportRetry.Outcome<String> outcome = retry.run(ServiceTier.FLEX, script(failure(Kind.RATE_LIMITED),
				failure(Kind.RATE_LIMITED), failure(Kind.RATE_LIMITED), "ok"));
		assertThat(outcome).isEqualTo(new TransportRetry.Outcome<>("ok", 4, ServiceTier.DEFAULT, true));
		assertThat(tiers).containsExactly(ServiceTier.FLEX, ServiceTier.FLEX, ServiceTier.FLEX, ServiceTier.DEFAULT);
	}

	@Test
	void fallbackHasThreeAttemptsOfItsOwn() {
		assertThatExceptionOfType(LlmUnavailableException.class)
			.isThrownBy(() -> retry.run(ServiceTier.FLEX, script(failure(Kind.RATE_LIMITED), failure(Kind.RATE_LIMITED),
					failure(Kind.RATE_LIMITED), failure(Kind.RATE_LIMITED), failure(Kind.RATE_LIMITED),
					failure(Kind.RATE_LIMITED), "never")))
			.satisfies(ex -> {
				assertThat(ex.attempts()).isEqualTo(6);
				assertThat(ex.serviceTier()).isEqualTo(ServiceTier.DEFAULT);
				assertThat(ex.flexFallback()).isTrue();
			});
	}

	@Test
	void theLongestCallIsEveryAttemptTimingOutWithTheLongestWaitsBetweenThem() {
		// 3 × 120 s + 2 × 60 s
		assertThat(TransportRetry.maxCallDuration(Duration.ofSeconds(120), ServiceTier.DEFAULT))
			.isEqualTo(Duration.ofMinutes(8));
		// 3 × 300 s + 2 × 60 s
		assertThat(TransportRetry.maxCallDuration(Duration.ofSeconds(300), ServiceTier.DEFAULT))
			.isEqualTo(Duration.ofMinutes(17));
		// Flex falls back to the default tier after a failed flex tier: twice that (production).
		assertThat(TransportRetry.maxCallDuration(Duration.ofSeconds(300), ServiceTier.FLEX))
			.isEqualTo(Duration.ofMinutes(34));
	}

	@Test
	void flexFailingWithMixedErrorsFallsBack() {
		TransportRetry.Outcome<String> outcome = retry.run(ServiceTier.FLEX, script(failure(Kind.RATE_LIMITED),
				failure(Kind.SERVER_ERROR), failure(Kind.RATE_LIMITED), "ok"));
		assertThat(outcome).isEqualTo(new TransportRetry.Outcome<>("ok", 4, ServiceTier.DEFAULT, true));
		assertThat(tiers).containsExactly(ServiceTier.FLEX, ServiceTier.FLEX, ServiceTier.FLEX, ServiceTier.DEFAULT);
	}

	@Test
	void flexTimingOutFallsBack() {
		TransportRetry.Outcome<String> outcome = retry.run(ServiceTier.FLEX,
				script(failure(Kind.TIMEOUT), failure(Kind.TIMEOUT), failure(Kind.TIMEOUT), "ok"));
		assertThat(outcome).isEqualTo(new TransportRetry.Outcome<>("ok", 4, ServiceTier.DEFAULT, true));
		assertThat(tiers).containsExactly(ServiceTier.FLEX, ServiceTier.FLEX, ServiceTier.FLEX, ServiceTier.DEFAULT);
	}

	@Test
	void bothTiersUsedUpAreUnavailableWithTheLastAttemptsFailure() {
		assertThatExceptionOfType(LlmUnavailableException.class)
			.isThrownBy(() -> retry.run(ServiceTier.FLEX, script(failure(Kind.RATE_LIMITED), failure(Kind.CONNECTION),
					failure(Kind.SERVER_ERROR), failure(Kind.SERVER_ERROR), failure(Kind.RATE_LIMITED),
					failure(Kind.TIMEOUT), "never")))
			.satisfies(ex -> {
				assertThat(ex.failure()).isEqualTo(CallFailure.TIMEOUT);
				assertThat(ex.attempts()).isEqualTo(6);
				assertThat(ex.serviceTier()).isEqualTo(ServiceTier.DEFAULT);
				assertThat(ex.flexFallback()).isTrue();
			});
	}

	@Test
	void aRejectionOnFlexDoesNotFallBack() {
		assertThatExceptionOfType(LlmCallException.class)
			.isThrownBy(() -> retry.run(ServiceTier.FLEX,
					script(failure(Kind.RATE_LIMITED), failure(Kind.REJECTED), "never")))
			.satisfies(ex -> {
				assertThat(ex).isNotInstanceOf(LlmUnavailableException.class);
				assertThat(ex.attempts()).isEqualTo(2);
				assertThat(ex.flexFallback()).isFalse();
			});
		assertThat(tiers).containsOnly(ServiceTier.FLEX);
	}

	private TransportRetry.Attempt<String> script(Object... steps) {
		Deque<Object> remaining = new ArrayDeque<>(List.of(steps));
		return tier -> {
			tiers.add(tier);
			Object step = remaining.removeFirst();
			if (step instanceof TransportException failure) {
				throw failure;
			}
			return (String) step;
		};
	}

	private static TransportException failure(Kind kind) {
		return failure(kind, null);
	}

	private static TransportException failure(Kind kind, Duration retryAfter) {
		return new TransportException(kind, kind.name(), retryAfter, null);
	}

}
