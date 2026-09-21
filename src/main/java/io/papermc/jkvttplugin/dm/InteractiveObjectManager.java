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
 * wall, bookshelf, …). A player right-click then routes through the DM instead of doing the vanilla
 * thing. State persists per world+coords.
 *
 * <p>An object has exactly one {@link Obj.Opening} — what happens when a player tries to open it —
 * plus flags that are genuinely independent of it: hidden, trapped, and loot. A {@code description}
 * is orthogonal to all of them: a sealed prop, a locked strongbox and a trapped chest can each carry
 * flavor text, and so can a plain openable block.
 *
 * <p>Passive-Perception reveal and per-player reveal state are later slices of the epic.
 */
public final class InteractiveObjectManager {

    private InteractiveObjectManager() {}

    /** One annotated block. Mutable so the DM can toggle flags in place. */
    public static final class Obj {
        /**
         * What happens when a player tries to open this block. Exactly one value applies, which is
         * why it's a single field and not a pile of booleans — a block can't be both pickable and
         * never-opening. Orthogonal state (hidden, trapped, loot, description) lives alongside it.
         */
        public enum Opening {
            /** Vanilla behavior: it opens. A description, if set, is shown as flavor first. */
            OPENS,
            /** It doesn't open, and the DM is pinged to call a check — players can try to get past it. */
            LOCKED,
            /** Scenery. It never opens, no roll will change that, and the DM isn't pinged. */
            SEALED
        }

        public Opening opening = Opening.OPENS;
        public boolean hidden;   // players don't get the interaction until the DM reveals it
        public String description = "";   // flavor, shown whatever the opening/trap state is
        // Trap (#185): armed until disarmed. On interaction the DM is offered spot/disarm/trigger.
        public boolean trapped;
        public boolean disarmed;
        public String trapDamage = "";   // dice, e.g. "2d10"
        public String trapSave = "";     // ability the victim saves with, e.g. "dexterity"
        public int trapDc;               // reference DC (spot / disarm / save); the DM can adjust per check
        public java.util.List<String> loot = new java.util.ArrayList<>(); // item ids, e.g. "longsword", "gold_piece x10"
        // Key (#200): the item id that opens this lock without a check, or "" for none. A key is used
        // up only when keySingleUse is set; either way the lock stays open afterwards, since a consumed
        // key must not leave a lock that can never open again.
        public String keyItem = "";
        public boolean keySingleUse;

        /** True when a player poking this block springs the trap (trapped and not yet disarmed). */
        public boolean hasArmedTrap() {
            return trapped && !disarmed;
        }

        public boolean hasKey() { return keyItem != null && !keyItem.isBlank(); }

        /** True when this is a lock whose key is among the item ids a player is carrying. */
        public boolean keyOpens(java.util.Collection<String> carriedItemIds) {
            if (opening != Opening.LOCKED || !hasKey() || carriedItemIds == null) return false;
            for (String id : carriedItemIds) if (keyItem.equalsIgnoreCase(id)) return true;
            return false;
        }
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

