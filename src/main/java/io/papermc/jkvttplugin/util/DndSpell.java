package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.JkVttPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DndSpell {
    private static final Map<String, DndSpell> spells = new HashMap<>();
    // need to add ritual and concentration to the spells
    private final String name;
    private final int level;
    private final String school;
    private final String castingTime;
    private final String range;
    private final String duration;
    private final List<String> components;
    private final String description;
    private final Material material;
    private final List<String> classes;

    public DndSpell(String name, int level, String school, String castingTime, String range, String duration, List<String> components, String description, Material material, List<String> classes) {
        this.name = name;
        this.level = level;
        this.school = school;
        this.castingTime = castingTime;
        this.range = range;
        this.duration = duration;
        this.components = components;
        this.description = description;
        this.material = material;
        this.classes = classes;
    }

    public String getName() { return name.toLowerCase(); }
    public int getLevel() { return level; }
    public String getSchool() { return school; }
    public static List<DndSpell> getSpellsByClass(String className) {
        List<DndSpell> filteredSpells = new ArrayList<>();

//        System.out.println("In DndSpell GetSPellsByClass: " + className + " " + spells);
        for (DndSpell spell : spells.values()) {
            if (spell.classes.contains(className.toLowerCase())) {
                filteredSpells.add(spell);
            }
        }

        return filteredSpells;
    }

    public Component getHoverString() {
        return Component.text(getName() + "\n" +
                "Casting Time: " + castingTime + "\n" +
                "Range: " + range + "\n" +
                "Components: " + components + "\n" +
                "Duration: " + duration + "\n" +
                "Description: " + description);
    }

    public static void loadSpells() {
        File file = new File(JkVttPlugin.getInstance().getDataFolder(), "spells.yml");

        if (!file.exists()) {
            JkVttPlugin.getInstance().saveResource("spells.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("spells");
        if (section == null) {
            JkVttPlugin.getInstance().getLogger().warning("No Spells found in spells.yml");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection spellData = section.getConfigurationSection(key);
            if (spellData == null) continue;

            String name = spellData.getString("name", key);
            int level = spellData.getInt("level", 0);
            String school = spellData.getString("school", "");
            String castingTime = spellData.getString("casting_time");
            String range = spellData.getString("range");
            String duration = spellData.getString("duration");
            List<String> components = spellData.getStringList("components");
            String description = spellData.getString("description");
            Material material = Material.matchMaterial(spellData.getString("material", "BONEMEAL"));
//            String itemModelName = spellData.getString("item_model_name");
            List<String> classes = spellData.getStringList("classes");

            if (material == null) {
                JkVttPlugin.getInstance().getLogger().warning("Invalid material for spell: " + key);
                continue;
            }

            spells.put(key.toLowerCase(), new DndSpell(name, level, school, castingTime, range, duration, components, description, material, classes));
        }

        JkVttPlugin.getInstance().getLogger().info("Loaded " + spells.size() + " D&D spells.");
    }
}
