-- Pipeline runs and their steps, posts with their raw payloads, and every assessment attempt (S-02).
-- Closed value sets are TEXT + CHECK with the Java enums' lower-case values.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.

-- One run of the pipeline: a date range of Wykop (range), the leftovers of earlier runs (retry) or
-- posts picked by an admin (posts). Counters are written while the run is running.
CREATE TABLE pipeline_runs (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    kind               TEXT        NOT NULL,
    status             TEXT        NOT NULL DEFAULT 'queued',
    range_from         TIMESTAMPTZ NULL,
    range_to           TIMESTAMPTZ NULL,
    requested_post_ids UUID[]      NULL,
    requested_by       UUID        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at         TIMESTAMPTZ NULL,
    finished_at        TIMESTAMPTZ NULL,
    fetched            INTEGER     NOT NULL DEFAULT 0,
    new_posts          INTEGER     NOT NULL DEFAULT 0,
    assessed_ok        INTEGER     NOT NULL DEFAULT 0,
    assessed_failed    INTEGER     NOT NULL DEFAULT 0,
    -- posts with a technical error for which no assessment was stored (not failed LLM attempts)
    processing_errors  INTEGER     NOT NULL DEFAULT 0,
    dead               INTEGER     NOT NULL DEFAULT 0,
    skipped            INTEGER     NOT NULL DEFAULT 0,
    error              TEXT        NULL,
    CONSTRAINT pipeline_runs_pkey PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_runs_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT ck_pipeline_runs_kind CHECK (kind IN ('range', 'retry', 'posts')),
    CONSTRAINT ck_pipeline_runs_status
        CHECK (status IN ('queued', 'running', 'succeeded', 'partial', 'failed', 'interrupted')),
    CONSTRAINT ck_pipeline_runs_range
        CHECK ((kind = 'range') = (range_from IS NOT NULL AND range_to IS NOT NULL)
            AND (range_from IS NULL OR range_from <= range_to)),
    CONSTRAINT ck_pipeline_runs_requested_posts CHECK ((kind = 'posts') = (requested_post_ids IS NOT NULL))
);

-- At most one run is running (the in-process queue runs one at a time; second line of defence).
CREATE UNIQUE INDEX uq_pipeline_runs_one_running ON pipeline_runs (status) WHERE status = 'running';
CREATE INDEX ix_pipeline_runs_created ON pipeline_runs (created_at DESC, id DESC);

-- The steps of a run as they happen; items_in/items_out are the step's own counts.
CREATE TABLE pipeline_run_steps (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    run_id      UUID        NOT NULL,
    step        TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'running',
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ NULL,
    items_in    INTEGER     NULL,
    items_out   INTEGER     NULL,
    error       TEXT        NULL,
    CONSTRAINT pipeline_run_steps_pkey PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_run_steps_run FOREIGN KEY (run_id) REFERENCES pipeline_runs (id),
    CONSTRAINT ck_pipeline_run_steps_step CHECK (step IN ('fetch', 'store', 'select', 'assess', 'summarize')),
    CONSTRAINT ck_pipeline_run_steps_status CHECK (status IN ('running', 'succeeded', 'failed'))
);

CREATE INDEX ix_pipeline_run_steps_run ON pipeline_run_steps (run_id, started_at, id);

