#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
[ -f "$root/.local-prod-test-credentials" ] || exit 1
printf 'URL : http://localhost:18089\n'
sed -n 's/^GALAXAM_DEFAULT_ADMIN_EMAIL=/Identifiant : /p;s/^GALAXAM_DEFAULT_ADMIN_PASSWORD=/Mot de passe : /p' "$root/.local-prod-test-credentials"
