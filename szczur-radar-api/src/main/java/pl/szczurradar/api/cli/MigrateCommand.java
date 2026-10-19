package pl.szczurradar.api.cli;

import java.io.PrintStream;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.context.ConfigurableApplicationContext;

import pl.szczurradar.api.SzczurRadarApiApplication;

/**
 * {@code migrate}: applies pending Flyway migrations and exits (Railway pre-deploy).
 *
 * <p>The server runs with {@code spring.flyway.enabled=false}; this command starts a non-web Spring
 * context with Flyway switched on, so Spring Boot's Flyway auto-configuration migrates the
 * database during startup with the same datasource and {@code spring.flyway.*} settings as the
 * server. A failed migration fails the context start, which maps to {@link ExitCode#FAILED} (a non-zero
 * exit code) and stops the Railway deploy (the previous version keeps serving). An invalid setting
 * (e.g. {@code LLM_TIMEOUT=120}) fails the start before any migration and maps to {@link ExitCode#USAGE},
 * which stops the deploy too.
 */
public final class MigrateCommand implements Command {

	/** The override that turns migrations on; shared with tests that exercise the same path. */
	public static final String ENABLE_FLYWAY = "spring.flyway.enabled=true";

	private final Function<String[], ConfigurableApplicationContext> contextStarter;

	private final PrintStream err;

	public MigrateCommand() {
		this(MigrateCommand::startMigratingContext, System.err);
	}

	MigrateCommand(Function<String[], ConfigurableApplicationContext> contextStarter, PrintStream err) {
		this.contextStarter = contextStarter;
		this.err = err;
	}

	@Override
	public CommandType type() {
		return CommandType.MIGRATE;
	}

	@Override
	public ExitCode run(String[] args) {
		try (ConfigurableApplicationContext ignored = contextStarter.apply(args)) {
			return ExitCode.OK;
		}
		catch (RuntimeException ex) {
			return CommandChecks.contextFailed(type(), ex, err);
		}
	}

	private static ConfigurableApplicationContext startMigratingContext(String[] args) {
		String[] runArgs = Stream.concat(Stream.of("--" + ENABLE_FLYWAY), Stream.of(args))
				.toArray(String[]::new);
		return SzczurRadarApiApplication.commandApplication().run(runArgs);
	}

}
