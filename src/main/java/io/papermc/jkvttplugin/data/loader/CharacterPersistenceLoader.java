package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.character.CharacterSheet;
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
        dataFolder = new File(plugin.getDataFolder(), "Characters");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        loadAllCharacters();
    }

    // ToDo: fix saving character to yaml file
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

    // ToDo: actually load saved characters somewhere smh
    public static void loadAllCharacters() {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.info("No character files found in " + dataFolder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);

                CharacterSheet sheet = deserializeCharacterSheet(data);
                if (sheet != null) {
                    playerCharacters.computeIfAbsent(sheet.getPlayerId(), k -> new ConcurrentHashMap<>()).put(sheet.getCharacterId(), sheet);
                    LOGGER.info("Loaded character: " + sheet.getCharacterName());
                }
            } catch (IOException e) {
                LOGGER.severe("Failed to load character file " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public static void saveAllCharacters() {
        for (Map.Entry<UUID, Map<UUID, CharacterSheet>> playerEntry : playerCharacters.entrySet()) {
            for (CharacterSheet sheet : playerEntry.getValue().values()) {
                saveCharacter(sheet);
            }
        }
    }

    public static void storeCharacterInMemory(CharacterSheet sheet) {
        playerCharacters.computeIfAbsent(sheet.getPlayerId(), k -> new ConcurrentHashMap<>()).put(sheet.getCharacterId(), sheet);
    }

    public static CharacterSheet getCharacter(UUID playerId, UUID characterId) {
        Map<UUID, CharacterSheet> characters = playerCharacters.get(playerId);
        return characters != null ? characters.get(characterId) : null;
    }

    public static List<CharacterSheet> getPlayerCharacters(UUID playerId) {
        Map<UUID, CharacterSheet> characters = playerCharacters.get(playerId);
        return characters != null ? new ArrayList<>(characters.values()) : new ArrayList<>();
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

    private static Map<String, Object> serializeCharacterSheet(CharacterSheet sheet) {
        Map<String, Object> data = new HashMap<>();

        data.put("characterId", sheet.getCharacterId().toString());
        data.put("playerId", sheet.getPlayerId().toString());
        data.put("characterName", sheet.getCharacterName());
        data.put("raceName", sheet.getRace() != null ? sheet.getRace().getId() : null);
        data.put("subraceName", sheet.getSubrace() != null ? sheet.getSubrace().getId() : null);
        data.put("className", sheet.getMainClass() != null ? sheet.getMainClass().getId() : null);
        data.put("backgroundName", sheet.getBackground() != null ? sheet.getBackground().getId() : null);
        data.put("currentHealth", sheet.getCurrentHealth());
        data.put("maxHealth", sheet.getMaxHealth());
        data.put("armorClass", sheet.getArmorClass());

        // Serialize abilities
        Map<String, Integer> abilities = new HashMap<>();
        for (Ability ability : Ability.values()) {
            abilities.put(ability.name(), sheet.getAbility(ability));
        }
        data.put("abilities", abilities);

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

        // ToDo: Save spell slot consumption (Issue #31)
        // Currently spell slots reset to full on every reload
        // Need to add: data.put("currentSpellSlots", sheet.getCurrentSpellSlots());

        // ToDo: Save equipped armor/shield (Issue #31)
        // Currently equipped armor is lost on reload, causing wrong AC until re-equipped
        // Need to add: data.put("equippedArmor", sheet.getEquippedArmor().getId());

        return data;
    }

    private static CharacterSheet deserializeCharacterSheet(Map<String, Object> data) {
        try {
            UUID characterId = UUID.fromString((String) data.get("characterId"));
            UUID playerId = UUID.fromString((String) data.get("playerId"));
            String characterName = (String) data.get("characterName");
            String raceName = (String) data.get("raceName");
            String subraceName = (String) data.get("subraceName");
            String className = (String) data.get("className");
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
                        plugin.getLogger().warning("Invalid ability name in character data: " + entry.getKey());
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

            return CharacterSheet.loadFromData(characterId, playerId, characterName, raceName, subraceName, className, backgroundName, abilities, knownSpells, knownCantrips, currentHealth, maxHealth, armorClass);

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
            plugin.getLogger().severe("Failed to deserialize character sheet: " + e.getMessage());
            return null;
        }
    }
}
