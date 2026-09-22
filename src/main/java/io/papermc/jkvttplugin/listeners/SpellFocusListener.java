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

        event.setCancelled(true);
        Player player = event.getPlayer();

        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null || !sheet.hasSpells()) {
            player.sendMessage("You don't know any spells!");
            return;
        }

        // A class with no spellcasting has no focus type, so only a component pouch works for it.
        // Skipping the check for them let a tiefling rogue cast through thieves' tools.
        String focusType = getFocusType(item);
        String classRequirement = sheet.getMainClass() != null && sheet.getMainClass().getSpellcastingInfo() != null
                ? sheet.getMainClass().getSpellcastingInfo().getSpellcastingFocusType() : null;
        if (!canUseThisFocus(focusType, classRequirement)) {
            player.sendMessage("You cannot use this type of focus!");
            return;
        }

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
