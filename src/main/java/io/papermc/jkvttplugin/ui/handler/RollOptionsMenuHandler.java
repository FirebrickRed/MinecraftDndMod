package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.combat.RollService;
import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.ui.menu.SkillsMenu;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
            case ROLL_NORMAL -> rollOrPrompt(player, character, type, value, RollMode.NORMAL);
            case ROLL_ADVANTAGE -> rollOrPrompt(player, character, type, value, RollMode.ADVANTAGE);
            case ROLL_DISADVANTAGE -> rollOrPrompt(player, character, type, value, RollMode.DISADVANTAGE);
            case SHOW_MODIFIER -> showModifier(character, type, value);
        }
    }

    /** Physical mode: prompt the player to roll in chat. Auto mode: roll it for them (as before). */
    private static void rollOrPrompt(Player player, CharacterSheet character, String type, String value, RollMode mode) {
        if (PluginConfig.isAutoRoll()) {
            performRoll(character, type, value, mode);
        } else {
            promptSkillRoll(player, character, type, value, mode);
        }
    }

    /** Send a clickable chat prompt asking the player to roll this check physically. */
    public static void promptSkillRoll(Player player, CharacterSheet character, String type, String value, RollMode mode) {
        RollInfo info = getRollInfo(character, type, value);
        String bonusStr = info.bonus >= 0 ? "+" + info.bonus : String.valueOf(info.bonus);
        String cmd = "/character check " + type + " " + value + " --roll ";
        String advNote = switch (mode) {
            case ADVANTAGE -> " (advantage — roll two, use the higher)";
            case DISADVANTAGE -> " (disadvantage — roll two, use the lower)";
            default -> "";
        };
        player.sendMessage(Component.text("🎲 Roll " + info.displayName + advNote + " — ", NamedTextColor.GOLD)
                .append(Component.text("[click, then type your d20]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(cmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd + "<your d20> — the game adds " + bonusStr + ".")))));
    }

    /**
     * Resolve a physical skill/check/save roll (via RollService) and broadcast it. Returns false if
     * physical mode still needs a die (the caller should prompt).
     */
    public static boolean resolvePhysical(CharacterSheet character, String type, String value, Integer roll, Integer total) {
        RollInfo info = getRollInfo(character, type, value);
        RollService.RollResult r = RollService.resolve(roll, total, info.bonus, info.breakdown);
        if (r == null) return false;
        String dice = r.providedTotal() ? "total" : String.valueOf(r.d20());
        broadcastRoll(character, info, r.total(), dice, null, null);
        return true;
    }

    /** Roll mode for programmatic rolls outside the menu flow (Issue #61 - /check). */
    public enum RollMode { NORMAL, ADVANTAGE, DISADVANTAGE }

    /**
     * Perform a roll for a character without opening the menu (Issue #61).
     * @param type  "SKILL", "CHECK", or "SAVE"
     * @param value the enum name (e.g. "STEALTH", "STRENGTH")
     */
    public static void performRoll(CharacterSheet character, String type, String value, RollMode mode) {
        switch (mode) {
            case NORMAL -> rollNormal(character, type, value);
            case ADVANTAGE -> rollAdvantage(character, type, value);
            case DISADVANTAGE -> rollDisadvantage(character, type, value);
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