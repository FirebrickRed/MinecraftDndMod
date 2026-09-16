package io.papermc.jkvttplugin.ui.listener;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.InnateSpell;
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

        // In combat, hand off to /combat cast so the spell actually resolves (attack/save/damage) —
        // don't consume anything here, the cast flow owns slots and concentration (#196).
        if (routeToCombatCast(player, cantrip)) return;

        // Handle concentration
        handleConcentration(player, sheet, cantrip);

        // Cast message
        player.sendMessage(Component.text("You cast ", NamedTextColor.AQUA)
                .append(Component.text(spellName, NamedTextColor.YELLOW))
                .append(Component.text("!", NamedTextColor.AQUA)));

        // Close inventory after casting (consistent UX)
        player.closeInventory();

        // ToDo: Out-of-combat spell effects (#152). In-combat casting resolves through /combat cast.
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

        // In combat, hand off to /combat cast (see handleCantripCast). Slot/concentration are the
        // cast flow's job, so nothing is consumed here.
        if (routeToCombatCast(player, spell)) return;

        // Check if this is an innate spell
        InnateSpell innateSpell = sheet.getAvailableInnateSpells().stream()
                .filter(innate -> innate.getSpellId().equalsIgnoreCase(spell.getId()))
                .findFirst()
                .orElse(null);

        if (innateSpell != null) {
            // Casting innate spell - check uses instead of spell slots
            if (!innateSpell.canCast()) {
                player.sendMessage(Component.text("You have no uses remaining for this ability!", NamedTextColor.RED));
                return;
            }

            // Use the innate spell
            innateSpell.use();

            // Handle concentration
            handleConcentration(player, sheet, spell);

            // Cast message
            player.sendMessage(Component.text("You use ", NamedTextColor.AQUA)
                    .append(Component.text(spellName, NamedTextColor.YELLOW))
                    .append(Component.text("! (", NamedTextColor.AQUA))
                    .append(Component.text(innateSpell.getUsageDisplay() + " remaining", NamedTextColor.GRAY))
                    .append(Component.text(")", NamedTextColor.AQUA)));
        } else {
            // Casting class spell - check spell slots
            if (!sheet.hasSpellSlot(castingLevel)) {
                player.sendMessage(Component.text("You don't have any " + castingLevel + " level slots remaining!", NamedTextColor.RED));
                return;
            }

            // Consume slot
            sheet.consumeSpellSlot(castingLevel);

            // Handle concentration
            handleConcentration(player, sheet, spell);

            // Cast message
            player.sendMessage(Component.text("You cast ", NamedTextColor.AQUA)
                    .append(Component.text(spellName, NamedTextColor.YELLOW))
                    .append(Component.text("!", NamedTextColor.AQUA)));
        }

        // Close inventory after casting (consistent UX with cantrips)
        player.closeInventory();

        // ToDo: Out-of-combat spell effects (#152). In-combat casting resolves through /combat cast.
    }

    /**
     * If the player is in an active combat, close the menu and fill the {@code /combat cast}
     * command in chat so the real cast flow resolves the spell (attack roll, save, damage) — the
     * spellbook alone only consumed a slot and printed a message (#196). Returns true if it routed.
     *
     * <p>A targeted spell fills with a trailing space for the target name (tab-completes to
     * combatants); a self / AoE spell fills the whole command ready to send. Nothing is consumed
     * here — {@code /combat cast} owns slots and concentration, so routing can't double-spend.
     */
    private boolean routeToCombatCast(Player player, DndSpell spell) {
        io.papermc.jkvttplugin.combat.CombatSession session =
                io.papermc.jkvttplugin.combat.CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session == null || session.isSetupPhase()) return false; // out of combat → #152 path

        player.closeInventory();
        boolean needsTarget = !spell.isAoe()
                && !(spell.getRange() != null && spell.getRange().equalsIgnoreCase("Self"));
        String cmd = "/combat cast " + spell.getId() + (needsTarget ? " " : "");
        player.sendMessage(Component.text("✨ Cast " + spell.getName() + " — ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(needsTarget ? "[click, then name your target]" : "[click to cast]",
                        NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(
                                needsTarget ? "Fills: " + cmd + "<target> — then pick your roll mode."
                                            : "Fills: " + cmd + " — press Enter to cast.")))));
        return true;
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

    /**
     * Handles concentration for spell casting.
     * If the new spell requires concentration and the character is already concentrating,
     * breaks the old concentration and notifies the player.
     * Then sets the new concentration if the spell requires it.
     *
     * @param player The player casting the spell
     * @param sheet The character sheet
     * @param spell The spell being cast
     */
    private void handleConcentration(Player player, CharacterSheet sheet, DndSpell spell) {
        // Break existing concentration if new spell requires it
        if (spell.isConcentration() && sheet.isConcentrating()) {
            DndSpell currentConc = sheet.getConcentratingOn();
            player.sendMessage(Component.text("Breaking concentration on ", NamedTextColor.YELLOW)
                    .append(Component.text(currentConc.getName(), NamedTextColor.AQUA))
                    .append(Component.text("...", NamedTextColor.YELLOW)));
            sheet.breakConcentration();
        }

        // Set new concentration if needed
        if (spell.isConcentration()) {
            sheet.setConcentratingOn(spell);
        }
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
