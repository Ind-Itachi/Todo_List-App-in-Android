#!/usr/bin/env bash
# Builds a signed Counter APK without Gradle or Android Studio.
#
# Needs (Ubuntu/Debian):  sudo apt-get install aapt dalvik-exchange zipalign apksigner default-jdk curl
#
# It compiles against the Android 14 (API 34) framework. Without an Android SDK, that framework is
# taken from Robolectric's "android-all" jar on Maven Central (downloaded once to ~/.cache).
# To use a real SDK instead: ANDROID_JAR=$ANDROID_HOME/platforms/android-34/android.jar ./build.sh
#
# Signing key: set KEYSTORE / KS_PASS to use your own; otherwise a local key is
# created at ~/.android/counter.jks. Keep using the same key, or Android will
# refuse to install a new build over the old one.
set -euo pipefail

cd "$(dirname "$0")"
ALL_VER=14-robolectric-10818077
if [ -z "${ANDROID_JAR:-}" ]; then
  ANDROID_JAR="$HOME/.cache/counter-build/android-all-$ALL_VER.jar"
  if [ ! -f "$ANDROID_JAR" ]; then
    echo "• Downloading Android 14 framework (~130 MB, once)"
    mkdir -p "$(dirname "$ANDROID_JAR")"
    curl -fL -o "$ANDROID_JAR.part" \
      "https://repo.maven.apache.org/maven2/org/robolectric/android-all/$ALL_VER/android-all-$ALL_VER.jar"
    mv "$ANDROID_JAR.part" "$ANDROID_JAR"
  fi
fi
KEYSTORE="${KEYSTORE:-$HOME/.android/counter.jks}"
KS_PASS="${KS_PASS:-android}"
OUT=build
SRC=src/main

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "• Compiling resources"
aapt package -f -m \
  -M "$SRC/AndroidManifest.xml" \
  -S "$SRC/res" \
  -I "$ANDROID_JAR" \
  -J "$OUT/gen" \
  -F "$OUT/unsigned.apk"

echo "• Compiling Java"
javac -nowarn -Xlint:-options --release 8 \
  -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  $(find "$SRC/java" "$OUT/gen" -name '*.java')

echo "• Dexing"
dalvik-exchange --dex --min-sdk-version=26 --output="$OUT/dex/classes.dex" "$OUT/classes"
(cd "$OUT/dex" && zip -q -u ../unsigned.apk classes.dex)

echo "• Aligning"
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  echo "• Creating signing key at $KEYSTORE"
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias counter -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Counter" >/dev/null 2>&1
fi

echo "• Signing"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
  --min-sdk-version 26 --out "$OUT/Counter.apk" "$OUT/aligned.apk"
apksigner verify --verbose "$OUT/Counter.apk" | head -5

echo "Done: $(pwd)/$OUT/Counter.apk"
