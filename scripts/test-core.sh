#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Supply Gson/Fastutil jars from the Gradle cache (or another local installation).
: "${CORE_TEST_CLASSPATH:?Set CORE_TEST_CLASSPATH to Gson and Fastutil jars}"
mkdir -p build/core-tests
mapfile -t sources < <(find src/main/java/dev/pocketlod/{cache,config,generation} -name '*.java' ! -name DistantGenerator.java)
sources+=(src/main/java/dev/pocketlod/core/{TerrainTile,SurfaceScan,SurfaceMesher,WorkBudget}.java src/test/java/dev/pocketlod/core/CoreTests.java)
if [[ -n "${ECJ_JAR:-}" ]]; then
    java -jar "$ECJ_JAR" -17 -cp "$CORE_TEST_CLASSPATH" -d build/core-tests "${sources[@]}"
else
    javac --release 17 -cp "$CORE_TEST_CLASSPATH" -d build/core-tests "${sources[@]}"
fi
java -ea -cp "build/core-tests:$CORE_TEST_CLASSPATH" dev.pocketlod.core.CoreTests
