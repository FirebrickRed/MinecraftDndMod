package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.dm.DMManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The combat actions that used to only announce themselves (#176): Help, Hide and Search.
 * Each returns true once it has actually happened, so the caller spends the Action then (a roll
 * still waiting on the player's die costs nothing yet).
 *
 * <ul>
 *   <li><b>Help</b> (PHB p.192): an ally's next attack roll or ability check before your next turn
 *       has advantage.</li>
 *   <li><b>Hide</b> (PHB p.177): a Stealth check. The DM sees it against each opponent's passive
 *       Perception and decides; [Hidden] gives the Hidden condition (advantage on your attacks,
 *       disadvantage on theirs), which ends after you attack or when you're found.</li>
 *   <li><b>Search</b>: a Perception (or Investigation) check. The DM sees it first, next to anyone on
 *       the other side who's hidden, with [Reveal] for each it beats.</li>
 * </ul>
 * Rolls are DM-first: the table is told what was tried, not the number.
 */
public final class CombatActions {

    private CombatActions() {}

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(15)).build();

    // ==================== HELP ====================

    /** {@code /combat action help <ally>}: no name lists the allies to pick from. */
    static boolean help(Player player, CombatSession session, Combatant actor, List<String> words) {
        if (words.isEmpty()) {
            Component row = Component.text("Help whom? ", NamedTextColor.YELLOW);
            for (Combatant c : session.getCombatants()) {
                if (c == actor || c.isDead() || c.isPlayer() != actor.isPlayer()) continue;
                String name = c.getDisplayName();
                String cmd = "/combat action help " + (name.contains(" ") ? "\"" + name + "\"" : name);
                row = row.append(Component.text("[" + name + "] ", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(cmd)).hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd))));
            }
            player.sendMessage(row);
            return false;
        }
        String name = io.papermc.jkvttplugin.util.NameUtil.stripQuotes(String.join(" ", words));
        Combatant ally = null;
        for (Combatant c : session.getCombatants()) {
            if (c.getDisplayName().equalsIgnoreCase(name)) { ally = c; break; }
        }
        if (ally == null || ally == actor) {
            player.sendMessage(Component.text(ally == null ? "No one in the fight called " + name + "." : "You can't Help yourself.", NamedTextColor.RED));
            return false;
        }
        ally.setHelpedBy(actor);
        session.broadcast(Component.text("🤝 " + actor.getDisplayName(true) + " helps " + ally.getDisplayName(true)
                + ": advantage on their next attack roll or ability check before " + actor.getDisplayName(true) + "'s next turn.",
                NamedTextColor.AQUA));
        return true;
    }

    // ==================== HIDE ====================

    /** {@code /combat action hide [roll words]}: a Stealth check, graded by the DM. */
    static boolean hide(Player player, CombatSession session, Combatant actor, String[] rollWords) {
        SkillRoll sr = skillFor(actor, Skill.STEALTH);
        RollService.RollInput in = RollService.parseInput(rollWords, player);
        RollService.RollResult r = RollService.resolve(in, sr.bonus(), sr.label(), actor.rerollsNat1(), sr.advantage());
        if (r == null) {
            player.sendMessage(RollPrompt.again(player, "🥷 Roll Stealth to Hide:", RollPrompt.d20(sr.advantage()), sr.label()));
            return false;
        }
        actor.clearHelp(); // a Help was spent on this check, if there was one
        SpellEffects.useUp(actor, io.papermc.jkvttplugin.effect.ActiveEffect.CHECKS); // Guidance, once (#225)
        int total = r.total();
        player.sendMessage(Component.text("🥷 Stealth: " + r.breakdown() + ". The DM decides who notices you.", NamedTextColor.DARK_AQUA));
        session.broadcast(Component.text("🥷 " + actor.getDisplayName(true) + " tries to Hide.", NamedTextColor.DARK_AQUA));

        // The DM: the total against each opponent's passive Perception, and the call.
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("🥷 " + actor.getDisplayName() + " hides: Stealth " + total
                + ". Against passive Perception (they also need cover or heavy obscurement):", NamedTextColor.DARK_AQUA));
        for (Combatant c : session.getCombatants()) {
            if (c.isDead() || c.isPlayer() == actor.isPlayer()) continue;
            int passive = passivePerception(c);
            boolean unseen = total > passive; // RAW: a tie goes to the one noticing
            lines.add(Component.text("   " + (unseen ? "✔ " : "✗ ") + c.getDisplayName() + " (passive " + passive + ")"
                    + (unseen ? " doesn't notice" : " notices"), unseen ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        Component buttons = Component.text("   ")
                .append(Component.text("[Hidden]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text("They're hidden: advantage on their attacks, disadvantage on attacks against them. Ends after they attack.")))
                        .clickEvent(ClickEvent.callback(a -> {
                            if (actor.isDead() || !session.isActive()) return;
                            actor.addCondition("hidden");
                            actor.setHiddenStealth(total);
                            session.broadcast(Component.text("🥷 " + actor.getDisplayName(true) + " is hidden.", NamedTextColor.DARK_AQUA));
                            session.updateScoreboard();
                        }, ONCE)))
                .append(Component.text("  "))
                .append(Component.text("[Not hidden]", NamedTextColor.RED, TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text("They're seen (no cover, or someone noticed)")))
                        .clickEvent(ClickEvent.callback(a -> {
                            Player p = actor.getPlayer();
                            if (p != null) p.sendMessage(Component.text("🥷 You're not hidden: they can still see you.", NamedTextColor.GRAY));
                            else session.sendToDM(Component.text(actor.getDisplayName() + " stays in plain sight.", NamedTextColor.GRAY));
                        }, ONCE)));
        lines.add(buttons);
        toDms(player, session, lines);
        // Bardic Inspiration after the roll (#40): the DM sees the new total and makes the call.
        InspirationPrompt.offer(actor.getCharacterSheet(), "Stealth check", total, null, InspirationPrompt.rollerAndDms(player));
        return true;
    }

    // ==================== SEARCH ====================

    /** {@code /combat action search [perception|investigation] [roll words]}. */
    static boolean search(Player player, CombatSession session, Combatant actor, List<String> words, String[] rollWords) {
        Skill skill = !words.isEmpty() && words.get(0).equalsIgnoreCase("investigation") ? Skill.INVESTIGATION : Skill.PERCEPTION;
        SkillRoll sr = skillFor(actor, skill);
        RollService.RollInput in = RollService.parseInput(rollWords, player);
        RollService.RollResult r = RollService.resolve(in, sr.bonus(), sr.label(), actor.rerollsNat1(), sr.advantage());
        if (r == null) {
            player.sendMessage(RollPrompt.again(player, "🔍 Roll " + skill.getDisplayName() + " to Search:", RollPrompt.d20(sr.advantage()), sr.label()));
            return false;
        }
        actor.clearHelp();
        SpellEffects.useUp(actor, io.papermc.jkvttplugin.effect.ActiveEffect.CHECKS); // Guidance, once (#225)
        int total = r.total();
        player.sendMessage(Component.text("🔍 " + skill.getDisplayName() + ": " + r.breakdown() + ". The DM tells you what you find.", NamedTextColor.GOLD));
        session.broadcast(Component.text("🔍 " + actor.getDisplayName(true) + " Searches.", NamedTextColor.GOLD));

        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("🔍 " + actor.getDisplayName() + " searches: " + skill.getDisplayName() + " " + total + ".", NamedTextColor.GOLD));
        // Anyone on the other side who's hidden: found if this beats their Stealth (a tie: still hidden).
        if (skill == Skill.PERCEPTION) {
            for (Combatant c : session.getCombatants()) {
                Integer stealth = c.getHiddenStealth();
                if (stealth == null || c.isPlayer() == actor.isPlayer()) continue;
                boolean found = total > stealth;
                Component line = Component.text("   " + c.getDisplayName() + " is hidden (Stealth " + stealth + "): "
                        + (found ? "found. " : "still hidden. "), found ? NamedTextColor.GREEN : NamedTextColor.GRAY);
                if (found) {
                    line = line.append(Component.text("[Reveal]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                            .hoverEvent(HoverEvent.showText(Component.text("They're no longer hidden")))
                            .clickEvent(ClickEvent.callback(a -> {
                                c.removeCondition("hidden");
                                c.setHiddenStealth(null);
                                session.broadcast(Component.text("🔍 " + actor.getDisplayName(true) + " spots " + c.getDisplayName(true) + "!", NamedTextColor.GOLD));
                                session.updateScoreboard();
                            }, ONCE)));
                }
                lines.add(line);
            }
        }
        toDms(player, session, lines);
        InspirationPrompt.offer(actor.getCharacterSheet(), skill.getDisplayName() + " check", total, null, InspirationPrompt.rollerAndDms(player));
        return true;
    }

    // ==================== SHARED ====================

    /** A skill check's bonus, how it reads, and its advantage, for a character or a creature. */
    record SkillRoll(int bonus, String label, Advantage advantage) {}

    static SkillRoll skillFor(Combatant c, Skill skill) {
        Advantage adv = Advantage.NONE;
        CharacterSheet s = c.getCharacterSheet();
        int bonus;
        String label;
        Ability ability = skill.getAbility();
        if (s != null) {
            bonus = s.getSkillBonus(skill);
            label = s.getSkillBonusBreakdown(skill);
            if (s.armorPenaltyApplies(ability)) adv = adv.with(false);
            if (s.conditionDisadvantageOn(false, ability) != null) adv = adv.with(false);
            if (s.effectAdvantageSource(io.papermc.jkvttplugin.effect.RollTags.check(ability)) != null) adv = adv.with(true);
            if (s.effectDisadvantageSource(io.papermc.jkvttplugin.effect.RollTags.check(ability)) != null) adv = adv.with(false);
        } else {
            DndEntity t = c.getEntityInstance() != null ? c.getEntityInstance().getTemplate() : null;
            bonus = t != null ? t.getSkillBonus(skill) : 0;
            label = (bonus >= 0 ? "+" : "") + bonus + "[" + (t != null && t.listsSkill(skill) ? skill.getDisplayName() : ability.getAbbreviation()) + "]"
                    + c.rollBonusLabel(io.papermc.jkvttplugin.effect.ActiveEffect.CHECKS);
        }
        // An ally's Help counts on an ability check too; the caller uses it up once the roll is made.
        if (c.getHelpedByName() != null) adv = adv.with(true);
        return new SkillRoll(bonus, label, adv);
    }

    /** 10 + Perception bonus (PHB p.175). */
    static int passivePerception(Combatant c) {
        CharacterSheet s = c.getCharacterSheet();
        if (s != null) return 10 + s.getSkillBonus(Skill.PERCEPTION);
        DndEntity t = c.getEntityInstance() != null ? c.getEntityInstance().getTemplate() : null;
        return 10 + (t != null ? t.getSkillBonus(Skill.PERCEPTION) : 0);
    }

    /** DM-first: every online DM gets the lines (the roller too, when it's the DM running a creature). */
    private static void toDms(Player roller, CombatSession session, List<Component> lines) {
        for (Player dm : DMManager.getOnlineDMs()) for (Component l : lines) dm.sendMessage(l);
    }
}
