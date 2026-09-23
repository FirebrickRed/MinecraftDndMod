package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.ui.menu.SpellCastingMenu;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class SpellFocusListener implements Listener {
    private final Plugin plugin;

    public SpellFocusListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!isSpellFocus(item)) return;
        Player player = event.getPlayer();

        // Right-clicking a chest or door with thieves' tools (or anything that doubles as a focus) is
        // using the block, not casting: leave it to the chest's [Open it] / [Ask for a check] prompt.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && (event.getClickedBlock().getState() instanceof org.bukkit.block.Container
                    || event.getClickedBlock().getType().isInteractable())) return;

        // A class with no spellcasting has no focus type, so only a component pouch works for it.
        // For someone who can't cast through this item (a rogue's thieves' tools), it's just a tool:
        // say nothing and let the click through, rather than "You cannot use this type of focus!".
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(player);
        String focusType = getFocusType(item);
        String classRequirement = sheet != null && sheet.getMainClass() != null && sheet.getMainClass().getSpellcastingInfo() != null
                ? sheet.getMainClass().getSpellcastingInfo().getSpellcastingFocusType() : null;
        if (sheet == null || !sheet.hasSpells() || !canUseThisFocus(focusType, classRequirement)) return;

        event.setCancelled(true);

        SpellCastingMenu.open(player, sheet);
    }

    private boolean isSpellFocus(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("jkvtt", "spell_focus"), PersistentDataType.STRING);
    }

    private boolean canUseThisFocus(String focusType, String classRequirement) {
        if ("component".equals(focusType)) return true;

        return focusType != null && focusType.equals(classRequirement);
    }

    private String getFocusType(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("jkvtt", "spell_focus"), PersistentDataType.STRING);
    }
}
