package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.LoreBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Roll options menu - shown when player clicks a skill.
 * Offers normal roll, advantage, disadvantage, show modifier only, or cancel.
 */
public class RollOptionsMenu {
    private RollOptionsMenu() {}

    /**
     * Opens the roll options menu for a specific skill.
     *
     * @param player The player opening the menu
     * @param characterId The character's UUID
     * @param skill The skill being rolled
     */
    public static void open(Player player, UUID characterId, Skill skill) {
        player.openInventory(build(player, characterId, skill));
    }

    public static Inventory build(Player player, UUID characterId, Skill skill) {
        // Get character to calculate breakdown
        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
        if (character == null) {
            throw new IllegalStateException("Character not found for player " + player.getName());
        }

        int totalBonus = character.getSkillBonus(skill);
        String skillDisplayName = skill.getDisplayName();
        String sign = totalBonus >= 0 ? "+" : "";

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.ROLL_OPTIONS_MENU, characterId),
                27, // 3 rows
                Component.text("Roll " + skillDisplayName + " " + sign + totalBonus + "?")
        );

        // Payload is just the skill enum name (e.g., "STEALTH")
        // Handler will look up character and get breakdown
        String payload = skill.name();

        // Row 1: Roll options
        // Slot 10: Normal Roll
        ItemStack normalRoll = ItemUtil.createActionItem(
                Material.PAPER,
                Component.text("Normal Roll", NamedTextColor.WHITE),
                LoreBuilder.create()
                        .addLine("Roll 1d20 " + sign + totalBonus, NamedTextColor.GRAY)
                        .build(),
                MenuAction.ROLL_NORMAL,
                payload
        );
        inventory.setItem(10, normalRoll);

        // Slot 12: Advantage
        ItemStack advantage = ItemUtil.createActionItem(
                Material.LIME_DYE,
                Component.text("Roll with Advantage", NamedTextColor.GREEN),
                LoreBuilder.create()
                        .addLine("Roll 2d20 (take higher) " + sign + totalBonus, NamedTextColor.GRAY)
                        .build(),
                MenuAction.ROLL_ADVANTAGE,
                payload
        );
        inventory.setItem(12, advantage);

        // Slot 14: Disadvantage
        ItemStack disadvantage = ItemUtil.createActionItem(
                Material.RED_DYE,
                Component.text("Roll with Disadvantage", NamedTextColor.RED),
                LoreBuilder.create()
                        .addLine("Roll 2d20 (take lower) " + sign + totalBonus, NamedTextColor.GRAY)
                        .build(),
                MenuAction.ROLL_DISADVANTAGE,
                payload
        );
        inventory.setItem(14, disadvantage);

        // Row 2: Utility options
        // Slot 19: Show Modifier Only
        ItemStack showModifier = ItemUtil.createActionItem(
                Material.BOOK,
                Component.text("Show Modifier Only", NamedTextColor.YELLOW),
                LoreBuilder.create()
                        .addLine("Display " + sign + totalBonus + " in chat", NamedTextColor.GRAY)
                        .addLine("For manual rolling with physical dice", NamedTextColor.DARK_GRAY)
                        .build(),
                MenuAction.SHOW_MODIFIER,
                payload
        );
        inventory.setItem(19, showModifier);

        // Slot 21: Cancel
        ItemStack cancel = ItemUtil.createActionItem(
                Material.BARRIER,
                Component.text("Cancel", NamedTextColor.RED),
                LoreBuilder.create()
                        .addLine("Return to skills menu", NamedTextColor.GRAY)
                        .build(),
                MenuAction.CANCEL_ROLL,
                null
        );
        inventory.setItem(21, cancel);

        return inventory;
    }

}