#!/data/data/com.termux/files/usr/bin/bash
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1

echo "===== VidCut Phase 14 TIGER-DnR benchmark ====="
echo "Build: ./gradlew assembleDebug"
echo "Install APK and download TIGER-DnR models in Auto Dubbing."
echo "Use a 60-second source video and capture logcat."

aDB="$(command -v adb || true)"
if [ -z "$aDB" ]; then
  echo "adb not found; use Android Studio/ADB from another host."
  exit 0
fi
adb logcat -c 2>/dev/null || true
adb logcat -v time | grep -E "TigerAudioSeparator|TIGER|LiteRT|vidcut" | tee phase14-benchmark.log
