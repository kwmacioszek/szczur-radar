# Daily pipeline trigger (S-05, daily-scheduled-run-freshness; see README.md): bash, curl and jq, and
# coreutils for GNU `timeout`. The same image runs as Railway's cron service `pipeline-trigger` and in
# test.sh. The API is called over the private network with plain http, so no certificates are needed.
FROM alpine:3.24

RUN apk add --no-cache bash coreutils curl jq \
    && adduser -D -H -S trigger

COPY trigger.sh /usr/local/bin/
RUN chmod 0755 /usr/local/bin/trigger.sh

# The job only makes HTTP requests: no root.
USER trigger

# A job, not a server: no EXPOSE, no HEALTHCHECK. The container runs one trigger and exits with its
# result (README.md, exit codes).
ENTRYPOINT ["trigger.sh"]
