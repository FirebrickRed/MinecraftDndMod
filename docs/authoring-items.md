# Authoring weapons, armor & items

How to add equipment to `DMContent/Weapons/`, `DMContent/Armor/` and `DMContent/Items/`. This is the
companion to [`authoring-spells.md`](authoring-spells.md). Any `.yml` file in those folders is
loaded; group entries however you like. After editing, run `/dm reload`.

> **Read the console after `/dm reload`.** The loaders parse leniently, so a typo usually doesn't
> fail, it just quietly doesn't work. The **content check** (`ContentValidator`) runs after every load
> and names those: unknown ids in kits, shops and loot, unwearable armor, bad materials, focus types no
> class uses, prices over 64. A clean load prints nothing. See **Gotchas** at the end.

---

## Ids: one id, used everywhere

The top-level key is the **item id** (`longsword:`), lowercase with underscores. It gets stamped onto
every copy of the item as the `item_id` tag, and that tag is how shops, loot, ammunition, equip
tracking and attack prompts all recognise it. Display names can change freely; the id can't
without orphaning items already in players' inventories.

- **Keep ids unique across all three folders.** When an id is looked up, weapons are checked first,
  then armor, then items. A clash means the weapon silently wins.
- **Anything that names an item must use the exact id**: class starting equipment, shop
  `item_id:`, entity `item:`/`inventory:`/`loot:`, a weapon's `ammunition:`. An unknown id doesn't
  stop anything loading. Starting equipment becomes a paper called "Unknown item" and a shop row simply
  doesn't appear, but the content check names every one on reload.
- **Always hand items out with `/dm give`.** A vanilla `/give arrow` has no `item_id`, so a bow
  won't fire it and a shop won't buy it.

---

## Fields every piece of equipment shares

```yaml
some_id:
  name: "Display Name"         # REQUIRED. No fallback: a missing name gives a nameless item
  description: "Flavor + rules text shown in the lore."
  weight: "3 lb"               # free text, display only
  cost:                        # optional; used for display and as a reference price
    amount: 15
    currency: gold             # copper | silver | electrum | gold | platinum (default gold)
  material: IRON_SWORD         # the vanilla Minecraft item it renders as
  # custom_model: longsword    # optional resource-pack model; only if its texture exists
```

