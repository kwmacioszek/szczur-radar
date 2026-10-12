package pl.szczurradar.api.auth.activation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.szczurradar.api.config.ApiError;
import pl.szczurradar.api.config.ErrorCode;

/**
 * Setting the first (or a reset) password through a one-time link, under {@code /api/auth/activation}.
 * Both routes are public: the token in the body is the credential (permitted in {@code SecurityConfig}).
 *
 * <p>The SPA reads the token from the link's fragment and sends it only in a JSON body. Neither route
 * touches the HTTP session or the security context: they create no session and set no cookie, and only
 * {@code POST /api/auth/login} logs the account in afterwards. Cross-origin calls are refused by
 * {@code OriginGuardFilter} like every other write. Nothing here logs a token or a password.
 */
@RestController
@RequestMapping("/api/auth/activation")
class ActivationController {

	/** Longer than any issued token (43 characters), short enough to stop junk before hashing. */
	static final int MAX_TOKEN_LENGTH = 64;

	private final ActivationLinks links;

	ActivationController(ActivationLinks links) {
		this.links = links;
	}

	/**
	 * 200 with the e-mail of the account a valid link activates, for the password form; changes nothing.
	 * 400 {@code activation_link_invalid} for an unknown, used, expired or replaced link (indistinguishable).
	 */
	@PostMapping(path = "/lookup", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<?> lookup(@Valid @RequestBody LookupRequest body) {
		return links.lookup(body.token())
			.<ResponseEntity<?>>map(email -> ResponseEntity.ok(new LookupResponse(email)))
			.orElseGet(() -> ApiError.response(ErrorCode.ACTIVATION_LINK_INVALID));
	}

	/**
	 * 204 once the password is set and the link used up. 400 {@code password_policy} for a password outside
	 * the policy (the link still works), 400 {@code activation_link_invalid} as in {@link #lookup}.
	 */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<?> activate(@Valid @RequestBody ActivateRequest body) {
		return switch (links.activate(body.token(), body.password())) {
			case ACTIVATED -> ResponseEntity.noContent().build();
			case LINK_INVALID -> ApiError.response(ErrorCode.ACTIVATION_LINK_INVALID);
			case PASSWORD_POLICY -> ApiError.response(ErrorCode.PASSWORD_POLICY);
		};
	}

	/** Body of the lookup. Unknown fields are rejected (closed model). */
	record LookupRequest(@NotNull @Size(max = MAX_TOKEN_LENGTH) String token) {

		@Override
		public String toString() {
			return "LookupRequest[token=<hidden>]";
		}

	}

	/** The account the link activates. */
	record LookupResponse(String email) {
	}

	/**
	 * Body of the activation. Unknown fields are rejected (closed model).
	 *
	 * @param password capped well above the policy maximum, as at login; the policy itself is checked by
	 *     {@link ActivationLinks#activate}
	 */
	record ActivateRequest(@NotNull @Size(max = MAX_TOKEN_LENGTH) String token,
			@NotNull @Size(max = 1024) String password) {

		@Override
		public String toString() {
			return "ActivateRequest[token=<hidden>, password=<hidden>]";
		}

	}

}
