#!/bin/bash
set -euo pipefail

# Only run in remote Claude Code on the web sessions
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# Ensure the Gradle wrapper is executable
chmod +x "${CLAUDE_PROJECT_DIR}/gradlew"

cd "${CLAUDE_PROJECT_DIR}"

# Set JAVA_HOME to JDK 17 if not already set to a compatible version
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  elif [ -d /usr/lib/jvm/temurin-17 ]; then
    export JAVA_HOME=/usr/lib/jvm/temurin-17
  fi
  echo "export JAVA_HOME=\"${JAVA_HOME}\"" >> "${CLAUDE_ENV_FILE}"
fi

# Pre-download Gradle wrapper and all compile/test dependencies by running
# the unit-test compile step (mirrors what CI does). This warms the Gradle
# daemon cache so subsequent test/lint runs are fast.
./gradlew \
  :app:compileDebugUnitTestKotlin \
  --no-configuration-cache \
  --daemon \
  -Dorg.gradle.jvmargs="-Xmx2g" \
  -q
