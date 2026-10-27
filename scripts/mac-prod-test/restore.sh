#!/bin/sh
set -eu
. "$(dirname "$0")/common.sh"
assert_test_stack
archive=${1:?Donner le chemin absolu de l archive de recette.}
case "$archive" in "$root"/local-prod-test-backups/galaxam-mac-prodtest-*.tar) ;; *) echo 'Archive hors de la recette.' >&2; exit 1;; esac
[ -f "$archive" ] || { echo 'Archive introuvable.' >&2; exit 1; }
temp=$(mktemp -d)
helper="galaxam-mac-prodtest-restore-$$"
stopped=0
cleanup() {
  docker rm -f "$helper" >/dev/null 2>&1 || true
  [ -z "${pg:-}" ] || docker exec "$pg" rm -f /tmp/galaxam-prodtest-restore.dump >/dev/null 2>&1 || true
  rm -rf "$temp"
}
trap cleanup EXIT INT TERM
tar -xf "$archive" -C "$temp" database.dump uploads.tar manifest.json
python3 - "$temp" <<'PY'
import hashlib,json,pathlib,sys
p=pathlib.Path(sys.argv[1]); m=json.loads((p/'manifest.json').read_text())
assert m['format']=='galaxam-mac-prodtest-v1'
for name,key in [('database.dump','databaseSha256'),('uploads.tar','uploadsSha256')]:
 assert hashlib.sha256((p/name).read_bytes()).hexdigest()==m[key], name
PY
printf 'La restauration remplace les données de recette. Taper RESTAURER : '
read -r answer
[ "$answer" = RESTAURER ] || exit 1
# Une copie de sécurité avant chaque restauration, même sur la recette.
"$root/scripts/mac-prod-test/backup.sh" >/dev/null
compose stop frontend backend >/dev/null
stopped=1
pg=$(container_id postgres)
backend=$(container_id backend)
docker cp "$temp/database.dump" "$pg:/tmp/galaxam-prodtest-restore.dump"
docker exec "$pg" pg_restore --list /tmp/galaxam-prodtest-restore.dump >/dev/null
docker exec "$pg" dropdb -U galaxam_admin --force sirh_galaxam
docker exec "$pg" createdb -U galaxam_admin -O galaxam_admin sirh_galaxam
docker exec "$pg" pg_restore -U galaxam_admin -d sirh_galaxam --single-transaction --no-owner --exit-on-error /tmp/galaxam-prodtest-restore.dump
image=$(docker inspect --format '{{.Image}}' "$pg")
docker create --name "$helper" --volumes-from "$backend" --entrypoint sh "$image" -c 'set -eu; tar -tf /tmp/uploads.tar >/dev/null; find /app/uploads -mindepth 1 -maxdepth 1 -exec rm -rf {} \;; tar -xf /tmp/uploads.tar -C /app/uploads' >/dev/null
docker cp "$temp/uploads.tar" "$helper:/tmp/uploads.tar"
docker start -a "$helper" >/dev/null
[ "$(docker inspect --format '{{.State.ExitCode}}' "$helper")" = 0 ] || { echo 'Echec de restauration des documents ; application maintenue arrêtée.' >&2; exit 1; }
compose up -d --no-deps backend >/dev/null
compose up -d --no-deps --force-recreate frontend >/dev/null
stopped=0
cleanup
trap - EXIT INT TERM
wait_web
printf 'Restauration de recette terminée.\n'
