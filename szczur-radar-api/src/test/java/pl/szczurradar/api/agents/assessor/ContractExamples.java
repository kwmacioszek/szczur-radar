package pl.szczurradar.api.agents.assessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * The numbered examples of {@code context/foundation/llm-assessment/examples.md}: each section
 * {@code # N.} with two {@code ```json} blocks (input, output). Read from the repository so that the
 * document and the code cannot drift apart.
 */
public record ContractExamples(int number, String title, String inputJson, String outputJson) {

	/** Relative to the module directory, where Maven runs the tests. */
	public static final Path EXAMPLES = Path.of("../context/foundation/llm-assessment/examples.md");

	private static final Pattern SECTION = Pattern.compile("(?m)^# (\\d+)\\. (.+)$");

	private static final Pattern JSON_BLOCK = Pattern.compile("(?s)```json\\n(.*?)\\n```");

	@Override
	public String toString() {
		return number + ". " + title;
	}

	public static List<ContractExamples> load() {
		String text;
		try {
			text = Files.readString(EXAMPLES, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("cannot read " + EXAMPLES.toAbsolutePath(), ex);
		}
		List<MatchResult> sections = SECTION.matcher(text).results().toList();
		List<ContractExamples> examples = new ArrayList<>();
		for (int i = 0; i < sections.size(); i++) {
			MatchResult section = sections.get(i);
			int end = i + 1 < sections.size() ? sections.get(i + 1).start() : text.length();
			List<String> json = JSON_BLOCK.matcher(text.substring(section.start(), end))
				.results()
				.map(block -> block.group(1))
				.toList();
			if (json.size() == 2) {
				examples.add(new ContractExamples(Integer.parseInt(section.group(1)), section.group(2), json.get(0),
						json.get(1)));
			}
		}
		return examples;
	}

}
