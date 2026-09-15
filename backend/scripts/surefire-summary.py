#!/usr/bin/env python3
"""Affiche le total courant depuis les rapports Surefire, sans compteur documentaire."""

from pathlib import Path
from xml.etree import ElementTree

totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for report in Path("target/surefire-reports").glob("TEST-*.xml"):
    suite = ElementTree.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.attrib.get(key, 0))

print(" ".join(f"{key}={value}" for key, value in totals.items()))
