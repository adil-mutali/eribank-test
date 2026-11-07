#!/usr/bin/env bash
# Runs inside the booted Android emulator (called by android-emulator-runner).
set -x
export JAVA_HOME="$JAVA_HOME_8_X64"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$GITHUB_WORKSPACE"

adb devices
nohup appium --log-level info > appium.log 2>&1 &

for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:4723/wd/hub/status > /dev/null; then break; fi
  sleep 2
done
curl -s http://127.0.0.1:4723/wd/hub/status

cd Assign
# the assignment files use Windows (CRLF) line endings; gradlew must be LF to run on Linux
sed -i 's/\r$//' gradlew
chmod +x gradlew
mkdir -p results
./gradlew run --no-daemon --console=plain 2>&1 | tee ../gradle.log
ls -la results
