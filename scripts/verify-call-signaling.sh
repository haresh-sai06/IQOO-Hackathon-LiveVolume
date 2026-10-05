#!/usr/bin/env bash
#
# Verify the LiveVolume call-signaling handshake end to end: boot the Firebase
# emulator suite, point a connected Android device at it, run the instrumented
# suite against real Firestore + real security rules, then tear everything down.
#
# Why a script instead of three remembered commands: each prerequisite fails in
# a way that looks like an unrelated timeout.
#
#   1. firebase-tools v15 requires Java 21+, and resolves `java` through PATH --
#      JAVA_HOME is ignored. The JDK on PATH is frequently 17, so the JetBrains
#      Runtime bundled with Android Studio is used when it is available.
#   2. The device can only reach the emulator through `adb reverse`, and that
#      tunnel is dropped every time the cable is unplugged. Without it every
#      listener stays silent and the suite times out rather than erroring.
#   3. Only loopback cleartext is permitted (app/src/debug/res/xml/
#      network_security_config.xml), so an emulator host alias needs --host.
#
# Usage:
#   scripts/verify-call-signaling.sh                  # device or AVD, full run
#   scripts/verify-call-signaling.sh --unit           # JVM suite only, no device
#   scripts/verify-call-signaling.sh --keep-emulators # leave the suite running
#   scripts/verify-call-signaling.sh --host 10.0.2.2  # skip adb reverse
#
# Exit status is the suite's: 0 means every test passed.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

PROJECT_ID="demo-livevolume"
FIRESTORE_PORT=8080
AUTH_PORT=9099

UNIT_ONLY=0
KEEP_EMULATORS=0
HOST_OVERRIDE=""

# Emulators this script started; left alone when they were already running, so a
# long-lived instance is never killed out from under another terminal.
STARTED_EMULATORS=0

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m warn\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31merror\033[0m %s\n' "$*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --unit) UNIT_ONLY=1 ;;
    --keep-emulators) KEEP_EMULATORS=1 ;;
    --host) shift; HOST_OVERRIDE="${1:-}" ;;
    # Print the header comment and stop at the first line of code.
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
  shift
done

# ---------------------------------------------------------------------------
# Ports and teardown
# ---------------------------------------------------------------------------

port_open() {
  (echo > "/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1
}

# PIDs listening on a port. `uname` decides which tool to reach for because
# Git Bash on Windows has neither lsof nor a native port scanner.
port_pids() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti "tcp:$1" 2>/dev/null || true
  else
    netstat -ano 2>/dev/null \
      | grep -E "[:.]$1[[:space:]]+.*LISTENING" \
      | awk '{print $NF}' | sort -u || true
  fi
}

# Git Bash rewrites a leading `//` back to `/` for native tools, but only while
# its path conversion is on -- and turning that off to protect a Windows path
# elsewhere in the command then breaks the flag instead. Try both spellings so
# the kill works whichever convention is in force.
win_kill() {
  taskkill //F //PID "$1" >/dev/null 2>&1 && return 0
  MSYS_NO_PATHCONV=1 taskkill /F /PID "$1" >/dev/null 2>&1
}

kill_port() {
  local pid
  for pid in $(port_pids "$1"); do
    [ -n "$pid" ] || continue
    if command -v taskkill >/dev/null 2>&1; then
      win_kill "$pid" || true
    else
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
  done
}

# The reverse tunnels are cheap to remove and must not survive the run, or the
# next person inherits a device pointed at a dead port.
remove_reverses() {
  command -v adb >/dev/null 2>&1 || return 0
  adb reverse --remove "tcp:$FIRESTORE_PORT" >/dev/null 2>&1 || true
  adb reverse --remove "tcp:$AUTH_PORT" >/dev/null 2>&1 || true
}

cleanup() {
  local status=$?
  remove_reverses
  if [ "$STARTED_EMULATORS" -eq 1 ] && [ "$KEEP_EMULATORS" -eq 0 ]; then
    log "Stopping the emulator suite"
    kill_port "$FIRESTORE_PORT"
    kill_port "$AUTH_PORT"
  fi
  if [ "$STARTED_EMULATORS" -eq 1 ] && [ "$KEEP_EMULATORS" -eq 1 ]; then
    log "Leaving the emulator suite running (ports $FIRESTORE_PORT / $AUTH_PORT)"
  fi
  exit $status
}
trap cleanup EXIT INT TERM

# ---------------------------------------------------------------------------
# Java 21 for firebase-tools
# ---------------------------------------------------------------------------

