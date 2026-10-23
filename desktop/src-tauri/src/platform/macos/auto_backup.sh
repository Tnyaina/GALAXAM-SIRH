#!/bin/sh
set -eu
umask 077
PATH=/usr/bin:/bin:/usr/sbin:/sbin
export PATH
HOME_DIR="$1"
VERSION_DIR="$2"
INSTALL_ID="$3"
PG_PORT="$4"
PG="$VERSION_DIR/postgresql/bin"
CONFIG="$HOME_DIR/data/postgresql/config"
CLUSTER="$HOME_DIR/data/postgresql/cluster"
BACKUPS="$HOME_DIR/backups"
LOG="$HOME_DIR/logs/backup.log"

fail() { echo "$(date '+%Y-%m-%d %H:%M:%S') sauvegarde automatique échouée : $1" >> "$LOG"; exit 1; }
[ "$(cat "$CONFIG/installation.id" 2>/dev/null || true)" = "$INSTALL_ID" ] || fail "identité de l'installation différente"
[ -d "$BACKUPS" ] && [ ! -L "$BACKUPS" ] || fail "dossier de sauvegardes absent ou invalide"
[ "$(/usr/bin/stat -f %u "$BACKUPS")" = "$(/usr/bin/id -u)" ] || fail "dossier de sauvegardes non possédé par l'utilisateur"
/bin/chmod 700 "$BACKUPS" || fail "permissions du dossier"
LOCK="$BACKUPS/.automatic.lock"
/usr/bin/shlock -f "$LOCK" -p $$ || exit 0
STAGING=''
PENDING=''
cleanup() {
  [ -z "$STAGING" ] || /bin/rm -rf "$STAGING"
  [ -z "$PENDING" ] || /bin/rm -f "$PENDING"
  if [ "$(/bin/cat "$LOCK" 2>/dev/null || true)" = "$$" ]; then /bin/rm -f "$LOCK"; fi
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

NOW=$(/bin/date +%s)
LAST=$(/bin/cat "$BACKUPS/.automatic-last-success" 2>/dev/null || true)
case "$LAST" in ''|*[!0-9]*) LAST=0;; esac
[ "$LAST" -le "$NOW" ] || LAST=0
[ "$((NOW-LAST))" -ge 86400 ] || exit 0
FREE_KB=$(/bin/df -Pk "$BACKUPS" | /usr/bin/awk 'NR==2 {print $4}')
case "$FREE_KB" in ''|*[!0-9]*) fail "espace disque non vérifiable";; esac
[ "$FREE_KB" -ge 512000 ] || fail "moins de 500 Mo libres"
[ -f "$CLUSTER/PG_VERSION" ] && [ "$(cat "$CLUSTER/PG_VERSION")" = 15 ] || fail "cluster PostgreSQL invalide"
"$PG/pg_ctl" -D "$CLUSTER" status >/dev/null 2>&1 || fail "PostgreSQL arrêté"
ADMIN_PASS=$(/usr/bin/awk -F= '$1=="admin"{print $2}' "$CONFIG/installation.secrets")
APP_PASS=$(/usr/bin/awk -F= '$1=="app"{print $2}' "$CONFIG/installation.secrets")
[ -n "$ADMIN_PASS" ] && [ -n "$APP_PASS" ] || fail "secrets PostgreSQL absents"
ACTUAL=$(PGPASSWORD="$ADMIN_PASS" "$PG/psql" -X -A -t -w -h 127.0.0.1 -p "$PG_PORT" -U galaxam_cluster_admin -d postgres -c 'show data_directory' 2>/dev/null || true)
[ "$(cd "$ACTUAL" 2>/dev/null && pwd -P)" = "$(cd "$CLUSTER" && pwd -P)" ] || fail "identité PostgreSQL non confirmée"

STAGING=$(/usr/bin/mktemp -d "$BACKUPS/.automatic.XXXXXXXX") || fail "préparation impossible"
STAMP=$(/bin/date -u +%Y%m%dT%H%M%SZ)
PENDING="$BACKUPS/galaxam-auto-$STAMP.tar.pending"
FINAL="$BACKUPS/galaxam-auto-$STAMP.tar"
PGPASSWORD="$APP_PASS" "$PG/pg_dump" -Fc --no-owner --no-acl -h 127.0.0.1 -p "$PG_PORT" -U galaxam_app -d sirh_galaxam -f "$STAGING/database.dump" >/dev/null 2>&1 || fail "export PostgreSQL"
"$PG/pg_restore" -l "$STAGING/database.dump" >/dev/null 2>&1 || fail "vérification PostgreSQL"
DOCUMENTS=false
if [ -e "$HOME_DIR/documents" ]; then
  [ -d "$HOME_DIR/documents" ] && [ ! -L "$HOME_DIR/documents" ] || fail "dossier des documents invalide"
  SPECIAL=$(/usr/bin/find "$HOME_DIR/documents" ! -type d ! -type f -print -quit)
  [ -z "$SPECIAL" ] || fail "liens ou fichiers spéciaux non pris en charge dans documents"
  DOCUMENTS=true
fi
/usr/bin/printf '{"format":"galaxam-local-backup-v1","database":"sirh_galaxam","documents_included":%s,"created_unix_seconds":%s}\n' "$DOCUMENTS" "$NOW" > "$STAGING/manifest.json"
if [ "$DOCUMENTS" = true ]; then
  /usr/bin/tar -cf "$PENDING" -C "$STAGING" database.dump manifest.json -C "$HOME_DIR" documents >/dev/null 2>&1 || fail "archivage des documents"
else
  /usr/bin/tar -cf "$PENDING" -C "$STAGING" database.dump manifest.json >/dev/null 2>&1 || fail "archivage"
fi
/usr/bin/tar -tf "$PENDING" >/dev/null 2>&1 || fail "vérification de l'archive"
/bin/mv "$PENDING" "$FINAL" || fail "publication de l'archive"
PENDING=''
/usr/bin/printf '%s\n' "$NOW" > "$BACKUPS/.automatic-last-success.pending"
/bin/mv "$BACKUPS/.automatic-last-success.pending" "$BACKUPS/.automatic-last-success"
/usr/bin/find "$BACKUPS" -maxdepth 1 -type f -name 'galaxam-auto-*.tar' -print | /usr/bin/sort -r | /usr/bin/sed -n '8,$p' | while IFS= read -r OLD; do /bin/rm -f "$OLD"; done
echo "$(date '+%Y-%m-%d %H:%M:%S') sauvegarde automatique créée : $FINAL" >> "$LOG"
