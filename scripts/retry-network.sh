#!/usr/bin/env bash
# Retry only transport failures. Never bypass Go checksum verification or hide compiler errors.
retry_network() {
  local attempt status log
  log="$(mktemp)" || return 1
  for attempt in 1 2 3; do
    if "$@" >"$log" 2>&1; then
      cat "$log"; rm -f -- "$log"; return 0
    else
      status=$?
    fi
    cat "$log" >&2
    # Integrity and source errors must fail immediately, even if a network phrase is present.
    if grep -Eqi 'checksum mismatch|SECURITY ERROR|syntax error|undefined:|cannot use .* as' "$log" ||
       ! grep -Eqi 'stream error:.*INTERNAL_ERROR|unexpected EOF|TLS handshake timeout|i/o timeout|connection reset|connection refused|temporary failure in name resolution|no such host|(^|[ :])(502 Bad Gateway|503 Service Unavailable|504 Gateway Timeout)' "$log" ||
       [ "$attempt" -eq 3 ]; then
      rm -f -- "$log"; return "$status"
    fi
    echo "Transient dependency transport failure; retry $((attempt + 1))/3" >&2
    # A flaky HTTP/2 connection must not be reused for the next download. TLS and sumdb stay enabled.
    if grep -Eqi 'stream error:.*INTERNAL_ERROR' "$log"; then
      export GODEBUG="${GODEBUG:+$GODEBUG,}http2client=0"
    fi
    sleep "$((attempt * 5))"
  done
}
