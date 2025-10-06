package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.model.DndArmor;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;

import static io.papermc.jkvttplugin.util.Util.normalize;

public class ArmorLoader {
    private static final Map<String, DndArmor> loadedArmors = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("ArmorLoader");

    public static void loadAllArmors(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.warning("No armor files found in " + folder.getPath());
            return;
        }

        Yaml yaml = new Yaml();

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);

                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String armorId = entry.getKey();

                    if (entry.getValue() instanceof Map<?, ?> armorData) {
                        DndArmor armor = parseArmor(armorId, armorData);
                        loadedArmors.put(normalize(armorId), armor);
                        LOGGER.info("Loaded armor: " + armor.getName());
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to load armors from " + file.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static DndArmor parseArmor(String id, Map<?, ?> data) {
        DndArmor armor = new DndArmor();

        armor.setId(id);
        // ToDo: Figure out why get or default is being stupid
//        armor.setName((String) data.getOrDefault("name", id.replace('_', ' ')));
//        armor.setCategory((String) data.getOrDefault("category", "light"));
        armor.setName((String) data.get("name"));
        armor.setCategory((String) data.get("category"));
        armor.setWeight((String) data.get("weight"));
        armor.setCost((String) data.get("cost"));
        armor.setDescription((String) data.get("description"));
        armor.setIcon((String) data.get("icon"));

        // Parse material
        Object materialObj = data.get("material");
        if (materialObj instanceof String materialStr) {
            try {
                armor.setMaterial(org.bukkit.Material.valueOf(materialStr));
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Invalid material '" + materialStr + "' for armor " + id + ", defaulting to LEATHER_CHESTPLATE");
            }
        }

        Object acObj = data.get("ac");
        if (acObj instanceof Integer) {
            armor.setBaseAC((Integer) acObj);
            armor.setAddsDexModifier(!("heavy".equalsIgnoreCase(armor.getCategory()) || "shield".equalsIgnoreCase(armor.getCategory())));

            if ("medium".equalsIgnoreCase(armor.getCategory())) {
                armor.setMaxDexModifier(2);
            }
        } else if (acObj instanceof String acStr) {
            parseACString(armor, acStr);
        } else if (acObj instanceof Map<?, ?> acMap) {
            parseACMap(armor, acMap);
        }

        Object strReq = data.get("strength_requirement");
        if (strReq instanceof Integer) {
            armor.setStrengthRequirement((Integer) strReq);
        }

        Object stealthDisadv = data.get("stealth_disadvantage");
        if (stealthDisadv instanceof Boolean) {
            armor.setStealthDisadvantage((Boolean) stealthDisadv);
        }

        return armor;
    }

    private static void parseACString(DndArmor armor, String acStr) {
        try {
            String[] parts = acStr.toLowerCase().split("\\+");
            if (parts.length >= 1) {
                armor.setBaseAC(Integer.parseInt(parts[0].trim()));

                if (parts.length > 1 && parts[1].contains("dex")) {
                    armor.setAddsDexModifier(true);

                    if (parts[1].contains("max")) {
                        String maxPart = parts[1].substring(parts[1].indexOf("max"));
                        String[] maxParts = maxPart.split("\\s+");
                        for (String part : maxParts) {
                            try {
                                int maxDex = Integer.parseInt(part.replaceAll("[^0-9]", ""));
                                armor.setMaxDexModifier(maxDex);
                                break;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid AC format for armor " + armor.getId() + ": " + acStr);
            armor.setBaseAC(10);
        }
    }

    private static void parseACMap(DndArmor armor, Map<?, ?> acMap) {
        Object base = acMap.get("base");
        if (base instanceof Integer) {
            armor.setBaseAC((Integer) base);
        }

        Object addsDex = acMap.get("adds_dex");
        if (addsDex instanceof Boolean) {
            armor.setAddsDexModifier((Boolean) addsDex);
        }

        Object maxDex = acMap.get("max_dex");
        if (maxDex instanceof Integer) {
            armor.setMaxDexModifier((Integer) maxDex);
        }
    }

    public static DndArmor getArmor(String id) {
        if (id == null) return null;
        return loadedArmors.get(normalize(id));
    }

    public static Collection<DndArmor> getAllArmors() {
        return Collections.unmodifiableCollection(loadedArmors.values());
    }

    public static List<DndArmor> getArmorsByCategory(String category) {
        return loadedArmors.values().stream()
                .filter(armor -> category.equalsIgnoreCase(armor.getCategory()))
                .toList();
    }

    public static List<DndArmor> getLightArmors() {
        return getArmorsByCategory("light");
    }

    public static List<DndArmor> getMediumArmors() {
        return getArmorsByCategory("medium");
    }

    public static List<DndArmor> getHeavyArmors() {
        return getArmorsByCategory("heavy");
    }

    public static List<DndArmor> getShields() {
        return getArmorsByCategory("shield");
    }

    public static void clearAll() {
        loadedArmors.clear();
    }
}
