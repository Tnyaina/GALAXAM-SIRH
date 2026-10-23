#!/bin/sh
set -eu
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TMP=$(/usr/bin/mktemp -d)
trap '/bin/rm -rf "$TMP"' EXIT
HOME_DIR="$TMP/GALAXAM SIRH"
VERSION_DIR="$TMP/version"
BIN="$VERSION_DIR/postgresql/bin"
CLUSTER="$HOME_DIR/data/postgresql/cluster"
CONFIG="$HOME_DIR/data/postgresql/config"
ID=0123456789abcdef0123456789abcdef0123456789abcdef
/bin/mkdir -p "$BIN" "$CLUSTER" "$CONFIG" "$HOME_DIR/backups" "$HOME_DIR/documents" "$HOME_DIR/logs"
/bin/echo 15 > "$CLUSTER/PG_VERSION"
/bin/echo "$ID" > "$CONFIG/installation.id"
/usr/bin/printf 'admin=secret\napp=secret\n' > "$CONFIG/installation.secrets"
/bin/echo document > "$HOME_DIR/documents/example.txt"
/bin/cat > "$BIN/pg_ctl" <<'EOF'
#!/bin/sh
exit 0
EOF
/bin/cat > "$BIN/psql" <<'EOF'
#!/bin/sh
/usr/bin/printf '%s\n' "$MOCK_CLUSTER"
EOF
/bin/cat > "$BIN/pg_dump" <<'EOF'
#!/bin/sh
[ "${MOCK_DUMP_FAIL:-0}" = 0 ] || exit 1
while [ "$#" -gt 0 ]; do
  if [ "$1" = -f ]; then shift; /bin/echo mock-dump > "$1"; exit 0; fi
  shift
done
exit 1
EOF
/bin/cat > "$BIN/pg_restore" <<'EOF'
#!/bin/sh
exit 0
EOF
/bin/chmod 700 "$BIN"/*
export MOCK_CLUSTER="$CLUSTER"
SCRIPT="$ROOT/desktop/src-tauri/src/platform/macos/auto_backup.sh"
/bin/sh "$SCRIPT" "$HOME_DIR" "$VERSION_DIR" "$ID" 15432
ARCHIVE=$(/usr/bin/find "$HOME_DIR/backups" -name 'galaxam-auto-*.tar' -type f -print)
[ -n "$ARCHIVE" ]
/usr/bin/tar -tf "$ARCHIVE" | /usr/bin/grep -q '^database.dump$'
/usr/bin/tar -tf "$ARCHIVE" | /usr/bin/grep -q '^documents/example.txt$'
/bin/sh "$SCRIPT" "$HOME_DIR" "$VERSION_DIR" "$ID" 15432
[ "$(/usr/bin/find "$HOME_DIR/backups" -name 'galaxam-auto-*.tar' -type f | /usr/bin/wc -l | /usr/bin/tr -d ' ')" = 1 ]
/usr/bin/printf '%s\n' 0 > "$HOME_DIR/backups/.automatic-last-success"
if [ -L "$HOME_DIR/documents/link" ]; then /bin/rm "$HOME_DIR/documents/link"; fi
/bin/ln -s /etc/passwd "$HOME_DIR/documents/link"
if /bin/sh "$SCRIPT" "$HOME_DIR" "$VERSION_DIR" "$ID" 15432; then
  /bin/echo 'expected special document file rejection' >&2; exit 1
fi
/bin/rm "$HOME_DIR/documents/link"
if MOCK_DUMP_FAIL=1 /bin/sh "$SCRIPT" "$HOME_DIR" "$VERSION_DIR" "$ID" 15432; then
  /bin/echo 'expected pg_dump failure' >&2; exit 1
fi
[ "$(/usr/bin/find "$HOME_DIR/backups" -name '*.pending' -o -name '.automatic.*' -type d | /usr/bin/wc -l | /usr/bin/tr -d ' ')" = 0 ]
[ "$(/bin/cat "$HOME_DIR/backups/.automatic-last-success")" = 0 ]
for N in 1 2 3 4 5 6 7 8; do /bin/echo old > "$HOME_DIR/backups/galaxam-auto-2020010${N}T000000Z.tar"; done
/bin/sh "$SCRIPT" "$HOME_DIR" "$VERSION_DIR" "$ID" 15432
[ "$(/usr/bin/find "$HOME_DIR/backups" -name 'galaxam-auto-*.tar' -type f | /usr/bin/wc -l | /usr/bin/tr -d ' ')" = 7 ]
/bin/echo 'auto backup smoke: OK'
