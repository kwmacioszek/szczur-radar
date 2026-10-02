package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Origin check on state-changing API requests, ported from the Python tests/test_security.py. */
class OriginGuardFilterTest {

	private static final String HOST = "testserver";

	private final OriginGuardFilter filter = new OriginGuardFilter();

	@Test
	void foreignOriginIsRejected() throws Exception {
		MockHttpServletResponse response = run("POST", "/api/auth/logout", "https://evil.example");

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).isEqualTo("application/json");
		assertThat(response.getContentAsString()).isEqualTo("{\"detail\":\"cross_origin\"}");
	}

	@Test
	void sameOriginPasses() throws Exception {
		assertPassed(run("POST", "/api/auth/logout", "https://testserver"));
	}

	@Test
	void hostComparisonIgnoresCase() throws Exception {
		assertPassed(run("POST", "/api/auth/logout", "https://TestServer"));
	}

	@Test
	void sameHostOnAnotherPortIsRejected() throws Exception {
		assertThat(run("POST", "/api/auth/logout", "https://testserver:8443").getStatus()).isEqualTo(403);
	}

	@Test
	void nullOriginIsRejected() throws Exception {
		assertThat(run("POST", "/api/auth/logout", "null").getStatus()).isEqualTo(403);
	}

	@Test
	void missingOriginPasses() throws Exception {
		assertPassed(run("POST", "/api/auth/logout", null));
	}

	@Test
	void everyUnsafeMethodIsChecked() throws Exception {
		for (String method : new String[] { "POST", "PUT", "PATCH", "DELETE" }) {
			assertThat(run(method, "/api/anything", "https://evil.example").getStatus()).as(method).isEqualTo(403);
		}
	}

	@Test
	void safeMethodsAreNotChecked() throws Exception {
		assertPassed(run("GET", "/api/health", "https://evil.example"));
	}

	@Test
	void pathsOutsideTheApiAreNotChecked() throws Exception {
		assertPassed(run("POST", "/healthz", "https://evil.example"));
	}

	private MockHttpServletResponse run(String method, String path, String origin) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.addHeader("Host", HOST);
		if (origin != null) {
			request.addHeader("Origin", origin);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, response, chain);
		if (chain.getRequest() != null) {
			response.setStatus(204); // what the guarded endpoint would answer
		}
		return response;
	}

	private static void assertPassed(MockHttpServletResponse response) {
		assertThat(response.getStatus()).isEqualTo(204);
	}

}
