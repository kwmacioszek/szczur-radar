package pl.szczurradar.api.auth.activation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.auth.Passwords;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.persistence.Sha256;
import pl.szczurradar.api.user.Role;

/**
 * The public activation API over HTTP ({@link ActivationController}): checking a link, setting the
 * password, logging in with it afterwards, the error codes and the Origin guard.
 *
 * <p>Every call to an activation route goes through {@link #activationCall}, which checks that it set no
 * cookie and stored no session. Each test uses its own e-mails; logins come from an address of their
 * own, so the shared login limiter never mixes with {@code AuthIT}.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActivationIT {

	private static final String LOOKUP = "/api/auth/activation/lookup";

	private static final String ACTIVATE = "/api/auth/activation";

	private static final String PASSWORD = "correct horse battery";

	private static final Duration TTL = Duration.ofHours(12);

	private static final String CLIENT_IP = "10.7.0.1";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	MockMvcTester mvc;

	@Autowired
	ActivationLinks links;

	@Autowired
	JdbcClient jdbc;

	// --- lookup ---------------------------------------------------------------------------------

	@Test
	void lookupOfAValidLinkReturnsItsEmail() {
		String email = newEmail();
		String token = newLink(email);

		assertThat(lookup(token)).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"email\":\"%s\"}".formatted(email));
	}

	@Test
	void lookupOfAnUnknownOrExpiredLinkIsInvalid() {
		String token = newLink(newEmail());
		expire(token);

		for (String invalid : List.of("unknown-" + UUID.randomUUID(), token, "")) {
			assertLinkInvalid(lookup(invalid));
		}
	}

	// --- activation -----------------------------------------------------------------------------

	@Test
	void activationSetsThePasswordForLogin() {
		String email = newEmail();
		String token = newLink(email);

		MvcTestResult result = activate(token, PASSWORD);

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
		assertThat(login(email, PASSWORD)).hasStatusOk();
	}

	@Test
	void aLinkWorksOnce() {
		String email = newEmail();
		String token = newLink(email);
		assertThat(activate(token, PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

		assertLinkInvalid(activate(token, "another password!"));
		assertLinkInvalid(lookup(token));
		assertThat(login(email, PASSWORD)).hasStatusOk();
	}

	@Test
	void anInvalidLinkCannotActivate() {
		String token = newLink(newEmail());
		expire(token);

		assertLinkInvalid(activate(token, PASSWORD));
		assertLinkInvalid(activate("unknown-" + UUID.randomUUID(), PASSWORD));
	}

	@Test
	void aPasswordAgainstThePolicyKeepsTheLink() {
		String email = newEmail();
		String token = newLink(email);

		for (String password : List.of("x".repeat(Passwords.MIN_LENGTH - 1), "x".repeat(Passwords.MAX_LENGTH + 1))) {
			assertThat(activate(token, password)).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"password_policy\"}");
		}

		assertThat(lookup(token)).hasStatusOk();
		assertThat(activate(token, PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(login(email, PASSWORD)).hasStatusOk();
	}

	// --- request shape and origin ---------------------------------------------------------------

	@Test
	void unknownFieldsAndMissingOrOversizedValuesAreInvalidRequests() {
		String token = newLink(newEmail());
		List<MvcTestResult> results = List.of(
				activationCall(LOOKUP, "{\"token\":\"%s\",\"email\":\"x@example.com\"}".formatted(token)),
				activationCall(ACTIVATE, "{\"token\":\"%s\",\"password\":\"%s\",\"role\":\"admin\"}".formatted(token,
						PASSWORD)),
				activationCall(LOOKUP, "{}"),
				activationCall(ACTIVATE, "{\"token\":\"%s\"}".formatted(token)),
				lookup("x".repeat(ActivationController.MAX_TOKEN_LENGTH + 1)));

		for (MvcTestResult result : results) {
			assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"invalid_request\"}");
		}
		assertThat(lookup(token)).hasStatusOk();
	}

	@Test
	void aForeignOriginIsRejectedAndLeavesTheLinkUsable() {
		String email = newEmail();
		String token = newLink(email);

		List<MvcTestResult> results = List.of(foreignOriginCall(LOOKUP, tokenJson(token)),
				foreignOriginCall(ACTIVATE, activationJson(token, PASSWORD)));

		for (MvcTestResult result : results) {
			assertThat(result).hasStatus(HttpStatus.FORBIDDEN)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"cross_origin\"}");
		}
		assertThat(lookup(token)).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"email\":\"%s\"}".formatted(email));
	}

	// --- helpers --------------------------------------------------------------------------------

	private static String newEmail() {
		return "activation" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8)
				+ "@example.com";
	}

	/** A new account waiting for activation; returns the raw token of its link. */
	private String newLink(String email) {
		IssueOutcome outcome = links.createUser(email, Role.USER, TTL);
		assertThat(outcome).isInstanceOf(IssuedLink.class);
		return ((IssuedLink) outcome).token();
	}

	/** Moves the link's expiry into the past. */
	private void expire(String token) {
		int updated = jdbc
			.sql("UPDATE activation_tokens SET expires_at = now() - interval '1 second' WHERE token_hash = ?")
			.param(Sha256.hex(token))
			.update();
		assertThat(updated).isOne();
	}

	private MvcTestResult lookup(String token) {
		return activationCall(LOOKUP, tokenJson(token));
	}

	private MvcTestResult activate(String token, String password) {
		return activationCall(ACTIVATE, activationJson(token, password));
	}

	private MvcTestResult activationCall(String path, String json) {
		return activationCall(mvc.post().uri(path), path, json);
	}

	private MvcTestResult foreignOriginCall(String path, String json) {
		return activationCall(
				mvc.post().uri(path).header("Host", "szczur.example").header("Origin", "https://evil.example"), path,
				json);
	}

	/** A call to an activation route, which must neither set a cookie nor store a session. */
	private MvcTestResult activationCall(MockMvcRequestBuilder request, String path, String json) {
		long sessionsBefore = sessionRows();

		MvcTestResult result = json(request, json).exchange();

		assertThat(result.getResponse().getHeaders("Set-Cookie")).as("Set-Cookie of POST " + path).isEmpty();
		assertThat(sessionRows()).as("session rows after POST " + path).isEqualTo(sessionsBefore);
		return result;
	}

	private MvcTestResult login(String email, String password) {
		return json(mvc.post().uri("/api/auth/login").header("X-Real-IP", CLIENT_IP),
				"{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
			.exchange();
	}

	private static MockMvcRequestBuilder json(MockMvcRequestBuilder request, String json) {
		return request.contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private static String tokenJson(String token) {
		return "{\"token\":\"%s\"}".formatted(token);
	}

	private static String activationJson(String token, String password) {
		return "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password);
	}

	private static void assertLinkInvalid(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"activation_link_invalid\"}");
	}

	private long sessionRows() {
		return jdbc.sql("SELECT count(*) FROM spring_session").query(Long.class).single()
				+ jdbc.sql("SELECT count(*) FROM spring_session_attributes").query(Long.class).single();
	}

}
