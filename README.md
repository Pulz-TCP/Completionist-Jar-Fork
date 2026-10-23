# Completionist Jar (Fabric fork)

A Fabric port of **Completionist Jar** for **Minecraft 26.1.2**. Place a jar to claim it, build 3x3x4 and 6x6x8 shells of jars to grow it, and watch the marbles fill as you complete advancements.

This fork contains the Advancement Jar only. The original mod's FTB Quests "Quest Jar" and Jade tooltip are not ported.

## Credit and license

Based on [Completionist Jar](https://www.curseforge.com/minecraft/mc-mods/completionist-jar-your-progress-in-a-jar) by Laszlo Takacs (NeoForge 1.21.1), which is MIT licensed. The original source was not published, so this is a rewrite for Fabric guided by the original's behaviour; textures and marble layouts come from the original jar. Released under the MIT license, see `LICENSE`.

## Differences from the original

- Fabric instead of NeoForge, Minecraft 26.1.2 instead of 1.21.1.
- Fabric has no "advancement earned" event, so each online player's progress is polled once a second.
- Held and dropped jars currently show an empty jar model (no marble fill yet).

## Building

Requires JDK 25.

```
./gradlew build
```

The jar is written to `build/libs/`.