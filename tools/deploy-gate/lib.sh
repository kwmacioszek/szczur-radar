# Helpers shared by the deploy gate's scripts (S-14, safe-deploy-gate; see README.md). Sourced, never
# run: `. "$here/lib.sh"`. The sourcing script defines usage_error (exit 2) before it calls them.

# Durations, as in tools/pipeline-trigger/trigger.sh: a positive whole number with an optional unit s,
# m or h (a bare number is seconds). Sets REPLY to the seconds of the duration in the variable named $1.
duration_seconds() {
    [[ ${!1:-} =~ ^([1-9][0-9]*)([smh]?)$ ]] \
        || usage_error "$1 must be a duration such as 30s, 3m or 2h (it is '${!1:-}')"
    case ${BASH_REMATCH[2]} in
        h) REPLY=$(( BASH_REMATCH[1] * 3600 )) ;;
        m) REPLY=$(( BASH_REMATCH[1] * 60 )) ;;
        *) REPLY=${BASH_REMATCH[1]} ;;
    esac
}

# The web origin a script was given: an http(s) URL with a host and no path. Sets REPLY to it without a
# trailing slash.
web_origin() {
    [[ $1 =~ ^https?://[A-Za-z0-9.-]+(:[0-9]+)?/?$ ]] \
        || usage_error "the web address must be an http(s) origin such as https://szczur-radar-web-production.up.railway.app (it is '$1')"
    REPLY=${1%/}
}

# Between attempts while the API wakes or restarts (the same pause as tools/pipeline-trigger).
readonly WAKE_INTERVAL=2

# One GET of the URL $1: sets REPLY to the HTTP status (000: no answer), leaves the body in $HTTP_BODY
# and curl's error in $HTTP_ERROR (files the caller names). `--disable` first: no .curlrc is read.
http_get() {
    local out
    : >"$HTTP_BODY"
    out=$(curl --disable --silent --show-error --output "$HTTP_BODY" --write-out '%{http_code}' \
        --connect-timeout 10 --max-time 30 "$1" 2>"$HTTP_ERROR") || true
    REPLY=$out
    [[ $REPLY =~ ^[0-9]{3}$ && $REPLY != 000 ]] || REPLY=000
    return 0
}

# The last request's outcome for the log: curl's error, or the HTTP status.
http_outcome() {
    local text
    if [[ $REPLY == 000 ]]; then
        text=$(head -n 1 "$HTTP_ERROR")
        printf '%s\n' "${text:-no answer}"
    else
        printf 'HTTP %s\n' "$REPLY"
    fi
}
