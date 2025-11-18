package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.LoreBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a D&D 5e stat block for an entity.
 * Shows different information based on whether the viewer is a DM or player.
 */
public class EntityStatBlockMenu {

    private EntityStatBlockMenu() {}

    /**
     * Opens the stat block menu for a player viewing a specific entity instance.
     *
     * @param player The player viewing the stat block
     * @param instance The spawned entity instance to display
     */
    public static void open(Player player, DndEntityInstance instance) {
        player.openInventory(build(player, instance));
    }

    /**
     * Builds the stat block inventory.
     * NOTE: Currently hardcoded for DM-only access. Player visibility system deferred.
     *
     * @param player The player viewing the stat block
     * @param instance The entity instance being viewed
     * @return Inventory containing the stat block display
     */
    public static Inventory build(Player player, DndEntityInstance instance) {
        // TODO: Future - Implement proper DM permission system (jkvtt.dm check)
        // TODO: Future - Re-enable StatBlockVisibility for player/DM differentiation if needed
        boolean isDm = true; // HARDCODED: All users treated as DMs (stat blocks are DM-only for now)
        DndEntity template = instance.getTemplate();

        Inventory inv = Bukkit.createInventory(null, 54,
            Component.text(instance.getDisplayName() + "'s Stat Block"));

        // ====================  HEADER (Slot 4) ====================
        inv.setItem(4, buildHeaderItem(instance, template));

        // ==================== BASIC STATS (Slot 10) ====================
        inv.setItem(10, buildBasicStatsItem(instance, template));

        // ==================== ABILITIES (Slot 12) ====================
        inv.setItem(12, buildAbilitiesItem(template));

        // ==================== ATTACKS (Slot 14) ====================
        if (!template.getAttacks().isEmpty()) {
            inv.setItem(14, buildAttacksItem(template));
        }

        // ==================== DM NOTES (Slot 16) ====================
        if (template.getDmNotes() != null && !template.getDmNotes().isEmpty()) {
            inv.setItem(16, buildDmNotesItem(template));
        }

        // ==================== CLOSE BUTTON (Slot 49) ====================
        inv.setItem(49, buildCloseButton());

        return inv;
    }


    // ==================== ITEM BUILDERS ====================

    private static ItemStack buildHeaderItem(DndEntityInstance instance, DndEntity template) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(instance.getDisplayName(), NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = LoreBuilder.create()
            .addLine(capitalize(template.getSize()) + " " + capitalize(template.getCreatureType()), NamedTextColor.GRAY)
            .addLine(template.getSubtype() != null ? "(" + capitalize(template.getSubtype()) + ")" : "", NamedTextColor.DARK_GRAY)
            .build();

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildBasicStatsItem(DndEntityInstance instance, DndEntity template) {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Basic Stats", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));

        LoreBuilder builder = LoreBuilder.create();

        // HP (current/max)
        builder.addKeyValue("Hit Points", instance.getCurrentHp() + "/" + instance.getMaxHp(), NamedTextColor.RED);

        // AC
        builder.addKeyValue("Armor Class", String.valueOf(template.getArmorClass()), NamedTextColor.YELLOW);

        // Speed
        builder.addKeyValue("Speed", template.getSpeed() + " ft", NamedTextColor.GREEN);

        meta.lore(builder.build());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildAbilitiesItem(DndEntity template) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Ability Scores", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        for (Ability ability : Ability.values()) {
            int score = template.getAbilityScore(ability);
            int modifier = template.getAbilityModifier(ability);
            String modStr = modifier >= 0 ? "+" + modifier : String.valueOf(modifier);

            lore.add(Component.text(ability.getAbbreviation() + ": " + score + " (" + modStr + ")", NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildAttacksItem(DndEntity template) {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Attacks", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        for (DndAttack attack : template.getAttacks()) {
            lore.add(Component.text("• " + attack.getName(), NamedTextColor.YELLOW));
            lore.add(Component.text("  To Hit: +" + attack.getToHit() + ", Reach: " + attack.getReach(), NamedTextColor.GRAY));
            lore.add(Component.text("  Damage: " + attack.getDamage() + " " + attack.getDamageType(), NamedTextColor.GRAY));
            lore.add(Component.empty());
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildDmNotesItem(DndEntity template) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("DM Notes", NamedTextColor.DARK_RED)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("[DM ONLY - Hidden from Players]", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());

        // Wrap DM notes for readability
        for (String line : template.getDmNotes().split("\n")) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Close", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));

        item.setItemMeta(meta);
        return item;
    }

    // ==================== UTILITY ====================

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}