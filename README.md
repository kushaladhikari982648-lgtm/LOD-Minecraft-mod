# PocketLOD 0.4.0

A Fabric **client-side voxel terrain LOD mod for Minecraft Java 1.20.1**, aimed at
4 GB mobile devices (MJ Launcher / Zalith) and low-end PCs. PocketLOD renders
block-aligned surface columns beyond vanilla render distance, not sloped terrain
triangles. It is an independent implementation, not Distant Horizons.

> **Validation status:** the portable core regression suite passes locally. A full
> Fabric/Minecraft build and in-game rendering have **not** been verified in the
> development sandbox: external Gradle/Fabric/Mojang downloads are blocked there.
> The GitHub Actions workflow builds with Java 17 on an unrestricted runner. Treat
> this as a source implementation requiring build and gameplay validation, not a
> tested release binary. No FPS or mobile compatibility guarantee is implied.

## Build and install

Requirements: a **JDK 17**, internet access to Gradle, Fabric Maven, Maven Central,
and Mojang's downloads.

```sh
./gradlew --no-daemon build        # Windows: gradlew.bat build
./gradlew coreTest                # deterministic core regression runner
```

Install the remapped `build/libs/pocketlod-0.4.0.jar` (not the `-sources.jar`) and
**Fabric API 0.92.2+1.20.1** into the launcher's `mods` directory. Use Minecraft
**1.20.1**, Fabric Loader **0.16.14**, and Java **17**. The build pins Yarn
`1.20.1+build.10:v2`, Loom **1.6.12**, Gradle **8.7**, and `options.release = 17`.
This repository does **not** contain a 1.16.5/Java 8 port.

On a physical 4 GB device, leave memory for Android and the graphics driver; do
not assign the entire 4 GB to the Java heap. Choose a heap size appropriate to the
launcher and other installed mods. Begin with a modest vanilla render distance.

## Controls

- **Video Settings → PocketLOD...**, **F8**, or **`/pocketlod menu`**.
- LOD and generation radii each have a slider, minus/plus controls, and an exact
  numeric field. Valid values apply immediately; incomplete/invalid typed values
  are red and leave the previous valid setting unchanged.
- Four graphics levels: LOW, MEDIUM, HIGH, ULTRA.
- Singleplayer generation is **off by default** and asks for confirmation before
  being enabled. It generates and saves **real world chunks**: back up your save.
- Trees+Terrain requests FEATURES; Surface Fast requests SURFACE. Enabling features
  gradually upgrades coarse generated tiles. Live client chunks have full detail.
- Hide caves skips buried-camera rendering; stats display cache and mesh counts.

```text
/pocketlod                         open settings
/pocketlod menu                    open settings
/pocketlod status                  print settings/cache status
/pocketlod toggle                  enable/disable LOD
/pocketlod distance <8..96>        LOD radius in chunks
/pocketlod genradius <6..64>       singleplayer generation radius
/pocketlod quality <low|medium|high|ultra>
/pocketlod preset <mobile4gb|lowendpc|ultra|tiny|balanced>
/pocketlod generation              confirm enabling background generation
/pocketlod generation off          disable and release generation tickets
/pocketlod clear                   confirm clearing this dimension's LOD cache
/pocketlod clear confirm           clear immediately (does not delete world chunks)
```

### Presets

| Setting | 4 GB Mobile (default) | Low-End PC | Ultra |
|---|---:|---:|---:|
| LOD radius (chunks / blocks) | 32 / 512 | 48 / 768 | 64 / 1024 |
| Generation radius (chunks) | 24 | 32 | 48 |
| Cached tiles | 8,192 | 16,384 | 24,576 |
| Quad budget | 49,152 | 81,920 | 131,072 |
| Graphics | HIGH | HIGH | ULTRA |
| Base generation interval | 800 ms | 500 ms | 350 ms |

All three use a 128 MiB minimum free-heap guard and a 48 MiB per-dimension disk
budget. `balanced` aliases `mobile4gb`; `tiny` uses 16/12 chunks, LOW graphics and a
1,200 ms interval. Presets do not silently enable generation.

