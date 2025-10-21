package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.SkillsMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.UUID;

/**
 * Handles roll options menu clicks - performs dice rolls and announces results.
 */
public class RollOptionsMenuHandler implements MenuClickHandler {
    private static final Random RANDOM = new Random();

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID characterId, MenuAction action, String payload) {
        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
        if (character == null) return;

        // Handle cancel - return to skills menu
        if (action == MenuAction.CANCEL_ROLL) {
            SkillsMenu.open(player, characterId);
            return;
        }

        // Parse payload: skill enum name (e.g., "STEALTH")
        if (payload == null) return;

        Skill skill;
        try {
            skill = Skill.valueOf(payload);
        } catch (IllegalArgumentException e) {
            return;
        }

        // Close inventory after clicking
        player.closeInventory();

        // Handle different roll types
        switch (action) {
            case ROLL_NORMAL -> rollNormal(character, skill);
            case ROLL_ADVANTAGE -> rollAdvantage(character, skill);
            case ROLL_DISADVANTAGE -> rollDisadvantage(character, skill);
            case SHOW_MODIFIER -> showModifier(character, skill);
        }
    }

    /**
     * Roll 1d20 + bonus with breakdown
     */
    private static void rollNormal(CharacterSheet character, Skill skill) {
        int d20 = rollD20();
        int bonus = character.getSkillBonus(skill);
        int total = d20 + bonus;
        String breakdown = character.getSkillBonusBreakdown(skill);

        // Broadcast to all players
        // Format: "[CharName] rolled Stealth: 18 (d20: 13 +3 +2 prof)"
        Component message = Component.text(character.getCharacterName(), NamedTextColor.AQUA)
                .append(Component.text(" rolled ", NamedTextColor.GRAY))
                .append(Component.text(skill.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(total, NamedTextColor.WHITE))
                .append(Component.text(" (d20: " + d20 + " ", NamedTextColor.DARK_GRAY))
                .append(Component.text(breakdown, NamedTextColor.GRAY))
                .append(Component.text(")", NamedTextColor.DARK_GRAY));

        Bukkit.broadcast(message);
    }

    /**
     * Roll 2d20 (take higher) + bonus with breakdown
     */
    private static void rollAdvantage(CharacterSheet character, Skill skill) {
        int d20_1 = rollD20();
        int d20_2 = rollD20();
        int higher = Math.max(d20_1, d20_2);
        int bonus = character.getSkillBonus(skill);
        int total = higher + bonus;
        String breakdown = character.getSkillBonusBreakdown(skill);

        // Broadcast to all players
        Component message = Component.text(character.getCharacterName(), NamedTextColor.AQUA)
                .append(Component.text(" rolled ", NamedTextColor.GRAY))
                .append(Component.text(skill.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text(" with ", NamedTextColor.GRAY))
                .append(Component.text("advantage", NamedTextColor.GREEN))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(total, NamedTextColor.WHITE))
                .append(Component.text(" (d20: " + d20_1 + ", " + d20_2 + " ", NamedTextColor.DARK_GRAY))
                .append(Component.text(breakdown, NamedTextColor.GRAY))
                .append(Component.text(")", NamedTextColor.DARK_GRAY));

        Bukkit.broadcast(message);
    }

    /**
     * Roll 2d20 (take lower) + bonus with breakdown
     */
    private static void rollDisadvantage(CharacterSheet character, Skill skill) {
        int d20_1 = rollD20();
        int d20_2 = rollD20();
        int lower = Math.min(d20_1, d20_2);
        int bonus = character.getSkillBonus(skill);
        int total = lower + bonus;
        String breakdown = character.getSkillBonusBreakdown(skill);

        // Broadcast to all players
        Component message = Component.text(character.getCharacterName(), NamedTextColor.AQUA)
                .append(Component.text(" rolled ", NamedTextColor.GRAY))
                .append(Component.text(skill.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text(" with ", NamedTextColor.GRAY))
                .append(Component.text("disadvantage", NamedTextColor.RED))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(total, NamedTextColor.WHITE))
                .append(Component.text(" (d20: " + d20_1 + ", " + d20_2 + " ", NamedTextColor.DARK_GRAY))
                .append(Component.text(breakdown, NamedTextColor.GRAY))
                .append(Component.text(")", NamedTextColor.DARK_GRAY));

        Bukkit.broadcast(message);
    }

    /**
     * Just show the modifier with breakdown (for manual rolling with physical dice)
     */
    private static void showModifier(CharacterSheet character, Skill skill) {
        String breakdown = character.getSkillBonusBreakdown(skill);

        Component message = Component.text(character.getCharacterName(), NamedTextColor.AQUA)
                .append(Component.text("'s ", NamedTextColor.GRAY))
                .append(Component.text(skill.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text(" modifier: ", NamedTextColor.GRAY))
                .append(Component.text(breakdown, NamedTextColor.WHITE));

        Bukkit.broadcast(message);
    }

    /**
     * Roll a d20 (1-20)
     */
    private static int rollD20() {
        return RANDOM.nextInt(20) + 1;
    }
}