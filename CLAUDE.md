# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Paper/Spigot Minecraft plugin that transforms Minecraft into a virtual tabletop for D&D 5th Edition. Players can create D&D characters following 5e rules, manage equipment, cast spells, and engage in combat mechanics—all within Minecraft.

**Target Platform:** PaperMC 26.2 (Java 25). The build downloads JDK 25 itself (Gradle toolchain + foojay in `settings.gradle`); the server needs Java 25 to run.

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

### Tests (#14)

`gradlew test` (and `gradlew build`, which runs it) executes the JUnit suite in `src/test/java`.
**A failing test fails the build**, so no jar is produced.

- **Tests run against the repo's real `DMContent/`**, loaded once by `TestContent.load()` through the
  same `DataManager` the plugin uses. `ContentLoadTest.contentCheckIsClean` fails on any content-check
  warning, so "a clean load prints nothing" is enforced, not a convention. `DMContent` is a declared
  test input, so a YAML-only edit re-runs the tests instead of being skipped as up to date.
- **Helpers:** `TestContent.character(race, subrace, class, background, scores(...), skills...)` builds a
  level-1 sheet the way a saved one loads; `TestContent.session(...)` builds a creation session with
  its choices, and `merged(session)` gives the menu's sections.
- **What can't be tested here:** anything needing a live server: `ItemStack` creation (starting gear,
  `also_give`, thieves' tools breaking), online players, chat prompts. Those stay in `TEST_PLAN.md`.
- **Don't write throwaway `main()` harnesses in the scratchpad to check logic.** Write the check as a
  test, so it keeps guarding after the session ends. When you fix a bug, add the test that would have
  caught it.

### The manual test plan (`TEST_PLAN.md`)

- **Rows go under the character that tests them**, not a new section per ticket: a fighter feature
  under *Fighter*, a spell under the class that casts it, a race trait under *Races*. Only rows that
  don't care who you play go under *Any character* / *DM tools*; anything needing a second, non-DM
  player goes in Part 2. The point: the tester makes a character once and works straight down.
