package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.data.loader.*;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.data.model.enums.ToolRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

// ToDO: Look up Records to see about Intellij's suggestion of turning this into a record class
public class DataManager {
    private final File dmContentFolder;

    public DataManager(JavaPlugin plugin) {
        this(new File(plugin.getDataFolder(), "DMContent"));
    }

    /** Load from any content folder — the plugin's own, or the repo's DMContent in tests. */
    public DataManager(File dmContentFolder) {
        this.dmContentFolder = dmContentFolder;
        if (!dmContentFolder.exists()) {
            dmContentFolder.mkdirs();
            // ToDo: Optionally copy defaults here from internal resources
        }
    }

    /** Loads everything in dependency order; returns the content check's warnings (empty = clean). */
    public List<String> loadAllData() {
        // Clear existing data before reloading (for /reloadyaml command)
        clearAllData();

        File spellFolder = new File(dmContentFolder, "Spells"); // No Dependencies
        File weaponFolder = new File(dmContentFolder, "Weapons");  // No Dependencies
        File armorFolder = new File(dmContentFolder, "Armor"); // No Dependencies
        File itemFolder = new File(dmContentFolder, "Items"); // No Dependencies
        File racesFolder = new File(dmContentFolder, "Races"); // References Spells for innate Casting
        File classFolder = new File(dmContentFolder, "Classes"); // References Spells for Spell lists
        File backgroundsFolder = new File(dmContentFolder, "Backgrounds"); // references items/tools
        File entitiesFolder = new File(dmContentFolder, "Entities"); // References Weapons/Armor/Items
        File conditionsFolder = new File(dmContentFolder, "Conditions"); // No Dependencies
        // Languages first: race/class/background loaders validate against the registry.
        LanguageRegistry.load(new File(dmContentFolder, "Languages.yml"));
        SpellLoader.loadAllSpells(spellFolder);
        ConditionLoader.loadAllConditions(conditionsFolder);
        DamageTypeLoader.loadAll(new File(dmContentFolder, "DamageTypes.yml"));
        WeaponLoader.loadAllWeapons(weaponFolder);
        ArmorLoader.loadAllArmors(armorFolder);
        ItemLoader.loadAllItems(itemFolder);
        // Tools are items (artisan_tool / musical_instrument / gaming_set / tool tags); register them
        // before races/classes/backgrounds expand their tool choices.
        ToolRegistry.registerItems(ItemLoader.getAllItems());
        RaceLoader.loadAllRaces(racesFolder);
        ClassLoader.loadAllClasses(classFolder);
        BackgroundLoader.loadAllBackgrounds(backgroundsFolder);
        EntityLoader.loadAllEntities(entitiesFolder);

        // Cross-content sanity pass: typos that would otherwise fail silently (unknown item ids in
        // shops/kits/loot, unwearable armor, focus types no class uses…). Warnings only.
        return ContentValidator.validateAll();
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
        ToolRegistry.reset();
        EntityLoader.clear();
        ConditionLoader.clear();
        DamageTypeLoader.clear();
    }
}
