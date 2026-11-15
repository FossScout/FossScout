#!/usr/bin/env python3
"""Generate the signed F-Droid-style index used by the F19 smoke test."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

KEY_ALIAS = "f19-smoke"
KEY_PASSWORD = "changeit"
TIMESTAMP = 1_767_225_600_000
PACKAGES = {
    "com.fossscout.app": ["android.permission.INTERNET", "android.permission.CAMERA"],
    "com.fossscout.app.vanilla": [
        "android.permission.INTERNET",
        "android.permission.CAMERA",
        "android.permission.QUERY_ALL_PACKAGES",
    ],
    "dev.fossscout.smoke.tracker": [
        "android.permission.INTERNET",
    ],
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def run(command: list[object]) -> None:
    completed = subprocess.run(
        [str(part) for part in command],
        text=True,
        capture_output=True,
    )
    if completed.returncode != 0:
        raise RuntimeError(
            f"command failed ({completed.returncode}): {' '.join(map(str, command))}\n"
            f"{completed.stdout}\n{completed.stderr}"
        )


def tool(name: str, java_home: Path) -> Path:
    candidate = java_home / "bin" / name
    if candidate.is_file():
        return candidate
    found = shutil.which(name)
    if found:
        return Path(found)
    raise FileNotFoundError(f"{name} was not found")


def index_document() -> str:
    packages = {}
    for package, permissions in PACKAGES.items():
        version_hash = sha256(package.encode("utf-8"))
        packages[package] = {
            "metadata": {
                "added": TIMESTAMP,
                "lastUpdated": TIMESTAMP,
                "name": {"en-US": package},
                "summary": {"en-US": "F19 smoke fixture"},
                "license": "Apache-2.0",
                "sourceCode": "https://example.invalid/f19-smoke",
            },
            "versions": {
                version_hash: {
                    "added": TIMESTAMP,
                    "file": {
                        "name": "/f19-smoke.apk",
                        "sha256": version_hash,
                        "size": 1,
                    },
                    "manifest": {
                        "versionName": "1.0",
                        "versionCode": 1,
                        "usesSdk": {
                            "minSdkVersion": 24,
                            "targetSdkVersion": 36,
                        },
                        "usesPermission": [
                            {"name": permission} for permission in permissions
                        ],
                    },
                }
            },
        }
    return json.dumps(
        {
            "repo": {
                "name": {"en-US": "FossScout F19 smoke repository"},
                "address": (
                    "https://raw.githubusercontent.com/FossScout/FossScout/main/"
                    ".github/fixtures/f19/repo"
                ),
            },
            "packages": packages,
        },
        indent=2,
    ) + "\n"


def write_jar(path: Path, entry_json: str) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        jar.writestr("entry.json", entry_json)


def build(args: argparse.Namespace) -> None:
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    index_bytes = index_document().encode("utf-8")
    (output / "index-v2.json").write_bytes(index_bytes)
    entry_json = json.dumps(
        {
            "timestamp": TIMESTAMP,
            "version": 30000,
            "maxAge": 14,
            "index": {
                "name": "/index-v2.json",
                "sha256": sha256(index_bytes),
                "size": len(index_bytes),
                "numPackages": len(PACKAGES),
            },
            "diffs": {},
        },
        indent=2,
    ) + "\n"

    keytool = tool("keytool", args.java_home.resolve())
    jarsigner = tool("jarsigner", args.java_home.resolve())
    with tempfile.TemporaryDirectory(prefix="f19-repo-") as temporary:
        root = Path(temporary)
        key_store = root / "f19-smoke.p12"
        run(
            [
                keytool,
                "-genkeypair",
                "-alias",
                KEY_ALIAS,
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "3650",
                "-dname",
                "CN=FossScout F19 Smoke",
                "-keystore",
                key_store,
                "-storetype",
                "PKCS12",
                "-storepass",
                KEY_PASSWORD,
                "-keypass",
                KEY_PASSWORD,
                "-noprompt",
            ]
        )
        jar = root / "entry.jar"
        write_jar(jar, entry_json)
        run(
            [
                jarsigner,
                "-keystore",
                key_store,
                "-storepass",
                KEY_PASSWORD,
                "-keypass",
                KEY_PASSWORD,
                jar,
                KEY_ALIAS,
            ]
        )
        (output / "entry.jar").write_bytes(jar.read_bytes())
    run([jarsigner, "-verify", output / "entry.jar"])
    print(f"Wrote F19 smoke repository to {output}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    build(parse_args())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
