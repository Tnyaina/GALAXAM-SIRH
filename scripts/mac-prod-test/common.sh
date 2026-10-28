#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$root"
: "${SIRH_MAC_TEST_DB_PASSWORD:?Definir SIRH_MAC_TEST_DB_PASSWORD sur le Mac}"
: "${SIRH_MAC_TEST_JWT_SECRET:?Definir SIRH_MAC_TEST_JWT_SECRET sur le Mac}"
: "${SIRH_MAC_TEST_ADMIN_PASSWORD:?Definir SIRH_MAC_TEST_ADMIN_PASSWORD sur le Mac}"
: "${SIRH_MAC_TEST_ADMIN_EMAIL:?Definir SIRH_MAC_TEST_ADMIN_EMAIL sur le Mac}"
: "${SIRH_MAC_TEST_ADMIN_NAME:=Tatamo Michele}"
export SIRH_PROD_DB_PASSWORD="$SIRH_MAC_TEST_DB_PASSWORD"
export SIRH_PROD_JWT_SECRET="$SIRH_MAC_TEST_JWT_SECRET"
export SIRH_ADMIN_NAME="$SIRH_MAC_TEST_ADMIN_NAME"
export SIRH_ADMIN_PASSWORD="$SIRH_MAC_TEST_ADMIN_PASSWORD"
export SIRH_ADMIN_EMAIL="$SIRH_MAC_TEST_ADMIN_EMAIL"
export SIRH_PROD_WEB_PORT=18089
export SIRH_PROD_CORS_ORIGIN=http://localhost:18089,http://127.0.0.1:18089
export SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED=true
export GALAXAM_PROJECT_NAME=galaxam-sirh-mac-prodtest
export GALAXAM_PG_VOLUME=galaxam_mac_prodtest_pg_data
export GALAXAM_FILES_VOLUME=galaxam_mac_prodtest_business_files
export GALAXAM_RELEASE=mac-prodtest
compose() { docker compose -f "$root/docker-compose.web.yml" "$@"; }
assert_test_stack() {
  compose config --format json | python3 -c 'import json,sys; c=json.load(sys.stdin); assert c["name"]=="galaxam-sirh-mac-prodtest"; assert c["volumes"]["pg_data"]["name"]=="galaxam_mac_prodtest_pg_data"; assert c["volumes"]["business_files"]["name"]=="galaxam_mac_prodtest_business_files"; assert str(c["services"]["frontend"]["ports"][0]["published"])=="18089"' || { echo 'Identité de la recette incorrecte ; opération annulée.' >&2; exit 1; }
}
container_id() { compose ps -a -q "$1"; }
wait_web() {
  i=0
  while [ "$i" -lt 60 ]; do
    if curl -fsS http://127.0.0.1:18089/api/auth/ping 2>/dev/null | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"]=="UP"' >/dev/null 2>&1; then return 0; fi
    i=$((i+1)); sleep 2
  done
  echo 'Application de recette indisponible.' >&2
  return 1
}
