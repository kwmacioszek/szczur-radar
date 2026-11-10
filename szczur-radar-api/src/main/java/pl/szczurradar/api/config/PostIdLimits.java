package pl.szczurradar.api.config;

/**
 * The bound of an admin's list of chosen posts: a run of chosen posts ({@code POST /api/admin/pipeline-runs} with
 * {@code postIds}) and a restore of dead posts ({@code POST /api/admin/posts/restore}, S-33) name at most
 * {@link #MAX_POSTS} ids; more is a 400 {@code invalid_request}.
 */
public final class PostIdLimits {

	/** The most posts one request may name. */
	public static final int MAX_POSTS = 100;

	private PostIdLimits() {
	}

}
