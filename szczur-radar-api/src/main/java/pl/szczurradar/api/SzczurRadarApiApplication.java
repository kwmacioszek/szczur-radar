package pl.szczurradar.api;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import pl.szczurradar.api.cli.CommandLine;

/**
 * One jar, two modes.
 *
 * <ul>
 *   <li>No arguments: the HTTP server ({@code java -jar target/szczur-radar-api.jar}).
 *   <li>A command name: an operator command run without the web server, e.g. {@code migrate}
 *       (Railway pre-deploy). The JVM exits with the command's exit code.
 * </ul>
 */
@SpringBootApplication
public class SzczurRadarApiApplication {

	public static void main(String[] args) {
		if (args.length == 0) {
			SpringApplication.run(SzczurRadarApiApplication.class, args);
			return;
		}
		System.exit(CommandLine.standard().run(args));
	}

	/**
	 * Builder for an operator command's Spring context: same configuration as the server, but no
	 * web server and no banner. Commands that need to override configuration pass it as
	 * {@code --name=value} run arguments (these take precedence over application.properties).
	 */
	public static SpringApplicationBuilder commandApplication() {
		return new SpringApplicationBuilder(SzczurRadarApiApplication.class)
				.web(WebApplicationType.NONE)
				.bannerMode(Banner.Mode.OFF);
	}

}
