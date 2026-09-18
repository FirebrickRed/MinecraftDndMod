# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Paper/Spigot Minecraft plugin that transforms Minecraft into a virtual tabletop for D&D 5th Edition. Players can create D&D characters following 5e rules, manage equipment, cast spells, and engage in combat mechanics—all within Minecraft.

**Target Platform:** PaperMC 1.21.8 (Java 21)

## ⚠️ Pre-Alpha Development Status

**This project is in PRE-ALPHA.** We are actively refining data structures, APIs, and architecture. Breaking changes are expected and encouraged.

**If you notice structural improvements that should be made, NOW is the time to suggest them.** Don't hesitate to propose changes to:
- YAML schema/format (item costs, entity definitions, etc.)
- Class hierarchies and data models
- Command syntax and naming
- Database/persistence structure
- API designs

We will not have this flexibility once we reach alpha/beta, so please flag any architectural concerns or improvements early. It's much easier to fix now than later.

## Build Commands

```bash
# On Windows (Command Prompt or PowerShell):
gradlew build

# Or explicitly use the batch file:
gradlew.bat build

# Clean and build:
gradlew clean build

# The compiled JAR will be in: build/libs/
```

**Note:** On Windows, use `gradlew` (not `./gradlew`). The `.bat` extension is implied.

## Development Environment

