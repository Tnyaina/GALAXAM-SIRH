#!/bin/sh
set -eu
. "$(dirname "$0")/common.sh"
assert_test_stack
mkdir -p "$root/local-prod-test-backups"
temp=$(mktemp -d)
helper="galaxam-mac-prodtest-files-$$"
stopped=0
cleanup() {
  docker rm -f "$helper" >/dev/null 2>&1 || true
  [ -z "${pg:-}" ] || docker exec "$pg" rm -f /tmp/galaxam-prodtest.dump >/dev/null 2>&1 || true
  if [ "$stopped" -eq 1 ]; then
    compose up -d --no-deps backend >/dev/null
    compose up -d --no-deps --force-recreate frontend >/dev/null
  fi
  rm -rf "$temp"
}
trap cleanup EXIT INT TERM
compose stop frontend backend >/dev/null
stopped=1
pg=$(container_id postgres)
backend=$(container_id backend)
[ -n "$pg" ] && [ -n "$backend" ] || { echo 'Conteneur absent.' >&2; exit 1; }
docker exec "$pg" pg_dump -U galaxam_admin -d sirh_galaxam -Fc -f /tmp/galaxam-prodtest.dump
docker exec "$pg" pg_restore --list /tmp/galaxam-prodtest.dump >/dev/null
docker cp "$pg:/tmp/galaxam-prodtest.dump" "$temp/database.dump"
image=$(docker inspect --format '{{.Image}}' "$pg")
docker create --name "$helper" --volumes-from "$backend:ro" --entrypoint sh "$image" -c 'tar -cf /tmp/uploads.tar -C /app/uploads .' >/dev/null
docker start -a "$helper" >/dev/null
[ "$(docker inspect --format '{{.State.ExitCode}}' "$helper")" = 0 ] || { echo 'Documents non archives.' >&2; exit 1; }
docker cp "$helper:/tmp/uploads.tar" "$temp/uploads.tar"
python3 - "$temp" <<'PY'
import hashlib,json,pathlib,sys,datetime
p=pathlib.Path(sys.argv[1])
sha=lambda f: hashlib.sha256((p/f).read_bytes()).hexdigest()
(p/'manifest.json').write_text(json.dumps({'format':'galaxam-mac-prodtest-v1','createdAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'databaseSha256':sha('database.dump'),'uploadsSha256':sha('uploads.tar')},indent=2))
PY
archive="$root/local-prod-test-backups/galaxam-mac-prodtest-$(date +%Y%m%d-%H%M%S)-$$.tar"
tar -cf "$archive.partial" -C "$temp" database.dump uploads.tar manifest.json
mv "$archive.partial" "$archive"
cleanup
trap - EXIT INT TERM
wait_web
printf 'Sauvegarde : %s\n' "$archive"
