package pl.szczurradar.api.measurement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.prompt.AssessmentPrompt;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.measurement.GoldLabels.Label;
import pl.szczurradar.api.measurement.GoldLabels.LabeledTarget;
import pl.szczurradar.api.measurement.GoldMetrics.Metric;
import pl.szczurradar.api.measurement.GoldMetrics.Ratio;
import pl.szczurradar.api.measurement.ModelsConfig.Model;
import pl.szczurradar.api.measurement.ModelsConfig.Price;

/**
 * Writes the gold report (plan phase 6): quality and cost of every configuration in
 * {@code models.json} from its recordings, judged again with today's code — no model calls.
 *
 * <pre>
 * ./mvnw -Pmeasurement test -Dtest=GoldReport -Dreport.out=../context/changes/assessment-contract-harness/reports/gold-&lt;date&gt;.md
 * </pre>
 *
 * A configuration enters the main table only with a current recording of every gold post; an
 * incomplete one is listed with its missing posts.
 */
@Tag("measurement")
class GoldReport {

	/** Monthly volume used for the cost estimate (frame.md, plan: ~12k posts a month). */
	static final int POSTS_PER_MONTH = 12_000;

	/**
	 * Gold targets covered by contract §6.5a, which was written after labeling these posts (review of
	 * phase 5, F4): the report gives target metrics with and without them.
	 */
	static final Map<String, String> SECTION_6_5A_TARGETS = Map.of("gielda-2026-10-01-005", "krach",
			"gielda-2026-10-01-014", "dzien kibla", "gielda-2026-10-01-023", "pompa", "gielda-2026-10-01-024", "KRACHU",
			"gielda-2026-10-02-005", "spadki", "gielda-2026-10-02-043", "Pompujemy", "gielda-2026-10-02-057", "bessa",
			"gielda-2026-10-01-034", "Nic nie rośnie", "gielda-2026-10-02-041", "jebło");

	private static final List<Metric> TARGET_METRICS = List.of(Metric.TARGET_RECALL, Metric.TARGET_PRECISION,
			Metric.TARGET_DIRECTION_ACCURACY, Metric.DOMINANT_DIRECTION_AGREEMENT, Metric.CONTRADICTION_RATE);

