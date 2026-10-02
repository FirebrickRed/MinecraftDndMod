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

    /** The loaders whose warnings are content problems (#243): captured while loading, see {@link #loadAllData}. */
    private static final List<String> LOADER_LOGGERS = List.of("ArmorLoader", "BackgroundLoader", "ClassLoader",
            "ConditionLoader", "DamageTypeLoader", "EntityLoader", "ItemLoader", "RaceLoader", "SoundLoader",
            "SpellLoader", "WeaponLoader");

    /**
     * Every YAML file under DMContent that won't parse at all, as "Spells/spells1.yml line 12: ..." (#243).
     * A file like that loads NOTHING, so its spells, items or creatures would vanish on a reload:
     * {@code /dm reload} checks this first and refuses, keeping what's loaded.
     */
    public List<String> syntaxErrors() {
        List<String> out = new java.util.ArrayList<>();
        if (!dmContentFolder.exists()) return out;
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(dmContentFolder.toPath())) {
            for (java.nio.file.Path p : files.filter(x -> { String n = x.toString().toLowerCase(); return n.endsWith(".yml") || n.endsWith(".yaml"); }).sorted().toList()) {
                String name = dmContentFolder.toPath().relativize(p).toString().replace('\\', '/');
                try (java.io.Reader r = java.nio.file.Files.newBufferedReader(p)) {
                    for (Object ignored : new org.yaml.snakeyaml.Yaml().loadAll(r)) { /* parse every document */ }
                } catch (org.yaml.snakeyaml.error.MarkedYAMLException e) {
                    var mark = e.getProblemMark();
                    out.add(name + (mark != null ? " line " + (mark.getLine() + 1) : "") + ": " + e.getProblem());
                } catch (Exception e) {
                    out.add(name + ": " + e.getMessage());
                }
            }
        } catch (java.io.IOException e) {
            out.add("DMContent couldn't be read: " + e.getMessage());
        }
        return out;
    }

    /**
     * Loads everything in dependency order; returns the content check's warnings (empty = clean).
     * Anything a loader logs as a warning while loading counts as one too (#243): about forty loader
     * messages (a duplicate id, a file that failed) used to reach only the console, so neither
     * /dm reload nor the build's clean-load test knew about them.
     */
    public List<String> loadAllData() {
        java.util.logging.Handler capture = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) {
                if (r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                    ContentValidator.loadProblem("[" + r.getLoggerName() + "] " + r.getMessage());
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        for (String name : LOADER_LOGGERS) java.util.logging.Logger.getLogger(name).addHandler(capture);
        try {
            return loadEverything();
        } finally {
            for (String name : LOADER_LOGGERS) java.util.logging.Logger.getLogger(name).removeHandler(capture);
        }
    }

    private List<String> loadEverything() {
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
        DamageTypeLoader.loadAll(new File(dmContentFolder, "DamageTypes.yml")); // before spells: their visual: blocks check against it (#230)
        SpellLoader.loadAllSpells(spellFolder);
        ConditionLoader.loadAllConditions(conditionsFolder);
        SoundLoader.load(new File(dmContentFolder, "Sounds.yml")); // the table's sounds and music (#16)
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
        SoundLoader.clear();
    }
}
