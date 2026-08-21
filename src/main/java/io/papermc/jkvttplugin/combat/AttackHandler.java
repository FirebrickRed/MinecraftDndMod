package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.DiceRoller;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles attack roll logic for the combat system (Issue #99).
 *
 * Supports three input modes:
 * - Auto-roll: system rolls d20 and adds modifiers
 * - --roll N: player provides physical d20 result, system adds modifiers
 * - --total N: player provides final total, compared directly to AC
 *
 * Damage dice are rolled and displayed on hit, but NOT applied to HP (Issue #100).
 */
public class AttackHandler {

    private static final Pattern DICE_COUNT_PATTERN = Pattern.compile("(\\d*)d(\\d+)");

    // ==================== PLAYER ATTACKS ====================

    /**
     * Execute a player character's attack against a target.
     */
    public static void executePlayerAttack(Combatant attacker, Combatant target,
                                           CombatSession session, Player player,
                                           String weaponId, Integer providedRoll,
                                           Integer providedTotal, boolean showMods) {
        CharacterSheet sheet = attacker.getCharacterSheet();
        if (sheet == null) {
            player.sendMessage(Component.text("No active character found.", NamedTextColor.RED));
            return;
        }

        // Resolve weapon
        DndWeapon weapon = resolvePlayerWeapon(player, weaponId);
        // weapon == null means unarmed strike

        // Calculate attack modifier
        int attackMod = calculatePlayerAttackMod(sheet, weapon);
        String modBreakdown = buildPlayerModBreakdown(sheet, weapon);

        // --showmods: just show the breakdown, don't attack
        if (showMods) {
            showAttackModifiers(player, attacker, weapon, attackMod, modBreakdown);
            return;
        }

        // Build damage string
        String damageStr = buildPlayerDamageString(sheet, weapon);
        String damageType = (weapon != null) ? weapon.getDamageType() : "bludgeoning";

        // Determine the d20 result and total
        int targetAC = target.getArmorClass();

        if (providedTotal != null) {
            // --total mode: no crit detection, just compare to AC
            boolean hit = providedTotal >= targetAC;
            broadcastAttackResult(session, attacker, target, false,
                    providedTotal, targetAC, hit, false, false,
                    damageStr, damageType, providedTotal + " (provided total)");
        } else {
            // Auto-roll or --roll mode
            int d20 = (providedRoll != null) ? providedRoll : DiceRoller.rollDice(1, 20);
            int total = d20 + attackMod;
            boolean isNat20 = (d20 == 20);
            boolean isNat1 = (d20 == 1);
            boolean hit = isNat20 || (!isNat1 && total >= targetAC);

            String rollDetail = "d20(" + d20 + ") " + modBreakdown + " = " + total;

            if (isNat20) {
                damageStr = doubleDice(damageStr);
            }

            broadcastAttackResult(session, attacker, target, false,
                    total, targetAC, hit, isNat20, isNat1,
                    damageStr, damageType, rollDetail);
        }
    }

    /**
     * Resolve which weapon a player is using.
     * @param weaponId explicit weapon ID from command, or null for auto-detect
     * @return DndWeapon, or null for unarmed strike
     */
    public static DndWeapon resolvePlayerWeapon(Player player, String weaponId) {
        if (weaponId != null) {
            DndWeapon weapon = WeaponLoader.getWeapon(weaponId);
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

        return abilityMod + profBonus;
    }

    /**
     * Resolve which ability to use for a weapon attack.
     * Finesse weapons use the better of STR or DEX.
     */
    private static Ability resolveAttackAbility(CharacterSheet sheet, DndWeapon weapon) {
        if (weapon == null) {
            // Unarmed: always STR
            return Ability.STRENGTH;
        }

        Ability primary = weapon.getPrimaryAbility();
        if (primary != null) {
            return primary;
        }

        // Finesse: use the better of STR or DEX
        int strMod = sheet.getModifier(Ability.STRENGTH);
        int dexMod = sheet.getModifier(Ability.DEXTERITY);
        return (dexMod >= strMod) ? Ability.DEXTERITY : Ability.STRENGTH;
    }

    /**
     * Build a human-readable modifier breakdown string.
     * Example: "+3[STR] +2[Prof]"
     */
    private static String buildPlayerModBreakdown(CharacterSheet sheet, DndWeapon weapon) {
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

        return sb.toString();
    }

    /**
     * Build the damage dice string for a player weapon attack.
     * Includes the ability modifier as a bonus.
     * Example: "1d8+3" (longsword with +3 STR)
     */
    public static String buildPlayerDamageString(CharacterSheet sheet, DndWeapon weapon) {
        Ability attackAbility = resolveAttackAbility(sheet, weapon);
        int abilityMod = sheet.getModifier(attackAbility);

        if (weapon == null) {
            // Unarmed strike: 1 + STR modifier bludgeoning, minimum 1 (5e floor).
            int damage = Math.max(1, 1 + abilityMod);
            return damage + "";
        }

        String baseDice = weapon.getDamage();
        if (baseDice == null || baseDice.isEmpty()) {
            return "1";
        }

        if (abilityMod > 0) {
            return baseDice + "+" + abilityMod;
        } else if (abilityMod < 0) {
            return baseDice + abilityMod; // negative sign included
        }
        return baseDice;
    }

    // ==================== ENTITY ATTACKS ====================

    /**
     * Execute an entity's attack against a target.
     */
    public static void executeEntityAttack(Combatant attacker, Combatant target,
                                           CombatSession session, Player dm,
                                           String attackName, Integer providedRoll,
                                           Integer providedTotal, boolean showMods) {
        DndEntityInstance entity = attacker.getEntityInstance();
        if (entity == null) {
            dm.sendMessage(Component.text("Entity data not found.", NamedTextColor.RED));
            return;
        }

        List<DndAttack> attacks = entity.getTemplate().getAttacks();
        if (attacks == null || attacks.isEmpty()) {
            dm.sendMessage(Component.text(attacker.getDisplayName() + " has no attacks defined.", NamedTextColor.RED));
            return;
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
                return;
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
            dm.sendMessage(Component.text("Use --roll <d20> to provide a physical roll.", NamedTextColor.DARK_GRAY));
            dm.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
            return;
        }

        String damageStr = attack.getDamage();
        String damageType = attack.getDamageType();
        int targetAC = target.getArmorClass();

        if (providedTotal != null) {
            boolean hit = providedTotal >= targetAC;
            broadcastAttackResult(session, attacker, target, false,
                    providedTotal, targetAC, hit, false, false,
                    damageStr, damageType, providedTotal + " (provided total)");
        } else {
            int d20 = (providedRoll != null) ? providedRoll : DiceRoller.rollDice(1, 20);
            int total = d20 + toHit;
            boolean isNat20 = (d20 == 20);
            boolean isNat1 = (d20 == 1);
            boolean hit = isNat20 || (!isNat1 && total >= targetAC);

            String rollDetail = "d20(" + d20 + ") +" + toHit + "[ToHit] = " + total;

            if (isNat20) {
                damageStr = doubleDice(damageStr);
            }

            broadcastAttackResult(session, attacker, target, false,
                    total, targetAC, hit, isNat20, isNat1,
                    damageStr, damageType, rollDetail);
        }
    }

    /**
     * Find an attack by name (case-insensitive, supports partial match and underscores).
     * Handles both "Warhammer" and "warhammer" formats, as well as "heavy_crossbow" for "Heavy Crossbow".
     */
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
        Component separator = Component.text("━━━ Attack Roll ━━━", NamedTextColor.GOLD, TextDecoration.BOLD);
        session.broadcast(Component.empty());
        session.broadcast(separator);

        // Attacker → Target line
        session.broadcast(Component.text(attacker.getDisplayName(isViewerDM) + " attacks " +
                target.getDisplayName(isViewerDM) + "!", NamedTextColor.WHITE));

        // Roll details
        session.broadcast(Component.text("Roll: " + rollDetail, NamedTextColor.GRAY));
        session.broadcast(Component.text("vs AC " + targetAC, NamedTextColor.GRAY));

        // Result — attack resolves HIT/MISS only. Damage is a separate step (/combat damage).
        if (isNat20) {
            session.broadcast(Component.text("★ CRITICAL HIT! ★", NamedTextColor.GOLD, TextDecoration.BOLD));
            promptDamage(session, attacker, target, damageStr, damageType, true);
        } else if (isNat1) {
            session.broadcast(Component.text("✗ CRITICAL MISS!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        } else if (hit) {
            session.broadcast(Component.text("HIT!", NamedTextColor.GREEN, TextDecoration.BOLD));
            promptDamage(session, attacker, target, damageStr, damageType, false);
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
    private static void promptDamage(CombatSession session, Combatant attacker, Combatant target,
                                     String damageStr, String damageType, boolean isCrit) {
        String name = target.getDisplayName();
        String quoted = name.contains(" ") ? "\"" + name + "\"" : name;
        // Dice formulas (e.g. 1d8+3) go through --roll; a flat number (e.g. 6) is a plain amount.
        String amtFlag;
        if (damageStr == null || damageStr.isEmpty()) {
            amtFlag = "";
        } else if (damageStr.toLowerCase().contains("d")) {
            amtFlag = " --roll " + damageStr;
        } else {
            amtFlag = " " + damageStr;
        }
        String typeFlag = (damageType == null || damageType.isEmpty()) ? "" : " --type " + damageType;

        Component prompt = Component.text("→ Apply damage: ", NamedTextColor.YELLOW)
                .append(Component.text("/combat damage " + quoted + amtFlag + typeFlag, NamedTextColor.WHITE));
        if (isCrit) {
            prompt = prompt.append(Component.text("  (crit — dice already doubled)", NamedTextColor.GRAY));
        }

        // The attacking player applies their own damage; the DM always sees it (oversight / override).
        if (attacker.isPlayer() && attacker.getPlayer() != null) {
            attacker.getPlayer().sendMessage(prompt);
        }
        session.sendToDM(prompt);
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

        player.sendMessage(Component.text("Use --roll <d20> to provide a physical roll.", NamedTextColor.DARK_GRAY));
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