	@Test
	void writesTheReport() throws IOException {
		Path out = Path.of(Objects.requireNonNull(System.getProperty("report.out"), "-Dreport.out=<path> is required"));
		ModelsConfig config = ModelsConfig.read(ModelsConfig.FILE);
		List<GoldLabels.Post> posts = GoldLabels.readPosts(GoldLabels.POSTS);
		List<Label> labels = GoldLabels.readLabels(GoldLabels.LABELS);
		List<Label> without65a = withoutSection65a(labels);
		Map<String, Map<String, List<String>>> aliases = GoldLabels.readAliases(GoldLabels.ALIASES);

		StringBuilder md = new StringBuilder();
		LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
		md.append("# Raport zestawu wzorcowego — ").append(today).append("\n\n");
		md.append("- Prompt: `").append(AssessmentPrompt.PROMPT_VERSION).append("`, kontrakt: `")
			.append(AssessmentOutputV2.CONTRACT_VERSION).append("`\n");
		md.append("- Wzorzec: ").append(posts.size()).append(" postów (")
			.append(labels.stream().filter(label -> !label.hard()).count())
			.append(" bez „trudnych”), dni ")
			.append(posts.stream().map(GoldLabels.Post::day).distinct().sorted().map(LocalDate::toString)
				.collect(Collectors.joining(", ")))
			.append("\n");
		md.append("- Ceny: ").append(config.pricesCheckedAt()).append(" (")
			.append(String.join("; ", config.priceSources())).append(")\n");
		md.append("- Wzorzec uzupełniony 2026-10-05 według §6.3/§6.5a o cele, które pominięto przy oznaczaniu: kandydatów ")
			.append("wskazały co najmniej dwa modele, każdą decyzję (dodać / odrzucić / alias) podjął użytkownik ")
			.append("(`implementation-notes.md`, N13); aliasy: `gold/aliases.json`\n");
		md.append("- Model domyślny w `models.json`: ")
			.append(config.defaultId() == null ? "— (do wyboru)" : "`" + config.defaultId() + "`").append("\n\n");

		List<Replay.Outcome> complete = new ArrayList<>();
		List<Replay.Outcome> incomplete = new ArrayList<>();
		for (Model model : config.models()) {
			Replay.Outcome outcome = Replay.of(model, posts);
			(outcome.complete() ? complete : incomplete).add(outcome);
		}

		md.append("## Jakość\n\n");
		md.append("| Miara | Próg |");
		complete.forEach(outcome -> md.append(" `").append(outcome.model().id()).append("` (")
			.append(outcome.model().role().value()).append(") |"));
		md.append("\n|---|---|").append("---|".repeat(complete.size())).append("\n");
		Map<String, GoldMetrics> metricsByModel = new TreeMap<>();
		for (Replay.Outcome outcome : complete) {
			metricsByModel.put(outcome.model().id(), GoldMetrics.compute(labels, outcome.outputs(), aliases));
		}
		for (Metric metric : Metric.values()) {
			md.append("| ").append(label(metric)).append(" | ").append(metric.atLeast() ? "≥ " : "≤ ")
				.append(percent(metric.threshold())).append(" |");
			for (Replay.Outcome outcome : complete) {
				GoldMetrics metrics = metricsByModel.get(outcome.model().id());
				md.append(' ').append(cell(metrics.ratio(metric), metric.meets(metrics.ratio(metric)))).append(" |");
			}
			md.append('\n');
		}
		md.append("| **Wszystkie progi** | |");
		for (Replay.Outcome outcome : complete) {
			List<Metric> unmet = metricsByModel.get(outcome.model().id()).unmet();
			md.append(unmet.isEmpty() ? " ✅ tak |" : " ❌ nie (" + unmet.size() + ") |");
		}
		md.append("\n\n");

		md.append("### Miary celów bez ").append(SECTION_6_5A_TARGETS.size()).append(" celów z §6.5a\n\n");
		md.append("§6.5a (slang ruchu rynku → cel „rynek”) powstał po oznaczeniu tych samych postów (przegląd fazy 5, F4); ")
			.append("poniżej te same miary po usunięciu tych celów ze wzorca: ")
			.append(SECTION_6_5A_TARGETS.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey().substring("gielda-".length()) + " „" + entry.getValue() + "”")
				.collect(Collectors.joining(", ")))
			.append(".\n\n| Miara |");
		complete.forEach(outcome -> md.append(" `").append(outcome.model().id()).append("` z §6.5a | bez |"));
		md.append("\n|---|").append("---|---|".repeat(complete.size())).append("\n");
		for (Metric metric : TARGET_METRICS) {
			md.append("| ").append(label(metric)).append(" |");
			for (Replay.Outcome outcome : complete) {
				Ratio with = metricsByModel.get(outcome.model().id()).ratio(metric);
				Ratio without = GoldMetrics.compute(without65a, outcome.outputs(), aliases).ratio(metric);
				md.append(' ').append(cell(with, metric.meets(with))).append(" | ")
					.append(cell(without, metric.meets(without))).append(" |");
			}
			md.append('\n');
		}
		md.append('\n');

		md.append("## Naruszenia kontraktu (wg klasy)\n\n| Klasa |");
		complete.forEach(outcome -> md.append(" `").append(outcome.model().id()).append("` |"));
		md.append("\n|---|").append("---|".repeat(complete.size())).append("\n");
		for (FailureClass failureClass : FailureClass.values()) {
			md.append("| `").append(failureClass.value()).append("` |");
			for (Replay.Outcome outcome : complete) {
				long count = outcome.results().values().stream().filter(r -> r.failureClass() == failureClass).count();
				md.append(' ').append(count).append(" |");
			}
			md.append('\n');
		}
		md.append('\n');

