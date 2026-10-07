package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.combat.RollPrompt;
import io.papermc.jkvttplugin.combat.RollService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
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
    /**
     * Bardic Inspiration after the roll (#40): the holder may add it; the DM rules on the new total. A
     * roll the DM called goes to the DM too; the player's own private roll stays theirs.
     */
    private static void offerInspiration(CharacterSheet character, RollInfo info, RollService.RollResult r, boolean dmCalled) {
        Player owner = Bukkit.getPlayer(character.getPlayerId());
        if (owner == null) return;
        io.papermc.jkvttplugin.combat.InspirationPrompt.offer(character, info.displayName, r.total(), null,
                dmCalled ? io.papermc.jkvttplugin.combat.InspirationPrompt.rollerAndDms(owner) : owner::sendMessage);
    }

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
        String boon = boonReason(character, type, value);
        if (boon != null) line = line.append(Component.text("↑ advantage: " + boon + " ", NamedTextColor.GREEN));
        // What the sheet already knows decides the roll, so the buttons don't offer what can't happen
        // (playtest: Poisoned said "disadvantage" and still offered [Normal] and [Advantage]). One of each
        // cancels out however many there are (PHB p.173), so the only open question is the other side.
        boolean forcedDis = penalty != null && boon == null, forcedAdv = boon != null && penalty == null;
        List<RollMode> offered = penalty != null && boon != null ? List.of(RollMode.NORMAL)
                : forcedDis ? List.of(RollMode.NORMAL, RollMode.ADVANTAGE)
                : forcedAdv ? List.of(RollMode.NORMAL, RollMode.DISADVANTAGE)
                : List.of(RollMode.values());
        for (RollMode mode : offered) {
            boolean cancels = (forcedDis && mode == RollMode.ADVANTAGE) || (forcedAdv && mode == RollMode.DISADVANTAGE);
            RollMode rolled = withPenalties(character, type, value, mode); // what this button really rolls
            String label = cancels ? (forcedDis ? "[I also have advantage]" : "[I also have disadvantage]")
                    : offered.size() < 3 ? (rolled == RollMode.NORMAL ? "[Roll]" : "[Roll with " + rolled.name().toLowerCase() + "]")
                    : switch (mode) { case NORMAL -> "[Normal]"; case ADVANTAGE -> "[Advantage]"; case DISADVANTAGE -> "[Disadvantage]"; };
            NamedTextColor color = cancels ? NamedTextColor.GRAY : switch (rolled) {
                case NORMAL -> NamedTextColor.WHITE; case ADVANTAGE -> NamedTextColor.GREEN; case DISADVANTAGE -> NamedTextColor.RED; };
            String hover = (cancels ? "From something the game doesn't know about (the DM said so, an ally's help). "
                    + "The two cancel out: " : "") + switch (rolled) {
                        case NORMAL -> cancels ? "roll one d20" : "Roll one d20";
                        case ADVANTAGE -> "Roll two d20 and keep the higher";
                        case DISADVANTAGE -> "Roll two d20 and keep the lower";
                    };
            line = line.append(Component.text(label, color, TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(Component.text(hover)))
                    .clickEvent(ClickEvent.callback(a -> promptSkillRoll(player, character, type, value, mode), reusable)))
                    .append(Component.text(" "));
        }
        player.sendMessage(line);
    }

    /** Send a clickable chat prompt asking the player to roll this check physically. */
    public static void promptSkillRoll(Player player, CharacterSheet character, String type, String value, RollMode mode) {
        promptSkillRoll(player, character, type, value, mode, null);
    }

    /**
     * @param requestId the save request this prompt is for (#272), or null. It rides in the buttons' command,
     *                  so the answer is tied to that request and nothing else can be taken for it.
     */
    public static void promptSkillRoll(Player player, CharacterSheet character, String type, String value, RollMode mode,
                                       String requestId) {
        RollInfo info = getRollInfo(character, type, value);
        // Carry the menu's adv/dis pick in the command, or physical mode rolls it normal. The manual
        // form puts it before manualRoll so the player's typed d20 still lands last.
        String modeWord = switch (mode) { case ADVANTAGE -> "adv "; case DISADVANTAGE -> "dis "; default -> ""; };
        String base = "/character check " + type + " " + value + " " + (requestId != null ? "request " + requestId + " " : "") + modeWord;
        mode = withPenalties(character, type, value, mode, requestId); // show armor/condition disadvantage before they roll (#209, #175)
        String dice = RollPrompt.d20(switch (mode) {
            case ADVANTAGE -> io.papermc.jkvttplugin.combat.Advantage.ADVANTAGE;
            case DISADVANTAGE -> io.papermc.jkvttplugin.combat.Advantage.DISADVANTAGE;
            default -> io.papermc.jkvttplugin.combat.Advantage.NONE;
        });
        String advNote = mode == RollMode.NORMAL ? "" : " (" + mode.name().toLowerCase() + ")";
        player.sendMessage(RollPrompt.line("🎲 Roll " + info.displayName + advNote + ":", NamedTextColor.GOLD,
                base, dice, info.breakdown));
    }

    /**
     * Resolve a physical skill/check/save roll (via RollService) and report it. Returns false if
     * physical mode still needs a die (the caller should prompt).
     */
    public static boolean resolvePhysical(CharacterSheet character, String type, String value, Integer roll, Integer total,
                                          boolean forceAuto, io.papermc.jkvttplugin.combat.Advantage chosen) {
        return resolvePhysical(character, type, value, roll, total, forceAuto, chosen, null);
    }

    /**
     * Which pending check this roll answers (#272). A roll carrying a save request's id answers that
     * request's check and no other; null with {@code settled} true means the request is gone (answered,
     * ruled, lapsed, or never this character's save). A roll with no id answers the player's current
     * check, unless that check belongs to a request: then it's just a roll of their own.
     */
    record Answering(io.papermc.jkvttplugin.dm.CheckManager.Pending pending, boolean settled) {}

    static Answering answering(CharacterSheet character, String type, String value, String answeredRequest) {
        if (answeredRequest != null) {
            io.papermc.jkvttplugin.dm.CheckManager.Pending p = io.papermc.jkvttplugin.dm.CheckManager.peekRequest(answeredRequest);
            io.papermc.jkvttplugin.combat.SaveOutcome.Request req =
                    io.papermc.jkvttplugin.combat.SaveOutcome.find(answeredRequest, character.getPlayerId());
            boolean thisSave = "SAVE".equals(type) && req != null && (req.ability() == null || req.ability().name().equals(value));
            return p != null && thisSave ? new Answering(p, false) : new Answering(null, true);
        }
        io.papermc.jkvttplugin.dm.CheckManager.Pending p = io.papermc.jkvttplugin.dm.CheckManager.peekPending(character.getPlayerId());
        return new Answering(p != null && p.requestId() != null ? null : p, false);
    }

    /** @param answeredRequest the save request id the roll's command carried, or null */
    public static boolean resolvePhysical(CharacterSheet character, String type, String value, Integer roll, Integer total,
                                          boolean forceAuto, io.papermc.jkvttplugin.combat.Advantage chosen, String answeredRequest) {
        RollInfo info = getRollInfo(character, type, value);
        // A DM-called check (#186) may carry advantage/disadvantage — apply it to the roll (so autoRoll
        // actually rolls 2d20 and keeps the right one), and report DM-first instead of broadcasting.
        Answering answering = answering(character, type, value, answeredRequest);
        if (answering.settled()) {
            Player owner = Bukkit.getServer() == null ? null : Bukkit.getPlayer(character.getPlayerId());
            if (owner != null) owner.sendMessage(Component.text("That save has already been settled.", NamedTextColor.GRAY));
            return true; // handled: nothing is rolled against a request that's gone
        }
        io.papermc.jkvttplugin.dm.CheckManager.Pending pending = answering.pending();
        String requestId = pending != null ? pending.requestId() : null;
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
        // An effect granting advantage (Rage on a STR check, #223).
        String boon = boonReason(character, type, value, requestId);
        if (boon != null) {
            advantage = advantage.with(true);
            Player owner = Bukkit.getPlayer(character.getPlayerId());
            if (owner != null) owner.sendMessage(Component.text("↑ Advantage: " + boon + ".", NamedTextColor.GREEN));
        }
        // An ally's Help in a fight (#176): advantage on the next ability check (not a save).
        io.papermc.jkvttplugin.combat.Combatant helped = !"SAVE".equals(type) ? combatantOf(character) : null;
        if (helped != null && helped.getHelpedByName() != null) {
            advantage = advantage.with(true);
            Player owner = Bukkit.getPlayer(character.getPlayerId());
            if (owner != null) owner.sendMessage(Component.text("↑ Advantage: helped by " + helped.getHelpedByName() + ".", NamedTextColor.GREEN));
        }
        RollService.RollResult r = RollService.resolve(roll, total, info.bonus, info.breakdown,
                character.rerollsNat1(), advantage, forceAuto);
        if (r == null) return false;
        if (helped != null) helped.clearHelp(); // the Help is used up by this check
        // Guidance on a check, Resistance on a save: once, and this roll was it (#225).
        io.papermc.jkvttplugin.combat.SpellEffects.useUp(character, "SAVE".equals(type)
                ? io.papermc.jkvttplugin.effect.ActiveEffect.SAVES : io.papermc.jkvttplugin.effect.ActiveEffect.CHECKS);
        // Studying an annotated block (#231): the result is the block's text, not a private roll report.
        // A check the DM called comes first.
        if (pending == null && io.papermc.jkvttplugin.dm.StudyInteraction.takeRoll(character, type, value, r, roll)) return true;
        if (pending != null) {
            io.papermc.jkvttplugin.dm.CheckManager.take(character.getPlayerId(), pending);
            if (pending.contestId() != null) {
                reportContest(character, info, r.total(), r.breakdown(), pending);
            } else if (pending.groupId() != null) {
                reportGroupRoll(character, info, r.total(), r.breakdown(), pending);
            } else {
                io.papermc.jkvttplugin.dm.CheckManager.recordActive(character.getPlayerId(), info.displayName, r.total());
                reportDmCheck(character, info, r.total(), r.breakdown(), pending);
                // The spell or trap waiting on this very save (#245, #272): the result decides its damage, no
                // second click. Only a check called for a request has one; any other save resolves nothing.
                if (requestId != null && pending.dc() != null) {
                    io.papermc.jkvttplugin.combat.SaveOutcome.graded(requestId, r.total() >= pending.dc());
                }
                if ("TOOL".equals(type)) maybeBreakThievesTools(character, value, r.total(), pending);
            }
            offerInspiration(character, info, r, true);
            return true;
        }
        reportOwnRoll(character, info, r, advantage);
        offerInspiration(character, info, r, false);
        return true;
    }

    /** This character's combatant in a running fight, or null. */
    private static io.papermc.jkvttplugin.combat.Combatant combatantOf(CharacterSheet character) {
        var session = io.papermc.jkvttplugin.combat.CombatSession.getSessionForPlayer(character.getPlayerId());
        if (session == null) return null;
        for (var c : session.getCombatants()) if (c.isPlayer() && c.getId().equals(character.getPlayerId())) return c;
        return null;
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
        String condition = character.conditionDisadvantageOn("SAVE".equals(type), a);
        if (condition != null) return condition;
        return character.effectDisadvantageSource(rollTags(type, a));
    }

    /** What gives this roll advantage (an effect such as Rage, #223), or null. */
    private static String boonReason(CharacterSheet character, String type, String value) {
        return boonReason(character, type, value, null);
    }

    /** @param requestId the save request this roll or prompt is for, or null for any other roll */
    private static String boonReason(CharacterSheet character, String type, String value, String requestId) {
        String effect = character.effectAdvantageSource(rollTags(type, abilityOf(type, value)));
        return effect != null ? effect : saveContextReason(character, type, value, requestId);
    }

    /**
     * "against poison": a called save that says what it's against (a spell's or a trap's tags on the pending
     * check) and a conditional advantage that matches (Stout Resilience, Fey Ancestry, Gnome Cunning for its
     * three abilities). The sheet's own rule, the one a fight uses; asked here by the prompt and by the roll,
     * so they can't disagree (#266).
     */
    private static String saveContextReason(CharacterSheet character, String type, String value, String requestId) {
        if (!"SAVE".equals(type) || requestId == null) return null; // only the save a request asked for (#272)
        java.util.Set<String> tags = io.papermc.jkvttplugin.combat.SaveOutcome.tagsOf(requestId);
        return tags.isEmpty() ? null : character.saveAdvantageSourceVs(abilityOf(type, value), tags);
    }

    /** A save is a save; a skill, check or tool check is an ability check. */
    private static java.util.List<String> rollTags(String type, Ability a) {
        return "SAVE".equals(type) ? io.papermc.jkvttplugin.effect.RollTags.save(a) : io.papermc.jkvttplugin.effect.RollTags.check(a);
    }

    /** Fold a disadvantage and an advantage into a menu roll mode (5e: one of each cancels out). */
    static RollMode withPenalties(CharacterSheet character, String type, String value, RollMode mode) {
        return withPenalties(character, type, value, mode, null);
    }

    /** @param requestId the save request this roll is for, whose tags may earn advantage (#266); null for any other roll */
    static RollMode withPenalties(CharacterSheet character, String type, String value, RollMode mode, String requestId) {
        io.papermc.jkvttplugin.combat.Advantage adv = switch (mode) {
            case ADVANTAGE -> io.papermc.jkvttplugin.combat.Advantage.ADVANTAGE;
            case DISADVANTAGE -> io.papermc.jkvttplugin.combat.Advantage.DISADVANTAGE;
            default -> io.papermc.jkvttplugin.combat.Advantage.NONE;
        };
        if (penaltyReason(character, type, value) != null) adv = adv.with(false);
        if (boonReason(character, type, value, requestId) != null) adv = adv.with(true);
        return adv.isAdvantage() ? RollMode.ADVANTAGE : adv.isDisadvantage() ? RollMode.DISADVANTAGE : RollMode.NORMAL;
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
        // Show the work, not just the number: "🎲 you rolled 16 +3[DEX] +2[Prof] = 21".
        String shareText = rollerName + " rolled " + info.displayName + ": " + work;
        String token = io.papermc.jkvttplugin.dm.CheckManager.stashShare(shareText);
        Component verdict = Component.empty();
        if (p.dc() != null) {
            boolean success = total >= p.dc();
            verdict = Component.text(success ? "  ✔ SUCCESS" : "  ✘ FAIL",
                            success ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .append(Component.text(" (DC " + p.dc() + ")", NamedTextColor.DARK_GRAY));
        }
        Component dmMsg = Component.text(shareText, NamedTextColor.GOLD)
                .append(verdict)
                .append(Component.text("  "))
                .append(Component.text("[Share with players]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/dm check share " + token))
                        .hoverEvent(HoverEvent.showText(Component.text("Announce this roll to the table."))));
        // Called by a command block or the console (no DM behind it): every DM online gets it.
        Player dm = p.dmId() != null ? Bukkit.getPlayer(p.dmId()) : null;
        boolean told = false;
        if (dm != null) { dm.sendMessage(dmMsg); told = true; }
        else if (p.dmId() == null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (io.papermc.jkvttplugin.dm.DMManager.isDM(online)) { online.sendMessage(dmMsg); told = true; }
            }
        }
        if (!told) Bukkit.broadcast(Component.text(shareText, NamedTextColor.YELLOW)); // no DM around → announce
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
        if (!io.papermc.jkvttplugin.dm.LockTools.is(tool) || p.dc() == null) return; // any item tagged `lockpick`
        String toolName = io.papermc.jkvttplugin.data.model.enums.ToolRegistry.displayName(tool);
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
            owner.sendMessage(Component.text("🔧 Your " + toolName + " snap: that set is ruined.", NamedTextColor.RED));
            Component note = Component.text("🔧 " + character.getCharacterName() + "'s " + toolName + " broke ("
                    + (total < p.dc() ? "failed" : "used") + ", DC " + p.dc() + ").", NamedTextColor.GRAY);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (io.papermc.jkvttplugin.dm.DMManager.isDM(online)) online.sendMessage(note);
            }
            return;
        }
        // Not carrying any: nothing to break. The DM was already told "NOT carrying them" when calling it.
    }

    /**
     * One member of a group check has rolled (#186): the DM sees it as it arrives, with the count so
     * far; when the last one's in, the group's verdict follows.
     */
    private static void reportGroupRoll(CharacterSheet character, RollInfo info, int total, String work,
                                        io.papermc.jkvttplugin.dm.CheckManager.Pending p) {
        Player owner = Bukkit.getPlayer(character.getPlayerId());
        if (owner != null) owner.sendMessage(Component.text("You rolled " + info.displayName + ": " + work + " — sent to the DM.", NamedTextColor.GRAY));
        var g = io.papermc.jkvttplugin.dm.CheckManager.recordGroupRoll(p.groupId(), character.getPlayerId(), total);
        if (g == null) return; // the DM already closed it
        Component line = Component.text("  📋 " + character.getCharacterName() + " — " + info.displayName + ": " + work, NamedTextColor.GOLD);
        if (p.dc() != null) {
            boolean ok = total >= p.dc();
            line = line.append(Component.text(ok ? "  ✔" : "  ✘", ok ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        line = line.append(Component.text("  (" + g.totals.size() + "/" + g.members.size() + " in)", NamedTextColor.DARK_GRAY));
        Player dm = p.dmId() != null ? Bukkit.getPlayer(p.dmId()) : null;
        if (dm != null) dm.sendMessage(line);
        else for (Player d : io.papermc.jkvttplugin.dm.DMManager.getOnlineDMs()) d.sendMessage(line);
        if (g.allIn()) io.papermc.jkvttplugin.commands.CheckCommand.reportGroupVerdict(io.papermc.jkvttplugin.dm.CheckManager.closeGroup(g.id));
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
     * A sheet roll the player made themselves, shown to them only (#186): "Zek rolled Stealth with advantage: 18  (🎲 d20 [9, 15]
     * advantage +3[DEX] +2[Prof] = 18)". The work in brackets is {@link RollPrompt}'s wording, so it
     * reads the same as every other roll; a natural 1 or 20 is pulled out and shown loud.
     */
    private static void reportOwnRoll(CharacterSheet character, RollInfo info, RollService.RollResult r,
                                      io.papermc.jkvttplugin.combat.Advantage advantage) {
        Component message = Component.text(character.getCharacterName(), NamedTextColor.AQUA)
                .append(Component.text(" rolled ", NamedTextColor.GRAY))
                .append(Component.text(info.displayName, NamedTextColor.YELLOW));
        if (advantage != null && advantage.affectsRoll() && !r.providedTotal()) {
            message = message.append(Component.text(" with ", NamedTextColor.GRAY))
                    .append(Component.text(advantage.label(), advantage.isAdvantage() ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        String nat = r.providedTotal() ? "" : RollService.natCallout(r.d20());
        String work = nat.isEmpty() ? r.breakdown() : r.breakdown().replace(nat, "");
        message = message.append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(r.total(), NamedTextColor.WHITE))
                .append(Component.text("  (" + work + ")", NamedTextColor.GRAY));
        if (!nat.isEmpty()) {
            message = message.append(Component.text(nat, r.d20() == 20 ? NamedTextColor.GOLD : NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }
        // A roll you make yourself is yours (#186): only you see it, and [Show the DM] passes it on.
        // The DM gets it with [Share with players], like a check they called.
        Player owner = Bukkit.getPlayer(character.getPlayerId());
        if (owner == null) return;
        final Component result = message;
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message);
        Component show = Component.text("[Show the DM]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text("Only you saw this roll. Send it to the DM, who can share it with the table.")))
                .clickEvent(ClickEvent.callback(a -> {
                    String token = io.papermc.jkvttplugin.dm.CheckManager.stashShare(plain);
                    Component toDm = result.append(Component.text("  "))
                            .append(Component.text("[Share with players]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                                    .clickEvent(ClickEvent.runCommand("/dm check share " + token))
                                    .hoverEvent(HoverEvent.showText(Component.text("Announce this roll to the table."))));
                    var dms = io.papermc.jkvttplugin.dm.DMManager.getOnlineDMs();
                    if (dms.isEmpty()) { owner.sendMessage(Component.text("No DM is online.", NamedTextColor.GRAY)); return; }
                    for (Player dm : dms) if (!dm.equals(owner)) dm.sendMessage(toDm);
                    owner.sendMessage(Component.text("Sent to the DM.", NamedTextColor.GRAY));
                }, net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).lifetime(java.time.Duration.ofMinutes(30)).build()));
        owner.sendMessage(message.append(Component.text("  ")).append(show));
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
     * Helper record to bundle roll information
     */
    private record RollInfo(String displayName, int bonus, String breakdown) {}
}
