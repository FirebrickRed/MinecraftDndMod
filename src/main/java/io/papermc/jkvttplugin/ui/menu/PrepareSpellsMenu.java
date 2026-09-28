package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.PreparedSpells;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Prepare Spells (#218): the prepared caster's choice of the day's spells, from their class list
 * (cleric, druid, paladin, artificer) or their spellbook (wizard).
 *
 * <pre>
 *  row 0     what this is · N/M prepared · when you can change them
 *  rows 1-4  the spells to choose from; a prepared one glows. Always-prepared spells come last, fixed
 *  row 5     ← back to the spellbook
 * </pre>
 *
 * Outside the long rest window it's view-only and says when it opens (a new wizard's first time
 * is allowed straight away).
 */
public final class PrepareSpellsMenu {

    private PrepareSpellsMenu() {}

    public static void open(Player player, CharacterSheet sheet) {
        player.openInventory(build(sheet));
    }

    /** [Prepare spells] in chat (after a long rest, a new wizard): opens this menu for that character. */
    public static Component button(CharacterSheet sheet) {
        return Component.text("[Prepare spells]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Open the Prepare Spells menu")))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.callback(a -> {
                    if (a instanceof Player p) open(p, sheet);
                }, net.kyori.adventure.text.event.ClickCallback.Options.builder()
                        .uses(net.kyori.adventure.text.event.ClickCallback.UNLIMITED_USES)
                        .lifetime(java.time.Duration.ofHours(2)).build()));
    }

    static Inventory build(CharacterSheet sheet) {
        Inventory inv = Bukkit.createInventory(new MenuHolder(MenuType.PREPARE_SPELLS, sheet.getCharacterId()), 54,
                Component.text("Prepare Spells", NamedTextColor.DARK_PURPLE));
        boolean canChange = PreparedSpells.canChangeNow(sheet);
        boolean book = PreparedSpells.kind(sheet) == PreparedSpells.Kind.SPELLBOOK;
        int max = PreparedSpells.max(sheet);
        List<DndSpell> prepared = PreparedSpells.prepared(sheet);

        List<Component> head = new ArrayList<>();
        head.add(line((book ? "From your spellbook" : "From the " + sheet.getMainClass().getName().toLowerCase() + " spell list")
                + ", up to " + Util.getOrdinal(Math.max(1, PreparedSpells.highestSlotLevel(sheet))) + " level.", NamedTextColor.GRAY));
        head.add(line("Cantrips, racial spells and always-prepared spells don't count.", NamedTextColor.DARK_GRAY));
        head.add(Component.empty());
        head.add(canChange ? line("Click a spell to prepare or unprepare it.", NamedTextColor.GREEN)
                : line(PreparedSpells.whenYouCanChange(), NamedTextColor.YELLOW));
        if (book) {
            head.add(line("A ritual in your spellbook can be cast as a ritual", NamedTextColor.LIGHT_PURPLE));
            head.add(line("even when it isn't prepared (10 extra minutes, no slot).", NamedTextColor.LIGHT_PURPLE));
        } else {
            head.add(line("You can only cast a ritual you have prepared.", NamedTextColor.LIGHT_PURPLE));
        }
        inv.setItem(4, tile(Material.WRITABLE_BOOK, "Prepared: " + prepared.size() + "/" + max,
                prepared.size() >= max ? NamedTextColor.GREEN : NamedTextColor.YELLOW, head));

        int slot = 9;
        for (DndSpell spell : PreparedSpells.candidates(sheet)) {
            if (slot > 44) break;
            boolean on = PreparedSpells.isPrepared(sheet, spell);
            ItemStack item = spell.createItemStack();
            List<Component> lore = new ArrayList<>();
            lore.add(line(on ? "✔ Prepared" : "Not prepared", on ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            if (!on && spell.isRitual() && book) lore.add(line("Ritual: you can still cast it as one.", NamedTextColor.LIGHT_PURPLE));
            if (canChange) lore.add(line(on ? "Click to unprepare" : prepared.size() >= max ? "You're at " + max + ": unprepare one first" : "Click to prepare",
                    NamedTextColor.AQUA));
            if (item.lore() != null) { lore.add(Component.empty()); lore.addAll(item.lore()); }
            item.editMeta(m -> {
                m.lore(lore);
                if (on) { m.addEnchant(Enchantment.UNBREAKING, 1, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
            });
            if (canChange) ItemUtil.tagAction(item, MenuAction.PREPARE_SPELL, spell.getId());
            inv.setItem(slot++, item);
        }
        // Always prepared (domain / oath / subclass spells): shown so the list is complete, fixed.
        for (String id : sheet.alwaysPreparedSpellIds()) {
            if (slot > 44) break;
            DndSpell spell = SpellLoader.getSpell(id);
            if (spell == null || spell.getLevel() == 0 || spell.getLevel() > PreparedSpells.highestSlotLevel(sheet)) continue;
            ItemStack item = spell.createItemStack();
            List<Component> lore = new ArrayList<>();
            lore.add(line("✦ Always prepared (" + (sheet.getSubclass() != null ? sheet.getSubclass().getName() : "a feature") + ")", NamedTextColor.GOLD));
            lore.add(line("Doesn't count against your " + max + ".", NamedTextColor.DARK_GRAY));
            if (item.lore() != null) { lore.add(Component.empty()); lore.addAll(item.lore()); }
            item.editMeta(m -> m.lore(lore));
            inv.setItem(slot++, item);
        }

        ItemStack back = tile(Material.ARROW, "← Spellbook", NamedTextColor.YELLOW, List.of());
        ItemUtil.tagAction(back, MenuAction.PREPARE_BACK, null);
        inv.setItem(45, back);
        return inv;
    }

    /** A click: prepare/unprepare, then redraw; or back to the spellbook. */
    public static void handleClick(Player player, CharacterSheet sheet, MenuAction action, String payload) {
        if (action == MenuAction.PREPARE_BACK) {
            SpellCastingMenu.open(player, sheet);
            return;
        }
        if (action != MenuAction.PREPARE_SPELL || payload == null) return;
        if (!PreparedSpells.canChangeNow(sheet)) {
            player.sendMessage(Component.text(PreparedSpells.whenYouCanChange(), NamedTextColor.YELLOW));
            return;
        }
        DndSpell spell = SpellLoader.getSpell(payload);
        boolean was = PreparedSpells.isPrepared(sheet, spell);
        String problem = PreparedSpells.toggle(sheet, spell);
        if (problem != null) {
            player.sendMessage(Component.text(problem, NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text((was ? "Unprepared " : "Prepared ") + spell.getName() + " ("
                + PreparedSpells.prepared(sheet).size() + "/" + PreparedSpells.max(sheet) + ").", NamedTextColor.GRAY));
        player.openInventory(build(sheet));
    }

    private static ItemStack tile(Material mat, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(m -> {
            m.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
        });
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
