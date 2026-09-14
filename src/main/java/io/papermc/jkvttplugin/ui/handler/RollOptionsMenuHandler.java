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
        String manualCmd = "/character check " + type + " " + value + " manualRoll ";
        String autoCmd = "/character check " + type + " " + value + " autoRoll";
        String advNote = switch (mode) {
            case ADVANTAGE -> " (advantage)";
            case DISADVANTAGE -> " (disadvantage)";
            default -> "";
        };
        String advHover = switch (mode) {
            case ADVANTAGE -> "\nAdvantage: the game rolls two d20 and keeps the higher.";
            case DISADVANTAGE -> "\nDisadvantage: the game rolls two d20 and keeps the lower.";
            default -> "";
        };
        player.sendMessage(Component.text("🎲 Roll " + info.displayName + advNote + " — ", NamedTextColor.GOLD)
                .append(Component.text("[click, then type your d20]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(manualCmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills: " + manualCmd + "<your d20> — the game adds " + bonusStr + "." + advHover))))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text("[or let the game roll]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand(autoCmd))
                        .hoverEvent(HoverEvent.showText(Component.text("The game rolls your d20" + (mode == RollMode.NORMAL ? "" : " (" + mode.name().toLowerCase() + ", 2d20)") + " and adds " + bonusStr + ".")))));
    }

    /**
     * Resolve a physical skill/check/save roll (via RollService) and broadcast it. Returns false if
     * physical mode still needs a die (the caller should prompt).
     */
    public static boolean resolvePhysical(CharacterSheet character, String type, String value, Integer roll, Integer total, boolean forceAuto) {
        RollInfo info = getRollInfo(character, type, value);
        // A DM-called check (#186) may carry advantage/disadvantage — apply it to the roll (so autoRoll
        // actually rolls 2d20 and keeps the right one), and report DM-first instead of broadcasting.
        io.papermc.jkvttplugin.dm.CheckManager.Pending pending =
                io.papermc.jkvttplugin.dm.CheckManager.peekPending(character.getPlayerId());
        io.papermc.jkvttplugin.combat.Advantage advantage = pending != null
                ? pending.advantage() : io.papermc.jkvttplugin.combat.Advantage.NONE;
        RollService.RollResult r = RollService.resolve(roll, total, info.bonus, info.breakdown,
                character.rerollsNat1(), advantage, forceAuto);
        if (r == null) return false;
        if (pending != null) {
            io.papermc.jkvttplugin.dm.CheckManager.takePending(character.getPlayerId());
            if (pending.contestId() != null) {
                reportContest(character, info, r.total(), pending);
            } else {
                io.papermc.jkvttplugin.dm.CheckManager.recordActive(character.getPlayerId(), info.displayName, r.total());
                reportDmCheck(character, info, r.total(), pending);
            }
            return true;
        }
        String dice = r.providedTotal() ? "total" : String.valueOf(r.d20());
        broadcastRoll(character, info, r.total(), dice, null, null);
        return true;
    }

    /** Report a DM-called check to the DM (with success/fail vs the private DC) + a Share button. */
    private static void reportDmCheck(CharacterSheet character, RollInfo info, int total,
                                      io.papermc.jkvttplugin.dm.CheckManager.Pending p) {
        String rollerName = character.getCharacterName();
        // The roller sees their own number (never the DC).
        Player owner = Bukkit.getPlayer(character.getPlayerId());
        if (owner != null) {
            owner.sendMessage(Component.text("You rolled " + info.displayName + ": " + total
                    + " — sent to the DM.", NamedTextColor.GRAY));
        }
        String shareText = rollerName + " rolled " + info.displayName + ": " + total;
        String token = io.papermc.jkvttplugin.dm.CheckManager.stashShare(shareText);
        Component verdict = Component.empty();
        if (p.dc() != null) {
            boolean success = total >= p.dc();
            verdict = Component.text(success ? "  ✔ SUCCESS" : "  ✘ FAIL",
                            success ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .append(Component.text(" (DC " + p.dc() + ")", NamedTextColor.DARK_GRAY));
        }
        Component dmMsg = Component.text("🎲 " + shareText, NamedTextColor.GOLD)
                .append(verdict)
                .append(Component.text("  "))
                .append(Component.text("[Share with players]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/dm check share " + token))
                        .hoverEvent(HoverEvent.showText(Component.text("Announce this roll to the table."))));
        Player dm = Bukkit.getPlayer(p.dmId());
        if (dm != null) dm.sendMessage(dmMsg);
        else Bukkit.broadcast(Component.text("🎲 " + shareText, NamedTextColor.YELLOW)); // DM offline → announce
    }

    /** Record one side of a contested check; when both sides are in, report the winner to the DM (#186). */
    private static void reportContest(CharacterSheet character, RollInfo info, int total,
                                      io.papermc.jkvttplugin.dm.CheckManager.Pending p) {
        Player owner = Bukkit.getPlayer(character.getPlayerId());
        if (owner != null) {
            owner.sendMessage(Component.text("You rolled " + info.displayName + ": " + total
                    + " — sent to the DM.", NamedTextColor.GRAY));
        }
        io.papermc.jkvttplugin.dm.CheckManager.Contest done =
                io.papermc.jkvttplugin.dm.CheckManager.recordContestRoll(p.contestId(), character.getPlayerId(), total);
        Player dm = Bukkit.getPlayer(p.dmId());
        if (done == null) { // still waiting on the other side
            if (dm != null) dm.sendMessage(Component.text(character.getCharacterName() + " rolled "
                    + info.displayName + ": " + total + " — waiting on the other side…", NamedTextColor.GRAY));
            return;
        }
        var a = done.sides.get(0);
        var b = done.sides.get(1);
        String result;
        if (a.total > b.total) result = a.name + " wins — " + a.label + " " + a.total + " vs " + b.label + " " + b.total;
        else if (b.total > a.total) result = b.name + " wins — " + b.label + " " + b.total + " vs " + a.label + " " + a.total;
        else result = "Tie (" + a.total + " vs " + b.total + ") — DM decides";
        String token = io.papermc.jkvttplugin.dm.CheckManager.stashShare(result);
        Component msg = Component.text("⚔ Contested: " + result, NamedTextColor.GOLD)
                .append(Component.text("  "))
                .append(Component.text("[Share with players]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/dm check share " + token))
                        .hoverEvent(HoverEvent.showText(Component.text("Announce the contest result to the table."))));
        if (dm != null) dm.sendMessage(msg);
        else Bukkit.broadcast(Component.text("⚔ " + result, NamedTextColor.YELLOW));
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