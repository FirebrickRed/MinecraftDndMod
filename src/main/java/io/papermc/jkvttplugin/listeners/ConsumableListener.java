package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.commands.DrinkCommand;
import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.data.model.DndItem;
import io.papermc.jkvttplugin.util.ItemUtil;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Clicking a drinkable item (any item with {@code healing:}) offers to drink it.
 *
 * <p>The click never drinks it by itself — it fills {@code /character drink} into chat, so the
 * player confirms with Enter and chooses how the dice are rolled. That's the same rule the attack
 * and damage prompts follow (#170/#11), and it keeps one path for healing: the command.
 *
 * <p>The vanilla drink is always cancelled, so a D&D potion never applies a Minecraft potion effect.
 */
public class ConsumableListener implements Listener {

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        // Only a click at open air. Clicking a BLOCK is left alone so holding a potion never eats a
        // chest, door or annotated-object click — the lesson from the character sheet swallowing
        // chest clicks in the 2026-09-16 playtest.
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_AIR) return;

        DndItem potion = drinkable(event.getItem());
        if (potion == null) return;

        event.setCancelled(true);      // never the vanilla drink — HP only changes through the command
        if (event.getHand() != EquipmentSlot.HAND) return; // one prompt per click, not one per hand
        DrinkCommand.promptRoll(event.getPlayer(), potion);
    }

    /** Belt and braces: if a drink somehow starts, stop it — HP only changes through the command. */
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (drinkable(event.getItem()) != null) event.setCancelled(true);
    }

    private static DndItem drinkable(ItemStack stack) {
        if (stack == null) return null;
        String id = ItemUtil.getItemId(stack);
        if (id == null) return null;
        DndItem item = ItemLoader.getItem(id);
        return (item != null && item.isDrinkable()) ? item : null;
    }
}
