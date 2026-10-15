# Changelog

## 0.4.0 — build-tested release

- Fabric 1.20.1 / Java 17 project, Gradle wrapper and CI build workflow.
- Immutable median-based terrain pyramid and section-based chunk capture.
- Greedy voxel meshing with atomic budgets, surface lips and directional AO.
- Dynamic-buffer renderer, circular overlap, recenter scheduling and custom shaders.
- Single-flight integrated-server SURFACE/FEATURES generation with cancellable tickets.
- Primitive caches/queues, checksummed persistence and dirty-tracked autosaving.
- Video Settings integration, F8, exact radius controls, presets and client commands.
- Dependency-light core regression suite and manual release-validation checklist.

Full pinned Minecraft/Fabric build and core tests pass on GitHub CI.
Mobile and in-game graphics validation remain pending; see README.

### Release fixes

- Removed default helper methods from the server accessor mixin so Mixin
  annotation processing correctly recognizes it as a pure accessor interface.
- Added release JAR validation and build-gated publishing with SHA-256 checksums.
