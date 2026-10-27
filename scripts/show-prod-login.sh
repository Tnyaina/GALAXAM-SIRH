#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
credential_file="$root/.local-prod-credentials"
if [ ! -f "$credential_file" ]; then
  printf '%s\n' 'Aucun identifiant local stocké ; vérifiez le coffre de mots de passe.' >&2
  exit 1
fi
email=$(sed -n 's/^GALAXAM_DEFAULT_ADMIN_EMAIL=//p' "$credential_file")
password=$(sed -n 's/^GALAXAM_DEFAULT_ADMIN_PASSWORD=//p' "$credential_file")
if [ -z "$email" ]; then
  printf '%s\n' 'Adresse non enregistrée dans ce fichier.' >&2
  exit 1
fi
printf 'Adresse : %s\n' "$email"
if [ -z "$password" ]; then
  printf '%s\n' 'Mot de passe non enregistré localement. Consultez votre gestionnaire de mots de passe ou changez-le dans l’application.'
  exit 2
fi
printf 'Mot de passe : %s\n' "$password"
