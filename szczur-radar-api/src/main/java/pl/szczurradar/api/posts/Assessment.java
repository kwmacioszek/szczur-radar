package pl.szczurradar.api.posts;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;

import pl.szczurradar.api.agents.assessor.AssessmentResult;
import pl.szczurradar.api.agents.assessor.Provenance;
import pl.szczurradar.api.agents.assessor.contract.AssessmentInputV1;
import pl.szczurradar.api.agents.assessor.contract.AssessmentOutputV2;
import pl.szczurradar.api.agents.assessor.contract.ContractJson;
import pl.szczurradar.api.agents.assessor.validation.FailureClass;
import pl.szczurradar.api.agents.assessor.validation.Violation;
import pl.szczurradar.api.llm.Channel;
import pl.szczurradar.api.llm.OutputMode;
import pl.szczurradar.api.llm.Provider;
import pl.szczurradar.api.llm.ReasoningEffort;
import pl.szczurradar.api.llm.ServiceTier;
import pl.szczurradar.api.persistence.Sha256;
import pl.szczurradar.api.text.Nul;

/**
 * One assessment attempt of a post ({@code assessments}, owned by Flyway,
 * {@code V3__create_pipeline_and_posts.sql}), stored as the assessor returned it: the input sent, the
 * output (only {@code ok}), the violations, the model's raw text and the provenance (contract-v2.md
 * §15). Never changed after it is stored.
 *
 * <p>The JSON columns hold contract JSON written and read by {@link ContractJson#MAPPER} (strict and
 * independent of Boot's mapper), not by Hibernate's JSON mapping. U+0000, which the database rejects,
 * is stored as U+FFFD in the JSON and the raw text ({@link Nul}).
 *
 * <ul>
 *   <li>{@code jsonb} keeps the values but not the text: key order and white space are normalized, so
 *       {@link #getInputHash()} — the hash of the text sent — cannot be recomputed from the stored
 *       {@code input}.
 *   <li>Hibernate passes the {@code String} fields through unchanged, but it still resolves its JSON
 *       format mapper at start-up, and in Hibernate 7.2 that is Jackson 2 ({@code jackson-databind}
 *       2.x, today on the classpath through other dependencies). Without it the context fails.
 * </ul>
 */
@Entity
@Table(name = "assessments")
public class Assessment {

	private static final TypeReference<List<Violation>> VIOLATIONS = new TypeReference<>() {
	};

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "post_id", nullable = false, updatable = false)
	private UUID postId;

	@Column(name = "run_id", nullable = false, updatable = false)
	private UUID runId;

	@Column(nullable = false, updatable = false)
	private AssessmentResult.Status status;

	@Column(name = "failure_class", updatable = false)
	private @Nullable FailureClass failureClass;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false)
	private String input;

	@Column(name = "input_hash", nullable = false, updatable = false)
	private String inputHash;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false)
	private @Nullable String output;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false)
	private String violations;

	@Column(name = "raw_text", updatable = false)
	private @Nullable String rawText;

	@Column(name = "result_source", nullable = false, updatable = false)
	private Provenance.Source resultSource;

	@Column(updatable = false)
	private @Nullable Provider provider;

	@Column(updatable = false)
	private @Nullable String model;

	@Column(updatable = false)
	private @Nullable Channel channel;

	@Column(name = "service_tier", updatable = false)
	private @Nullable ServiceTier serviceTier;

	@Column(name = "flex_fallback", nullable = false, updatable = false)
	private boolean flexFallback;

	@Column(name = "reasoning_effort", updatable = false)
	private @Nullable ReasoningEffort reasoningEffort;

	@Column(name = "output_mode", updatable = false)
	private @Nullable OutputMode outputMode;

	@Column(name = "prompt_version", updatable = false)
	private @Nullable String promptVersion;

	@Column(name = "contract_version", nullable = false, updatable = false)
	private String contractVersion;

	@Column(name = "assessed_at", nullable = false, updatable = false)
	private Instant assessedAt;

	@Column(name = "input_tokens", updatable = false)
	private @Nullable Integer inputTokens;

	@Column(name = "output_tokens", updatable = false)
	private @Nullable Integer outputTokens;

	@Column(name = "cached_tokens", updatable = false)
	private @Nullable Integer cachedTokens;

	@Column(name = "duration_ms", nullable = false, updatable = false)
	private long durationMs;

	@Column(nullable = false, updatable = false)
	private int attempts;

	/** For JPA. */
	protected Assessment() {
	}

	/** The attempt of the run {@code runId} that assessed {@code input} of the post {@code postId}. */
	public Assessment(UUID postId, UUID runId, AssessmentInputV1 input, AssessmentResult result) {
		this.postId = Objects.requireNonNull(postId, "postId");
		this.runId = Objects.requireNonNull(runId, "runId");
		this.status = result.status();
		this.failureClass = result.failureClass();
		String sent = ContractJson.MAPPER.writeValueAsString(input);
		this.input = Nul.replacedInJson(sent);
		this.inputHash = Sha256.hex(sent);
		this.output = result.output() == null ? null
				: Nul.replacedInJson(ContractJson.MAPPER.writeValueAsString(result.output()));
		this.violations = Nul.replacedInJson(ContractJson.MAPPER.writeValueAsString(result.violations()));
		this.rawText = Nul.replaced(result.rawText());
		Provenance provenance = result.provenance();
		this.resultSource = provenance.source();
		this.provider = provenance.provider();
		this.model = provenance.model();
		this.channel = provenance.channel();
		this.serviceTier = provenance.serviceTier();
		this.flexFallback = provenance.flexFallback();
		this.reasoningEffort = provenance.reasoningEffort();
		this.outputMode = provenance.outputMode();
		this.promptVersion = provenance.promptVersion();
		this.contractVersion = provenance.contractVersion();
		this.assessedAt = provenance.assessedAt();
		this.inputTokens = provenance.inputTokens();
		this.outputTokens = provenance.outputTokens();
		this.cachedTokens = provenance.cachedTokens();
		this.durationMs = provenance.durationMs();
		this.attempts = provenance.attempts();
	}

	public UUID getId() {
		return id;
	}

	public UUID getPostId() {
		return postId;
	}

	public UUID getRunId() {
		return runId;
	}

	public AssessmentResult.Status getStatus() {
		return status;
	}

	public @Nullable FailureClass getFailureClass() {
		return failureClass;
	}

	/** The input as it was sent to the model. */
	public AssessmentInputV1 getInput() {
		return ContractJson.MAPPER.readValue(input, AssessmentInputV1.class);
	}

	/** Hex SHA-256 of the input's contract JSON as sent to the model (not of the normalized jsonb). */
	public String getInputHash() {
		return inputHash;
	}

	/** The valid output; {@code null} for a failed attempt. */
	public @Nullable AssessmentOutputV2 getOutput() {
		return output == null ? null : ContractJson.MAPPER.readValue(output, AssessmentOutputV2.class);
	}

	/** Every violation found, in order; empty for an {@code ok} attempt. */
	public List<Violation> getViolations() {
		return ContractJson.MAPPER.readValue(violations, VIOLATIONS);
	}

	public @Nullable String getRawText() {
		return rawText;
	}

	public Provenance getProvenance() {
		return new Provenance(resultSource, provider, model, channel, serviceTier, flexFallback, reasoningEffort,
				outputMode, promptVersion, contractVersion, assessedAt, inputTokens, outputTokens, cachedTokens,
				durationMs, attempts);
	}

}
