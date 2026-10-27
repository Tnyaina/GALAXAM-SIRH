#!/usr/bin/env bash
set -euo pipefail

# Erase les lignes RH fournies comme exemples dans le schéma canonique.
# N'accepte que le conteneur/base jetable UX-15 prévus pour cette recette.
container="${UX15_PG_CONTAINER:-galaxam-ux15-pg}"
database="${UX15_DB_NAME:-galaxam_ux15}"
db_user="${UX15_DB_USER:-ux15_app}"

if [[ "$container" != "galaxam-ux15-pg" || "$database" != "galaxam_ux15" || "$db_user" != "ux15_app" ]]; then
  echo "Refus : cette commande est réservée à galaxam-ux15-pg / galaxam_ux15 / ux15_app." >&2
  exit 2
fi

inspect="$(docker inspect "$container")"
python3 -c 'import json,sys; d=json.load(sys.stdin)[0];
assert d["Name"] == "/galaxam-ux15-pg", "nom de conteneur inattendu";
assert d["Config"]["Image"].startswith("postgres:15"), "image PostgreSQL inattendue";
env=dict(x.split("=",1) for x in d["Config"].get("Env",[]) if "=" in x);
assert env.get("POSTGRES_DB") == "galaxam_ux15", "base attendue absente";
assert env.get("POSTGRES_USER") == "ux15_app", "utilisateur attendu absent";
assert all(m["Type"] == "volume" and m["Destination"] == "/var/lib/postgresql/data" for m in d.get("Mounts",[])), "montage inattendu";
bindings=d["HostConfig"].get("PortBindings",{}).get("5432/tcp",[]);
assert bindings and all(x.get("HostIp") == "127.0.0.1" and x.get("HostPort") == "55434" for x in bindings), "port de recette inattendu"' <<< "$inspect"

actual_db="$(docker exec "$container" psql -X -v ON_ERROR_STOP=1 -U "$db_user" -d "$database" -Atc 'SELECT current_database()')"
if [[ "$actual_db" != "$database" ]]; then
  echo "Refus : PostgreSQL a répondu pour la base '$actual_db'." >&2
  exit 3
fi

# Ce TRUNCATE n'installe aucune donnée de scénario : il enlève uniquement les
# dossiers personnels préchargés par les fichiers de schéma. Les relations
# métier du scénario seront ensuite créées par les API et workflows RH.
docker exec "$container" psql -X -v ON_ERROR_STOP=1 -U "$db_user" -d "$database" \
  -c 'TRUNCATE TABLE employe CASCADE'

remaining="$(docker exec "$container" psql -X -v ON_ERROR_STOP=1 -U "$db_user" -d "$database" -Atc 'SELECT COUNT(*) FROM employe')"
if [[ "$remaining" != "0" ]]; then
  echo "Échec de l'isolement : $remaining salarié(s) restent dans la base jetable." >&2
  exit 4
fi
echo "Base UX-15 assainie : aucun dossier salarié canonique ne reste." 