- **End each row with its ticket number**, e.g. `(#229)`, when there is one. A ticket's rows end up
  spread across several characters, and the number is how you find them all, both to report a
  failure and to close a `needs-testing` ticket once every one of its rows is ticked.

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
   The name step has two ways, same rules (`CreationNameListener.validate`): **Name via form**, a
   dialog (`CreationNameDialog`, also what Finish opens when there's no name), and **Name via chat**
   (`CreationNameListener`). The form replaces the anvil idea: reliable anvil text input needed a
   library (#121), and dialogs are Paper's own text box. (The old `CharacterNameListener` and
   `AnvilNameListener` were dead iterations and have been removed.)
3. Character data persists to `plugins/jkvttplugin/Saved/Characters/` as YAML files

### Testing Character Features
- `/dm rest <character|all> short [1h]` - Recover short rest resources and innate spells (DM-only; run it when the rest is over, the time moves the clock)
- `/dm rest <character|all> long [8h]` - Fully restore HP, spell slots, and all resources
- Right-click character sheet item to view character stats
- Click ability scores to view skills and roll checks
- Click skills to roll with advantage/disadvantage

### Testing Shop System (Issue #75)

**1. Setup Test Merchant:**

A merchant is an entity whose YAML has a `shop:` section — there is **no `shop create`**; `shop add`
on an entity without one fails with "is not a merchant". `balin_blacksmith` ships with a shop. The
commands take the spawned creature's **name** ("Balin"), not the template id:
```
/dm entity spawn balin_blacksmith
/dm entity shop add Balin shortsword 10 gold 3      # <item_id> <price> <currency> [stock]
```
See `docs/authoring-entities.md` → *Merchants* for the YAML.

**2. Test Tab Completion:**
- Type `/dm entity shop add Balin ` and press TAB → should suggest all item IDs (weapons, armor, items)
- Type `/dm entity shop add Balin longsword 15 ` and press TAB → should suggest currencies (gold, silver, copper, platinum, electrum)
- Type `/dm entity shop restock Balin ` and press TAB → should suggest only items in Balin's current inventory

**3. Test Player Buying (Merchant to Player):**
```
/dm entity trade Balin
```
- Merchant GUI should open with items for sale
- Each item should show price in gold pieces
- Execute a trade to buy longsword
- Verify stock decreases: `/dm entity shop view Balin`
- Buy remaining stock until item is out of stock
- Verify "out of stock" message appears

**4. Test Player Selling (Player to Merchant):**
```
/dm give <you> longsword 1
/dm entity trade Balin
```
(`/dm give <player> <item_id> [amount]`: the player is required and always first, your own name
included, so tab completion goes players → items → amounts. The item type is auto-detected from the id — there is no
`<item_type>` argument. There is no standalone `/dmgive`; it lives under `/dm give`.)
- Merchant GUI should show reverse trades (player gives item, gets currency)
- Sell longsword to merchant for gold (50% of buy price)
- Verify merchant's inventory increases: `/dm entity shop view Balin`
- Verify sold item appears in merchant's stock
- Try selling an item not in merchant's acceptance list → should fail

**5. Test NBT-Based Item Identification:**
- Create two longswords with different display names but same item_id
- Verify both are recognized as "longsword" by the shop system
- Verify currency items (gold_piece, silver_piece) are properly identified
- Check that items have `item_id` NBT tag: drop item, inspect with F3+H

**6. Test Shop Persistence:**
```
/dm entity shop view Balin
```
- Note current stock levels
- Restart the server (not `/reload confirm` — Paper plugin reloads are unsupported)
- Verify merchant still exists and stock persists
- Check `plugins/jkvttplugin/Saved/Shops/<instance-uuid>.yml` exists — shops are saved **per spawned
  creature**, keyed by its instance id, not by the template id
- Verify both stock decreases (from buying) and inventory increases (from selling) persist

**7. Test Complete Buy/Sell Cycle:**
1. Buy longsword from merchant (stock: 5 → 4)
2. Verify stock decreased: `/dm entity shop view Balin`
3. Sell longsword back to merchant (stock: 4 → 5)
4. Verify merchant inventory increased
5. Remove merchant: `/dm entity remove Balin`, then `/dm entity spawn balin_blacksmith`
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
- **Languages:** `DMContent/Languages.yml` (optional list of homebrew languages; the PHB ones are built in)
- **Sounds:** `DMContent/Sounds.yml` (moments, combat music tracks, the DM's Sound Board; #16)
- **Damage-type looks:** `DMContent/DamageTypes.yml` (a particle per damage type, plus `healing` / `buff` / `arcane`: the hit puff and every spell's default look; #230)

Each category has a corresponding loader in `src/main/java/io/papermc/jkvttplugin/data/loader/` and model in `data/model/`.

**Authoring guides** (every field, what it actually drives, and the silent failure modes):
`docs/authoring-spells.md` · `docs/authoring-items.md` (weapons, armor, items) ·
`docs/authoring-entities.md` (NPCs, monsters, merchants, loot) ·
`docs/authoring-races.md` · `docs/authoring-classes.md` · `docs/authoring-backgrounds.md`, with
the pieces those three share (proficiency ids, `player_choices`, duplicate proficiencies) in
`docs/authoring-character-options.md`.

### Proficiencies, tools & languages: one id each

- **A tool proficiency is an item id.** `ToolRegistry` is built from items tagged `artisan_tool` /
  `musical_instrument` / `gaming_set` / `tool` (plus built-in `vehicles_land|water|air|space`,
  the only tools that aren't items). A homebrew tool is just an item with the tag.
- **Every spelling folds to one id at load** (`ToolRegistry.idOf`, `LanguageRegistry.idOf`):
  `"Navigator's Tools"` = `navigators_tools`, `vehicles(land)` = `vehicles_land`, `Deep Speech` =
  `deep_speech`. Sheets, choice keys and grants all hold ids; display names come from the registry.
  Never compare display strings.
- **Choices go through `ChoiceContributor`**, one path for race, subrace, class, subclass and
  background. (Five hand-copied switches used to drift apart: backgrounds dropped tool choices.)
- **Grants carry ids** (`AutomaticGrant.proficiency(...)`, `grant.key()`). "Already known"
  filtering (`KnownItemCollector`) and duplicate detection read the session's grants, so every
  source is covered at once.
- **Duplicate proficiencies (PHB p.125)**: the same skill/tool from two fixed sources becomes a
  "Replace duplicate …" pick (`CharacterCreationService.duplicateReplacements`).
- **Chosen tools/languages persist** (`chosenTools` / `chosenLanguages`); grants re-derive on load.
- **`also_give: true`** on a `type: tool` choice grants the picked tool as an item too (Guild Artisan).
  Starting-equipment picks are resolved from **every** source in one pass (`resolveEquipmentFromChoices`);
  it used to run only for class and background, so a race/subclass equipment pick granted nothing.
- **Expertise** (`type: expertise` choice; rogue) doubles proficiency on skills and tools the character
  already has. `CharacterSheet.hasExpertise`, persisted as `expertise`. **Tool checks**: `/dm check <p> tool <tool>`
  → `getToolCheckBonus` (ability + tool proficiency, ×2 with expertise); the ability defaults to the item's
  `check_ability:` (thieves' tools → DEX). Picking a lock is a thieves' tools check, not Sleight of Hand.

### Icons & Materials (Resource Pack)

Two clear YAML keys, used consistently — change them in YAML, not code:

- **`material:`** — the **vanilla Minecraft item** to render (e.g. `IRON_SWORD`, `GOLD_INGOT`).
  This is what everyone sees, and the fallback for players without the resource pack.
  Used by the things a player physically receives: **weapons, armor, items**. Absent/invalid
  → `PAPER` for all three (there's no per-type default). Armor's `material:` must be a wearable
  chestplate (or `SHIELD`), or equip tracking never sees it.
  **Spells** follow the same convention: `material:` sets the base item, absent → a level-based
  default (cantrip → `PAPER`, low → `BOOK`, high → `ENCHANTED_BOOK`); `custom_model:` is optional.
  (No auto `spell_<name>` model — that produced purple placeholders.)
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
  class resources alike. `icon:` isn't read anywhere (the old fallback is gone), so a file still
  using it just gets the vanilla default. In code the same split holds: `getCustomModel()` for the
  pack model, `material` for the vanilla item.
- Fixed game concepts use hardcoded models: ability tiles resolve to `<abbr>_icon`
  (`str_icon`, `dex_icon`, …). Other fixed UI icons (Back arrow, tabs) are vanilla until
  their pack textures exist; a house-rule/UI-icon override config is future work (#104).
- A model needs a **complete chain** to render: `items/<name>.json` → `models/item/<name>.json`
  → `textures/item/<name>.png`. A model with no texture shows a broken (purple) placeholder,
  which is worse than the vanilla fallback — so only reference models whose textures exist.
- The `ResourcePack/` folder is **not** committed (gitignored); it lives locally for testing. How to add models, sounds and music to it, test it locally and share it: `docs/resource-pack.md`.

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
  Anything the content loaders log as a warning while loading counts too (`DataManager.loadAllData` captures
  their loggers, #243), and `/dm reload` refuses outright when a file won't parse (`syntaxErrors`), so a
  syntax error can't wipe the content it defines.
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
- **Prepared spells (#218):** `character/PreparedSpells` is the one place. `kind()` is `CLASS_LIST`
  (cleric, druid, paladin, artificer: prepared = known spells) or `SPELLBOOK` (wizard: `knownSpells`
  is the book, `CharacterSheet.preparedSpellIds` the day's pick, saved as `preparedSpells:`).
  **Every cast path asks `PreparedSpells.castRefusal(sheet, spell, asRitual)`**, not `knowsSpell`;
  it also covers rituals (a wizard casts a book ritual unprepared, `/character cast <id> ritual` and
  `/combat cast … ritual`; others need it prepared). Changing them is `PrepareSpellsMenu`, allowed
  while `isLongRestOpen()` (a long rest, or a new character; closed by a fight or the next rest).
  A subclass's `bonus_spells` are always prepared and don't count. The rest summary
  (`RestCommand.sendRestOptions`) lists what can be done during the rest: Hit Dice and Arcane
  Recovery (`recover_slots:`) after a short one, changing prepared spells after a long one.

**Equipment System:**
- Weapons/armor/items have custom NBT data for identification — all via the shared `item_id` tag
  (`ItemUtil.getItemId`). There is no `armor_id`/`weapon_id` key; anything reading one is a bug.
- `WeaponListener` handles weapon interactions and the **left-click attack prompt** (#189)
- **Armor proficiency is enforced (#209, PHB p.144).** `CharacterSheet.unproficientArmorWorn()` /
  `armorPenaltyApplies(ability)` is the one source: disadvantage on weapon attacks
  (`Combatant.attackAdvantageAgainst`), STR/DEX saves (`saveAdvantage`), STR/DEX checks, skills and
  tool checks (`RollOptionsMenuHandler`), and initiative; `/combat cast` and `/character cast` refuse.
  Equipping it warns but never blocks. No auto-equip at creation; equipped armor comes from the real
  slots below.
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
  - `bonus_spells` - Domain/oath spells (always prepared/known, don't count against limit)
  - `expanded_spells` - A warlock patron's expanded list: added to what the player may PICK, each costs a pick (#228)
  - `expanded_spell_lists` - Whole class lists to pick from (Divine Soul: `[cleric]`)
  - `features` - Effect Engine features, as on a class (Draconic Resilience: AC formula + `max_hp_per_level`, #224)
  - `additional_spells` - Bonus cantrips (e.g., Light cantrip for Light Domain)
  - Spells or other things based on a player choice (Genie kind): a `custom` choice whose options carry `grants:` (#222)
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
- A roll a player makes from their own sheet is **private** to them, with **[Show the DM]** (the DM
  can then [Share] it); a DM-called check comes back to the DM first. Nothing a player rolls for
  themselves is broadcast (#186).
- `/dm check` also does groups (`all` or `A, B`, verdict = at least half succeed, `CheckManager.Group`,
  [Close now]), passive checks (`passive <skill>`, 10 + bonus ±5), and the player's pick of approach
  (`skill athletics|acrobatics`).
- Combat Help / Hide / Search (#176) are `combat/CombatActions`: Help sets `Combatant.helpedBy`
  (advantage on the next attack or check, lapses at the helper's turn); Hide rolls Stealth and the DM
  grants the **Hidden** condition (`conditions.yml`); `Combatant.afterAttackRoll` uses up a Help and
  ends Hidden after any attack roll.
- Skill proficiencies tracked from class, background, and race

**Class Resource System (Issue #25):**
- Tracks limited-use class features (Rage, Ki Points, Sorcery Points, etc.)
- Resources defined in class YAML with recovery type (short rest, long rest, dawn)
- Supports fixed amounts, ability modifiers, proficiency bonus, and formulas
- Displayed in character sheet with current/max tracking
- Recovered by `/dm rest <character|all> <short|long> [time passed]` (rests are the DM's call; there is no player rest command)
- A long rest restores short-rest resources too; a warlock's Pact Magic slots return on a short rest
  (`slot_recovery: short_rest`).
- **Hit Dice (#52):** one per level, the class's die (`CharacterSheet.getHitDiceRemaining`, saved as
  `hitDice:` only when some are spent). A short rest opens a window (`isShortRestOpen`, not saved)
  in which `/character hitdice` spends one (die + CON, the three roll buttons, healed through
  `DamageHandler`); a long rest or joining a fight closes it. A long rest gives back half, at least 1.

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

### Sounds and spell visuals (#16, #230)

- **Every sound is a name in `DMContent/Sounds.yml`**, vanilla or from the resource pack (`jkvttresourcepack:…`);
  the plugin never hardcodes one. Custom sounds need a resource pack (sounds are client-side assets); a
  datapack can't add them. `sound/Sounds` plays a moment: in a fight to everyone in it (`Sounds.table`), out
  of one only to the player it's about (`toPlayer`), so a private roll stays private. The d20 sound comes from
  `RollService.resolve` for whoever typed the command (the root commands call `Sounds.setRoller`).
  Sliders: moments on Players, combat music on Records (Jukebox/Note Blocks), the Sound Board on Ambient.
- **Combat music** is `sound/CombatMusic`: one task per fight re-checks listeners each second, hushes vanilla
  music, and loops by the track's `length:` (the server can't read an .ogg's length). A creature's
  `combat_music:` picks the track; `/combat music` switches it.
- **The DM's Sound Board** is `/dm sound` and a DM-mode tool (`SoundBoardMenu`).
- **Spell visuals** are `combat/SpellVisuals`: shape from what the spell does (bolt / burst / glow), look from its
  damage type in `DamageTypes.yml`, overridable per spell with `visual:`. Particles are vanilla only: nothing can
  add a particle type. Damage-type fire is `setVisualFire`, never real fire ticks (those burn a player's hearts).

### UI standards: one helper per thing

Players read the same kinds of text over and over, so each kind has **one** builder. Before
writing a message, a hover or a prompt, use the helper; if it can't do what you need, extend it.
Hand-rolled copies drifted into ten wordings of the same roll button, which is what these replace.

| What | Use | Not |
|---|---|---|
| **A roll prompt** (d20 or dice, player's or the DM's for a creature) | `RollPrompt.line(lead, color, base, dice, bonus)`: always `[Roll it] [I rolled…] [My total…]`, each **filling chat, never running** (no total button when nothing is added; pass `RollPrompt.d20(advantage)` as the dice so the hover says "2d20 and keep the lower"). `RollPromptTest` fails the build if another file builds its own. | your own `[let the game roll]` |
| **A roll result** | `RollPrompt.gameRolled` / `youRolled` / `yourTotal` ("🎲 2d6 [4, 3] +3[CHA] = 10", "🎲 you rolled 7 +3 = 10", "🎲 your total: 10"). A d20 through `RollService.resolve` already does this, plus the nat 1/20 callout. | "d20(14)…", "(provided total)" |
| **The bonus a roll adds** | Labelled by source, the same string in the prompt and the result: `+3[INT] +2[Prof]`. Sheet: `getSaveBreakdown` / `getSkillBonusBreakdown` / `getSpellAttackBreakdown` / `getSpellModBreakdown`; either side: `Combatant.saveBreakdown`; weapons: `AttackHandler.buildPlayerModBreakdown`; a formula's own flat part (a potion's 2d4**+2**, Magic Missile's +1): `RollPrompt.split(formula, source)` → `+2[Potion of Healing]`, labelled with the thing itself | `+5[Spell]`, "your spellcasting modifier", an unlabelled `+2` |
| **A roll command typed without roll words** | `RollPrompt.again(sender, lead, dice, bonus)`: the three buttons on the exact line they typed (`/combat` and `/character` remember it) | "type 'manualRoll <n>', or 'autoRoll'" |
| **Wrapped text** (lore, hovers) | `Util.wrapText(text)` / `LoreBuilder.addWrappedText(text, color)`: `Util.WRAP_WIDTH`, paragraph breaks kept | a width of your own |
| **What a spell does** | `DndSpell.detailLore()`, used by the spellbook item, the creation tile and the chat hover | a new summary |
| **Reading a name** | `NameUtil.readName` / `collapseName` (quoted or not); a creature via `DndEntityInstance.findByName`, a character via `CharacterResolver` | `args[i]` |
| **Suggesting names** | `CombatTargets.suggestions(typed)` / `characterSuggestions(typed)`, positions via `NameUtil.collapseForCompletion` (#215 moves the rest) | raw `getDisplayName()` lists |
| **Asking the DM** | `DmRequests.send` + `DmRequests.button`: one open request per player, a repeat isn't re-sent, a new one replaces the old | a fresh DM message per click |
| **Does it reach?** (spells and weapons, in and out of a fight) | `Reach.spell` / `weapon` / `creatureAttack`: 5 ft of slack, Self = the caster only (an area from you isn't checked); out of reach → `Reach.refuse` ([Ask the DM], or [Do it anyway] for a DM), and `Reach.spend` when the action goes through | a range check of your own |

### Key Design Patterns

**Player Choice System:**
- `PlayersChoice` + `PendingChoice` → generic system for handling player choices during character creation
- Used for equipment selection, spell selection, and racial/class feature choices
- `ChoiceUtil` provides helper methods for processing choices
- `ChoiceMerger` handles merging and deduplicating choices from multiple sources

**ItemStack Creation:**
- `ItemUtil` provides centralized item creation with NBT tags
- D&D objects (`DndWeapon`, `DndArmor`, `DndItem`, `DndSpell`) can create their own `ItemStack` representations
- Every item is identified by the one shared `item_id` tag (`ItemUtil.getItemId`), never `weapon_id` / `armor_id`

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
   - `bonus_spells` - Domain/oath spells, free (list of spell IDs); `expanded_spells` for a patron-style "choose from" list
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

1. Edit the race YAML (e.g. `tiefling.yml`, or a subrace under `elf.yml` → `dark_elf`)
2. Add an `innate_spells` list. Each entry takes `spell_id` (required), `level_requirement`
   (character level, default 1), `spell_level` (slot level, default the spell's own),
   `uses` (a number or `"proficiency_bonus"`), `recovery` (`long_rest` / `short_rest`) and
   `casting_ability`. Cantrip-ness comes from the **spell's own level**, so no flag is needed.
   Full reference: `docs/authoring-races.md`.
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

Stat-block lines beyond the basics (#252): `saving_throws: {constitution: 4}` (the printed total, like
`skills:`; unlisted = the ability modifier, `DndEntity.getSaveBonus` / `getSaveLabel` is the one source for
every creature save), and `damage_resistances` / `damage_immunities` / `damage_vulnerabilities` /
`condition_immunities` (lists; `Combatant` reads them from the template, `DamageHandler` already applies
them, and `Combatant.isImmuneToCondition` refuses a condition). Multiattack, riders and traits are #253–#255.

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
- ✅ Darkvision, movement speeds (swimming, flying, climbing); darkvision gives Minecraft night vision (#148, `CharacterSight`, applied with the body in `CharacterBody.apply`), and a possessing DM gets the creature's (entity `darkvision:`). In the dark (light ≤ 7 at eye level) the render distance also drops to the darkvision range in chunks, min 2 (`sight.darkvision_view_limit`)
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
- ✅ Choice-based grants (#222): a custom option can grant resistances, bonus spells, or the racial spells' casting ability (Genie kind, dragonborn ancestry, genasi)
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
- ✅ Features & Traits page (#65): race traits, class/subclass `features_by_level` text (`FeatureText`: a map `{name, description}` or a `"Name: text"` string, case kept), your picks, and every Effect Engine feature; a usable one fills its `/combat use` / `/character use` on click

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

Consolidated into 4 base commands (Issue #122): `/character`, `/roll`, `/combat`, `/dm`.
Entities moved under `/dm entity` — `/dmentity` is **gone**, so `/dm` + Tab no longer
autocompletes into it. The legacy per-action commands have been removed (their executor
classes remain and are delegated to from CharacterCommand / DmCommand).
- **Character (any player):** `/character <create|view|list|rest|give|delete>` (alias `/char`); `create <player>` and `give <player> <name>` are DM-only. `view` opens your own characters only (a DM can open anyone's); `delete` by a player is a request a DM approves.
- **Roll:** `/roll <XdY[+Z]>` (alias of the old `/rolldice`).
- **Combat (`/combat <sub>`):** `start`, `add`, `remove`, `surprise`, `initiative`, `nextturn`, `endturn`, `turn`, `status`, `finished`, `reveal`, `hide`, `action`, `bonusAction`, `movement`, `attack`, `damage`, `heal`, `temphp`, `deathsave`, `cast`, `save`, `concentration`, `use`, `reactions`. Players may use `action`/`bonusAction`/`attack`/`endturn`/`deathsave` on their own turn only.
  - **Initiative order:** `CombatSession.sortByInitiative` keeps the current turn with its *owner*
    when it re-sorts mid-fight (it used to keep the index). A player added mid-fight gets a game roll
    as a placeholder and may replace it with their own until their first turn
    (`canRollOwnInitiative` / `useOwnInitiative`). The DM's `/combat initiative <who> set <n>` works any time.
  - **Roll input (#183):** a d20 action takes one bare keyword — `autoRoll` (game rolls, applies advantage → 2d20), `manualRoll <n>` (you rolled it, game adds mods), or `total <n>` (final, nothing added). Damage uses `manualRoll <n>` / `autoRoll <dice>` / a flat `<amount>`; the **damage type is automatic** (`type <t>` overrides). There is **no** `--roll`/`--total`/`--type` — those aliases were removed. `RollService.parseInput`/`RollInput` is the one parser (a non-number after `manualRoll`/`total` is reported, not dropped; `isDice` tells dice from typos), and `RollPrompt.baseLine` strips the typed roll words so a re-prompt's buttons don't stack on a bad one; `RollService.resolve(...)` applies reroll (Lucky) + advantage. The out-of-combat `/character check|save|loot` roller is separate (`RollOptionsMenuHandler`).
  - **Attacking (#189):** on your turn, holding a weapon, **left-click** the enemy (or left-click while looking at them) and `WeaponListener` hands you the filled-in `/combat attack`. The click only *prompts* — the roll still goes through the command. **Right-click never attacks**; it means "use" (spell focus, area-effect confirm #173), and is suppressed only for ranged weapons so a bow does not loose a real arrow. Left-clicking a combatant is always cancelled so a punch never damages the armor stand they are rendered on. **Spells the same way (#179):** picking a targeted spell from the spellbook on your turn *readies* it (`SpellTargeting`, lapses with the `TurnState`), and the next left-click at a creature fills `/combat cast <spell> <target> [level N]` with the roll buttons for an attack spell; an area spell goes straight to its aim preview. `CombatCommand.afterCast` spends the slot, concentration and action for both, on an area spell's confirm too.
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
  - **A spell's AC bonus is data (#147):** `ac_bonus:` in a spell's YAML (Shield 5, the caster only)
    becomes `Combatant.grantTempAc`, added on top of `getBaseArmorClass()` and dropped at the start of
    that combatant's next turn. Don't hardcode a spell name to move AC.
  - **Spells with timed effects on their targets (#225):** a spell's `effect:` block (the feature `apply:`
    vocabulary) goes on each target as an `ActiveEffect` (`combat/SpellEffects`): Bless, Bane (on a failed save),
    Guidance, Resistance, Shield of Faith. A `roll_bonus` die is written into the labelled bonus
    (`+1d4[Bless]`) by the breakdown helpers, and **`RollService.resolve` rolls any dice it finds in a
    bonus label**, so every d20 path picks it up. Rounds tick at the holder's turn; out of combat only the
    DM moving the clock (`TimeCommand.shift` → `SpellEffects.passTime`) runs them down. The caster's
    concentration ending ends it on everyone (`CharacterSheet.breakConcentration`/`setConcentratingOn`).
    `until_used` effects are spent by `SpellEffects.useUp` at each attack/check/save site. Creatures hold effects
    too (`DndEntityInstance.getEffects`, in memory).
    Typed bonus dice: `manualRoll 14 3` (parseInput stores them for that tick; `rollLabelDice` takes them). A manualRoll
    never rolls a bonus die for you: missing or impossible → resolve returns null and `RollPrompt.buttons` shows why.
    **Bardic Inspiration (#40)** is a feature with `target: other_creature` + `range:` (`FeatureUse.give`),
    whose effect is `held`: never added by itself; after each d20 roll the holder is asked (`InspirationPrompt.offer`,
    answered by `/character inspiration`). A missed attack re-checks AC; failed concentration/death saves wait (`onDeclined`).
    Multi-target spells are picked by clicks in `SpellTargeting` (one click per target, [+ me], [Cast on these]).
  - **Spell slots are spent in one place (#152):** `character/SpellCost` — `of(sheet, spell)` to check
    *before* resolving, `spend(...)` only once it has. `level <n>` (last argument, after the target)
    upcasts and spends that slot — the spellbook's "⬆ Casting at 2nd level" fills it in. `/combat cast` used to spend nothing at all
    (the spellbook menu deducted the slot, and routing to the command skipped it), so a 1st-level
    spell in a fight was free. The spellbook menu now only *fills a command*; it consumes nothing.
  - **Level-1 class features (#229):** a custom choice option can `grants: { features: [...] }`
    (inline Effect Engine features), which `CharacterSheet.getAllFeatures` includes: that's the
    Fighting Style. New effect primitives: `attack_bonus` (Archery), `ac_bonus` (Defense),
    `bonus_damage when: melee_one_handed` (Dueling), flags `reroll_low_damage` (GWF, via
    `TurnState` → `/combat damage autoRoll`) and `offhand_ability_damage` (TWF), and `sneak_attack`
    (`SneakAttack`: automatic only with advantage; otherwise the hit offers [Ask the DM] and the DM's
    [Allow] adds the dice to the pending damage, or deals them on their own if it already landed. It
    rides on `TurnState.pendingSneak` and is spent in `applyHit`, so a hit Shield cancels keeps it).
    Creation's two-weapon picks ("martial_weapon x2") fill one slot per drill-down pick
    (`EquipmentOption.fillFirstOpenSlot`); a bundle with an open slot isn't a finished pick. A feature with `heal:` / `sense:` (Second Wind, Lay on Hands, Divine Sense) runs through
    `FeatureUse`, from `/combat use` and from `/character use` out of combat. `/combat use` spends the
    action / bonus action a feature's `activation` says. The dice roller takes several groups
    ("1d8+1d6+3").
  - **Bonus actions (#176):** `/combat bonusAction` with no argument lists what this character can
    actually do — bonus-action spells, features with `activation: bonus_action`, an off-hand attack
    when dual-wielding, a monk's bonus unarmed strike — each filling a command rather than firing it.
    **`/combat attack` works out what an attack costs** (`AttackCost.decide`): the Action (starting the
    Attack action, `TurnState.markAttackAction`), then the Attack action's further attacks (Extra Attack,
    #153; 1 per action until level-up), then the bonus action if a bonus attack fits the weapon
    (`BonusAttack.check`: off-hand light weapon, or a feature with an `attack:` block). The word `bonus`
    asks for the bonus action while the Action is still free. `combat.bonus_attack_timing`: `any_time`
    (default, BG3) or `after_attack_action` (tabletop). The action bar names what the bonus action is
    good for (`READY: Martial Arts`, or — when nothing). `bonusAction used` is the
    "anything else" escape hatch that just marks it spent. There is **no `/combat bonus`** — the
    short alias was removed so there's one spelling to learn and to document. `/combat action` with
    no argument is character-aware the same way (held weapon, Action-cost spells, Action features),
    with the generic Dodge/Disengage/Help/Hide/Ready/Search row after it.
  - **Concentration (#152):** `ConcentrationManager` owns it. `DamageHandler` calls `onDamage` once —
    it covers a concentrated spell AND a channelled ritual with one roll, so a caster doing both is
    asked once. DC is max(10, damage/2). **The game never rolls it**: the target gets the standard
    `autoRoll`/`manualRoll`/`total` prompt with the modifier spelled out ("+1 CON, +2 proficiency"),
    answered by `/combat concentration`; the DM rolls for a creature. Downed, dead or incapacitated
    ends it with no save. `/combat cast` sets concentration when the spell resolves (`castMark` does
    its own for Hex/Hunter's Mark), a second concentration spell replaces the first, and the action
    bar shows **◈ <spell>**. An unanswered save holds the caster's turn, like a reaction window.
    (The old `RitualManager.onDamage` rolled the check itself, the only d20 in combat the game took
    out of the players' hands; it's been removed.)
  - **Gear changes mid-turn (#190):** scrolling to another weapon costs nothing (an action-bar hint only).
    The switch is settled when the weapon is **used**: `WeaponSwitch.allow`, from the left-click prompt and
    `/combat attack`, holds the attack (nothing rolled) for [Yes, I'm switching] (uses the free object
    interaction) or [Ask the DM]; a second switch would take the Action, so only the DM can allow it. Empty
    hands draw without asking. `TurnState` snapshots both hands at turn start. A shield is still a warning only.
  - **Damage approval (#175):** a player's `/combat damage` applies at once, except when `combat.damage_approval` says to hold it: `reactions` (default: a hit that opened a reaction window, flagged on `TurnState`), `always`, or `off`. A hold is keyed to the attacker's `TurnState`, so it dies with its turn; the DM gets [Apply] / [Deny]. A **refused** `/combat damage` (off-turn, no hit, wrong target) offers the player [Ask the DM] (`askDmButton`), whose Apply goes through `AdjustCommand.hp`. The DM's own damage never waits. **Surprise** is a toggle, also a DM-mode tool.
- **DM entities & items (`/dm entity <sub>`):** `spawn`, `list`, `remove`, `rename`, `revive`, `teleport`, `info`, `trade`, `shop <view|add|restock|adjust|discount|markup|reset|setfunds|setmultiplier>` (no `create` — a merchant needs `shop:` in its YAML). (`spawngroup` is registered but unimplemented — it prints a notice, see #79.)
  - **Entity identity (#194):** a template's `id:` is the permanent key — it's written into every spawned armor stand's PDC and looked up on restore, so changing it orphans anything already in the world (restore skips it and says so in the console, once per stand). `name:` is only read *at spawn*; a live creature's name is per-instance state on its body, so renaming one is `/dm entity rename`, not a YAML edit + `/dm reload`. Everything else on a spawned entity still comes from the shared template (see #194).
- **DM admin (`/dm <sub>`):** `add`, `remove`, `list` (role mgmt; add/remove op-only), `give`, `check` (DM-first checks, #186), `adjust` (the DM's override: HP, AC, conditions, in or out of a fight; a menu, a command and a DM-mode tool, #175), `view` (quick look in chat, or `full`: inventory + DM notes), `note` (DM-only notes on a character or creature), `revive` (#101), `object` (annotate blocks, #185), `mode` (DM toolbar), `tp`, `rest <character> <short|long>`, `resource <restore|consume> <character> …`, `reload`.
  - **A block's openability is one value, not flags (#185):** `InteractiveObjectManager.Obj.Opening`
    is `OPENS` / `LOCKED` / `SEALED` — set by `/dm object unlock|lock|seal`. They're mutually
    exclusive by construction, so a block can't be both pickable and never-opening. `hidden`,
    `trapped`/`disarmed`, `loot` and `description` are genuinely independent and stay as their own
    fields: a chest can be locked AND trapped AND hold loot AND carry flavor text. `SEALED` is
    scenery — it never opens and **no DM is pinged**; `LOCKED` pings the DM with [call a check].
    The `description` renders on every path, including the trap one.
  - **Keys (#200):** `Obj.keyItem` / `keySingleUse`, set by `/dm object key <item_id> [single-use]`.
    A player carrying that item gets past the lock with no roll (`Obj.keyOpens`) and it **stays
    open** afterwards (a consumed key mustn't leave a lock that can never open). A trap still fires
    first. Keys are ordinary items tagged `key`; one per lock is just a uniquely named item.
  - **Study checks (#231):** `Obj.study` (`dm/Study`): passive or rolled, up to three skills, the
    description plus up to three DC tiers of text, remembered per **character** and repeated on later
    clicks, optionally DM-first. Authored in `StudyDialog` (the annotate dialog's "Save + study check…");
    the click is `StudyInteraction`, and the roll is an ordinary `/character check` that
    `resolvePhysical` hands to `StudyInteraction.takeRoll`. The record prints to the console (#193).
    Design: `docs/exploration-and-checks.md`.
  - **One prompt on every container (#185):** right-clicking a chest gives **[Open it]** /
    **[Ask for a check]** — the same two buttons whether or not the block is annotated, because a
    prompt that only appeared on annotated blocks would itself be the tell, and a menu naming
    "[Check for traps]" / "[Pick the lock]" announces what the DM prepared. The player says aloud
    what they're doing and the DM calls the check (#186). Scope is `objects.interaction_prompt`
    (`all_containers` default / `annotated_only` / `off`). Buttons are Adventure
    `ClickEvent.callback`s, not commands — nothing to type, replay, or aim at a distant chest.
    **`hidden` is absolute**: no prompt, no trap, nothing until `/dm object reveal` (blundering into
    a trap is #202's walk-over trigger). A sprung trap auto-disarms; `/dm object arm` resets it.
  - **HP changes aren't combat-only (#175):** `DamageHandler` takes a **nullable** `CombatSession`, so a trap, a potion or a DM correction runs the same resistance → damage → downing → persistence path as a sword swing. `CombatTargets` resolves the live `Combatant` when a fight is running and a transient one otherwise; out of combat the messages go to the affected player and the DMs instead of the table. **Never write a second HP path** — route new sources of damage or healing through `DamageHandler`.
  - **Death lives on the sheet (#101).** `CharacterSheet` owns `dead` and the death-save tally, persisted
    (`dead:`, `deathSaves:`), and applies the 0-HP rules in `takeDamage`: damage at 0 is a failed save
    (two on a crit), massive damage kills outright. `Combatant` reads them from the sheet (its own fields
    are only a snapshot for an offline player in a restored fight), and an entity combatant reads its
    instance. The dead ignore healing and rests; the one way back is `DamageHandler.revive`
    (`/dm revive`, `/dm entity revive`). A new fight doesn't reset a dying character's tally.
    A dead player's body is `PlayerCorpse`: an armor stand tagged `jkvtt:corpse_of` = character id,
    placed where they fell (`DeathSaveHandler.leaveBody`), right-click → [Ask for a check]. The player goes
    to spectator (previous mode kept in their PDC) until revived or `/character create`. Revive removes the
    body and offers the DM [Teleport them to the body], never an automatic teleport; a stale body is
    cleared on chunk load.
  - **Conditions and the DM's AC adjustment live on the sheet / creature too (#175).** `CharacterSheet`
    and `DndEntityInstance` own `conditions` and an `AcAdjustment` (amount + how long: next turn, short
    rest, long rest, until removed), persisted (`conditions:` / `acAdjustment:`, and PDC for creatures).
    `Combatant.getConditions()` / `addCondition` read and write through to the owner; its own set is
    only a snapshot for an offline player in a restored fight. A fight's end removes only turn-scoped
    conditions (`until_next_turn`, e.g. Dodging), so a poisoned character stays poisoned. Sheet rolls
    apply `self_check` / `save_disadvantage` out of combat (`RollOptionsMenuHandler.penaltyReason`).
    A creature can also have its own permanent AC (`acOverride`, first slice of #194).
  - **Characters are never erased by the game.** A player's `/character delete` is a request the DM
    approves; deletion archives the file to `Saved/Characters/Deleted/`. Other people's sheets are DM-only.

**DM authorization:** a "DM" is an op, a holder of the `jkvtt.dm` permission node, OR a
player added via `/dm add` (`DMManager.isDM`). DM commands are gated in-command, not via
plugin.yml permissions (a plugin.yml permission would default to op-only and block `/dm add` DMs).

## In Progress / Future Work

### Not Yet Implemented
- ❌ Level-up system (all characters are level 1)
- ❌ Multiclassing
- ❌ Feats
- ❌ Lunar Sorcery's once-per-long-rest free cast of the current phase's spell, and changing phase on a rest (its spells themselves are known, via `bonus_spells`)
- ⚠️ Conditional advantages — saving-throw ones apply (Fey Ancestry, Dwarven Resilience); other types are display only
- ⚠️ Combat system — largely implemented: initiative, turn/action economy, attack/spell rolls, damage/healing, temp HP, death saves (#97–#101); conditions with advantage/disadvantage (#103); the Effect Engine (#70: active buffs like Rage, the breath-weapon action path, passive features like Lucky/Savage/Relentless, resistances); AoE aim preview (#173); Hex (#178); and the autoRoll/manualRoll/total command redesign (#183). Reaction windows that hold the damage until the target answers, and Shield actually moving AC (#195). Remaining/rough edges: enemy-visibility polish (#102), the rest of the action-economy menu (#176 — bonus actions list, actions still just markers), out-of-combat casting resolving rolls (#152), and assorted spell mechanics (#182). Combat survives a restart (#105, #165): a normal shutdown suspends the fight (snapshot + keep the file; `endCombat` is only `/combat finished`), it restores on boot, visuals return as players rejoin, and the interrupted turn restarts. Active effects like Rage save with the character (#212) and come back with the rounds they had left. Much of this is committed but largely un-playtested.
- ❌ Equipment management (equip/unequip in-game)

### Planned Enhancements
- Issue #70: Structured features system (usage tracking, action economy, save DCs, damage formulas)
- Equipment inventory management
- NPC interaction system
- Encounter builder
- Issue #194: [Epic] Live entity instances — a spawned entity is a thin wrapper over a *shared*
  template (only name/HP/dead/shop are per-instance), so you can't arm one guard differently from
  its siblings. Per-instance overrides for AC, abilities, attacks and gear; a `/dm entity edit` GUI;
  and an alias/reveal model to replace the binary `???` hidden flag (supersedes that half of #102).
  `/dm entity rename` is the first slice, already landed.
- Issue #188: [Epic] Magic items & attunement — magic item schema (`+N`, charges, recharge),
  attunement tracking with a chest-style GUI, bonuses gated on being attuned. Deliberately scoped
  *before* level-up (#153): we have shops, chests and loot with no treasure to put in them.
  Attunement is a **short** rest in RAW (DMG 138), with `attunement.time` per item for the
  artifact exceptions.
  **First slice done:** magic *weapons*. A weapon's `base:` merges in its mundane stats
  (`WeaponLoader.resolveBase`; name/cost/description aren't inherited); `rarity:` and
  `magic: {bonus | attack_bonus, damage_bonus, crit_bonus_damage}` feed `AttackHandler`
  (to-hit, damage, labelled `+2[Longsword +2]`, crit-only flat bonus). Proficiency matches the
  base; magic weapons are excluded from the derived weapon tags. 42 +1/+2/+3 weapons and 3 Vicious
  weapons ship in `Weapons/magic_weapons.yml`. Next: extra dice / a second damage type (Dragon
  Slayer, Flame Tongue — damage strings are one dice group today), attunement, charges, armor.
- ~~Persist equipped armor~~ **done** (#31): equipped armor/shield save and restore, and
  `ArmorEquipListener` now tracks them live. Current HP, temp HP, spell slots and class resources
  persist event-driven — `CharacterSheet` flushes to disk on every change, plus a save per combat turn.

## Notes

- Race and class data is declarative in YAML - add new content without touching Java code
- `conditional_advantages` of `type: saving_throw` are applied (advantage on saves vs that condition tag); other types are parsed but not applied yet
- Character sheets are read-only in-game (use commands for rest, no HP editing yet)
- The NPC system is separate and allows spawning stat-block entities
- **Known Bug Fix:** Class armor/weapon proficiencies now correctly applied to all characters (previously only racial/subclass proficiencies worked)
