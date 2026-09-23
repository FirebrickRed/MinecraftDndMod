package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import io.papermc.jkvttplugin.data.model.AcAdjustment;
import io.papermc.jkvttplugin.data.model.DndCondition;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.dm.AdjustCommand;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Adjust menu (#175): the DM's hands on one creature or character. Opened by {@code /dm adjust
 * <who>} or the DM-mode Adjust tool. Every tile calls the same {@link AdjustCommand} methods as the
 * typed command, so the two can't disagree.
 *
 * <pre>
 *  row 0  header (who, HP, AC, conditions)
 *  row 1  HP: nudge · set exactly · full · temp · max (creatures) · drop to 0 · revive
 *  row 2  AC: nudge (click temporary, shift-click permanent for creatures) · clear · stat block
 *  rows 3-4  conditions, click to toggle
 *  row 5  hide / reveal (in a fight)
 * </pre>
 */
public final class AdjustMenu {

    private AdjustMenu() {}

    /** A temporary AC nudge waiting on "for how long?" — per DM, since the chooser is its own screen. */
    private static final Map<UUID, Integer> pendingAcDelta = new HashMap<>();

    public static void open(Player dm, UUID targetId) {
        CombatTargets.Target t = AdjustCommand.targetFor(targetId);
        if (t == null) {
            dm.sendMessage(Component.text("They're not around any more.", NamedTextColor.GRAY));
            dm.closeInventory();
            return;
        }
        dm.openInventory(build(t));
    }

