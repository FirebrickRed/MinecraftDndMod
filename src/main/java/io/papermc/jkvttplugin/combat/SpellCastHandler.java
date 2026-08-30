package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import io.papermc.jkvttplugin.data.model.DndCondition;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat spellcasting (Issue #123, Phase 1). Attack-roll spells route through the same roll/damage
 * machinery as attacks; save spells force the target to roll a save (player rolls; DM is prompted for
 * an entity) and resolve damage + a condition on the result. Reuses RollService, AttackHandler's
 * damage prompt, and the #103 condition system.
 */
public class SpellCastHandler {

    /** A saving throw a target still owes from a save spell. */
    private record PendingSave(String spellName, UUID casterId, int dc, Ability ability,
                               String damage, String damageType, String saveEffect, String conditionOnFail) {}
    private static final Map<UUID, PendingSave> pendingSaves = new HashMap<>();

    /** @return true if the spell actually resolved (so the action is spent). */
    public static boolean cast(Combatant caster, Combatant target, CombatSession session, Player player,
                               DndSpell spell, Integer providedRoll, Integer providedTotal) {
        CharacterSheet sheet = caster.getCharacterSheet();
        if (sheet == null) {
            player.sendMessage(Component.text("Only characters cast spells this way (an entity's spells are attacks — use /combat attack).", NamedTextColor.RED));
            return false;
        }
        Ability ability = spellcastingAbility(sheet);
        if (ability == null) {
            player.sendMessage(Component.text("Your class doesn't have spellcasting.", NamedTextColor.RED));
            return false;
        }
        if (!sheet.getKnownCantrips().contains(spell) && !sheet.getKnownSpells().contains(spell)) {
            player.sendMessage(Component.text(sheet.getCharacterName() + " doesn't know " + spell.getName() + ".", NamedTextColor.RED));
            return false;
        }
        int mod = sheet.getProficiencyBonus() + sheet.getModifier(ability);

        if (spell.isAttackRoll()) {
            RollService.RollResult r = RollService.resolve(providedRoll, providedTotal, mod, "+" + mod + "[Spell]");
            if (r == null) {
                player.sendMessage(Component.text("Roll your d20: add --roll <n>.", NamedTextColor.YELLOW));
                return false;
            }
            int ac = target.getArmorClass();
            boolean hit = r.providedTotal() ? r.total() >= ac : (r.nat20() || (!r.nat1() && r.total() >= ac));
            session.broadcast(Component.empty());
            session.broadcast(Component.text("✨ " + caster.getDisplayName(true) + " casts " + spell.getName()
                    + " at " + target.getDisplayName(true) + "!", NamedTextColor.LIGHT_PURPLE));
            session.broadcast(Component.text("Spell attack: " + r.breakdown() + " vs AC " + ac, NamedTextColor.GRAY));
            if (hit) {
                session.broadcast(Component.text(r.nat20() ? "★ CRITICAL HIT! ★" : "HIT!", NamedTextColor.GREEN, TextDecoration.BOLD));
                String dmg = r.nat20() ? AttackHandler.doubleDice(spell.getDamage()) : spell.getDamage();
                if (dmg != null) AttackHandler.promptDamage(session, caster, target, dmg, spell.getDamageType(), r.nat20());
            } else {
                session.broadcast(Component.text("MISS", NamedTextColor.RED));
            }
            return true;
        }

        if (spell.isSaveSpell()) {
            Ability saveAbility = parseAbility(spell.getSaveType());
            if (saveAbility == null) {
                player.sendMessage(Component.text("This spell's save type is invalid.", NamedTextColor.RED));
                return false;
            }
            int dc = 8 + mod;
            session.broadcast(Component.empty());
            session.broadcast(Component.text("✨ " + caster.getDisplayName(true) + " casts " + spell.getName()
                    + " at " + target.getDisplayName(true) + " — DC " + dc + " " + saveAbility.getAbbreviation() + " save!", NamedTextColor.LIGHT_PURPLE));
            pendingSaves.put(target.getId(), new PendingSave(spell.getName(), caster.getId(), dc, saveAbility,
                    spell.getDamage(), spell.getDamageType(), spell.getSaveEffect(), spell.getConditionOnFail()));
            promptSave(session, target, saveAbility);
            return true;
        }

        // Utility / non-damaging spell — announce; the DM narrates the effect (full utility casting is #152).
        session.broadcast(Component.text("✨ " + caster.getDisplayName(true) + " casts " + spell.getName() + ".", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /** Send the target's controller a clickable prompt to roll the pending save. */
    private static void promptSave(CombatSession session, Combatant target, Ability ability) {
        String cmd = "/combat save --roll ";
        Component prompt = Component.text("🛡 Roll a " + ability.getAbbreviation() + " saving throw — ", NamedTextColor.GOLD)
                .append(Component.text("[click, then type your d20]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(cmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd + "<your d20> — the game adds your save bonus."))));
        if (target.isPlayer() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(prompt);
        } else {
            // Entity: the DM rolls the save for it.
            session.sendToDM(Component.text("Roll " + target.getDisplayName(true) + "'s save: ", NamedTextColor.GOLD)
                    .append(Component.text("[click, then type the d20]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.suggestCommand("/combat save " + quoted(target.getDisplayName()) + " --roll "))
                            .hoverEvent(HoverEvent.showText(Component.text("Rolls the save for the entity.")))));
        }
    }

    /** Resolve a pending save for {@code target}. Players roll their own; the DM rolls for entities. */
    public static void resolveSave(Player roller, CombatSession session, Combatant target,
                                   Integer providedRoll, Integer providedTotal) {
        PendingSave ps = pendingSaves.get(target.getId());
        if (ps == null) {
            roller.sendMessage(Component.text(target.getDisplayName() + " has no pending save.", NamedTextColor.RED));
            return;
        }
        int bonus = saveBonus(target, ps.ability());
        RollService.RollResult r = RollService.resolve(providedRoll, providedTotal, bonus,
                "+" + bonus + "[" + ps.ability().getAbbreviation() + "]");
        if (r == null) {
            roller.sendMessage(Component.text("Add your roll: --roll <n>.", NamedTextColor.YELLOW));
            return;
        }
        pendingSaves.remove(target.getId());
        boolean success = r.total() >= ps.dc();
        Combatant caster = findById(session, ps.casterId());
        Combatant damageSource = caster != null ? caster : target;

        session.broadcast(Component.text(target.getDisplayName(true) + " " + ps.ability().getAbbreviation()
                + " save: " + r.breakdown() + " vs DC " + ps.dc() + " → " + (success ? "SUCCESS" : "FAIL"),
                success ? NamedTextColor.GREEN : NamedTextColor.RED));

        if (success) {
            if ("half".equalsIgnoreCase(ps.saveEffect()) && ps.damage() != null) {
                session.broadcast(Component.text(ps.spellName() + " deals half on a save.", NamedTextColor.GRAY));
                AttackHandler.promptDamage(session, damageSource, target, ps.damage(), ps.damageType(), false);
                if (damageSource.getTurnState() != null) damageSource.getTurnState().setPendingDamageHalf(true);
            } else {
                session.broadcast(Component.text(target.getDisplayName(true) + " shrugs it off.", NamedTextColor.GRAY));
            }
            return;
        }
        // Failed save: full damage + any condition.
        if (ps.damage() != null) AttackHandler.promptDamage(session, damageSource, target, ps.damage(), ps.damageType(), false);
        DndCondition cond = ps.conditionOnFail() != null ? ConditionLoader.get(ps.conditionOnFail()) : null;
        if (cond != null && target.addCondition(cond.getId())) {
            session.setConditionEffect(target, cond, true);
            session.broadcast(Component.text(target.getDisplayName(true) + " is now " + cond.getName() + "!", NamedTextColor.YELLOW));
            session.updateScoreboard();
        }
    }

    public static boolean hasPendingSave(UUID targetId) { return pendingSaves.containsKey(targetId); }

    // ==================== HELPERS ====================

    private static int saveBonus(Combatant c, Ability ability) {
        if (c.isPlayer() && c.getCharacterSheet() != null) return c.getCharacterSheet().getSavingThrowBonus(ability);
        if (c.isEntity() && c.getEntityInstance() != null) {
            return Ability.getModifier(c.getEntityInstance().getTemplate().getAbilityScore(ability));
        }
        return 0;
    }

    private static Ability spellcastingAbility(CharacterSheet sheet) {
        if (sheet.getMainClass() == null || sheet.getMainClass().getSpellcastingInfo() == null) return null;
        return parseAbility(sheet.getMainClass().getSpellcastingInfo().getCastingAbility());
    }

    private static Ability parseAbility(String name) {
        if (name == null) return null;
        try { return Ability.valueOf(name.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static Combatant findById(CombatSession session, UUID id) {
        for (Combatant c : session.getCombatants()) if (c.getId().equals(id)) return c;
        return null;
    }

    private static String quoted(String name) { return name.contains(" ") ? "\"" + name + "\"" : name; }
}
