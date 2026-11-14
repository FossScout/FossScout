#!/usr/bin/env python3
"""Run the CI-emulatable subset of the FossScout device smoke test."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable

NAV_LABELS = ("Discover", "Installed", "Saved", "Stores", "Settings", "About")
F19_STORE_NAME = "F19 smoke fixture"
F19_TRACKER_PACKAGE = "dev.fossscout.smoke.tracker"
F19_DOWNLOADS = {
    "playstore": "foss-scout-play.apk",
    "vanilla": "foss-scout-vanilla.apk",
    "tracker": "f19-tracker.apk",
}
NOT_COVERED = (
    "Release-signed Play AAB, Play Console upload, and internal-track installation.",
    "A real Google Play or F-Droid client install, and work-profile separation.",
    "Google Play and F-Droid client-provided installer labels (the mapping is "
    "unit-tested; this run uses installed-package fixtures).",
    "Store signer rotation/mismatch, storage budget, and a real repository's freshness.",
    "Matching correction, replacement handoff, other sharing/import flows, "
    "backup/restore, contribution, and settings reset flows.",
    "Inventory stale-threshold, hide/unhide persistence, and inaccessible-app counts.",
    "Real-device rendering, performance, and battery behavior.",
)


@dataclass(frozen=True)
class Build:
    flavor: str
    package: str
    apk: Path


@dataclass
class Check:
    scope: str
    name: str
    status: str
    detail: str = ""


class Failure(RuntimeError):
    pass


class Smoke:
    def __init__(self, args: argparse.Namespace) -> None:
        self.out = Path(args.output).resolve()
        self.out.mkdir(parents=True, exist_ok=True)
        self.adb_path = os.environ.get("ADB") or shutil.which("adb")
        self.aapt2_path = args.aapt2 or shutil.which("aapt2")
        self.builds = [
            Build("playstore", "com.fossscout.app", args.play_apk.resolve()),
            Build("vanilla", "com.fossscout.app.vanilla", args.vanilla_apk.resolve()),
        ]
        self.checks: list[Check] = []
        self.device: dict[str, str] = {}
        self.error: str | None = None
        self.static_only = args.static_only
        self.f19_store_url = args.f19_store_url
        self.f19_tracker_apk = (
            args.f19_tracker_apk.resolve() if args.f19_tracker_apk else None
        )
        self._screen_size_cache: tuple[int, int] | None = None

    def run(
        self,
        command: Iterable[object],
        *,
        check: bool = True,
        timeout: int = 120,
    ) -> subprocess.CompletedProcess[str]:
        proc = subprocess.run(
            [str(part) for part in command],
            text=True,
            capture_output=True,
            timeout=timeout,
        )
        if check and proc.returncode != 0:
            raise Failure(
                f"command failed ({proc.returncode}): {' '.join(map(str, command))}\n"
                f"{proc.stdout}\n{proc.stderr}"
            )
        return proc

    def adb(self, *args: object, check: bool = True, timeout: int = 120):
        if not self.adb_path:
            raise Failure("adb was not found")
        return self.run([self.adb_path, *args], check=check, timeout=timeout)

    def sh(self, *args: object, check: bool = True, timeout: int = 120):
        return self.adb("shell", *args, check=check, timeout=timeout)

    def record(self, scope: str, name: str, fn: Callable[[], object]) -> object:
        try:
            detail = fn()
        except Exception as exc:
            self.checks.append(Check(scope, name, "FAIL", str(exc)))
            print(f"FAIL [{scope}] {name}: {exc}")
            raise
        text = "" if detail is None else str(detail)
        self.checks.append(Check(scope, name, "PASS", text))
        print(f"PASS [{scope}] {name}" + (f": {text}" if text else ""))
        return detail

    def aapt2_dump(self, *args: object) -> str:
        if not self.aapt2_path:
            raise Failure("aapt2 was not found")
        return self.run([self.aapt2_path, *args]).stdout

    def sha256(self, path: Path) -> str:
        digest = hashlib.sha256()
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest()

    def save(self, relative: str, text: str) -> Path:
        path = self.out / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def static_checks(self) -> None:
        static_dir = self.out / "static"
        static_dir.mkdir(parents=True, exist_ok=True)
        for build in self.builds:
            scope = build.flavor
            badging = self.aapt2_dump("dump", "badging", build.apk)
            permissions = self.aapt2_dump("dump", "permissions", build.apk)
            manifest = self.aapt2_dump(
                "dump", "xmltree", build.apk, "--file", "AndroidManifest.xml"
            )
            self.save(f"static/{scope}-badging.txt", badging)
            self.save(f"static/{scope}-permissions.txt", permissions)
            self.save(f"static/{scope}-manifest.txt", manifest)

            self.record(
                scope,
                "APK package and version",
                lambda: self._check_badging(scope, build, badging),
            )
            self.record(
                scope,
                "package visibility permission",
                lambda: self._check_permissions(scope, permissions),
            )
            self.record(
                scope,
                "no install, delete, or accessibility service",
                lambda: self._check_forbidden_manifest_entries(scope, manifest),
            )
            self.record(
                scope,
                "APK checksum",
                lambda: f"sha256 {self.sha256(build.apk)}",
            )

    def _check_badging(self, scope: str, build: Build, badging: str) -> str:
        package = re.search(r"package: name='([^']+)'", badging)
        if not package or package.group(1) != build.package:
            raise Failure(f"unexpected package in {scope} APK")
        version = re.search(r"versionCode='([^']+)' versionName='([^']+)'", badging)
        if not version:
            raise Failure(f"missing version in {scope} APK")
        min_sdk = re.search(r"minSdkVersion:'(\d+)'", badging)
        target_sdk = re.search(r"targetSdkVersion:'(\d+)'", badging)
        if not min_sdk or not target_sdk:
            raise Failure(f"missing SDK levels in {scope} APK")
        if min_sdk.group(1) != "24" or target_sdk.group(1) != "36":
            raise Failure(
                f"unexpected SDK levels in {scope} APK: "
                f"min {min_sdk.group(1)}, target {target_sdk.group(1)}"
            )
        return (
            f"package {build.package}, version {version.group(2)} "
            f"({version.group(1)}), min {min_sdk.group(1)}, target {target_sdk.group(1)}"
        )

    def _check_permissions(self, scope: str, permissions: str) -> str:
        has_query_all = "android.permission.QUERY_ALL_PACKAGES" in permissions
        if scope == "playstore" and has_query_all:
            raise Failure("Play APK must not request QUERY_ALL_PACKAGES")
        if scope == "vanilla" and not has_query_all:
            raise Failure("Vanilla APK must request QUERY_ALL_PACKAGES")
        return "limited visibility" if scope == "playstore" else "broad visibility"

    def _check_forbidden_manifest_entries(self, scope: str, manifest: str) -> str:
        forbidden = (
            "android.permission.INSTALL_PACKAGES",
            "android.permission.DELETE_PACKAGES",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.accessibilityservice",
        )
        found = [entry for entry in forbidden if entry in manifest]
        if found:
            raise Failure(f"{scope} APK contains forbidden manifest entries: {found}")
        return "none present"

    def require_device(self) -> None:
        output = self.adb("devices", "-l").stdout
        if not re.search(r"(?m)^\S+\s+device\b", output):
            raise Failure(f"no emulator or device is attached:\n{output}")
        self.device = {
            "model": self.sh("getprop", "ro.product.model").stdout.strip(),
            "device": self.sh("getprop", "ro.product.device").stdout.strip(),
            "api": self.sh("getprop", "ro.build.version.sdk").stdout.strip(),
            "release": self.sh("getprop", "ro.build.version.release").stdout.strip(),
            "abi": self.sh("getprop", "ro.product.cpu.abi").stdout.strip(),
        }
        size = self.screen_size()
        self.device["screen"] = f"{size[0]}x{size[1]}"
        self.save(
            "device.txt",
            "\n".join(f"{key}: {value}" for key, value in self.device.items()) + "\n",
        )

    def install_initial(self) -> None:
        for build in self.builds:
            self.record(
                build.flavor,
                "install debug APK",
                lambda build=build: self._install(build.apk),
            )

    def install_f19_fixtures(self) -> None:
        if self.f19_tracker_apk is None:
            raise Failure("F19 tracker APK is required for the device run")
        self.record(
            "f19",
            "install tracker fixture APK",
            lambda: self._install(self.f19_tracker_apk),
        )
        self.sh("mkdir", "-p", "/sdcard/Download")
        downloads = {
            F19_DOWNLOADS["tracker"]: self.f19_tracker_apk,
            F19_DOWNLOADS["playstore"]: self.builds[0].apk,
            F19_DOWNLOADS["vanilla"]: self.builds[1].apk,
        }
        for name, path in downloads.items():
            self.adb("push", path, f"/sdcard/Download/{name}", timeout=120)
        path = self.sh("pm", "path", F19_TRACKER_PACKAGE).stdout.strip()
        if "package:" not in path:
            raise Failure("tracker fixture package was not installed")

    def _install(self, apk: Path) -> str:
        result = self.adb("install", "-r", "-t", apk, timeout=300).stdout
        if "Success" not in result:
            raise Failure(f"install failed for {apk}:\n{result}")
        return apk.name

    def clear_app(self, package: str) -> None:
        result = self.sh("pm", "clear", package).stdout
        if "Success" not in result:
            raise Failure(f"could not clear {package}:\n{result}")

    def resolve_activity(self, package: str) -> str:
        output = self.sh("cmd", "package", "resolve-activity", "--brief", package).stdout
        components = [line.strip() for line in output.splitlines() if "/" in line]
        if not components:
            raise Failure(f"could not resolve launcher activity for {package}:\n{output}")
        return components[-1]

    def is_foreground(self, package: str) -> bool:
        output = self.sh("dumpsys", "activity", "activities").stdout
        for marker in ("topResumedActivity=", "mResumedActivity="):
            match = re.search(
                marker + r"ActivityRecord\{[^}]*?\s+([A-Za-z0-9_.]+)/",
                output,
            )
            if match:
                return match.group(1) == package
        return f"{package}/" in output

    def launch(self, package: str) -> None:
        component = self.resolve_activity(package)
        self.sh("am", "force-stop", package)
        self.sh("am", "start", "-W", "-n", component, timeout=60)
        deadline = time.time() + 30
        while time.time() < deadline:
            if self.is_foreground(package):
                return
            time.sleep(1)
        raise Failure(f"{package} did not reach the foreground")

    def rotation_value(self) -> int | None:
        root = self.dump_ui()
        value = root.get("rotation")
        if value is not None:
            return int(value)
        output = self.sh("dumpsys", "input").stdout
        match = re.search(r"SurfaceOrientation:\s*(\d+)", output)
        if match:
            return int(match.group(1))
        output = self.sh("dumpsys", "window").stdout
        match = re.search(r"mRotation=ROTATION_(\d+)", output)
        return int(match.group(1)) if match else None

    def screen_size(self) -> tuple[int, int]:
        if self._screen_size_cache is not None:
            return self._screen_size_cache
        output = self.sh("wm", "size").stdout
        match = re.search(r"(\d+)x(\d+)", output)
        if not match:
            raise Failure(f"could not read screen size:\n{output}")
        width, height = int(match.group(1)), int(match.group(2))
        if self.rotation_value() in (1, 3):
            width, height = height, width
        self._screen_size_cache = (width, height)
        return width, height

    def invalidate_screen_size(self) -> None:
        self._screen_size_cache = None

    def is_landscape(self) -> bool:
        rotation = self.rotation_value()
        if rotation is not None:
            return rotation in (1, 3)
        width, height = self.screen_size()
        return width > height

    def dump_raw(self) -> str:
        last = ""
        for _ in range(6):
            proc = self.sh("uiautomator", "dump", "/sdcard/window.xml", check=False)
            xml = self.adb("exec-out", "cat", "/sdcard/window.xml", check=False).stdout
            if xml.lstrip().startswith("<?xml"):
                return xml
            last = f"{proc.stdout}\n{proc.stderr}\n{xml[:200]}"
            time.sleep(1)
        raise Failure(f"uiautomator dump failed:\n{last}")

    def dump_ui(self) -> ET.Element:
        last = ""
        for _ in range(3):
            xml = self.dump_raw()
            try:
                return ET.fromstring(xml)
            except ET.ParseError as exc:
                last = f"{exc}: {xml[:300]}"
                time.sleep(1)
        raise Failure(f"could not parse uiautomator XML: {last}")

    def node_bounds(self, node: ET.Element) -> tuple[int, int, int, int] | None:
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not match:
            return None
        return tuple(int(value) for value in match.groups())  # type: ignore[return-value]

    def node_labels(self, node: ET.Element) -> list[str]:
        return [
            value
            for value in (node.get("text", ""), node.get("content-desc", ""))
            if value
        ]

    def find_node(
        self,
        root: ET.Element,
        needle: str,
        *,
        exact: bool = False,
        region: str | None = None,
    ) -> ET.Element | None:
        width, height = self.screen_size()
        for node in root.iter("node"):
            labels = self.node_labels(node)
            if not labels:
                continue
            matched = (
                any(label == needle for label in labels)
                if exact
                else any(needle in label for label in labels)
            )
            if not matched:
                continue
            bounds = self.node_bounds(node)
            if not bounds:
                continue
            center_y = (bounds[1] + bounds[3]) // 2
            if region == "top" and center_y > height * 0.55:
                continue
            if region == "bottom" and center_y < height * 0.65:
                continue
            return node
        return None

    def tap_node(self, node: ET.Element) -> None:
        bounds = self.node_bounds(node)
        if not bounds:
            raise Failure("node has no tappable bounds")
        x = (bounds[0] + bounds[2]) // 2
        y = (bounds[1] + bounds[3]) // 2
        self.sh("input", "tap", x, y)

    def swipe_up(self) -> None:
        width, height = self.screen_size()
        self.sh(
            "input",
            "swipe",
            width // 2,
            int(height * 0.80),
            width // 2,
            int(height * 0.30),
            350,
        )
        time.sleep(0.5)

    def wait_for_node(
        self,
        needle: str,
        *,
        exact: bool = False,
        region: str | None = None,
        timeout: int = 20,
        scroll: bool = False,
    ) -> ET.Element:
        deadline = time.time() + timeout
        while True:
            node = self.find_node(
                self.dump_ui(),
                needle,
                exact=exact,
                region=region,
            )
            if node is not None:
                return node
            if time.time() >= deadline:
                raise Failure(f"UI text not found: {needle!r}")
            if scroll:
                self.swipe_up()
            else:
                time.sleep(1)

    def wait_for_any_text(
        self,
        needles: Iterable[str],
        *,
        exact: bool = False,
        timeout: int = 30,
        scroll: bool = True,
    ) -> tuple[str, ET.Element]:
        values = tuple(needles)
        deadline = time.time() + timeout
        while True:
            root = self.dump_ui()
            for needle in values:
                node = self.find_node(root, needle, exact=exact)
                if node is not None:
                    return needle, node
            if time.time() >= deadline:
                raise Failure(f"none of these UI texts appeared: {values!r}")
            if scroll:
                self.swipe_up()
            else:
                time.sleep(1)

    def tap_text(
        self,
        text: str,
        *,
        exact: bool = True,
        region: str | None = None,
        timeout: int = 20,
        scroll: bool = False,
    ) -> None:
        node = self.wait_for_node(
            text,
            exact=exact,
            region=region,
            timeout=timeout,
            scroll=scroll,
        )
        self.tap_node(node)

    def assert_absent(self, text: str, *, exact: bool = True) -> None:
        if self.find_node(self.dump_ui(), text, exact=exact) is not None:
            raise Failure(f"unexpected UI text is present: {text!r}")

    def wait_for_navigation(self, timeout: int = 30) -> None:
        deadline = time.time() + timeout
        missing: list[str] = []
        while time.time() < deadline:
            root = self.dump_ui()
            missing = [
                label
                for label in NAV_LABELS
                if self.find_node(root, label, exact=True, region="bottom") is None
            ]
            if not missing:
                return
            time.sleep(1)
        raise Failure(f"bottom navigation is missing: {missing}")

    def capture(self, build: Build, name: str) -> str:
        directory = self.out / build.flavor
        directory.mkdir(parents=True, exist_ok=True)
        path = directory / f"{name}.png"
        with path.open("wb") as handle:
            proc = subprocess.run(
                [self.adb_path, "exec-out", "screencap", "-p"],
                stdout=handle,
                stderr=subprocess.PIPE,
                timeout=60,
            )
        if proc.returncode != 0:
            raise Failure(f"screenshot failed: {proc.stderr.decode(errors='replace')}")
        return str(path)

    def save_ui(self, build: Build, name: str) -> None:
        self.save(f"{build.flavor}/{name}.xml", self.dump_raw())

    def open_destination(self, label: str, heading: str, build: Build, name: str) -> None:
        self.tap_text(label, exact=True, region="bottom", timeout=30)
        self.wait_for_node(heading, exact=False, region="top", timeout=30)
        time.sleep(0.5)
        self.capture(build, name)
        self.save_ui(build, name)

    def common_ui(self, build: Build) -> None:
        scope = build.flavor

        def launch_fresh() -> str:
            self.adb("logcat", "-c")
            self.clear_app(build.package)
            self.launch(build.package)
            self.wait_for_navigation()
            self.capture(build, "01-discover")
            self.save_ui(build, "01-discover")
            return f"{build.package} launched"

        def navigate() -> str:
            self.open_destination("Discover", "FossScout", build, "01-discover")
            self.open_destination("Installed", "Installed apps", build, "02-installed")
            self.open_destination("Saved", "Saved projects", build, "03-saved")
            self.open_destination("Stores", "Stores", build, "04-stores")
            self.wait_for_node("F-Droid", exact=False, timeout=30, scroll=True)
            self.wait_for_node("IzzyOnDroid", exact=False, timeout=30, scroll=True)
            self.open_destination("Settings", "Settings", build, "05-settings")
            self.wait_for_node("Backup and restore", exact=False, timeout=30, scroll=True)
            self.open_destination("About", "Version 0.1.0", build, "06-about")
            self.wait_for_node(
                "Donate via Liberapay",
                exact=True,
                timeout=30,
                scroll=True,
            )
            self.capture(build, "07-about-support")
            self.save_ui(build, "07-about-support")
            return "all six destinations opened"

        def favourites() -> str:
            self.open_destination("Discover", "FossScout", build, "01-discover")
            self.tap_text("Save Blender", exact=True, timeout=20, scroll=True)
            self.wait_for_node(
                "Remove Blender from saved projects",
                exact=True,
                timeout=20,
            )
            time.sleep(1.5)
            self.launch(build.package)
            self.wait_for_navigation()
            self.open_destination("Saved", "Saved projects", build, "08-saved-persisted")
            self.wait_for_node("Blender", exact=True, timeout=20, scroll=True)
            self.capture(build, "08-saved-persisted")
            self.tap_text(
                "Remove Blender from saved projects",
                exact=True,
                timeout=20,
                scroll=True,
            )
            self.wait_for_node("Nothing saved yet", exact=True, timeout=20)
            return "saved project survived a process restart"

        def rotation() -> str:
            self.open_destination("Discover", "FossScout", build, "01-discover")
            self.sh("settings", "put", "system", "accelerometer_rotation", "0")
            self.sh("wm", "user-rotation", "lock", "1")
            time.sleep(3)
            self.invalidate_screen_size()
            if not self.is_landscape():
                self.save_rotation_diagnostics(build)
                raise Failure("emulator did not rotate to landscape")
            self.wait_for_navigation()
            self.capture(build, "09-landscape")
            self.sh("wm", "user-rotation", "lock", "0")
            time.sleep(3)
            self.invalidate_screen_size()
            if self.is_landscape():
                self.save_rotation_diagnostics(build)
                raise Failure("emulator did not rotate back to portrait")
            self.wait_for_navigation()
            return "portrait and landscape"

        def font_scale() -> str:
            self.sh("settings", "put", "system", "font_scale", "1.3")
            self.launch(build.package)
            self.wait_for_navigation()
            self.capture(build, "10-font-scale")
            self.sh("settings", "put", "system", "font_scale", "1.0")
            self.launch(build.package)
            self.wait_for_navigation()
            return "1.0 and 1.3"

        def dark_theme() -> str:
            self.sh("cmd", "uimode", "night", "yes")
            self.launch(build.package)
            self.wait_for_navigation()
            self.capture(build, "11-dark")
            self.sh("cmd", "uimode", "night", "no")
            self.launch(build.package)
            self.wait_for_navigation()
            return "light and dark"

        def offline() -> str:
            self.set_airplane(True)
            try:
                self.launch(build.package)
                self.wait_for_navigation()
                self.open_destination("Stores", "Stores", build, "12-offline-stores")
                self.wait_for_node("F-Droid", exact=False, timeout=20, scroll=True)
            finally:
                self.set_airplane(False)
            self.launch(build.package)
            self.wait_for_navigation()
            return "offline launch and Stores screen"

        self.record(scope, "fresh launch", launch_fresh)
        self.record(scope, "bottom navigation and screens", navigate)
        self.record(scope, "favourite persistence", favourites)
        self.record(scope, "rotation", rotation)
        self.record(scope, "font scale", font_scale)
        self.record(scope, "dark theme", dark_theme)
        self.record(scope, "offline launch", offline)

    def save_rotation_diagnostics(self, build: Build) -> None:
        commands = (
            ("wm-size", ("wm", "size")),
            ("wm-user-rotation", ("wm", "user-rotation")),
            ("settings-user-rotation", ("settings", "get", "system", "user_rotation")),
            (
                "settings-accelerometer-rotation",
                ("settings", "get", "system", "accelerometer_rotation"),
            ),
            ("dumpsys-input", ("dumpsys", "input")),
            ("dumpsys-window-displays", ("dumpsys", "window", "displays")),
        )
        lines: list[str] = []
        for name, command in commands:
            result = self.sh(*command, check=False)
            text = f"{result.stdout}{result.stderr}"
            if name.startswith("dumpsys"):
                text = "\n".join(
                    line
                    for line in text.splitlines()
                    if re.search(
                        r"SurfaceOrientation|mRotation|rotation|cur=|mDisplayInfo",
                        line,
                        re.IGNORECASE,
                    )
                )
            lines.append(f"## {name}\n{text}")
        try:
            lines.append(f"## uiautomator rotation\n{self.dump_ui().get('rotation')}")
        except Exception as exc:
            lines.append(f"## uiautomator rotation\nunavailable: {exc}")
        self.save(f"{build.flavor}/rotation-diagnostics.txt", "\n".join(lines))

    def set_airplane(self, enabled: bool) -> None:
        action = "enable" if enabled else "disable"
        result = self.sh("cmd", "connectivity", "airplane-mode", action, check=False)
        if result.returncode == 0:
            return
        self.sh(
            "settings",
            "put",
            "global",
            "airplane_mode_on",
            "1" if enabled else "0",
            check=False,
        )
        self.sh(
            "am",
            "broadcast",
            "-a",
            "android.intent.action.AIRPLANE_MODE",
            "--ez",
            "state",
            "true" if enabled else "false",
            check=False,
        )
        time.sleep(2)

    def runtime_permissions(self, build: Build) -> str:
        output = self.sh("dumpsys", "package", build.package).stdout
        self.save(f"{build.flavor}/package-dumpsys.txt", output)
        has_query_all = "android.permission.QUERY_ALL_PACKAGES" in output
        if build.flavor == "playstore" and has_query_all:
            raise Failure("Play build has QUERY_ALL_PACKAGES at runtime")
        if build.flavor == "vanilla" and not has_query_all:
            raise Failure("Vanilla build is missing QUERY_ALL_PACKAGES at runtime")
        for forbidden in (
            "android.permission.INSTALL_PACKAGES",
            "android.permission.DELETE_PACKAGES",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
        ):
            if forbidden in output:
                raise Failure(f"{build.package} has forbidden permission {forbidden}")
        return "package visibility matches the flavor"

    def open_inventory(self, build: Build) -> None:
        self.open_destination("Discover", "FossScout", build, "01-discover")
        self.open_destination("Installed", "Installed apps", build, "02-installed")

    def find_editable(self, root: ET.Element) -> ET.Element | None:
        for node in root.iter("node"):
            labels = self.node_labels(node)
            if node.get("class") == "android.widget.EditText":
                return node
            if any("App name, package" in label for label in labels):
                return node
            if any("org.example.app" in label for label in labels):
                return node
        return None

    def wait_for_editable(self, timeout: int = 20) -> ET.Element:
        deadline = time.time() + timeout
        while time.time() < deadline:
            node = self.find_editable(self.dump_ui())
            if node is not None:
                return node
            time.sleep(1)
        raise Failure("inventory query field was not found")

    def input_text(self, text: str) -> None:
        self.sh("input", "text", text.replace(" ", "%s"))
        time.sleep(0.3)

    def hide_keyboard(self) -> None:
        if "mInputShown=true" in self.sh("dumpsys", "input_method").stdout:
            self.sh("input", "keyevent", "4")
            time.sleep(0.5)

    def enter_package(self, build: Build, package: str) -> None:
        self.open_inventory(build)
        field = self.wait_for_editable()
        self.tap_node(field)
        time.sleep(0.5)
        self.sh("input", "keyevent", "123")
        self.sh("input", "keyevent", *(["67"] * 80))
        self.sh("input", "text", package)
        time.sleep(0.5)
        self.hide_keyboard()

    def run_as(self, package: str, *args: object, check: bool = True):
        return self.adb(
            "exec-out",
            "run-as",
            package,
            *args,
            check=check,
            timeout=30,
        )

    def manual_check(
        self,
        build: Build,
        package: str,
        expected_any: Iterable[str],
        screenshot: str,
    ) -> str:
        self.enter_package(build, package)
        self.tap_text("Check app", exact=True, timeout=20)
        self.wait_for_node("Checked one app", exact=True, timeout=30)
        try:
            observed, _ = self.wait_for_any_text(
                expected_any,
                exact=False,
                scroll=False,
            )
        except Failure:
            self.capture(build, f"{screenshot}-failure")
            self.save_ui(build, f"{screenshot}-failure")
            raise
        self.wait_for_node("Unknown", exact=True, timeout=20, scroll=False)
        self.capture(build, screenshot)
        self.save_ui(build, screenshot)
        return f"{package}: {observed}"

    def edittext_for_label(
        self,
        root: ET.Element,
        label: str,
    ) -> ET.Element | None:
        label_node = self.find_node(root, label, exact=True)
        if label_node is None:
            return None
        label_bounds = self.node_bounds(label_node)
        if not label_bounds:
            return None
        for node in root.iter("node"):
            if node.get("class") != "android.widget.EditText":
                continue
            bounds = self.node_bounds(node)
            if not bounds:
                continue
            if (
                bounds[0] <= label_bounds[0]
                and bounds[2] >= label_bounds[2]
                and bounds[1] - 18 <= label_bounds[1]
                and bounds[3] + 18 >= label_bounds[3]
            ):
                return node
        return None

    def fill_store_field(self, label: str, value: str) -> None:
        deadline = time.time() + 20
        while time.time() < deadline:
            field = self.edittext_for_label(self.dump_ui(), label)
            if field is not None:
                self.tap_node(field)
                self.input_text(value)
                self.hide_keyboard()
                return
            self.hide_keyboard()
            width, height = self.screen_size()
            self.sh(
                "input",
                "swipe",
                width // 2,
                int(height * 0.70),
                width // 2,
                int(height * 0.35),
                300,
            )
            time.sleep(0.5)
        raise Failure(f"store editor field was not found: {label}")

    def add_f19_store(self, build: Build) -> None:
        if not self.f19_store_url:
            raise Failure("F19 store URL is required for the device run")
        self.open_destination("Stores", "Stores", build, "20-f19-stores-empty")
        self.tap_text("Add store", exact=True, timeout=20, scroll=True)
        self.wait_for_node("Homepage / repository URL", exact=True, timeout=20)
        self.fill_store_field("Name", F19_STORE_NAME)
        self.fill_store_field("Homepage / repository URL", self.f19_store_url)
        self.hide_keyboard()
        self.tap_text("Save", exact=True, timeout=20)
        self.wait_for_node(F19_STORE_NAME, exact=True, timeout=30, scroll=True)
        self.capture(build, "21-f19-store-added")
        self.save_ui(build, "21-f19-store-added")

    def setup_f19_store(self, build: Build) -> str:
        self.add_f19_store(build)
        return self.sync_f19_store(build)

    def f19_sync_scope(self, root: ET.Element, anchor: ET.Element) -> ET.Element | None:
        parents = {
            child: parent for parent in root.iter("node") for child in parent
        }
        node: ET.Element | None = anchor
        while node is not None:
            for candidate in node.iter("node"):
                labels = self.node_labels(candidate)
                if any(
                    label in ("Sync now", "Sync again", "Cancel")
                    or label.startswith("Last successful sync")
                    or "Downloading and verifying" in label
                    for label in labels
                ):
                    return node
            node = parents.get(node)
        return None

    def node_center(self, node: ET.Element) -> tuple[int, int] | None:
        bounds = self.node_bounds(node)
        if not bounds:
            return None
        return (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2

    def is_tappable(self, node: ET.Element) -> bool:
        center = self.node_center(node)
        if center is None:
            return False
        _, height = self.screen_size()
        _, y = center
        return 0 < y < height - int(height * 0.10)

    def nudge_up(self) -> None:
        width, height = self.screen_size()
        self.sh(
            "input",
            "swipe",
            width // 2,
            int(height * 0.72),
            width // 2,
            int(height * 0.50),
            250,
        )
        time.sleep(0.5)

    def save_f19_sync_failure(self, build: Build, name: str) -> None:
        try:
            self.capture(build, name)
            self.save_ui(build, name)
        except Exception as exc:
            print(f"could not capture F19 sync failure screen: {exc}")
        try:
            logs = self.adb("logcat", "-d", "-v", "threadtime").stdout
            self.save(f"{build.flavor}/{name}-logcat.txt", logs)
        except Exception as exc:
            print(f"could not capture F19 sync logcat: {exc}")
        try:
            listing = self.run_as(
                build.package,
                "find",
                "files",
                "-type",
                "f",
                check=False,
            )
            self.save(f"{build.flavor}/{name}-files.txt", listing.stdout)
            for path in (
                "files/datastore/store_registry.preferences_pb",
                "files/datastore/index_state.preferences_pb",
            ):
                encoded = self.run_as(build.package, "base64", path, check=False)
                if encoded.returncode == 0:
                    self.save(
                        f"{build.flavor}/{name}-{Path(path).name}.base64.txt",
                        encoded.stdout,
                    )
        except Exception as exc:
            print(f"could not capture F19 store data: {exc}")

    def sync_f19_store(self, build: Build) -> str:
        for _ in range(15):
            root = self.dump_ui()
            anchor = self.find_node(root, F19_STORE_NAME, exact=True)
            if anchor is not None:
                scope = self.f19_sync_scope(root, anchor)
                if scope is not None:
                    button = next(
                        (
                            node
                            for node in scope.iter("node")
                            if any(
                                label in ("Sync now", "Sync again")
                                for label in self.node_labels(node)
                            )
                        ),
                        None,
                    )
                    if button is not None and self.is_tappable(button):
                        self.tap_node(button)
                        break
                self.nudge_up()
            else:
                self.swipe_up()
        else:
            self.save_f19_sync_failure(build, "21-f19-store-sync-button-failure")
            raise Failure("F19 store sync button was not found or was off-screen")

        errors = (
            "The index could not be downloaded",
            "The index failed signature or hash verification",
            "The store returned an unsupported or malformed index",
            "The index would exceed your storage budget",
            "The index could not be saved",
            "Sync cancelled",
            "Sync failed",
            "This adapter cannot sync indexes",
            "Plain HTTP is blocked",
            "The repository fingerprint changed",
        )
        deadline = time.time() + 120
        while True:
            root = self.dump_ui()
            anchor = self.find_node(root, F19_STORE_NAME, exact=True)
            scope = None if anchor is None else self.f19_sync_scope(root, anchor)
            if scope is not None:
                labels = [
                    label
                    for node in scope.iter("node")
                    for label in self.node_labels(node)
                ]
                error = next(
                    (
                        label
                        for label in labels
                        if any(label.startswith(prefix) for prefix in errors)
                    ),
                    None,
                )
                if error is not None:
                    self.save_f19_sync_failure(build, "21-f19-store-sync-failure")
                    raise Failure(f"F19 store sync failed: {error}")
                if any(
                    label.startswith("Last successful sync") for label in labels
                ) and any(label in ("Fresh", "Stale") for label in labels):
                    self.capture(build, "21-f19-store-synced")
                    self.save_ui(build, "21-f19-store-synced")
                    return "F19 fixture store synced"
            if time.time() >= deadline:
                self.save_f19_sync_failure(build, "21-f19-store-sync-timeout")
                raise Failure("F19 store did not finish syncing within 120 seconds")
            time.sleep(1)

    def pick_download_apk(self, build: Build, filename: str, screenshot: str) -> None:
        deadline = time.time() + 60
        while time.time() < deadline:
            root = self.dump_ui()
            file_node = self.find_node(root, filename, exact=True)
            if file_node is not None:
                self.tap_node(file_node)
                return
            downloads = self.find_node(root, "Downloads", exact=True)
            if downloads is not None:
                self.tap_node(downloads)
                time.sleep(1.5)
                continue
            roots = self.find_node(root, "Show roots", exact=True)
            if roots is not None:
                self.tap_node(roots)
                time.sleep(1)
                continue
            time.sleep(1)
        self.capture(build, f"{screenshot}-picker-failure")
        self.save_ui(build, f"{screenshot}-picker-failure")
        raise Failure(f"document picker did not show {filename}")

    def f19_comparison(
        self,
        build: Build,
        package: str,
        manifest_apk: str,
        mismatch_apk: str,
        mismatch_package: str,
        expect_tracker_match: bool,
    ) -> str:
        self.enter_package(build, package)
        self.tap_text("Check app", exact=True, timeout=20)
        self.wait_for_node("Checked one app", exact=True, timeout=30)
        self.tap_text("FOSS match details", exact=True, timeout=20, scroll=True)
        self.wait_for_node("FOSS match", exact=False, region="top", timeout=30)
        self.wait_for_node("Exact FOSS match", exact=True, timeout=20, scroll=True)
        self.tap_text("Compare permissions", exact=True, timeout=20, scroll=True)
        self.wait_for_node("Permission comparison", exact=False, region="top", timeout=30)

        self.tap_text("Choose APK", exact=True, timeout=20, scroll=True)
        self.pick_download_apk(build, manifest_apk, "22-f19-manifest")
        self.wait_for_node(
            "Using the selected APK's manifest",
            exact=False,
            timeout=40,
            scroll=True,
        )
        self.wait_for_node(
            "APK manifest version:",
            exact=False,
            timeout=20,
            scroll=True,
        )
        permissions_node = self.wait_for_node(
            "Manifest permissions:",
            exact=False,
            timeout=20,
            scroll=True,
        )
        permission_detail = next(
            label
            for label in self.node_labels(permissions_node)
            if label.startswith("Manifest permissions:")
        )
        count = re.search(r"(\d+)", permission_detail)
        if not count or int(count.group(1)) < 1:
            raise Failure("selected APK manifest did not expose permissions")
        self.wait_for_node(
            "A manifest shows declared permissions, not runtime use.",
            exact=False,
            timeout=20,
            scroll=True,
        )
        self.wait_for_node(
            "Alternative: manifest of a locally selected APK.",
            exact=False,
            timeout=20,
            scroll=True,
        )

        self.tap_text("Choose another APK", exact=True, timeout=20, scroll=True)
        self.pick_download_apk(build, mismatch_apk, "23-f19-manifest-mismatch")
        mismatch = f"The selected APK is for {mismatch_package}, not {package}."
        self.wait_for_node(mismatch, exact=True, timeout=40, scroll=True)
        self.wait_for_node(
            "Using the selected APK's manifest",
            exact=False,
            timeout=20,
            scroll=True,
        )

        self.wait_for_node(
            "Dataset: Exodus Privacy ETIP tracker signatures",
            exact=False,
            timeout=20,
            scroll=True,
        )
        self.wait_for_node(
            "License: ODbL 1.0 / DbCL 1.0",
            exact=False,
            timeout=20,
            scroll=True,
        )
        if expect_tracker_match:
            self.wait_for_node(
                "1 tracker signature match",
                exact=True,
                timeout=40,
                scroll=True,
            )
            self.wait_for_node("Aarki", exact=True, timeout=20, scroll=True)
            tracker = "Aarki"
        else:
            self.wait_for_node(
                "No known tracker signature was found",
                exact=False,
                timeout=40,
                scroll=True,
            )
            tracker = "no tracker match"

        self.capture(build, "24-f19-comparison")
        self.save_ui(build, "24-f19-comparison")
        return (
            f"{tracker}; manifest {permission_detail}; "
            f"mismatch rejected for {mismatch_package}"
        )

    def f19_audit(self, build: Build, package: str, tracker_match: bool) -> str:
        existing = set(
            self.run_as(build.package, "ls", "-1t", "cache/exports", check=False)
            .stdout.splitlines()
        )
        self.tap_text("Export audit report", exact=True, timeout=20, scroll=True)
        deadline = time.time() + 30
        filename = ""
        while time.time() < deadline:
            listing = self.run_as(
                build.package,
                "ls",
                "-1t",
                "cache/exports",
                check=False,
            ).stdout.splitlines()
            new_files = [
                entry
                for entry in listing
                if entry not in existing and entry.endswith(".json")
            ]
            if new_files:
                filename = new_files[0]
                break
            time.sleep(0.5)
        if not filename:
            self.capture(build, "25-f19-audit-failure")
            self.save_ui(build, "25-f19-audit-failure")
            raise Failure("comparison audit file was not written")
        content = self.run_as(
            build.package,
            "cat",
            f"cache/exports/{filename}",
        ).stdout
        self.save(f"{build.flavor}/f19-audit.json", content)
        audit = json.loads(content)
        if audit["sources"]["alternative"] != "selected_local_apk_manifest":
            raise Failure("audit did not record the selected manifest source")
        if audit["alternativeManifest"]["packageName"] != package:
            raise Failure("audit manifest package does not match the comparison")
        if audit["installed"]["packageName"] != package:
            raise Failure("audit installed package does not match the comparison")
        if audit["exportedAtEpochMillis"] <= 0:
            raise Failure("audit timestamp is missing")
        if not audit["exportedAtUtc"].endswith("Z"):
            raise Failure("audit UTC timestamp is missing")
        if audit["tracker"]["dataset"]["version"] != "2026-11-10":
            raise Failure("audit tracker dataset version is missing")
        if audit["tracker"]["scanStatus"] != "COMPLETE":
            raise Failure(
                f"audit tracker scan did not complete: {audit['tracker']['scanStatus']}"
            )
        names = [match["name"] for match in audit["tracker"]["matches"]]
        if tracker_match and "Aarki" not in names:
            raise Failure("audit did not preserve the Aarki tracker match")
        if not tracker_match and names:
            raise Failure(f"audit unexpectedly reported tracker matches: {names}")
        diff = audit["diff"]
        if diff is None or diff["installedOnly"] or diff["alternativeOnly"]:
            raise Failure("manifest replacement did not recompute the permission diff")
        if not diff["shared"]:
            raise Failure("manifest replacement produced no shared permissions")
        limitation = (
            "The selected APK manifest was read locally; the APK was not installed, "
            "executed, or signature-verified."
        )
        if limitation not in audit["limitations"]:
            raise Failure("audit did not record the manifest limitation")

        deadline = time.time() + 10
        while time.time() < deadline and self.is_foreground(build.package):
            time.sleep(0.5)
        if self.is_foreground(build.package):
            raise Failure("audit share sheet did not open")
        self.sh("input", "keyevent", "4")
        self.wait_for_node("Permission comparison", exact=False, region="top", timeout=20)
        return f"audit {filename}; {len(audit['alternative']['permissions'])} permissions"

    def f19_specific(self, build: Build) -> None:
        if self.f19_tracker_apk is None:
            raise Failure("F19 tracker APK is required for the device run")
        self.record(
            build.flavor,
            "F19 fixture store sync",
            lambda: self.setup_f19_store(build),
        )
        if build.flavor == "playstore":
            package = F19_TRACKER_PACKAGE
            manifest_apk = F19_DOWNLOADS["tracker"]
            mismatch_apk = F19_DOWNLOADS["vanilla"]
            mismatch_package = self.builds[1].package
            expect_match = True
        else:
            package = build.package
            manifest_apk = F19_DOWNLOADS["vanilla"]
            mismatch_apk = F19_DOWNLOADS["playstore"]
            mismatch_package = self.builds[0].package
            expect_match = False
        self.record(
            build.flavor,
            "F19 tracker and APK manifest comparison",
            lambda: self.f19_comparison(
                build,
                package,
                manifest_apk,
                mismatch_apk,
                mismatch_package,
                expect_match,
            ),
        )
        self.record(
            build.flavor,
            "F19 standalone audit export",
            lambda: self.f19_audit(build, package, expect_match),
        )

    def play_specific(self, build: Build) -> None:
        self.record(
            build.flavor,
            "Play package visibility",
            lambda: self.runtime_permissions(build),
        )
        self.record(
            build.flavor,
            "Play manual package check and installer fallback",
            lambda: self.manual_check(
                build,
                build.package,
                ("Installed by", "Installer unknown"),
                "13-manual-check",
            ),
        )
        self.record(
            build.flavor,
            "Play manual check finds a third-party launcher app",
            lambda: self.manual_check(
                build,
                self.builds[1].package,
                ("Installed by", "Installer unknown"),
                "13b-manual-check-third-party",
            ),
        )
        self.record(
            build.flavor,
            "Play full scan unavailable",
            lambda: self.play_no_full_scan(build),
        )

    def play_no_full_scan(self, build: Build) -> str:
        self.open_inventory(build)
        self.wait_for_node(
            "Full scan unavailable in this build",
            exact=True,
            timeout=20,
        )
        self.assert_absent("Scan all apps", exact=True)
        return "no full-scan action"

    def reinstall_play_with(self, installer: str | None) -> None:
        build = self.builds[0]
        self.adb("uninstall", build.package, check=False)
        remote = "/data/local/tmp/fossscout-play-smoke.apk"
        self.adb("push", build.apk, remote, timeout=120)
        output = self.sh("pm", "install", "-r", remote, timeout=180).stdout
        if "Success" not in output:
            raise Failure(f"installer fixture install failed:\n{output}")
        if installer:
            output = self.sh(
                "pm",
                "set-installer",
                build.package,
                installer,
                timeout=60,
            ).stdout
            if "Success" not in output:
                raise Failure(f"installer fixture set failed:\n{output}")
        label = installer or "none"
        self.save(
            f"installer-fixtures/{label}-package-dumpsys.txt",
            self.sh("dumpsys", "package", build.package, check=False).stdout,
        )

    def vanilla_specific(self, build: Build) -> None:
        self.record(
            build.flavor,
            "Vanilla package visibility",
            lambda: self.runtime_permissions(build),
        )
        self.record(
            build.flavor,
            "Vanilla full scan",
            lambda: self.full_scan(build),
        )

        def installer_fixtures() -> str:
            results: list[str] = []
            cases: list[tuple[str | None, tuple[str, ...], str]] = [
                (None, ("Installed by ADB", "Installer unknown"), "14-installer-adb"),
                ("com.android.shell", ("Installed by ADB",), "15-installer-known"),
                (
                    "com.android.settings",
                    ("Installed by com.android.settings",),
                    "16-installer-other",
                ),
            ]
            for installer, expected, screenshot in cases:
                self.reinstall_play_with(installer)
                results.append(
                    self.manual_check(
                        self.builds[1],
                        self.builds[0].package,
                        expected,
                        screenshot,
                    )
                )
            results.append(
                self.manual_check(
                    build,
                    "com.android.settings",
                    ("Preinstalled system app",),
                    "18-installer-system",
                )
            )
            return "; ".join(results)

        self.record(build.flavor, "installer source labels", installer_fixtures)

    def full_scan(self, build: Build) -> str:
        self.open_inventory(build)
        self.tap_text("Scan all apps", exact=True, timeout=20)
        self.wait_for_node("Full device scan", exact=True, timeout=180)
        self.wait_for_node("Last checked", exact=False, timeout=20)
        status, _ = self.wait_for_any_text(
            ("Complete", "Partial coverage"),
            exact=True,
            timeout=20,
            scroll=False,
        )
        self.capture(build, "19-full-scan")
        self.save_ui(build, "19-full-scan")
        return f"full scan completed: {status}"

    def check_logcat(self, build: Build) -> None:
        logs = self.adb("logcat", "-d", "-v", "threadtime").stdout
        self.save(f"{build.flavor}/logcat.txt", logs)
        lines = logs.splitlines()
        for index, line in enumerate(lines):
            if "FATAL EXCEPTION" in line or f"ANR in {build.package}" in line:
                context = "\n".join(lines[index : index + 40])
                if build.package in context or f"ANR in {build.package}" in line:
                    raise Failure(f"{build.package} crashed or ANR'd:\n{context}")

    def run_device(self) -> None:
        self.require_device()
        self.install_initial()
        self.install_f19_fixtures()
        self.adb("logcat", "-c")
        play = self.builds[0]
        self.common_ui(play)
        self.play_specific(play)
        self.f19_specific(play)
        self.record(play.flavor, "no crash or ANR", lambda: self.check_logcat(play))

        self.adb("logcat", "-c")
        vanilla = self.builds[1]
        self.common_ui(vanilla)
        self.vanilla_specific(vanilla)
        self.f19_specific(vanilla)
        self.record(vanilla.flavor, "no crash or ANR", lambda: self.check_logcat(vanilla))

    def write_report(self) -> None:
        date = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
        lines = [
            "# Device smoke test (CI emulator)",
            "",
            f"- Date: {date}",
            "- Runner: GitHub Actions `ubuntu-24.04`, API 36 AOSP x86_64 emulator",
            "- Builds: debug APKs from the current commit",
        ]
        if self.device:
            for key, value in self.device.items():
                lines.append(f"- {key}: {value}")
        lines += [
            "",
            "## Result",
            "",
            f"**{'FAIL' if self.error else 'PASS'}**"
            + (f" — {self.error}" if self.error else ""),
            "",
            "## Checks",
            "",
            "| Scope | Check | Result | Detail |",
            "| --- | --- | --- | --- |",
        ]
        for check in self.checks:
            detail = check.detail.replace("|", "\\|").replace("\n", " ")
            lines.append(
                f"| {check.scope} | {check.name} | {check.status} | {detail} |"
            )
        lines += ["", "## Not covered by this run", ""]
        lines += [f"- {item}" for item in NOT_COVERED]
        lines += [
            "",
            "## Artifacts",
            "",
            "- `playstore/` and `vanilla/` screenshots and UI dumps",
            "- `*/logcat.txt`, `*/package-dumpsys.txt`, and `static/` APK inspection",
            "- `*/f19-audit.json` comparison audit exports",
            "- `installer-fixtures/` package dumps for the installer cases",
            "- `report.md`, `device.txt`, and the emulator log",
            "",
            "This partial run does not replace the full checklist in "
            "[`docs/DEVICE_SMOKE_TEST.md`]"
            "(https://github.com/FossScout/FossScout/blob/main/docs/DEVICE_SMOKE_TEST.md).",
        ]
        self.save("report.md", "\n".join(lines) + "\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--play-apk", type=Path, required=True)
    parser.add_argument("--vanilla-apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--aapt2")
    parser.add_argument("--f19-store-url")
    parser.add_argument("--f19-tracker-apk", type=Path)
    parser.add_argument("--static-only", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    smoke = Smoke(args)
    try:
        smoke.static_checks()
        if not smoke.static_only:
            smoke.run_device()
    except Exception as exc:
        smoke.error = str(exc)
        traceback.print_exc()
    finally:
        smoke.write_report()
    if smoke.error:
        print(f"\nDevice smoke test failed: {smoke.error}", file=sys.stderr)
        return 1
    print("\nDevice smoke test passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
