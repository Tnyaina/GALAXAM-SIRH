#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
desktop_dir="$(cd -- "$script_dir/.." && pwd)"
repo_dir="$(cd -- "$desktop_dir/.." && pwd)"
bundle_dir="$desktop_dir/bundle"
runtime_dir="$bundle_dir/runtime"
jar_source="$repo_dir/backend/target/sirh-0.0.1-SNAPSHOT.jar"

if [[ "$(uname -s)" != Darwin || "$(uname -m)" != arm64 ]]; then
  printf 'This prototype builder requires macOS ARM64.\n' >&2
  exit 2
fi

jdk_home="${GALAXAM_BUILD_JDK_HOME:-$(/usr/libexec/java_home -v 17)}"
if [[ ! -x "$jdk_home/bin/jlink" || ! -d "$jdk_home/jmods" ]]; then
  printf 'A Java 17 JDK with jlink and jmods is required for the build.\n' >&2
  exit 2
fi
if ! "$jdk_home/bin/java" -version 2>&1 | head -1 | grep -q 'version "17\.'; then
  printf 'The build JDK must be Java 17.\n' >&2
  exit 2
fi
if ! grep -q 'IMPLEMENTOR="Eclipse Adoptium"' "$jdk_home/release"; then
  printf 'This prototype requires Eclipse Temurin for reproducible licensing.\n' >&2
  exit 2
fi

(cd "$repo_dir/backend" && mvn -q -DskipTests package)
mkdir -p "$bundle_dir"
cp "$jar_source" "$bundle_dir/backend.jar"

# jdeps only sees the outer Spring Boot JAR directly. Spring, POI, TLS and JDBC
# also load classes dynamically, so keep the modules verified by native startup.
modules=java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.zipfs

python3 - "$runtime_dir" <<'PY'
from pathlib import Path
import shutil
import sys
path = Path(sys.argv[1])
if path.exists():
    shutil.rmtree(path)
PY

"$jdk_home/bin/jlink" \
  --module-path "$jdk_home/jmods" \
  --add-modules "$modules" \
  --strip-debug --no-header-files --no-man-pages --compress=2 \
  --output "$runtime_dir"

# jlink includes command-line tools from selected modules. The application only
# launches java; these tools are unnecessary in an end-user bundle.
python3 - "$runtime_dir" <<'PY'
from pathlib import Path
import sys
bin_dir = Path(sys.argv[1]) / "bin"
for tool in bin_dir.iterdir():
    if tool.name != "java" and tool.is_file():
        tool.unlink()
PY

if [[ ! -x "$runtime_dir/bin/java" || ! -f "$runtime_dir/legal/java.base/LICENSE" ]]; then
  printf 'The embedded Java runtime or its license is incomplete.\n' >&2
  exit 2
fi

# Keep every jlink legal notice in the bundle. Tauri's resource code generator
# cannot recopy the read-only LICENSE files on an incremental macOS build, so
# store them as a separate archive beside the runtime image.
python3 - "$runtime_dir" "$bundle_dir/runtime-licenses.zip" <<'PY'
from pathlib import Path
import shutil
import sys
import zipfile
runtime = Path(sys.argv[1])
archive = Path(sys.argv[2])
legal = runtime / "legal"
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
    for path in legal.rglob("*"):
        if path.is_file():
            output.write(path, path.relative_to(runtime))
shutil.rmtree(legal)
PY
printf 'Prepared Temurin 17 runtime and Spring Boot JAR in %s\n' "$bundle_dir"
