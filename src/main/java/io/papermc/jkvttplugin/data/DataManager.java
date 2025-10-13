package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.data.loader.*;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

// ToDO: Look up Records to see about Intellij's suggestion of turning this into a record class
public class DataManager {
    private final File dmContentFolder;

    public DataManager(JavaPlugin plugin) {
        this.dmContentFolder = new File(plugin.getDataFolder(), "DMContent");
        if (!dmContentFolder.exists()) {
            dmContentFolder.mkdirs();
            // ToDo: Optionally copy defaults here from internal resources
        }
    }

    public void loadAllData() {
        // Clear existing data before reloading (for /reloadyaml command)
        clearAllData();

        File racesFolder = new File(dmContentFolder, "Races");
        File classFolder = new File(dmContentFolder, "Classes");
        File backgroundsFolder = new File(dmContentFolder, "Backgrounds");
        File spellFolder = new File(dmContentFolder, "Spells");
        File weaponFolder = new File(dmContentFolder, "Weapons");
        File armorFolder = new File(dmContentFolder, "Armor");
        File itemFolder = new File(dmContentFolder, "Items");
        RaceLoader.loadAllRaces(racesFolder);
        ClassLoader.loadAllClasses(classFolder);
        BackgroundLoader.loadAllBackgrounds(backgroundsFolder);
        SpellLoader.loadAllSpells(spellFolder);
        WeaponLoader.loadAllWeapons(weaponFolder);
        ArmorLoader.loadAllArmors(armorFolder);
        ItemLoader.loadAllItems(itemFolder);
    }

    /**
     * Clears all loaded data from static registries.
     * Called before reloading to ensure deleted content is removed.
     */
    private void clearAllData() {
        RaceLoader.clear();
        ClassLoader.clear();
        BackgroundLoader.clear();
        SpellLoader.clear();
        WeaponLoader.clear();
        ArmorLoader.clear();
        ItemLoader.clear();
    }
}
