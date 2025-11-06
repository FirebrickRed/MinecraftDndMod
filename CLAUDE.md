# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Paper/Spigot Minecraft plugin that transforms Minecraft into a virtual tabletop for D&D 5th Edition. Players can create D&D characters following 5e rules, manage equipment, cast spells, and engage in combat mechanics—all within Minecraft.

**Target Platform:** PaperMC 1.21.3 (Java 21)

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
- Use backslashes for file paths

### Hot Reloading Data
The plugin loads D&D content (races, classes, spells, weapons, armor, items) from YAML files in `DMContent/`. Use `/reloadyaml` in-game to reload data without restarting the server.

### Testing Character Creation
1. Use `/createcharacter` to start the character creation flow
2. Alternatively, right-click a paper item named "Character Sheet" to open the creation menu
3. Character data persists to `plugins/jkvttplugin/Characters/` as YAML files

### Testing Character Features
- `/shortrest` - Recover short rest resources and innate spells
- `/longrest` - Fully restore HP, spell slots, and all resources
- Right-click character sheet item to view character stats
- Click ability scores to view skills and roll checks
- Click skills to roll with advantage/disadvantage

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
- Weapons/armor/items have custom NBT data for identification
- `WeaponListener` handles weapon interactions (showing attack modifiers)
- `CharacterSheet` auto-equips armor/shields during character creation if proficient
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
- Recovered automatically via `/shortrest` and `/longrest` commands

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
4. Run `/reloadyaml` or restart server
5. Class will appear in `ClassSelectionMenu` automatically

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
3. Run `/reloadyaml`
4. Subclass appears in selection menu for that class
5. Bonus spells and proficiencies applied automatically during character creation

### Adding a New Spell

1. Create or edit YAML in `DMContent/Spells/*.yml`
2. Define: `name`, `level`, `school`, `casting_time`, `range`, `components`, `duration`, `description`, `classes` (spell list)
3. Run `/reloadyaml`
4. Spell will appear in `SpellSelectionMenu` for classes that have it in their spell list

### Adding Racial Innate Spells

1. Edit race YAML (e.g., `drow.yml`)
2. Add `innate_spells` list with:
   - `spell` - Spell ID
   - `level` - Character level when available
   - `uses` - Number of uses (0 = unlimited for cantrips)
   - `recovery` - "long_rest", "short_rest", or "proficiency_bonus"
   - `casting_ability` - "charisma", "intelligence", or "wisdom"
3. Run `/reloadyaml`
4. Innate spells applied automatically during character creation

### Debugging Character Creation

- Character creation sessions are logged when created/destroyed
- Check `CharacterCreationService.getSession()` for active sessions
- Character sheets saved to `plugins/jkvttplugin/Characters/<UUID>.yml`
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

### Commands
- ✅ `/createcharacter` - Start character creation
- ✅ `/reloadyaml` - Reload all YAML content (Issue #30)
- ✅ `/shortrest` - Recover short rest resources (Issue #41)
- ✅ `/longrest` - Recover long rest resources (Issue #41)

## In Progress / Future Work

### Not Yet Implemented
- ❌ Level-up system (all characters are level 1)
- ❌ Multiclassing
- ❌ Feats
- ❌ Conditional spell application (Genie patron, Lunar Sorcery)
- ❌ Conditional advantages application
- ❌ Combat system (initiative tracking, HP management)
- ❌ Equipment management (equip/unequip in-game)
- ❌ Persistence of player-chosen tool/language proficiencies (Issue #17)

### Planned Enhancements
- Issue #70: Structured features system (usage tracking, action economy, save DCs, damage formulas)
- Spell slot recovery for Warlocks (short rest pact magic)
- Equipment inventory management
- NPC interaction system
- Encounter builder
- Persist current spell slots and equipped armor (Issue #31)

## Notes

- Race and class data is declarative in YAML - add new content without touching Java code
- The `conditional_advantages` and `conditional_bonus_spells` fields are parsed but not yet applied to characters
- Character sheets are read-only in-game (use commands for rest, no HP editing yet)
- The NPC system is separate and allows spawning stat-block entities
- **Known Bug Fix:** Class armor/weapon proficiencies now correctly applied to all characters (previously only racial/subclass proficiencies worked)