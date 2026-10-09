package pl.szczurradar.api.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Assessor;
import pl.szczurradar.api.agents.assessor.Prefilter;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.config.LlmProviderProperties;
import pl.szczurradar.api.llm.LlmClientFactory;
import pl.szczurradar.api.sources.wykop.WykopItem;
import pl.szczurradar.api.sources.wykop.WykopMapper;

/**
 * Live check of one provider before the gold measurement (plan phase 4): the first
 * {@value #ASSESSED} assessable posts of the pilot sample of 2026-09-29, assessed through
 * {@link Assessor} with the client built from {@code LLM_*} in the shell. Prints the denominators,
 * contract compliance, failure classes, tokens, median time and retries; writes nothing to the repo.
 *
 * <p>{@code ./mvnw -Pmeasurement test -Dtest=LiveSmokeTest} (see {@code README.md} here). The sample
 * path can be changed with {@code -Dsmoke.sample=<path>}.
 */
@Tag("measurement")
class LiveSmokeTest {

	static final int ASSESSED = 30;

	static final String DEFAULT_SAMPLE = "../context/archive/2026-09-27-assessment-contract-harness/pilot/wykop-gielda-sample-2026-09-29.json";

	/** The pilot sample's record: a Wykop entry or link, anonymized. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record SampleRecord(@JsonProperty("sample_id") String sampleId, String resource,
			@JsonProperty("created_at") String createdAt, @Nullable String title, @Nullable String body) {
	}

	@Test
	void assessesThePilotSample() throws IOException {
		LlmProviderProperties properties = LlmProviderProperties.from(new StandardEnvironment());
		Assessor assessor = new Assessor(LlmClientFactory.create(properties), properties.provider(), properties.model(),
				Clock.systemUTC());
		List<SampleRecord> sample = JsonMapper.shared()
			.readValue(Files.readString(Path.of(System.getProperty("smoke.sample", DEFAULT_SAMPLE))),
					new TypeReference<List<SampleRecord>>() {
					});

		int inputs = 0;
		int prefiltered = 0;
		List<AssessmentResult> calls = new ArrayList<>();
		System.out.println("Live smoke: " + properties);
		for (SampleRecord record : sample) {
			if (calls.size() == ASSESSED) {
				break;
			}
			inputs++;
			AssessmentInputV1 input = input(record);
			if (!Prefilter.isAssessable(input)) {
				prefiltered++;
				continue;
			}
			AssessmentResult result = assessor.assess(input);
			calls.add(result);
			System.out.printf("%s %-6s %-26s %5d ms, %d attempt(s)%s%n", record.sampleId(), result.status().value(),
					result.failureClass() == null ? "" : result.failureClass().value(),
					result.provenance().durationMs(), result.provenance().attempts(),
					result.provenance().flexFallback() ? ", flex fallback" : "");
			for (Violation violation : result.violations()) {
				System.out.printf("    %s %s %s: %s%n", violation.failureClass().value(), violation.rule(),
						violation.path(), violation.message());
			}
		}

		report(properties, inputs, prefiltered, calls);
		assertThat(calls).as("assessable posts in the sample").hasSize(ASSESSED);
	}

	/** A pilot record in the API's field names, mapped by {@link WykopMapper} like a live element. */
	static AssessmentInputV1 input(SampleRecord record) {
		boolean entry = "entry".equals(record.resource());
		return WykopMapper.map(new WykopItem(record.resource(), null, record.createdAt(), entry ? record.body() : null,
				entry ? null : record.title(), entry ? null : record.body(), null, null, null, null), "gielda");
	}

	private static void report(LlmProviderProperties properties, int inputs, int prefiltered,
			List<AssessmentResult> calls) {
		long ok = calls.stream().filter(AssessmentResult::ok).count();
		Map<FailureClass, Integer> failures = new EnumMap<>(FailureClass.class);
		Map<String, Integer> shapeRules = new TreeMap<>();
		for (AssessmentResult result : calls) {
			if (result.failureClass() != null) {
				failures.merge(result.failureClass(), 1, Integer::sum);
			}
			result.violations()
				.stream()
				.filter(violation -> violation.failureClass() == FailureClass.SCHEMA_VIOLATION)
				.forEach(violation -> shapeRules.merge(violation.rule(), 1, Integer::sum));
		}
		List<Long> durations = calls.stream().map(result -> result.provenance().durationMs()).sorted().toList();
		int retries = calls.stream().mapToInt(result -> result.provenance().attempts() - 1).sum();
		long retried = calls.stream().filter(result -> result.provenance().attempts() > 1).count();
		long fallbacks = calls.stream().filter(result -> result.provenance().flexFallback()).count();

		System.out.println();
		System.out.println("== Live smoke summary: " + properties.provider().id() + " " + properties.model()
				+ " tier=" + properties.serviceTier().id());
		System.out.println("inputs read:              " + inputs);
		System.out.println("prefilter (no content):   " + prefiltered);
		System.out.println("model calls (denominator): " + calls.size());
		System.out.printf("contract compliance:      %d/%d = %.1f%%%n", ok, calls.size(), percent(ok, calls.size()));
		System.out.println("failure classes:          " + failures);
		System.out.println("schema rules violated:    " + shapeRules
				+ " (N1/N2: schema:additionalProperties, schema:uniqueItems)");
		System.out.println("models reported:          " + calls.stream()
			.map(result -> result.provenance().model())
			.filter(Objects::nonNull)
			.distinct()
			.toList());
		System.out.println("tokens input/output/cached: " + sum(calls, Provenance::inputTokens) + " / "
				+ sum(calls, Provenance::outputTokens) + " / " + sum(calls, Provenance::cachedTokens)
				+ "  (calls reporting cache: "
				+ calls.stream().filter(result -> result.provenance().cachedTokens() != null).count() + ")");
		System.out.println("median duration:          " + (durations.isEmpty() ? "-" : median(durations) + " ms"));
		System.out.println("retried calls / retries:  " + retried + " / " + retries
				+ " (429, 5xx, time-out or connection error)");
		System.out.printf("flex fallbacks:           %d/%d = %.1f%% (each = 3 consecutive 429 refusals of flex)%n",
				fallbacks, calls.size(), percent(fallbacks, calls.size()));
		System.out.println("prompt / contract:        " + calls.stream()
			.map(result -> result.provenance().promptVersion() + " / " + result.provenance().contractVersion())
			.distinct()
			.toList());
	}

	private static int sum(List<AssessmentResult> calls, Function<Provenance, @Nullable Integer> field) {
		return calls.stream()
			.map(result -> field.apply(result.provenance()))
			.filter(Objects::nonNull)
			.mapToInt(Integer::intValue)
			.sum();
	}

	private static long median(List<Long> sorted) {
		int middle = sorted.size() / 2;
		return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
	}

	private static double percent(long part, int whole) {
		return whole == 0 ? 0 : 100.0 * part / whole;
	}

}
