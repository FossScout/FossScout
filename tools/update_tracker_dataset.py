#!/usr/bin/env python3
"""Regenerate the bundled Exodus ETIP tracker snapshot.

Run this from the repository root when a new snapshot has been reviewed. The
app loads the generated file locally and never calls the ETIP API at runtime.
"""

import argparse
import json
import re
import urllib.request
from datetime import date
from pathlib import Path

SOURCE_URL = "https://etip.exodus-privacy.eu.org/api/trackers/"
OUTPUT = Path("core/matching/src/main/resources/trackers/exodus-etip.json")
VALID_SIGNATURE = re.compile(r"^[A-Za-z0-9_.$/]+$")


def confidence(item):
    if item.get("is_in_exodus") and not item.get("needs_rework"):
        return "KNOWN"
    return "CANDIDATE"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--date", default=date.today().isoformat())
    parser.add_argument("--source", default=SOURCE_URL)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()

    with urllib.request.urlopen(args.source, timeout=60) as response:
        items = json.load(response)

    trackers = []
    for item in items:
        signatures = []
        for part in (item.get("code_signature") or "").split("|"):
            value = part.strip()
            if not value or not VALID_SIGNATURE.fullmatch(value):
                continue
            if any(existing["value"] == value for existing in signatures):
                continue
            signatures.append(
                {
                    "value": value,
                    "confidence": confidence(item),
                }
            )
        if not signatures:
            continue
        categories = [
            category.get("name")
            for category in item.get("category", [])
            if category.get("name")
        ]
        website = (item.get("website") or "").strip() or None
        trackers.append(
            {
                "name": item.get("name") or "Unnamed tracker",
                "categories": categories,
                "website": website,
                "signatures": signatures,
            }
        )

    trackers.sort(key=lambda tracker: (tracker["name"].casefold(), tracker["name"]))
    dataset = {
        "schemaVersion": 1,
        "dataset": {
            "name": "Exodus Privacy ETIP tracker signatures",
            "version": args.date,
            "licenseName": "ODbL 1.0 / DbCL 1.0",
            "licenseUrl": "https://opendatacommons.org/licenses/odbl/1-0/",
            "contentsLicenseUrl": "https://opendatacommons.org/licenses/dbcl/1-0/",
            "sourceUrl": SOURCE_URL,
            "retrievedAt": args.date,
            "attribution": "Tracker data from Exodus Privacy ETIP, licensed under ODbL 1.0 and DbCL 1.0.",
        },
        "trackers": trackers,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(dataset, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    signature_count = sum(len(tracker["signatures"]) for tracker in trackers)
    print(f"Wrote {len(trackers)} trackers / {signature_count} signatures to {args.output}")


if __name__ == "__main__":
    main()
