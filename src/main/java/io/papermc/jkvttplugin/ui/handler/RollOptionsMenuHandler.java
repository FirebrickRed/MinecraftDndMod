package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.SkillsMenu;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles roll options menu clicks - performs dice rolls and announces results.
 * Supports skills, ability checks, and saving throws using payload prefixes:
 * - "SKILL:STEALTH" for skill rolls
 * - "CHECK:STRENGTH" for ability checks
 * - "SAVE:DEXTERITY" for saving throws
 */
public class RollOptionsMenuHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID characterId, MenuAction action, String payload) {
        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
        if (character == null) return;

        // Handle cancel - return to skills menu
        if (action == MenuAction.CANCEL_ROLL) {
            SkillsMenu.open(player, characterId);
            return;
        }

        if (payload == null || !payload.contains(":")) return;

        // Close inventory after clicking
        player.closeInventory();

        // Parse payload: "TYPE:VALUE"
        String[] parts = payload.split(":", 2);
        String type = parts[0];
        String value = parts[1];

        // Route based on roll action and type
        switch (action) {
            case ROLL_NORMAL -> rollNormal(character, type, value);
            case ROLL_ADVANTAGE -> rollAdvantage(character, type, value);
            case ROLL_DISADVANTAGE -> rollDisadvantage(character, type, value);
            case SHOW_MODIFIER -> showModifier(character, type, value);
        }
    }

    /**
     * Roll 1d20 + bonus with breakdown
     */
    private static void rollNormal(CharacterSheet character, String type, String value) {
        int d20 = rollD20();
        RollInfo info = getRollInfo(character, type, value);
        int total = d20 + info.bonus;

        broadcastRoll(character, info, total, String.valueOf(d20), null, null);
    }

    /**
     * Roll 2d20 (take higher) + bonus with breakdown
     */
    private static void rollAdvantage(CharacterSheet character, String type, String value) {
        int d20_1 = rollD20();
        int d20_2 = rollD20();
        int higher = Math.max(d20_1, d20_2);
        RollInfo info = getRollInfo(character, type, value);
        int total = higher + info.bonus;

        broadcastRoll(character, info, total, "[" + d20_1 + ", " + d20_2 + "]", "advantage", NamedTextColor.GREEN);
    }

    /**
     * Roll 2d20 (take lower) + bonus with breakdown
     */
    private static void rollDisadvantage(CharacterSheet character, String type, String value) {
        int d20_1 = rollD20();
        int d20_2 = rollD20();
        int lower = Math.min(d20_1, d20_2);
        RollInfo info = getRollInfo(character, type, value);
        int total = lower + info.bonus;

        broadcastRoll(character, info, total, "[" + d20_1 + ", " + d20_2 + "]", "disadvantage", NamedTextColor.RED);
    }

    /**
     * Unified message builder for all roll types.
     * Formats: "[CharName] rolled Stealth: 18 (d20: 13 +3[DEX] +2[Prof])"
     *      or: "[CharName] rolled Stealth with advantage: 18 (d20: [15, 10] +3[DEX] +2[Prof])"
     */
    private static void broadcastRoll(CharacterSheet character, RollInfo info, int total,
                                       String diceResult, String rollType, NamedTextColor rollTypeColor) {
        Component message = Component.text(character.getCharacterName(), NamedTextColor.AQUA)
                .append(Component.text(" rolled ", NamedTextColor.GRAY))
                .append(Component.text(info.displayName, NamedTextColor.YELLOW));

        // Add "with advantage/disadvantage" if present
        if (rollType != null) {
            message = message.append(Component.text(" with ", NamedTextColor.GRAY))
                    .append(Component.text(rollType, rollTypeColor));
        }

        message = message.append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(total, NamedTextColor.WHITE))
                .append(Component.text(" (d20: " + diceResult + " ", NamedTextColor.DARK_GRAY))
                .append(Component.text(info.breakdown, NamedTextColor.GRAY))
                .append(Component.text(")", NamedTextColor.DARK_GRAY));

        Bukkit.broadcast(message);
    }

    /**
     * Just show the modifier with breakdown (for manual rolling with physical dice)
     */
    private static void showModifier(CharacterSheet character, String type, String value) {
        RollInfo info = getRollInfo(character, type, value);

        Component message = Component.text(character.getCharacterName(), NamedTextColor.AQUA)
                .append(Component.text("'s ", NamedTextColor.GRAY))
                .append(Component.text(info.displayName, NamedTextColor.YELLOW))
                .append(Component.text(" modifier: ", NamedTextColor.GRAY))
                .append(Component.text(info.breakdown, NamedTextColor.WHITE));

        Bukkit.broadcast(message);
    }

    /**
     * Helper to get roll information based on type
     */
    private static RollInfo getRollInfo(CharacterSheet character, String type, String value) {
        return switch (type) {
            case "SKILL" -> {
                Skill skill = Skill.valueOf(value);
                yield new RollInfo(
                        skill.getDisplayName(),
                        character.getSkillBonus(skill),
                        character.getSkillBonusBreakdown(skill)
                );
            }
            case "CHECK" -> {
                Ability ability = Ability.valueOf(value);
                yield new RollInfo(
                        ability.getAbbreviation() + " check",
                        character.getModifier(ability),
                        character.getAbilityCheckBreakdown(ability)
                );
            }
            case "SAVE" -> {
                Ability ability = Ability.valueOf(value);
                yield new RollInfo(
                        ability.getAbbreviation() + " save",
                        character.getSavingThrowBonus(ability),
                        character.getSaveBreakdown(ability)
                );
            }
            default -> throw new IllegalArgumentException("Unknown roll type: " + type);
        };
    }

    /**
     * Roll a d20 (1-20) using the DiceRoller utility
     */
    private static int rollD20() {
        return DiceRoller.rollDice(1, 20);
    }

    /**
     * Helper record to bundle roll information
     */
    private record RollInfo(String displayName, int bonus, String breakdown) {}
}