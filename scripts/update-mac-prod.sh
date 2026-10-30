#!/bin/sh
# Mise a jour de la production Docker du Mac, avec les memes SQL que Windows natif.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"
for name in SIRH_PROD_DB_PASSWORD SIRH_PROD_JWT_SECRET SIRH_PROD_WEB_PORT SIRH_PROD_CORS_ORIGIN SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED SIRH_ADMIN_NAME SIRH_ADMIN_EMAIL SIRH_ADMIN_PASSWORD; do
  printenv "$name" >/dev/null || { echo "Variable absente : $name" >&2; exit 1; }
done
compose() { docker compose --project-directory "$root" -f "$root/docker-compose.web.yml" "$@"; }
compose config --quiet
[ "$(compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')" = galaxam-sirh-prod ] || { echo 'Projet Docker inattendu.' >&2; exit 1; }
[ "$(compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["volumes"]["pg_data"]["name"])')" = galaxam_prod_pg_data ] || { echo 'Volume PostgreSQL inattendu.' >&2; exit 1; }
[ "$(compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["volumes"]["business_files"]["name"])')" = galaxam_prod_business_files ] || { echo 'Volume documents inattendu.' >&2; exit 1; }
pg=$(compose ps -q postgres)
[ -n "$pg" ] || { echo 'PostgreSQL PROD non demarre.' >&2; exit 1; }
mkdir -p "$root/local-prod-backups"
stamp=$(date +%Y%m%d-%H%M%S)
archive="$root/local-prod-backups/galaxam-prod-$stamp.tar"
temp=$(mktemp -d)
stopped=0
migrated=0
completed=0
cleanup() {
  if [ "$stopped" -eq 1 ] && [ "$migrated" -eq 0 ]; then
    compose start backend frontend >/dev/null 2>&1 || true
  fi
  if [ "$migrated" -eq 1 ] && [ "$completed" -eq 0 ]; then
    compose stop frontend backend >/dev/null 2>&1 || true
    echo 'Migration appliquee mais nouveau code non verifie : web arrete. Restaurer la sauvegarde avant tout retour arriere.' >&2
  fi
  rm -rf "$temp"
}
trap cleanup EXIT HUP INT TERM
echo 'Compilation des images PROD...'
compose build backend frontend
echo 'Arret du web pour une sauvegarde coherente...'
compose stop frontend backend
stopped=1
if ! docker exec "$pg" pg_dump -U galaxam_admin -d sirh_galaxam -Fc > "$temp/database.dump"; then
  echo 'Sauvegarde PostgreSQL echouee.' >&2
  exit 1
fi
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
echo "Sauvegarde : $archive"
if ! compose run --rm --no-deps migrate; then
  echo 'Migration echouee ; base inchangee par la transaction.' >&2
  exit 1
fi
migrated=1
if ! compose up -d --no-deps --wait backend frontend; then
  echo "Nouveau code indisponible. Restaurer la base depuis $archive avant de revenir a l ancien code." >&2
  exit 1
fi
completed=1
echo 'Mise a jour PROD Docker terminee.'
