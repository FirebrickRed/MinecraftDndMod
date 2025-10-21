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

        // Check concentration (some cantrips require concentration)
        if (cantrip.isConcentration() && sheet.isConcentrating()) {
            DndSpell currentConc = sheet.getConcentratingOn();
            player.sendMessage(Component.text("Breaking concentration on ", NamedTextColor.YELLOW)
                    .append(Component.text(currentConc.getName(), NamedTextColor.AQUA))
                    .append(Component.text("...", NamedTextColor.YELLOW)));
            sheet.breakConcentration();
        }

        // Set concentration if needed
        if (cantrip.isConcentration()) {
            sheet.setConcentratingOn(cantrip);
        }

        // Cast message
        player.sendMessage(Component.text("You cast ", NamedTextColor.AQUA)
                .append(Component.text(spellName, NamedTextColor.YELLOW))
                .append(Component.text("!", NamedTextColor.AQUA)));

        // Close inventory after casting (consistent UX)
        player.closeInventory();

        // ToDo: Implement actual spell effects when spell system is built
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

        // Check if slot available
        if (!sheet.hasSpellSlot(castingLevel)) {
            player.sendMessage(Component.text("You don't have any " + castingLevel + " level slots remaining!", NamedTextColor.RED));
            return;
        }

        // Load spell to check concentration (normalize name to key)
        DndSpell spell = SpellLoader.getSpell(Util.normalize(spellName));
        if (spell == null) {
            player.sendMessage(Component.text("Spell not found: " + spellName, NamedTextColor.RED));
            return;
        }

        // Check concentration
        if (spell.isConcentration() && sheet.isConcentrating()) {
            DndSpell currentConc = sheet.getConcentratingOn();
            player.sendMessage(Component.text("Breaking concentration on ", NamedTextColor.YELLOW)
                    .append(Component.text(currentConc.getName(), NamedTextColor.AQUA))
                    .append(Component.text("...", NamedTextColor.YELLOW)));
            sheet.breakConcentration();
        }

        // Consume slot
        sheet.consumeSpellSlot(castingLevel);

        // Set concentration if needed
        if (spell.isConcentration()) {
            sheet.setConcentratingOn(spell);
        }

        // Cast message
        player.sendMessage(Component.text("You cast ", NamedTextColor.AQUA)
                .append(Component.text(spellName, NamedTextColor.YELLOW))
                .append(Component.text("!", NamedTextColor.AQUA)));

        // Close inventory after casting (consistent UX with cantrips)
        player.closeInventory();

        // ToDo: Implement actual spell effects when spell system is built
    }

    private void handleSlotSelection(Player player, CharacterSheet sheet, String levelStr) {
        if (levelStr == null) return;

        int spellLevel;
        try {
            spellLevel = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            return;
        }

        if (!sheet.hasSpellSlot(spellLevel)) {
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
