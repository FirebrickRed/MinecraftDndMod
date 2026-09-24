package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import io.papermc.jkvttplugin.data.model.DndCondition;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
                               String damage, String damageType, String saveEffect, String conditionOnFail,
                               java.util.Set<String> saveTags) {}

    /** What a save is "against" — drives conditional advantages (e.g. Dwarf vs poison, Gnome vs magic). */
    private static java.util.Set<String> saveTagsFor(DndSpell spell) {
        java.util.Set<String> tags = new java.util.HashSet<>();
        tags.add("magic"); // every spell save is against magic
        if (spell.getDamageType() != null && !spell.getDamageType().isBlank()) tags.add(spell.getDamageType().toLowerCase());
        if (spell.getConditionOnFail() != null && !spell.getConditionOnFail().isBlank()) tags.add(spell.getConditionOnFail().toLowerCase());
        return tags;
    }
    private static final Map<UUID, PendingSave> pendingSaves = new HashMap<>();

    /** @return true if the spell actually resolved (so the action is spent). */
    public static boolean cast(Combatant caster, Combatant target, CombatSession session, Player player,
                               DndSpell spell, Integer providedRoll, Integer providedTotal, boolean forceAuto) {
        CharacterSheet sheet = caster.getCharacterSheet();
        if (sheet == null) {
            player.sendMessage(Component.text("Only characters cast spells this way (an entity's spells are attacks — use /combat attack).", NamedTextColor.RED));
            return false;
        }
        if (!sheet.knowsSpell(spell)) {
            player.sendMessage(Component.text(sheet.getCharacterName() + " doesn't know " + spell.getName() + ".", NamedTextColor.RED));
            return false;
        }
        Ability ability = sheet.castingAbilityFor(spell);
        if (ability == null) {
            player.sendMessage(Component.text("No spellcasting ability to cast " + spell.getName() + " with.", NamedTextColor.RED));
            return false;
        }
        // Range: Touch = 5 ft, Self = only yourself, "N feet" = N. Unknown → not enforced.
        String rangeErr = spellRangeError(caster, target, spell);
        if (rangeErr != null) { player.sendMessage(Component.text(rangeErr, NamedTextColor.RED)); return false; }
        int mod = sheet.getProficiencyBonus() + sheet.getModifier(ability);

        // Healing / temp HP spells (Cure Wounds, Healing Word, False Life…).
        if (spell.isHealing() || spell.grantsTempHp()) {
            Integer healAmount = null;
            String work = null;
            if (spell.isHealing()) {
                HealRoll heal = healRoll(sheet, spell, providedRoll, providedTotal, forceAuto);
                if (heal == null) { promptHealingRoll(player, sheet, target, spell); return false; } // physical: ask them to roll
                healAmount = heal.amount();
                work = heal.work();
            }
            session.broadcast(Component.empty());
            session.broadcast(Component.text("✨ " + caster.getDisplayName(true) + " casts " + spell.getName()
                    + " on " + target.getDisplayName(true) + ".", NamedTextColor.LIGHT_PURPLE));
            if (work != null) session.broadcast(Component.text(work, NamedTextColor.GRAY));
            if (healAmount != null) DamageHandler.applyHealing(session, target, healAmount);
            if (spell.grantsTempHp()) DamageHandler.applyTempHp(session, target, Math.max(0, rollAmount(spell.getTempHp(), session)));
            return true;
        }

        // Auto-hit spells (Magic Missile): no attack roll, no save — straight to the damage step.
        if (spell.isAutoHit()) {
            session.broadcast(Component.empty());
            session.broadcast(Component.text("✨ " + caster.getDisplayName(true) + " casts " + spell.getName()
                    + " at " + target.getDisplayName(true) + " — it hits automatically.", NamedTextColor.LIGHT_PURPLE));
            AttackHandler.promptDamage(session, caster, target, spell.getDamage() == null ? "" : spell.getDamage(),
                    spell.getDamageType(), false, flatLabel(spell.getDamage(), spell.getName()));
            return true;
        }

        if (spell.isAttackRoll()) {
            Advantage advantage = caster.attackAdvantageAgainst(target); // spell attacks get condition adv/dis too (#103)
            if (advantage != Advantage.NONE) {
                player.sendMessage(Component.text("↯ You have " + advantage.label() + " on this spell attack.",
                        advantage.isAdvantage() ? NamedTextColor.GREEN : advantage.isDisadvantage() ? NamedTextColor.RED : NamedTextColor.GRAY));
            }
            String attackLabel = sheet.getSpellAttackBreakdown(spell);
            RollService.RollResult r = RollService.resolve(providedRoll, providedTotal, mod,
                    attackLabel, caster.rerollsNat1(), advantage, forceAuto);
            if (r == null) {
                player.sendMessage(RollPrompt.again(player, "✨ Roll to hit with " + spell.getName() + ":", "d20", attackLabel));
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
                String base = spell.getDamage();
                String dmg = (base != null && r.nat20()) ? AttackHandler.doubleDice(base) : base;
                // Always open the damage step on a hit — even if the spell defines no fixed damage
                // (variable/misconfigured), the caster gets an editable prompt instead of a dead-end
                // where "/combat damage" reports "no attack hit to apply damage for".
                if (dmg == null) {
                    session.broadcast(Component.text("(no fixed damage on this spell — enter the amount)", NamedTextColor.DARK_GRAY));
                }
                // Passing the roll total lets a reaction window open on the hit (#195) — Shield stops
                // a Fire Bolt exactly as it stops a sword.
                AttackHandler.promptDamage(session, caster, target, dmg == null ? "" : dmg,
                        spell.getDamageType(), r.nat20(), flatLabel(dmg, spell.getName()), r.total());
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
                    spell.getDamage(), spell.getDamageType(), spell.getSaveEffect(), spell.getConditionOnFail(), saveTagsFor(spell)));
            promptSave(session, target, saveAbility);
            return true;
        }

        // Utility / non-damaging spell — announce; the DM narrates the effect (full utility casting is #152).
        session.broadcast(Component.text("✨ " + caster.getDisplayName(true) + " casts " + spell.getName() + ".", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /**
     * Cast a mark/curse spell (Hex, Hunter's Mark — #178). Entities can't hold effects, so the mark
     * lives on the CASTER: while they concentrate, they deal the rider damage on hits against the
     * marked target, and (Hex) the target has disadvantage on checks with the chosen ability.
     */
    public static boolean castMark(Combatant caster, Combatant target, CombatSession session, Player player,
                                   DndSpell spell, Ability choice) {
        CharacterSheet sheet = caster.getCharacterSheet();
        if (sheet == null) { player.sendMessage(Component.text("Only characters cast this spell.", NamedTextColor.RED)); return false; }

        if (sheet.isConcentrating() && sheet.getConcentratingOn() != spell) {
            session.broadcast(Component.text(caster.getDisplayName(true) + "'s concentration on "
                    + sheet.getConcentratingOn().getName() + " ends.", NamedTextColor.GRAY));
        }
        sheet.setConcentratingOn(spell);
        String abilityName = choice != null ? choice.name().toLowerCase() : null;
        sheet.setSpellMark(target.getId(), spell.getMarkDamage(), spell.getDamageType(), abilityName);

        session.broadcast(Component.empty());
        session.broadcast(Component.text("✨ " + caster.getDisplayName(true) + " marks " + target.getDisplayName(true)
                + " with " + spell.getName() + "!", NamedTextColor.LIGHT_PURPLE));
        String dtype = spell.getDamageType() != null ? " " + spell.getDamageType() : "";
        session.broadcast(Component.text("+" + spell.getMarkDamage() + dtype + " on every hit against "
                + target.getDisplayName(true) + " while concentrating.", NamedTextColor.GRAY));
        if (choice != null) {
            session.broadcast(Component.text(target.getDisplayName(true) + " has disadvantage on "
                    + choice.name().charAt(0) + choice.name().substring(1).toLowerCase() + " checks.", NamedTextColor.GRAY));
        }
        return true;
    }

    /**
     * Begin casting an area spell (#149): enter the aim-and-confirm preview (#173) so the caster can
     * see the shape and who's caught before committing. The spell resolves only on confirm; the
     * caller must NOT spend the action here — {@link #resolveAoeNow} does that on confirm.
     * Returns false so the command layer skips its own action-spend (the confirm handles it).
     */
    public static boolean castAoe(Combatant caster, CombatSession session, Player player, DndSpell spell,
                                  Integer providedRoll, Integer providedTotal) {
        CharacterSheet sheet = caster.getCharacterSheet();
        if (sheet == null) {
            player.sendMessage(Component.text("Only characters cast spells this way.", NamedTextColor.RED));
            return false;
        }
        if (!sheet.knowsSpell(spell)) {
            player.sendMessage(Component.text(sheet.getCharacterName() + " doesn't know " + spell.getName() + ".", NamedTextColor.RED));
            return false;
        }
        Ability ability = sheet.castingAbilityFor(spell);
        if (ability == null) { player.sendMessage(Component.text("No spellcasting ability to cast " + spell.getName() + " with.", NamedTextColor.RED)); return false; }
        Runnable onConfirm = () -> {
            resolveAoeNow(caster, session, player, spell);
            TurnState ts = caster.getTurnState();
            if (ts != null && !ts.isActionUsed()) ts.useAction();
            session.sendActionBar(caster);
        };
        AreaTargeting.begin(player, session, caster, spell.getName(), spell.getAoeShape(), spell.getAoeSize(),
                spell.getAoeTargets(), onConfirm);
        return false; // preview started; the action is spent on confirm
    }

    /** Resolve an area spell against everyone caught right now (run on aim-confirm). */
    private static void resolveAoeNow(Combatant caster, CombatSession session, Player player, DndSpell spell) {
        CharacterSheet sheet = caster.getCharacterSheet();
        if (sheet == null) return;
        Ability ability = sheet.castingAbilityFor(spell);
        if (ability == null) return;
        int mod = sheet.getProficiencyBonus() + sheet.getModifier(ability);

        java.util.List<Combatant> affected = creaturesInArea(caster, player, spell.getAoeShape(), spell.getAoeSize());
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

        if (affected.isEmpty()) return;

        if (spell.isSaveSpell()) {
            Ability saveAbility = parseAbility(spell.getSaveType());
            if (saveAbility == null) { player.sendMessage(Component.text("Invalid save type.", NamedTextColor.RED)); return; }
            int dc = 8 + mod;
            session.broadcast(Component.text("DC " + dc + " " + saveAbility.getAbbreviation() + " save — each caught creature rolls:", NamedTextColor.GRAY));
            for (Combatant t : affected) {
                pendingSaves.put(t.getId(), new PendingSave(spell.getName(), caster.getId(), dc, saveAbility,
                        spell.getDamage(), spell.getDamageType(), spell.getSaveEffect(), spell.getConditionOnFail(), saveTagsFor(spell)));
                promptSave(session, t, saveAbility);
            }
        } else {
            session.broadcast(Component.text("(no save defined — the DM applies the effect)", NamedTextColor.DARK_GRAY));
        }
    }

    /**
     * Generic area-save resolution (#70): project an area of {@code shape}/{@code sizeFeet} from the
     * caster, catch the right creatures, and open a save-for-half (or save-negates) window on each.
     * Shared by spell AoE and by feature actions such as a dragonborn's breath weapon — the caller
     * supplies the DC and damage, so no ability is hardcoded here.
     */
    public static boolean castAreaSave(Combatant caster, CombatSession session, Player player,
                                       String sourceName, String shape, double sizeFeet, String targets,
                                       Ability saveAbility, int dc, String damage, String damageType,
                                       String saveEffect) {
        java.util.List<Combatant> affected = creaturesInArea(caster, player, shape, sizeFeet);
        affected.removeIf(c -> c.getId().equals(caster.getId()) || c.isDead());
        if (targets != null && !"all".equalsIgnoreCase(targets)) {
            boolean wantAllies = "allies".equalsIgnoreCase(targets);
            affected.removeIf(c -> (c.isPlayer() == caster.isPlayer()) != wantAllies);
        }
        for (Combatant t : affected) markAoeTarget(t);

        session.broadcast(Component.empty());
        session.broadcast(Component.text("💥 " + caster.getDisplayName(true) + " unleashes " + sourceName
                + " (" + shape + ", " + (int) sizeFeet + " ft) — " + affected.size()
                + " creature" + (affected.size() == 1 ? "" : "s") + " caught!", NamedTextColor.GOLD));

        if (affected.isEmpty()) return true;

        session.broadcast(Component.text("DC " + dc + " " + saveAbility.getAbbreviation()
                + " save — each caught creature rolls:", NamedTextColor.GRAY));
        java.util.Set<String> tags = damageType != null && !damageType.isBlank()
                ? java.util.Set.of(damageType.toLowerCase()) : java.util.Set.of();
        for (Combatant t : affected) {
            pendingSaves.put(t.getId(), new PendingSave(sourceName, caster.getId(), dc, saveAbility,
                    damage, damageType, saveEffect, null, tags));
            promptSave(session, t, saveAbility);
        }
        return true;
    }

    /** Flag a creature caught in an AoE with a magical particle burst so the caster sees who's hit. */
    private static void markAoeTarget(Combatant c) {
        org.bukkit.Location loc = c.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(org.bukkit.Particle.WITCH, loc.clone().add(0, 1.2, 0), 25, 0.3, 0.7, 0.3, 0.03);
    }

    /** Public view of who an area of {@code shape}/{@code sizeFeet} catches — used by the aim preview (#173). */
    public static java.util.List<Combatant> combatantsInArea(Combatant caster, Player player, String shapeName, double sizeFeet) {
        return creaturesInArea(caster, player, shapeName, sizeFeet);
    }

    /** Combatants inside an area of the given shape/size (feet), originating from the caster. */
    private static java.util.List<Combatant> creaturesInArea(Combatant caster, Player player, String shapeName, double sizeFeet) {
        java.util.List<Combatant> result = new java.util.ArrayList<>();
        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session == null) return result;
        org.bukkit.Location origin = caster.getLocation();
        if (origin == null) return result;
        double size = sizeFeet / 5.0; // feet → blocks
        String shape = shapeName.toLowerCase();
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
        String adds = target.saveBreakdown(ability);
        if (target.isPlayer() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(RollPrompt.line("🛡 Roll a " + ability.getAbbreviation() + " saving throw:",
                    NamedTextColor.GOLD, "/combat save ", "d20", adds));
        } else {
            // Entity: the DM rolls the save for it.
            session.sendToDM(RollPrompt.line("🛡 Roll " + target.getDisplayName(true) + "'s " + ability.getAbbreviation() + " save:",
                    NamedTextColor.GOLD, "/combat save " + quoted(target.getDisplayName()) + " ", "d20", adds));
        }
    }

    /** Resolve a pending save for {@code target}. Players roll their own; the DM rolls for entities. */
    public static void resolveSave(Player roller, CombatSession session, Combatant target,
                                   Integer providedRoll, Integer providedTotal, boolean forceAuto) {
        PendingSave ps = pendingSaves.get(target.getId());
        if (ps == null) {
            roller.sendMessage(Component.text(target.getDisplayName() + " has no pending save.", NamedTextColor.RED));
            return;
        }
        int bonus = saveBonus(target, ps.ability());
        // Advantage/disadvantage on the save from conditions + racial conditional advantages (#103/#174).
        Advantage advantage = target.saveAdvantage(ps.ability(), ps.saveTags());
        if (advantage != Advantage.NONE) {
            roller.sendMessage(Component.text("↯ " + target.getDisplayName() + " rolls this save with "
                    + advantage.label() + ".", advantage.isAdvantage() ? NamedTextColor.GREEN : advantage.isDisadvantage() ? NamedTextColor.RED : NamedTextColor.GRAY));
        }
        String label = target.saveBreakdown(ps.ability());
        RollService.RollResult r = RollService.resolve(providedRoll, providedTotal, bonus,
                label, target.rerollsNat1(), advantage, forceAuto);
        if (r == null) {
            roller.sendMessage(RollPrompt.again(roller, "🛡 Roll " + target.getDisplayName() + "'s " + ps.ability().getAbbreviation() + " save:", "d20", label));
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
                AttackHandler.promptDamage(session, damageSource, target, ps.damage(), ps.damageType(), false, flatLabel(ps.damage(), ps.spellName()));
                if (damageSource.getTurnState() != null) damageSource.getTurnState().setPendingDamageHalf(true);
            } else {
                session.broadcast(Component.text(target.getDisplayName(true) + " shrugs it off.", NamedTextColor.GRAY));
            }
            return;
        }
        // Failed save: full damage + any condition.
        if (ps.damage() != null) AttackHandler.promptDamage(session, damageSource, target, ps.damage(), ps.damageType(), false, flatLabel(ps.damage(), ps.spellName()));
        DndCondition cond = ps.conditionOnFail() != null ? ConditionLoader.get(ps.conditionOnFail()) : null;
        if (cond != null && target.addCondition(cond.getId())) {
            // Incapacitated ends concentration outright, no save (PHB 203).
            if (target.cannotAct()) ConcentrationManager.onIncapacitated(session, target, "they were " + cond.getName().toLowerCase());
            session.setConditionEffect(target, cond, true);
            session.broadcast(Component.text(target.getDisplayName(true) + " is now " + cond.getName() + "!", NamedTextColor.YELLOW));
            session.updateScoreboard();
        }
    }

    public static boolean hasPendingSave(UUID targetId) { return pendingSaves.containsKey(targetId); }

    // ==================== HELPERS ====================

    /** A healing roll's amount and its result line. */
    record HealRoll(int amount, String work) {}

    /** What a healing spell adds to its dice: its own flat part if any ("+4[…]"), then the caster's modifier ("+3[WIS]"). */
    static String healBonus(CharacterSheet sheet, DndSpell spell) {
        String own = RollPrompt.split(spell.getHealing(), spell.getName()).label();
        return (own != null ? own + " " : "") + sheet.getSpellModBreakdown(spell);
    }

    /** The dice a healing spell rolls, without its flat part ("1d8"). */
    static String healDice(DndSpell spell) {
        return RollPrompt.split(spell.getHealing(), spell.getName()).dice();
    }

    /**
     * <b>The</b> healing roll, in or out of combat: a total as given, your dice plus what's added, or
     * the game's roll. Null when nothing was given in physical-dice mode (the caller prompts).
     */
    static HealRoll healRoll(CharacterSheet sheet, DndSpell spell, Integer providedRoll, Integer providedTotal, boolean forceAuto) {
        Ability ability = sheet.castingAbilityFor(spell);
        int mod = ability != null ? sheet.getModifier(ability) : 0;
        RollPrompt.Formula f = RollPrompt.split(spell.getHealing(), spell.getName());
        String bonus = healBonus(sheet, spell);
        if (providedTotal != null) return new HealRoll(Math.max(1, providedTotal), RollPrompt.yourTotal(providedTotal));
        if (providedRoll != null) {
            int amount = providedRoll + f.flat() + mod;
            return new HealRoll(Math.max(1, amount), RollPrompt.youRolled(providedRoll, bonus, amount));
        }
        if (!forceAuto && !io.papermc.jkvttplugin.config.PluginConfig.isAutoRoll()) return null;
        io.papermc.jkvttplugin.util.DiceRoller.Rolled r = io.papermc.jkvttplugin.util.DiceRoller.rollOrFlat(spell.getHealing());
        int amount = (r == null ? 0 : r.total()) + mod;
        String shown = r == null ? "0" : r.dice().isEmpty() ? String.valueOf(r.total()) : r.dice().toString();
        return new HealRoll(Math.max(1, amount), RollPrompt.gameRolled(f.dice(), shown, bonus, amount));
    }

    /** A spell formula's own flat part, labelled with the spell ("+1[Magic Missile]"), or "" if none. */
    private static String flatLabel(String damage, String spellName) {
        String label = RollPrompt.split(damage, spellName).label();
        return label == null ? "" : label;
    }

    private static int saveBonus(Combatant c, Ability ability) {
        if (c.isPlayer() && c.getCharacterSheet() != null) return c.getCharacterSheet().getSavingThrowBonus(ability);
        if (c.isEntity() && c.getEntityInstance() != null) {
            return Ability.getModifier(c.getEntityInstance().getTemplate().getAbilityScore(ability));
        }
        return 0;
    }

    /**
     * Prompt a caster to roll their healing dice (physical mode). Built on the command they typed,
     * so an upcast ("level 2") survives into the roll.
     */
    private static void promptHealingRoll(Player player, CharacterSheet sheet, Combatant target, DndSpell spell) {
        player.sendMessage(RollPrompt.again(player, "💚 Roll " + spell.getName() + " (" + spell.getHealing() + ") on "
                + target.getDisplayName() + ":", healDice(spell), healBonus(sheet, spell)));
    }

    /** Range error for a single-target spell, or null if in range / unknown. Touch=5 ft, Self=self only. */
    private static String spellRangeError(Combatant caster, Combatant target, DndSpell spell) {
        int rangeFeet = parseSpellRange(spell.getRange());
        if (rangeFeet < 0) return null; // unknown/unlimited → don't enforce
        if (rangeFeet == 0) {
            return target.getId().equals(caster.getId()) ? null : spell.getName() + " only targets you (range: Self).";
        }
        org.bukkit.Location a = caster.getLocation(), t = target.getLocation();
        if (a == null || t == null || a.getWorld() == null || !a.getWorld().equals(t.getWorld())) return null;
        double feet = a.distance(t) * 5.0;
        if (feet > rangeFeet + 2.5) {
            String r = rangeFeet == 5 ? "touch" : rangeFeet + " ft";
            return target.getDisplayName() + " is out of range — " + Math.round(feet) + " ft away (" + spell.getName() + " range: " + r + ").";
        }
        return null;
    }

    /** Parse a spell's range string to feet: Self→0, Touch→5, "60 feet"→60; -1 if unknown. */
    private static int parseSpellRange(String range) {
        if (range == null) return -1;
        String r = range.trim().toLowerCase();
        if (r.startsWith("self")) return 0;
        if (r.startsWith("touch")) return 5;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(r);
        if (m.find()) return Integer.parseInt(m.group(1));
        return -1;
    }

    /**
     * Roll a spell's dice ("1d8", "1d4+4") or read a flat number ("5"); 0 if unreadable. The dice are
     * shown to the table, since the game rolled them.
     */
    private static int rollAmount(String expr, CombatSession session) {
        io.papermc.jkvttplugin.util.DiceRoller.Rolled rolled = io.papermc.jkvttplugin.util.DiceRoller.rollOrFlat(expr);
        if (rolled == null) return 0;
        if (session != null) session.broadcast(Component.text(rolled.display(), NamedTextColor.GRAY));
        return rolled.total();
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
