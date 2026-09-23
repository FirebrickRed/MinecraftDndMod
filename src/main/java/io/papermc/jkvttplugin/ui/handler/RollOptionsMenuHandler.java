package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.combat.RollService;
import io.papermc.jkvttplugin.config.PluginConfig;
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
 * Out-of-combat skill, check, save and tool rolls for a character: the sheet's chat roll line
 * ({@link #offerRoll}), /character check, and DM-called checks (#186). Types are "SKILL", "CHECK",
 * "SAVE" and "TOOL", with the skill or ability as the value.
 */
public class RollOptionsMenuHandler {

    /**
     * Clicking a skill, check or save on the sheet: one chat line instead of a second menu,
     * "🎲 Persuasion +8 (+4[CHA] +4[Prof ×2]) [Normal] [Advantage] [Disadvantage]". The bonus and
     * where it comes from are right there, and each button rolls (or, with physical dice, prompts)
     * exactly as the old menu's buttons did. A penalty (armor, a condition) is named up front.
     */
    public static void offerRoll(Player player, CharacterSheet character, String type, String value) {
        RollInfo info = getRollInfo(character, type, value);
        player.closeInventory();
        var reusable = net.kyori.adventure.text.event.ClickCallback.Options.builder()
                .uses(net.kyori.adventure.text.event.ClickCallback.UNLIMITED_USES)
                .lifetime(java.time.Duration.ofMinutes(10)).build();
        Component line = Component.text("🎲 " + info.displayName + " " + (info.bonus >= 0 ? "+" : "") + info.bonus + " ", NamedTextColor.GOLD)
                .append(Component.text("(" + info.breakdown.trim() + ") ", NamedTextColor.GRAY));
        String penalty = penaltyReason(character, type, value);
        if (penalty != null) line = line.append(Component.text("↯ disadvantage: " + penalty + " ", NamedTextColor.RED));
        for (RollMode mode : RollMode.values()) {
            String label = switch (mode) { case NORMAL -> "[Normal]"; case ADVANTAGE -> "[Advantage]"; case DISADVANTAGE -> "[Disadvantage]"; };
            NamedTextColor color = switch (mode) { case NORMAL -> NamedTextColor.WHITE; case ADVANTAGE -> NamedTextColor.GREEN; case DISADVANTAGE -> NamedTextColor.RED; };
            line = line.append(Component.text(label, color, TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(Component.text(switch (mode) {
                        case NORMAL -> "Roll one d20";
                        case ADVANTAGE -> "Roll two d20 and keep the higher";
                        case DISADVANTAGE -> "Roll two d20 and keep the lower";
                    })))
                    .clickEvent(ClickEvent.callback(a -> rollOrPrompt(player, character, type, value, mode), reusable)))
                    .append(Component.text(" "));
        }
        player.sendMessage(line);
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
        // Carry the menu's adv/dis pick in the command, or physical mode rolls it normal. The manual
        // form puts it before manualRoll so the player's typed d20 still lands last.
        String modeWord = switch (mode) { case ADVANTAGE -> "adv "; case DISADVANTAGE -> "dis "; default -> ""; };
        String manualCmd = "/character check " + type + " " + value + " " + modeWord + "manualRoll ";
        String autoCmd = "/character check " + type + " " + value + " " + modeWord + "autoRoll";
        mode = withPenalties(character, type, value, mode); // show armor/condition disadvantage before they roll (#209, #175)
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
                        .clickEvent(ClickEvent.suggestCommand(autoCmd))
                        .hoverEvent(HoverEvent.showText(Component.text("The game rolls your d20" + (mode == RollMode.NORMAL ? "" : " (" + mode.name().toLowerCase() + ", 2d20)") + " and adds " + bonusStr + ".")))));
    }

    /** The "[advantage: 15/10]" note RollService puts on a roll it made with two dice. */
    private static final java.util.regex.Pattern ADV_DICE =
            java.util.regex.Pattern.compile("\\[(advantage|disadvantage): (\\d+)/(\\d+)\\]", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Resolve a physical skill/check/save roll (via RollService) and broadcast it. Returns false if
     * physical mode still needs a die (the caller should prompt).
     */
    public static boolean resolvePhysical(CharacterSheet character, String type, String value, Integer roll, Integer total,
                                          boolean forceAuto, io.papermc.jkvttplugin.combat.Advantage chosen) {
        RollInfo info = getRollInfo(character, type, value);
        // A DM-called check (#186) may carry advantage/disadvantage — apply it to the roll (so autoRoll
        // actually rolls 2d20 and keeps the right one), and report DM-first instead of broadcasting.
        io.papermc.jkvttplugin.dm.CheckManager.Pending pending =
                io.papermc.jkvttplugin.dm.CheckManager.peekPending(character.getPlayerId());
        io.papermc.jkvttplugin.combat.Advantage advantage = pending != null
                ? pending.advantage() : io.papermc.jkvttplugin.combat.Advantage.NONE;
        if (pending == null && chosen != null) advantage = chosen; // the sheet menu's own adv/dis pick
        // Unproficient armor (STR/DEX, #209) or a condition (Poisoned, #175) → disadvantage.
        String penalty = penaltyReason(character, type, value);
        if (penalty != null) {
            advantage = advantage.with(false);
            Player owner = Bukkit.getPlayer(character.getPlayerId());
            if (owner != null) owner.sendMessage(Component.text("↯ Disadvantage: " + penalty + ".", NamedTextColor.RED));
        }
        RollService.RollResult r = RollService.resolve(roll, total, info.bonus, info.breakdown,
                character.rerollsNat1(), advantage, forceAuto);
        if (r == null) return false;
        if (pending != null) {
            io.papermc.jkvttplugin.dm.CheckManager.takePending(character.getPlayerId());
            if (pending.contestId() != null) {
                reportContest(character, info, r.total(), r.breakdown(), pending);
            } else {
                io.papermc.jkvttplugin.dm.CheckManager.recordActive(character.getPlayerId(), info.displayName, r.total());
                reportDmCheck(character, info, r.total(), r.breakdown(), pending);
                if ("TOOL".equals(type)) maybeBreakThievesTools(character, value, r.total(), pending);
            }
            return true;
        }
        String dice = r.providedTotal() ? "total" : String.valueOf(r.d20());
        // Advantage or disadvantage the game rolled: show both dice and say which, or a real advantage
        // roll reads exactly like a normal one (the playtest thought adv was being ignored).
        java.util.regex.Matcher both = ADV_DICE.matcher(r.breakdown());
        if (both.find()) {
            boolean adv = both.group(1).equalsIgnoreCase("advantage");
            broadcastRoll(character, info, r.total(), "[" + both.group(2) + ", " + both.group(3) + "]",
                    adv ? "advantage" : "disadvantage", adv ? NamedTextColor.GREEN : NamedTextColor.RED);
            return true;
        }
        broadcastRoll(character, info, r.total(), dice, null, null);
        return true;
    }

    /** The ability a roll of this type uses: a skill's ability, the check/save ability, a tool check's. */
    private static Ability abilityOf(String type, String value) {
        return switch (type) {
            case "SKILL" -> Skill.valueOf(value).getAbility();
            case "CHECK", "SAVE" -> Ability.valueOf(value);
            case "TOOL" -> Ability.valueOf(value.substring(0, value.indexOf(':')).toUpperCase());
            default -> null;
        };
    }

    /**
     * Why this roll is at disadvantage, or null: unproficient armor on a STR/DEX roll (#209), or a
     * condition on the character (Poisoned on checks, Restrained on DEX saves, #175). One place, so
     * the prompt, the roll and the message all agree.
     */
    private static String penaltyReason(CharacterSheet character, String type, String value) {
        Ability a = abilityOf(type, value);
        if (a != null && character.armorPenaltyApplies(a)) return character.armorPenaltyReason();
        return character.conditionDisadvantageOn("SAVE".equals(type), a);
    }

    /** Fold a disadvantage into a menu roll mode (5e: advantage + disadvantage → normal). */
    private static RollMode withPenalties(CharacterSheet character, String type, String value, RollMode mode) {
        if (penaltyReason(character, type, value) == null) return mode;
        return switch (mode) {
            case ADVANTAGE -> RollMode.NORMAL;
            default -> RollMode.DISADVANTAGE;
        };
    }

    /** Report a DM-called check to the DM (with success/fail vs the private DC) + a Share button. */
    private static void reportDmCheck(CharacterSheet character, RollInfo info, int total, String work,
                                      io.papermc.jkvttplugin.dm.CheckManager.Pending p) {
        String rollerName = character.getCharacterName();
        // The roller sees their own number (never the DC).
        Player owner = Bukkit.getPlayer(character.getPlayerId());
        if (owner != null) {
            owner.sendMessage(Component.text("You rolled " + info.displayName + ": " + work
                    + " — sent to the DM.", NamedTextColor.GRAY));
        }
        // Show the work, not just the number: "d20(16) +3[DEX] +2[Prof] = 21".
        String shareText = rollerName + " rolled " + info.displayName + ": " + work;
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

    /**
     * A graded thieves' tools check may use up one set, per {@code objects.thieves_tools_break}
     * (#210): {@code on_fail} (default, BG3-style), {@code always}, or {@code never} (RAW). Needs a DC,
     * since an ungraded check has no pass or fail. Removes the item from the live inventory and the
     * sheet's recorded gear together, and tells the player and the DMs.
     */
    private static void maybeBreakThievesTools(CharacterSheet character, String value, int total,
                                               io.papermc.jkvttplugin.dm.CheckManager.Pending p) {
        String tool = io.papermc.jkvttplugin.data.model.enums.ToolRegistry.idOf(value.substring(value.indexOf(':') + 1));
        if (!"thieves_tools".equals(tool) || p.dc() == null) return;
        boolean breaks = switch (io.papermc.jkvttplugin.config.PluginConfig.getThievesToolsBreak()) {
            case NEVER -> false;
            case ALWAYS -> true;
            case ON_FAIL -> total < p.dc();
        };
        if (!breaks) return;

        Player owner = Bukkit.getPlayer(character.getPlayerId());
        if (owner == null) return;
        org.bukkit.inventory.ItemStack[] contents = owner.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (!tool.equalsIgnoreCase(io.papermc.jkvttplugin.util.ItemUtil.getItemId(contents[i]))) continue;
            org.bukkit.inventory.ItemStack stack = contents[i];
            if (stack.getAmount() > 1) stack.setAmount(stack.getAmount() - 1);
            else owner.getInventory().setItem(i, null);
            character.removeEquipmentItem(tool, 1);
            owner.sendMessage(Component.text("🔧 Your thieves' tools snap — that set is ruined.", NamedTextColor.RED));
            Component note = Component.text("🔧 " + character.getCharacterName() + "'s thieves' tools broke ("
                    + (total < p.dc() ? "failed" : "used") + ", DC " + p.dc() + ").", NamedTextColor.GRAY);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (io.papermc.jkvttplugin.dm.DMManager.isDM(online)) online.sendMessage(note);
            }
            return;
        }
        // Not carrying any: nothing to break. The DM was already told "NOT carrying them" when calling it.
    }

    /** Record one side of a contested check; when both sides are in, report the winner to the DM (#186). */
    private static void reportContest(CharacterSheet character, RollInfo info, int total, String work,
                                      io.papermc.jkvttplugin.dm.CheckManager.Pending p) {
        Player owner = Bukkit.getPlayer(character.getPlayerId());
        if (owner != null) {
            owner.sendMessage(Component.text("You rolled " + info.displayName + ": " + work
                    + " — sent to the DM.", NamedTextColor.GRAY));
        }
        recordContestSide(p.contestId(), character.getPlayerId(), character.getCharacterName(), info.displayName, total, work);
    }

    /**
     * One side of a contest has rolled: a character through the roll menu, or the DM for an NPC
     * ({@code /dm check npcroll}). When both are in, the winner goes to the DM with [Share].
     */
    public static void recordContestSide(String contestId, java.util.UUID sideKey, String name, String label, int total, String work) {
        io.papermc.jkvttplugin.dm.CheckManager.Contest pendingContest = io.papermc.jkvttplugin.dm.CheckManager.getContest(contestId);
        if (pendingContest == null) return; // already resolved or expired
        Player dm = pendingContest.dmId != null ? Bukkit.getPlayer(pendingContest.dmId) : null;
        io.papermc.jkvttplugin.dm.CheckManager.Contest done =
                io.papermc.jkvttplugin.dm.CheckManager.recordContestRoll(contestId, sideKey, total);
        if (done == null) { // still waiting on the other side
            if (dm != null) dm.sendMessage(Component.text(name + " rolled " + label + ": " + work
                    + " — waiting on the other side…", NamedTextColor.GRAY));
            return;
        }
        var a = done.sides.get(0);
        var b = done.sides.get(1);
        String result;
        if (a.total > b.total) result = a.name + " wins — " + a.label + " " + a.total + " vs " + b.label + " " + b.total;
        else if (b.total > a.total) result = b.name + " wins — " + b.label + " " + b.total + " vs " + a.label + " " + a.total;
        // PHB p.174: on a tie the situation stays as it was before the contest.
        else result = "Tie (" + a.total + " vs " + b.total + ") — nothing changes";
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
        switch (withPenalties(character, type, value, mode)) { // armor (#209) or a condition (#175) → disadvantage
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
            case "TOOL" -> {
                // "DEXTERITY:thieves_tools" — an ability check that adds tool proficiency (#207).
                int colon = value.indexOf(':');
                Ability ability = Ability.valueOf(value.substring(0, colon).toUpperCase());
                String tool = value.substring(colon + 1);
                yield new RollInfo(
                        io.papermc.jkvttplugin.data.model.enums.ToolRegistry.displayName(tool) + " (" + ability.getAbbreviation() + ")",
                        character.getToolCheckBonus(ability, tool),
                        character.getToolCheckBreakdown(ability, tool)
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