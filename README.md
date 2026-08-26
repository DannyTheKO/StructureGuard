# StructureGuard `1.2.0`

**Automatic WorldGuard protection for ANY structure — vanilla, modded, or datapack.**

> 1.21+ BoundingBox-accurate | On-demand chunk-load protection | Zero-lag async detection

## Features

- 🎯 **On-Demand Protection** — Structures protected when chunks load, no scanning required
- 📦 **BoundingBox Mode** — Region = structure BB expanded by `padding` on all sides (Y clamped to world height)
- 🌍 **Universal Compatibility** — Paper, Spigot, Folia, NeoForge, Fabric hybrid servers
- 🎨 **Pattern Matching** — Protect `minecraft:*`, `cobblemon:*_gym`, or just `*` for everything
- ⚡ **Zero Lag** — Async NMS detection, sync region creation, batched DB writes
- 🔧 **Full WorldGuard Integration** — All flags supported, including Extra Flags

## Quick Start

```bash
# Install WorldGuard, drop StructureGuard-1.2.0.jar in /plugins/, restart

# Protect all villages with 5-block padding
/sg protect minecraft:village 5

# Protect ALL structures
/sg protect * 5

# Set flags
/sg flag minecraft:village pvp deny

# Check status
/sg status
```

## Commands

### Discovery
| Command | Description |
|---------|-------------|
| `/sg listall [page]` | Show all structure types in registry |
| `/sg find <structure>` | Locate nearest structure (live scan + DB fallback) |
| `/sg info` | Structure info (BB) at your location |
| `/sg probe [chunkX chunkZ]` | Verbose NMS probe for a chunk |
| `/sg methods` | Dump chunk Map methods (diagnostic) |

### Protection Rules
| Command | Description |
|---------|-------------|
| `/sg protect <pattern> [padding]` | Add protection rule (BB + padding) |
| `/sg unprotect <pattern> [--clear]` | Remove rule (--clear removes regions) |
| `/sg enable <pattern> [padding]` | Enable / create rule |
| `/sg disable <pattern>` | Disable rule (keeps in config) |
| `/sg rules` | List all rules |

### Flags & Regions
| Command | Description |
|---------|-------------|
| `/sg flag <pattern> <flag> <value>` | Set WorldGuard flags on rules & regions |
| `/sg addowner <pattern> <player\|g:group>` | Add region owner |
| `/sg addmember <pattern> <player\|g:group>` | Add region member |
| `/sg removeowner <pattern> <player\|g:group>` | Remove region owner |
| `/sg removemember <pattern> <player\|g:group>` | Remove region member |
| `/sg clearregions <pattern> [world]` | Remove WorldGuard regions (`*` = all `sg_*`) |
| `/sg resetworld <world> confirm` | Clear all data for a world (for resets) |

### Utility
| Command | Description |
|---------|-------------|
| `/sg list <pattern> [page]` | List protected structures (DB) |
| `/sg status` | System status (WorldGuard, rules, DB, chunk queue, debug) |
| `/sg reload` | Reload config and sync flags to existing regions |
| `/sg debug` | Toggle debug mode |

## Pattern Examples

| Pattern | Matches |
|---------|---------|
| `minecraft:village` | Exactly villages |
| `minecraft:*` | All vanilla structures |
| `cobblemon:*_gym` | All Cobblemon gyms |
| `*` | Everything |

## Configuration (`src/main/resources/config.yml`)

```yaml
debug: false
process-existing-chunks: true
disabled-worlds:
  # - resource_world

default-padding: 5

default-flags:
  use: allow
  interact: allow
  creeper-explosion: deny
  tnt: deny
  deny-message: "&cThis structure is protected!"

# Managed via /sg protect — region = BB expanded by padding
protected-structures:
  # "minecraft:village":
  #   enabled: true
  #   padding: 5
  #   priority: 10
  #   flags:
  #     pvp: deny
```

### Config Sync

Edit flags in `config.yml` then run `/sg reload` to apply changes to all existing regions. No need to recreate regions!

### Disabled Worlds

Add world names to `disabled-worlds` to completely skip protection in those worlds (resource/mining/creative worlds).

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `structureguard.admin` | All commands | op |
| `structureguard.find` | Use /sg find | op |
| `structureguard.listall` | Use /sg listall | false |
| `structureguard.info` | Use /sg info | false |
| `structureguard.teleport` | Clickable teleport links | op |
| `structureguard.helper` | Read-only (find/list/listall/info) | false |
| `structureguard.moderator` | helper + scan/protect/flag/teleport | false |

## Project Structure (`1.2.0` modular layout)

```
src/main/java/com/structureguard/
├── StructureGuardPlugin.java
├── config/          ConfigManager, ProtectionRule
├── database/        StructureDatabase + model/StructureInfo
├── structure/       StructureFinder + model/{StructureResult,ScanState} + nms/NmsReflectionCache
├── region/          RegionManager, RegionFlagService
├── listener/        ChunkLoadListener
├── command/         SgCommand (router) + SgSubCommand + subcommand/* (19 per-file commands)
└── util/            PatternMatcher, ChunkUtil
```

## Build

```bash
# Windows
BUILD.bat
# or
./gradlew clean shadowJar

# Output: build/libs/StructureGuard-1.2.0.jar
```

Version is defined in `build.gradle` (`version = '1.2.0'`) and injected into `plugin.yml` (`${version}`) at build time. The JAR is now versioned (`StructureGuard-1.2.0.jar`) for clean release tracking.

## Requirements

- Minecraft 1.21+
- WorldGuard 7.0+
- Java 21+

## License

MIT License
