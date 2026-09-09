"""Execute retry behavior in Bash with fake download commands; no network or SDK needed."""
from pathlib import Path
import os, shutil, subprocess, tempfile
root = Path(__file__).resolve().parent.parent
bash = "C:/Program Files/Git/bin/bash.exe" if os.name == 'nt' else shutil.which('bash')
assert bash
with tempfile.TemporaryDirectory(prefix='nebula-retry-') as directory:
    temp = Path(directory)
    shutil.copyfile(root/'scripts/retry-network.sh', temp/'retry-network.sh')
    (temp/'test.sh').write_bytes(r'''set -euo pipefail
source ./retry-network.sh
sleep() { :; }
count=0
transient() {
  count=$((count + 1))
  if [ "$count" -lt 3 ]; then echo 'stream error: stream ID 245; INTERNAL_ERROR; received from peer' >&2; return 7; fi
  [ "${GODEBUG:-}" = 'http2client=0,http2client=0' ]
}
retry_network transient
[ "$count" -eq 3 ]
count=0
compiler_error() { count=$((count + 1)); echo 'undefined: missingSymbol'; return 9; }
if retry_network compiler_error; then exit 1; else [ "$?" -eq 9 ]; fi
[ "$count" -eq 1 ]
count=0
checksum_error() { count=$((count + 1)); echo 'SECURITY ERROR checksum mismatch unexpected EOF'; return 10; }
if retry_network checksum_error; then exit 1; else [ "$?" -eq 10 ]; fi
[ "$count" -eq 1 ]
count=0
outage() { count=$((count + 1)); echo '503 Service Unavailable'; return 11; }
if retry_network outage; then exit 1; else [ "$?" -eq 11 ]; fi
[ "$count" -eq 3 ]
count=0
success() { count=$((count + 1)); return 0; }
retry_network success
[ "$count" -eq 1 ]
'''.encode())
    subprocess.run([bash,'test.sh'],cwd=temp,check=True)
script = (root/'scripts/build-core.sh').read_text(encoding='utf-8')
assert '@latest' not in '\n'.join(line for line in script.splitlines() if not line.lstrip().startswith('#'))
assert 'retry_network gomobile bind' in script
assert 'go mod verify' in script and 'golang.org/x/mobile/cmd/gobind' in script
assert 'GOSUMDB=off' not in script and 'GONOSUMDB=*' not in script
workflow = (root/'.github/workflows/android.yml').read_text(encoding='utf-8')
assert "go-version-file: 'bind/go.mod'" in workflow
print('5 retry scenarios passed; pinned Go tooling, bounded retries and checksum enforcement verified')