-- Raw source payloads, apart from the application data (context/foundation/lessons.md, RAW vs.
-- operational data): the exact JSON bytes, gzip-compressed in the application (TOAST does not
-- compress rows under ~2 KB), behind raw/RawPayloadStore so the backend can move later. No content
-- indexes: the table is outside every list query.
CREATE TABLE raw_payloads (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    source           TEXT        NOT NULL,
    content_encoding TEXT        NOT NULL,
    media_type       TEXT        NOT NULL,
    payload          BYTEA       NOT NULL,
    -- hex SHA-256 of the uncompressed bytes
    sha256           TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT raw_payloads_pkey PRIMARY KEY (id),
    CONSTRAINT ck_raw_payloads_source CHECK (source IN ('wykop')),
    CONSTRAINT ck_raw_payloads_content_encoding CHECK (content_encoding IN ('gzip')),
    CONSTRAINT ck_raw_payloads_media_type CHECK (media_type IN ('application/json')),
    CONSTRAINT ck_raw_payloads_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

-- A post of a source, once per (source, resource, source_item_id). stage and assessment_attempts are
-- the post's progress: an attempt counts only when a failed assessment was stored, the third makes the
-- post dead; assessing_run_id is the run that holds an assessing post.
CREATE TABLE posts (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    source              TEXT        NOT NULL,
    resource            TEXT        NOT NULL,
    source_item_id      TEXT        NOT NULL,
    published_at        TIMESTAMPTZ NOT NULL,
    title               TEXT        NULL,
    body                TEXT        NULL,
    author              TEXT        NULL,
    slug                TEXT        NULL,
    web_url             TEXT        NULL,
    tags                TEXT[]      NOT NULL DEFAULT '{}',
    raw_payload_id      UUID        NOT NULL,
    -- hex SHA-256 of the post's text (title and body), to notice an edited post
    content_hash        TEXT        NOT NULL,
    stage               TEXT        NOT NULL DEFAULT 'stored',
    assessment_attempts INTEGER     NOT NULL DEFAULT 0,
    assessing_run_id    UUID        NULL,
    first_run_id        UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT posts_pkey PRIMARY KEY (id),
    CONSTRAINT uq_posts_source_item UNIQUE (source, resource, source_item_id),
    CONSTRAINT uq_posts_raw_payload UNIQUE (raw_payload_id),
    CONSTRAINT fk_posts_raw_payload FOREIGN KEY (raw_payload_id) REFERENCES raw_payloads (id),
    CONSTRAINT fk_posts_assessing_run FOREIGN KEY (assessing_run_id) REFERENCES pipeline_runs (id),
    CONSTRAINT fk_posts_first_run FOREIGN KEY (first_run_id) REFERENCES pipeline_runs (id),
    CONSTRAINT ck_posts_source CHECK (source IN ('wykop')),
    CONSTRAINT ck_posts_resource CHECK (resource IN ('entry', 'link')),
    CONSTRAINT ck_posts_content_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_posts_stage CHECK (stage IN ('stored', 'assessing', 'assessed', 'failed', 'dead')),
    -- stored: never failed; failed: 1–2 failed attempts; dead: 3; assessing/assessed: any of 0–2
    CONSTRAINT ck_posts_stage_attempts CHECK (
        assessment_attempts BETWEEN 0 AND 3
        AND (stage <> 'stored' OR assessment_attempts = 0)
        AND (stage <> 'failed' OR assessment_attempts BETWEEN 1 AND 2)
        AND (stage <> 'dead' OR assessment_attempts = 3)
        AND (stage NOT IN ('assessing', 'assessed') OR assessment_attempts <= 2)),
    CONSTRAINT ck_posts_assessing_owner CHECK ((stage = 'assessing') = (assessing_run_id IS NOT NULL))
);

CREATE INDEX ix_posts_stage_published ON posts (stage, published_at DESC, id DESC);

-- Every assessment attempt of a post: the input sent, the output (only ok), the violations, the
-- model's raw text and the provenance (contract-v2.md §15). The latest attempt of a post is the one
-- with the latest assessed_at, then the greatest id (an explicit tie rule).
CREATE TABLE assessments (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    post_id          UUID        NOT NULL,
    run_id           UUID        NOT NULL,
    status           TEXT        NOT NULL,
    failure_class    TEXT        NULL,
    input            JSONB       NOT NULL,
    -- hex SHA-256 of the input's contract JSON as sent
    input_hash       TEXT        NOT NULL,
    output           JSONB       NULL,
    violations       JSONB       NOT NULL,
    raw_text         TEXT        NULL,
    result_source    TEXT        NOT NULL,
    provider         TEXT        NULL,
    model            TEXT        NULL,
    channel          TEXT        NULL,
    service_tier     TEXT        NULL,
    flex_fallback    BOOLEAN     NOT NULL,
    reasoning_effort TEXT        NULL,
    output_mode      TEXT        NULL,
    prompt_version   TEXT        NULL,
    contract_version TEXT        NOT NULL,
    assessed_at      TIMESTAMPTZ NOT NULL,
    input_tokens     INTEGER     NULL,
    output_tokens    INTEGER     NULL,
    cached_tokens    INTEGER     NULL,
    duration_ms      BIGINT      NOT NULL,
    attempts         INTEGER     NOT NULL,
    CONSTRAINT assessments_pkey PRIMARY KEY (id),
    CONSTRAINT fk_assessments_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT fk_assessments_run FOREIGN KEY (run_id) REFERENCES pipeline_runs (id),
    CONSTRAINT ck_assessments_status CHECK (status IN ('ok', 'failed')),
    CONSTRAINT ck_assessments_failure_class CHECK (failure_class IN ('provider_error', 'timeout',
        'invalid_structured_output', 'schema_violation', 'invalid_reference', 'fabricated_evidence',
        'domain_invariant_violation')),
    CONSTRAINT ck_assessments_outcome CHECK (
        (status = 'ok' AND output IS NOT NULL AND failure_class IS NULL)
        OR (status = 'failed' AND output IS NULL AND failure_class IS NOT NULL)),
    CONSTRAINT ck_assessments_input_hash CHECK (input_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_assessments_result_source CHECK (result_source IN ('model', 'prefilter')),
    CONSTRAINT ck_assessments_provider CHECK (provider IN ('gemini', 'deepseek', 'openrouter')),
    CONSTRAINT ck_assessments_channel CHECK (channel IN ('gemini', 'deepseek', 'openrouter', 'openai/flex', 'openai')),
    CONSTRAINT ck_assessments_service_tier CHECK (service_tier IN ('default', 'flex')),
    CONSTRAINT ck_assessments_reasoning_effort
        CHECK (reasoning_effort IN ('none', 'minimal', 'low', 'medium', 'high', 'xhigh')),
    CONSTRAINT ck_assessments_output_mode CHECK (output_mode IN ('json_schema', 'json_mode'))
);

CREATE INDEX ix_assessments_post_latest ON assessments (post_id, assessed_at DESC, id DESC);