Custom radii grow cache and quad capacity automatically, up to **32,768 tiles**
and **131,072 quads**. Capacity increases do not make complex terrain unlimited:
whole tiles that cannot fit the hard quad budget are omitted, never half-drawn.
Nearest candidates get priority. A radius beyond known/cached terrain will be
empty until you explore it or singleplayer generation fills it.

## Implementation

- **Terrain:** immutable 8×8 two-block samples plus a compact 85-entry pyramid.
  Coarse cells use a non-empty median height and colors only within ±2 blocks of
  that height, avoiding floating averages between ground and canopies.
- **Capture:** direct chunk-section reads with empty-section skipping, safe
  heightmap selection and no neighboring chunk requests. Quartile biome caching,
  texture-modulated biome colors, shallow/deep water shading and under-block cliff
  colors. Live chunks refresh incrementally to pick up edits.
- **Geometry:** axis-aligned greedy roofs, single-pass cardinal wall strips,
  unknown-edge skirts, surface lips, directional sunlight and vertical AO. Each
  tile is staged in reusable arrays and committed atomically under the quad cap.
- **Rendering:** dynamic VBO, circular near cutout with three-chunk baked overlap,
  continuously updated/clamped camera center, depth bias, dither feather, block-grid
  grain and aerial haze. Stale jobs cancel at three chunks of movement; urgent
  rebuilds get boosted budgets; heavy CPU completion and GPU upload are split
  across frames when an existing mesh is visible. Old geometry remains visible
  during ordinary movement, but is suppressed after a long-range teleport.
- **Generation:** one integrated-server request at a time, explicit level tickets,
  SURFACE/FEATURES futures, server-thread capture, epoch-guarded publication and
  immediate ticket release on >2-block movement or server stopping. Low FPS and
  slow server ticks **pace** generation instead of disabling it. Heap guards and
  explicit user disabling still apply.
- **Persistence:** primitive insertion-ordered cache, deduplicated primitive queues,
  serialized asynchronous disk I/O, CRC32 records, atomic replacement, v1 migration,
  and dirty-version autosaves. Session tokens reject stale disk/generation results.

Settings are in `config/pocketlod.json`. Terrain caches are in
`pocketlod-cache/<sha256-world-or-server-and-dimension>.plod` inside the game folder.
The hash keeps server addresses/save paths out of cache filenames; it is not
anonymization against someone who already knows those values. Disk limits are
**per dimension**, not a global quota across all past servers/worlds.

## Compatibility and limitations

- Multiplayer is capture-only; PocketLOD cannot discover chunks the server has
  never sent. Cached terrain can become stale after server-side changes outside
  your loaded area.
- Active **Iris shader packs pause LOD rendering** to avoid incompatible framebuffer
  and shader behavior; Iris reflection is resolved once. Sodium/mobile GL wrappers
  still require the manual compatibility checks below.
- Generated tiles contain surface columns, not underground caves, block models,
  entities, light propagation, transparent material layers, or full-resolution
  textures. Unknown blocks fall back to their default map color.
- Two-block sampling intentionally loses narrow features. Generation follows real
  Minecraft dependencies; one outstanding request can still cause surrounding
  chunk-generation work. It is not a zero-cost or strictly one-chunk-memory process.
- Visual seam handling and low-FPS behavior are implemented but require in-game
  validation; they are not claimed to be glitch-free on untested render drivers.

## Tests and maintenance

`./gradlew build` runs the dependency-light `CoreTests` runner as part of `check`.
For core-only testing without Minecraft/Gradle downloads, supply local Gson and
Fastutil jars:

```sh
CORE_TEST_CLASSPATH=/path/to/gson.jar:/path/to/fastutil.jar scripts/test-core.sh
# With a Java 17 runtime plus Eclipse ECJ instead of javac:
ECJ_JAR=/path/to/ecj.jar CORE_TEST_CLASSPATH=... scripts/test-core.sh
```

See [docs/VALIDATION.md](docs/VALIDATION.md) for the release checklist and
[docs/CACHE_FORMAT.md](docs/CACHE_FORMAT.md) for persistence details. Do not publish
an untested binary as a validated mobile release.

License: [MIT](LICENSE).
