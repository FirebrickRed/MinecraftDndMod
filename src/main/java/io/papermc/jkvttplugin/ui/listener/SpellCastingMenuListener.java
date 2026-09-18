package io.papermc.jkvttplugin.ui.listener;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.ui.menu.SpellCastingMenu;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class SpellCastingMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        if (holder.getType() != MenuType.SPELL_CASTING) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        Player player = (Player) event.getWhoClicked();
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(player);

        if (sheet == null) {
            player.sendMessage(Component.text("No active character found!", NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        MenuAction action = ItemUtil.getAction(clickedItem);
        String payload = ItemUtil.getPayload(clickedItem);

        if (action == null) return;

        switch (action) {
            case CAST_CANTRIP -> handleCantripCast(player, sheet, payload);
            case CAST_SPELL -> handleSpellCast(player, sheet, payload);
            case SELECT_SPELL_LEVEL -> handleSlotSelection(player, sheet, payload);
            case VIEW_CANTRIPS -> handleViewCantrips(player, sheet);
            case BREAK_CONCENTRATION -> handleConcentrationClick(player, sheet);
            default -> {} // Ignore other actions
        }
    }

    private void handleCantripCast(Player player, CharacterSheet sheet, String spellName) {
        if (spellName == null) return;

        // Load cantrip to check concentration (normalize name to key)
        DndSpell cantrip = SpellLoader.getSpell(Util.normalize(spellName));
        if (cantrip == null) {
            player.sendMessage(Component.text("Cantrip not found: " + spellName, NamedTextColor.RED));
            return;
        }

        // Either way the click fills a command rather than casting from the menu: /combat cast in a
        // fight (it resolves the roll, #196), /character cast outside one (it announces and spends,
        // #152). The menu itself no longer consumes anything — one path owns the cost.
        routeToCastCommand(player, cantrip, 0);
    }

    private void handleSpellCast(Player player, CharacterSheet sheet, String payload) {
        if (payload == null) return;

        // Parse payload: "spellName:level"
        String[] parts = payload.split(":");
        if (parts.length != 2) return;

        String spellName = parts[0];
        int castingLevel;
        try {
            castingLevel = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }

        // Load spell to check concentration (normalize name to key)
        DndSpell spell = SpellLoader.getSpell(Util.normalize(spellName));
        if (spell == null) {
            player.sendMessage(Component.text("Spell not found: " + spellName, NamedTextColor.RED));
            return;
        }

        // Both in and out of combat, the click fills a command — the command owns the cost.
        // The slot level travels with it: the menu showed "⬆ Casting at 2nd level", so the command
        // has to spend a 2nd-level slot, not the spell's own.
        routeToCastCommand(player, spell, castingLevel);
    }

    /**
     * Close the menu and fill the right cast command in chat.
     *
     * <p>In a fight that's {@code /combat cast}, which resolves the attack roll, save or area
     * (#196). Outside one it's {@code /character cast}, which announces the spell, spends the slot
     * and hands the DM the damage/healing command (#152, first slice).
     *
     * <p>Nothing is consumed here. The menu used to deduct the slot itself, which meant the
     * in-combat route spent nothing at all and the out-of-combat route spent a slot for a message
     * with no visible effect. One path, one cost.
     */
    private void routeToCastCommand(Player player, DndSpell spell, int castingLevel) {
        io.papermc.jkvttplugin.combat.CombatSession session =
                io.papermc.jkvttplugin.combat.CombatSession.getSessionForPlayer(player.getUniqueId());
        boolean inCombat = session != null && !session.isSetupPhase();

        player.closeInventory();
        boolean needsTarget = !spell.isAoe()
                && !(spell.getRange() != null && spell.getRange().equalsIgnoreCase("Self"));
        // A spell picked from a higher slot's page carries that level, so the command spends it.
        // "level N" has to come last (it stops target-name collection), so when there's both a
        // target and an upcast the command fills with a <target> placeholder to replace rather than
        // a trailing space to type into — otherwise the level would be lost off the end.
        boolean upcast = castingLevel > spell.getLevel() && spell.getLevel() > 0;
        String base = (inCombat ? "/combat cast " : "/character cast ") + spell.getId();
        String cmd = upcast
                ? base + (needsTarget ? " <target>" : "") + " level " + castingLevel
                : base + (needsTarget ? " " : "");
        String where = inCombat ? "then pick your roll mode." : "the DM applies the effect.";
        String hover = needsTarget && !upcast
                ? "Fills: " + cmd + "<target> — " + where
                : "Fills: " + cmd;
        if (upcast) hover += "\nCast from a level " + castingLevel + " slot"
                + (needsTarget ? " — replace <target> before sending." : ".");
        player.sendMessage(Component.text("✨ Cast " + spell.getName()
                        + (upcast ? " (level " + castingLevel + ")" : "") + " — ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(needsTarget ? "[click, then name your target]" : "[click to cast]",
                        NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(hover)))));
    }

    private void handleSlotSelection(Player player, CharacterSheet sheet, String levelStr) {
        if (levelStr == null) return;

        int spellLevel;
        try {
            spellLevel = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            return;
        }

        // Check if character has spell slots OR innate spells at this level
        boolean hasSpellSlots = sheet.hasSpellSlot(spellLevel);
        boolean hasInnateSpells = sheet.hasInnateSpellsAtLevel(spellLevel);

        if (!hasSpellSlots && !hasInnateSpells) {
            player.sendMessage(Component.text("No spell slots available for that level!", NamedTextColor.RED));
            return;
        }

        // Rebuild menu showing spells for this level
        player.openInventory(SpellCastingMenu.build(sheet, spellLevel));
    }

    private void handleViewCantrips(Player player, CharacterSheet sheet) {
        // Rebuild menu with cantrips view (level 0)
        player.openInventory(SpellCastingMenu.build(sheet, 0));
    }

    private void handleConcentrationClick(Player player, CharacterSheet sheet) {
        if (!sheet.isConcentrating()) {
            player.sendMessage(Component.text("You are not concentrating on anything.", NamedTextColor.GRAY));
            return;
        }

        DndSpell spell = sheet.getConcentratingOn();
        sheet.breakConcentration();

        player.sendMessage(Component.text("You stop concentrating on ", NamedTextColor.YELLOW).append(Component.text(spell.getName(), NamedTextColor.AQUA)));

        // Refresh menu
        player.openInventory(SpellCastingMenu.build(sheet, 1)); // Default back to 1st level
    }
}
