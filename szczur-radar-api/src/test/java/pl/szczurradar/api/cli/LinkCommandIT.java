package pl.szczurradar.api.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.testcontainers.postgresql.PostgreSQLContainer;

import pl.szczurradar.api.SzczurRadarApiApplication;
import pl.szczurradar.api.TestcontainersConfiguration;
import pl.szczurradar.api.auth.activation.ActivationLinks;
import pl.szczurradar.api.auth.activation.ActivationSettings;
import pl.szczurradar.api.auth.activation.ActivationToken;
import pl.szczurradar.api.auth.activation.ActivationTokenRepository;
import pl.szczurradar.api.persistence.Sha256;
import pl.szczurradar.api.user.Role;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * {@code create-user} and {@code reset-password} against a real database. Each run starts the
 * command's own Spring context, as {@code java -jar szczur-radar-api.jar create-user} does (no web
 * server, so no Spring Session repository), pointed at this test's Postgres; this test's context
 * migrates the schema and checks the result. Usage and configuration errors are covered by
 * {@code CreateUserCommandTest} and {@code ResetPasswordCommandTest}. Each test uses its own e-mails.
 */
@SpringBootTest(properties = MigrateCommand.ENABLE_FLYWAY)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class LinkCommandIT {

	private static final String WEB = "https://web.example.test";

	private static final Pattern LINK = Pattern.compile(Pattern.quote(WEB + "/activate#") + "([A-Za-z0-9_-]{43})");

	private static final String PASSWORD = "correct horse battery";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	/**
	 * The settings the commands check before their context starts. The database values only have to
	 * be present: the context gets this test's database from its arguments.
	 */
	private static final Map<String, String> ENVIRONMENT = Map.of("PGHOST", "testcontainers", "PGPORT", "5432",
			"PGDATABASE", "test", "PGUSER", "test", "PGPASSWORD", "test", ActivationSettings.WEB_PUBLIC_URL, WEB);

	@Autowired
	PostgreSQLContainer postgres;

	@Autowired
	UserRepository users;

	@Autowired
	ActivationTokenRepository tokens;

	@Autowired
	ActivationLinks links;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessions;

	private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

	private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

	// --- create-user ------------------------------------------------------------------------------

	@Test
	void createUserPrintsALinkAndMakesAUserWithoutAPassword(CapturedOutput log) {
		String email = newEmail();
		int logBefore = log.getAll().length();

		int code = run(CreateUserCommand::new, "--email", " " + email.toUpperCase(Locale.ROOT) + " ");

		String commandLog = log.getAll().substring(logBefore);
		assertThat(code).isZero();
		User user = users.findByEmail(email).orElseThrow();
		assertThat(user.getRole()).isEqualTo(Role.USER);
		assertThat(user.getPasswordHash()).isNull();
		String token = printedToken();
		ActivationToken stored = tokens.findById(user.getId()).orElseThrow();
		assertThat(stored.getTokenHash()).isEqualTo(Sha256.hex(token));
		assertThat(description()).isEqualTo("Created account " + email + " (role user) without a password."
				+ " One-time activation link, valid until " + stored.getExpiresAt() + ":");
		assertThat(links.lookup(token)).contains(email);
		// The token is on the link line only: not on stderr, not in the log of the command's context.
		assertThat(stderr()).isEmpty();
		assertThat(commandLog).as("log of the command's context").contains("Started ").doesNotContain(token);
	}

	@Test
	void createUserWithRoleAdminMakesAnAdmin() {
		String email = newEmail();

		assertThat(run(CreateUserCommand::new, "--email", email, "--role", "admin")).isZero();

		User admin = users.findByEmail(email).orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.getPasswordHash()).isNull();
		assertThat(description()).contains("(role admin)");
		assertThat(links.lookup(printedToken())).contains(email);
	}

	@Test
	void createUserRefusesATakenEmail() {
		String email = newEmail();
		UUID id = users.save(new User(email, passwordEncoder.encode(PASSWORD), Role.USER)).getId();

		int code = run(CreateUserCommand::new, "--email", email, "--role", "admin");

		assertThat(code).isEqualTo(LinkCommand.REFUSED);
		assertThat(stderr()).contains("already exists").contains("reset-password --email " + email);
		assertThat(stdout()).isEmpty();
		User user = users.findById(id).orElseThrow();
		assertThat(user.getRole()).isEqualTo(Role.USER);
		assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
		assertThat(tokens.findById(id)).isEmpty();
	}

	// --- reset-password ---------------------------------------------------------------------------

	@Test
	void resetPasswordOfAnUnknownEmailIsRefused() {
		String email = newEmail();

		int code = run(ResetPasswordCommand::new, "--email", email);

		assertThat(code).isEqualTo(LinkCommand.REFUSED);
		assertThat(stderr()).contains("No account with e-mail " + email).contains("create-user --email " + email);
		assertThat(stdout()).isEmpty();
		assertThat(users.findByEmail(email)).isEmpty();
	}

	@Test
	void resetPasswordEndsTheSessionsAndPrintsALinkToANewPassword() {
		String email = newEmail();
		UUID id = users.save(new User(email, passwordEncoder.encode(PASSWORD), Role.ADMIN)).getId();
		createSession(sessions, id);
		assertThat(sessionRows(id)).isOne();

		int code = run(ResetPasswordCommand::new, "--email", email);

		assertThat(code).isZero();
		assertThat(sessionRows(id)).isZero();
		User admin = users.findById(id).orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.getPasswordHash()).isNull();
		assertThat(description()).startsWith("Reset account " + email + " (role admin): password removed");
		assertThat(links.lookup(printedToken())).contains(email);
	}

	// --- helpers ----------------------------------------------------------------------------------

	/** Runs a command as the jar does, with its own context on this test's database. */
	private int run(CommandFactory factory, String... args) {
		Command command = factory.create(ENVIRONMENT::get, this::startCommandContext,
				new PrintStream(outBytes, true, StandardCharsets.UTF_8),
				new PrintStream(errBytes, true, StandardCharsets.UTF_8));
		return command.run(args);
	}

	private ConfigurableApplicationContext startCommandContext(String[] args) {
		return SzczurRadarApiApplication.commandApplication()
			.run("--spring.datasource.url=" + postgres.getJdbcUrl(),
					"--spring.datasource.username=" + postgres.getUsername(),
					"--spring.datasource.password=" + postgres.getPassword());
	}

	/** The token of the link, which must be the last line of stdout and alone on it. */
	private String printedToken() {
		List<String> lines = stdout().lines().toList();
		assertThat(lines).hasSize(2);
		Matcher link = LINK.matcher(lines.getLast());
		assertThat(link.matches()).as("link line: %s", lines.getLast()).isTrue();
		assertThat(lines.getFirst()).doesNotContain(link.group(1));
		return link.group(1);
	}

	/** The line before the link. */
	private String description() {
		return stdout().lines().findFirst().orElseThrow();
	}

	private long sessionRows(UUID userId) {
		return jdbc.sql("SELECT count(*) FROM spring_session WHERE principal_name = ?")
			.param(userId.toString())
			.query(Long.class)
			.single();
	}

	/** A session of {@code userId} as login stores it: the user's id is the principal name. */
	private static <S extends Session> void createSession(FindByIndexNameSessionRepository<S> repository, UUID userId) {
		S session = repository.createSession();
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId.toString());
		repository.save(session);
	}

	private static String newEmail() {
		return "cli" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}

	private String stdout() {
		return outBytes.toString(StandardCharsets.UTF_8);
	}

	private String stderr() {
		return errBytes.toString(StandardCharsets.UTF_8);
	}

	/** The package constructor both link commands have. */
	@FunctionalInterface
	private interface CommandFactory {

		Command create(Function<String, String> environment,
				Function<String[], ConfigurableApplicationContext> contextStarter, PrintStream out, PrintStream err);

	}

}
