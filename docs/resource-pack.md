# The resource pack: models, sounds and music

Everything a player **sees or hears that Minecraft doesn't ship** comes from the resource pack:
custom item models, your own sounds, combat music. The plugin only ever refers to things by **name**
(`custom_model: bard_icon`, `sound: jkvttresourcepack:dice_roll`), so adding art or audio never needs
code: put the file in the pack, name it in YAML, `/dm reload`.

- The pack lives in `ResourcePack/` locally. It's **not** in git (`.gitignore`).
- Its namespace is `jkvttresourcepack` (`ItemUtil.RESOURCE_PACK_NAMESPACE`).
- A **datapack can't add models, sounds or music**. Those are files on the player's computer, and only
  a resource pack puts them there. (Datapacks are server-side: dimensions, loot, recipes.)
- **Particles:** nothing can add a new particle type. A pack can only repaint an existing one. Spells
  pick from Minecraft's own in `DMContent/DamageTypes.yml` and `visual:` (see `authoring-spells.md`).

## Models (items, menu tiles, creatures)

A model needs the whole chain, or it renders as a purple-and-black placeholder, which looks worse
than the vanilla item it replaced:

```
assets/jkvttresourcepack/items/<name>.json          → points at the model
assets/jkvttresourcepack/models/item/<name>.json    → the model, points at the texture
assets/jkvttresourcepack/textures/item/<name>.png   → the texture
```

Then `custom_model: <name>` on the race, class, weapon or item (or `model:` on a creature). Only set it
once the texture exists. The rules are in `CLAUDE.md` → *Icons & Materials*.

## Sounds

1. Put the .ogg in the pack (Minecraft only plays .ogg; mono for a sound that should come from a
   place, stereo is fine for music):
   `assets/jkvttresourcepack/sounds/dice_roll.ogg`
2. Name it in the pack's `assets/jkvttresourcepack/sounds.json` (create it if it isn't there; one file
   holds all of them):
   ```json
   {
     "dice_roll": { "sounds": [ "jkvttresourcepack:dice_roll" ] },
     "wolf_pack": { "sounds": [ "jkvttresourcepack:wolf_pack_1", "jkvttresourcepack:wolf_pack_2" ] }
   }
   ```
   The key (`dice_roll`) is the sound's name; each entry in `sounds` is a file path under `sounds/`,
   without `.ogg`. List several and Minecraft picks one at random each time.
3. Use `jkvttresourcepack:dice_roll` anywhere `DMContent/Sounds.yml` takes a sound, then `/dm reload`:
   ```yaml
   moments:
     dice_roll: { sound: jkvttresourcepack:dice_roll }
   board:
     wolf_pack: { name: "Wolves", sound: jkvttresourcepack:wolf_pack, range: 80 }
   ```

A player without the pack simply hears nothing for that sound. It's never an error.

## Music (combat music)

The same as a sound, with two differences:

```json
"music.skyrim_combat": { "sounds": [ { "name": "jkvttresourcepack:music/skyrim_combat", "stream": true } ] }
```

- **`"stream": true`** for anything long. Minecraft then plays it as it loads instead of loading the
  whole file first (without it, a long track stutters or is cut off).
- **`length:`** in `Sounds.yml` is the track's length in seconds, so the plugin knows when to start it
  again (the server can't read an .ogg's length). Round up a second or two:
  ```yaml
  music:
    default: skyrim
    tracks:
      skyrim: { name: "Skyrim combat", sound: jkvttresourcepack:music.skyrim_combat, length: 152 }
  ```

A creature can bring its own track with `combat_music: skyrim` in its YAML (`authoring-entities.md`).

## Testing your changes

**Test with your local copy; you don't need to upload anything:**

1. Copy (or link) `ResourcePack/` into your Minecraft `resourcepacks` folder and turn it on under
   Options → Resource Packs.
2. After each change to the pack, press **F3 + T** in game to reload resource packs.
3. After each change to `DMContent/*.yml`, run `/dm reload`.

If the server also sends players a pack (the `resource-pack=` URL in `server.properties`), that one loads
**above** your local one. New sounds and models still work, since `sounds.json` files from different
packs are merged. But a file that's in both (the same model or texture) shows the server's version, so
for those, turn the server pack off while testing (Server list → Edit → Server Resource Packs: Disabled).

## Sharing it with the players

Only once it works: zip the pack (the zip's top level is `pack.mcmeta` and `assets/`, not a folder
holding them), host it where the server's `resource-pack=` URL points, and **update
`resource-pack-sha1`** in `server.properties` to the new zip's SHA-1. Without a new SHA-1, players'
games keep the copy they downloaded last time. On Windows:

```
certutil -hashfile ResourcePack.zip SHA1
```

Players get the new pack the next time they join. The server's `pack.mcmeta` format has to match the
Minecraft version (the Paper 26.2 section of `TEST_PLAN.md` has the numbers).
