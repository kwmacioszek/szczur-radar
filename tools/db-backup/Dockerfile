# Database backup job (F-01, production-data-gate; see README.md): the Postgres 18 client and server
# for pg_dump and the restore check, rclone for the upload, jq for the manifest, curl for the check-ins
# to healthchecks.io (F-04, out-of-app-alert-channel). The same image runs as Railway's cron service
# `db-backup`, in test.sh and for a manual restore check of a downloaded dump.
# Same major version as production's Postgres (ghcr.io/railwayapp-templates/postgres-ssl:18).
FROM postgres:18

RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates curl jq rclone \
    && rm -rf /var/lib/apt/lists/*

# tables.sh (the table classification) is only sourced by the scripts next to it: read-only.
COPY backup.sh restore-check.sh nas-retention.sh tables.sh /usr/local/bin/
RUN chmod 0755 /usr/local/bin/backup.sh /usr/local/bin/restore-check.sh /usr/local/bin/nas-retention.sh \
    && chmod 0644 /usr/local/bin/tables.sh

# A job, not a server: no EXPOSE, no HEALTHCHECK. Without arguments the container runs one backup;
# `docker run <image> restore-check.sh …` (or nas-retention.sh) runs another tool of the image.
ENTRYPOINT ["backup.sh"]
CMD []
