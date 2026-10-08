package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import io.papermc.jkvttplugin.data.model.DndCondition;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.sound.Sounds;
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




    /**
     * @param castId this cast's identity ({@code SpellEffects.newCast()}), the same for every target of one cast:
     *               its effects and the saves it leaves pending carry it (#269)
     * @return true if the spell actually resolved (so the action is spent)
     */
    public static boolean cast(Combatant caster, Combatant target, CombatSession session, Player player,
                               DndSpell spell, Integer providedRoll, Integer providedTotal, boolean forceAuto, long castId) {
        CharacterSheet sheet = caster.getCharacterSheet();
        if (sheet == null) {
            player.sendMessage(Component.text("Only characters cast spells this way (an entity's spells are attacks — use /combat attack).", NamedTextColor.RED));
            return false;
        }
        String refusal = io.papermc.jkvttplugin.character.PreparedSpells.castRefusal(sheet, spell, false); // known and prepared (#218)
        if (refusal != null) {
            player.sendMessage(Component.text(refusal, NamedTextColor.RED));
            return false;
        }
        Ability ability = sheet.castingAbilityFor(spell);
        if (ability == null) {
            player.sendMessage(Component.text("No spellcasting ability to cast " + spell.getName() + " with.", NamedTextColor.RED));
            return false;
        }
        if (!reaches(caster, target, player, spell)) return false;
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
            session.broadcast(spell.castLine("✨ " + caster.getDisplayName(true) + " casts ", " on " + target.getDisplayName(true) + ".", NamedTextColor.LIGHT_PURPLE));
            SpellVisuals.play(spell, caster.getLocation(), target.getLocation()); // how it looks (#230)
            if (work != null) session.broadcast(Component.text(work, NamedTextColor.GRAY));
            if (healAmount != null) DamageHandler.applyHealing(session, target, healAmount);
            if (spell.grantsTempHp()) DamageHandler.applyTempHp(session, target, Math.max(0, rollAmount(spell.getTempHp(), session)));
            return true;
        }

        // Auto-hit spells (Magic Missile): no attack roll, no save — straight to the damage step.
        if (spell.isAutoHit()) {
            session.broadcast(Component.empty());
            session.broadcast(spell.castLine("✨ " + caster.getDisplayName(true) + " casts ", " at " + target.getDisplayName(true) + " — it hits automatically.", NamedTextColor.LIGHT_PURPLE));
            SpellVisuals.play(spell, caster.getLocation(), target.getLocation()); // how it looks (#230)
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
                player.sendMessage(RollPrompt.again(player, "✨ Roll to hit with " + spell.getName() + ":", RollPrompt.d20(advantage), attackLabel));
                return false;
            }
            caster.afterAttackRoll(session); // a Help is used up; a hidden caster is revealed (#176)
            SpellEffects.useUp(caster, io.papermc.jkvttplugin.effect.ActiveEffect.ATTACKS); // a creature's Bardic Inspiration (a character's is asked for after the roll, #40)
            int ac = target.getArmorClass();
            boolean hit = RollService.hits(r, ac);
            session.broadcast(Component.empty());
            session.broadcast(spell.castLine("✨ " + caster.getDisplayName(true) + " casts ", " at " + target.getDisplayName(true) + "!", NamedTextColor.LIGHT_PURPLE));
            SpellVisuals.play(spell, caster.getLocation(), target.getLocation()); // how it looks (#230)
            session.broadcast(Component.text("Spell attack: " + r.breakdown() + " vs AC " + ac, NamedTextColor.GRAY));
            if (hit) {
                session.broadcast(Component.text(r.nat20() ? "★ CRITICAL HIT! ★" : "HIT!", NamedTextColor.GREEN, TextDecoration.BOLD));
                if (!r.nat20()) Sounds.table(Sounds.HIT, session, target.getLocation()); // #16
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
                if (!r.nat1()) Sounds.table(Sounds.MISS, session, target.getLocation()); // #16
                // Bardic Inspiration after the roll (#40): a higher total that beats the AC is a hit after all.
                if (!r.nat1()) {
                    final TurnState turnAtRoll = caster.getTurnState();
                    InspirationPrompt.offer(sheet, spell.getName() + " attack", r.total(), newTotal -> {
                        int nowAc = target.getArmorClass();
                        if (newTotal < nowAc) {
                            session.broadcast(Component.text("   Still a miss (AC " + nowAc + ").", NamedTextColor.RED));
                        } else if (caster.getTurnState() != turnAtRoll) {
                            session.broadcast(Component.text("   That's a hit, but the turn has moved on: the DM applies the damage.", NamedTextColor.YELLOW));
                        } else {
                            session.broadcast(Component.text("HIT!", NamedTextColor.GREEN, TextDecoration.BOLD));
                            Sounds.table(Sounds.HIT, session, target.getLocation()); // #16
                            String dmg = spell.getDamage();
                            AttackHandler.promptDamage(session, caster, target, dmg == null ? "" : dmg,
                                    spell.getDamageType(), false, flatLabel(dmg, spell.getName()), newTotal);
                        }
                    }, InspirationPrompt.table(session));
                }
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
            session.broadcast(spell.castLine("✨ " + caster.getDisplayName(true) + " casts ", " at " + target.getDisplayName(true) + " — DC " + sheet.getSpellSaveDcBreakdown(spell) + " " + saveAbility.getAbbreviation() + " save!", NamedTextColor.LIGHT_PURPLE));
            SpellVisuals.play(spell, caster.getLocation(), target.getLocation()); // how it looks (#230)
            // The save's facts, effect included: Bane's -1d4 lands on a failed save (#225).
            leavePending(session, target, SpellSave.Facts.of(spell, caster.getId(), sheet.getCharacterId(), dc, saveAbility, SpellSave.tagsFor(spell), castId));
            return true;
        }

        // A timed effect on the target (#225): Bless, Guidance, Shield of Faith. It lasts its rounds, or until
        // the caster's concentration ends (afterCast starts that).
        if (spell.hasEffect()) {
            SpellEffects.apply(sheet.getCharacterId(), target, spell, castId);
            session.broadcast(spell.castLine("✨ " + caster.getDisplayName(true) + " casts ", " on " + target.getDisplayName(true)
                    + ": " + SpellEffects.describe(spell) + ".", NamedTextColor.LIGHT_PURPLE));
            SpellVisuals.play(spell, caster.getLocation(), target.getLocation()); // how it looks (#230)
            session.updateScoreboard();
            return true;
        }

        // Utility / non-damaging spell — announce; the DM narrates the effect (full utility casting is #152).
        session.broadcast(spell.castLine("✨ " + caster.getDisplayName(true) + " casts ", ".", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /**
     * Cast a mark/curse spell (Hex, Hunter's Mark — #178). The mark is the caster's rider damage, so it
     * lives on the CASTER: while they concentrate, they deal the rider damage on hits against the
     * marked target, and (Hex) the target has disadvantage on checks with the chosen ability.
     *
     * @return whom to mark, for the caller to complete the cast with; null when it was refused
     */
    public static CastCompletion.Mark castMark(Combatant caster, Combatant target, CombatSession session, Player player,
                                               DndSpell spell, Ability choice) {
        CharacterSheet sheet = caster.getCharacterSheet();
        if (sheet == null) { player.sendMessage(Component.text("Only characters cast this spell.", NamedTextColor.RED)); return null; }
        // Known and prepared, like every other cast path: a spare slot was enough to cast an unknown Hex (#236).
        String refusal = io.papermc.jkvttplugin.character.PreparedSpells.castRefusal(sheet, spell, false);
        if (refusal != null) { player.sendMessage(Component.text(refusal, NamedTextColor.RED)); return null; }
        if (!reaches(caster, target, player, spell)) return null; // Hex reaches 90 ft; it wasn't checked at all
        // Nothing is changed here any more: concentration and the mark are set when the cast completes
        // (CastCompletion, #269), with the slot. This only says what happened and hands back whom to mark.

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
        return CastCompletion.Mark.of(spell, target.getId(), choice);
    }
    /**
     * Begin casting an area spell (#149): enter the aim-and-confirm preview (#173) so the caster can
     * see the shape and who's caught before committing. The spell resolves only on confirm; the
     * caller must NOT spend the action here — {@link #resolveAoeNow} does that on confirm.
     * Returns false so the command layer skips its own action-spend (the confirm handles it).
     */
    public static boolean castAoe(Combatant caster, CombatSession session, Player player, DndSpell spell,
                                  Integer providedRoll, Integer providedTotal, Runnable afterConfirm,
                                  java.util.function.Supplier<String> recheck, long castId) {
        CharacterSheet sheet = caster.getCharacterSheet();
        if (sheet == null) {
            player.sendMessage(Component.text("Only characters cast spells this way.", NamedTextColor.RED));
            return false;
        }
        String refusal = io.papermc.jkvttplugin.character.PreparedSpells.castRefusal(sheet, spell, false); // known and prepared (#218)
        if (refusal != null) {
            player.sendMessage(Component.text(refusal, NamedTextColor.RED));
            return false;
        }
        Ability ability = sheet.castingAbilityFor(spell);
        if (ability == null) { player.sendMessage(Component.text("No spellcasting ability to cast " + spell.getName() + " with.", NamedTextColor.RED)); return false; }
        Runnable onConfirm = () -> {
            resolveAoeNow(caster, session, player, spell, castId);
            afterConfirm.run(); // the slot, concentration and the action, as for any cast (#179)
        };
        AreaTargeting.begin(player, session, caster, spell.getName(), spell.getAoeShape(), spell.getAoeSize(),
                spell.getAoeTargets(), onConfirm, recheck);
        return false; // preview started; the action is spent on confirm
    }

    /** Resolve an area spell against everyone caught right now (run on aim-confirm). */
    private static void resolveAoeNow(Combatant caster, CombatSession session, Player player, DndSpell spell, long castId) {
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
        session.broadcast(spell.castLine("✨ " + caster.getDisplayName(true) + " casts ", " (" + spell.getAoeShape() + ", " + spell.getAoeSize() + " ft) — " + affected.size()
                + " creature" + (affected.size() == 1 ? "" : "s") + " caught!", NamedTextColor.LIGHT_PURPLE));

        if (affected.isEmpty()) return;

        if (spell.isSaveSpell()) {
            Ability saveAbility = parseAbility(spell.getSaveType());
            if (saveAbility == null) { player.sendMessage(Component.text("Invalid save type.", NamedTextColor.RED)); return; }
            int dc = 8 + mod;
            session.broadcast(Component.text("DC " + dc + " " + saveAbility.getAbbreviation() + " save — each caught creature rolls:", NamedTextColor.GRAY));
            for (Combatant t : affected) {
                leavePending(session, t, SpellSave.Facts.of(spell, caster.getId(), sheet.getCharacterId(), dc, saveAbility, SpellSave.tagsFor(spell), castId));
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
            leavePending(session, t, new SpellSave.Facts(sourceName, caster.getId(), null, dc, saveAbility,
                    damage, damageType, saveEffect, null, tags, null, 0)); // a feature's area (a breath weapon): damage only, no cast
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
    /**
     * A spell leaves {@code target} a save to make (#273): a save request of its own ({@link SaveOutcome}), so
     * a second spell at the same creature is a second save, not a replacement. Its id rides in the roll
     * buttons; answering it runs this spell's outcome and no other's. It used to be one slot per target.
     */
    private static void leavePending(CombatSession session, Combatant target, SpellSave.Facts facts) {
        String request = SaveOutcome.awaitInFight(target.getId(), facts.dc(), facts.ability(), facts.saveTags(), facts.spellName(),
                saved -> applyOutcome(session, target, facts, saved));
        promptSave(session, target, SaveOutcome.find(request));
    }

    /** Send the target's controller the roll buttons for one save it owes; they carry that save's request id. */
    private static void promptSave(CombatSession session, Combatant target, SaveOutcome.Request save) {
        Ability ability = save.ability();
        String adds = target.saveBreakdown(ability);
        // The same advantage the resolve step applies (conditions, Fey Ancestry…), so [My total…] says how to roll.
        String dice = RollPrompt.d20(target.saveAdvantage(ability, save.tags()));
        String against = save.label() != null ? " against " + save.label() : "";
        if (target.isPlayer() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(RollPrompt.line("🛡 Roll a " + ability.getAbbreviation() + " saving throw" + against + ":",
                    NamedTextColor.GOLD, "/combat save request " + save.id() + " ", dice, adds));
        } else {
            // Entity: the DM rolls the save for it.
            session.sendToDM(RollPrompt.line("🛡 Roll " + target.getDisplayName(true) + "'s " + ability.getAbbreviation() + " save" + against + ":",
                    NamedTextColor.GOLD, "/combat save " + quoted(target.getDisplayName()) + " request " + save.id() + " ", dice, adds));
        }
    }

    /**
     * Which of the saves {@code targetId} owes an answer is for. With a request id: that one, or why not (it's
     * gone, or it's someone else's). With none: the only one they owe; {@code several} when they owe more than
     * one, for the caller to ask; a refusal when they owe none. Pure, so the isolation can be tested.
     */
    record Picked(SaveOutcome.Request save, String refusal, java.util.List<SaveOutcome.Request> several) {}

    static Picked pickSave(UUID targetId, String targetName, String requestId) {
        if (requestId != null) {
            SaveOutcome.Request r = SaveOutcome.find(requestId);
            String refusal = SaveOutcome.refusal(requestId, targetId, true, r != null ? r.ability() : null, r != null ? r.dc() : null);
            if (refusal == null && !r.inFight()) refusal = "That save isn't one a fight is waiting on.";
            return refusal != null ? new Picked(null, refusal, java.util.List.of()) : new Picked(r, null, java.util.List.of());
        }
        java.util.List<SaveOutcome.Request> owed = SaveOutcome.inFightFor(targetId);
        if (owed.isEmpty()) return new Picked(null, targetName + " has no pending save.", java.util.List.of());
        if (owed.size() == 1) return new Picked(owed.get(0), null, java.util.List.of());
        return new Picked(null, null, owed);
    }

    /**
     * Resolve one save {@code target} owes. Players roll their own; the DM rolls for entities.
     *
     * @param requestId which save, from the roll buttons; null when the command was typed bare
     */
    public static void resolveSave(Player roller, CombatSession session, Combatant target,
                                   Integer providedRoll, Integer providedTotal, boolean forceAuto, String requestId) {
        // Which save this answers is settled first, before anything is rolled or a one-use effect is spent:
        // a stale id (answered, or from a fight that's over) resolves nothing (#273).
        Picked picked = pickSave(target.getId(), target.getDisplayName(), requestId);
        if (picked.refusal() != null) {
            roller.sendMessage(Component.text(picked.refusal(), NamedTextColor.RED));
            return;
        }
        if (picked.save() == null) {
            roller.sendMessage(Component.text(target.getDisplayName() + " owes " + picked.several().size() + " saves. Which one?", NamedTextColor.YELLOW));
            for (SaveOutcome.Request owed : picked.several()) promptSave(session, target, owed);
            return;
        }
        SaveOutcome.Request ps = picked.save();
        int bonus = saveBonus(target, ps.ability());
        // Advantage/disadvantage on the save from conditions + racial conditional advantages (#103/#174).
        Advantage advantage = target.saveAdvantage(ps.ability(), ps.tags());
        if (advantage != Advantage.NONE) {
            roller.sendMessage(Component.text("↯ " + target.getDisplayName() + " rolls this save with "
                    + advantage.label() + ".", advantage.isAdvantage() ? NamedTextColor.GREEN : advantage.isDisadvantage() ? NamedTextColor.RED : NamedTextColor.GRAY));
        }
        String label = target.saveBreakdown(ps.ability());
        RollService.RollResult r = RollService.resolve(providedRoll, providedTotal, bonus,
                label, target.rerollsNat1(), advantage, forceAuto);
        if (r == null) {
            roller.sendMessage(RollPrompt.again(roller, "🛡 Roll " + target.getDisplayName() + "'s " + ps.ability().getAbbreviation() + " save:", RollPrompt.d20(advantage), label));
            return;
        }
        SpellEffects.useUp(target, io.papermc.jkvttplugin.effect.ActiveEffect.SAVES); // Resistance, once (#225)
        boolean success = SpellSave.saved(r.total(), ps.dc()); // grading; what it does is applyOutcome, below

        session.broadcast(Component.text(target.getDisplayName(true) + " " + ps.ability().getAbbreviation()
                + " save" + (ps.label() != null ? " against " + ps.label() : "") + ": " + r.breakdown() + " vs DC " + ps.dc()
                + " → " + (success ? "SUCCESS" : "FAIL"),
                success ? NamedTextColor.GREEN : NamedTextColor.RED));
        // A failed save may still be saved by Bardic Inspiration (#40); the DM rules on the new total.
        if (!success && target.getCharacterSheet() != null) {
            InspirationPrompt.offer(target.getCharacterSheet(), ps.ability().getAbbreviation() + " save (DC " + ps.dc() + ")", r.total(), null,
                    InspirationPrompt.table(session));
        }
        SaveOutcome.graded(ps.id(), success); // this request's outcome, once: applyOutcome for the spell that left it
    }

    /**
     * What a graded save does in a fight. The consequences are the shared ones (SpellSave, #267); rolling the
     * damage and telling the table are this path's own: the caster's /combat damage step, and a broadcast.
     */
    private static void applyOutcome(CombatSession session, Combatant target, SpellSave.Facts ps, boolean success) {
        Combatant caster = findById(session, ps.casterId());
        Combatant damageSource = caster != null ? caster : target;
        SpellSave.Outcome outcome = SpellSave.apply(ps, SpellSave.subject(target, session, target.getDisplayName(true)), success);
        if (success) {
            if (outcome.damage() == SpellSave.Owed.HALF) {
                session.broadcast(Component.text(ps.spellName() + " deals half on a save.", NamedTextColor.GRAY));
                AttackHandler.promptDamage(session, damageSource, target, ps.damage(), ps.damageType(), false, flatLabel(ps.damage(), ps.spellName()));
                if (damageSource.getTurnState() != null) damageSource.getTurnState().setPendingDamageHalf(true);
            } else {
                session.broadcast(Component.text(target.getDisplayName(true) + " shrugs it off.", NamedTextColor.GRAY));
            }
            return;
        }
        // Failed save: the effect, full damage, then any condition.
        for (Component line : outcome.effectLines()) session.broadcast(line);
        if (outcome.damage() == SpellSave.Owed.FULL) {
            AttackHandler.promptDamage(session, damageSource, target, ps.damage(), ps.damageType(), false, flatLabel(ps.damage(), ps.spellName()));
        }
        for (Component line : outcome.conditionLines()) session.broadcast(line);
        if (outcome.conditionApplied()) session.updateScoreboard();
    }
    public static boolean hasPendingSave(UUID targetId) { return !SaveOutcome.inFightFor(targetId).isEmpty(); }
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
        DiceAmount.Result r = DiceAmount.resolve(new RollService.RollInput(providedRoll, providedTotal, forceAuto),
                spell.getHealing(), spell.getName(), mod, sheet.getSpellModBreakdown(spell),
                io.papermc.jkvttplugin.config.PluginConfig.isAutoRoll());
        if (r.status() == DiceAmount.Status.NEEDS_ROLL) return null;
        if (r.status() == DiceAmount.Status.BAD_DICE) {
            // Healing dice the roller can't read count as 0, so the spell still heals its modifier (as before).
            return new HealRoll(Math.max(1, mod), RollPrompt.gameRolled(RollPrompt.split(spell.getHealing(), spell.getName()).dice(),
                    "0", healBonus(sheet, spell), mod));
        }
        return new HealRoll(Math.max(1, r.amount()), r.work()); // a healing spell never heals less than 1
    }

    /** A spell formula's own flat part, labelled with the spell ("+1[Magic Missile]"), or "" if none. */
    private static String flatLabel(String damage, String spellName) {
        String label = RollPrompt.split(damage, spellName).label();
        return label == null ? "" : label;
    }

    private static int saveBonus(Combatant c, Ability ability) {
        if (c.isPlayer() && c.getCharacterSheet() != null) return c.getCharacterSheet().getSavingThrowBonus(ability);
        if (c.isEntity() && c.getEntityInstance() != null) {
            return c.getEntityInstance().getTemplate().getSaveBonus(ability); // its listed save, else the modifier (#252)
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

    /**
     * Does a single-target spell reach? The one rule ({@link Reach}); when it doesn't, the caster is
     * told why and offered [Ask the DM] ([Do it anyway] for a DM), which hands back this command.
     */
    private static boolean reaches(Combatant caster, Combatant target, Player player, DndSpell spell) {
        String why = Reach.spell(caster.getLocation(), target.getLocation(), target.getDisplayName(),
                target.getId().equals(caster.getId()), spell);
        String what = "spell:" + spell.getId();
        if (why == null || Reach.isAllowed(player.getUniqueId(), what, target.getId())) return true;
        String retry = RollPrompt.lastCommand(player);
        Reach.refuse(player, why, what, target.getId(), spell.getName() + " on " + target.getDisplayName(),
                retry != null ? retry : "/combat cast " + spell.getId() + " " + quoted(target.getDisplayName()));
        return false;
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
