package io.papermc.jkvttplugin.dm;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * DM-annotated interactive objects (#185) — a lightweight layer over ANY block (chest, door, false
 * wall, …). A block can be marked locked and/or hidden and carry a description; a player right-click
 * then routes through the DM instead of doing the vanilla thing. State persists per world+coords.
 *
 * <p>This is the MVP: locked + hidden + description. Traps, loot tables, and passive-Perception reveal
 * are later slices of the epic.
 */
public final class InteractiveObjectManager {

    private InteractiveObjectManager() {}

    /** One annotated block. Mutable so the DM can toggle flags in place. */
    public static final class Obj {
        public boolean locked;
        public boolean hidden;   // players don't get the interaction until the DM reveals it
        public String description = "";
    }

    private static final Map<String, Obj> objects = new HashMap<>(); // "world:x:y:z" -> obj
    private static final Logger LOGGER = Logger.getLogger("InteractiveObjects");
    private static Plugin plugin;
    private static File file;

    public static void init(Plugin p) {
        plugin = p;
        file = new File(new File(p.getDataFolder(), "Saved"), "WorldObjects.yml");
        load();
    }

    /** A stable key for a block location (world + integer block coords). */
    public static String key(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public static Obj get(Location loc) {
        String k = key(loc);
        return k == null ? null : objects.get(k);
    }

    /** Get the object at this block, creating a blank one if absent (for annotation). */
    public static Obj getOrCreate(Location loc) {
        String k = key(loc);
        if (k == null) return null;
        return objects.computeIfAbsent(k, x -> new Obj());
    }

    public static boolean remove(Location loc) {
        String k = key(loc);
        if (k == null) return false;
        boolean removed = objects.remove(k) != null;
        if (removed) save();
        return removed;
    }

    public static void save() {
        if (file == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Obj> e : objects.entrySet()) {
            String path = e.getKey().replace(':', '_'); // ':' is a YAML path separator — encode it
            yaml.set(path + ".rawKey", e.getKey());
            yaml.set(path + ".locked", e.getValue().locked);
            yaml.set(path + ".hidden", e.getValue().hidden);
            yaml.set(path + ".description", e.getValue().description);
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (Exception ex) {
            LOGGER.warning("Failed to save world objects: " + ex.getMessage());
        }
    }

    private static void load() {
        objects.clear();
        if (file == null || !file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String path : yaml.getKeys(false)) {
            String rawKey = yaml.getString(path + ".rawKey");
            if (rawKey == null) continue;
            Obj o = new Obj();
            o.locked = yaml.getBoolean(path + ".locked", false);
            o.hidden = yaml.getBoolean(path + ".hidden", false);
            o.description = yaml.getString(path + ".description", "");
            objects.put(rawKey, o);
        }
        LOGGER.info("Loaded " + objects.size() + " interactive objects.");
    }
}
