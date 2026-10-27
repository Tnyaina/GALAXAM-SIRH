#!/bin/bash
set -euo pipefail
script_dir=$(cd "$(dirname "$0")" && pwd)
stage="$script_dir/../bundle/postgresql"
if [[ ! -e "$stage" ]]; then
  exec bash "$script_dir/build-postgresql-macos.sh"
fi
for required in bin/postgres bin/initdb bin/pg_ctl bin/pg_isready bin/pg_dump bin/pg_restore bin/psql lib/btree_gist.so share/extension/btree_gist.control COPYRIGHT; do
  if [[ ! -f "$stage/$required" ]]; then
    printf 'Incomplete PostgreSQL staging directory: %s\n' "$stage" >&2
    exit 1
  fi
done
if [[ "$("$stage/bin/postgres" --version)" != 'postgres (PostgreSQL) 15.19' ]]; then
  printf 'The staged PostgreSQL version is not 15.19.\n' >&2
  exit 1
fi
printf 'Using staged PostgreSQL 15.19 in %s\n' "$stage"
