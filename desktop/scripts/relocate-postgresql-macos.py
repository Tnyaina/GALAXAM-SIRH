#!/usr/bin/env python3
"""Rewrite PostgreSQL Mach-O references to its adjacent bundled libraries."""
import pathlib
import subprocess
import sys

stage = pathlib.Path(sys.argv[1]).resolve()
lib = stage / "lib"
for directory in (stage / "bin", lib):
    for item in directory.iterdir():
        if not item.is_file() or item.is_symlink():
            continue
        kind = subprocess.run(["/usr/bin/file", "-b", str(item)], capture_output=True, text=True, check=True).stdout
        if not kind.startswith("Mach-O"):
            continue
        output = subprocess.run(["/usr/bin/otool", "-L", str(item)], capture_output=True, text=True, check=True).stdout
        references = [line.strip().split(" (", 1)[0] for line in output.splitlines()[1:]]
        for old in references:
            if not old.startswith(str(lib) + "/"):
                continue
            relative = "../lib/" if directory.name == "bin" else ""
            new = "@loader_path/" + relative + pathlib.Path(old).name
            if pathlib.Path(old).resolve() == item.resolve():
                subprocess.run(["/usr/bin/install_name_tool", "-id", new, str(item)], check=True)
            else:
                subprocess.run(["/usr/bin/install_name_tool", "-change", old, new, str(item)], check=True)
print("PostgreSQL Mach-O references relocated")
