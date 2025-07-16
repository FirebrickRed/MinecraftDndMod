package io.papermc.jkvttplugin.CustomNPCs;

import io.papermc.jkvttplugin.JkVttPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NpcManager {
    private static final Map<String, NpcData> npcs = new HashMap<>();

    public static void loadNpcs() {
        File file = new File(JkVttPlugin.getInstance().getDataFolder(), "npcs.yml");
        if (!file.exists()) {
            JkVttPlugin.getInstance().saveResource("npcs.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("npcs");
        if (section == null) {
            JkVttPlugin.getInstance().getLogger().warning("No NPCs found in npcs.yml!");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection npcData = section.getConfigurationSection(key);
            if (npcData == null) continue;

            String name = npcData.getString("name", key);
            String type = npcData.getString("type", "Unknown");
            int hitPoints = npcData.getInt("hit_points", 10);
            int armorClass = npcData.getInt("armor_class", 10);
            int speed = npcData.getInt("speed", 30);

            Map<String, Integer> abilities = new HashMap<>();
            ConfigurationSection abilitiesSection = npcData.getConfigurationSection("abilities");
            if (abilitiesSection != null) {
                for (String ability : abilitiesSection.getKeys(false)) {
                    abilities.put(ability, abilitiesSection.getInt(ability));
                }
            }

            List<NpcData.NpcAttack> attacks = new ArrayList<>();
            List<Map<?, ?>> attackList = npcData.getMapList("attacks");
            for (Map<?, ?> attack : attackList) {
                String attackName = (String) attack.get("name");
                int toHit = (int) attack.get("to_hit");
                String damage = (String) attack.get("damage");
                attacks.add(new NpcData.NpcAttack(attackName, toHit, damage));
            }

            List<String> inventory = npcData.getStringList("inventory");

            npcs.put(key.toLowerCase(), new NpcData(name, type, hitPoints, armorClass, speed, abilities, attacks, inventory));
        }

        JkVttPlugin.getInstance().getLogger().info("Loaded " + npcs.size() + " NPCs");
    }

    public static NpcData getNpc(String name) {
        String cleanName = name.toLowerCase().replace("'", "").replace("’", "");

        if (!npcs.containsKey(cleanName)) {
            JkVttPlugin.getInstance().getLogger().warning("NPC Not Found: " + name + " (Searching as: " + cleanName + ")");
            return null;
        }
        return npcs.get(cleanName);
    }
}
