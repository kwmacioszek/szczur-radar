package pl.szczurradar.api.posts;

/** The source-side identity of a post within one source: its resource type and source id. */
public record PostKey(PostResource resource, String sourceItemId) {

}
