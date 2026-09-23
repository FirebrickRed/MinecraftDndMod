# Authoring entities (NPCs, monsters, merchants)

How to add a creature to `DMContent/Entities/`. This is the companion to
[`authoring-spells.md`](authoring-spells.md) and [`authoring-items.md`](authoring-items.md).
Subfolders are scanned recursively (`town/`, `wilderness/beasts/`, `my_campaign/act2/`), so
organise however you like. After editing, run `/dm reload` and read the console.

> **YAML edits don't reach creatures already standing in the world** (#194). A spawned entity
> keeps the template it was spawned from, its name is fixed at spawn, and its shop is a private
> copy. To see a change, remove the creature and spawn it again. Use `/dm entity rename` for names.

---

## The minimum

```yaml
id: alira          # the ONLY required field
```

That spawns, fights, dies and can be looted. Every other field has a default:

| Absent key | Default |
|---|---|
| `name` | the id, prettified (`alira_the_kindler` → "Alira The Kindler") |
| `creature_type` / `size` | humanoid / medium |
| `hit_points` / `hit_dice` | 10 HP |
| `armor_class` | 10 |
| `speed` | 30 |
| `abilities` | all 10 |
| `skills` | none (every skill = the ability modifier) |
| `attacks`, `inventory`, `loot`, `shop` | none |

**The id is permanent.** It's written into every spawned armor stand and looked up when the server
restarts. Changing it orphans creatures already in the world, so choose a name-free id
(`alira`, not `alira_the_kindler`, in case she's renamed at the reveal).

---

## One creature per file, or many

```yaml
# DMContent/Entities/town/alira.yml — ONE creature: `id:` at the root
id: alira
name: "Alira"
```

```yaml
# DMContent/Entities/wilderness/kobolds.yml — MANY: no root `id:`, each key is an id
kobold:
  name: "Kobold"
kobold_sorcerer:
  name: "Kobold Sorcerer"
```

The loader tells the two apart by whether the file has an `id:` at the root.

---

## A full stat block

```yaml
kobold_sorcerer:
  name: "Kobold Sorcerer"
  random_names: ["Zix the Scorched", "Ember-Scale", "Flamecaller Rik"]  # one is picked at spawn

  creature_type: humanoid        # free text; `/dm entity remove type <t>` matches it
  subtype: kobold                # display only
  size: small                    # tiny|small|medium|large|huge|gargantuan (see note below)

  hit_points: 27                 # a whole number…
  # hit_dice: "5d6+10"           # …or dice, rolled per spawn. hit_dice wins if both are set.
  armor_class: 15
  speed: 30

  abilities:                     # FULL names only: `str: 7` is silently ignored
    strength: 7
    dexterity: 15
    constitution: 14
    intelligence: 10
    wisdom: 9
    charisma: 14

  attacks:
    - name: "Dagger"
      item: dagger               # a real weapon id: held while possessed, and lootable
      to_hit: 4
      reach: "5 ft."
      damage: "1d4+2"
      damage_type: piercing
    - name: "Fire Bolt"          # no item: a natural or spell attack
      material: FIRE_CHARGE      # what it shows as in the possessing DM's hotbar
      to_hit: 4
      reach: "120 ft."
      damage: "1d10"
      damage_type: fire

  reactions:                     # free text, listed in the DM's reactions roster
    - "Parry: +2 AC against one melee hit it can see."

  inventory:
    - arcane_focus               # carried gear (and looted, unless you write a loot: section)

  model: "kobold_sorcerer"       # resource-pack model; ONLY if its texture exists
  dm_notes: "Arrogant. Brags about dragon heritage. Focuses spellcasters first."
```

### Field notes

| Field | Notes |
|---|---|
| `hit_points` vs `hit_dice` | `hit_points: 7d8+2` (dice without quotes) is **not** a number. The loader warns and falls back to 10 HP. Put dice in `hit_dice: "7d8+2"`. |
| `armor_class`, `speed` | Must be whole numbers. A blank or text value warns and falls back to the default. |
| `abilities` | Full lowercase names. Modifiers feed saves and checks. **No proficiency bonus is added to entity saves**, so fold it into the score if it matters. |
| `skills` | `{deception: 5}`: the bonus **as the stat block prints it** ("Deception +5"), proficiency included. A skill that isn't listed uses the plain ability modifier, which is how monster stat blocks work. Used by contested checks (`/dm check Zek insight vs Balin deception`). A bad skill name or non-number warns on load. |
| `size` | Stored and shown, but **doesn't scale the body** yet (#194 §6). A gargantuan dragon stands as tall as a kobold. It does scale the DM while possessing. |
| `random_names` | Picked at spawn when you don't pass a name (`/dm entity spawn kobold "Meepo"` overrides it). |
| `model` | Absent means an invisible stand with a floating nameplate. **A model with no texture renders as a purple box**, which is worse than no model. |
| `dm_notes` | DM-only notes (a string; `|` for several lines). Shown in the stat block (`/dm entity info`) and in `/dm view` (quick and full). Add more in-game on one spawned creature with `/dm note <name> add …`. |

---

## Attacks

Entity attacks are **attack rolls only**: `to_hit` against AC, then `damage`. The DM uses them by
possessing the creature (DM mode → Possess) and attacking like a player would.

| Key | Required | Notes |
|---|---|---|
| `name` | yes | Shown in prompts and the hotbar. |
| `to_hit` | yes | The **full** bonus (ability + proficiency + magic). A missing value means +0. |
| `damage` | yes | Dice **including** the modifier, e.g. `"1d8+3"`. Nothing is added for you. |
| `damage_type` | yes | Resistances read it. |
| `reach` | recommended | `"5 ft."` for melee, `"80/320 ft."` for ranged. The `/` form gives a range limit. A single distance past 10 ft is treated as ranged (for the cosmetic projectile). |
| `item` | optional | A weapon id. The possessing DM holds that weapon, and it becomes loot (Investigation DC 5). |
| `lootable` | optional | `false` means it's used in the fight but never dropped (a kobold's smashed sling). |
| `material` | optional | Hotbar icon for an attack with no `item`. |

