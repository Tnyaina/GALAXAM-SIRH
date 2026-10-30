#!/bin/sh
# Remise a zero explicite de la base PROD Docker du Mac.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"
for name in SIRH_PROD_DB_PASSWORD SIRH_PROD_JWT_SECRET SIRH_PROD_WEB_PORT SIRH_PROD_CORS_ORIGIN SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED SIRH_ADMIN_NAME SIRH_ADMIN_EMAIL SIRH_ADMIN_PASSWORD; do
  printenv "$name" >/dev/null || { echo "Variable absente : $name" >&2; exit 1; }
done
compose() { docker compose --project-directory "$root" -f "$root/docker-compose.web.yml" "$@"; }
compose config --quiet
compose config --format json | python3 -c 'import json,sys; c=json.load(sys.stdin); assert c["name"]=="galaxam-sirh-prod"; assert c["volumes"]["pg_data"]["name"]=="galaxam_prod_pg_data"; assert c["volumes"]["business_files"]["name"]=="galaxam_prod_business_files"' || { echo 'Identite Docker PROD inattendue.' >&2; exit 1; }
[ "$SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED" = true ] || { echo 'Activer SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED=true pour recreer le premier administrateur.' >&2; exit 1; }
pg=$(compose ps -q postgres)
[ -n "$pg" ] || { echo 'PostgreSQL PROD non demarre.' >&2; exit 1; }
printf 'La base PROD sirh_galaxam sera EFFACEE. Tapez exactement sirh_galaxam : '
read -r answer
[ "$answer" = sirh_galaxam ] || { echo 'Annule.' >&2; exit 1; }
mkdir -p "$root/local-prod-backups"
stamp=$(date +%Y%m%d-%H%M%S)
archive="$root/local-prod-backups/galaxam-prod-before-reset-$stamp.tar"
temp=$(mktemp -d)
reset_started=0
completed=0
cleanup() {
  if [ "$reset_started" -eq 0 ] && [ "$completed" -eq 0 ]; then compose start backend frontend >/dev/null 2>&1 || true; fi
  if [ "$reset_started" -eq 1 ] && [ "$completed" -eq 0 ]; then
    compose stop frontend backend >/dev/null 2>&1 || true
    echo "Remise a zero incomplete ; web arrete. Sauvegarde : $archive" >&2
  fi
  rm -rf "$temp"
}
trap cleanup EXIT HUP INT TERM
compose stop frontend backend
docker exec "$pg" pg_dump -U galaxam_admin -d sirh_galaxam -Fc > "$temp/database.dump"
docker exec -i "$pg" pg_restore --list < "$temp/database.dump" > /dev/null
docker run --rm -v galaxam_prod_business_files:/data:ro -v "$temp:/backup" alpine:3.20 tar -cf /backup/uploads.tar -C /data .
python3 - "$temp" <<'PY'
import hashlib, json, pathlib, sys
p = pathlib.Path(sys.argv[1])
(p / 'manifest.json').write_text(json.dumps({
    'format': 'galaxam-prod-v1',
    'databaseSha256': hashlib.sha256((p / 'database.dump').read_bytes()).hexdigest(),
    'uploadsSha256': hashlib.sha256((p / 'uploads.tar').read_bytes()).hexdigest(),
}, indent=2))
PY
tar -cf "$archive.partial" -C "$temp" database.dump uploads.tar manifest.json
mv "$archive.partial" "$archive"
echo "Sauvegarde avant reset : $archive"
reset_started=1
compose run --rm --no-deps --entrypoint psql migrate -X -v ON_ERROR_STOP=1 --single-transaction -f /reset-prod.sql
compose run --rm --no-deps migrate
compose up -d --no-deps --wait backend frontend
completed=1
echo 'Base PROD reinitialisee, migrations appliquees, application prete.'