java_major() {
  local bin="$1" out
  out="$("$bin" -version 2>&1 | head -1 || true)"
  echo "$out" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p'
}

ensure_java21() {
  local current
  current="$(java_major java)"
  if [ -n "$current" ] && [ "$current" -ge 21 ]; then
    return 0
  fi

  local candidate bin major
  for candidate in \
    "${JAVA_HOME:-}/bin/java" \
    "/c/Program Files/Android/Android Studio/jbr/bin/java" \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" \
    "$HOME/android-studio/jbr/bin/java" \
    $(ls -d /usr/lib/jvm/*/bin/java 2>/dev/null || true)
  do
    [ -n "$candidate" ] || continue
    [ -x "$candidate" ] || continue
    major="$(java_major "$candidate")"
    [ -n "$major" ] || continue
    if [ "$major" -ge 21 ]; then
      bin="$(dirname "$candidate")"
      warn "Using Java $major at $bin (firebase-tools requires 21+, PATH java is too old)"
      PATH="$bin:$PATH"
      export PATH
      return 0
    fi
  done

  warn "No Java 21+ found, so the emulator suite will start with the JDK on PATH"
  warn "(firebase-tools v15 rejects anything older; install JDK 21 or Android Studio's JBR)"
}

# ---------------------------------------------------------------------------
# Emulator suite
# ---------------------------------------------------------------------------

start_emulators() {
  if port_open "$FIRESTORE_PORT" && port_open "$AUTH_PORT"; then
    log "Reusing the emulator suite already listening on $FIRESTORE_PORT / $AUTH_PORT"
    return 0
  fi

  command -v firebase >/dev/null 2>&1 || die "the firebase CLI is not on PATH (npm i -g firebase-tools)"

  ensure_java21

  mkdir -p build/emulator
  log "Starting Firestore + Auth emulators (project $PROJECT_ID)"
  firebase emulators:start --only firestore,auth --project "$PROJECT_ID" \
    > build/emulator/verify-emulator.log 2>&1 &
  STARTED_EMULATORS=1

  local waited=0
  while [ "$waited" -lt 120 ]; do
    if port_open "$FIRESTORE_PORT" && port_open "$AUTH_PORT"; then
      log "Emulators ready after ${waited}s"
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done

  warn "Emulator log follows:"
  tail -25 build/emulator/verify-emulator.log >&2 || true
  die "emulators did not become ready within 120s"
}

# ---------------------------------------------------------------------------
# Target device
# ---------------------------------------------------------------------------

# Echoes the serial of the first usable device. An unauthorised device is a
# distinct failure from no device, and the fix is different.
#
# `|| true` matters: with `set -o pipefail` the grep misses on an empty device
# list, and an assignment from a failing command substitution would abort the
# script before it can explain what is wrong.
find_device() {
  adb devices | sed '1d' | grep -E '[[:space:]]device$' | awk '{print $1}' | head -1 || true
}

setup_target() {
  command -v adb >/dev/null 2>&1 || die "adb is not on PATH (install Android platform-tools)"

  local serial
  serial="$(find_device)"
  if [ -z "$serial" ]; then
    if adb devices | grep -q 'unauthorized'; then
      die "device is attached but unauthorised: accept the 'Allow USB debugging' prompt on the phone"
    fi
    die "no device found. Attach a phone (enable USB debugging) or boot an AVD, then re-run."
  fi

  log "Target device: $serial"

  if [ -n "$HOST_OVERRIDE" ]; then
    HOST_ARG="$HOST_OVERRIDE"
    log "Using host $HOST_ARG (adb reverse skipped)"
    return 0
  fi

  log "Tunnelling emulator ports into $serial with adb reverse"
  adb reverse "tcp:$FIRESTORE_PORT" "tcp:$FIRESTORE_PORT" >/dev/null
  adb reverse "tcp:$AUTH_PORT" "tcp:$AUTH_PORT" >/dev/null
  HOST_ARG="127.0.0.1"
}

# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------

HOST_ARG=""

if [ "$UNIT_ONLY" -eq 1 ]; then
  log "Running the JVM suite (no device or emulator needed)"
  ./gradlew :app:testDebugUnitTest --console=plain
  exit 0
fi

start_emulators
setup_target

log "Running the call-signaling suite on $HOST_ARG"
./gradlew :app:connectedDebugAndroidTest --console=plain \
  "-Pandroid.testInstrumentationRunnerArguments.emulatorHost=$HOST_ARG"
