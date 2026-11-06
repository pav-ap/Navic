#!/usr/bin/env bash
# Build a debug APK that actually installs.
#
# Why this exists: the vendored ffmpeg only ships prebuilt static libs for
# arm64-v8a and armeabi-v7a, so the default debug ABI set (which adds x86 and
# x86_64) fails in configureCMakeDebug. The workaround is to pin the ABI set
# with -Pandroid.injected.build.abi, but that path makes AGP stop at the
# unsigned intermediate APK in build/intermediates/apk/... and never write a
# signed one to build/outputs/. Sideloading that intermediate gives Android's
# unhelpful "app not installed" with no reason, because it has no signature.
#
# So: build with the injected ABI, then zipalign + apksigner against the debug
# keystore ourselves.
#
# Usage: scripts/build-debug-apk.sh [-o output.apk] [-p package_suffix]
set -euo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$PWD"

ABI="${ABI:-arm64-v8a}"
OUT="${OUT:-$REPO_ROOT/navic-bpm-debug.apk}"
VARIANT="${VARIANT:-DefaultDebug}"
TASK="assemble${VARIANT}"

# Toolchain: prefer env, fall back to local.properties / common locations.
if [ -z "${JAVA_HOME:-}" ]; then
	if [ -f "$REPO_ROOT/local.properties" ]; then
		JAVA_HOME="$(sed -n 's/^java.home=//p' "$REPO_ROOT/local.properties" | tail -1)"
	fi
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -f "$REPO_ROOT/local.properties" ]; then
	ANDROID_HOME="$(sed -n 's/^sdk.dir=//p' "$REPO_ROOT/local.properties" | tail -1)"
fi
: "${JAVA_HOME:?set JAVA_HOME or add java.home= to local.properties}"
: "${ANDROID_HOME:?set ANDROID_HOME or add sdk.dir= to local.properties}"

BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
[ -x "$BUILD_TOOLS/apksigner" ] || { echo "no apksigner in $BUILD_TOOLS" >&2; exit 1; }

export JAVA_HOME ANDROID_HOME
export PATH="$JAVA_HOME/bin:$PATH"

KEYSTORE="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
	echo "no debug keystore at $KEYSTORE (run any debug build once, or set DEBUG_KEYSTORE)" >&2
	exit 1
fi

echo "==> building $TASK for $ABI"
MARKER="$(mktemp)"
sleep 1
touch "$MARKER"
./gradlew ":app:$TASK" \
	-Dorg.gradle.jvmargs="-Xmx6g -Xms1g" \
	-Pkotlin.compiler.execution.strategy=in-process \
	-Pnavic.abis="$ABI" \
	-Pandroid.injected.build.abi="$ABI" \
	-Pandroid.injected.testOnly=false \
	--console=plain "$@"

UNSIGNED="$(find app/build/intermediates/apk -name '*.apk' -newer "$MARKER" 2>/dev/null | head -1)"
rm -f "$MARKER"
[ -n "$UNSIGNED" ] || { echo "build produced no fresh APK" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> aligning"
"$BUILD_TOOLS/zipalign" -p -f 4 "$UNSIGNED" "$WORK/aligned.apk"

echo "==> signing"
"$BUILD_TOOLS/apksigner" sign \
	--ks "$KEYSTORE" \
	--ks-key-alias androiddebugkey \
	--ks-pass pass:android \
	--key-pass pass:android \
	--out "$OUT" \
	"$WORK/aligned.apk"

"$BUILD_TOOLS/apksigner" verify "$OUT" >/dev/null || { echo "signature check failed" >&2; exit 1; }

echo "==> ok: $OUT ($(du -h "$OUT" | cut -f1))"
"$BUILD_TOOLS/aapt2" dump badging "$OUT" 2>/dev/null | grep -E '^package|^native-code' || true
