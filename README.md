# OmniMiner

**OmniMiner** is a Fabric mod for Minecraft 1.20.1 that mines a whole connected group of ores in one action, even when the group contains different ore types.

## Features

- Breaks face-connected ore blocks of different types as one vein.
- Activates automatically when mining an ore with a pickaxe.
- Uses the player's actual mining interaction for each additional block, preserving normal drops and tool durability behavior.
- Supports vanilla ores and modded ores registered in the common `c:ores` block tag.
- Limits one vein to 64 ore blocks to prevent runaway scans.

## Build

- Minecraft 1.20.1
- Java 17
- Fabric Loader 0.15.11+
- Fabric API 0.92.2+1.20.1

Run `gradle build` (or `./gradlew build` when a Gradle wrapper is added) to build the mod. The jar is generated in `build/libs`.

## License

MIT
