#!/usr/bin/env bash
# Build the shaded GausVibe jar and install it to the Vibe plugin dir.
# After this, ~/.vibe/plugins/gausvibe/gausvibe.jar is the runtime artifact;
# the git repo is not needed at tool-execution time.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PLUGIN_DIR="${HOME}/.vibe/plugins/gausvibe"
JAR_VERSION="$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' "${REPO_ROOT}/pom.xml" | head -1)"
JAR="${REPO_ROOT}/target/gausvibe-${JAR_VERSION}-all.jar"

cd "${REPO_ROOT}"
mvn -q clean package

mkdir -p "${PLUGIN_DIR}"
cp "${JAR}" "${PLUGIN_DIR}/gausvibe.jar"
echo "Installed ${JAR} -> ${PLUGIN_DIR}/gausvibe.jar"