    private static Inventory build(CombatTargets.Target t) {
        Combatant c = t.combatant();
        UUID id = c.getId();
        Inventory inv = Bukkit.createInventory(new MenuHolder(MenuType.DM_ADJUST, id), 54,
                Component.text("Adjust: " + c.getDisplayName()));
        ItemStack filler = tile(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of(), null);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        boolean creature = c.getEntityInstance() != null;

        // Header
        inv.setItem(4, tile(creature ? Material.ARMOR_STAND : Material.PLAYER_HEAD, c.getDisplayName(), NamedTextColor.GOLD,
                List.of(AdjustCommand.summary(c)), null));

        // HP
        String hpLine = c.getCurrentHp() + "/" + c.getMaxHp() + (c.getTempHp() > 0 ? " (+" + c.getTempHp() + " temp)" : "");
        inv.setItem(10, tile(Material.GOLDEN_APPLE, "HP " + hpLine, NamedTextColor.RED,
                lines("Left-click: heal 1 · Right-click: damage 1", "Shift: 5 at a time"), "hp"));
        inv.setItem(11, tile(Material.NAME_TAG, "Set HP exactly…", NamedTextColor.WHITE,
                lines("Fills /dm adjust … hp in chat:", "12 sets, +5 heals, -2d6 damages"), "fill:hp"));
        inv.setItem(12, tile(Material.ENCHANTED_GOLDEN_APPLE, "Full HP", NamedTextColor.GREEN, lines(), "full"));
        inv.setItem(13, tile(Material.GOLDEN_CARROT, "Temp HP…", NamedTextColor.AQUA, lines("Fills /dm adjust … temp"), "fill:temp"));
        if (creature) {
            inv.setItem(14, tile(Material.APPLE, "Max HP " + c.getMaxHp() + "…", NamedTextColor.RED,
                    lines("Fills /dm adjust … maxhp", "e.g. hit dice you rolled yourself"), "fill:maxhp"));
        }
        inv.setItem(15, tile(Material.WITHER_ROSE, "Drop to 0 HP", NamedTextColor.DARK_RED,
                lines(creature ? "The creature dies." : "The character goes down and starts death saves."), "down"));
        if (c.isDead()) inv.setItem(16, tile(Material.TOTEM_OF_UNDYING, "Revive (1 HP)", NamedTextColor.GREEN, lines(), "revive"));

        // AC
        List<Component> acLore = new ArrayList<>();
        acLore.add(line("Click: +1 / right-click: -1, temporary", NamedTextColor.GRAY));
        acLore.add(line(creature ? "Shift-click: +1 / -1 for good (this creature only)"
                : "Their base AC comes from their armor", NamedTextColor.GRAY));
        if (c.getAcAdjustment() != null) acLore.add(line("Now: " + c.getAcAdjustment().describe(), NamedTextColor.LIGHT_PURPLE));
        DndEntityInstance inst = c.getEntityInstance();
        if (inst != null && inst.getAcOverride() != null) {
            acLore.add(line("Own AC " + inst.getAcOverride() + " (stat block says " + inst.getTemplate().getArmorClass() + ")", NamedTextColor.YELLOW));
        }
        inv.setItem(19, tile(Material.IRON_CHESTPLATE, "AC " + c.getArmorClass(), NamedTextColor.AQUA, acLore, "ac"));
        if (c.getAcAdjustment() != null) {
            inv.setItem(20, tile(Material.MILK_BUCKET, "Clear the DM AC adjustment", NamedTextColor.WHITE, lines(), "ac_clear"));
        }
        if (inst != null && inst.getAcOverride() != null) {
            inv.setItem(21, tile(Material.BOOK, "Back to the stat block's AC", NamedTextColor.WHITE, lines(), "ac_reset"));
        }

        // Conditions
        int slot = 27;
        for (DndCondition cond : ConditionLoader.getAll()) {
            if (slot > 44) break;
            boolean has = c.hasCondition(cond.getId());
            List<Component> lore = new ArrayList<>();
            lore.add(line(has ? "✔ Has it — click to remove" : "Click to add", has ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            for (String r : cond.getRules()) {
                boolean first = true;
                for (String part : io.papermc.jkvttplugin.util.Util.wrapText(r, 40)) {
                    lore.add(line((first ? "• " : "  ") + part, NamedTextColor.DARK_GRAY));
                    first = false;
                }
            }
            ItemStack it = tile(has ? Material.LIME_DYE : Material.GRAY_DYE, (has ? "✔ " : "") + cond.getName(),
                    has ? NamedTextColor.GREEN : NamedTextColor.WHITE, lore, "cond:" + cond.getId());
            if (has) it.editMeta(m -> m.setEnchantmentGlintOverride(true));
            inv.setItem(slot++, it);
        }

        // In a fight: hidden from the players or not
        if (t.inCombat()) {
            inv.setItem(49, c.isHidden()
                    ? tile(Material.ENDER_EYE, "Reveal to the players", NamedTextColor.YELLOW, lines("Shows as ??? now"), "reveal")
                    : tile(Material.SPYGLASS, "Hide from the players", NamedTextColor.GRAY, lines("Shows as ??? on the tracker"), "hide"));
        }
        return inv;
    }

    /** "For how long?" for a new temporary AC change. */
    private static Inventory durationChooser(CombatTargets.Target t, int delta) {
        Inventory inv = Bukkit.createInventory(new MenuHolder(MenuType.DM_ADJUST, t.combatant().getId()), 27,
                Component.text("AC " + (delta >= 0 ? "+" : "") + delta + ": for how long?"));
        ItemStack filler = tile(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of(), null);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        int slot = 10;
        for (AcAdjustment.Until u : AcAdjustment.Until.values()) {
            if (u == AcAdjustment.Until.NEXT_TURN && !t.inCombat()) continue;
            inv.setItem(slot, tile(Material.CLOCK, u.label(), NamedTextColor.AQUA, lines(), "until:" + u.name()));
            slot += 2;
        }
        inv.setItem(22, tile(Material.ARROW, "← Back", NamedTextColor.YELLOW, lines(), "back"));
        return inv;
    }

    // ==================== CLICKS ====================

    public static void handleClick(Player dm, UUID targetId, String payload, ClickType click) {
        CombatTargets.Target t = AdjustCommand.targetFor(targetId);
        if (t == null || payload == null) { dm.closeInventory(); return; }
        Combatant c = t.combatant();
        String name = quote(c.getDisplayName());
        boolean shift = click.isShiftClick();
        boolean right = click.isRightClick();

        if (payload.startsWith("fill:")) {
            String cmd = "/dm adjust " + name + " " + payload.substring(5) + " ";
            dm.closeInventory();
            dm.sendMessage(Component.text("[click to type the number]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.suggestCommand(cmd)).hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd))));
            return;
        }
        if (payload.startsWith("cond:")) {
            DndCondition cond = ConditionLoader.get(payload.substring(5));
            if (cond != null) AdjustCommand.setCondition(dm, t, cond, !c.hasCondition(cond.getId()));
            open(dm, targetId);
            return;
        }
        if (payload.startsWith("until:")) {
            Integer delta = pendingAcDelta.remove(dm.getUniqueId());
            AcAdjustment.Until u = AcAdjustment.Until.parse(payload.substring(6));
            if (delta != null && u != null) AdjustCommand.nudgeTempAc(dm, t, delta, u);
            open(dm, targetId);
            return;
        }
        switch (payload) {
            case "hp" -> {
                int step = shift ? 5 : 1;
                AdjustCommand.hp(dm, t, (right ? "-" : "+") + step, null);
            }
            case "full" -> AdjustCommand.full(dm, t);
            case "down" -> AdjustCommand.setHp(dm, t, 0);
            case "revive" -> io.papermc.jkvttplugin.combat.DamageHandler.revive(t.session(), c, 1);
            case "ac" -> {
                int delta = right ? -1 : 1;
                if (shift) {
                    DndEntityInstance inst = c.getEntityInstance();
                    if (inst == null) {
                        dm.sendMessage(Component.text("A character's AC comes from their armor; click (no shift) for a temporary change.", NamedTextColor.GRAY));
                    } else {
                        AdjustCommand.setPermanentAc(dm, t, inst.getBaseArmorClass() + delta);
                    }
                } else if (c.getAcAdjustment() == null) {
                    // A new temporary change needs a duration first.
                    pendingAcDelta.put(dm.getUniqueId(), delta);
                    dm.openInventory(durationChooser(t, delta));
                    return;
                } else {
                    AdjustCommand.nudgeTempAc(dm, t, delta, null);
                }
            }
            case "ac_clear" -> AdjustCommand.setTempAc(dm, t, null);
            case "ac_reset" -> AdjustCommand.setPermanentAc(dm, t, null);
            case "hide" -> dm.performCommand("combat hide " + c.getDisplayName());
            case "reveal" -> dm.performCommand("combat reveal " + c.getDisplayName());
            case "back" -> pendingAcDelta.remove(dm.getUniqueId());
            default -> { }
        }
        open(dm, targetId);
    }

    // ==================== HELPERS ====================

    private static ItemStack tile(Material mat, String name, NamedTextColor color, List<Component> lore, String payload) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(m -> {
            m.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
        });
        if (payload != null) ItemUtil.tagAction(item, MenuAction.ADJUST, payload);
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

    private static String quote(String name) { return name.contains(" ") ? "\"" + name + "\"" : name; }
}
