package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.enums.Ability;
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
 * Roll options menu - shown when player clicks a skill, ability check, or saving throw.
 * Offers normal roll, advantage, disadvantage, show modifier only, or cancel.
 */
public class RollOptionsMenu {
    private RollOptionsMenu() {}

    /**
     * Opens the roll options menu for a specific skill.
     */
    public static void openForSkillCheck(Player player, UUID characterId, Skill skill) {
        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
        if (character == null) {
            throw new IllegalStateException("Character not found for player " + player.getName());
        }

        int bonus = character.getSkillBonus(skill);
        String title = "Roll " + skill.getDisplayName() + " " + formatBonus(bonus) + "?";
        String payload = "SKILL:" + skill.name();

        player.openInventory(buildRollMenu(characterId, title, bonus, payload));
    }

    /**
     * Opens the roll options menu for an ability check.
     */
    public static void openForAbilityCheck(Player player, UUID characterId, Ability ability) {
        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
        if (character == null) {
            throw new IllegalStateException("Character not found for player " + player.getName());
        }

        int bonus = character.getModifier(ability);
        String title = "Roll " + ability.getAbbreviation() + " Check " + formatBonus(bonus) + "?";
        String payload = "CHECK:" + ability.name();

        player.openInventory(buildRollMenu(characterId, title, bonus, payload));
    }

    /**
     * Opens the roll options menu for a saving throw.
     */
    public static void openForSavingThrow(Player player, UUID characterId, Ability ability) {
        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
        if (character == null) {
            throw new IllegalStateException("Character not found for player " + player.getName());
        }

        int bonus = character.getSavingThrowBonus(ability);
        String title = "Roll " + ability.getAbbreviation() + " Save " + formatBonus(bonus) + "?";
        String payload = "SAVE:" + ability.name();

        player.openInventory(buildRollMenu(characterId, title, bonus, payload));
    }

    /**
     * Unified builder for all roll options menus (skills, ability checks, saving throws).
     * Constructs the same menu layout with different title, bonus, and payload.
     */
    private static Inventory buildRollMenu(UUID characterId, String title, int bonus, String payload) {
        String sign = bonus >= 0 ? "+" : "";

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.ROLL_OPTIONS_MENU, characterId),
                27,
                Component.text(title)
        );

        // Row 1: Roll options
        ItemStack normalRoll = ItemUtil.createActionItem(
                Material.PAPER,
                Component.text("Normal Roll", NamedTextColor.WHITE),
                LoreBuilder.create()
                        .addLine("Roll 1d20 " + sign + bonus, NamedTextColor.GRAY)
                        .build(),
                MenuAction.ROLL_NORMAL,
                payload
        );
        inventory.setItem(10, normalRoll);

        ItemStack advantage = ItemUtil.createActionItem(
                Material.LIME_DYE,
                Component.text("Roll with Advantage", NamedTextColor.GREEN),
                LoreBuilder.create()
                        .addLine("Roll 2d20 (take higher) " + sign + bonus, NamedTextColor.GRAY)
                        .build(),
                MenuAction.ROLL_ADVANTAGE,
                payload
        );
        inventory.setItem(12, advantage);

        ItemStack disadvantage = ItemUtil.createActionItem(
                Material.RED_DYE,
                Component.text("Roll with Disadvantage", NamedTextColor.RED),
                LoreBuilder.create()
                        .addLine("Roll 2d20 (take lower) " + sign + bonus, NamedTextColor.GRAY)
                        .build(),
                MenuAction.ROLL_DISADVANTAGE,
                payload
        );
        inventory.setItem(14, disadvantage);

        // Row 2: Utility options
        ItemStack showModifier = ItemUtil.createActionItem(
                Material.BOOK,
                Component.text("Show Modifier Only", NamedTextColor.YELLOW),
                LoreBuilder.create()
                        .addLine("Display " + sign + bonus + " in chat", NamedTextColor.GRAY)
                        .addLine("For manual rolling with physical dice", NamedTextColor.DARK_GRAY)
                        .build(),
                MenuAction.SHOW_MODIFIER,
                payload
        );
        inventory.setItem(19, showModifier);

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

    /**
     * Helper to format bonus with + or - sign.
     */
    private static String formatBonus(int bonus) {
        return (bonus >= 0 ? "+" : "") + bonus;
    }
}