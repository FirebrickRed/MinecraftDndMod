package io.papermc.jkvttplugin.dm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DM Inventory Mode (Issue #85 redesign). Toggling DM mode swaps the DM's hotbar for a toolbar of
 * DM tools (View, Exit, and later Spawn Group / Group Move / …). Their real inventory is saved to
 * disk on enter and restored on exit — and, if they disconnect or the server crashes while in DM
 * mode, they come back OUT of DM mode with their real inventory restored on next login.
 */
public class DmModeManager {

    private static Plugin plugin;
    private static File folder;
    private static NamespacedKey toolKey;

    /** DM tool identifiers (stored on the item's PDC). */
    public static final String TOOL_VIEW = "view";
    public static final String TOOL_POSSESS = "possess";
    public static final String TOOL_START = "combat_start";
    public static final String TOOL_ADD = "combat_add";
    public static final String TOOL_INITIATIVE = "combat_initiative";
    public static final String TOOL_MOVE = "move";
    public static final String TOOL_EXIT = "exit";

    private static final Set<UUID> inDmMode = new HashSet<>();

    public static void initialize(Plugin p) {
        plugin = p;
        toolKey = new NamespacedKey(p, "dm_tool");
        folder = new File(p.getDataFolder(), "Saved/DmInventory");
        if (!folder.exists()) folder.mkdirs();
    }

    public static boolean isInDmMode(Player player) {
        return inDmMode.contains(player.getUniqueId());
    }

    public static void toggle(Player player) {
        if (isInDmMode(player)) exit(player); else enter(player);
    }

    // ==================== ENTER / EXIT ====================

    public static void enter(Player player) {
        if (isInDmMode(player)) return;
        if (!saveInventory(player)) {
            player.sendMessage(Component.text("Couldn't save your inventory — DM mode not entered.", NamedTextColor.RED));
            return;
        }
        inDmMode.add(player.getUniqueId());
        player.getInventory().clear();
        giveTools(player);
        player.sendMessage(Component.text("⚙ DM mode ON", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" — your items are safely stashed. Use the Exit item or /dm mode to leave.", NamedTextColor.GRAY)));
    }

    public static void exit(Player player) {
        if (!inDmMode.remove(player.getUniqueId())) return;
        PossessionManager.endPossession(player, true); // drop possession (invis + follow) if any
        MoveToolManager.clearSelection(player);        // drop any Move-tool selection (clears glow)
        player.getInventory().clear();
        restoreInventory(player); // repopulates from the snapshot, then deletes it
        player.sendMessage(Component.text("⚙ DM mode OFF", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" — your inventory is back.", NamedTextColor.GRAY)));
    }

    /**
     * On login: if a snapshot exists, the player was in DM mode when they left (or the server
     * crashed). Restore their real inventory and leave them OUT of DM mode.
     */
    public static void recoverOnJoin(Player player) {
        // A crash mid-possession can leave the persisted invisibility/scale on the player even when
        // no snapshot exists; always scrub those so nobody logs in invisible or shrunk.
        PossessionManager.clearPossessionEffects(player);
        if (snapshotFile(player.getUniqueId()).exists()) {
            inDmMode.remove(player.getUniqueId());
            player.getInventory().clear();
            restoreInventory(player);
            player.sendMessage(Component.text("Restored your inventory (you were in DM mode before).", NamedTextColor.YELLOW));
        }
    }

    // ==================== INVENTORY SNAPSHOT (persisted) ====================

    private static File snapshotFile(UUID id) {
        return new File(folder, id + ".yml");
    }

    private static boolean saveInventory(Player player) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("contents", player.getInventory().getContents());
        cfg.set("armor", player.getInventory().getArmorContents());
        cfg.set("offhand", player.getInventory().getItemInOffHand());
        try {
            cfg.save(snapshotFile(player.getUniqueId()));
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save DM-mode inventory for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreInventory(Player player) {
        File f = snapshotFile(player.getUniqueId());
        if (!f.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);

        List<ItemStack> contents = (List<ItemStack>) cfg.getList("contents");
        if (contents != null) player.getInventory().setContents(contents.toArray(new ItemStack[0]));

        List<ItemStack> armor = (List<ItemStack>) cfg.getList("armor");
        if (armor != null) player.getInventory().setArmorContents(armor.toArray(new ItemStack[0]));

        ItemStack offhand = cfg.getItemStack("offhand");
        if (offhand != null) player.getInventory().setItemInOffHand(offhand);

        player.updateInventory();
        if (!f.delete()) f.deleteOnExit();
    }

    // ==================== DM TOOLS ====================

    public static void giveTools(Player player) {
        PossessionManager.clearWornGear(player); // drop any possessed entity's armor when back on the toolbar
        player.getInventory().setItem(0, tool(Material.SPYGLASS, TOOL_VIEW, "View",
                "Right-click a player → their character sheet", "Right-click an entity → its stat block"));
        player.getInventory().setItem(1, tool(Material.LEAD, TOOL_POSSESS, "Possess",
                "Right-click an entity → control it", "(you go invisible, it follows you; sneak to stop)"));
        player.getInventory().setItem(2, tool(Material.IRON_SWORD, TOOL_START, "Start Combat",
                "Right-click to begin a combat encounter", "Right-click again to cancel it (before initiative)",
                "(then add combatants and roll initiative)"));
        // NB: not a NAME_TAG — vanilla would stamp the tool's name onto the clicked mob.
        player.getInventory().setItem(3, tool(Material.BOOK, TOOL_ADD, "Add / Remove Combatant",
                "Right-click a player or entity to add them (they glow)", "Right-click again to remove them"));
        player.getInventory().setItem(4, tool(Material.CLOCK, TOOL_INITIATIVE, "Roll for Initiative",
                "Right-click to roll initiative", "(begins turns for everyone added)"));
        player.getInventory().setItem(5, tool(Material.LEATHER_BOOTS, TOOL_MOVE, "Move",
                "Right-click entities to select them (they glow)", "then right-click the ground to send them there",
                "(in combat: only on that entity's turn, counts vs speed)"));
        player.getInventory().setItem(8, tool(Material.BARRIER, TOOL_EXIT, "Exit DM Mode",
                "Right-click to leave DM mode", "(gives your normal inventory back)"));
    }

    private static ItemStack tool(Material material, String toolId, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        for (String l : loreLines) lore.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, toolId);
        item.setItemMeta(meta);
        return item;
    }

    /** The DM-tool id on this item, or null if it isn't a DM tool. */
    public static String getToolType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
    }
}
