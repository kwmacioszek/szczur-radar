package pl.szczurradar.api.posts;

import java.util.UUID;

/** No assessed post has the id: it is unknown, or the post is in another stage. */
public class PostNotFoundException extends RuntimeException {

	public PostNotFoundException(UUID id) {
		super("no assessed post " + id);
	}

}
