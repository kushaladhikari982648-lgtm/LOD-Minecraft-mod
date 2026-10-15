# Release validation checklist

## Current evidence

- Portable core: **178 passing assertions** on a Java 17 runtime, ECJ 3.33.0,
  Fastutil 8.5.9, Gson 2.10. No Minecraft stubs are used by this test runner.
- Tests cover median/color filtering, EMPTY handling, immutability, pyramid indexing,
  negative-height scanning, empty-section complexity, low-FPS pacing, single-flight
  epochs, primitive keys/queues, bounded eviction, unchanged-cache dirty tracking,
  circular generation plans, greedy flat roofs, axis-aligned cliffs, skirts, lips,
  neighbor culling, detail rings, near-circle corners, whole-tile limits, configuration
  clamping/scaling/persistence, cache CRC/truncation rejection, v1 migration and disk caps.
- Yarn/Fabric source signature review was performed for integration APIs.
- **Not performed:** full Loom build, mixin application, real shader compilation,
  Minecraft launch, driver/mobile tests, frame-time or heap benchmarks.
- `./gradlew --version` fails at the Gradle distribution download in the sandbox
  with an SSL/network error; Fabric's Maven host is also unreachable.

## Before publishing v0.4.0

1. Run `./gradlew --no-daemon clean build` with a JDK 17 and unrestricted dependencies.
   Confirm the remapped mod JAR has Java class major version 61, correct metadata,
   all four mixins and all shader resources. Resolve any compile/remapping errors.
2. Launch Fabric 1.20.1 with the pinned API and loader. Check logs for mixin and
   shader errors. Open Video Settings, F8 and every command/menu entry.
3. On MJ Launcher and Zalith, record device, launcher, Java runtime, GL wrapper,
   heap allocation, vanilla render distance, preset, FPS and memory use. Also test
   a low-end PC. Do not infer compatibility from the core tests.
4. Test each slider, +/- controls and exact text (8, 96, 6, 64, blanks, non-numbers,
   out-of-range values). Restart and verify persistence. Check generation consent,
   detail switching, stats, hide caves and aliases.
5. Explore plains, cherry forest, canopy, mountains, snow layers, rivers/coasts,
   deep ocean, negative world heights, Nether and End. Inspect block-aligned roofs,
   tree median transitions, texture tints, water shading, grass/snow cliff lips and AO.
6. Test vanilla distance 4/8/12, all graphics levels and custom radii 8/32/64/96.
   Walk straight and diagonally at chunk boundaries. Cap FPS at 5/10/15 and simulate
   slow chunk builds/lag spikes. Watch for exposed near holes, overlap fighting,
   horizon disappearance and stale recenter jobs; verify long-range teleport behavior.
7. Enable generation on a disposable singleplayer save. Verify low-FPS pacing still
   advances, FEATURES upgrades SURFACE tiles, heap guards pause safely, movement
   removes tickets, and disabling/disconnecting/stopping never leaves live tickets.
   Check near vanilla chunks are not starved. Measure server tick cost and heap use.
8. Join a multiplayer server: verify no distant generation and no world mutation.
   Disconnect/reconnect, switch dimensions/worlds quickly while disk I/O or a future
   is pending; stale results must never appear in a different session.
9. Verify disk reload, CRC-corrupt files, radius/cache eviction, unchanged autosaves,
   clear during a pending write, and clean shutdown with a dirty cache. Confirm
   cache size is within each dimension's configured quota.
10. Test vanilla renderer and Sodium, Iris without/with a shader pack, and available
    mobile drivers. Check shader-pack pause behavior, GL state restoration and
    fabulous graphics framebuffer/depth behavior.

Publish artifacts only after these checks; list exact environments and known
limitations in the release notes rather than promising universal glitch-free FPS.
