package pl.szczurradar.api.pipeline;

import java.util.List;
import java.util.stream.Collectors;

import pl.szczurradar.api.sources.Source;

/** A range run needs sources that are not configured here (e.g. no keys), or no source is configured. */
public class SourceNotConfiguredException extends RuntimeException {

	private final List<Source> sources;

	/** @param sources the requested sources that are not configured; empty when none is configured at all */
	public SourceNotConfiguredException(List<Source> sources) {
		super(sources.isEmpty() ? "no source is configured"
				: "not configured: " + sources.stream().map(Source::value).collect(Collectors.joining(", ")));
		this.sources = List.copyOf(sources);
	}

	public List<Source> sources() {
		return sources;
	}

}
