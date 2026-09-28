package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles attack roll logic for the combat system (Issue #99).
 *
 * Supports three input modes (#183):
 * - autoRoll: the game rolls the d20 (with advantage) and adds modifiers
 * - manualRoll N: player provides their physical d20 result, the game adds modifiers
 * - total N: player provides a final total, compared directly to AC
 *
 * Damage dice are rolled and displayed on hit, but NOT applied to HP (Issue #100).
 */
public class AttackHandler {

    private static final Pattern DICE_COUNT_PATTERN = Pattern.compile("(\\d*)d(\\d+)");

    // ==================== PLAYER ATTACKS ====================

    /**
     * Execute a player character's attack against a target.
     */
    public static boolean executePlayerAttack(Combatant attacker, Combatant target,
                                           CombatSession session, Player player,
                                           String weaponId, Integer providedRoll,
                                           Integer providedTotal, boolean showMods, boolean forceAuto) {
        return executePlayerAttack(attacker, target, session, player, weaponId, providedRoll, providedTotal,
                showMods, forceAuto, false);
    }

    /**
     * @param offHand a two-weapon-fighting bonus attack (PHB p.195): no positive ability modifier on
     *                the damage. A Martial Arts bonus strike isn't one; it adds the modifier as usual.
     */
    public static boolean executePlayerAttack(Combatant attacker, Combatant target,
                                           CombatSession session, Player player,
                                           String weaponId, Integer providedRoll,
                                           Integer providedTotal, boolean showMods, boolean forceAuto,
                                           boolean offHand) {
        CharacterSheet sheet = attacker.getCharacterSheet();
        if (sheet == null) {
            player.sendMessage(Component.text("No active character found.", NamedTextColor.RED));
            return false;
        }

        // An explicitly named weapon that resolves to nothing is a mistake (typo, or a weapon
        // the player doesn't have). Fail loudly instead of silently downgrading to an unarmed
        // strike — otherwise the player makes a wrong attack without knowing it. (#109)
        if (weaponId != null && !weaponId.isBlank()
                && !"unarmed".equalsIgnoreCase(weaponId)
                && WeaponLoader.getWeapon(weaponId.toLowerCase()) == null) {
            player.sendMessage(Component.text("Unknown weapon '" + weaponId
                    + "'. Use a weapon you have, 'unarmed', or omit it to use your main hand.",
                    NamedTextColor.RED));
            return false;
        }

        // Resolve weapon
        DndWeapon weapon = resolvePlayerWeapon(player, weaponId);
        // weapon == null means unarmed strike (no explicit name, or an empty main hand)

        // Calculate attack modifier
        int attackMod = calculatePlayerAttackMod(sheet, weapon);
        String modBreakdown = buildPlayerModBreakdown(sheet, weapon);

        // --showmods: just show the breakdown, don't attack (and don't spend the action)
        if (showMods) {
            showAttackModifiers(player, attacker, weapon, attackMod, modBreakdown);
            return false;
        }

        // Build damage string, then hand off to the shared resolver. The off-hand attack drops a positive
        // ability modifier, unless the Two-Weapon Fighting style puts it back (#229).
        boolean dropOffHandMod = offHand && !sheet.hasPassiveFlag("offhand_ability_damage");
        String damageStr = buildPlayerDamageString(sheet, weapon, dropOffHandMod);
        String damageType = (weapon != null) ? weapon.getDamageType() : "bludgeoning";

        // Effect bonus damage by what kind of swing this is: a melee STR swing (Rage, #70; unarmed
        // counts), and one melee weapon with nothing else (Dueling: a shield is fine, #229).
        boolean meleeStr = (weapon == null || !weapon.isRanged())
                && resolveAttackAbility(sheet, weapon) == Ability.STRENGTH;
        boolean oneHanded = weapon != null && !weapon.isRanged() && !isTwoHanded(weapon)
                && !offHand && !offHandHoldsWeapon(player);
        List<String> damageTags = new java.util.ArrayList<>();
        if (meleeStr) damageTags.add("melee_str");
        if (oneHanded) damageTags.add("melee_one_handed");
        for (String tag : damageTags) {
            int bonus = attacker.effectBonusDamageFor(tag);
            if (bonus > 0) damageStr = addFlatDamage(damageStr, bonus);
        }
        attacker.markEffectsMaintained("attacked"); // keeps Rage etc. going (#70)

        // Labeled damage-bonus breakdown for clarity (#168): "+5[STR] +2[Rage]".
        Ability dmgAbility = resolveAttackAbility(sheet, weapon);
        int dmgAbilityMod = sheet.getModifier(dmgAbility);
        if (dropOffHandMod) dmgAbilityMod = Math.min(0, dmgAbilityMod); // matches the damage string
        String bonusLabel = dmgAbilityMod != 0
                ? (dmgAbilityMod > 0 ? "+" : "") + dmgAbilityMod + "[" + dmgAbility.getAbbreviation() + "]" : "";
        String magicDmg = magicLabel(weapon, weapon != null ? weapon.getDamageBonus() : 0);
        if (!magicDmg.isEmpty()) bonusLabel = bonusLabel.isEmpty() ? magicDmg : bonusLabel + " " + magicDmg;
        for (String tag : damageTags) {
            String eff = attacker.effectBonusDamageBreakdownFor(tag);
            if (!eff.isEmpty()) bonusLabel = bonusLabel.isEmpty() ? eff : bonusLabel + " " + eff;
        }

        // Great Weapon Fighting (#229): a two-handed melee weapon (or a versatile one with the off hand
        // free) rerolls 1s and 2s on its damage dice, when the game rolls them.
        boolean gwf = sheet.hasPassiveFlag("reroll_low_damage") && weapon != null && !weapon.isRanged()
                && (isTwoHanded(weapon) || (weapon.hasProperty("versatile") && player != null
                        && player.getInventory().getItemInOffHand().getType().isAir()));
        if (attacker.getTurnState() != null) attacker.getTurnState().setRerollLowForNextHit(gwf);

        // Sneak Attack (#229): its dice join the weapon's, so a crit doubles them too.
        SneakAttack.Use sneak = SneakAttack.check(session, attacker, target, sheet, weapon);
        if (sneak != null) damageStr = SneakAttack.addDice(damageStr, sneak.dice());
        final Runnable onHit = () -> {
            if (sneak != null) SneakAttack.spend(session, attacker, target, sneak);
            if (gwf) player.sendMessage(Component.text("Great Weapon Fighting: rolling it yourself? Reroll any 1 or 2 "
                    + "on the damage dice once (the game does it when it rolls).", NamedTextColor.GRAY));
        };

        // Half-Orc Savage Attacks: one extra weapon die on a melee-weapon crit (unarmed doesn't count).
        boolean extraCritDie = weapon != null && !weapon.isRanged() && attacker.hasExtraCritDie();

        // Cosmetic projectile flair on a hit (#181): a fired/thrown weapon sends an arrow/trident.
        String projectile = CombatVisuals.projectileFor(weapon);

        // Ammunition (#128): refuse the shot up front if they're empty, so nothing else happens
        // first. A round is only spent once the attack actually resolves below — resolveAttack
        // returns false while it's still waiting on the player's d20, and that must not cost an
        // arrow (nor does it spend the action, for the same reason).
        if (!AmmunitionManager.hasAmmo(player, weapon)) {
            AmmunitionManager.warnEmpty(player, weapon);
            return false;
        }

        boolean resolved = resolveAttack(session, attacker, target, attackMod, modBreakdown, damageStr, damageType,
                providedRoll, providedTotal, player, bonusLabel, extraCritDie, projectile, forceAuto,
                AmmunitionManager.spentRoundId(weapon),
                weapon != null ? (weapon.getLongRange() > 0 ? weapon.getLongRange() : weapon.getNormalRange()) : 0,
                weapon != null ? weapon.getCritBonusDamage() : 0, weapon != null ? weapon.getName() : null, onHit);
        if (resolved) AmmunitionManager.consume(player, weapon);
        return resolved;
    }

    /** Merge a flat bonus into a damage string's trailing modifier (1d8+3, +2 → 1d8+5), keeping the
     *  single-modifier form the dice parser accepts. */
    private static String addFlatDamage(String dmg, int bonus) {
        if (bonus == 0 || dmg == null) return dmg;
        Matcher m = Pattern.compile("^(.*?d\\d+)\\s*([+\\-]\\s*\\d+)?\\s*$").matcher(dmg.trim());
        if (m.matches()) {
            int existing = (m.group(2) == null) ? 0 : Integer.parseInt(m.group(2).replaceAll("\\s", ""));
            int total = existing + bonus;
            return m.group(1) + (total == 0 ? "" : (total > 0 ? "+" + total : String.valueOf(total)));
        }
        return dmg + "+" + bonus;
    }

    /**
     * Shared attack resolution for players and entities (Issue #139). Given the pre-computed
     * to-hit modifier + its breakdown text and the damage string/type, this rolls (or takes the
     * provided roll/total), compares to AC with nat-20/nat-1 handling, doubles dice on a crit, and
     * broadcasts the result. The two callers differ only in how they produce those inputs.
     */
    private static boolean resolveAttack(CombatSession session, Combatant attacker, Combatant target,
                                      int attackMod, String modBreakdown, String damageStr, String damageType,
                                      Integer providedRoll, Integer providedTotal, Player commandUser) {
        return resolveAttack(session, attacker, target, attackMod, modBreakdown, damageStr, damageType,
                providedRoll, providedTotal, commandUser, "", false, null, false, null, 0, 0, null, null);
    }

    /** @param onHit run once the attack is known to hit, before the damage prompt (Sneak Attack's spend, #229); may be null */
    private static boolean resolveAttack(CombatSession session, Combatant attacker, Combatant target,
                                      int attackMod, String modBreakdown, String damageStr, String damageType,
                                      Integer providedRoll, Integer providedTotal, Player commandUser, String bonusLabel,
                                      boolean extraCritDie, String projectileVisual, boolean forceAuto,
                                      String spentAmmoId, int projectileRangeFeet,
                                      int critBonusDamage, String critBonusSource, Runnable onHit) {
        // Advantage/disadvantage from conditions (#103): auto-applied when the game rolls, and the
        // roller is reminded either way (a physical roll or provided total is trusted as-is).
        Advantage advantage = attacker.attackAdvantageAgainst(target);
        if (commandUser != null) {
            if (advantage != Advantage.NONE) {
                commandUser.sendMessage(Component.text("↯ You have " + advantage.label() + " on this attack.",
                        advantage.isAdvantage() ? NamedTextColor.GREEN : advantage.isDisadvantage() ? NamedTextColor.RED : NamedTextColor.GRAY));
            }
            for (String note : attacker.attackReminders(target)) {
                commandUser.sendMessage(Component.text("  • " + note, NamedTextColor.GRAY));
            }
        }
        RollService.RollResult r = RollService.resolve(providedRoll, providedTotal, attackMod, modBreakdown, attacker.rerollsNat1(), advantage, forceAuto);
        if (r == null) {
            // Physical-roll mode with no die supplied — ask for one and DON'T spend the action.
            commandUser.sendMessage(RollPrompt.again(commandUser, "⚔ Roll to hit " + target.getDisplayName() + ":", RollPrompt.d20(advantage), modBreakdown));
            return false;
        }
        int targetAC = target.getArmorClass();
        boolean hit = RollService.hits(r, targetAC);
        // A crit doubles the weapon dice; Half-Orc Savage Attacks adds one more weapon die on top (#70).
        String finalDamage = damageStr;
        if (r.nat20()) {
            finalDamage = doubleDice(damageStr);
            if (extraCritDie) finalDamage = addOneDie(finalDamage);
            // A flat bonus on a natural 20 only, not doubled (Vicious Weapon, #188).
            if (critBonusDamage != 0) {
                finalDamage = addFlatDamage(finalDamage, critBonusDamage);
                String tag = (critBonusDamage > 0 ? "+" : "") + critBonusDamage + "[" + (critBonusSource != null ? critBonusSource : "crit") + "]";
                bonusLabel = bonusLabel == null || bonusLabel.isEmpty() ? tag : bonusLabel + " " + tag;
            }
        }
        broadcastAttackResult(session, attacker, target, false,
                r.total(), targetAC, hit, r.nat20(), r.nat1(),
                finalDamage, damageType, r.breakdown(), bonusLabel);
        // Send the cosmetic projectile (#181) — on a miss too, so the spent round it carries lands
        // somewhere scattered rather than neatly at the target's feet (#191).
        CombatVisuals.launch(attacker, target, projectileVisual, spentAmmoId, hit, projectileRangeFeet);
        if (hit) {
            if (onHit != null) onHit.run();
            remindMarkRider(attacker, target, commandUser); // Hex / Hunter's Mark rider (#178)
        }
        return true;
    }

    /** If the attacker has marked this target (Hex / Hunter's Mark), remind them to apply the rider
     *  damage as a separate, correctly-typed roll (the dice parser takes one die + one modifier). */
    private static void remindMarkRider(Combatant attacker, Combatant target, Player commandUser) {
        if (commandUser == null) return;
        CharacterSheet sheet = attacker.getCharacterSheet();
        if (sheet == null) return;
        String rider = sheet.markRiderAgainst(target.getId());
        if (rider == null) return;
        String type = sheet.getMarkDamageType();
        String name = target.getDisplayName();
        String quoted = name.contains(" ") ? "\"" + name + "\"" : name;
        String cmd = "/combat damage " + quoted + " autoRoll " + rider + (type != null ? " type " + type : "");
        commandUser.sendMessage(Component.text("✦ Mark: +" + rider + (type != null ? " " + type : "") + " — ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("[click to apply the rider]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(cmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Apply " + rider + (type != null ? " " + type : "") + " from your mark, after the weapon damage.")))));
    }

    /** Add one more of the first dice group (Savage Attacks): "2d12+3" → "3d12+3". */
    static String addOneDie(String damageStr) {
        if (damageStr == null) return null;
        Matcher m = DICE_COUNT_PATTERN.matcher(damageStr);
        if (m.find()) {
            int count = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
            StringBuilder sb = new StringBuilder();
            m.appendReplacement(sb, (count + 1) + "d" + m.group(2));
            m.appendTail(sb);
            return sb.toString();
        }
        return damageStr;
    }

    /**
     * Resolve which weapon a player is using.
     * @param weaponId explicit weapon ID from command, or null for auto-detect
     * @return DndWeapon, or null for unarmed strike
     */
    public static DndWeapon resolvePlayerWeapon(Player player, String weaponId) {
        // Explicit unarmed strike — never fall back to a held weapon.
        if ("unarmed".equalsIgnoreCase(weaponId)) return null;

        if (weaponId != null) {
            DndWeapon weapon = WeaponLoader.getWeapon(weaponId.toLowerCase());
            if (weapon != null) return weapon;
        }

        // Auto-detect from main hand
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        String mainHandId = ItemUtil.getItemId(mainHand);
        if (mainHandId != null) {
            DndWeapon weapon = WeaponLoader.getWeapon(mainHandId);
            if (weapon != null) return weapon;
        }

        // Unarmed strike
        return null;
    }

    /**
     * Calculate the attack modifier for a player's weapon attack.
     * Formula: ability mod + proficiency bonus (if proficient)
     */
    public static int calculatePlayerAttackMod(CharacterSheet sheet, DndWeapon weapon) {
        Ability attackAbility = resolveAttackAbility(sheet, weapon);
        int abilityMod = sheet.getModifier(attackAbility);
        int profBonus = 0;

        if (weapon != null) {
            if (weapon.isProficient(sheet.getWeaponProficiencies())) {
                profBonus = sheet.getProficiencyBonus();
            }
        } else {
            // Unarmed strikes: all characters are proficient
            profBonus = sheet.getProficiencyBonus();
        }

        // A magic weapon's bonus (#188) — added whether or not you're proficient, as in the DMG.
        int magic = weapon != null ? weapon.getAttackBonus() : 0;
        return abilityMod + profBonus + magic + sheet.attackBonusFor(attackTag(weapon)); // Archery (#229)
    }

    /** What kind of attack a weapon makes, for an effect's attack_bonus "when" (Archery: ranged). */
    static String attackTag(DndWeapon weapon) {
        return weapon != null && weapon.isRanged() ? "ranged" : "melee";
    }

    static boolean isTwoHanded(DndWeapon weapon) {
        return weapon != null && (weapon.hasProperty("two-handed") || weapon.hasProperty("two_handed"));
    }

    /** True if the off hand holds a weapon (a shield or a torch doesn't count). */
    static boolean offHandHoldsWeapon(Player player) {
        if (player == null) return false;
        String id = ItemUtil.getItemId(player.getInventory().getItemInOffHand());
        return id != null && WeaponLoader.getWeapon(id) != null;
    }

    /** "+2[Longsword +2]" for a magic weapon's bonus in a roll breakdown; empty for none. */
    private static String magicLabel(DndWeapon weapon, int bonus) {
        return (weapon == null || bonus == 0) ? "" : (bonus > 0 ? "+" : "") + bonus + "[" + weapon.getName() + "]";
    }

    /**
     * Resolve which ability to use for a weapon attack.
     * Finesse weapons use the better of STR or DEX.
     */
    static Ability resolveAttackAbility(CharacterSheet sheet, DndWeapon weapon) {
        if (weapon == null) {
            // Unarmed: STR, unless an effect says otherwise (Martial Arts: the better of STR and DEX, #221).
            var rule = sheet.unarmedStrikeRule();
            return rule != null ? rule.getValue().ability(sheet::getModifier) : Ability.STRENGTH;
        }

        Ability chosen = weapon.getPrimaryAbility();
        if (chosen == null) {
            // Finesse: use the better of STR or DEX
            int strMod = sheet.getModifier(Ability.STRENGTH);
            int dexMod = sheet.getModifier(Ability.DEXTERITY);
            chosen = (dexMod >= strMod) ? Ability.DEXTERITY : Ability.STRENGTH;
        }
        // An effect may let this weapon use another ability (monk weapons, #221); only if it's better.
        var rule = sheet.weaponAbilityFor(weapon);
        if (rule != null) {
            Ability alt = rule.getValue().ability(sheet::getModifier);
            if (sheet.getModifier(alt) > sheet.getModifier(chosen)) chosen = alt;
        }
        return chosen;
    }

    /**
     * Build a human-readable modifier breakdown string.
     * Example: "+3[STR] +2[Prof]"
     */
    public static String buildPlayerModBreakdown(CharacterSheet sheet, DndWeapon weapon) {
        Ability attackAbility = resolveAttackAbility(sheet, weapon);
        int abilityMod = sheet.getModifier(attackAbility);

        StringBuilder sb = new StringBuilder();
        sb.append(abilityMod >= 0 ? "+" : "").append(abilityMod);
        sb.append("[").append(attackAbility.getAbbreviation()).append("]");

        boolean proficient;
        if (weapon != null) {
            proficient = weapon.isProficient(sheet.getWeaponProficiencies());
        } else {
            proficient = true; // unarmed
        }

        if (proficient) {
            int profBonus = sheet.getProficiencyBonus();
            sb.append(" +").append(profBonus).append("[Prof]");
        }
        String magic = magicLabel(weapon, weapon != null ? weapon.getAttackBonus() : 0);
        if (!magic.isEmpty()) sb.append(" ").append(magic);
        String style = sheet.attackBonusBreakdownFor(attackTag(weapon)); // "+2[Archery]" (#229)
        if (!style.isEmpty()) sb.append(" ").append(style);

        return sb.toString();
    }

    /**
     * Build the damage dice string for a player weapon attack.
     * Includes the ability modifier as a bonus.
     * Example: "1d8+3" (longsword with +3 STR)
     */
    public static String buildPlayerDamageString(CharacterSheet sheet, DndWeapon weapon) {
        return buildPlayerDamageString(sheet, weapon, false);
    }

    /**
     * @param offHand a two-weapon-fighting bonus attack: a positive ability modifier isn't added to
     *                the damage (a negative one still is, PHB p.195).
     */
    public static String buildPlayerDamageString(CharacterSheet sheet, DndWeapon weapon, boolean offHand) {
        Ability attackAbility = resolveAttackAbility(sheet, weapon);
        int abilityMod = sheet.getModifier(attackAbility);
        if (offHand) abilityMod = Math.min(0, abilityMod);

        if (weapon == null) {
            // An effect's unarmed-strike die (Martial Arts: 1d4 + DEX at 1st level, #221).
            var rule = sheet.unarmedStrikeRule();
            if (rule != null) return withFlat(rule.getValue().dieAt(sheet.getTotalLevel()), abilityMod);
            // Unarmed strike: 1 + STR modifier bludgeoning, minimum 1 (5e floor).
            int damage = Math.max(1, 1 + abilityMod);
            return damage + "";
        }

        String baseDice = weapon.getDamage();
        if (baseDice == null || baseDice.isEmpty()) {
            return "1";
        }
        // A monk weapon rolls the Martial Arts die when it's bigger than its own (#221). Only a single
        // die is compared, so a 2d6 weapon is never swapped for a 1d8.
        var weaponRule = sheet.weaponAbilityFor(weapon);
        if (weaponRule != null && weaponRule.getValue().usesUnarmedDieIfBigger()) {
            var unarmed = sheet.unarmedStrikeRule();
            int own = io.papermc.jkvttplugin.effect.UnarmedStrike.faces(baseDice);
            if (unarmed != null && own > 0) {
                String die = unarmed.getValue().dieAt(sheet.getTotalLevel());
                if (io.papermc.jkvttplugin.effect.UnarmedStrike.faces(die) > own) baseDice = die;
            }
        }

        // Ability modifier plus a magic weapon's damage bonus (#188), as one flat term (1d8+5).
        int flat = abilityMod + weapon.getDamageBonus();
        if (flat > 0) {
            return baseDice + "+" + flat;
        } else if (flat < 0) {
            return baseDice + flat; // negative sign included
        }
        return baseDice;
    }

    /** "1d4+3", "1d4-1" or "1d4": dice with one flat term, the form the dice parser accepts. */
    private static String withFlat(String dice, int flat) {
        if (flat > 0) return dice + "+" + flat;
        if (flat < 0) return dice + flat;
        return dice;
    }

    // ==================== ENTITY ATTACKS ====================

    /**
     * Execute an entity's attack against a target.
     */
    public static boolean executeEntityAttack(Combatant attacker, Combatant target,
                                           CombatSession session, Player dm,
                                           String attackName, Integer providedRoll,
                                           Integer providedTotal, boolean showMods, boolean forceAuto) {
        DndEntityInstance entity = attacker.getEntityInstance();
        if (entity == null) {
            dm.sendMessage(Component.text("Entity data not found.", NamedTextColor.RED));
            return false;
        }

        List<DndAttack> attacks = entity.getTemplate().getAttacks();
        if (attacks == null || attacks.isEmpty()) {
            dm.sendMessage(Component.text(attacker.getDisplayName() + " has no attacks defined.", NamedTextColor.RED));
            return false;
        }

        // Resolve which attack to use
        DndAttack attack;
        if (attackName != null) {
            attack = findAttackByName(attacks, attackName);
            if (attack == null) {
                dm.sendMessage(Component.text("Attack not found: " + attackName, NamedTextColor.RED));
                dm.sendMessage(Component.text("Available attacks: " +
                        String.join(", ", attacks.stream().map(DndAttack::getName).toList()),
                        NamedTextColor.YELLOW));
                return false;
            }
        } else {
            attack = attacks.get(0);
        }

        int toHit = attack.getToHit();

        // --showmods: just show the attack info
        if (showMods) {
            dm.sendMessage(Component.empty());
            dm.sendMessage(Component.text("━━━ Attack Info: " + attack.getName() + " ━━━", NamedTextColor.GOLD));
            dm.sendMessage(Component.text("To Hit: +" + toHit, NamedTextColor.YELLOW));
            if (attack.getReach() != null) {
                dm.sendMessage(Component.text("Reach: " + attack.getReach(), NamedTextColor.GRAY));
            }
            dm.sendMessage(Component.text("Damage: " + attack.getDamage() + " " + attack.getDamageType(), NamedTextColor.GRAY));
            dm.sendMessage(RollPrompt.again(dm, "Roll it:", "d20", (toHit >= 0 ? "+" : "") + toHit + "[" + attack.getName() + "]"));
            dm.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
            return false;
        }

        // Same resolver as player attacks — the entity just sources its numbers from the stat block,
        // including whether its attack throws a cosmetic projectile (#181).
        // Both bonuses named for the attack, as the prompt names them: "+4[Scimitar]" to hit, and its
        // damage formula's flat part ("1d6+2" → "+2[Scimitar]"), which is the creature's modifier.
        int dmgFlat = splitDamageBonus(attack.getDamage())[0];
        String dmgLabel = dmgFlat == 0 ? "" : (dmgFlat > 0 ? "+" : "") + dmgFlat + "[" + attack.getName() + "]";
        return resolveAttack(session, attacker, target, toHit, (toHit >= 0 ? "+" : "") + toHit + "[" + attack.getName() + "]",
                attack.getDamage(), attack.getDamageType(), providedRoll, providedTotal, dm, dmgLabel, false,
                CombatVisuals.projectileFor(attack), forceAuto, null, 0, 0, null, null); // monsters do not track ammo
    }

    /**
     * Find an attack by name (case-insensitive, supports partial match and underscores).
     * Handles both "Warhammer" and "warhammer" formats, as well as "heavy_crossbow" for "Heavy Crossbow".
     */
    /** Public resolver so callers (e.g. range checks) can look up an entity's attack before it fires. */
    public static DndAttack resolveEntityAttack(Combatant attacker, String attackName) {
        if (attacker == null || !attacker.isEntity() || attacker.getEntityInstance() == null) return null;
        List<DndAttack> attacks = attacker.getEntityInstance().getTemplate().getAttacks();
        if (attacks == null || attacks.isEmpty()) return null;
        if (attackName == null || attackName.isEmpty()) return attacks.get(0);
        return findAttackByName(attacks, attackName);
    }

    private static DndAttack findAttackByName(List<DndAttack> attacks, String name) {
        String lower = name.toLowerCase();
        // Also try with underscores replaced by spaces (tab completion format)
        String withSpaces = lower.replace("_", " ");

        // Exact match first
        for (DndAttack a : attacks) {
            String attackLower = a.getName().toLowerCase();
            if (attackLower.equals(lower) || attackLower.equals(withSpaces)) return a;
        }

        // Starts-with match
        for (DndAttack a : attacks) {
            String attackLower = a.getName().toLowerCase();
            if (attackLower.startsWith(lower) || attackLower.startsWith(withSpaces)) return a;
        }

        return null;
    }

    // ==================== RESULT DISPLAY ====================

    /**
     * Broadcast the attack result to the combat session.
     * Rolls damage dice on hit and displays the result (but does NOT apply damage).
     */
    private static void broadcastAttackResult(CombatSession session,
                                              Combatant attacker, Combatant target,
                                              boolean isViewerDM,
                                              int total, int targetAC,
                                              boolean hit, boolean isNat20, boolean isNat1,
                                              String damageStr, String damageType,
                                              String rollDetail) {
        broadcastAttackResult(session, attacker, target, isViewerDM, total, targetAC, hit, isNat20, isNat1,
                damageStr, damageType, rollDetail, "");
    }

    private static void broadcastAttackResult(CombatSession session,
                                              Combatant attacker, Combatant target,
                                              boolean isViewerDM,
                                              int total, int targetAC,
                                              boolean hit, boolean isNat20, boolean isNat1,
                                              String damageStr, String damageType,
                                              String rollDetail, String bonusLabel) {
        Component separator = Component.text("━━━ Attack Roll ━━━", NamedTextColor.GOLD, TextDecoration.BOLD);
        session.broadcast(Component.empty());
        session.broadcast(separator);

        // Attacker → Target line
        session.broadcast(Component.text(attacker.getDisplayName(isViewerDM) + " attacks " +
                target.getDisplayName(isViewerDM) + "!", NamedTextColor.WHITE));

        // Roll details
        session.broadcast(Component.text("Attack roll: " + rollDetail, NamedTextColor.GRAY));
        session.broadcast(Component.text("vs AC " + targetAC, NamedTextColor.GRAY));

        // Result — attack resolves HIT/MISS only. Damage is a separate step (/combat damage).
        if (isNat20) {
            session.broadcast(Component.text("★ CRITICAL HIT! ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            promptDamage(session, attacker, target, damageStr, damageType, true, bonusLabel, total);
        } else if (isNat1) {
            session.broadcast(Component.text("✗ CRITICAL MISS!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        } else if (hit) {
            session.broadcast(Component.text("HIT!", NamedTextColor.GREEN, TextDecoration.BOLD));
            promptDamage(session, attacker, target, damageStr, damageType, false, bonusLabel, total);
        } else {
            session.broadcast(Component.text("MISS", NamedTextColor.RED));
        }

        session.broadcast(Component.text("━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    /**
     * On a hit, privately prompt whoever applies damage (the attacking player, and the DM)
     * with a ready-to-run /combat damage command pre-filled with the weapon's damage formula.
     * Sent only to the attacker + DM (not broadcast) so hidden-entity names don't leak.
     */
    static void promptDamage(CombatSession session, Combatant attacker, Combatant target,
                                     String damageStr, String damageType, boolean isCrit) {
        promptDamage(session, attacker, target, damageStr, damageType, isCrit, "", null);
    }

    static void promptDamage(CombatSession session, Combatant attacker, Combatant target,
                                     String damageStr, String damageType, boolean isCrit, String bonusLabel) {
        promptDamage(session, attacker, target, damageStr, damageType, isCrit, bonusLabel, null);
    }

    /**
     * As above, but when {@code attackTotal} is given the hit can be held for a reaction (#195):
     * if the creature that was hit could cast Shield (or anything else with a reaction casting time),
     * a {@link ReactionWindow} opens, the damage prompt is withheld, and it's sent — or cancelled as
     * a miss — once they've answered. Pass null for a hit nothing can react to (an auto-hit spell,
     * a rider) and the prompt goes out immediately, as it always did.
     */
    static void promptDamage(CombatSession session, Combatant attacker, Combatant target,
                                     String damageStr, String damageType, boolean isCrit, String bonusLabel,
                                     Integer attackTotal) {
        recordHit(attacker, target, damageStr, damageType, isCrit, bonusLabel);
        if (attackTotal != null && ReactionWindow.openForHit(session, attacker, target, damageStr,
                damageType, isCrit, bonusLabel, attackTotal)) {
            return; // held — the window sends the prompt (or reports a miss) when it closes
        }
        sendDamagePrompt(session, attacker, target, damageStr, damageType, isCrit, bonusLabel);
    }

    /**
     * A damage formula split into what you roll and what the game adds. A flat bonus is only split
     * off when it has a named source ({@code bonusLabel}: "+3[STR] +2[Rage]", a creature's
     * "+2[Scimitar]"). Otherwise it's part of the formula itself (Magic Missile's 1d4+1, like a
     * potion's 2d4+2): you roll the whole thing and nothing is added, so nothing unlabelled is ever
     * "added" in a prompt.
     */
    record DamageSplit(String dice, int bonus, String label) {
        boolean hasDice() { return dice.toLowerCase().contains("d"); }
    }

    static DamageSplit splitDamage(String damageStr, String bonusLabel) {
        String formula = damageStr == null ? "" : damageStr.trim();
        if (bonusLabel == null || bonusLabel.isBlank()) return new DamageSplit(formula, 0, "");
        return new DamageSplit(formula.replaceAll("[+-]\\s*\\d+\\s*$", "").trim(),
                splitDamageBonus(formula)[0], bonusLabel.trim());
    }

    /**
     * Remember the hit on the attacker's turn state so {@code /combat damage} knows what to apply,
     * what type it is, and whether it was a crit. Split out from the prompt itself so a hit can be
     * recorded now and offered later, after a reaction window closes (#195).
     */
    private static void recordHit(Combatant attacker, Combatant target, String damageStr,
                                  String damageType, boolean isCrit, String bonusLabel) {
        DamageSplit d = splitDamage(damageStr, bonusLabel);
        if (attacker.getTurnState() != null) {
            attacker.getTurnState().markAttackHit(target.getId(), d.hasDice() ? d.bonus() : 0,
                    d.hasDice() ? d.label() : "", isCrit);
            // Great Weapon Fighting (#229): set by the weapon attack just made; a spell's hit takes false.
            attacker.getTurnState().setPendingDamageRerollLow(attacker.getTurnState().takeRerollLowForNextHit());
            attacker.getTurnState().setPendingDamageType(damageType); // so /combat damage needs no 'type' (#183)
            attacker.getTurnState().setPendingDamageDice(d.hasDice() ? d.dice() : ""); // so 'autoRoll' needs no dice (#183)
        }
    }

    /** The clickable "apply damage" prompt for a hit that's already been recorded. */
    static void sendDamagePrompt(CombatSession session, Combatant attacker, Combatant target,
                                     String damageStr, String damageType, boolean isCrit, String bonusLabel) {
        String name = target.getDisplayName();
        String quoted = name.contains(" ") ? "\"" + name + "\"" : name;
        // The damage type is auto-grabbed from this hit by /combat damage — no 'type' needed (#183).
        DamageSplit d = splitDamage(damageStr, bonusLabel);

        Component prompt;
        if (d.hasDice()) {
            // autoRoll needs no dice typed: /combat damage remembers them from this hit.
            prompt = RollPrompt.line("💥 Roll " + d.dice() + " damage against " + name + ":", NamedTextColor.YELLOW,
                    "/combat damage " + quoted + " ", d.dice(), d.bonus() == 0 ? null : d.label());
        } else {
            // Flat damage (e.g. unarmed): nothing to roll — one click applies it.
            String amt = (damageStr == null || damageStr.isEmpty()) ? "1" : damageStr;
            String cmd = "/combat damage " + quoted + " " + amt;
            prompt = Component.text("→ Apply damage (" + amt + "): ", NamedTextColor.YELLOW)
                    .append(Component.text("[click to apply]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.suggestCommand(cmd))
                            .hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd))));
        }
        if (isCrit) {
            prompt = prompt.append(Component.text("  (crit — dice doubled)", NamedTextColor.GRAY));
        }

        // The attacking player applies their own damage; the DM also sees it (oversight / override).
        // Guard against a double message when the attacker IS the DM (a DM running their own PC).
        boolean attackerIsDm = attacker.isPlayer() && attacker.getId().equals(session.getDmId());
        if (attacker.isPlayer() && attacker.getPlayer() != null) {
            attacker.getPlayer().sendMessage(prompt);
        }
        if (!attackerIsDm) {
            session.sendToDM(prompt);
        }
    }

    /** Extract the trailing flat bonus from a damage string like "1d8+3" → 3, "2d6-1" → -1, "1d6" → 0. */
    private static int[] splitDamageBonus(String damageStr) {
        if (damageStr == null) return new int[]{0};
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([+-]\\s*\\d+)\\s*$").matcher(damageStr);
        if (m.find()) {
            try { return new int[]{Integer.parseInt(m.group(1).replaceAll("\\s", ""))}; }
            catch (NumberFormatException ignored) { /* fall through */ }
        }
        return new int[]{0};
    }

    // ==================== MODIFIER DISPLAY ====================

    /**
     * Show attack modifier breakdown (--showmods flag).
     */
    private static void showAttackModifiers(Player player, Combatant attacker,
                                            DndWeapon weapon, int attackMod,
                                            String modBreakdown) {
        String weaponName = (weapon != null) ? weapon.getName() : "Unarmed Strike";

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━ Attack Modifiers: " + weaponName + " ━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Total: +" + attackMod, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Breakdown: " + modBreakdown, NamedTextColor.GRAY));

        if (weapon != null) {
            player.sendMessage(Component.text("Damage: " + weapon.getDamage() + " " + weapon.getDamageType(), NamedTextColor.GRAY));
            if (weapon.getProperties() != null && !weapon.getProperties().isEmpty()) {
                player.sendMessage(Component.text("Properties: " + String.join(", ", weapon.getProperties()), NamedTextColor.DARK_GRAY));
            }
            if (weapon.isRanged() && weapon.getNormalRange() > 0) {
                String range = weapon.getLongRange() > 0
                        ? weapon.getNormalRange() + "/" + weapon.getLongRange() + " ft"
                        : weapon.getNormalRange() + " ft";
                player.sendMessage(Component.text("Range: " + range, NamedTextColor.DARK_GRAY));
            }
        }

        player.sendMessage(RollPrompt.again(player, "Roll it:", "d20", modBreakdown));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Get a list of weapon IDs in the player's inventory (main hand first).
     * Used for tab completion.
     */
    public static List<String> getWeaponIdsInInventory(Player player) {
        List<String> weapons = new ArrayList<>();
        PlayerInventory inv = player.getInventory();

        // Main hand first
        String mainHandId = ItemUtil.getItemId(inv.getItemInMainHand());
        if (mainHandId != null && WeaponLoader.getWeapon(mainHandId) != null) {
            weapons.add(mainHandId);
        }

        // Scan rest of inventory
        for (ItemStack item : inv.getContents()) {
            if (item == null) continue;
            String itemId = ItemUtil.getItemId(item);
            if (itemId != null && !weapons.contains(itemId) && WeaponLoader.getWeapon(itemId) != null) {
                weapons.add(itemId);
            }
        }

        return weapons;
    }

    /**
     * Get attack names from an entity combatant's template.
     * Used for tab completion.
     */
    public static List<String> getEntityAttackNames(Combatant combatant) {
        List<String> names = new ArrayList<>();
        if (!combatant.isEntity()) return names;

        DndEntityInstance entity = combatant.getEntityInstance();
        if (entity == null) return names;

        List<DndAttack> attacks = entity.getTemplate().getAttacks();
        if (attacks != null) {
            for (DndAttack a : attacks) {
                names.add(a.getName().toLowerCase().replace(" ", "_"));
            }
        }
        return names;
    }

    /**
     * Double the dice count in a damage string for critical hits.
     * "1d8+3" → "2d8+3", "2d6+4" → "4d6+4", "d4" → "2d4"
     */
    public static String doubleDice(String damageStr) {
        if (damageStr == null) return damageStr;

        Matcher matcher = DICE_COUNT_PATTERN.matcher(damageStr);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            int count = matcher.group(1).isEmpty() ? 1 : Integer.parseInt(matcher.group(1));
            int doubled = count * 2;
            matcher.appendReplacement(result, doubled + "d" + matcher.group(2));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
