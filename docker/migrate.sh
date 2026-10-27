#!/bin/sh
# Une transaction unique : verrou, verification d'historique et migrations.
set -eu
sha256sum -c /canonical.sha256
sql=$(mktemp)
trap 'rm -f "$sql"' EXIT
cat > "$sql" <<'SQL'
\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(19760427);
CREATE TABLE IF NOT EXISTS public.galaxam_schema_history (
 version text PRIMARY KEY, checksum text NOT NULL, applied_at timestamptz NOT NULL DEFAULT now()
);
SQL
# Refuse une livraison ancienne/incomplete qui omet une migration deja appliquee.
printf 'CREATE TEMP TABLE supplied_migrations (version text PRIMARY KEY) ON COMMIT DROP; CREATE UNIQUE INDEX ON supplied_migrations ((left(version,4)));\n' >> "$sql"
for file in /migrations/[0-9][0-9][0-9][0-9]_*.sql; do
    [ -f "$file" ] || continue
    name=$(basename "$file")
    case "$name" in *[!a-zA-Z0-9_.-]*) echo "Nom de migration invalide" >&2; exit 1;; esac
    printf "INSERT INTO supplied_migrations VALUES ('%s');\n" "$name" >> "$sql"
done
cat >> "$sql" <<'SQL'
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM galaxam_schema_history h LEFT JOIN supplied_migrations s USING(version) WHERE s.version IS NULL) THEN
  RAISE EXCEPTION 'Livraison ancienne/incomplete : migration deja appliquee absente';
 END IF;
END $$;
SQL
for file in /migrations/[0-9][0-9][0-9][0-9]_*.sql; do
    [ -f "$file" ] || continue
    name=$(basename "$file")
    hash=$(sha256sum "$file" | cut -d ' ' -f1)
    cat >> "$sql" <<SQL
DO \$\$ BEGIN
 IF EXISTS (SELECT 1 FROM galaxam_schema_history WHERE version='$name' AND checksum <> '$hash') THEN
  RAISE EXCEPTION 'Migration modifiee apres application : $name';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM galaxam_schema_history WHERE version='$name') AND EXISTS (SELECT 1 FROM galaxam_schema_history WHERE version > '$name') THEN
  RAISE EXCEPTION 'Migration hors ordre : $name';
 END IF;
END \$\$;
SELECT NOT EXISTS (SELECT 1 FROM galaxam_schema_history WHERE version='$name') AS apply_migration \gset
\if :apply_migration
\i $file
INSERT INTO galaxam_schema_history(version,checksum) VALUES ('$name','$hash');
\endif
SQL
done
printf 'COMMIT;\n' >> "$sql"
psql -X -f "$sql"
