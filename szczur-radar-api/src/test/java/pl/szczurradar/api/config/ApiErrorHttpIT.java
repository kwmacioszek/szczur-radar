package pl.szczurradar.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.auth.activation.ActivationLinks;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * The body the servlet container really sends on its error path. MockMvc never forwards to
 * {@code /error}, so only a real port shows whether an uncaught exception, an unknown route or a wrong
 * method ends in {@code {"detail": "<code>"}} ({@link ApiErrorController}) and not in Spring Boot's
 * {@code timestamp / status / error / path} body.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
class ApiErrorHttpIT {

	private static final String PASSWORD = "correct horse battery";

	private static final String LOOKUP = "/api/auth/activation/lookup";

	@LocalServerPort
	int port;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	/** A public route whose service fails with an exception nothing handles. */
	@MockitoBean
	ActivationLinks activationLinks;

	@Test
	void anUncaughtExceptionIsAnInternalError() throws Exception {
		failTheLookup();

		HttpResponse<String> response = lookup("application/json");

		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/json"));
		assertThat(response.body()).isEqualTo("{\"detail\":\"internal_error\"}");
	}

	@Test
	void theBodyIsTheSameWhenTheClientAsksForHtml() throws Exception {
		failTheLookup();

		HttpResponse<String> response = lookup("text/html");

		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/json"));
		assertThat(response.body()).isEqualTo("{\"detail\":\"internal_error\"}");
	}

	/** Errors after the authorization, with a real session: the error dispatch is permitted for it. */
	@Test
	void anUnknownRouteAndAWrongMethodWithASessionKeepTheirStatusAndGetACode() throws Exception {
		String cookie = loginCookie();

		HttpResponse<String> unknown = get("/api/no-such-route", cookie);
		HttpResponse<String> wrongMethod = get("/api/auth/login", cookie);

		assertThat(unknown.statusCode()).isEqualTo(404);
		assertThat(unknown.body()).isEqualTo("{\"detail\":\"not_found\"}");
		assertThat(wrongMethod.statusCode()).isEqualTo(405);
		assertThat(wrongMethod.body()).isEqualTo("{\"detail\":\"invalid_request\"}");
	}

	private void failTheLookup() {
		when(activationLinks.lookup(any())).thenThrow(new IllegalStateException("boom: not for the body"));
	}

	private HttpResponse<String> lookup(String accept) throws Exception {
		HttpRequest request = request(LOOKUP)
				.header("Accept", accept)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"token\":\"x\"}"))
				.build();
		return send(request);
	}

	private HttpResponse<String> get(String path, String cookie) throws Exception {
		return send(request(path).header("Cookie", cookie).GET().build());
	}

	/** A real login with an account made here; the {@code sr_session} cookie as the client would send it. */
	private String loginCookie() throws Exception {
		String email = "error-it-" + UUID.randomUUID() + "@example.com";
		users.save(new User(email, passwordEncoder.encode(PASSWORD), Role.USER));
		HttpRequest login = request("/api/auth/login")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(
						"{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
				.build();

		HttpResponse<String> response = send(login);

		assertThat(response.statusCode()).isEqualTo(200);
		String header = response.headers().allValues("Set-Cookie").stream()
				.filter(value -> value.startsWith("sr_session="))
				.findFirst()
				.orElseThrow();
		return header.substring(0, header.indexOf(';'));
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create(origin() + path))
				.timeout(Duration.ofSeconds(10))
				.header("Origin", origin());
	}

	private static HttpResponse<String> send(HttpRequest request) throws Exception {
		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

	private String origin() {
		return "http://127.0.0.1:" + port;
	}

}
