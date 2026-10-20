package pl.szczurradar.api.cli;

import java.io.PrintStream;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import pl.szczurradar.api.alerts.AlertEvent;
import pl.szczurradar.api.alerts.AlertSettings;
import pl.szczurradar.api.alerts.HealthchecksAlerts;
import pl.szczurradar.api.alerts.OperatorAlerts;
import pl.szczurradar.api.alerts.OperatorAlerts.Delivery;
import pl.szczurradar.api.settings.InvalidSettingsException;

/**
 * {@code send-test-alert}: sends {@link AlertEvent#TEST_SIGNAL} to the healthchecks.io check in
 * {@code HEALTHCHECK_PING_URL} (F-04), so the operator sees the channel work end to end: a "down"
 * message in Telegram with the test text, then "up". In production it runs through {@code railway ssh},
 * like {@code create-user}.
 *
 * <p>Without Spring: it needs neither the database ({@code PG*}) nor {@code LLM_*}, only the one
 * setting, read with {@link AlertSettings}. Takes no arguments. The URL is a secret and is never
 * printed; the result is one line, on stdout when delivered and on stderr otherwise.
 *
 * <p>Exit codes: {@link ExitCode#OK} delivered, {@link ExitCode#FAILED} not delivered (the sender logged
 * why at WARN; nothing is retried), {@link ExitCode#USAGE} for an argument or when
 * {@code HEALTHCHECK_PING_URL} is unset or invalid.
 */
public final class SendTestAlertCommand implements Command {

	/** Where the detail says the test came from: the container's or machine's host name. */
	static final String HOSTNAME = "HOSTNAME";

	private final Function<String, @Nullable String> environment;

	private final Function<AlertSettings, OperatorAlerts> sender;

	private final PrintStream out;

	private final PrintStream err;

	public SendTestAlertCommand() {
		this(System::getenv, HealthchecksAlerts::new, System.out, System.err);
	}

	/** @param sender builds the sender for a configured {@code HEALTHCHECK_PING_URL} */
	SendTestAlertCommand(Function<String, @Nullable String> environment,
			Function<AlertSettings, OperatorAlerts> sender, PrintStream out, PrintStream err) {
		this.environment = environment;
		this.sender = sender;
		this.out = out;
		this.err = err;
	}

	@Override
	public CommandType type() {
		return CommandType.SEND_TEST_ALERT;
	}

	@Override
	public ExitCode run(String[] args) {
		if (args.length > 0) {
			err.println("usage: " + type().cliName() + " (no arguments; the check is " + AlertSettings.NAME + ")");
			return ExitCode.USAGE;
		}
		AlertSettings settings;
		try {
			settings = AlertSettings.from(environment);
		}
		catch (InvalidSettingsException ex) {
			err.println(type().cliName() + ": invalid configuration: " + ex.getMessage());
			return ExitCode.USAGE;
		}
		if (!settings.configured()) {
			printNotConfigured();
			return ExitCode.USAGE;
		}
		Delivery delivery = sender.apply(settings).report(AlertEvent.TEST_SIGNAL, detail());
		return switch (delivery) {
			case DELIVERED -> {
				out.println("Test alert delivered to the healthchecks.io check in " + AlertSettings.NAME
						+ ", which is up again: expect a \"down\" message with the test text, then \"up\".");
				yield ExitCode.OK;
			}
			case FAILED -> {
				err.println("Test alert not delivered (see the WARN line above; nothing is retried).");
				yield ExitCode.FAILED;
			}
			case NOT_CONFIGURED -> {
				printNotConfigured();
				yield ExitCode.USAGE;
			}
		};
	}

	private void printNotConfigured() {
		err.println("Operator alerts are not configured: set " + AlertSettings.NAME
				+ " to the ping URL of the healthchecks.io check for API events.");
	}

	private String detail() {
		String host = environment.apply(HOSTNAME);
		return type().cliName() + " from " + (host == null || host.isBlank() ? "operator" : host.strip());
	}

}
