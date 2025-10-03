# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Paper/Spigot Minecraft plugin that transforms Minecraft into a virtual tabletop for D&D 5th Edition. Players can create D&D characters following 5e rules, manage equipment, cast spells, and engage in combat mechanics—all within Minecraft.

**Target Platform:** PaperMC 1.21.3 (Java 21)

## Build Commands

```bash
# Build the plugin (Gradle wrapper may need to be generated first)
./gradlew build

# Clean and build
./gradlew clean build

# The compiled JAR will be in: build/libs/
```

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

**Equipment System:**
- Weapons/armor/items have custom NBT data for identification
- `WeaponListener` handles weapon interactions (showing attack modifiers)
- `CharacterSheet` auto-equips armor/shields during character creation if proficient
- Equipment choices during character creation handled via `PendingChoice` system

### Key Design Patterns

**Player Choice System:**
- `PlayersChoice` + `PendingChoice` → generic system for handling player choices during character creation
- Used for equipment selection, spell selection, and racial/class feature choices
- `ChoiceUtil` provides helper methods for processing choices

**ItemStack Creation:**
- `ItemUtil` provides centralized item creation with NBT tags
- D&D objects (`DndWeapon`, `DndArmor`, `DndItem`, `DndSpell`) can create their own `ItemStack` representations
- NBT tags used to identify items (e.g., `weapon_id`, `armor_id`, `spell_name`)

**Session Management:**
- Active character creation sessions stored in `CharacterCreationService`
- Each menu navigation preserves the session UUID
- Sessions cleared upon character sheet finalization

## Common Patterns

### Adding a New D&D Class

1. Create YAML in `DMContent/Classes/<classname>.yml` (see `warlock.yml` for spellcaster template)
2. Define: `hit_die`, `proficiency`, `saving_throws`, `armor_proficiencies`, `weapon_proficiencies`, `spellcasting` (if applicable), `starting_equipment`, `features_by_level`
3. Run `/reloadyaml` or restart server
4. Class will appear in `ClassSelectionMenu` automatically

### Adding a New Spell

1. Create or edit YAML in `DMContent/Spells/*.yml`
2. Define: `name`, `level`, `school`, `casting_time`, `range`, `components`, `duration`, `description`, `classes` (spell list)
3. Run `/reloadyaml`
4. Spell will appear in `SpellSelectionMenu` for classes that have it in their spell list

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

## Notes

- Not all D&D races/backgrounds are implemented yet (WIP)
- Level-up system is not yet implemented (all characters are level 1)
- Character sheet UI displays stats but is read-only
- Racial ability score bonuses are not yet applied (TODO in `CharacterSheet.java:68`)
- The NPC system is separate and allows spawning stat-block entities
