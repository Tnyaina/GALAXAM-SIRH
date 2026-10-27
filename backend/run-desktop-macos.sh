#!/usr/bin/env bash
set -euo pipefail

# Native backend launcher for the macOS prototype. Secrets stay in the caller's
# environment and are never written to this repository or printed by this script.
: "${GALAXAM_DESKTOP_DB_URL:?GALAXAM_DESKTOP_DB_URL is required}"
: "${GALAXAM_DESKTOP_DB_USER:?GALAXAM_DESKTOP_DB_USER is required}"
: "${GALAXAM_DESKTOP_DB_PASSWORD:?GALAXAM_DESKTOP_DB_PASSWORD is required}"
: "${GALAXAM_DESKTOP_JWT_SECRET:?GALAXAM_DESKTOP_JWT_SECRET is required}"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
app_home="${GALAXAM_DESKTOP_HOME:-${HOME}/Library/Application Support/GALAXAM SIRH}"
case "$app_home" in
  /*) ;;
  *) printf 'GALAXAM_DESKTOP_HOME must be an absolute path.\n' >&2; exit 2 ;;
esac

umask 077
mkdir -p \
  "$app_home/config" \
  "$app_home/data" \
  "$app_home/documents/photo" \
  "$app_home/documents/documents" \
  "$app_home/documents/prestataires" \
  "$app_home/logs"

jar_path="$script_dir/target/sirh-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$jar_path" ]]; then
  printf 'Backend JAR missing. Run: cd backend && mvn -DskipTests package\n' >&2
  exit 2
fi

export GALAXAM_DESKTOP_HOME="$app_home"
exec "${GALAXAM_JAVA_BIN:-java}" -Dspring.profiles.active=desktop -jar "$jar_path" "$@"
