# How the marble collection system works

*A note for Laszlo Takacs, author of the original Completionist Jar.*

You said you like the idea of a "collect" system, so this is a walkthrough of how it is built in this fork, written so you can lift any of it into your own mod. It covers only the parts that are new here. Everything about building jar structures, the marble layouts and the rendering is still yours, and the original idea of a jar that fills as you play is what all of this hangs off. Thank you for publishing it under MIT.

Honest caveats first:

- This fork targets **Fabric on Minecraft 26.1.2**. Your mod is NeoForge 1.21.1. The *ideas* carry over directly, but the API names differ, and I have only tested this on Fabric. Where I mention a NeoForge equivalent I say "roughly", because I have not run it there.
- Minecraft 1.21.1 predates "special item models" (added later), so the item-rendering section below would stay a `BlockEntityWithoutLevelRenderer` in your version.

## The idea in one paragraph

Instead of the jar reading your advancement progress directly, **completing an advancement hands you a marble**. Marbles sit in a personal storage (the *Marble Pouch*) until you carry them to a jar and drop them in. The jar then shows what you have *deposited*. All of that state lives on the **player**, not on the jar, so breaking or losing a jar loses nothing: the next jar you place just displays the same ledger.

```
advancement done --> marble minted --> pouch (player data) --> you drop it in a jar --> ledger (player data)
                                                                                           |
                                                          every jar you own displays <-----+
```

## 1. Per-player data: `MarbleData`

[`MarbleData.java`](../src/main/java/dev/completionist/jar/MarbleData.java) is one object per player holding:

| field | purpose |
|---|---|
| `pouch` | 27 item stacks: the marbles waiting to be deposited |
| `awarded` | ids of advancements that have already paid out a marble |
| `deposited` | marbles dropped into jars, counted per advancement tab (the **ledger**) |
| `pending` | marbles that did not fit in a full pouch |
| `pouchGranted` | whether the starting pouch has been given |

It is attached to the player with Fabric's data attachment API ([`ModAttachments.java`](../src/main/java/dev/completionist/jar/ModAttachments.java)):

```java
AttachmentRegistry.<MarbleData>create(id, builder ->
    builder.persistent(MarbleData.CODEC).copyOnDeath().initializer(MarbleData::new));
```

- `persistent(codec)` saves it with the player's data, so it is per world.
- `copyOnDeath()` carries it to the respawned player.
- NeoForge has the same concept (roughly: `AttachmentType.builder(...).serialize(codec).copyOnDeath()` on the player), so this part ports almost line for line.

The codec is an ordinary `RecordCodecBuilder`. Item stacks use `ItemStack.OPTIONAL_CODEC` (the "optional" matters, because empty slots must serialise too).

## 2. Earning marbles without ever paying twice

[`MarbleAwarder.java`](../src/main/java/dev/completionist/jar/MarbleAwarder.java) is the whole mint. For each advancement that has a display and is done, it checks `awarded`; if the id is new it records it and counts one marble toward that advancement's **root tab**.

Because "already paid" is tracked by advancement id (not by counting), the method is **idempotent**: you can run it as often as you like. That gives you two things for free:

- **Backfill.** Install the mod in a world where the player already has five advancements and the first run mints five marbles.
- **No exploits.** Revoking and re-granting an advancement cannot mint a second marble.

Fabric has no "advancement earned" event, so [`CompletionistJar.java`](../src/main/java/dev/completionist/jar/CompletionistJar.java) simply calls the awarder once a second per online player (and on join). NeoForge's `AdvancementEarnEvent` would be a cleaner trigger for you; I would keep the id-set idea even so, for the backfill.

Marbles that do not fit are kept as a per-tab count in `pending` and moved into the pouch as space frees up, so nothing is ever lost.

## 3. The marble item and its colour

[`MarbleItem.java`](../src/main/java/dev/completionist/jar/MarbleItem.java) is one item, but each stack carries a data component, [`MarbleTab`](../src/main/java/dev/completionist/jar/MarbleTab.java): the tab's id and its display title. Two marbles stack only if they come from the same tab, so you get a handful of stack types (one per tab), not one per advancement.

The title is stored *in* the component because the tooltip is built in common code that cannot look advancements up.

**Colouring without client code.** The colour is written into the vanilla `CUSTOM_MODEL_DATA` component when the stack is created, and the item definition reads it back as a tint ([`items/marble.json`](../src/main/resources/assets/completionist_jar/items/marble.json)):

```json
"tints": [ { "type": "minecraft:custom_model_data", "index": 0, "default": 16777215 } ]
```

One grey marble texture, any colour, and no Java on the client. Tab colours come from [`MarbleColors.java`](../src/main/java/dev/completionist/jar/MarbleColors.java): hand-picked for the five vanilla tabs, and a colour derived from the tab id's hash for any modded tab, so modded tabs get a stable colour automatically.

## 4. The Marble Pouch (an "ender chest" for marbles)

The pouch item does not hold anything itself. Using it opens a 3-row chest screen onto the **player's** `pouch` list:

