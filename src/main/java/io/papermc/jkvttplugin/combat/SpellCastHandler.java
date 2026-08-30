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

        // Healing / temp HP spells (Cure Wounds, Healing Word, False Life…) — no roll, applied at once.
        if (spell.isHealing() || spell.grantsTempHp()) {
            session.broadcast(Component.empty());
            session.broadcast(Component.text("✨ " + caster.getDisplayName(true) + " casts " + spell.getName()
                    + " on " + target.getDisplayName(true) + ".", NamedTextColor.LIGHT_PURPLE));
            if (spell.isHealing()) {
                int amount = Math.max(1, rollAmount(spell.getHealing()) + sheet.getModifier(ability));
                DamageHandler.applyHealing(session, target, amount);
            }
            if (spell.grantsTempHp()) {
                DamageHandler.applyTempHp(session, target, Math.max(0, rollAmount(spell.getTempHp())));
            }
            return true;
        }

        if (spell.isAttackRoll()) {
            RollService.RollResult r = RollService.resolve(providedRoll, providedTotal, mod, "+" + mod + "[Spell]");
            if (r == null) {
                player.sendMessage(Component.text("Roll your d20: add --roll <n>.", NamedTextColor.YELLOW));
                return false;
            }
            int ac = target.getArmorClass();
            boolean hit = RollService.hits(r, ac);
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

    /**
     * Cast an area spell (#149): find every creature in the shape and resolve the spell on each.
     * Cone/line/burst originate from the caster; sphere centers on where the caster is looking.
     */
    public static boolean castAoe(Combatant caster, CombatSession session, Player player, DndSpell spell,
                                  Integer providedRoll, Integer providedTotal) {
        CharacterSheet sheet = caster.getCharacterSheet();
        if (sheet == null) {
            player.sendMessage(Component.text("Only characters cast spells this way.", NamedTextColor.RED));
            return false;
        }
        Ability ability = spellcastingAbility(sheet);
        if (ability == null) { player.sendMessage(Component.text("Your class doesn't have spellcasting.", NamedTextColor.RED)); return false; }
        if (!sheet.getKnownCantrips().contains(spell) && !sheet.getKnownSpells().contains(spell)) {
            player.sendMessage(Component.text(sheet.getCharacterName() + " doesn't know " + spell.getName() + ".", NamedTextColor.RED));
            return false;
        }
        int mod = sheet.getProficiencyBonus() + sheet.getModifier(ability);

        java.util.List<Combatant> affected = creaturesInArea(caster, player, spell);
        affected.removeIf(c -> c.getId().equals(caster.getId()) || c.isDead()); // the caster isn't caught in their own AoE
        // Some AoE hit only enemies or only allies (e.g. a beneficial burst); "all" is the default.
        if (!"all".equalsIgnoreCase(spell.getAoeTargets())) {
            boolean wantAllies = "allies".equalsIgnoreCase(spell.getAoeTargets());
            affected.removeIf(c -> (c.isPlayer() == caster.isPlayer()) != wantAllies);
        }
        for (Combatant t : affected) markAoeTarget(t); // show who's caught (no explicit target)

        session.broadcast(Component.empty());
        session.broadcast(Component.text("✨ " + caster.getDisplayName(true) + " casts " + spell.getName()
                + " (" + spell.getAoeShape() + ", " + spell.getAoeSize() + " ft) — " + affected.size()
                + " creature" + (affected.size() == 1 ? "" : "s") + " caught!", NamedTextColor.LIGHT_PURPLE));

        if (affected.isEmpty()) return true;

        if (spell.isSaveSpell()) {
            Ability saveAbility = parseAbility(spell.getSaveType());
            if (saveAbility == null) { player.sendMessage(Component.text("Invalid save type.", NamedTextColor.RED)); return false; }
            int dc = 8 + mod;
            session.broadcast(Component.text("DC " + dc + " " + saveAbility.getAbbreviation() + " save — each caught creature rolls:", NamedTextColor.GRAY));
            for (Combatant t : affected) {
                pendingSaves.put(t.getId(), new PendingSave(spell.getName(), caster.getId(), dc, saveAbility,
                        spell.getDamage(), spell.getDamageType(), spell.getSaveEffect(), spell.getConditionOnFail()));
                promptSave(session, t, saveAbility);
            }
        } else {
            session.broadcast(Component.text("(no save defined — the DM applies the effect)", NamedTextColor.DARK_GRAY));
        }
        return true;
    }

    /** Flag a creature caught in an AoE with a magical particle burst so the caster sees who's hit. */
    private static void markAoeTarget(Combatant c) {
        org.bukkit.Location loc = c.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(org.bukkit.Particle.WITCH, loc.clone().add(0, 1.2, 0), 25, 0.3, 0.7, 0.3, 0.03);
    }

    /** Combatants inside the spell's area. */
    private static java.util.List<Combatant> creaturesInArea(Combatant caster, Player player, DndSpell spell) {
        java.util.List<Combatant> result = new java.util.ArrayList<>();
        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session == null) return result;
        org.bukkit.Location origin = caster.getLocation();
        if (origin == null) return result;
        double size = spell.getAoeSize() / 5.0; // feet → blocks
        String shape = spell.getAoeShape().toLowerCase();
        org.bukkit.util.Vector dir = player.getEyeLocation().getDirection().setY(0).normalize();

        org.bukkit.Location sphereCenter = null;
        if (shape.equals("sphere")) {
            org.bukkit.block.Block aimed = player.getTargetBlockExact(64);
            sphereCenter = aimed != null ? aimed.getLocation().add(0.5, 0.5, 0.5)
                    : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(20));
        }

        for (Combatant c : session.getCombatants()) {
            org.bukkit.Location loc = c.getLocation();
            if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(origin.getWorld())) continue;
            boolean in = switch (shape) {
                case "sphere" -> loc.distance(sphereCenter) <= size;
                case "burst" -> loc.distance(origin) <= size;
                case "cone", "line" -> inConeOrLine(origin, dir, loc, size, shape.equals("cone"));
                default -> false;
            };
            if (in) result.add(c);
        }
        return result;
    }

    /** Cone (5e: width == distance from you) or line (5-ft wide) from origin along dir. */
    private static boolean inConeOrLine(org.bukkit.Location origin, org.bukkit.util.Vector dir,
                                        org.bukkit.Location target, double lengthBlocks, boolean cone) {
        org.bukkit.util.Vector v = target.toVector().subtract(origin.toVector());
        v.setY(0);
        double along = v.dot(dir);
        if (along < 0 || along > lengthBlocks) return false;
        double perp = v.clone().subtract(dir.clone().multiply(along)).length();
        return cone ? perp <= along / 2.0 : perp <= 0.5; // cone widens; line ~5 ft wide
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

    /** Roll a dice expression ("1d8", "1d4+4") or read a flat number ("5"); 0 if unparseable. */
    private static int rollAmount(String expr) {
        if (expr == null || expr.isBlank()) return 0;
        int rolled = io.papermc.jkvttplugin.util.DiceRoller.parseDiceRoll(expr.trim());
        if (rolled >= 0) return rolled;
        try { return Integer.parseInt(expr.trim()); } catch (NumberFormatException e) { return 0; }
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
