package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The process exit codes are a contract with the operator, the README and Railway's pre-deploy. */
class ExitCodeTest {

	@Test
	void eachOutcomeHasItsProcessCode() {
		assertThat(ExitCode.OK.code()).isZero();
		assertThat(ExitCode.REFUSED.code()).isOne();
		assertThat(ExitCode.FAILED.code()).isOne();
		assertThat(ExitCode.USAGE.code()).isEqualTo(2);
	}

}