`material:` and `custom_model:` follow the rules in `CLAUDE.md` → *Icons & Materials*.
**Paper is the fallback** when `material:` is missing or misspelled, for weapons, armor and items
alike. (There's no smarter per-type default.)

---

## Weapons — `DMContent/Weapons/`

```yaml
handaxe:
  name: "Handaxe"
  category: "simple"           # simple | martial
  type: "melee"                # melee | ranged
  damage: "1d6"                # the weapon die; the ability modifier is added automatically
  damage_type: "slashing"      # becomes the hit's damage type (resistances read it)
  properties: ["light", "thrown"]
  range: "20/60"               # needed for thrown and ranged weapons
  weight: "2 lb"
  cost: { amount: 5, currency: gold }
  description: "A light throwing axe."
  material: IRON_AXE
```

### What each field actually drives

| Field | Effect in play |
|---|---|
| `category` + `type` | **Auto-tags** the weapon: `simple_weapon` and `simple_melee_weapon`, for example. Equipment choices like "any martial melee weapon" pick it up, and proficiency ("martial weapons") covers it. There's no tag list to edit. |
| `type: ranged` | Attacks use **DEX**, fire a cosmetic projectile, and are range-checked. `melee` uses **STR**. |
| `damage` / `damage_type` | Rolled on a hit; crits double the dice. |
| `range` | `"80/320"` (normal/long), `{ normal: 80, long: 320 }`, or a single number. Attacks beyond long range are refused. |
| `reach` | **A number in feet** (`reach: 10`). Default 5. This is what lets a glaive hit at 10 ft. The `"reach"` word in `properties` is display only. |
| `ammunition` | The **item id** this weapon fires (`arrow`, `bolt`, `sling_bullet`). Each shot consumes one; with none left, you can't attack. |
| `recovery_chance` | For thrown weapons: the % chance the weapon survives being picked up again. Default 100. |

### Properties that do something

Write properties in lowercase. Only these three change mechanics today; everything else (`light`,
`heavy`, `two-handed`, `loading`, `versatile`, `reach`, `special`) is shown in the lore and nothing more.

| Property | Mechanic |
|---|---|
| `finesse` | The attacker uses the better of STR and DEX. |
| `thrown` | **Stab or throw.** Adjacent defaults to a melee stab, farther defaults to a throw, and the prompt offers the other option. A thrown weapon **leaves your hand** and lands near the target to be picked up (#192). Needs `range:`. |
| `ammunition` | The weapon consumes its `ammunition:` item and blocks vanilla arrow fire (#128/#191). Always pair it with `ammunition: <id>`. |

> **Not modeled yet:** `versatile` (no two-handed damage die), `loading`, `two-handed` hand
> restrictions, `heavy` disadvantage for Small creatures, and magic `+N` bonuses (that's #188).

### The projectile you see

A ranged or thrown attack shows a cosmetic projectile: a **trident** if `material: TRIDENT`, otherwise
an **arrow**. It's purely visual; damage never comes from the Minecraft projectile.

---

## Armor & shields — `DMContent/Armor/`

```yaml
studded_leather_armor:
  name: "Studded Leather Armor"
  category: "light"            # light | medium | heavy | shield
  ac:                          # see the three AC formats below
    base: 12
    adds_dex: true
    max_dex: 5
  strength_requirement: 0      # shown in lore only (penalty not enforced, #34)
  stealth_disadvantage: false  # shown in lore only
  weight: "13 lb"
  cost: { amount: 45, currency: gold }
  description: "Leather reinforced with close-set rivets or spikes."
  material: LEATHER_CHESTPLATE # MUST be wearable (see below)
```

### Three ways to write `ac`

| Format | Example | Meaning |
|---|---|---|
| Number | `ac: 16` | Uses the category default: light adds full DEX, medium adds DEX (max 2), heavy and shield add none. |
| String | `ac: "12 + dex (max 2)"` | Base, plus DEX, with an optional cap. |
| Map | `ac: { base: 12, adds_dex: true, max_dex: 5 }` | Explicit. Use this when the category default is wrong. |

A shield's `ac` is the **bonus** (`ac: 2`).

### How armor gets worn

AC follows **what's actually in the slots** (#31): body armor in the **chestplate slot**, a shield in
the **off-hand**. So `material:` has to be something Minecraft lets you wear there:

- body armor → a chestplate: `LEATHER_CHESTPLATE`, `CHAINMAIL_CHESTPLATE`, `IRON_CHESTPLATE`, …
- shield → `SHIELD`

The trap: a helmet, leggings, boots or anything else makes the armor *render*, but it can't go in the
tracked slot, so it never counts toward AC. A misspelled material becomes paper, which can't be worn
either. The content check warns about both. (Case doesn't matter: `iron_chestplate` works.)

---

## Items — `DMContent/Items/`

Everything else: ammunition, currency, focuses, tools, gear, trinkets.

```yaml
arrow:
  name: "Arrow"
  type: "ammunition"           # free text, except "spellcasting_focus" (see below)
  description: "A flight arrow, fletched and sharp. Fired from a bow."
  material: "arrow"            # lowercase is fine for items
  tags: [ammunition]           # grouping tags (below)
  recovery_chance: 50          # % that survives pickup after being shot
  cost: { amount: 5, currency: copper }
```

| Field | Effect |
|---|---|
| `type` | Mostly descriptive. **`spellcasting_focus`** is the one that matters: together with `focus_type` it makes right-click open the casting menu. |
| `focus_type` | Has to **exactly match** the class's `spellcasting.spellcasting_focus_type` (`arcane_focus`, `holy_symbol`, `druidic_focus`, `musical_instrument`, `thieves_tools`). `component` works for every class. |
| `tags` | Data-driven groups. `gaming_set` feeds "choose a gaming set" equipment choices. `ammunition` gives a default 50% recovery. Add a new tag just by using it, then reference it from a class or background `player_choices`. |
| `recovery_chance` | Ammunition survival %. Defaults to 50 for `ammunition`-tagged items and 100 otherwise. |
| `healing` | Dice restored when the item is drunk (`"2d4+2"`). **Any item with this is drinkable** — no potion list in code. |
| `check_ability` | For a **tool**: the ability a check with it uses by default (`dexterity` on thieves' tools), so `/dm check <p> tool thieves_tools dc 15` needs no ability. Full ability names. Absent = the DM names the ability each time. |

### Potions

Give an item a `healing:` value and it becomes a potion:

```yaml
potion_of_healing:
  name: "Potion of Healing"
  type: "potion"
  material: "POTION"
  healing: "2d4+2"
  tags: [potion]
  cost: { amount: 50, currency: gold }
```

Clicking it doesn't drink it: it fills `/character drink potion_of_healing` into chat, and the player
picks `manualRoll <n>` (they rolled the dice) or `autoRoll` (the game rolls), then presses Enter. That
keeps one path for HP — the command — and means drinking costs the Action in combat and works
normally outside it. The vanilla Minecraft drink is always cancelled, so a D&D potion never applies a
Minecraft potion effect.

### Currency

Coins are ordinary items: `copper_piece`, `silver_piece`, `electrum_piece`, `gold_piece`,
`platinum_piece`. Shops recognise currency by an id ending in `_piece`. Hand out money with
`/dm give <player> gold_piece 25`. One stack caps at 64, so give large sums in several stacks or in
platinum.

---

## Using what you made

| Where | How it references the id |
|---|---|
| Class / background starting gear | `starting_equipment:` takes `- longsword` or `- gold_piece x15` (quantity). Choices take `- give: [light_crossbow, bolt x20]` (see CLAUDE.md, *Equipment choices*). Backgrounds must use `starting_equipment:`; `equipment:` is ignored (and warned about). |
| A shop | `items: [{ item_id: longsword, price: { amount: 15, currency: gold }, stock: 5 }]`, plus `accepts: [longsword]` for what the merchant buys |
| An entity | `attacks: [{ item: dagger, … }]`, `inventory: [arcane_focus]`, `loot: [{ item_id: gold_piece, qty: 12 }]` (see [`authoring-entities.md`](authoring-entities.md)) |
| Exploration loot | `/dm object loot gold_piece x25` |
| Directly | `/dm give <player> <id> [amount]` |

**Shop prices cap at 64 of one coin** (#94). A price of `75 gold` shows as a crossed-out 64 and
`1500 gold` can't be bought. Price big-ticket items in platinum (chain mail → `8 platinum`).

---

## Gotchas

The content check on `/dm reload` warns about every one of these unless noted.

- **Missing `name:`**: the item has no name.
- **Armor `material:` that isn't a chestplate or `SHIELD`**: it renders but never counts toward AC.
- **An id referenced somewhere that doesn't exist**: an "Unknown item" paper, or a missing shop row.
  Check spelling against the file: it's `chain_mail`, not `chainmail`, and `plate`, not `plate_armor`.
- **`focus_type` doesn't match any class**: "You cannot use this type of focus!" (the old
  `holy_symbol` item had `divine_focus`). A class whose focus type no item has is flagged too.
- **A `material:` that's a block rather than an item, or a made-up name** (`arcane_focus`): it renders as paper.
- **A weapon with the `ammunition` property but no `ammunition:` id**: it fires without consuming anything.
- **A thrown or ranged weapon without `range:`**.
- **A shop price over 64 coins** (#94).
- **Vanilla `/give`** *(not checkable)*: the item has no `item_id` and nothing recognises it.
- **The same id in two folders** *(not checked yet)*: the weapon wins, then armor, then item.

---

## What's already defined

- `Items/adventuring_gear.yml`: tools and kits (thieves' tools, which is also the artificer's focus,
  plus disguise kit, forgery kit and healer's kit), the spellbook, all eight class packs, common gear
  (torch, rope, lanterns, crowbar, rations, potion of healing, whetstone…), clothing, and the
  background keepsakes.
- `Items/musical_instruments.yml`: the ten PHB instruments, each a bard focus tagged `musical_instrument`.
- `Items/spellcasting_items.yml`: arcane focus, component pouch, holy symbol, druidic focus.
- `Items/ammunition.yml`, `currency.yml`, `gaming_sets.yml`, `keepsakes.yml`.

Packs are single items: opening one into its contents isn't a feature.
