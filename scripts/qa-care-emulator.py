#!/usr/bin/env python3
"""Exercise real Android time/idle/force-stop behavior on a selected QA AVD.

Install the normal debug application and instrumentation APK first. This script creates
one temporary account through the app's repository and restores the original session.
It never clears application data. It restores device settings in finally blocks.
"""

import argparse
from datetime import datetime, timedelta
import json
import re
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
from zoneinfo import ZoneInfo


class CareEmulatorQA:
    package = "com.example.bloomybeauty"
    test_class = "com.example.bloomybeauty.reminders.CareDeviceScenarioTest"

    def __init__(self, serial):
        self.adb = ["adb", "-s", serial]
        if self.shell("getprop", "ro.kernel.qemu").strip() != "1":
            raise RuntimeError("This script requires an emulator")
        name = self.run("emu", "avd", "name").splitlines()[0]
        if name != "Pixel_6":
            raise RuntimeError("Use Pixel_6 selected by the user; do not create or select another AVD")
        self.name = name
        if self.shell("getprop", "sys.boot_completed").strip() != "1":
            raise RuntimeError("Wait for sys.boot_completed=1 before running QA")
        for package in (self.package, self.package + ".test"):
            if not self.shell("pm", "path", package).strip().startswith("package:"):
                raise RuntimeError("Install the normal debug app and instrumentation APK first: " + package)

    def run(self, *args):
        return subprocess.check_output(self.adb + list(args), text=True, stderr=subprocess.STDOUT, timeout=45)

    def shell(self, *args):
        return self.run("shell", *args)

    def fixture(self, action, lead=120):
        output = self.shell("am", "instrument", "-w", "-e", "class", self.test_class,
                            "-e", "careScenario", action, "-e", "careLeadSeconds", str(lead),
                            self.package + ".test/androidx.test.runner.AndroidJUnitRunner")
        if "OK (1 test)" not in output:
            raise RuntimeError(output)
        for line in output.splitlines():
            prefix = "INSTRUMENTATION_STATUS: care_snapshot="
            if prefix in line:
                snapshot = json.loads(line.split(prefix, 1)[1])
                if action != "cleanup":
                    self.kill_app_process()
                return snapshot
        raise RuntimeError("Missing fixture snapshot: " + output)

    def kill_app_process(self):
        # An instrumentation process can still be finishing after am returns.
        # Make system broadcasts start a clean app process, without Force stop.
        current = subprocess.run(self.adb + ["shell", "pidof", self.package], text=True, capture_output=True)
        if not current.stdout.strip():
            return
        killed = subprocess.run(self.adb + ["shell", "run-as", self.package, "kill", "-9", *current.stdout.split()], text=True, capture_output=True)
        if killed.returncode != 0 and "No such process" not in killed.stderr:
            raise RuntimeError(killed.stderr)

    def scheduled(self, routine_id):
        xml = self.shell("run-as", self.package, "cat", "shared_prefs/care_alarms.xml")
        return [node.text for node in ET.fromstring(xml).iter("string")
                if node.text and node.text.startswith(str(routine_id) + ":")]

    def wait_schedule(self, routine_id, expected):
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            entries = self.scheduled(routine_id)
            if len(entries) == 1 and int(entries[0].split(":")[-1]) == expected:
                return entries
            time.sleep(0.5)
        raise AssertionError(f"Schedule did not become {expected}: {self.scheduled(routine_id)}")

    def has_alarm(self):
        # Match an active alarm's tag line, not a cancelled alarm in dumpsys history.
        pattern = r"^\s+tag=\*walarm\*:" + re.escape(self.package + ".action.CARE_REMINDER") + r"\s*$"
        return re.search(pattern, self.shell("dumpsys", "alarm"), re.MULTILINE) is not None

    def expected(self, routine, zone):
        now = int(self.shell("date", "+%s").strip())
        local = datetime.fromtimestamp(now, ZoneInfo(zone))
        for offset in range(8):
            candidate = (local + timedelta(days=offset)).replace(
                hour=routine["minute"] // 60, minute=routine["minute"] % 60, second=0, microsecond=0)
            if routine["days"] & (1 << candidate.weekday()) and candidate.timestamp() > now:
                return int(candidate.timestamp() * 1000)
        raise AssertionError("No selected day")

    def environment(self):
        zone = self.shell("getprop", "persist.sys.timezone").strip()
        automatic = self.shell("settings", "get", "global", "auto_time").strip()
        baseline = int(self.shell("date", "+%s").strip()) * 1000
        began = time.monotonic()
        changed_clock = False
        result = {}
        try:
            before = self.fixture("prepare", 600)
            sdk = before["sdk"]
            if sdk >= 33:
                self.shell("pm", "grant", self.package, "android.permission.POST_NOTIFICATIONS")
            result["before"] = before
            for target_zone in ("UTC", "America/New_York", zone):
                self.shell("cmd", "alarm", "set-timezone", target_zone)
                # Starting instrumentation can terminate a running app receiver. Wait
                # for the real system broadcast to finish before taking a snapshot.
                self.wait_schedule(before["routineId"], self.expected(before, target_zone))
                snapshot = self.fixture("inspect")
                snapshot["scheduled"] = self.wait_schedule(before["routineId"], snapshot["expectedNext"])
                assert snapshot["zone"] == target_zone, snapshot
                result.setdefault("timezone", []).append(snapshot)
            self.shell("settings", "put", "global", "auto_time", "0")
            changed_clock = True
            forward = baseline + 3_600_000 + int((time.monotonic() - began) * 1000)
            self.shell("cmd", "alarm", "set-time", str(forward))
            self.wait_schedule(before["routineId"], self.expected(before, zone))
            snapshot = self.fixture("inspect")
            assert snapshot["now"] >= forward - 1000, snapshot
            snapshot["scheduled"] = self.wait_schedule(before["routineId"], snapshot["expectedNext"])
            assert snapshot["lastOccurrence"] is None and not snapshot["notified"], snapshot
            result["clockForward"] = snapshot
            restored = baseline + int((time.monotonic() - began) * 1000)
            self.shell("cmd", "alarm", "set-time", str(restored))
            self.wait_schedule(before["routineId"], self.expected(before, zone))
            snapshot = self.fixture("inspect")
            snapshot["scheduled"] = self.wait_schedule(before["routineId"], snapshot["expectedNext"])
            assert abs(snapshot["now"] - restored) < 30_000, snapshot
            result["clockRestored"] = snapshot
            self.shell("am", "force-stop", self.package)
            deadline = time.monotonic() + 5
            while self.has_alarm() and time.monotonic() < deadline:
                time.sleep(0.2)
            assert not self.has_alarm(), "Alarm remained after Force stop"
            self.shell("am", "start", "-n", self.package + "/com.example.bloomybeauty.MainActivity")
            deadline = time.monotonic() + 20
            while not self.has_alarm() and time.monotonic() < deadline:
                time.sleep(0.5)
            assert self.has_alarm(), "Alarm was not restored after launch"
            snapshot = self.fixture("inspect")
            assert snapshot["routineId"] == before["routineId"] and snapshot["lastOccurrence"] is None
            result["forceStopRecovery"] = snapshot
            return result
        finally:
            if changed_clock:
                self.shell("cmd", "alarm", "set-time", str(baseline + int((time.monotonic() - began) * 1000)))
            self.shell("settings", "put", "global", "auto_time", automatic)
            self.shell("cmd", "alarm", "set-timezone", zone)
            self.fixture("cleanup")

    def doze(self, timeout):
        result = {}
        try:
            before = self.fixture("prepare", 120)
            if before["sdk"] >= 33:
                self.shell("pm", "grant", self.package, "android.permission.POST_NOTIFICATIONS")
            result["before"] = before
            whitelist = self.shell("dumpsys", "deviceidle", "whitelist")
            assert not any(self.package in row.split(",") for row in whitelist.splitlines()), "App must not be whitelisted"
            self.shell("input", "keyevent", "3")
            self.shell("dumpsys", "battery", "unplug")
            self.shell("input", "keyevent", "223")
            result["forceIdle"] = self.shell("dumpsys", "deviceidle", "force-idle").strip()
            assert self.shell("dumpsys", "deviceidle", "get", "deep").strip() == "IDLE"
            start = time.monotonic()
            next_progress = 0
            while time.monotonic() - start < timeout:
                elapsed = int(time.monotonic() - start)
                if elapsed >= next_progress:
                    print(f"Doze {self.name}: {elapsed}s, waiting for real alarm", flush=True)
                    next_progress += 30
                notifications = self.shell("dumpsys", "notification")
                if f"tag=care:{before['routineId']} " in notifications:
                    result["idleAtDelivery"] = self.shell("dumpsys", "deviceidle", "get", "deep").strip()
                    assert result["idleAtDelivery"] == "IDLE"
                    result["waitSeconds"] = elapsed
                    result["after"] = self.fixture("inspect")
                    assert result["after"]["lastOccurrence"] is not None and result["after"]["notified"]
                    return result
                time.sleep(5)
            raise TimeoutError("No real alarm observed within the QA timeout; no timing guarantee is inferred")
        finally:
            self.shell("dumpsys", "deviceidle", "unforce")
            self.shell("dumpsys", "battery", "reset")
            self.shell("input", "keyevent", "224")
            self.shell("input", "keyevent", "82")
            self.fixture("cleanup")

    def tests(self, scope):
        arguments = []
        if scope == "features":
            classes = ["data.care.CareRoutineProviderTest", "data.care.CareMigrationTest",
                       "events.OrderChangesTest", "feature.care.CareScreenTest"]
            arguments = ["-e", "class", ",".join(self.package + "." + item for item in classes)]
        elif scope == "data":
            arguments = ["-e", "package", self.package + ".data"]
        command = self.adb + ["shell", "am", "instrument", "-r", "-w"] + arguments + [
            self.package + ".test/androidx.test.runner.AndroidJUnitRunner"]
        print(f"Running {scope} tests on {self.name}", flush=True)
        output = subprocess.check_output(command, text=True, stderr=subprocess.STDOUT, timeout=600)
        return parse_test_results(output, int(self.shell("getprop", "ro.build.version.sdk").strip()))


def parse_test_results(output, sdk):
    cases = []
    status = {}
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            field, separator, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            if separator:
                status[field] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.split(":", 1)[1])
            if code != 1:
                cases.append({"class": status.get("class"), "name": status.get("test"), "code": code,
                              **({"error": status["stack"]} if "stack" in status else {})})
            status = {}
    if not cases or "INSTRUMENTATION_CODE: -1" not in output:
        raise RuntimeError(output)
    failed = [case for case in cases if case["code"] not in (0, -3, -4)]
    if failed:
        raise AssertionError(json.dumps(failed, ensure_ascii=False))
    return {"sdk": sdk, "tests": len(cases), "passed": sum(case["code"] == 0 for case in cases),
            "skipped": sum(case["code"] in (-3, -4) for case in cases), "failures": 0, "cases": cases}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--scenario", choices=("environment", "doze", "features", "data", "regression"), required=True)
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    qa = CareEmulatorQA(args.serial)
    result = {"avd": qa.name, "scenario": args.scenario, "passed": False}
    try:
        if args.scenario == "environment":
            result["details"] = qa.environment()
        elif args.scenario == "doze":
            result["details"] = qa.doze(args.timeout)
        else:
            result["details"] = qa.tests(args.scenario)
        result["passed"] = True
    except Exception as error:
        result["error"] = str(error)
        raise
    finally:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
        print(json.dumps({"scenario": args.scenario, "passed": result["passed"], "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
