package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.data.loader.*;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

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
        File racesFolder = new File(dmContentFolder, "Races");
        File classFolder = new File(dmContentFolder, "Classes");
        File backgroundsFolder = new File(dmContentFolder, "Backgrounds");
        File spellFolder = new File(dmContentFolder, "Spells");
        File weaponFolder = new File(dmContentFolder, "Weapons");
        File armorFolder = new File(dmContentFolder, "Armor");
        RaceLoader.loadAllRaces(racesFolder);
        ClassLoader.loadAllClasses(classFolder);
        BackgroundLoader.loadAllBackgrounds(backgroundsFolder);
        SpellLoader.loadAllSpells(spellFolder);
        WeaponLoader.loadAllWeapons(weaponFolder);
        ArmorLoader.loadAllArmors(armorFolder);
    }

//    private static Map<String, DndRace> allRaces = Map.of();
//
//    public static void loadAll(File pluginFolder, Logger logger) {
////        allRaces = RaceLoader.loadAllRaces(pluginFolder, "races");
//        File racesFolder = new File(pluginFolder, "races");
//        if (!racesFolder.exists()) {
//            logger.warning("Races folder not found. Creating a new one at: " + racesFolder.getPath());
//            racesFolder.mkdirs();
//        }
//
//        RaceLoader.loadAllRaces(racesFolder);
//        allRaces = RaceLoader.getAllRaces().
//    }
//
//    public static Map<String, DndRace> getAllRaces() {
//        return Collections.unmodifiableMap(allRaces);
//    }
//
//    public static DndRace getRace(String name) {
//        return allRaces.get(name.toLowerCase());
//    }
}
