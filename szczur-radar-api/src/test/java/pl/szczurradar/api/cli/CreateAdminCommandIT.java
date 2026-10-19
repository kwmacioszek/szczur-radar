package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * {@code create-admin} against a real database, ported from the Python tests/test_cli.py. Drives
 * {@link AdminBootstrap} (what the command runs once its arguments are valid) with a scripted
 * password prompt; usage errors are covered by {@code CreateAdminCommandTest}.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CreateAdminCommandIT {

	private static final String PASSWORD = "correct horse battery";

	@Autowired
	AdminBootstrap bootstrap;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DataSource dataSource;

	@Autowired
	MockMvcTester mvc;

	private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

	/**
	 * Removes every account that can log in and every admin; the system accounts of the migrations
	 * stay (they are not admins, runs they requested reference them, and no test here creates or
	 * counts them). Admins without login come from other classes sharing this context's database
	 * (e.g. {@code NotificationsIT}) and would make the bootstrap refuse, depending on class order.
	 */
	@BeforeEach
	void emptyDatabase() {
		jdbc.sql("DELETE FROM users WHERE login_enabled OR role = 'admin'").update();
	}

	@Test
	void createsAnAdminWithAnArgon2Hash() {
		ScriptedPrompt prompt = new ScriptedPrompt(PASSWORD, PASSWORD);

		ExitCode code = createAdmin("admin@example.com", prompt);

		assertThat(code).isEqualTo(ExitCode.OK);
		assertThat(prompt.prompts).hasSize(2);
		User admin = users.findByEmail("admin@example.com").orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.getPasswordHash()).startsWith("$argon2id$");
		assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
		assertThat(stdout()).contains("Created admin admin@example.com");
		assertThat(stdout() + stderr()).doesNotContain(PASSWORD);
	}

	@Test
	void theCreatedAdminCanLogIn() {
		assertThat(createAdmin("admin@example.com", new ScriptedPrompt(PASSWORD, PASSWORD))).isEqualTo(ExitCode.OK);

		assertThat(mvc.post()
			.uri("/api/auth/login")
			.header("X-Real-IP", "192.0.2.10")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"admin@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.role")
			.isEqualTo("admin");
	}

	@Test
	void refusesASecondAdminBeforeAskingForAPassword() {
		assertThat(createAdmin("first@example.com", new ScriptedPrompt(PASSWORD, PASSWORD))).isEqualTo(ExitCode.OK);
		ScriptedPrompt prompt = new ScriptedPrompt(PASSWORD, PASSWORD);

		ExitCode code = createAdmin("second@example.com", prompt);

		assertThat(code).isEqualTo(ExitCode.REFUSED);
		assertThat(prompt.prompts).isEmpty();
		assertThat(adminCount()).isOne();
		assertThat(stderr()).contains("Admin already exists");
	}

	@Test
	void concurrentBootstrapsCreateExactlyOneAdmin() throws Exception {
		ScriptedPrompt prompt = new ScriptedPrompt(PASSWORD, PASSWORD, PASSWORD, PASSWORD);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Future<ExitCode>> results = new ArrayList<>();
		try (Connection lockHolder = dataSource.getConnection()) {
			// Hold the bootstrap lock so that both bootstraps queue up behind it.
			lockHolder.setAutoCommit(false);
			try (PreparedStatement lock = lockHolder.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
				lock.setLong(1, AdminBootstrap.LOCK_ID);
				lock.execute();
			}
			for (int i = 0; i < 2; i++) {
				String email = "admin" + i + "@example.com";
				results.add(executor.submit(() -> createAdmin(email, prompt)));
			}
			Thread.sleep(300);
			assertThat(prompt.prompts).as("both wait before the admin check").isEmpty();
			lockHolder.commit(); // releases the lock
		}
		List<ExitCode> codes = new ArrayList<>();
		for (Future<ExitCode> result : results) {
			codes.add(result.get(30, TimeUnit.SECONDS));
		}
		executor.shutdown();

		assertThat(codes).containsExactlyInAnyOrder(ExitCode.OK, ExitCode.REFUSED);
		assertThat(prompt.prompts).hasSize(2);
		assertThat(adminCount()).isOne();
	}

	@Test
	void refusesATakenEmail() {
		users.save(new User("taken@example.com", passwordEncoder.encode("some password!!"), Role.USER));

		ExitCode code = createAdmin("taken@example.com", new ScriptedPrompt(PASSWORD, PASSWORD));

		assertThat(code).isEqualTo(ExitCode.REFUSED);
		assertThat(users.findByEmail("taken@example.com").orElseThrow().getRole()).isEqualTo(Role.USER);
		assertThat(adminCount()).isZero();
	}

	@Test
	void mismatchedPasswordsCreateNothing() {
		ExitCode code = createAdmin("admin@example.com", new ScriptedPrompt(PASSWORD, PASSWORD + "x"));

		assertThat(code).isEqualTo(ExitCode.REFUSED);
		assertThat(userCount()).isZero();
		assertThat(stderr()).contains("do not match");
	}

	@Test
	void tooShortPasswordCreatesNothing() {
		ExitCode code = createAdmin("admin@example.com", new ScriptedPrompt("short", "short"));

		assertThat(code).isEqualTo(ExitCode.REFUSED);
		assertThat(userCount()).isZero();
		assertThat(stderr()).contains("at least 12 characters");
	}

	private ExitCode createAdmin(String email, PasswordPrompt prompt) {
		return bootstrap.createAdmin(email, prompt, new PrintStream(outBytes, true, StandardCharsets.UTF_8),
				new PrintStream(errBytes, true, StandardCharsets.UTF_8));
	}

	private long adminCount() {
		return jdbc.sql("SELECT count(*) FROM users WHERE role = 'admin'").query(Long.class).single();
	}

	private long userCount() {
		return jdbc.sql("SELECT count(*) FROM users WHERE login_enabled").query(Long.class).single();
	}

	private String stdout() {
		return outBytes.toString(StandardCharsets.UTF_8);
	}

	private String stderr() {
		return errBytes.toString(StandardCharsets.UTF_8);
	}

	/** Answers prompts from a script and records which prompts were shown. Thread-safe. */
	private static final class ScriptedPrompt implements PasswordPrompt {

		final List<String> prompts = new CopyOnWriteArrayList<>();

		private final Iterator<String> answers;

		ScriptedPrompt(String... answers) {
			this.answers = List.of(answers).iterator();
		}

		@Override
		public synchronized String read(String prompt) {
			prompts.add(prompt);
			return answers.next();
		}

	}

}
