#!/usr/bin/env bash
# Build the runnable GausVibe jar (shaded, all dependencies included).
#
# The version IS the git commit count, so every artifact traces back to an
# exact repository state. It is embedded in the jar manifest and shown by
# `java -jar gausvibe.jar version`.
#
# Usage: scripts/build-artifact.sh [--install]
#   --install  also copy the jar to ~/.vibe/plugins/gausvibe/gausvibe.jar
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(git -C "${REPO_ROOT}" rev-list --count HEAD)"

cd "${REPO_ROOT}"
mvn -q clean package -Drevision="${VERSION}"

JAR="target/gausvibe-${VERSION}-all.jar"
echo "Built ${JAR} (version ${VERSION} = git commit count)"

if [[ "${1:-}" == "--install" ]]; then
  PLUGIN_DIR="${HOME}/.vibe/plugins/gausvibe"
  mkdir -p "${PLUGIN_DIR}"
  cp "${JAR}" "${PLUGIN_DIR}/gausvibe.jar"
  echo "Installed ${JAR} -> ${PLUGIN_DIR}/gausvibe.jar"
fi