**Not supported yet:** save-based abilities (breath weapons, a gaze that forces a save), multiattack,
and spellcasting from a spell list. Run those as the DM: call each player's save with
`/dm check <player> save dexterity dc 13`, then apply damage with
`/combat override <target> <amount>`. (`/combat save` only answers a pending *spell* save.)

---

## Loot

A dead creature is searched through the DM (the player declares, the DM calls the check). What's
found comes from one of two places:

1. **An explicit `loot:` section.** If it's present, it's the *whole* list and nothing else drops:

   ```yaml
   loot:
     - gold_piece                   # plain id: 1, Investigation DC 5
     - item_id: gold_piece
       qty: 12
     - item_id: potion_of_healing
       dc: 15                       # harder to find
       check: perception            # any skill name (default investigation)
     - item_id: cursed_ring
       lootable: false              # listed for the DM, never handed out
   ```

2. **No `loot:` section.** Loot is built for you from every attack `item:` (unless it's
   `lootable: false`) plus every `inventory:` entry. Inventory entries accept the same map form
   (`item_id`, `qty`, `dc`, `check`, `lootable`).

`lootable: false` at the **creature** level means nothing can be looted from it.

Every id must exist in Weapons/Armor/Items. See [`authoring-items.md`](authoring-items.md).
Random drops aren't supported yet (#164).

---

## Merchants

```yaml
shop:
  enabled: true
  items:
    - item_id: longsword          # must be an exact, existing id
      price: { amount: 15, currency: gold }
      stock: 5                    # -1 = unlimited · omitted = 1
    - item_id: chain_mail
      price: { amount: 8, currency: platinum }   # keep each price ≤ 64 coins (#94)
      stock: 2
  accepts:                         # what the merchant will BUY from players (at 50%)
    - longsword
    - chain_mail
```

- Players open it with `/dm entity trade <name>`. The DM manages it with
  `/dm entity shop view|add|restock|adjust|discount|markup|reset|setfunds|setmultiplier` (see `COMMANDS.md`).
- **Each spawned merchant gets its own copy** of this shop, saved to
  `plugins/jkvttplugin/Saved/Shops/<instance-uuid>.yml`. Stock changes persist across restarts for
  *that* creature. Removing it and spawning a new one starts again from the YAML.
- **An unknown `item_id` doesn't appear in the trade window.** The content check names it on
  `/dm reload`. Double-check ids (`chain_mail`, `plate`, not `chainmail`, `plate_armor`).

---

## Checklist before a session

1. `/dm reload`. No `[EntityLoader]` or `[ContentValidator]` warnings in the console? The content
   check names attack `item:`s, inventory/loot ids and shop ids that don't exist, plus shop prices
   over 64 coins.
2. `/dm entity spawn <id>`, then `/dm entity info <name>`. Do the HP, AC and attacks look right?
3. Possess it (DM mode) and check each attack shows in the hotbar and resolves.
4. Merchant: `/dm entity trade <name>`. Is every item there, with no crossed-out prices?
5. Kill it (`/combat override`) and search the body. Is the loot what you expected?
