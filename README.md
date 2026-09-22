# MobGrab (Fabric)

Sneak + right-click a mob to pick it up as a head item; right-click a block to set it back
down with everything about it intact — health, equipment, trades, profession, variant, age,
name, anger, passengers.

This is the Fabric port of the [MobGrab Paper plugin](https://thconman.github.io/MobGrab/).
The plugin needs a Paper server; this does not, so it works in a **singleplayer world,
including hardcore**, with no server, no operator and no cheats.

- **Requires:** Minecraft **26.2 or newer** - Fabric Loader **0.18.4+** - Fabric API - Java 25
- **Side:** server-side. A client copy is optional and adds a menu and key mappings.

## Installing

Drop `mobgrab-<version>.jar` and Fabric API into your `mods` folder.

"Server-side only" means the mod runs on the logical server. In a singleplayer or hardcore
world that server is the one inside your own game, so a normal single install is all it takes.
On a dedicated server, only the server needs it — players can join with a stock Minecraft
install and still see the mob heads, because the heads are ordinary player heads carrying a
skin texture rather than custom items.

## Using it

| Action | Result |
|---|---|
| Sneak + right-click a mob | Picks it up into your inventory |
| Right-click a block holding a mob item | Puts the mob back on that face |

Nothing else is required. There is no GUI to open, no permission to grant, and no command
you have to run first — which is the point, since a hardcore world usually has no way to run
one.

## What the item tells you

The head carries the mob's full state as tooltip lore, ported from the plugin:

- Type, custom name, exact health, and tamed owner
- **Villager**: profession, level, and every trade — `6x Pumpkin -> Emerald` — with
  exhausted trades struck through, and enchanted-book trades naming the enchantment
- **Variants**: horse colour and markings, cat type and collar, wolf coat and collar, sheep
  colour, axolotl, frog, parrot, fox, rabbit, mooshroom, cow, pig, chicken, salmon size,
  zombie nautilus, llama colour and strength, panda gene, tropical fish pattern and both
  colours, shulker colour, copper golem weathering
- **Wandering traders** list their stock, and **zombie villagers** keep the profession they
  will return to when cured
- **Endermen** name the block they are carrying
- **States**: charged creeper, sheared, screaming goat and missing horns, bee nectar/stung/angry,
  slime and magma cube size, phantom size, pufferfish puff, shivering strider, active creaking,
  baby, sitting, and anger on any mob that tracks it
- **Equipment**: every worn and held item with its enchantments, including body and saddle
  slots — horse armour, llama carpets, wolf armour, saddles and happy ghast harnesses

Turn it off with `showLore: false` if you prefer a bare head.

Trade lines carry the plugin's emoji item icons, generated from its own mapping
(161 icons, 13 shortened names). Everything else matches it too,
including the wording where vanilla and Bukkit disagree: a rabbit still reads "Salt And
Pepper" rather than vanilla's "Salt", and a parrot "Red" rather than "Red Blue".

## The menu

`/mobgrab gui` opens a chest menu of every mob, each as its own head, green for grabbable and
red for not. Click one to toggle it; the bottom row pages through and the book shows the
current settings. Non-operators can look but not change anything.

This is an ordinary container screen, so it needs **nothing installed on the client** — a
stock Minecraft client on a dedicated server gets it, exactly as the Paper plugin's did.
Nothing in it can be taken out: every slot is a painted button, and both normal and
shift-clicks are intercepted.

## Optional client menu and keys

Installing MobGrab on a client as well adds a menu and two key mappings. None of it is
required: the mod is still driven entirely from the server, a stock client can still join,
and a client with the mod can still join a server without it.

| Key | Default | Does |
|---|---|---|
| Open MobGrab menu | `G` | Mob toggles and the main settings, as the server currently has them |
| Grab mob you are looking at | unbound | Grabs without sneaking. Left unbound so it never collides with an existing key |

The menu shows the server's state, not a local copy. Pressing a toggle sends a request and
the display only changes when the server's reply arrives, so it can never show a change the
server refused. Non-operators see it read-only. On a server without MobGrab, the keys say so
rather than doing nothing.

Everything a client sends is re-checked on the server — permission, reach, config and
cooldown — so binding the grab key is no more trusted than an ordinary right-click.

## Presets

Save a grabbed mob under a name and hand copies out later. Useful for a villager with
particular trades. Stored in `config/mobgrab-presets.json` as readable SNBT.

| Command | Description |
|---|---|
| `/mobgrab preset save <name>` | Save the mob item in your main hand |
| `/mobgrab preset list` | List presets |
| `/mobgrab preset give <players> <name>` | Give a preset mob item |
| `/mobgrab preset delete <name>` | Remove a preset |

## Configuration

Everything lives in `config/mobgrab.json`, written on first launch and re-read by
`/mobgrab reload`. Options added by a later MobGrab version appear in the file automatically
on the next start, with their defaults, rather than staying invisible.

| Option | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `enableCommands` | `true` | Register the `/mobgrab` command tree at all. Grabbing and placing work either way. Applies on restart |
| `requireSneak` | `true` | Require sneaking to grab. Turning this off makes a bare right-click grab, which collides with trading, riding and shearing |
| `requireOp` | `false` | Restrict to operators. **Off** by default so singleplayer and hardcore need no setup |
| `cooldownSeconds` | `1.0` | Per-player spacing between grabs, counted in game ticks so a paused world does not burn it |
| `blacklistMode` | `false` | Meaning of the `mobs` list (see below) |
| `allowNewMobsByDefault` | `true` | What to do with a mob the list does not mention — how mobs from a newer Minecraft arrive |
| `fireproofItems` | `true` | Mob items survive fire and lava |
| `itemDamageImmunity` | `["minecraft:is_fire"]` | Damage-type tags the item ignores while fireproofing is on |
| `keepMobNameOnItem` | `true` | A named pet keeps its name on the item |
| `showLore` | `true` | Health, age and villager profession on the tooltip |
| `disabledDimensions` | `[]` | Dimensions where MobGrab is fully off, e.g. `"minecraft:the_nether"` |
| `pickupSound` / `placeSound` | chicken egg / enderman teleport | Sound ids, with `…Volume` and `…Pitch` alongside |
| `pickupParticle` / `placeParticle` | smoke / happy villager | Particle ids, with `…Count` alongside |
| `mobs` | 91 entries | Per-mob toggles, keyed by entity id |

### Fireproofing

`fireproofItems` gives the item the same `damage_resistant` component netherite gear uses, so
a pocketed mob survives being dropped in lava. Because it is a real item component rather
than an event handler, it holds up in every situation the game applies fire damage in.

`itemDamageImmunity` decides which damage that covers. It takes damage-type tags, so you can
widen it:

```json
"itemDamageImmunity": ["minecraft:is_fire", "minecraft:is_explosion"]
```

Toggling `fireproofItems` affects **newly grabbed** mobs. Items grabbed earlier keep the
component they were made with.

### The mob list

An explicit entry in `mobs` always wins, so both modes agree on every mob the file lists.
The modes only differ for ids that are absent — a mob added by a newer Minecraft version —
where blacklist mode admits it and whitelist mode does not, unless `allowNewMobsByDefault`
overrides that.

Ids that do not exist on your version are ignored rather than treated as errors, so one
config file works across 26.1.2 and everything after it. Giant, warden, wither and the ender
dragon ship disabled.

## Commands

Optional, and all of them only restate what the config file already controls. Everything
except `status` needs permission level 2 (`Permissions.COMMANDS_GAMEMASTER`).

| Command | Description |
|---|---|
| `/mobgrab` or `/mobgrab help` | List the subcommands you can run |
| `/mobgrab status` | Current settings and how many mobs are grabbable |
| `/mobgrab reload` | Re-read `config/mobgrab.json` |
| `/mobgrab fireproof <true\|false>` | Toggle fireproofing for future grabs |
| `/mobgrab enable\|disable <mob>` | Toggle one mob, by entity id |

## Notes on hardcore

- Nothing needs enabling. `requireOp` is off, so the mod works with cheats disabled.
- The cooldown counts game ticks, not wall-clock time, so pausing does not consume it.
- Grabbing checks for a free inventory slot *before* removing the mob, so a full inventory
  can never delete one.
- The stored UUID is dropped when a mob becomes an item, so a duplicated item cannot spawn
  two entities claiming to be the same one and corrupt leads, mounts or pet ownership.
- Grabbing a ridden mob (a chicken jockey, a saddled horse) takes the whole stack: the
  riders go into the item rather than being left standing in the world, so nothing is
  duplicated when it is placed again.
- A mob with a **player** on it cannot be grabbed at all.

## Differences from the Paper plugin

| Plugin | This mod |
|---|---|
| Paper server required | Runs anywhere, including singleplayer hardcore |
| Permission nodes via LuckPerms | A single `requireOp` switch |
| Admin chest GUI | `/mobgrab gui`, a real container menu that needs nothing on the client |
| Bedrock forms | Not applicable — no Geyser in a mod context |
| WorldGuard / GriefPrevention / PlotSquared hooks | None — no mod equivalent to hook |
| RoseStacker stack handling | None |
| Villager preset engine | `/mobgrab preset`, saved from the item in your hand rather than the mob you are looking at |
| Item lore describing the mob | Ported in full, minus the emoji icons on trade lines |
| `/mobgrab update` self-updater | Not ported — mod launchers handle updates |
| Fireproofing via a damage event listener | The vanilla `damage_resistant` item component |
| Entity state as an SNBT string | The entity's own save data, stored verbatim |

## Building

Requires JDK 25. The Gradle wrapper is included.

```bash
./gradlew build
```

The jar lands in `build/libs/`. To build straight into an instance:

```bash
./gradlew build -PmodsDir=/path/to/.minecraft/mods
```

To build and test against another Minecraft version without editing anything:

```bash
./gradlew build -Pminecraft_version=26.2 -Pfabric_api_version=0.156.0+26.2
```

`tools/bincompat.py` checks that a jar built against one version still resolves against
another, walking superclasses and interfaces the way the JVM does. Run it after any version
bump:

```bash
python tools/bincompat.py build/libs/mobgrab-1.2.0.jar <built-version-jar> <target-version-jar> "MC 26.2"
```

## License

MIT — see [LICENSE](LICENSE).
