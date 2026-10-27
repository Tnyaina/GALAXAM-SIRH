#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$root"
env_file="$root/.env.production.test"
[ -f "$env_file" ] || { echo 'Configuration .env.production.test absente.' >&2; exit 1; }
compose() { docker compose --env-file "$env_file" -f "$root/docker-compose.web.yml" "$@"; }
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
