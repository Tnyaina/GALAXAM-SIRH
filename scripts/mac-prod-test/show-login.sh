#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
. "$root/scripts/mac-prod-test/common.sh"
printf 'URL : http://localhost:18089\n'
printf 'Identifiant : %s\nMot de passe : %s\n' "$SIRH_MAC_TEST_ADMIN_EMAIL" "$SIRH_MAC_TEST_ADMIN_PASSWORD"
