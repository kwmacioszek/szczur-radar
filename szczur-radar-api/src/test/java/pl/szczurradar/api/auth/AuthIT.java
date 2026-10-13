package pl.szczurradar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.cli.MigrateCommand;
import pl.szczurradar.api.config.SessionConfig;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.SystemAccount;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * Login, /me and logout against a real database: the S-01 auth contract (ported from the Python
 * tests/test_auth.py) on Spring Security + Spring Session JDBC.
 *
 * <p>All tests share one application context, hence one login limiter: each test uses its own
 * e-mails and client IP ({@code X-Real-IP}) so limiter state never leaks between tests.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIT {

	private static final String PASSWORD = "correct horse battery";

	private static final String WRONG_PASSWORD = "wrong password!!";

	private static final long MAX_AGE = Duration.ofDays(30).toSeconds();

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessions;

	/** This test's client address; another address is {@link #otherIp}. */
	private final String ip = ipNumber(IP_SEQUENCE.incrementAndGet());

	private final String otherIp = ip.replace("10.0.", "10.1.");

	// --- login --------------------------------------------------------------------------------

	@Test
	void loginSetsTheSessionCookieAndReturnsTheUser() {
		String email = newEmail();
		UUID id = createUser(email, PASSWORD, Role.ADMIN);

		MvcTestResult result = login(email, PASSWORD);

		assertThat(result).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("{\"id\":\"%s\",\"email\":\"%s\",\"role\":\"admin\"}".formatted(id, email));
		assertFullSessionCookie(singleSessionCookie(result));
	}

	@Test
	void loginNormalizesTheEmail() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);

		assertThat(login("  " + email.toUpperCase(Locale.ROOT) + " ", PASSWORD)).hasStatusOk();
	}

	@Test
	void unknownEmailAndWrongPasswordLookIdentical() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);

		for (MvcTestResult result : List.of(login(email, WRONG_PASSWORD), login(newEmail(), PASSWORD))) {
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"invalid_credentials\"}");
			assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
		}
	}

	@Test
	void accountWithoutPasswordCannotLogIn() {
		String email = newEmail();
		createUser(email, null, Role.USER);

		MvcTestResult result = login(email, PASSWORD);

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"invalid_credentials\"}");
	}

	@Test
	void theSystemAccountCannotLogInAndLooksLikeAWrongPassword() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);

		MvcTestResult system = login(SystemAccount.SCHEDULER.email(), PASSWORD);
		MvcTestResult wrongPassword = login(email, WRONG_PASSWORD);

		for (MvcTestResult result : List.of(system, wrongPassword)) {
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.isStrictlyEqualTo("{\"detail\":\"invalid_credentials\"}");
			assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
		}
	}

	@Test
	void loggingInAgainRotatesTheSessionId() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);
		String first = singleSessionCookie(login(email, PASSWORD)).value();

		MvcTestResult again = mvc.post()
			.uri("/api/auth/login")
			.header("X-Real-IP", ip)
			.cookie(sessionCookie(first))
			.contentType(MediaType.APPLICATION_JSON)
			.content(credentials(email, PASSWORD))
			.exchange();

		String second = singleSessionCookie(again).value();
		assertThat(second).isNotEqualTo(first);
		assertThat(me(first)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(me(second)).hasStatusOk();
	}

	@Test
	void formEncodedLoginIsRejectedWithoutACookie() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);

		MvcTestResult result = mvc.post()
			.uri("/api/auth/login")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.content("email=" + email + "&password=" + PASSWORD.replace(' ', '+'))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
	}

	@Test
	void unknownFieldsAreRejectedWithoutACookie() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);

		MvcTestResult result = mvc.post()
			.uri("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\",\"role\":\"admin\"}".formatted(email, PASSWORD))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"invalid_request\"}");
		assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
	}

	@Test
	void foreignOriginIsRejectedBeforeAuthentication() {
		MvcTestResult result = mvc.post()
			.uri("/api/auth/login")
			.header("Host", "szczur.example")
			.header("Origin", "https://evil.example")
			.contentType(MediaType.APPLICATION_JSON)
			.content(credentials(newEmail(), PASSWORD))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN).bodyJson().isStrictlyEqualTo("{\"detail\":\"cross_origin\"}");
	}

	// --- limiter --------------------------------------------------------------------------------

	@Test
	void sixthFailureForAnEmailIsTooManyAttempts() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);
		for (int i = 0; i < LoginRateLimiter.EMAIL_LIMIT; i++) {
			assertThat(login(email, WRONG_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		MvcTestResult blocked = login(email, PASSWORD); // even the right password

		assertThat(blocked).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"too_many_attempts\"}");
		assertThat(Integer.parseInt(blocked.getResponse().getHeader("Retry-After"))).isPositive();
		assertThat(blocked.getResponse().getHeaders("Set-Cookie")).isEmpty();
	}

	@Test
	void ipLimitBlocksAcrossEmails() {
		for (int i = 0; i < LoginRateLimiter.IP_LIMIT; i++) {
			assertThat(login(newEmail(), PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		String fresh = newEmail();

		assertThat(login(fresh, PASSWORD)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(login(fresh, PASSWORD, otherIp)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void successfulLoginResetsTheEmailCounter() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);
		for (int round = 0; round < 2; round++) {
			for (int i = 0; i < LoginRateLimiter.EMAIL_LIMIT - 1; i++) {
				assertThat(login(email, WRONG_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
			}
			assertThat(login(email, PASSWORD)).hasStatusOk();
		}
	}

	// --- /me ------------------------------------------------------------------------------------

	@Test
	void meReturnsOnlyTheOwnAccount() {
		createUser(newEmail(), "another password!", Role.USER);
		String email = newEmail();
		UUID id = createUser(email, PASSWORD, Role.USER);
		String cookie = loginCookie(email);

		assertThat(me(cookie)).hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("{\"id\":\"%s\",\"email\":\"%s\",\"role\":\"user\"}".formatted(id, email));
	}

	@Test
	void anonymousRequestsAre401WithoutCreatingASession() throws Exception {
		for (String accept : List.of(MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE)) {
			long sessionsBefore = sessionRows();

			MvcTestResult result = mvc.get().uri("/api/auth/me").header("Accept", accept).exchange();

			assertThat(result).as(accept).hasStatus(HttpStatus.UNAUTHORIZED);
			assertThat(result.getResponse().getContentAsString()).as(accept)
				.isEqualTo("{\"detail\":\"not_authenticated\"}");
			assertThat(result.getResponse().getHeaders("Set-Cookie")).as(accept).isEmpty();
			assertThat(sessionRows()).as(accept).isEqualTo(sessionsBefore);
		}
	}

	@Test
	void sessionOfADeletedUserIs401AndIsNotRenewed() {
		String email = newEmail();
		UUID id = createUser(email, PASSWORD, Role.USER);
		String cookie = loginCookie(email);
		jdbc.sql("DELETE FROM users WHERE id = ?").param(id).update();

		MvcTestResult result = me(cookie);

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.isStrictlyEqualTo("{\"detail\":\"not_authenticated\"}");
		assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
	}

	@Test
	void deletingTheUsersSessionsRevokesThem() {
		String email = newEmail();
		UUID id = createUser(email, PASSWORD, Role.USER);
		String cookie = loginCookie(email);
		assertThat(me(cookie)).hasStatusOk();

		// How S-07 will revoke access: find the user's sessions by principal (= user id) and delete them.
		Set<String> ids = new HashSet<>(sessions.findByPrincipalName(id.toString()).keySet());
		assertThat(ids).hasSize(1);
		ids.forEach(sessions::deleteById);

		assertThat(me(cookie)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// --- logout ---------------------------------------------------------------------------------

	@Test
	void logoutEndsTheSessionAndClearsTheCookie() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);
		String cookie = loginCookie(email);

		MvcTestResult result = mvc.post().uri("/api/auth/logout").cookie(sessionCookie(cookie)).exchange();

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertClearedCookie(singleSessionCookie(result));
		assertThat(me(cookie)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void logoutWithoutASessionStillClearsTheCookie() {
		MvcTestResult result = mvc.post().uri("/api/auth/logout").exchange();

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertClearedCookie(singleSessionCookie(result));
	}

	// --- session lifetime and cookie renewal ----------------------------------------------------

	@Test
	void meRenewsTheCookieWithTheSameIdAndAFullMaxAge() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);
		String cookie = loginCookie(email);

		SetCookie renewed = singleSessionCookie(me(cookie));

		assertThat(renewed.value()).isEqualTo(cookie);
		assertFullSessionCookie(renewed);
	}

	@Test
	void probesDoNotRenewTheCookie() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);
		String cookie = loginCookie(email);

		for (String probe : List.of("/api/health", "/api/ready")) {
			MvcTestResult result = mvc.get().uri(probe).cookie(sessionCookie(cookie)).exchange();
			assertThat(result).as(probe).hasStatusOk();
			assertThat(result.getResponse().getHeaders("Set-Cookie")).as(probe).isEmpty();
		}
	}

	/**
	 * Deterministic 30-day scenario without waiting: the server's notion of time is moved by aging
	 * the session row, the browser's by a virtual clock applied to the cookie's Max-Age.
	 */
	@Test
	void anActiveSessionOutlivesTheFirstCookieTerm() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);
		Browser browser = new Browser();
		browser.store(singleSessionCookie(login(email, PASSWORD))); // day 0: cookie expires on day 30
		String sessionCookie = browser.cookie();

		ageSession(sessionCookie, Duration.ofDays(29));
		browser.advance(Duration.ofDays(29));
		MvcTestResult day29 = me(browser.cookie());
		assertThat(day29).hasStatusOk();
		browser.store(singleSessionCookie(day29)); // renewed: now expires on day 59

		ageSession(sessionCookie, Duration.ofDays(2));
		browser.advance(Duration.ofDays(2)); // day 31: the day-0 cookie would be gone
		assertThat(browser.expiresAt()).isEqualTo(Duration.ofDays(59));
		assertThat(me(browser.cookie())).hasStatusOk();
	}

	@Test
	void thirtyDaysOfInactivityEndTheSession() {
		String email = newEmail();
		createUser(email, PASSWORD, Role.USER);
		String cookie = loginCookie(email);

		ageSession(cookie, Duration.ofDays(30).plusMinutes(1));
		MvcTestResult result = me(cookie); // the old id sent by hand, past the cookie's own expiry

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(sessionCookies(result)).noneMatch(c -> c.maxAge() != null && c.maxAge() > 0);
	}

	// --- helpers --------------------------------------------------------------------------------

	private static String ipNumber(int n) {
		return "10.0." + n / 250 + "." + (n % 250 + 1);
	}

	private String newEmail() {
		return "user" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}

	private UUID createUser(String email, @Nullable String password, Role role) {
		return users.save(new User(email, password == null ? null : passwordEncoder.encode(password), role)).getId();
	}

	private MvcTestResult login(String email, String password) {
		return login(email, password, ip);
	}

	private MvcTestResult login(String email, String password, String clientIp) {
		return mvc.post()
			.uri("/api/auth/login")
			.header("X-Real-IP", clientIp)
			.contentType(MediaType.APPLICATION_JSON)
			.content(credentials(email, password))
			.exchange();
	}

	private String loginCookie(String email) {
		MvcTestResult result = login(email, PASSWORD);
		assertThat(result).hasStatusOk();
		return singleSessionCookie(result).value();
	}

	private MvcTestResult me(String cookie) {
		return mvc.get().uri("/api/auth/me").cookie(sessionCookie(cookie)).exchange();
	}

	private static String credentials(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	private static Cookie sessionCookie(String value) {
		return new Cookie(SessionConfig.COOKIE_NAME, value);
	}

	private long sessionRows() {
		return jdbc.sql("SELECT count(*) FROM spring_session").query(Long.class).single()
				+ jdbc.sql("SELECT count(*) FROM spring_session_attributes").query(Long.class).single();
	}

	/** Moves the session's last access (and expiry) {@code age} into the past. */
	private void ageSession(String cookie, Duration age) {
		String sessionId = new String(Base64.getDecoder().decode(cookie), StandardCharsets.UTF_8);
		int updated = jdbc
			.sql("UPDATE spring_session SET last_access_time = last_access_time - ?, expiry_time = expiry_time - ?"
					+ " WHERE session_id = ?")
			.params(age.toMillis(), age.toMillis(), sessionId)
			.update();
		assertThat(updated).isOne();
	}

	private static void assertFullSessionCookie(SetCookie cookie) {
		assertThat(cookie.value()).isNotEmpty();
		assertThat(cookie.flags()).contains("httponly", "secure");
		assertThat(cookie.attributes()).containsEntry("samesite", "strict").containsEntry("path", "/api");
		assertThat(cookie.maxAge()).isEqualTo(MAX_AGE);
	}

	private static void assertClearedCookie(SetCookie cookie) {
		assertThat(cookie.value()).isEmpty();
		assertThat(cookie.maxAge()).isZero();
		assertThat(cookie.attributes()).containsEntry("path", "/api");
	}

	private static SetCookie singleSessionCookie(MvcTestResult result) {
		List<SetCookie> cookies = sessionCookies(result);
		assertThat(cookies).as("sr_session Set-Cookie headers").hasSize(1);
		return cookies.getFirst();
	}

	private static List<SetCookie> sessionCookies(MvcTestResult result) {
		MockHttpServletResponse response = result.getResponse();
		return response.getHeaders("Set-Cookie")
			.stream()
			.map(SetCookie::parse)
			.filter(cookie -> cookie.name().equals(SessionConfig.COOKIE_NAME))
			.toList();
	}

	/** A parsed {@code Set-Cookie} header; attribute names lower-cased, values of samesite/path as sent. */
	private record SetCookie(String name, String value, Map<String, String> attributes, Set<String> flags) {

		static SetCookie parse(String header) {
			String[] parts = header.split(";");
			String[] pair = parts[0].split("=", 2);
			Map<String, String> attributes = new HashMap<>();
			Set<String> flags = new HashSet<>();
			for (int i = 1; i < parts.length; i++) {
				String[] attribute = parts[i].strip().split("=", 2);
				String key = attribute[0].toLowerCase(Locale.ROOT);
				if (attribute.length == 2) {
					attributes.put(key, key.equals("samesite") ? attribute[1].toLowerCase(Locale.ROOT) : attribute[1]);
				}
				else {
					flags.add(key);
				}
			}
			return new SetCookie(pair[0].strip(), pair.length == 2 ? pair[1] : "", attributes, flags);
		}

		@Nullable Long maxAge() {
			String maxAge = attributes.get("max-age");
			return maxAge == null ? null : Long.parseLong(maxAge);
		}

	}

	/** Cookie storage on a virtual clock: a cookie lasts Max-Age from the moment it was received. */
	private static final class Browser {

		private Duration now = Duration.ZERO;

		private @Nullable String value;

		private Duration expiresAt = Duration.ZERO;

		void store(SetCookie cookie) {
			value = cookie.value();
			expiresAt = now.plusSeconds(cookie.maxAge());
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		Duration expiresAt() {
			return expiresAt;
		}

		/** The stored cookie, which must not have expired by now. */
		String cookie() {
			assertThat(now).as("browser clock vs cookie expiry").isLessThan(expiresAt);
			return value;
		}

	}

}
