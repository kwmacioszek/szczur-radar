package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

	@Test
	void prefersXRealIp() {
		MockHttpServletRequest request = request();
		request.addHeader("X-Real-IP", " 203.0.113.7 ");
		request.addHeader("X-Forwarded-For", "10.1.1.1");

		assertThat(ClientIp.of(request)).isEqualTo("203.0.113.7");
	}

	@Test
	void fallsBackToThePeerAddress() {
		MockHttpServletRequest request = request();
		request.addHeader("X-Forwarded-For", "10.1.1.1");

		assertThat(ClientIp.of(request)).isEqualTo("172.16.0.9");
	}

	@Test
	void blankXRealIpFallsBackToThePeerAddress() {
		MockHttpServletRequest request = request();
		request.addHeader("X-Real-IP", "  ");

		assertThat(ClientIp.of(request)).isEqualTo("172.16.0.9");
	}

	private static MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
		request.setRemoteAddr("172.16.0.9");
		return request;
	}

}
