#!/usr/bin/env python3
"""Run installed Android tests only on the existing Pixel_6; restore temporary animation settings."""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes', required=True)
    parser.add_argument('--log', type=Path, required=True)
    parser.add_argument('--arg', action='append', default=[], metavar='KEY=VALUE', help='Additional instrumentation argument (repeatable).')
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--package', default='com.example.bloomybeauty.demo')
    parser.add_argument('--disable-animations', action='store_true')
    parser.add_argument('--timeout', type=int, default=1200)
    parser.add_argument('--adb', default=str(Path(os.environ.get('ANDROID_SDK_ROOT', Path.home() / 'Library/Android/sdk')) / 'platform-tools/adb'))
    args = parser.parse_args()
    extra_args=[]
    for argument in args.arg:
        key, separator, value = argument.partition('=')
        if not separator or not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*', key) or key == 'class':
            parser.error('Use --arg KEY=VALUE; class is supplied by --classes.')
        extra_args.extend(['-e',key,value])
    if not args.serial.startswith('emulator-'):
        parser.error('This project runs QA only on the existing Pixel_6 emulator.')

    def adb(*parts):
        return subprocess.run([args.adb, '-s', args.serial, *parts], capture_output=True, text=True, check=True, timeout=20).stdout.strip()

    avd = adb('emu', 'avd', 'name').splitlines()[0].strip()
    if avd != 'Pixel_6':
        raise RuntimeError(f'Refusing to run on {avd!r}; use the existing Pixel_6.')
    metadata = {'avd': avd, 'serial': args.serial, 'package': args.package, 'classes': args.classes, 'instrumentationArgs': args.arg,
                'api': adb('shell', 'getprop', 'ro.build.version.sdk'),
                'passed': False, 'startedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'animationsTemporarilyDisabled': args.disable_animations}
    keys = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']
    original = {key: adb('shell', 'settings', 'get', 'global', key) for key in keys} if args.disable_animations else {}
    metadata['originalAnimations'] = original
    args.log.parent.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    passed = False
    restore_errors = []

    def interrupted(_signal, _frame):
        raise KeyboardInterrupt()

    signal.signal(signal.SIGTERM, interrupted)
    try:
        for key in original:
            adb('shell', 'settings', 'put', 'global', key, '0')
        print(f'Running installed tests on {avd} ({args.serial}); log: {args.log}', flush=True)
        with args.log.open('w') as log:
            result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', args.classes, *extra_args,
                                     args.package + '.test/androidx.test.runner.AndroidJUnitRunner'], stdout=log, stderr=subprocess.STDOUT, timeout=args.timeout)
        output = args.log.read_text()
        match = re.search(r'OK \((\d+) tests?\)', output)
        passed = result.returncode == 0 and match is not None and 'FAILURES!!!' not in output
        metadata.update(commandExitCode=result.returncode, passed=passed, tests=int(match.group(1)) if match else None,
                        passedCases=len(re.findall(r'INSTRUMENTATION_STATUS_CODE: 0\s*$',output,re.M)),
                        assumedOrIgnoredCases=len(re.findall(r'INSTRUMENTATION_STATUS_CODE: -[34]\s*$',output,re.M)))
    finally:
        restored = {}
        for key, value in original.items():
            try:
                if value == 'null':
                    adb('shell', 'settings', 'delete', 'global', key)
                else:
                    adb('shell', 'settings', 'put', 'global', key, value)
                restored[key] = adb('shell', 'settings', 'get', 'global', key)
                if restored[key] != value:
                    restore_errors.append(f'{key}: expected {value}, got {restored[key]}')
            except Exception as error:
                restore_errors.append(f'{key}: {error}')
        metadata.update(restoredAnimations=restored, restoreErrors=restore_errors, elapsedSeconds=round(time.monotonic() - started, 3))
        args.log.with_suffix('.environment.json').write_text(json.dumps(metadata, indent=2) + '\n')
        print(json.dumps({'passed': passed, 'animationsRestored': not restore_errors, 'log': str(args.log)}), flush=True)
    return 0 if passed and not restore_errors else 1


if __name__ == '__main__':
    sys.exit(main())
