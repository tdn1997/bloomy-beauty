#!/bin/sh
# Isolated Firebase services on the host; Android QA reuses the user's existing Pixel_6.
set -eu
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_ROOT"
CLOUD_JAVA_HOME=${JAVA_HOME:-'/Applications/Android Studio.app/Contents/jbr/Contents/Home'}
if [ ! -x "$CLOUD_JAVA_HOME/bin/java" ]; then
    echo 'Set JAVA_HOME to a JDK 21 installation.' >&2
    exit 1
fi
export JAVA_HOME="$CLOUD_JAVA_HOME"
export PATH="$CLOUD_JAVA_HOME/bin:$PATH"
if [ ! -x firebase-tests/node_modules/.bin/firebase ] || [ ! -d functions/node_modules ]; then
    echo 'Run npm ci --prefix firebase-tests and npm ci --prefix functions first.' >&2
    exit 1
fi
if [ -f .local/cloud-emulator-data/firebase-export-metadata.json ]; then
    exec firebase-tests/node_modules/.bin/firebase emulators:start --config firebase.json --project demo-bloomy-commerce --only auth,firestore,functions --import .local/cloud-emulator-data --export-on-exit .local/cloud-emulator-data
else
    exec firebase-tests/node_modules/.bin/firebase emulators:start --config firebase.json --project demo-bloomy-commerce --only auth,firestore,functions --export-on-exit .local/cloud-emulator-data
fi
