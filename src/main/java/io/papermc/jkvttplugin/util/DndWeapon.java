package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.JkVttPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.util.*;
import java.util.List;

public class DndWeapon {
    private final String name;
    private final String damageDice;
    private final String damageType;
    private final String type;
    private final List<String> properties;
    private final Material material;
    private final String itemModelName;

    private static final Map<String, DndWeapon> weapons = new HashMap<>();

    public DndWeapon(String name, String damageDice, String damageType, String type, List<String> properties, Material material, String itemModelName) {
        this.name = name;
        this.damageDice = damageDice;
        this.damageType = damageType;
        this.type = type;
        this.properties = properties;
        this.material = material;
        this.itemModelName = itemModelName;
    }

    public String getName() { return name.toLowerCase(); }
    public String getDamageDice() { return damageDice; }
    public String getDamageType() { return damageType; }
    public String getType() { return type; }
    public List<String> getProperties() { return properties; }
    public Material getMaterial() { return material; }
    public String getItemModelName() { return itemModelName; }

    public static void loadWeapons() {
        File file = new File(JkVttPlugin.getInstance().getDataFolder(), "weapons.yml");

        if (!file.exists()) {
            JkVttPlugin.getInstance().saveResource("weapons.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("weapons");
        if (section == null) {
            JkVttPlugin.getInstance().getLogger().warning("No weapons found in weapons.yml");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection weaponData = section.getConfigurationSection(key);
            if (weaponData == null) continue;

            String name = weaponData.getString("name", key);
            String damageDice = weaponData.getString("damage_dice", "1d4");
            String damageType = weaponData.getString("damage_type", "Slashing");
            String type = weaponData.getString("type", "Simple");
            List<String> properties = weaponData.getStringList("properties");
            Material material = Material.matchMaterial(weaponData.getString("material", "IRON_SWORD"));
            String itemModelName = weaponData.getString("item_model_name", "katana");

            if (material == null) {
                JkVttPlugin.getInstance().getLogger().warning("Invalid material for weapon: " + key);
                continue;
            }

            weapons.put(key.toLowerCase(), new DndWeapon(name, damageDice, damageType, type, properties, material, itemModelName));
        }

        JkVttPlugin.getInstance().getLogger().info("Loaded " + weapons.size() + " D&D weapons.");
    }

    public static DndWeapon getWeapon(String name) {
        DndWeapon weapon = weapons.get(name.toLowerCase());

//        if (weapon == null) {
//            System.out.println("WARNING: weapon '" + name.toLowerCase() + "' not found in weapons.yml!");
//            System.out.println("Available weapons: " + weapons.keySet());
//        }
        return weapon;
    }

    public static Set<String> getAllWeaponNames() {
        return weapons.keySet();
    }

    public ItemStack toItemStack() {
        List<Component> lore = Arrays.asList(
                Component.text("Damage: " + getDamageDice() + " " + getDamageType()),
                Component.text("Type: " + getType()),
                Component.text("Properties: " + String.join(", ", getProperties()))
        );
        ItemStack item = Util.createItem(Component.text(getName()), lore, getItemModelName(), 0);

        return item;
    }
}
