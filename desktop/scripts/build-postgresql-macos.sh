#!/bin/bash
set -euo pipefail

# Phase 5A: stage PostgreSQL for testing. It is not added to tauri.conf.json.
version=15.19
archive="postgresql-${version}.tar.bz2"
sha256=e1a64a87a46b825b88c082e4518161a47aab53c45694964f8ba1df28f7859f89
script_dir=$(cd "$(dirname "$0")" && pwd)
repo_dir=$(cd "$script_dir/../.." && pwd)
stage="$repo_dir/desktop/bundle/postgresql"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

if [[ $(uname -s) != Darwin || $(uname -m) != arm64 ]]; then
  printf 'This build requires macOS ARM64.\n' >&2
  exit 1
fi

# Restrict build tools to Apple-provided tools to avoid Homebrew linkage.
export PATH=/usr/bin:/bin:/usr/sbin:/sbin
export MACOSX_DEPLOYMENT_TARGET=12.0
curl -fsSL "https://ftp.postgresql.org/pub/source/v${version}/${archive}" -o "$work/$archive"
printf '%s  %s\n' "$sha256" "$work/$archive" | shasum -a 256 -c -
tar -xjf "$work/$archive" -C "$work"
source_dir="$work/postgresql-$version"

# A staging path inside the ignored bundle tree keeps the source and output
# separate from application data. No service or database is touched here.
if [[ -e "$stage" ]]; then
  printf 'Staging directory already exists: %s\n' "$stage" >&2
  printf 'Move it aside manually before rebuilding.\n' >&2
  exit 1
fi
mkdir -p "$stage"
cd "$source_dir"
./configure --prefix="$stage" --without-readline --without-icu --without-openssl --disable-rpath
make -j4
make install
make -C contrib/btree_gist -j4
make -C contrib/btree_gist install
cp COPYRIGHT "$stage/COPYRIGHT"
python3 "$script_dir/relocate-postgresql-macos.py" "$stage"

printf 'PostgreSQL %s staged in %s\n' "$version" "$stage"
