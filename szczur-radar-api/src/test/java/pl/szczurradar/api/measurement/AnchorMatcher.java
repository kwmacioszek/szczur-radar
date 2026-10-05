package pl.szczurradar.api.measurement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import pl.szczurradar.api.agents.assessor.text.Whitespace;

/**
 * Pairs gold targets with a model's targets by anchor. Anchors are normalized (§10 white space,
 * lower case, leading {@code #} removed); two anchors match when they are equal or the shorter one
 * occurs in the longer one as a whole word — not preceded or followed by a letter, digit or
 * {@code _} ({@code XTB} ↔ {@code (XTB)}, {@code $XTB}, {@code akcje XTB}; not {@code XTBR}, not
 * {@code AI} ↔ {@code Taiwan}). Pairs are one-to-one and as many as possible; ties go by gold order,
 * then model order.
 */
public final class AnchorMatcher {

	private static final String WORD = "[\\p{L}\\p{N}_]";

	private AnchorMatcher() {
	}

	/** A matched pair: indices into the gold and the model target lists. */
	public record Pair(int gold, int model) {
	}

	/** The normalized form compared by {@link #matches}. */
	static String normalize(String anchor) {
		String text = Whitespace.normalize(anchor).toLowerCase(Locale.ROOT);
		int start = 0;
		while (start < text.length() && text.charAt(start) == '#') {
			start++;
		}
		return Whitespace.normalize(text.substring(start));
	}

	public static boolean matches(String gold, String model) {
		String a = normalize(gold);
		String b = normalize(model);
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		if (a.equals(b)) {
			return true;
		}
		String shorter = a.length() <= b.length() ? a : b;
		String longer = shorter == a ? b : a;
		return Pattern.compile("(?<!" + WORD + ")" + Pattern.quote(shorter) + "(?!" + WORD + ")").matcher(longer).find();
	}

	/**
	 * A maximum one-to-one matching (augmenting paths, gold targets in order, model targets tried in
	 * order), returned in gold order.
	 */
	public static List<Pair> match(List<String> gold, List<String> model) {
		return matchNames(gold.stream().map(List::of).toList(), model);
	}

	/**
	 * Like {@link #match}, but a gold target may have several names (its anchor and aliases from
	 * {@code gold/aliases.json}): it pairs with a model target that matches any of them.
	 */
	public static List<Pair> matchNames(List<List<String>> gold, List<String> model) {
		boolean[][] edges = new boolean[gold.size()][model.size()];
		for (int g = 0; g < gold.size(); g++) {
			for (int m = 0; m < model.size(); m++) {
				int target = m;
				edges[g][m] = gold.get(g).stream().anyMatch(name -> matches(name, model.get(target)));
			}
		}
		int[] goldOfModel = new int[model.size()];
		Arrays.fill(goldOfModel, -1);
		for (int g = 0; g < gold.size(); g++) {
			augment(g, edges, goldOfModel, new boolean[model.size()]);
		}
		List<Pair> pairs = new ArrayList<>();
		for (int g = 0; g < gold.size(); g++) {
			for (int m = 0; m < model.size(); m++) {
				if (goldOfModel[m] == g) {
					pairs.add(new Pair(g, m));
				}
			}
		}
		return pairs;
	}

	private static boolean augment(int g, boolean[][] edges, int[] goldOfModel, boolean[] visited) {
		for (int m = 0; m < goldOfModel.length; m++) {
			if (edges[g][m] && !visited[m]) {
				visited[m] = true;
				if (goldOfModel[m] < 0 || augment(goldOfModel[m], edges, goldOfModel, visited)) {
					goldOfModel[m] = g;
					return true;
				}
			}
		}
		return false;
	}

}
