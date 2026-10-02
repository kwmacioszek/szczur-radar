package pl.szczurradar.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import pl.szczurradar.api.auth.Passwords;

/**
 * The password encoder, in every context: the web server (login) and the operator commands, which
 * run without a web server and therefore without {@link SecurityConfig}.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return Passwords.encoder();
	}

}