- **OS**: Windows 11
- **Shell**: Command Prompt / PowerShell
- **Please use Windows-compatible commands**
    - Use `dir` instead of `ls`
    - Use `type` instead of `cat`
    - Use `del` instead of `rm`
    - Use backslashes `\` for paths
    - Use `copy` instead of `cp`
    - Use `move` instead of `mv`


## Development Workflow

- I develop on Windows
- Always use Windows-compatible commands and paths
- Use PowerShell or Command Prompt syntax

### ⚠️ Temporary code goes in the cleanup register (#193)

**If you add code that is meant to be removed later, add it to issue #193 in the same change
that introduces it.** That's a living document listing every bit of scaffolding, migration
shim, and unverified tuning constant in the repo. Equally: **when you finish work that
retires an entry, tick it off #193** — the register is only useful if it shrinks as well as grows.

This covers:
- **Playtest scaffolding** — "this is new, tell us if it looks wrong" notices, debug output
- **Migration shims** — a loader accepting an old YAML key so existing homebrew doesn't break
- **Tuning values that are guesses** — magic numbers that need eyes on a running server
- **Stubs** that print "not yet implemented" (and make sure `COMMANDS.md` says so too)

Every entry must name **what removes it** — a ticket, a date, or an event ("after the first
playtest"). An entry with no exit condition isn't temporary, it's just code.

The register exists because one session added a playtest message, two deprecation shims and
four tuned constants, none with an owner. That's how a codebase accretes permanent
"temporary" code. Also worth knowing: a stale comment that asserts something *false* is worse
than no comment — one claiming equipped armor wasn't persisted (it was, and had been for a
while) sent a whole debugging session down the wrong path.
- Use backslashes for file paths

### Hot Reloading Data
The plugin loads D&D content (races, classes, spells, weapons, armor, items) from YAML files in `DMContent/`. Use `/dm reload` in-game to reload data without restarting the server.

### Testing Character Creation
1. Use `/character create` to start the character creation flow. This also hands the player a
   **"Create Character"** paper — right-clicking it re-opens the in-progress creation menu, so
   closing out isn't destructive. On completion the paper is swapped for the real Character Sheet.
2. `CharacterSheetManager.giveCreationPaperIfAbsent` / `removeCreationPapers` manage that paper;
   `CreationNameListener` handles the in-chat name step. (The old `CharacterNameListener` and
   `AnvilNameListener` were dead iterations and have been removed.)
3. Character data persists to `plugins/jkvttplugin/Saved/Characters/` as YAML files

### Testing Character Features
- `/character rest short` - Recover short rest resources and innate spells
- `/character rest long` - Fully restore HP, spell slots, and all resources
- Right-click character sheet item to view character stats
- Click ability scores to view skills and roll checks
- Click skills to roll with advantage/disadvantage

### Testing Shop System (Issue #75)

**1. Setup Test Merchant:**

A merchant is an entity whose YAML has a `shop:` section — there is **no `shop create`**; `shop add`
on an entity without one fails with "is not a merchant". `balin_blacksmith` ships with a shop. The
commands take the spawned creature's **name** ("Balin"), not the template id:
```
/dmentity spawn balin_blacksmith
/dmentity shop add Balin shortsword 10 gold 3      # <item_id> <price> <currency> [stock]
```
See `docs/authoring-entities.md` → *Merchants* for the YAML.

**2. Test Tab Completion:**
- Type `/dmentity shop add Balin ` and press TAB → should suggest all item IDs (weapons, armor, items)
- Type `/dmentity shop add Balin longsword 15 ` and press TAB → should suggest currencies (gold, silver, copper, platinum, electrum)
- Type `/dmentity shop restock Balin ` and press TAB → should suggest only items in Balin's current inventory

**3. Test Player Buying (Merchant to Player):**
```
/dmentity trade Balin
```
- Merchant GUI should open with items for sale
- Each item should show price in gold pieces
- Execute a trade to buy longsword
- Verify stock decreases: `/dmentity shop view Balin`
- Buy remaining stock until item is out of stock
- Verify "out of stock" message appears

**4. Test Player Selling (Player to Merchant):**
```
/dm give longsword 1
/dmentity trade Balin
```
(`/dm give <item_id> [amount]` gives to yourself; `/dm give <player> <item_id> [amount]`
gives to another player. The item type is auto-detected from the id — there is no
`<item_type>` argument. There is no standalone `/dmgive`; it lives under `/dm give`.)
- Merchant GUI should show reverse trades (player gives item, gets currency)
- Sell longsword to merchant for gold (50% of buy price)
- Verify merchant's inventory increases: `/dmentity shop view Balin`
- Verify sold item appears in merchant's stock
- Try selling an item not in merchant's acceptance list → should fail

**5. Test NBT-Based Item Identification:**
- Create two longswords with different display names but same item_id
- Verify both are recognized as "longsword" by the shop system
- Verify currency items (gold_piece, silver_piece) are properly identified
- Check that items have `item_id` NBT tag: drop item, inspect with F3+H

**6. Test Shop Persistence:**
```
/dmentity shop view Balin
```
- Note current stock levels
- Restart the server (not `/reload confirm` — Paper plugin reloads are unsupported)
- Verify merchant still exists and stock persists
- Check `plugins/jkvttplugin/Saved/Shops/<instance-uuid>.yml` exists — shops are saved **per spawned
  creature**, keyed by its instance id, not by the template id
- Verify both stock decreases (from buying) and inventory increases (from selling) persist

**7. Test Complete Buy/Sell Cycle:**
1. Buy longsword from merchant (stock: 5 → 4)
2. Verify stock decreased: `/dmentity shop view Balin`
3. Sell longsword back to merchant (stock: 4 → 5)
4. Verify merchant inventory increased
5. Remove merchant: `/dmentity remove Balin`, then `/dmentity spawn balin_blacksmith`
6. Verify the **new** Balin starts from the YAML stock — a fresh spawn gets a new instance id, so it
   doesn't inherit the old creature's shop file (per-instance state, #194). Persistence covers
   restarts, not respawns.

**8. Test Edge Cases:**
- Try buying with insufficient inventory space
- Try buying out of stock items
- Try selling items with no acceptance list configured
- Try restocking with negative amounts
- Verify proper error messages for all failure cases

**Expected Results:**
- ✅ All items have `item_id` NBT tags
- ✅ Merchant GUI shows both buy and sell trades
- ✅ Stock tracking works correctly (increases/decreases)
- ✅ Shop data persists to YAML and survives restarts
- ✅ Tab completion works for items and currencies
- ✅ Sell prices are 50% of buy prices
- ✅ Acceptance list controls what players can sell

### Adding New D&D Content
All D&D content is defined in `DMContent/` YAML files:
- **Races:** `DMContent/Races/*.yml`
- **Classes:** `DMContent/Classes/*.yml` (includes spellcasting info, proficiencies, starting equipment)
- **Spells:** `DMContent/Spells/*.yml`
- **Weapons:** `DMContent/Weapons/*.yml`
- **Armor:** `DMContent/Armor/*.yml`
- **Items:** `DMContent/Items/*.yml`
- **Backgrounds:** `DMContent/Backgrounds/*.yml`

Each category has a corresponding loader in `src/main/java/io/papermc/jkvttplugin/data/loader/` and model in `data/model/`.

**Authoring guides** (every field, what it actually drives, and the silent failure modes):
`docs/authoring-spells.md` · `docs/authoring-items.md` (weapons, armor, items) ·
`docs/authoring-entities.md` (NPCs, monsters, merchants, loot).

### Icons & Materials (Resource Pack)

Two clear YAML keys, used consistently — change them in YAML, not code:

- **`material:`** — the **vanilla Minecraft item** to render (e.g. `IRON_SWORD`, `GOLD_INGOT`).
  This is what everyone sees, and the fallback for players without the resource pack.
  Used by the things a player physically receives: **weapons, armor, items**. Absent/invalid
  → `PAPER` for all three (there's no per-type default). Armor's `material:` must be a wearable
  chestplate (or `SHIELD`), or equip tracking never sees it.
  **Spells** follow the same convention: `material:` sets the base item, absent → a level-based
  default (cantrip → `PAPER`, low → `BOOK`, high → `ENCHANTED_BOOK`); `custom_model:` is optional.
  (No auto `spell_<name>` model — that produced purple placeholders. The old spell `icon:` key was
  renamed to `material:`.)
- **`custom_model:`** — the **resource-pack model name** to overlay if the pack is loaded
  (e.g. `custom_model: bard_icon` → the pack's `bard_icon` model in the `jkvttresourcepack`
  namespace). Optional and opt-in: absent/blank → keeps the vanilla `material`/item. Used by
  menu content (**races, subraces, classes, subclasses, backgrounds**) and, when art exists,
  by equipment. **Only set it when the model's texture actually exists** — a model with no
  texture renders a broken (purple) placeholder, which is worse than the vanilla fallback.
- All model application goes through one helper: `ItemUtil.applyModel(item, modelName)`
  (namespace constant: `ItemUtil.RESOURCE_PACK_NAMESPACE`). Never call `setItemModel` directly.
  Vanilla base materials go through `Util.parseMaterial(name, fallback)`.
- **There is no `icon:` key.** Everything that renders as a Minecraft item uses `material:`
  (+ optional `custom_model:`) — content items, entity attacks (`PossessionManager` hotbar), and
  class resources alike. `icon:` was the old spelling for the last two; loaders still read it so
  existing homebrew keeps its art, but they log a deprecation warning telling you to rename it.
- Fixed game concepts use hardcoded models: ability tiles resolve to `<abbr>_icon`
  (`str_icon`, `dex_icon`, …). Other fixed UI icons (Back arrow, tabs) are vanilla until
  their pack textures exist; a house-rule/UI-icon override config is future work (#104).
- A model needs a **complete chain** to render: `items/<name>.json` → `models/item/<name>.json`
  → `textures/item/<name>.png`. A model with no texture shows a broken (purple) placeholder,
  which is worse than the vanilla fallback — so only reference models whose textures exist.
- The `ResourcePack/` folder is **not** committed (gitignored); it lives locally for testing.

## Architecture

### Core Systems

**Character Creation Flow:**
1. `CharacterCreationSession` (session state) → stores player choices during creation
2. `CharacterCreationService` → manages active sessions
3. UI Menus (`ui/menu/*`) → inventory-based UI for race, class, abilities, spells, etc.
4. `CharacterSheet` (final character) → created from session when complete
5. `CharacterSheetManager` → creates, stores, and manages character sheets
6. `CharacterPersistenceLoader` → saves/loads character sheets to/from YAML

**Data Loading System:**
- `DataManager` coordinates loading all content from `DMContent/` folder
- Each content type has a dedicated loader (e.g., `RaceLoader`, `ClassLoader`, `SpellLoader`)
- Loaders populate static registries accessible throughout the plugin
- All loaders run on plugin startup via `JkVttPlugin.onEnable()`
- **`data/ContentValidator`** runs after every load / `/dm reload` and warns about cross-content
  mistakes the lenient loaders swallow: unknown item ids in kits, choices, shops and loot; unwearable
  armor; materials that aren't items; focus types no class uses; bad spell fields (`Self` range on a
  targeted spell, unknown condition, multi-group dice); prices over 64. A clean load prints nothing.
  **When you add a new cross-reference between content types, add its check there.**
- Shared parsing lives in `data/loader/util/ParseUtil` (generic YAML→value primitives) and the
  `data/loader/parser/` package (`AbilityParser`, `LanguageParser`, `EquipmentParser`,
  `ChoiceParser`, `InnateSpellParser`, `ShopParser`, `RaceClassParser`) — the old monolithic
  `LoaderUtils` was split into these (Issue #12)

**Menu System:**
- `MenuType` enum defines all menu types
- `MenuHolder` implements `InventoryHolder` to associate session data with inventories
- `MenuClickListener` routes all menu clicks based on `MenuType`
- Each menu (e.g., `RaceSelectionMenu`, `SpellSelectionMenu`) builds its own inventory

**Spell System:**
- Spells loaded from YAML into `DndSpell` objects
- Classes define spellcasting via `SpellcastingInfo` (ability, preparation type, spell list, slots)
- `SpellSelectionMenu` shows available spells filtered by class spell list
- `CharacterCreationSession` tracks selected cantrips and spells separately
- `SpellFocusListener` handles spellcasting focus item interactions
- Spellbook UI shows all known spells with spell slots by level

**Equipment System:**
- Weapons/armor/items have custom NBT data for identification — all via the shared `item_id` tag
  (`ItemUtil.getItemId`). There is no `armor_id`/`weapon_id` key; anything reading one is a bug.
- `WeaponListener` handles weapon interactions and the **left-click attack prompt** (#189)
- `CharacterSheet` auto-equips armor/shields during character creation if proficient
- **Live equip tracking (#31):** `ArmorEquipListener` re-reads the chestplate and off-hand slots on
  any event that could change them (click, drag, off-hand swap key, right-click-to-equip, drop,
  join, respawn) and updates `equippedArmor`/`equippedShield`, which recalculates AC and persists.
  Body armor is the chestplate slot (`LEATHER_`/`CHAINMAIL_`/`IRON_CHESTPLATE` by category), a
  shield is the off-hand (vanilla `SHIELD`) — real Minecraft items in real slots.
- Equipment choices during character creation handled via `PendingChoice` system

**Racial Traits System (Issue #51):**
- Races can grant innate spells, proficiencies, resistances, and movement speeds
- `InnateSpell` class tracks level-gated spells with usage limits
- Darkvision, swimming speed, flying speed stored on `CharacterSheet`
- Proficiency-based abilities scale with proficiency bonus
- Applied during character creation via `applyRacialTraits()`

**Subclass System (Issue #64):**
- **Subclass Selection Timing:**
  - Level 1: Cleric (Divine Domains), Warlock (Otherworldly Patrons), Sorcerer (Sorcerous Origins)
  - Level 2: Wizard (Arcane Traditions)
  - Level 3: Most other classes (implemented during level-up)
- **Subclass Data Model:**
  - `DndClass.subclasses` - Map of subclass definitions
  - `DndClass.subclassLevel` - When subclass is chosen
  - `DndClass.subclassTypeName` - Display name (e.g., "Divine Domain", "Otherworldly Patron")
- **Subclass Features:**
  - `bonus_spells` - Domain/expanded spells (always prepared/known, don't count against limit)
  - `additional_spells` - Bonus cantrips (e.g., Light cantrip for Light Domain)
  - `conditional_bonus_spells` - Spells based on player choice (e.g., Genie patron type)
  - `proficiencies` - Armor, weapon, skill, tool proficiencies
  - `languages` - Additional languages granted
  - `darkvision`, `swimming_speed` - Enhanced senses/movement
  - `conditional_advantages` - Situational advantages (e.g., saves vs disease)
  - `features_by_level` - Text descriptions of subclass features
  - `player_choices` - Subclass-specific choices (e.g., Genie kind, Knowledge Domain skills)
- **Application:** Applied automatically via `applySubclassTraits()` during character creation
- **Future:** See Issue #70 for structured features system (usage tracking, action economy, etc.)

**Skills and Rolling System (Issues #55, #60):**
- Skills menu shows all 18 D&D skills grouped by ability
- Interactive rolling with advantage/disadvantage support
- Ability checks and saving throws from character sheet
- Roll results broadcast to chat with breakdown
- Skill proficiencies tracked from class, background, and race

**Class Resource System (Issue #25):**
- Tracks limited-use class features (Rage, Ki Points, Sorcery Points, etc.)
- Resources defined in class YAML with recovery type (short rest, long rest, dawn)
- Supports fixed amounts, ability modifiers, proficiency bonus, and formulas
- Displayed in character sheet with current/max tracking
- Recovered automatically via `/character rest short` and `/character rest long` commands

**Shop System (Issue #75):**
- Native Minecraft Merchant GUI integration for D&D economy
- **Currency System:** Gold, silver, copper, platinum, electrum pieces as items with NBT tags
- **Shop Data Model:** `ShopConfig` defines items, prices, stock, and accepted items
- **Bidirectional Trading:**
  - Player buying from merchant (merchant stock decreases)
  - Player selling to merchant (merchant inventory increases with sold items)
- **Stock Tracking:** Limited and unlimited stock per item, persists across server restarts
- **Shop Persistence:** Each spawned merchant clones its template's shop and saves it to
  `plugins/jkvttplugin/Saved/Shops/<instance-uuid>.yml` (keyed by the creature, not the template id)
- **NBT-Based Item Identification:** All items tagged with `item_id` NBT for reliable identification
  - Allows items with different display names to share the same mechanics
  - Currency items identified by `item_id` ending in "_piece"
  - Works for weapons, armor, items, and custom content
- **Shop Commands:** DM entity commands for creating/managing shops (see Commands section)

### Key Design Patterns

**Player Choice System:**
- `PlayersChoice` + `PendingChoice` → generic system for handling player choices during character creation
- Used for equipment selection, spell selection, and racial/class feature choices
- `ChoiceUtil` provides helper methods for processing choices
- `ChoiceMerger` handles merging and deduplicating choices from multiple sources

**ItemStack Creation:**
- `ItemUtil` provides centralized item creation with NBT tags
- D&D objects (`DndWeapon`, `DndArmor`, `DndItem`, `DndSpell`) can create their own `ItemStack` representations
- NBT tags used to identify items (e.g., `weapon_id`, `armor_id`, `spell_name`)

**Session Management:**
- Active character creation sessions stored in `CharacterCreationService`
- Each menu navigation preserves the session UUID
- Sessions cleared upon character sheet finalization

**Builder Pattern:**
- `DndClass.Builder` for constructing class objects
- `LoreBuilder` utility for consistent item lore formatting
- Standardized across data models for clean instantiation

## Common Patterns

### Adding a New D&D Class

1. Create YAML in `DMContent/Classes/<classname>.yml` (see `warlock.yml` for spellcaster template)
2. Define: `hit_die`, `proficiency`, `saving_throws`, `armor_proficiencies`, `weapon_proficiencies`, `spellcasting` (if applicable), `starting_equipment`, `features_by_level`
3. For level 1 subclasses, add `subclass_level: 1`, `subclass_type_name`, and `subclasses` map
4. Run `/dm reload` or restart server
5. Class will appear in `ClassSelectionMenu` automatically

### Equipment choices & item tags (Issue #54)

Equipment `player_choices` (`type: equipment`) use a **flat** `options:` format:

```yaml
options:
  - greataxe                          # a lone item
  - martial_melee_weapon              # a lone tag (auto-detected)
  - handaxe x2                        # quantity via space-delimited 'xN' (the space matters)
  - give: [light_crossbow, bolt x20]  # a bundle given together; add `label:` for a menu label
```

- **Tags are data-driven** — never hardcoded. Weapon tags (`simple_weapon`, `martial_weapon`,
  `simple_melee_weapon`, `martial_melee_weapon`, …) are DERIVED from each weapon's
  `category` (simple/martial) + `type` (melee/ranged) at load (`WeaponLoader.installWeaponTags`).
  Item groupings come from an item's own `tags:` list (`ItemLoader.installItemTags`), e.g.
  `gaming_set`. Add a homebrew weapon/item and it joins the right tags automatically — no code.
- `TagRegistry` holds no hardcoded tags; `merge()` is the hook for a future DM-authored `Tags.yml`.

### Adding a New Subclass

1. Edit class YAML (e.g., `cleric.yml`)
2. Add subclass to `subclasses` map with:
   - `name` - Display name
   - `description` - Flavor text
   - `bonus_spells` - Domain/expanded spells (list of spell IDs)
   - `additional_spells` - Bonus cantrips
   - `proficiencies` - Armor, weapon, skill, tool
   - `languages` - Additional languages
   - `features_by_level` - Map of level → feature descriptions
3. Run `/dm reload`
4. Subclass appears in selection menu for that class
5. Bonus spells and proficiencies applied automatically during character creation

### Adding a New Spell

**Full guide: `docs/authoring-spells.md`** — the spell shapes (attack / save / AoE / healing /
social / utility), every YAML field, which shapes aren't supported yet (multi-beam, attack-then-AoE,
smites), and the load-time validation. Quick version:

1. Create or edit YAML in `DMContent/Spells/*.yml` (any file; ids must be unique across all of them)
2. Define the common fields (`name`, `level`, `school`, `casting_time`, `range`, `components`,
   `duration`, `description`, `classes`) plus the fields for its shape
3. A damaging spell **must** have `damage:` (dice) — declaring only `damage_type:` triggers a
   load warning and the "Apply damage (1)" bug
4. Run `/dm reload` and read the console — a clean load is silent; warnings list anything malformed
5. Spell appears in `SpellSelectionMenu` for classes that have it in their spell list

### Adding Racial Innate Spells

1. Edit race YAML (e.g., `drow.yml`)
2. Add `innate_spells` list with:
   - `spell` - Spell ID
   - `level` - Character level when available
   - `uses` - Number of uses (0 = unlimited for cantrips)
   - `recovery` - "long_rest", "short_rest", or "proficiency_bonus"
   - `casting_ability` - "charisma", "intelligence", or "wisdom"
3. Run `/dm reload`
4. Innate spells applied automatically during character creation

### Adding an Entity (NPC / monster)

**Only `id:` is required.** Every other field has a working default, so a one-line file spawns,
fights and can be looted — fill the stat block in when the creature earns one.

```yaml
id: alira          # the ONLY required field. Permanent: it's written into every spawned
                   # armor stand's PDC and looked up on restore, so changing it orphans
                   # anything already standing in the world. Pick a name-free id.
```

Defaults when a key is absent: `name:` → the id, prettified (`alira_the_kindler` → "Alira The
Kindler") · `creature_type:` humanoid · `size:` medium · HP 10 · `armor_class:` 10 · `speed:` 30 ·
every ability score 10 · no attacks, inventory or loot. A creature with no `attacks:` simply has
nothing to swing — it still takes damage, dies and drops loot.

Worth setting early: `name:` (what the party sees) and `hit_points:`/`armor_class:` (defaults make
a 10/10 punching bag). `model:` is a resource-pack model name — **only set it if the texture
exists**, or the NPC renders as a purple placeholder; absent means an invisible stand with a
floating nameplate.

**Watch the types.** `hit_points:` takes a whole number and `hit_dice:` a quoted dice string —
`hit_points: 7d8+2` is a string, not a number, and silently falls back to 10 HP. Same for a blank
`armor_class:`. The loader now warns on both at startup / `/dm reload`; check the console if a
creature feels wrong.

A file can hold one entity (with `id:` at the root) or many (root keys are the ids). See
`DMContent/Entities/town/town_guard.yml` for a full stat block and `balin_blacksmith.yml` for a
merchant with a shop.

### Debugging Character Creation

- Character creation sessions are logged when created/destroyed
- Check `CharacterCreationService.getSession()` for active sessions
- Character sheets saved to `plugins/jkvttplugin/Saved/Characters/<UUID>.yml`
- Use `CharacterPersistenceLoader.loadAllCharacters()` to reload saved characters

### Working with Inventory Menus

All custom menus follow this pattern:
```java
public static Inventory build(Player player, UUID sessionId, ...) {
    Inventory inv = Bukkit.createInventory(
        new MenuHolder(MenuType.YOUR_MENU, sessionId),
        54, // size
        Component.text("Menu Title")
    );
    // Populate inventory with items
    return inv;
}
```

Menu clicks are handled in `MenuClickListener.onMenuClick()` via switch on `MenuType`.

## Plugin Entry Point

`JkVttPlugin.onEnable()` initializes:
1. `ItemUtil` for item creation
2. `DataManager` to load all D&D content from YAML
3. `CharacterSheetManager` for character persistence
4. Event listeners (weapon, spell focus, NPC, character creation)
5. Commands (see `src/main/resources/plugin.yml`)

## Implemented Features

### Character Creation
- ✅ Race selection with subraces (Issue #27)
- ✅ Class selection with level 1 subclasses (Issue #64)
- ✅ Background selection
- ✅ Ability score allocation (point buy, standard array, manual)
- ✅ Skill proficiency selection
- ✅ Spell selection for spellcasters
- ✅ Equipment selection with player choices
- ✅ Character persistence to YAML (Issue #17)

### Racial Traits (Issue #51)
- ✅ Innate spellcasting (cantrips and leveled spells)
- ✅ Usage tracking with recovery (long rest, short rest, proficiency bonus)
- ✅ Darkvision, movement speeds (swimming, flying, climbing)
- ✅ Damage resistances
- ✅ Weapon and armor proficiencies
- ✅ Skill proficiencies
- ✅ Tool proficiencies
- ✅ Languages

### Subclasses (Issue #64)
- ✅ Level 1 subclass selection (Cleric, Warlock, Sorcerer)
- ✅ Bonus spells automatically granted (don't count against spells known)
- ✅ Additional cantrips from subclass
- ✅ Subclass proficiencies (armor, weapon, skill, tool, language)
- ✅ Languages, darkvision, swimming speed from subclass
- ✅ Player choices for subclass features (e.g., Knowledge Domain skills/languages)
- ✅ Enhanced tooltips showing features, spells, proficiencies, and choices
- ✅ Conditional bonus spells data structure (application pending)
- ✅ Display in character sheet viewer
- ✅ Validation for Wizard level 2 subclass choice (deferred to level-up)

### Proficiency System
- ✅ **Weapon Proficiencies** - From race, subrace, class, subclass
- ✅ **Armor Proficiencies** - From race, subrace, class, subclass
- ✅ **Tool Proficiencies** - From race, subrace, class, background, subclass, player choices
- ✅ **Languages** - From race, subrace, background, subclass, player choices
- ✅ **Skill Proficiencies** - From race, subrace, class, background, subclass (automatic and player-chosen)
- ✅ All proficiencies tracked on CharacterSheet and applied during creation/loading

### Backgrounds
- ✅ Background selection during character creation
- ✅ Automatic skill proficiencies (e.g., Acolyte → Insight, Religion)
- ✅ Tool proficiencies (e.g., Charlatan → Disguise Kit, Forgery Kit)
- ✅ Languages from background
- ✅ Starting equipment from background
- ✅ Background display in character sheet (slot 8)
- ✅ Background feature tracking

### Character Sheet
- ✅ Detailed stat display (HP, AC, speed, initiative)
- ✅ Ability scores with saving throw indicators (Issue #48)
- ✅ Skills drilldown menu (Issue #55)
- ✅ Interactive skill rolling with advantage/disadvantage
- ✅ Ability checks and saving throws (Issue #60)
- ✅ Spellbook with spell slots tracking
- ✅ Class resources display (Issue #25)
- ✅ Race and subclass display

### Shop System (Issue #75)
- ✅ Native Minecraft Merchant GUI integration
- ✅ Currency system (gold, silver, copper, platinum, electrum pieces)
- ✅ Bidirectional trading (buy from merchant, sell to merchant)
- ✅ Stock tracking with persistence across restarts
- ✅ NBT-based item identification (`item_id` tag on all items)
- ✅ Tab completion for shop commands (item IDs, currencies)
- ✅ Shop persistence to YAML files
- ✅ Merchant acceptance lists (controls what players can sell)
- ✅ Dynamic pricing (50% sell multiplier for player-to-merchant trades)

### Commands

**Canonical reference:** `COMMANDS.md` at the repo root is the full, grouped, up-to-date
command list. `src/main/resources/plugin.yml` is the registration source of truth.
Summary of groups:

Consolidated into 5 base commands (Issue #122): `/character`, `/roll`, `/combat`,
`/dmentity`, `/dm`. The legacy per-action commands have been removed (their executor
classes remain and are delegated to from CharacterCommand / DmCommand).
- **Character (any player):** `/character <create|view|list|close|rest|give>` (alias `/char`); `create <player>` and `give <player> <name>` are DM-only.
- **Roll:** `/roll <XdY[+Z]>` (alias of the old `/rolldice`).
- **Combat (`/combat <sub>`):** `start`, `add`, `remove`, `surprise`, `initiative`, `nextturn`, `endturn`, `turn`, `status`, `finished`, `reveal`, `hide`, `action`, `bonusAction`, `movement`, `attack`, `damage`, `heal`, `temphp`, `deathsave`, `cast`, `save`, `use`, `condition`, `reactions`. Players may use `action`/`bonusAction`/`attack`/`endturn`/`deathsave` on their own turn only.
  - **Roll input (#183):** a d20 action takes one bare keyword — `autoRoll` (game rolls, applies advantage → 2d20), `manualRoll <n>` (you rolled it, game adds mods), or `total <n>` (final, nothing added). Damage uses `manualRoll <n>` / `autoRoll <dice>` / a flat `<amount>`; the **damage type is automatic** (`type <t>` overrides). There is **no** `--roll`/`--total`/`--type` — those aliases were removed. `RollService.parseInput`/`RollInput` is the one parser; `RollService.resolve(...)` applies reroll (Lucky) + advantage. The out-of-combat `/character check|save|loot` roller is separate (`RollOptionsMenuHandler`).
  - **Attacking (#189):** on your turn, holding a weapon, **left-click** the enemy (or left-click while looking at them) and `WeaponListener` hands you the filled-in `/combat attack`. The click only *prompts* — the roll still goes through the command. **Right-click never attacks**; it means "use" (spell focus, area-effect confirm #173), and is suppressed only for ranged weapons so a bow does not loose a real arrow. Left-clicking a combatant is always cancelled so a punch never damages the armor stand they are rendered on.
  - **Reactions hold the attack (#195):** a hit on someone who could react (a character with their
    reaction in hand who knows a spell cast as a reaction) opens a `ReactionWindow`, broadcast to the
    whole table, and **blocks `/combat damage`** until every reactor answers — cast, `/combat reactions
    pass`, or the DM's `/combat reactions skip <who|all>`. When it closes, a reaction that raised the
    target's AC gets the hit re-checked (Shield turns a hit into a miss; a crit still lands). Ending
    the turn is blocked too, since the held damage lives on the attacker's `TurnState`. The window
    needs no timers because the attack→damage seam is already two commands.
  - **Opportunity attacks are the same window (#147/#195):** `ReactionManager` detects leaving reach
    per block moved but offers nothing mid-step; it re-arms a **settle** check (1s of standing still,
    via a per-mover ticket rather than polling) and then opens a `LEFT_REACH` window that holds the
    **mover's turn** — attack, cast, use, action, bonusAction and endturn all wait (`turnHeld`). A
    reaction cast is exempt, since casting off-turn is often how a window gets answered. Stepping
    back into reach before anyone is asked un-provokes it silently. A provocation now lives exactly
    as long as its window, which fixes the old bug where `pending` was only cleared on the mover's
    next turn and a round-1 OA could still be fired in round 3.
  - **A spell's AC bonus is data (#147):** `ac_bonus:` in a spell's YAML (Shield 5, Shield of Faith 2)
    becomes `Combatant.grantTempAc`, added on top of `getBaseArmorClass()` and dropped at the start of
    that combatant's next turn. Don't hardcode a spell name to move AC.
  - **Spell slots are spent in one place (#152):** `character/SpellCost` — `of(sheet, spell)` to check
    *before* resolving, `spend(...)` only once it has. `level <n>` (last argument, after the target)
    upcasts and spends that slot — the spellbook's "⬆ Casting at 2nd level" fills it in. `/combat cast` used to spend nothing at all
    (the spellbook menu deducted the slot, and routing to the command skipped it), so a 1st-level
    spell in a fight was free. The spellbook menu now only *fills a command*; it consumes nothing.
  - **Bonus actions (#176):** `/combat bonusAction` with no argument lists what this character can
    actually do — bonus-action spells, features with `activation: bonus_action`, an off-hand attack
    when dual-wielding — each filling a command rather than firing it. `bonusAction used` is the
    "anything else" escape hatch that just marks it spent. There is **no `/combat bonus`** — the
    short alias was removed so there's one spelling to learn and to document. `/combat action` with
    no argument is character-aware the same way (held weapon, Action-cost spells, Action features),
    with the generic Dodge/Disengage/Help/Hide/Ready/Search row after it.
  - **Gear changes mid-turn (#190):** swapping weapons or donning a shield produces a *warning only* (`GearChangeNotifier`) — the object-interaction / Action cost is never auto-consumed or blocked. `TurnState` snapshots the weapon held at turn start.
- **DM entities & items (`/dmentity <sub>`):** `spawn`, `list`, `remove`, `rename`, `revive`, `teleport`, `info`, `trade`, `cleanup`, `shop <view|add|restock|adjust|discount|markup|reset|setfunds|setmultiplier>` (no `create` — a merchant needs `shop:` in its YAML). (`spawngroup` is registered but unimplemented — it prints a notice, see #79.)
  - **Entity identity (#194):** a template's `id:` is the permanent key — it's written into every spawned armor stand's PDC and looked up on restore, so changing it orphans anything already in the world. `name:` is only read *at spawn*; a live creature's name is per-instance state on its body, so renaming one is `/dmentity rename`, not a YAML edit + `/dm reload`. Everything else on a spawned entity still comes from the shared template (see #194).
- **DM admin (`/dm <sub>`):** `add`, `remove`, `list` (role mgmt; add/remove op-only), `give`, `check` (DM-first checks, #186), `hp` (change HP anywhere, #175), `object` (annotate locks/traps/hidden blocks, #185), `mode` (DM toolbar), `tp`, `rest <character> <short|long>`, `resource <restore|consume> <character> …`, `reload`.
  - **HP changes aren't combat-only (#175):** `DamageHandler` takes a **nullable** `CombatSession`, so a trap, a potion or a DM correction runs the same resistance → damage → downing → persistence path as a sword swing. `CombatTargets` resolves the live `Combatant` when a fight is running and a transient one otherwise; out of combat the messages go to the affected player and the DMs instead of the table. **Never write a second HP path** — route new sources of damage or healing through `DamageHandler`.

**DM authorization:** a "DM" is an op, a holder of the `jkvtt.dm` permission node, OR a
player added via `/dm add` (`DMManager.isDM`). DM commands are gated in-command, not via
plugin.yml permissions (a plugin.yml permission would default to op-only and block `/dm add` DMs).

## In Progress / Future Work

### Not Yet Implemented
- ❌ Level-up system (all characters are level 1)
- ❌ Multiclassing
- ❌ Feats
- ❌ Conditional spell application (Genie patron, Lunar Sorcery)
- ❌ Conditional advantages application
- ⚠️ Combat system — largely implemented: initiative, turn/action economy, attack/spell rolls, damage/healing, temp HP, death saves (#97–#101); conditions with advantage/disadvantage (#103); the Effect Engine (#70: active buffs like Rage, the breath-weapon action path, passive features like Lucky/Savage/Relentless, resistances); AoE aim preview (#173); Hex (#178); and the autoRoll/manualRoll/total command redesign (#183). Reaction windows that hold the damage until the target answers, and Shield actually moving AC (#195). Remaining/rough edges: enemy-visibility polish (#102), the rest of the action-economy menu (#176 — bonus actions list, actions still just markers), out-of-combat casting resolving rolls (#152), and assorted spell mechanics (#182). Combat crash recovery (#105) is largely done — sessions restore on boot; only the in-progress turn resets and stray turn-glow isn't scrubbed at startup. Much of this is committed but largely un-playtested.
- ❌ Equipment management (equip/unequip in-game)
- ❌ Persistence of player-chosen tool/language proficiencies (Issue #17)

### Planned Enhancements
- Issue #70: Structured features system (usage tracking, action economy, save DCs, damage formulas)
- Spell slot recovery for Warlocks (short rest pact magic)
- Equipment inventory management
- NPC interaction system
- Encounter builder
- Issue #194: [Epic] Live entity instances — a spawned entity is a thin wrapper over a *shared*
  template (only name/HP/dead/shop are per-instance), so you can't arm one guard differently from
  its siblings. Per-instance overrides for AC, abilities, attacks and gear; a `/dmentity edit` GUI;
  and an alias/reveal model to replace the binary `???` hidden flag (supersedes that half of #102).
  `/dmentity rename` is the first slice, already landed.
- Issue #188: [Epic] Magic items & attunement — magic item schema (`+N`, charges, recharge),
  attunement tracking with a chest-style GUI, bonuses gated on being attuned. Deliberately scoped
  *before* level-up (#153): we have shops, chests and loot with no treasure to put in them.
  Attunement is a **short** rest in RAW (DMG 138), with `attunement.time` per item for the
  artifact exceptions.
- ~~Persist equipped armor~~ **done** (#31): equipped armor/shield save and restore, and
  `ArmorEquipListener` now tracks them live. Current HP, temp HP, spell slots and class resources
  persist event-driven — `CharacterSheet` flushes to disk on every change, plus a save per combat turn.

## Notes

- Race and class data is declarative in YAML - add new content without touching Java code
- The `conditional_advantages` and `conditional_bonus_spells` fields are parsed but not yet applied to characters
- Character sheets are read-only in-game (use commands for rest, no HP editing yet)
- The NPC system is separate and allows spawning stat-block entities
- **Known Bug Fix:** Class armor/weapon proficiencies now correctly applied to all characters (previously only racial/subclass proficiencies worked)
