package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.LootEntry;
import io.papermc.jkvttplugin.dm.AdjustCommand;
import io.papermc.jkvttplugin.dm.ViewCommand;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The DM's full view of one character or creature (#175): {@code /dm view <who> full}, or sneak +
 * right-click with the View tool. Read-only: nothing here can be taken or moved.
 *
 * <pre>
 *  row 0     summary · sheet / stat block · notes · add a note · adjust
 *  rows 1-4  a character's inventory (backpack, then hotbar), or what a creature carries and drops
 *  row 5     a character's armor and off-hand
 * </pre>
 *
 * A character's inventory is their player's (until characters get their own, #213).
 */
public final class DmViewMenu {

    private DmViewMenu() {}

    public static void open(Player dm, UUID targetId) {
        CombatTargets.Target t = AdjustCommand.targetFor(targetId);
        if (t == null) {
            dm.sendMessage(Component.text("They're not around any more.", NamedTextColor.GRAY));
            return;
        }
        dm.openInventory(build(t));
    }

    private static Inventory build(CombatTargets.Target t) {
        Combatant c = t.combatant();
        Inventory inv = Bukkit.createInventory(new MenuHolder(MenuType.DM_VIEW, c.getId()), 54,
                Component.text("View: " + c.getDisplayName()));
        DndEntityInstance creature = c.getEntityInstance();

        inv.setItem(0, tile(creature != null ? Material.ARMOR_STAND : Material.PLAYER_HEAD, c.getDisplayName(),
                NamedTextColor.GOLD, List.of(AdjustCommand.summary(c)), null));
        inv.setItem(2, creature != null
                ? tile(Material.WRITABLE_BOOK, "Stat block", NamedTextColor.YELLOW, lines("Abilities, attacks, senses"), "statblock")
                : tile(Material.WRITABLE_BOOK, "Character sheet", NamedTextColor.YELLOW, lines("Their full sheet"), "sheet"));

        List<String> notes = ViewCommand.notesFor(c);
        List<Component> noteLore = new ArrayList<>();
        if (notes.isEmpty()) noteLore.add(line("No notes yet.", NamedTextColor.DARK_GRAY));
        for (String n : notes) noteLore.add(line("• " + n, NamedTextColor.GRAY));
        noteLore.add(line("Only DMs ever see these.", NamedTextColor.DARK_AQUA));
        inv.setItem(4, tile(Material.PAPER, "DM notes (" + notes.size() + ")", NamedTextColor.DARK_AQUA, noteLore, null));
        inv.setItem(5, tile(Material.FEATHER, "Add a note…", NamedTextColor.AQUA, lines("Fills /dm note … add"), "note"));
        inv.setItem(8, tile(Material.BLAZE_ROD, "Adjust", NamedTextColor.RED, lines("HP, AC, conditions"), "adjust"));

        if (creature == null) {
            Player p = c.getPlayer();
            if (p == null) {
                inv.setItem(22, tile(Material.BARRIER, "They're offline", NamedTextColor.GRAY, lines("Their inventory shows while they're online."), null));
                return inv;
            }
            PlayerInventory pi = p.getInventory();
            for (int i = 9; i < 36; i++) inv.setItem(i, copy(pi.getItem(i)));         // backpack → rows 1-3
            for (int i = 0; i < 9; i++) inv.setItem(36 + i, copy(pi.getItem(i)));     // hotbar → row 4
            ItemStack[] armor = pi.getArmorContents(); // boots, legs, chest, helmet
            for (int i = 0; i < 4; i++) inv.setItem(48 - i, copy(armor[i]));           // helmet at 45 … boots at 48
            inv.setItem(50, copy(pi.getItemInOffHand()));
        } else {
            // What the creature carries: its loot table, with how hard each thing is to find.
            List<LootEntry> loot = creature.getTemplate().getLootTable();
            int slot = 9;
            if (loot.isEmpty()) {
                inv.setItem(22, tile(Material.BARRIER, "Carries nothing lootable", NamedTextColor.GRAY, lines(), null));
            }
            for (LootEntry e : loot) {
                if (slot > 53) break;
                ItemStack it = ItemUtil.itemFromId(e.getItemId(), Math.max(1, e.getQty()));
                if (it == null) it = tile(Material.PAPER, e.getItemId(), NamedTextColor.WHITE, lines("Unknown item id"), null);
                List<Component> lore = it.lore() != null ? new ArrayList<>(it.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(line(e.getDc() > 0 ? "Found with a DC " + e.getDc() + " " + (e.getCheck() != null ? e.getCheck().getDisplayName() : "check")
                        : "In plain sight on the body", NamedTextColor.GOLD));
                it.editMeta(m -> m.lore(lore));
                inv.setItem(slot++, it);
            }
        }
        return inv;
    }

    public static void handleClick(Player dm, UUID targetId, String payload) {
        CombatTargets.Target t = AdjustCommand.targetFor(targetId);
        if (t == null || payload == null) return;
        Combatant c = t.combatant();
        String arg = c.getDisplayName().contains(" ") ? "\"" + c.getDisplayName() + "\"" : c.getDisplayName();
        switch (payload) {
            case "sheet" -> {
                if (c.getCharacterSheet() != null) ViewCharacterSheetMenu.open(dm, c.getCharacterSheet().getCharacterId());
            }
            case "statblock" -> {
                if (c.getEntityInstance() != null) EntityStatBlockMenu.open(dm, c.getEntityInstance());
            }
            case "adjust" -> AdjustMenu.open(dm, targetId);
            case "note" -> {
                dm.closeInventory();
                String cmd = "/dm note " + arg + " add ";
                dm.sendMessage(Component.text("[click to write the note]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(cmd)).hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd))));
            }
            default -> { }
        }
    }

    // ==================== HELPERS ====================

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static ItemStack tile(Material mat, String name, NamedTextColor color, List<Component> lore, String payload) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(m -> {
            m.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
        });
        if (payload != null) ItemUtil.tagAction(item, MenuAction.DM_VIEW, payload);
        return item;
    }

    private static List<Component> lines(String... text) {
        List<Component> out = new ArrayList<>();
        for (String s : text) out.add(line(s, NamedTextColor.GRAY));
        return out;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
