# PocketLOD cache format

All integers are big-endian. This repository had no prior implementation or cache
fixtures; the v1 layout below is the explicitly supported migration contract.
Importing a different mod's cache format is not supported.

## Header (12 bytes)

| Offset | Type | Meaning |
|---:|---|---|
| 0 | int32 | magic `0x504C4431` (`PLD1`) |
| 4 | int32 | version (1 or 2) |
| 8 | int32 | record count, 0–32768 |

## Version 2

Each record has a **649-byte payload** followed by a 4-byte CRC32 of that payload.

| Bytes | Meaning |
|---:|---|
| 8 | chunk key: unsigned low 32 bits = signed x; high 32 bits = signed z |
| 1 | detail (0 LEGACY, 1 SURFACE, 2 FEATURES) |
| 128 | 64 signed int16 surface-top heights, z-major |
| 256 | 64 int32 top RGB colors (`0x00RRGGBB`) |
| 256 | 64 int32 side RGB colors |
| 4 | CRC32, stored as int32 bit pattern |

`Short.MIN_VALUE` is EMPTY. Stored height is the top face (block y + 1).
Only base samples are persisted; all 21 coarser samples are rebuilt on load.

## Version 1 migration

Each record is a **104-byte payload** and a 4-byte CRC32:
chunk key (8), 16 signed heights (32), 16 RGB colors (64). Samples form a z-major
4×4 grid. Each legacy sample expands into a 2×2 region of the new 8×8 grid; top
colors also become side colors; detail is LEGACY. The next changed-cache save
writes v2. Migration is tested with a synthesized v1 fixture.

## Safety and persistence

Readers bound file size before allocation, require exact header-derived size,
validate count/version/detail, and reject checksum failures, truncation and trailing
data. A corrupt cache is ignored with a warning, not partly published. Writers cap
record count by the disk budget, keep newest entries, write a sibling `.tmp`, and
replace the old file atomically where supported. On filesystems without atomic
rename, replacement is the fallback; power-loss durability is not guaranteed.

I/O is serialized on one worker. A periodic unchanged snapshot does not write.
World changes and shutdown enqueue the latest dirty snapshot; shutdown waits up to
five seconds. A process crash/forced launcher kill can lose unsaved recent captures.