		md.append("## Zużycie, czas i koszt\n\n");
		for (Replay.Outcome outcome : complete) {
			appendUsage(md, outcome);
		}

		if (!incomplete.isEmpty()) {
			md.append("## Konfiguracje bez kompletu bieżących nagrań\n\n");
			for (Replay.Outcome outcome : incomplete) {
				md.append("- `").append(outcome.model().id()).append("`: ").append(outcome.results().size()).append("/")
					.append(posts.size()).append(" nagrań; brakuje: ")
					.append(outcome.missing().size() > 20
							? String.join(", ", outcome.missing().subList(0, 20)) + " … (+" + (outcome.missing().size() - 20) + ")"
							: String.join(", ", outcome.missing()))
					.append('\n');
			}
			md.append('\n');
		}

		md.append(LEGEND);
		Files.createDirectories(out.toAbsolutePath().getParent());
		Files.writeString(out, md.toString());
		System.out.println("wrote " + out.toAbsolutePath());
	}

	private static void appendUsage(StringBuilder md, Replay.Outcome outcome) {
		Model model = outcome.model();
		Collection<Recording> recordings = outcome.recordings().values();
		int n = recordings.size();
		long input = sum(recordings, Recording::inputTokens);
		long cached = sum(recordings, Recording::cachedTokens);
		long output = sum(recordings, Recording::outputTokens);
		List<Long> durations = recordings.stream().map(Recording::durationMs).sorted().toList();
		int retries = recordings.stream().mapToInt(recording -> Math.max(0, recording.attempts() - 1)).sum();
		List<Recording> fallbacks = recordings.stream().filter(Recording::flexFallback).toList();

		md.append("### `").append(model.id()).append("` — ").append(model.provider()).append(' ').append(model.model())
			.append(", tier ").append(model.serviceTier()).append("\n\n");
		md.append("- Tokeny: wejście ").append(input).append(" (z cache ").append(cached).append("), wyjście ").append(output)
			.append("; na post średnio ").append(input / Math.max(1, n)).append(" / ").append(output / Math.max(1, n))
			.append('\n');
		md.append("- Mediana czasu: ").append(durations.isEmpty() ? "—" : durations.get(durations.size() / 2) + " ms")
			.append("; ponowienia transportowe: ").append(retries).append(" (w ")
			.append(recordings.stream().filter(recording -> recording.attempts() > 1).count()).append(" wywołaniach)\n");
		if ("flex".equals(model.serviceTier())) {
			md.append("- Flex: powroty do trybu standardowego ").append(fallbacks.size()).append("/").append(n)
				.append(" (każdy = 3 kolejne odmowy 429); koszt postów po powrocie: ")
				.append(usd(fallbacks.stream().mapToDouble(r -> actualCost(model, r)).sum())).append('\n');
		}
		Map<String, Double> perChannel = new TreeMap<>();
		model.prices().forEach((channel, price) -> perChannel.put(channel,
				recordings.stream().mapToDouble(r -> price.cost(r.inputTokens(), r.cachedTokens(), r.outputTokens())).sum()));
		md.append("- Koszt (USD) przy cenach kanału — za ").append(n).append(" postów / na post / na ")
			.append(POSTS_PER_MONTH).append(" postów miesięcznie:\n");
		perChannel.forEach((channel, cost) -> md.append("  - `").append(channel).append("`: ").append(usd(cost)).append(" / ")
			.append(usd(cost / Math.max(1, n))).append(" / **").append(usd(cost / Math.max(1, n) * POSTS_PER_MONTH))
			.append("**\n"));
		if (recordings.stream().allMatch(r -> r.channel() != null && model.prices().containsKey(r.channel()))) {
			double actual = recordings.stream().mapToDouble(r -> actualCost(model, r)).sum();
			md.append("  - faktyczny kanał każdego wywołania: ").append(usd(actual)).append(" / ")
				.append(usd(actual / Math.max(1, n))).append(" / **").append(usd(actual / Math.max(1, n) * POSTS_PER_MONTH))
				.append("**\n");
		}
		md.append('\n');
	}

	private static double actualCost(Model model, Recording recording) {
		Price price = recording.channel() == null ? null : model.prices().get(recording.channel());
		return price == null ? 0 : price.cost(recording.inputTokens(), recording.cachedTokens(), recording.outputTokens());
	}

	/** The labels without the targets of {@link #SECTION_6_5A_TARGETS}. */
	static List<Label> withoutSection65a(List<Label> labels) {
		return labels.stream().map(label -> {
			String anchor = SECTION_6_5A_TARGETS.get(label.postId());
			if (anchor == null) {
				return label;
			}
			List<LabeledTarget> kept = label.targets().stream().filter(target -> !target.anchor().equals(anchor)).toList();
			if (kept.size() != label.targets().size() - 1) {
				throw new IllegalStateException(label.postId() + " has no target '" + anchor + "'");
			}
			return new Label(label.postId(), label.relevance(), label.hard(), label.note(), kept);
		}).toList();
	}

	private static long sum(Collection<Recording> recordings, Function<Recording, @Nullable Integer> field) {
		return recordings.stream().map(field).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
	}

	private static String cell(Ratio ratio, boolean meets) {
		return (meets ? "✅ " : "❌ ") + percent(ratio.value()) + " (" + ratio.numerator() + "/" + ratio.denominator() + ")";
	}

	private static String percent(double value) {
		return String.format(Locale.ROOT, "%.1f %%", value * 100);
	}

	private static String usd(double value) {
		return String.format(Locale.ROOT, "$%.4f", value);
	}

	private static String label(Metric metric) {
		return LABELS.get(metric);
	}

	private static final Map<Metric, String> LABELS = new EnumMap<>(Map.of(Metric.CONTRACT_COMPLIANCE,
			"zgodność z kontraktem", Metric.RELEVANCE_BINARY_ACCURACY, "trafność (binarna)", Metric.TARGET_RECALL,
			"recall celów", Metric.TARGET_PRECISION, "precision celów", Metric.TARGET_DIRECTION_ACCURACY,
			"kierunek celów", Metric.DOMINANT_DIRECTION_AGREEMENT, "dominujący kierunek posta", Metric.CONTRADICTION_RATE,
			"sprzeczne kierunki"));

	private static final String LEGEND = """
			## Definicje miar

			Każdy oznaczony post się liczy; post bez poprawnej odpowiedzi (`failed` albo brak nagrania) nie ma trafności ani celów — jest błędem wszędzie, gdzie się liczy, i nie znika z mianownika. Posty „trudne” liczą się tylko do zgodności z kontraktem. Cele są parowane 1:1 przez `AnchorMatcher` (normalizacja białych znaków i wielkości liter, początkowe `#` pomijane, całe słowa).

			| Miara | Licznik / mianownik |
			|---|---|
			| zgodność z kontraktem | poprawne odpowiedzi / wszystkie posty |
			| trafność (binarna) | trafność modelu = wzorcowej / posty z trafnością wzorcową inną niż `unclear` (`unclear` modelu = błąd) |
			| recall celów | dopasowane cele wzorca / cele wzorca |
			| precision celów | dopasowane cele modelu / cele modelu (0, gdy model nie zwrócił żadnego) |
			| kierunek celów | cele wzorca z kierunkiem innym niż `not_expressed`, których dopasowany cel modelu ma ten sam kierunek / wszystkie takie cele — **niedopasowany cel wzorca = błąd** (decyzja z przeglądu fazy 5, F7) |
			| dominujący kierunek posta | posty, w których przewaga bullish/bearish modelu = wzorcowej / posty z przewagą we wzorcu |
			| sprzeczne kierunki | cele wzorca bullish/bearish, których dopasowany cel modelu ma kierunek przeciwny / cele wzorca bullish/bearish (niedopasowany = nie sprzeczny) |

			Koszt: tokeny wejścia nieobecne w cache × cena wejścia + tokeny z cache × cena cache + tokeny wyjścia × cena wyjścia; ceny za 1M tokenów z `models.json`.
			""";

}
