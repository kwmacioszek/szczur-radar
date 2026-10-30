package pl.szczurradar.api.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.auth.LoginRateLimiter.Outcome;
import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;
import pl.szczurradar.api.user.Emails;
import pl.szczurradar.api.user.User;
import pl.szczurradar.api.user.UserRepository;

/**
 * Login, logout and "who am I" under {@code /api/auth}. The session lives server-side (Spring
 * Session JDBC); the SPA only holds the HttpOnly {@code sr_session} cookie. Responses only ever
 * describe the logged-in account, and no error response sets the session cookie.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private final AuthenticationManager authenticationManager;

	private final SecurityContextRepository securityContextRepository;

	private final HttpSessionIdResolver sessionIdResolver;

	private final LoginRateLimiter limiter;

	private final UserRepository users;

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	AuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
			HttpSessionIdResolver sessionIdResolver, LoginRateLimiter limiter, UserRepository users) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.sessionIdResolver = sessionIdResolver;
		this.limiter = limiter;
		this.users = users;
	}

	/**
	 * 200 with the account and a new session; 401 {@code invalid_credentials} for an unknown e-mail,
	 * a wrong password or an account without a password (indistinguishable); 429
	 * {@code too_many_attempts} with {@code Retry-After} over the limit; 500 {@code internal_error} when the
	 * account store fails (the attempt does not count towards the limit).
	 */
	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		String email = Emails.normalize(body.email());
		String ip = ClientIp.of(request);

		Optional<Duration> retryAfter = limiter.reserve(email, ip);
		if (retryAfter.isPresent()) {
			return ResponseEntity.status(ErrorCode.TOO_MANY_ATTEMPTS.status())
				.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.get().toSeconds()))
				.body(new ApiError(ErrorCode.TOO_MANY_ATTEMPTS));
		}

		Outcome outcome = Outcome.ABORTED;
		try {
			Authentication authentication;
			try {
				authentication = authenticationManager
					.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
			}
			catch (InternalAuthenticationServiceException ex) {
				// E.g. the database is down: not a verdict on the credentials. Rethrowing would not do:
				// ExceptionTranslationFilter walks the cause chain, finds an AuthenticationException and
				// answers 401, which the SPA shows as a wrong password.
				log.error("login failed: the account store did not answer", ex);
				return ApiError.response(ErrorCode.INTERNAL_ERROR);
			}
			catch (AuthenticationException ex) {
				outcome = Outcome.FAILURE;
				return ApiError.response(ErrorCode.INVALID_CREDENTIALS);
			}
			User user = users.findById(UUID.fromString(authentication.getName()))
				.orElseThrow(() -> new IllegalStateException("authenticated account vanished"));
			startSession(authentication, request, response);
			outcome = Outcome.SUCCESS;
			return ResponseEntity.ok(UserResponse.of(user));
		}
		finally {
			limiter.finish(email, ip, outcome);
		}
	}

	/** Always 204: ends the session if there is one and clears the cookie. Needs no session. */
	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate(); // Spring Session then expires the cookie itself
		}
		else {
			sessionIdResolver.expireSession(request, response); // a stale or foreign cookie
		}
		contextHolder.clearContext();
		return ResponseEntity.noContent().build();
	}

	/** The account owning the session; 401 if it no longer exists. */
	@GetMapping("/me")
	ResponseEntity<?> me(Authentication authentication) {
		return users.findById(UUID.fromString(authentication.getName()))
			.<ResponseEntity<?>>map(user -> ResponseEntity.ok(UserResponse.of(user)))
			.orElseGet(() -> ApiError.response(ErrorCode.NOT_AUTHENTICATED));
	}

	/**
	 * Stores the authentication in the session. An existing session gets a new id first (session
	 * fixation); since Spring Security 6 the context must be saved explicitly.
	 */
	private void startSession(Authentication authentication, HttpServletRequest request,
			HttpServletResponse response) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}

}