- [`PouchItem.java`](../src/main/java/dev/completionist/jar/PouchItem.java) opens the menu on the server.
- [`PouchContainer.java`](../src/main/java/dev/completionist/jar/PouchContainer.java) is a `Container` that is a *live window* onto that list rather than a copy. This matters: marbles minted while the screen is open appear in it, and a stale copy cannot overwrite them when it closes.
- [`PouchMenu.java`](../src/main/java/dev/completionist/jar/PouchMenu.java) extends the vanilla `ChestMenu`, so the client uses the stock chest screen and no custom screen code is needed.

Because the data is on the player, losing the pouch item loses nothing; any pouch opens the same storage.

**A gotcha worth knowing:** I first tried to make the pouch marble-only by overriding `Container.canPlaceItem`. It does not work for menus: vanilla's plain `Slot.mayPlace` returns `true` and never asks the container. The restriction has to be in the **slot**. `PouchMenu` therefore builds the chest menu and then swaps each pouch slot for a `Slot` subclass whose `mayPlace` only accepts marbles. The server enforces it, including shift-click, because `moveItemStackTo` consults `mayPlace`. (The client's copy of the menu is the stock one, so it may briefly show a refused item before the server corrects it.)

## 5. Dropping a marble into a jar

All of it is in `MarbleItem.useOn`:

1. Check the clicked block is a jar and the player owns it (the owner uuid your jar already stored).
2. `MarbleData.get(player).deposit(tab, count)`: just bump the ledger.
3. Shrink the stack, then ask every jar the player owns to refresh.
4. Play `block.amethyst_block.fall`, with the pitch rising slightly as the jar fills.

Right-click drops one marble and holding the button repeats it, which gives a nice "pouring" feel. Sneak-right-click drops the whole stack.

**Another gotcha:** a player who is sneaking skips the *block's* `useItemOn` entirely and goes straight to the item's `useOn`. So the deposit logic lives in the item, and the jar block's `useItemOn` simply returns `PASS` for marbles so that non-sneaking clicks also fall through to the item.

## 6. How a jar displays "deposited" instead of "progress"

This is the smallest change to your original design, which is a good sign that the design was sound. Your jar already cached a `ProgressSnapshot` (completed, total, per-tab) and refreshed it when the owner was online. I only changed *where the numbers come from*:

[`ProgressSnapshot.compute`](../src/main/java/dev/completionist/jar/ProgressSnapshot.java) now takes `completed` from the owner's ledger (capped at the number of advancements in each tab) instead of from `PlayerAdvancements`. `total` is still the count of advancements that have a display. Everything downstream (the block entity, the item cache, structure merging, the fill-by-percentage maths) is unchanged.

Marble colours in the jar: [`JarRenderer.marbleColors`](../src/client/java/dev/completionist/jar/client/JarRenderer.java) splits the visible marbles between tabs in proportion to what was deposited from each, so a jar fills in coloured bands, bottom first.

## 7. The jar item with its fill (special item model)

On recent Minecraft versions an item can be drawn by a registered "special model renderer" instead of a block-entity-without-level renderer:

- [`items/advancement_jar.json`](../src/main/resources/assets/completionist_jar/items/advancement_jar.json) says `"type": "minecraft:special"` and names the renderer `completionist_jar:jar`. Its `base` model supplies only the display transforms.
- [`JarItemRenderer.java`](../src/client/java/dev/completionist/jar/client/JarItemRenderer.java) reads the percentage from the stack's `JAR_CONTENTS` component and draws the same shell and fill slab your original item renderer drew. `extractArgument` returns a small record, and the game redraws the item exactly when that record changes.
- It is registered once at client start-up.

For 1.21.1 you would keep your `BlockEntityWithoutLevelRenderer`; only the data it reads (the deposit ledger) changes.

## One more lesson: do not touch the world inside a chunk-load event

Unrelated to marbles, but it cost me a world: on Fabric I ran "self-heal the structure" logic from the block-entity-loaded event. That code read blocks in the chunk that was still loading, so the server ended up waiting on itself and the world hung on "Preparing spawn area". The fix ([`AdvancementJarTracker.queueLoad`](../src/main/java/dev/completionist/jar/AdvancementJarTracker.java)) just records the position and does the work on a later tick when the chunk is fully loaded. Your NeoForge `onLoad` runs later in the load, so you were probably not affected, but if you ever move that logic earlier, this is the symptom to watch for.

## Where to look

| to see how... | read |
|---|---|
| per-player data | `MarbleData`, `ModAttachments` |
| minting / backfill | `MarbleAwarder` |
| tab-coloured items | `MarbleItem`, `MarbleTab`, `MarbleColors`, `items/marble.json` |
| the pouch | `PouchItem`, `PouchContainer`, `PouchMenu` |
| depositing | `MarbleItem.useOn` |
| jar shows the ledger | `ProgressSnapshot.compute`, `JarRenderer.marbleColors` |
| jar item with fill | `JarItemRenderer`, `items/advancement_jar.json` |

Questions or ideas welcome, and feel free to use any of it.