    /**
     * The other half of a double chest, or null for anything else.
     *
     * <p>A double chest is two blocks with two sets of coordinates, so an annotation on one half
     * says nothing about the other. Without this, locking the left half leaves the right half an
     * ordinary chest and the party walks straight past the lock.
     */
    public static org.bukkit.block.Block partnerHalf(org.bukkit.block.Block block) {
        if (block == null || !(block.getState() instanceof org.bukkit.block.Chest chest)) return null;
        if (!(chest.getInventory() instanceof org.bukkit.inventory.DoubleChestInventory dci)) return null;
        Location left = dci.getLeftSide().getLocation();
        Location right = dci.getRightSide().getLocation();
        if (left == null || right == null) return null;
        Location mine = block.getLocation();
        Location other = sameBlock(left, mine) ? right : left;
        return sameBlock(other, mine) ? null : other.getBlock();
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    /**
     * The block that actually carries this block's annotation — itself, or the other half of its
     * double chest. Annotating either half is enough, and a chest joined or split after the fact
     * still resolves, because this looks both ways every time instead of picking a canonical half
     * once and storing it.
     */
    public static org.bukkit.block.Block annotationBlock(org.bukkit.block.Block block) {
        if (block == null || get(block.getLocation()) != null) return block;
        org.bukkit.block.Block partner = partnerHalf(block);
        return (partner != null && get(partner.getLocation()) != null) ? partner : block;
    }

    /** The annotation on this block or on its double-chest partner. */
    public static Obj getForBlock(org.bukkit.block.Block block) {
        return block == null ? null : get(annotationBlock(block).getLocation());
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

    /** Remove by stored key — the only way to reach an orphan whose block no longer exists. */
    public static boolean removeByKey(String k) {
        if (k == null) return false;
        boolean removed = objects.remove(k) != null;
        if (removed) save();
        return removed;
    }

    /** Put an annotation back at a location — used by {@code /dm object restore} after a break. */
    public static void put(Location loc, Obj o) {
        String k = key(loc);
        if (k == null || o == null) return;
        objects.put(k, o);
        save();
    }

    /** Every annotation, keyed by location. Read-only: mutate through the other methods. */
    public static Map<String, Obj> all() {
        return java.util.Collections.unmodifiableMap(objects);
    }

    /**
     * Turn a stored key back into a Location. Parsed from the RIGHT (the last three segments are
     * x/y/z) so a world name containing ':' survives the round trip. Returns null if the world isn't
     * loaded or the key is malformed.
     */
    public static Location locationFromKey(String k) {
        if (k == null) return null;
        int z = k.lastIndexOf(':');
        if (z <= 0) return null;
        int y = k.lastIndexOf(':', z - 1);
        if (y <= 0) return null;
        int x = k.lastIndexOf(':', y - 1);
        if (x <= 0) return null;
        org.bukkit.World world = Bukkit.getWorld(k.substring(0, x));
        if (world == null) return null;
        try {
            return new Location(world,
                    Integer.parseInt(k.substring(x + 1, y)),
                    Integer.parseInt(k.substring(y + 1, z)),
                    Integer.parseInt(k.substring(z + 1)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static void save() {
        if (file == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        // Index-based sections (o0, o1, …) so a world name with '.'/':'/'_' can never corrupt the path;
        // the real location key rides inside each entry.
        int i = 0;
        for (Map.Entry<String, Obj> e : objects.entrySet()) {
            String path = "o" + (i++);
            yaml.set(path + ".key", e.getKey());
            yaml.set(path + ".opening", e.getValue().opening.name());
            yaml.set(path + ".hidden", e.getValue().hidden);
            yaml.set(path + ".description", e.getValue().description);
            yaml.set(path + ".trapped", e.getValue().trapped);
            yaml.set(path + ".disarmed", e.getValue().disarmed);
            yaml.set(path + ".trapDamage", e.getValue().trapDamage);
            yaml.set(path + ".trapSave", e.getValue().trapSave);
            yaml.set(path + ".trapDc", e.getValue().trapDc);
            yaml.set(path + ".loot", e.getValue().loot);
            yaml.set(path + ".keyItem", e.getValue().keyItem);
            yaml.set(path + ".keySingleUse", e.getValue().keySingleUse);
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
            String rawKey = yaml.getString(path + ".key");
            if (rawKey == null) continue;
            Obj o = new Obj();
            String opening = yaml.getString(path + ".opening", Obj.Opening.OPENS.name());
            try {
                o.opening = Obj.Opening.valueOf(opening.toUpperCase());
            } catch (IllegalArgumentException ex) {
                LOGGER.warning("Unknown opening '" + opening + "' on " + rawKey + " — treating it as OPENS.");
            }
            o.hidden = yaml.getBoolean(path + ".hidden", false);
            o.description = yaml.getString(path + ".description", "");
            o.trapped = yaml.getBoolean(path + ".trapped", false);
            o.disarmed = yaml.getBoolean(path + ".disarmed", false);
            o.trapDamage = yaml.getString(path + ".trapDamage", "");
            o.trapSave = yaml.getString(path + ".trapSave", "");
            o.trapDc = yaml.getInt(path + ".trapDc", 0);
            o.loot = new java.util.ArrayList<>(yaml.getStringList(path + ".loot"));
            o.keyItem = yaml.getString(path + ".keyItem", "");
            o.keySingleUse = yaml.getBoolean(path + ".keySingleUse", false);
            objects.put(rawKey, o);
        }
        LOGGER.info("Loaded " + objects.size() + " interactive objects.");
    }
}
