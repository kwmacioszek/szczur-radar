-- In-app notifications (S-06): one row per recipient, each with its own read time. Today one kind: a
-- pipeline run that needs attention (run_attention, pipeline/run/PipelineRun.needsAttention) gives one to
-- every admin who can log in. The unique key makes creating them idempotent: every way a run ends reports
-- it, some of them twice. Other kinds, and kinds about no run, may follow.
-- Fix-forward: never edit a migration once deployed; correct it with a new one.
CREATE TABLE notifications (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    recipient_id UUID        NOT NULL,
    kind         TEXT        NOT NULL,
    run_id       UUID        NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- when the recipient read it; NULL while unread
    read_at      TIMESTAMPTZ NULL,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_run FOREIGN KEY (run_id) REFERENCES pipeline_runs (id),
    CONSTRAINT ck_notifications_kind CHECK (kind IN ('run_attention')),
    CONSTRAINT ck_notifications_run CHECK (kind <> 'run_attention' OR run_id IS NOT NULL),
    CONSTRAINT uq_notifications_recipient_kind_run UNIQUE (recipient_id, kind, run_id)
);

-- A recipient's newest notifications (the list), and the unread among them.
CREATE INDEX ix_notifications_recipient_created ON notifications (recipient_id, created_at DESC, id DESC);
