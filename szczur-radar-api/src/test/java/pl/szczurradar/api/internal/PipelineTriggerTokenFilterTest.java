package pl.szczurradar.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import pl.szczurradar.api.user.SystemAccount;

/** The trigger token authenticates the scheduler with one authority; anything else authenticates nobody. */
class PipelineTriggerTokenFilterTest {

	private static final String TOKEN = "9f2c4e6a8b0d1f3e5a7c9e1b3d5f7a9c0e2b4d6f8a1c3e5b7d9f0a2c4e6b8d0f";

	private final PipelineTriggerTokenFilter filter = new PipelineTriggerTokenFilter(
			PipelineTriggerToken.from(new MockEnvironment().withProperty(PipelineTriggerToken.NAME, TOKEN)));

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Bearer " + TOKEN, "bearer " + TOKEN, "BEARER " + TOKEN })
	void theTokenAuthenticatesTheSchedulerWithTheTriggerAuthorityOnly(String authorization) throws Exception {
		Authentication authentication = authenticationSeenByTheChain(filter, authorization);

		assertThat(authentication).isNotNull();
		assertThat(authentication.isAuthenticated()).isTrue();
		assertThat(authentication.getName()).isEqualTo(SystemAccount.SCHEDULER.id().toString());
		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactly(PipelineTriggerTokenFilter.AUTHORITY);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "Bearer", "Bearer ", "Bearer wrong", "Bearer " + TOKEN + "0", "Basic " + TOKEN, TOKEN,
			"Bearer" + TOKEN })
	void anythingElseAuthenticatesNobody(@Nullable String authorization) throws Exception {
		assertThat(authenticationSeenByTheChain(filter, authorization)).isNull();
	}

	@Test
	void withoutAConfiguredTokenNothingAuthenticates() throws Exception {
		PipelineTriggerTokenFilter disabled = new PipelineTriggerTokenFilter(
				PipelineTriggerToken.from(new MockEnvironment()));

		assertThat(authenticationSeenByTheChain(disabled, "Bearer " + TOKEN)).isNull();
		assertThat(authenticationSeenByTheChain(disabled, "Bearer ")).isNull();
	}

	private static @Nullable Authentication authenticationSeenByTheChain(PipelineTriggerTokenFilter filter,
			@Nullable String authorization) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/pipeline-runs/daily");
		if (authorization != null) {
			request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
		}
		AtomicReference<Authentication> seen = new AtomicReference<>();
		filter.doFilter(request, new MockHttpServletResponse(),
				(req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));
		return seen.get();
	}

}
