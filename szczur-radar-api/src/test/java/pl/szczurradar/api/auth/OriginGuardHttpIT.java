package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;

/** Real HTTP preserves percent escapes until Tomcat parses the path, unlike MockMvc. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
class OriginGuardHttpIT {

	@LocalServerPort
	int port;

	@ParameterizedTest
	@ValueSource(strings = { "/api/auth/logout", "/%61pi/auth/logout", "/a%70i/auth/logout", "/ap%69/auth/logout" })
	void foreignOriginCannotClearTheSessionCookie(String path) throws Exception {
		HttpResponse<String> response = post(path, "https://evil.example");

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).isEqualTo("{\"detail\":\"cross_origin\"}");
		assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/auth/logout", "/%61pi/auth/logout", "/a%70i/auth/logout", "/ap%69/auth/logout" })
	void sameOriginCanStillLogOut(String path) throws Exception {
		HttpResponse<String> response = post(path, origin());

		assertThat(response.statusCode()).isEqualTo(204);
		assertThat(response.headers().allValues("Set-Cookie")).singleElement()
				.asString().contains("sr_session=;", "Max-Age=0", "Path=/api");
	}

	private HttpResponse<String> post(String path, String origin) throws Exception {
		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
			HttpRequest request = HttpRequest.newBuilder(URI.create(origin() + path))
					.timeout(Duration.ofSeconds(10))
					.header("Origin", origin)
					.POST(HttpRequest.BodyPublishers.noBody()).build();
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

	private String origin() {
		return "http://127.0.0.1:" + port;
	}

}
