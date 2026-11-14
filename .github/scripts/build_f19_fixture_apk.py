#!/usr/bin/env python3
"""Build the signed APK used by the F19 device-smoke tracker check."""

from __future__ import annotations

import argparse
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

PACKAGE = "dev.fossscout.smoke.tracker"
LABEL = "F19 tracker fixture"
KEY_ALIAS = "f19-smoke"
KEY_PASSWORD = "changeit"


def run(command: list[object], *, cwd: Path | None = None) -> None:
    completed = subprocess.run(
        [str(part) for part in command],
        cwd=cwd,
        text=True,
        capture_output=True,
    )
    if completed.returncode != 0:
        raise RuntimeError(
            f"command failed ({completed.returncode}): {' '.join(map(str, command))}\n"
            f"{completed.stdout}\n{completed.stderr}"
        )


def tool(name: str, *directories: Path) -> Path:
    for directory in directories:
        candidate = directory / name
        if candidate.is_file():
            return candidate
    found = shutil.which(name)
    if found:
        return Path(found)
    raise FileNotFoundError(f"{name} was not found")


def write_sources(root: Path) -> None:
    source = root / "src" / "com" / "aarki"
    source.mkdir(parents=True)
    (source / "FixtureActivity.java").write_text(
        """
package com.aarki;

import android.app.Activity;
import android.os.Bundle;

public final class FixtureActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setContentDescription("com.aarki");
    }
}
""".strip()
        + "\n",
        encoding="utf-8",
    )
    (source / "TrackerMarker.java").write_text(
        """
package com.aarki;

public final class TrackerMarker {
    public static final String SIGNATURE = "com.aarki";

    private TrackerMarker() {}
}
""".strip()
        + "\n",
        encoding="utf-8",
    )
    (root / "AndroidManifest.xml").write_text(
        f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{PACKAGE}"
    android:versionCode="1"
    android:versionName="1.0">

    <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />

    <application android:label="{LABEL}">
        <activity
            android:name="com.aarki.FixtureActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""",
        encoding="utf-8",
    )


def build(args: argparse.Namespace) -> None:
    android_jar = args.android_jar.resolve()
    build_tools = args.build_tools.resolve()
    java_bin = args.java_home.resolve() / "bin"
    aapt2 = tool("aapt2", build_tools)
    d8_jar = build_tools / "lib" / "d8.jar"
    zipalign = tool("zipalign", build_tools)
    apksigner_jar = build_tools / "lib" / "apksigner.jar"
    java = tool("java", java_bin)
    javac = tool("javac", java_bin)
    keytool = tool("keytool", java_bin)
    for required in (d8_jar, apksigner_jar):
        if not required.is_file():
            raise FileNotFoundError(required)

    with tempfile.TemporaryDirectory(prefix="f19-apk-") as temporary:
        root = Path(temporary)
        write_sources(root)
        classes = root / "classes"
        classes.mkdir()
        run(
            [
                javac,
                "-source",
                "8",
                "-target",
                "8",
                "-classpath",
                android_jar,
                "-d",
                classes,
                root / "src" / "com" / "aarki" / "FixtureActivity.java",
                root / "src" / "com" / "aarki" / "TrackerMarker.java",
            ]
        )

        dex = root / "dex"
        dex.mkdir()
        run(
            [
                java,
                "-cp",
                d8_jar,
                "com.android.tools.r8.D8",
                "--lib",
                android_jar,
                "--min-api",
                "24",
                "--output",
                dex,
                classes / "com" / "aarki" / "FixtureActivity.class",
                classes / "com" / "aarki" / "TrackerMarker.class",
            ]
        )

        unsigned = root / "unsigned.apk"
        run(
            [
                aapt2,
                "link",
                "-o",
                unsigned,
                "-I",
                android_jar,
                "--manifest",
                root / "AndroidManifest.xml",
            ]
        )
        with zipfile.ZipFile(unsigned, "a") as apk:
            apk.write(dex / "classes.dex", "classes.dex")

        aligned = root / "aligned.apk"
        run([zipalign, "-f", "-p", "4", unsigned, aligned])

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

        args.output.parent.mkdir(parents=True, exist_ok=True)
        run(
            [
                java,
                "-jar",
                apksigner_jar,
                "sign",
                "--ks",
                key_store,
                "--ks-pass",
                f"pass:{KEY_PASSWORD}",
                "--key-pass",
                f"pass:{KEY_PASSWORD}",
                "--out",
                args.output,
                aligned,
            ]
        )
        run([java, "-jar", apksigner_jar, "verify", "--verbose", args.output])


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--android-jar", type=Path, required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    build(parse_args())
    print(f"Built {PACKAGE} fixture APK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
