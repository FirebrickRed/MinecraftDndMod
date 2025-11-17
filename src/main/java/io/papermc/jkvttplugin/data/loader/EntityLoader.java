package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.Util;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;

/**
 * Loads entity templates from DMContent/Entities/ with recursive directory scanning.
 *
 * Key Features:
 * - Recursively scans ALL subdirectories (DMs can organize however they want)
 * - Supports single-entity files (one entity per YAML)
 * - Supports multi-entity files (multiple entities in one YAML)
 * - Parses abilities from string keys to Ability enum
 */
public class EntityLoader {
    private static final Map<String, DndEntity> loadedEntities = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("EntityLoader");

    /**
     * Recursively loads all entities from the given folder and subfolders.
     *
     * @param folder The root folder (typically DMContent/Entities/)
     */
    public static void loadAllEntities(File folder) {
        if (!folder.exists() || !folder.isDirectory()) {
            LOGGER.warning("Entity folder does not exist: " + folder.getPath());
            return;
        }

        LOGGER.info("Loading entities from: " + folder.getPath());
        int entityCount = recursiveLoad(folder);
        LOGGER.info("Loaded " + entityCount + " entities from " + folder.getPath());
    }

    /**
     * Recursively scans directories and loads YAML files.
     * This allows DMs to organize entities however they want:
     * - DMContent/Entities/town/guards.yml
     * - DMContent/Entities/wilderness/beasts/wolves.yml
     * - DMContent/Entities/my_campaign/chapter1/boss.yml
     *
     * @param folder Current folder being scanned
     * @return Number of entities loaded from this folder and subfolders
     */
    private static int recursiveLoad(File folder) {
        int count = 0;
        File[] files = folder.listFiles();

        if (files == null) {
            return 0;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Recursively scan subdirectories
                count += recursiveLoad(file);
            } else if (file.getName().endsWith(".yml")) {
                // Load entities from YAML file
                count += loadFromFile(file);
            }
        }

