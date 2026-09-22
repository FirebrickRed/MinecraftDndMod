package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.ClassResource;
import io.papermc.jkvttplugin.data.model.DndArmor;
import io.papermc.jkvttplugin.util.Util;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CharacterPersistenceLoader {
    private static final Map<UUID, Map<UUID, CharacterSheet>> playerCharacters = new ConcurrentHashMap<>();
    private static final Logger LOGGER = Logger.getLogger("CharacterLoader");
    private static Plugin plugin;
    private static File dataFolder;

    public static void initialize(Plugin pluginInstance) {
        plugin = pluginInstance;
        // Runtime saves live under the plugin data folder: plugins/jkvttplugin/Saved/Characters
        // (NOT a relative path — that resolved to the server working dir and created a stray
        //  DMContent/Saved in the server root, separate from the authored content folder).
        dataFolder = new File(plugin.getDataFolder(), "Saved/Characters");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        loadAllCharacters();
    }

    public static void saveCharacter(CharacterSheet sheet) {
        File characterFile = new File(dataFolder, sheet.getCharacterId().toString() + ".yml");

        try {
            Map<String, Object> data = serializeCharacterSheet(sheet);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);

            try (FileWriter writer = new FileWriter(characterFile)) {
                yaml.dump(data, writer);
            }
        } catch(IOException e) {
            LOGGER.severe("Failed to save character " + sheet.getCharacterName() + ": " + e.getMessage());
        }
    }

    public static void loadAllCharacters() {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.info("No character files found in " + dataFolder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        int loaded = 0;
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);

                CharacterSheet sheet = deserializeCharacterSheet(data);
                if (sheet != null) {
                    sheet.setSavable(true); // now live — future mutations auto-save (#31)
                    playerCharacters.computeIfAbsent(sheet.getPlayerId(), k -> new ConcurrentHashMap<>()).put(sheet.getCharacterId(), sheet);
                    LOGGER.fine("Loaded character: " + sheet.getCharacterName());
                    loaded++;
                }
            } catch (IOException e) {
                LOGGER.severe("Failed to load character file " + file.getName() + ": " + e.getMessage());
            }
        }
        LOGGER.info("Loaded " + loaded + " characters.");
    }

    public static void saveAllCharacters() {
        for (Map.Entry<UUID, Map<UUID, CharacterSheet>> playerEntry : playerCharacters.entrySet()) {
            for (CharacterSheet sheet : playerEntry.getValue().values()) {
                saveCharacter(sheet);
            }
        }
    }

    public static void storeCharacterInMemory(CharacterSheet sheet) {
        sheet.setSavable(true); // now live — future mutations auto-save (#31)
        playerCharacters.computeIfAbsent(sheet.getPlayerId(), k -> new ConcurrentHashMap<>()).put(sheet.getCharacterId(), sheet);
    }

    public static CharacterSheet getCharacter(UUID playerId, UUID characterId) {
        Map<UUID, CharacterSheet> characters = playerCharacters.get(playerId);
        return characters != null ? characters.get(characterId) : null;
    }

    /** Look up a character by id across all players (for DMs viewing someone else's sheet). */
    public static CharacterSheet getCharacterById(UUID characterId) {
        for (Map<UUID, CharacterSheet> characters : playerCharacters.values()) {
            CharacterSheet sheet = characters.get(characterId);
            if (sheet != null) return sheet;
        }
        return null;
    }

    public static List<CharacterSheet> getPlayerCharacters(UUID playerId) {
        Map<UUID, CharacterSheet> characters = playerCharacters.get(playerId);
        return characters != null ? new ArrayList<>(characters.values()) : new ArrayList<>();
    }

    /** Every loaded character across all players (for DM {@code /character list all}). */
    public static List<CharacterSheet> getAllCharacters() {
        List<CharacterSheet> all = new ArrayList<>();
        for (Map<UUID, CharacterSheet> playerChars : playerCharacters.values()) {
            all.addAll(playerChars.values());
        }
        return all;
    }

    /**
     * Find a character by name (case-insensitive search across all players).
     * Returns the first match found.
     */
    public static CharacterSheet findCharacterByName(String characterName) {
        for (Map<UUID, CharacterSheet> playerChars : playerCharacters.values()) {
            for (CharacterSheet sheet : playerChars.values()) {
                if (sheet.getCharacterName().equalsIgnoreCase(characterName)) {
                    return sheet;
                }
            }
        }
        return null;
    }

    /** All characters matching a name (case-insensitive). Used to detect/disambiguate duplicates (#53). */
    public static List<CharacterSheet> findAllCharactersByName(String characterName) {
        List<CharacterSheet> matches = new ArrayList<>();
        for (Map<UUID, CharacterSheet> playerChars : playerCharacters.values()) {
            for (CharacterSheet sheet : playerChars.values()) {
                if (sheet.getCharacterName().equalsIgnoreCase(characterName)) {
                    matches.add(sheet);
                }
            }
        }
        return matches;
    }

    /**
     * Get all character names for tab completion.
     */
    public static List<String> getAllCharacterNames() {
        List<String> names = new ArrayList<>();
        for (Map<UUID, CharacterSheet> playerChars : playerCharacters.values()) {
            for (CharacterSheet sheet : playerChars.values()) {
                names.add(sheet.getCharacterName());
            }
        }
        return names;
    }

    public static void removePlayerCharacters(UUID playerId) {
        // Remove from memory
        Map<UUID, CharacterSheet> characters = playerCharacters.remove(playerId);

        // Remove files from disk
        if (characters != null) {
            for (UUID characterId : characters.keySet()) {
                File characterFile = new File(dataFolder, characterId.toString() + ".yml");
                if (characterFile.exists()) {
                    characterFile.delete();
                }
            }
        }
    }

    public static void removeCharacter(UUID playerId, UUID characterId) {
        // Remove from memory
        Map<UUID, CharacterSheet> characters = playerCharacters.get(playerId);
        if (characters != null) {
            characters.remove(characterId);
            if (characters.isEmpty()) {
                playerCharacters.remove(playerId);
            }
        }

        // Remove file from disk
        File characterFile = new File(dataFolder, characterId.toString() + ".yml");
        if (characterFile.exists()) {
            characterFile.delete();
        }
    }

    public static List<String> validateCharacterData(Map<String, Object> data) {
        List<String> issues = new ArrayList<>();

        String raceName = (String) data.get("raceName");
        String className = (String) data.get("className");
        String backgroundName = (String) data.get("backgroundName");

        // Check if referenced content still exists
        if (raceName != null && RaceLoader.getRace(raceName) == null) {
            issues.add("Race '" + raceName + "' no longer exists");
        }

        if (className != null && ClassLoader.getClass(className) == null) {
            issues.add("Class '" + className + "' no longer exists");
        }

        if (backgroundName != null && BackgroundLoader.getBackground(backgroundName) == null) {
            issues.add("Background '" + backgroundName + "' no longer exists");
        }

        return issues;
    }

    // Package-private so tests can round-trip a sheet without the plugin or files (#14).
    static Map<String, Object> serializeCharacterSheet(CharacterSheet sheet) {
        Map<String, Object> data = new HashMap<>();

        data.put("characterId", sheet.getCharacterId().toString());
        data.put("playerId", sheet.getPlayerId().toString());
        data.put("characterName", sheet.getCharacterName());
        data.put("raceName", sheet.getRace() != null ? sheet.getRace().getId() : null);
        data.put("subraceName", sheet.getSubrace() != null ? sheet.getSubrace().getId() : null);
        data.put("className", sheet.getMainClass() != null ? sheet.getMainClass().getId() : null);
        data.put("subclassName", sheet.getSubclass() != null ? sheet.getSubclass().getId() : null);
        data.put("backgroundName", sheet.getBackground() != null ? sheet.getBackground().getId() : null);
        data.put("currentHealth", sheet.getCurrentHealth());
        data.put("maxHealth", sheet.getMaxHealth());
        data.put("armorClass", sheet.getArmorClass());
        if (sheet.getTempHealth() > 0) data.put("tempHealth", sheet.getTempHealth());
        if (sheet.isRelentlessEnduranceUsed()) data.put("relentlessEnduranceUsed", true);
        // Dying / dead (#101): written only when there's something to say, so a healthy sheet's
        // file doesn't change shape.
        if (sheet.isDead()) data.put("dead", true);
        if (sheet.getDeathSaveSuccesses() > 0 || sheet.getDeathSaveFailures() > 0) {
            Map<String, Object> saves = new LinkedHashMap<>();
            saves.put("successes", sheet.getDeathSaveSuccesses());
            saves.put("failures", sheet.getDeathSaveFailures());
            data.put("deathSaves", saves);
        }

        // Serialize abilities
        Map<String, Integer> abilities = new HashMap<>();
        for (Ability ability : Ability.values()) {
            abilities.put(ability.name(), sheet.getAbility(ability));
        }
        data.put("abilities", abilities);

        // Serialize skill proficiencies
        List<String> skillProficiencies = new ArrayList<>();
        for (var skill : sheet.getSkillProficiencies()) {
            skillProficiencies.add(skill.name());
        }
        data.put("skillProficiencies", skillProficiencies);

        // Tool / language picks from creation (#17). Granted ones re-derive from race/class/background
        // on load; these exist only on the sheet, so without saving them they vanished on restart.
        if (!sheet.getChosenToolProficiencies().isEmpty()) {
            data.put("chosenTools", new ArrayList<>(sheet.getChosenToolProficiencies()));
        }
        if (!sheet.getChosenLanguages().isEmpty()) {
            data.put("chosenLanguages", new ArrayList<>(sheet.getChosenLanguages()));
        }
        if (!sheet.getExpertise().isEmpty()) {
            data.put("expertise", new ArrayList<>(sheet.getExpertise()));
        }
        // Live buffs such as Rage (#212): just the source feature and its live state; the effect
        // itself is rebuilt from the feature's YAML on load.
        if (!sheet.getActiveEffects().isEmpty()) {
            List<Map<String, Object>> effects = new ArrayList<>();
            for (var e : sheet.getActiveEffects()) {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("source", e.getSourceId());
                em.put("roundsRemaining", e.getRoundsRemaining());
                em.put("maintainedThisRound", e.isMaintainedThisRound());
                effects.add(em);
            }
            data.put("activeEffects", effects);
        }

        // Serialize spells and cantrips (save normalized keys, not display names)
        if (sheet.hasSpells()) {
            List<String> spellKeys = new ArrayList<>();
            for (var spell : sheet.getKnownSpells()) {
                spellKeys.add(Util.normalize(spell.getName()));
            }
            data.put("knownSpells", spellKeys);

            List<String> cantripKeys = new ArrayList<>();
            for (var cantrip : sheet.getKnownCantrips()) {
                cantripKeys.add(Util.normalize(cantrip.getName()));
            }
            data.put("knownCantrips", cantripKeys);
        }

        // Class-resource spent state (#31): max/recovery/icon re-derive from the class on load, so
        // save only the current uses (keyed by resource name).
        if (sheet.getClassResources() != null && !sheet.getClassResources().isEmpty()) {
            Map<String, Integer> resourceCurrent = new LinkedHashMap<>();
            for (ClassResource resource : sheet.getClassResources()) {
                resourceCurrent.put(resource.getName(), resource.getCurrent());
            }
            data.put("classResources", resourceCurrent);
        }

        // Current (remaining) spell slots per level (#31): max re-derives on load, so save only what's
        // left. Keyed by spell level (1-9); levels with no slots are omitted.
        Map<Integer, Integer> currentSpellSlots = new LinkedHashMap<>();
        for (int level = 1; level <= 9; level++) {
            if (sheet.getMaxSpellSlots(level) > 0) {
                currentSpellSlots.put(level, sheet.getSpellSlotsRemaining(level));
            }
        }
        if (!currentSpellSlots.isEmpty()) {
            data.put("currentSpellSlots", currentSpellSlots);
        }

        // Inventory items (#31): save item id + quantity; regenerated from the loaders on load.
        if (sheet.getEquipment() != null && !sheet.getEquipment().isEmpty()) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (org.bukkit.inventory.ItemStack stack : sheet.getEquipment()) {
                if (stack == null) continue;
                String itemId = io.papermc.jkvttplugin.util.ItemUtil.getItemId(stack);
                if (itemId == null || itemId.isBlank()) continue; // untagged item — can't be regenerated
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", itemId);
                entry.put("quantity", stack.getAmount());
                items.add(entry);
            }
            if (!items.isEmpty()) data.put("equipment", items);
        }

        // Equipped armor/shield (#31): save ids so AC is correct on load without re-equipping.
        if (sheet.getEquippedArmor() != null) {
            data.put("equippedArmor", sheet.getEquippedArmor().getId());
        }
        if (sheet.getEquippedShield() != null) {
            data.put("equippedShield", sheet.getEquippedShield().getId());
        }

        // CUSTOM choice selections (e.g. draconic_ancestry) so feature actions still resolve their
        // per-choice variant after a restart (breath weapon, #70).
        if (!sheet.getCustomChoices().isEmpty()) {
            data.put("customChoices", new LinkedHashMap<>(sheet.getCustomChoices()));
        }

        return data;
    }

    /** Re-attach saved buffs (#212). A feature that no longer exists is reported and dropped. */
    static void restoreActiveEffects(CharacterSheet sheet, Object raw) {
        if (!(raw instanceof List<?> list)) return;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> em) || !(em.get("source") instanceof String source)) continue;
            int rounds = parseIntOrDefault(em.get("roundsRemaining"), -1);
            boolean maintained = Boolean.TRUE.equals(em.get("maintainedThisRound"));
            if (!sheet.restoreActiveEffect(source, rounds, maintained)) {
                String who = sheet.getCharacterName();
                java.util.logging.Logger.getLogger("CharacterPersistence").warning(who + " had an active '" + source
                        + "' effect, but no feature with that id exists any more — dropped.");
            }
        }
    }

    /** The strings in a YAML list, or an empty list if the key is absent or not a list. */
    private static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) for (Object o : list) if (o instanceof String s) out.add(s);
        return out;
    }

    /** Parse a YAML map key (Integer or String) to an int, or return def if unparseable. */
    private static int parseIntOrDefault(Object key, int def) {
        if (key instanceof Number n) return n.intValue();
        if (key instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    static CharacterSheet deserializeCharacterSheet(Map<String, Object> data) {
        try {
            UUID characterId = UUID.fromString((String) data.get("characterId"));
            UUID playerId = UUID.fromString((String) data.get("playerId"));
            String characterName = (String) data.get("characterName");
            String raceName = (String) data.get("raceName");
            String subraceName = (String) data.get("subraceName");
            String className = (String) data.get("className");
            String subclassName = (String) data.get("subclassName");
            String backgroundName = (String) data.get("backgroundName");

            // Deserialize abilities
            EnumMap<Ability, Integer> abilities = new EnumMap<>(Ability.class);
            Map<String, Integer> abilityData = (Map<String, Integer>) data.get("abilities");
            if (abilityData != null) {
                for (Map.Entry<String, Integer> entry : abilityData.entrySet()) {
                    try {
                        Ability ability = Ability.valueOf(entry.getKey());
                        abilities.put(ability, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("Invalid ability name in character data: " + entry.getKey());
                    }
                }
            }

            // Deserialize skill proficiencies
            Set<Skill> skillProficiencies = new HashSet<>();
            List<String> skillData = (List<String>) data.get("skillProficiencies");
            if (skillData != null) {
                for (String skillName : skillData) {
                    try {
                        Skill skill = Skill.valueOf(skillName);
                        skillProficiencies.add(skill);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("Invalid skill name in character data: " + skillName);
                    }
                }
            }

            // Deserialize spells
            Set<String> knownSpells = new HashSet<>();
            List<String> spellData = (List<String>) data.get("knownSpells");
            if (spellData != null) {
                knownSpells.addAll(spellData);
            }

            Set<String> knownCantrips = new HashSet<>();
            List<String> cantripData = (List<String>) data.get("knownCantrips");
            if (cantripData != null) {
                knownCantrips.addAll(cantripData);
            }

            int currentHealth = (Integer) data.getOrDefault("currentHealth", 1);
            int maxHealth = (Integer) data.getOrDefault("maxHealth", 1);
            int armorClass = (Integer) data.getOrDefault("armorClass", 10);

            CharacterSheet sheet = CharacterSheet.loadFromData(characterId, playerId, characterName, raceName, subraceName, className, subclassName, backgroundName, abilities, skillProficiencies, knownSpells, knownCantrips, currentHealth, maxHealth, armorClass);

            // Restore class-resource spent state (#31). loadFromData initialized resources to full;
            // reapply the saved current uses by name (unknown/renamed resources are ignored).
            if (data.get("classResources") instanceof Map<?, ?> resMap) {
                for (Map.Entry<?, ?> entry : resMap.entrySet()) {
                    if (entry.getKey() instanceof String name && entry.getValue() instanceof Number current) {
                        ClassResource resource = sheet.getResource(name);
                        if (resource != null) resource.setCurrent(current.intValue());
                    }
                }
            }

            // Restore creation-time tool / language picks (#17).
            sheet.restoreChosenProficiencies(stringList(data.get("chosenTools")), stringList(data.get("chosenLanguages")));
            sheet.restoreExpertise(stringList(data.get("expertise")));
            restoreActiveEffects(sheet, data.get("activeEffects"));
            int dsSuccesses = 0, dsFailures = 0;
            if (data.get("deathSaves") instanceof Map<?, ?> saves) {
                dsSuccesses = parseIntOrDefault(saves.get("successes"), 0);
                dsFailures = parseIntOrDefault(saves.get("failures"), 0);
            }
            sheet.restoreRestState(parseIntOrDefault(data.get("tempHealth"), 0),
                    Boolean.TRUE.equals(data.get("relentlessEnduranceUsed")));
            sheet.restoreDeathState(dsSuccesses, dsFailures, Boolean.TRUE.equals(data.get("dead")));

            // Restore CUSTOM choice selections (#70) so feature actions resolve their variant.
            if (data.get("customChoices") instanceof Map<?, ?> ccMap) {
                for (Map.Entry<?, ?> entry : ccMap.entrySet()) {
                    if (entry.getKey() instanceof String id && entry.getValue() instanceof String value) {
                        sheet.setCustomChoice(id, value);
                    }
                }
                sheet.applyLinkedResistances(); // e.g. dragonborn ancestry -> element resistance (#51)
            }

            // Restore current (remaining) spell slots (#31). Keys may load as Integer or String.
            if (data.get("currentSpellSlots") instanceof Map<?, ?> slotMap) {
                for (Map.Entry<?, ?> entry : slotMap.entrySet()) {
                    int level = (entry.getKey() instanceof Number n) ? n.intValue()
                            : parseIntOrDefault(entry.getKey(), -1);
                    if (level >= 1 && entry.getValue() instanceof Number remaining) {
                        sheet.setSpellSlotsRemaining(level, remaining.intValue());
                    }
                }
            }

            // Restore inventory items (#31). loadFromData does not grant starting equipment, so the
            // equipment list is empty here — just re-add the saved items by id + quantity.
            if (data.get("equipment") instanceof List<?> itemList) {
                for (Object element : itemList) {
                    if (element instanceof Map<?, ?> entry && entry.get("id") instanceof String itemId) {
                        int quantity = parseIntOrDefault(entry.get("quantity"), 1);
                        sheet.addEquipmentItem(itemId, quantity);
                    }
                }
            }

            // Re-equip saved armor/shield (#31) so AC is correct on load.
            if (data.get("equippedArmor") instanceof String armorId) {
                DndArmor armor = ArmorLoader.getArmor(armorId);
                if (armor != null) sheet.equipArmor(armor);
            }
            if (data.get("equippedShield") instanceof String shieldId) {
                DndArmor shield = ArmorLoader.getArmor(shieldId);
                if (shield != null) sheet.equipShield(shield);
            }

            return sheet;

            // You could validate the loaded data against your loaders here if needed:
            // - Check if the race/class/background still exists
            // - Log warnings if references are broken
            // - Provide fallbacks or mark character as needing updates

//            return CharacterSheetBuilder.createFromData()
//                    .withCharacterId(characterId)
//                    .withPlayerId(playerId)
//                    .withName(characterName)
//                    .withRace(raceName)
//                    .withSubrace(subraceName)
//                    .withClass(className)
//                    .withBackground(backgroundName)
//                    .withAbilityScores(abilities)
//                    .withSpells(knownSpells, new HashSet<>()) // TODO: separate cantrips from spells in serialization
//                    .build();

        } catch (Exception e) {
            LOGGER.severe("Failed to deserialize character sheet: " + e.getMessage());
            return null;
        }
    }
}