        return count;
    }

    /**
     * Loads entities from a single YAML file.
     * Handles both formats:
     * 1. Single entity (root level has entity fields)
     * 2. Multiple entities (root level is a map of entity_id -> entity_data)
     *
     * @param file The YAML file to load
     * @return Number of entities loaded
     */
    private static int loadFromFile(File file) {
        int count = 0;
        Yaml yaml = new Yaml();

        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> data = yaml.load(reader);

            if (data == null || data.isEmpty()) {
                LOGGER.warning("Empty or invalid YAML file: " + file.getName());
                return 0;
            }

            // Detect format: Does this look like a single entity or multiple entities?
            if (isSingleEntityFile(data)) {
                // Single entity file (has "id" field at root level)
                DndEntity entity = parseEntity(data);
                if (entity != null && entity.getId() != null) {
                    loadedEntities.put(Util.normalize(entity.getId()), entity);
                    LOGGER.info("  Loaded entity: " + entity.getName() + " [" + entity.getId() + "]");
                    count++;
                }
            } else {
                // Multi-entity file (root level is map of id -> entity_data)
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String entityId = entry.getKey();

                    if (entry.getValue() instanceof Map<?, ?> entityData) {
                        DndEntity entity = parseEntity(entityId, entityData);
                        if (entity != null) {
                            loadedEntities.put(Util.normalize(entityId), entity);
                            LOGGER.info("  Loaded entity: " + entity.getName() + " [" + entity.getId() + "]");
                            count++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to load entities from " + file.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        return count;
    }

    /**
     * Determines if a YAML file represents a single entity.
     * Single entity files have "id" field at the root level.
     */
    private static boolean isSingleEntityFile(Map<String, Object> data) {
        return data.containsKey("id");
    }

    /**
     * Parses a single entity from YAML data (for single-entity files).
     */
    private static DndEntity parseEntity(Map<String, Object> data) {
        return parseEntity(null, data);
    }

    /**
     * Parses a single entity from YAML data.
     *
     * @param idOverride Optional ID override (for multi-entity files where ID is the map key)
     * @param data The entity data from YAML
     * @return Parsed DndEntity or null if parsing failed
     */
    @SuppressWarnings("unchecked")
    private static DndEntity parseEntity(String idOverride, Map<?, ?> data) {
        try {
            DndEntity entity = new DndEntity();

            // ID: Use override if provided (multi-entity file), otherwise from data
            String id = idOverride != null ? idOverride : (String) data.get("id");
            entity.setId(id);

            // Basic identification
            entity.setName((String) data.get("name"));

            Object randomNamesObj = data.get("random_names");
            if (randomNamesObj instanceof List<?> namesList) {
                List<String> randomNames = new ArrayList<>();
                for (Object name : namesList) {
                    if (name instanceof String) {
                        randomNames.add((String) name);
                    }
                }
                entity.setRandomNames(randomNames);
            }

            // D&D stats
            entity.setCreatureType((String) data.get("creature_type"));
            entity.setSubtype((String) data.get("subtype"));
            entity.setSize((String) data.get("size"));

            // HP system: hitDice > hitPoints > default
            if (data.get("hit_dice") instanceof String hitDice) {
                entity.setHitDice(hitDice);
            } else if (data.get("hit_points") instanceof Integer hitPoints) {
                entity.setHitPoints(hitPoints);
            }

            // AC and speed
            if (data.get("armor_class") instanceof Integer ac) {
                entity.setArmorClass(ac);
            }
            if (data.get("speed") instanceof Integer speed) {
                entity.setSpeed(speed);
            }

            // Abilities: Parse from string keys to Ability enum
            Object abilitiesObj = data.get("abilities");
            if (abilitiesObj instanceof Map<?, ?> abilitiesMap) {
                Map<Ability, Integer> abilities = new HashMap<>();
                for (Map.Entry<?, ?> entry : abilitiesMap.entrySet()) {
                    String abilityName = (String) entry.getKey();
                    Integer abilityScore = (Integer) entry.getValue();

                    Ability ability = Ability.fromString(abilityName);
                    if (ability != null && abilityScore != null) {
                        abilities.put(ability, abilityScore);
                    }
                }
                entity.setAbilities(abilities);
            }

            // Attacks
            Object attacksObj = data.get("attacks");
            if (attacksObj instanceof List<?> attacksList) {
                List<DndAttack> attacks = new ArrayList<>();
                for (Object attackObj : attacksList) {
                    if (attackObj instanceof Map<?, ?> attackData) {
                        DndAttack attack = parseAttack(attackData);
                        if (attack != null) {
                            attacks.add(attack);
                        }
                    }
                }
                entity.setAttacks(attacks);
            }

            // Inventory
            Object inventoryObj = data.get("inventory");
            if (inventoryObj instanceof List<?> inventoryList) {
                List<String> inventory = new ArrayList<>();
                for (Object item : inventoryList) {
                    if (item instanceof String) {
                        inventory.add((String) item);
                    }
                }
                entity.setInventory(inventory);
            }

            // Visual and metadata
            entity.setModel((String) data.get("model"));
            entity.setDmNotes((String) data.get("dm_notes"));

            return entity;

        } catch (Exception e) {
            LOGGER.severe("Failed to parse entity: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parses attack data from YAML.
     */
    private static DndAttack parseAttack(Map<?, ?> data) {
        DndAttack attack = new DndAttack();

        attack.setName((String) data.get("name"));

        if (data.get("to_hit") instanceof Integer toHit) {
            attack.setToHit(toHit);
        }

        attack.setReach((String) data.get("reach"));
        attack.setDamage((String) data.get("damage"));
        attack.setDamageType((String) data.get("damage_type"));

        return attack;
    }

    // ==================== PUBLIC API ====================

    /**
     * Get an entity template by ID.
     *
     * @param id The entity ID (case-insensitive, normalized)
     * @return The entity template or null if not found
     */
    public static DndEntity getEntity(String id) {
        if (id == null) return null;
        return loadedEntities.get(Util.normalize(id));
    }

    /**
     * Get all loaded entities.
     */
    public static Collection<DndEntity> getAllEntities() {
        return Collections.unmodifiableCollection(loadedEntities.values());
    }

    /**
     * Get entities by creature type (humanoid, beast, dragon, etc.)
     */
    public static List<DndEntity> getEntitiesByType(String creatureType) {
        return loadedEntities.values().stream()
                .filter(entity -> creatureType.equalsIgnoreCase(entity.getCreatureType()))
                .toList();
    }

    /**
     * Clears all loaded entities. Called before reloading data.
     */
    public static void clear() {
        loadedEntities.clear();
        LOGGER.info("Cleared all loaded entities");
    }
}