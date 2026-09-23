package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.SpellCost;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.dm.InteractiveObjectManager;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Attacks and harmful spells outside a fight (#152).
 *
 * <ul>
 *   <li><b>At a creature or character:</b> the DM decides. [Start combat] opens a fight with both of
 *       them (the DM adds anyone else and marks who's surprised before rolling initiative), and the
 *       attacker gets their move back as a filled-in command on their first turn. [Let it happen]
 *       resolves it once without a fight: attack roll first, then damage. [Deny] stops it.</li>
 *   <li><b>At a thing</b> (the torch on the cave wall): no creature where they're looking, so the
 *       caster confirms, rolls to hit, and the DM sees the total and what they were aiming at, with
 *       an [Ask for damage] button if it matters.</li>
 *   <li><b>Healing</b> needs no one's permission: it rolls and applies.</li>
 * </ul>
 *
 * <p>HP still changes in one place: {@link DamageHandler}, with no session, exactly as a trap does.
 * Nothing is spent until the spell actually resolves, so a denied or abandoned cast costs nothing.
 */
public final class OutOfCombatAttack {

    private OutOfCombatAttack() {}

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(10)).build();

    /** How long a DM approval, a "cast it anyway", or a held opening move stays good. */
    private static final long GOOD_FOR_MS = Duration.ofMinutes(10).toMillis();

    /** What a caster is allowed to go ahead with: this spell at this target (null target = aimed at a thing). */
    private record Permit(String spellId, UUID targetId, long at) {
        boolean covers(DndSpell spell, UUID target) {
            return spellId.equalsIgnoreCase(spell.getId()) && java.util.Objects.equals(targetId, target)
                    && System.currentTimeMillis() - at < GOOD_FOR_MS;
        }
    }

    /** A hit waiting for its damage roll: {@code /character damage}. A null target means a thing, not a creature. */
    private record PendingDamage(String source, String dice, String damageType, CombatTargets.Target target,
                                 String objectLabel, boolean half) {}

    /** A move held when a fight was started over it, handed back on the attacker's first turn. */
    private record Opening(String label, String command, long at) {}

    private static final Map<UUID, Permit> permits = new HashMap<>();
    private static final Map<UUID, PendingDamage> pendingDamage = new HashMap<>();
    private static final Map<UUID, Opening> openings = new HashMap<>();

    // ==================== SPELLS ====================

    /** Harmful spells: an attack roll, a save, or damage. These are the ones that need the DM's say. */
    public static boolean isHarmful(DndSpell spell) {
        if (spell.isHealing() || spell.grantsTempHp()) return false;
        return spell.isAttackRoll() || spell.isSaveSpell()
                || (spell.getDamage() != null && !spell.getDamage().isBlank());
    }

    /**
     * {@code /character cast} out of combat for a harmful or healing spell. Returns true once it has
     * handled the command, whether it resolved, prompted, or refused.
     *
     * @param targetName the typed target, or null to use what the caster is looking at
     */
    public static boolean cast(Player player, CharacterSheet sheet, DndSpell spell, Integer castLevel,
                               String targetName, RollService.RollInput roll, SpellCost cost) {
        Aim aim = aim(player, targetName);
        if (aim == null) return true; // a typed name that didn't resolve; the resolver said why

        if (spell.isHealing()) return heal(player, sheet, spell, aim, roll, cost);

        String retry = "/character cast " + spell.getId() + (aim.target != null ? " " + quote(aim.targetName()) : "")
                + (castLevel != null ? " level " + castLevel : "") + " ";

        if (aim.target != null) {
            // A creature or a character: that's a fight unless the DM says otherwise.
            if (!permitted(player, spell, aim.target.combatant().getId())) {
                String combatCmd = "/combat cast " + spell.getId() + " " + quote(aim.targetName())
                        + (castLevel != null ? " level " + castLevel : "") + " ";
                askDm(player, sheet.getCharacterName() + " wants to cast " + spell.getName() + " at " + aim.targetName(),
                        aim.target.combatant(), spell.getName() + " at " + aim.targetName(), combatCmd,
                        () -> grant(player, spell, aim.target.combatant().getId(), retry));
                return true;
            }
            if (!inRange(player, aim, spell)) return true;
            return resolveAtCreature(player, sheet, spell, aim, roll, cost, retry);
        }

        // Nothing living where they're looking: the torch on the wall, or just the wall.
        if (!permitted(player, spell, null)) {
            Component ask = Component.text("You're not aiming at a creature. Cast " + spell.getName() + " anyway? ", NamedTextColor.YELLOW)
                    .append(button("[Cast it]", NamedTextColor.GREEN, "Go ahead: roll to hit, and the DM decides what happens",
                            a -> { permits.put(player.getUniqueId(), new Permit(spell.getId(), null, System.currentTimeMillis()));
                                   rollPrompt(player, retry); }))
                    .append(Component.text("  "))
                    .append(button("[Cancel]", NamedTextColor.GRAY, "Don't cast it", a -> player.sendMessage(
                            Component.text("Cancelled.", NamedTextColor.GRAY))));
            player.sendMessage(ask);
            return true;
        }
        return resolveAtThing(player, sheet, spell, aim, roll, cost, retry);
    }

    private static boolean resolveAtCreature(Player player, CharacterSheet sheet, DndSpell spell, Aim aim,
                                             RollService.RollInput roll, SpellCost cost, String retry) {
        Combatant caster = CombatTargets.forPlayer(player).combatant();
        Combatant target = aim.target.combatant();
        String who = sheet.getCharacterName();

        if (spell.isAttackRoll()) {
            RollService.RollResult r = attackRoll(player, sheet, spell, caster.attackAdvantageAgainst(target), roll, caster.rerollsNat1());
            if (r == null) { rollPrompt(player, retry); return true; }
            commit(player, sheet, spell, cost);
            int ac = target.getArmorClass();
            boolean hit = RollService.hits(r, ac);
            tell(player, Component.text("✨ " + who + " casts " + spell.getName() + " at " + aim.targetName() + "!", NamedTextColor.LIGHT_PURPLE));
            tell(player, Component.text("Spell attack: " + r.breakdown() + " vs AC " + ac + " — " + (hit ? (r.nat20() ? "CRITICAL HIT!" : "HIT!") : "MISS"),
                    hit ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (hit) offerDamage(player, spell, r.nat20(), aim.target, null, false);
            return true;
        }

        commit(player, sheet, spell, cost);
        if (spell.isSaveSpell()) {
            Ability save = parseAbility(spell.getSaveType());
            int dc = 8 + sheet.getProficiencyBonus() + modFor(sheet, spell);
            String abbr = save != null ? save.getAbbreviation() : spell.getSaveType();
            tell(player, Component.text("✨ " + who + " casts " + spell.getName() + " at " + aim.targetName()
                    + " — DC " + dc + " " + abbr + " save!", NamedTextColor.LIGHT_PURPLE));
            boolean hasDamage = spell.getDamage() != null && !spell.getDamage().isBlank();
            boolean halfOnSave = "half".equalsIgnoreCase(spell.getSaveEffect());
            Component dm = Component.text("   DM: " + aim.targetName() + " makes a DC " + dc + " " + abbr + " save. ", NamedTextColor.GRAY);
            if (target.isPlayer() && save != null) {
                String check = "/dm check " + quote(aim.targetName()) + " save " + save.getAbbreviation().toLowerCase() + " dc " + dc;
                dm = dm.append(Component.text("[Call the save]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(check)).hoverEvent(HoverEvent.showText(Component.text("Fills: " + check))))
                        .append(Component.text(" "));
            }
            if (hasDamage) {
                dm = dm.append(button("[Failed: damage]", NamedTextColor.RED, "They failed: " + who + " rolls full damage",
                        a -> offerDamage(player, spell, false, aim.target, null, false)));
                dm = dm.append(Component.text(" "));
                dm = dm.append(halfOnSave
                        ? button("[Saved: half]", NamedTextColor.YELLOW, "They saved: " + who + " rolls, and it's halved",
                                a -> offerDamage(player, spell, false, aim.target, null, true))
                        : button("[Saved: nothing]", NamedTextColor.GRAY, "They saved: no damage",
                                a -> tell(player, Component.text(aim.targetName() + " resists " + spell.getName() + ".", NamedTextColor.GRAY))));
            } else if (spell.getConditionOnFail() != null && !spell.getConditionOnFail().isBlank()) {
                dm = dm.append(Component.text("On a fail: " + spell.getConditionOnFail() + ".", NamedTextColor.GRAY));
            }
            toDms(dm);
            return true;
        }

        // Hits automatically (Magic Missile) or just deals damage.
        tell(player, Component.text("✨ " + who + " casts " + spell.getName() + " at " + aim.targetName() + ".", NamedTextColor.LIGHT_PURPLE));
        offerDamage(player, spell, false, aim.target, null, false);
        return true;
    }

    private static boolean resolveAtThing(Player player, CharacterSheet sheet, DndSpell spell, Aim aim,
                                          RollService.RollInput roll, SpellCost cost, String retry) {
        String who = sheet.getCharacterName();
        String at = aim.objectLabel != null ? aim.objectLabel : "nothing in particular";
        if (spell.isAttackRoll()) {
            RollService.RollResult r = attackRoll(player, sheet, spell, Advantage.NONE, roll, false);
            if (r == null) { rollPrompt(player, retry); return true; }
            commit(player, sheet, spell, cost);
            permits.remove(player.getUniqueId());
            tell(player, Component.text("✨ " + who + " casts " + spell.getName() + " at " + at + ": "
                    + r.breakdown() + " to hit.", NamedTextColor.LIGHT_PURPLE));
        } else {
            commit(player, sheet, spell, cost);
            permits.remove(player.getUniqueId());
            tell(player, Component.text("✨ " + who + " casts " + spell.getName() + " at " + at + ".", NamedTextColor.LIGHT_PURPLE));
        }
        Component dm = Component.text("   DM: they're looking at " + at + ". ", NamedTextColor.GRAY);
        if (aim.objectNote != null && !aim.objectNote.isBlank()) {
            dm = dm.append(Component.text("(" + aim.objectNote + ") ", NamedTextColor.DARK_GRAY));
        }
        if (spell.getDamage() != null && !spell.getDamage().isBlank()) {
            dm = dm.append(button("[Ask for damage]", NamedTextColor.AQUA, "Have " + who + " roll " + spell.getDamage(),
                    a -> offerDamage(player, spell, false, null, at, false)));
        }
        toDms(dm);
        return true;
    }

    /** Healing out of combat: no permission needed. Rolls through the usual prompt and applies. */
    private static boolean heal(Player player, CharacterSheet sheet, DndSpell spell, Aim aim,
                                RollService.RollInput roll, SpellCost cost) {
        CombatTargets.Target target = aim.target != null ? aim.target : CombatTargets.forPlayer(player);
        String name = aim.target != null ? aim.targetName() : sheet.getCharacterName();
        if (aim.target != null && !inRange(player, aim, spell)) return true;
        int mod = modFor(sheet, spell);
        Integer amount;
        if (roll.providedTotal() != null) amount = roll.providedTotal();
        else if (roll.providedRoll() != null) amount = roll.providedRoll() + mod;
        else if (roll.forceAuto() || io.papermc.jkvttplugin.config.PluginConfig.isAutoRoll()) {
            DiceRoller.Rolled r = DiceRoller.rollOrFlat(spell.getHealing());
            if (r == null) { player.sendMessage(Component.text(spell.getName() + " has no healing dice.", NamedTextColor.RED)); return true; }
            tell(player, Component.text(r.display(), NamedTextColor.GRAY));
            amount = r.total() + mod;
        } else {
            String cmd = "/character cast " + spell.getId() + (aim.target != null ? " " + quote(name) : "") + " ";
            player.sendMessage(Component.text("Roll " + spell.getHealing() + " (the game adds your " + signed(mod) + "): ", NamedTextColor.YELLOW)
                    .append(fill("[I rolled…]", cmd + "manualRoll ")).append(Component.text(" "))
                    .append(fill("[Let the game roll]", cmd + "autoRoll")));
            return true;
        }
        commit(player, sheet, spell, cost);
        tell(player, Component.text("✨ " + sheet.getCharacterName() + " casts " + spell.getName() + " on " + name + ".", NamedTextColor.LIGHT_PURPLE));
        DamageHandler.applyHealing(target.session(), target.combatant(), Math.max(1, amount));
        return true;
    }

    // ==================== DAMAGE ====================

    /** Hand the caster their damage roll: {@code /character damage}. */
    private static void offerDamage(Player caster, DndSpell spell, boolean crit, CombatTargets.Target target,
                                    String objectLabel, boolean half) {
        String dice = spell.getDamage() == null ? "" : (crit ? AttackHandler.doubleDice(spell.getDamage()) : spell.getDamage());
        pendingDamage.put(caster.getUniqueId(), new PendingDamage(spell.getName(), dice, spell.getDamageType(), target, objectLabel, half));
        caster.sendMessage(Component.text("Roll " + (dice.isBlank() ? "damage" : dice) + (half ? " (halved)" : "") + " for "
                        + spell.getName() + ": ", NamedTextColor.YELLOW)
                .append(fill("[I rolled…]", "/character damage manualRoll "))
                .append(Component.text(" "))
                .append(fill("[Let the game roll]", "/character damage autoRoll")));
    }

    /** {@code /character damage [autoRoll | manualRoll <n> | total <n> | <n>]} — finish an out-of-combat hit. */
    public static void damage(Player player, String[] args) {
        PendingDamage p = pendingDamage.get(player.getUniqueId());
        if (p == null) {
            player.sendMessage(Component.text("There's no hit waiting for damage. (In a fight, use /combat damage.)", NamedTextColor.YELLOW));
            return;
        }
        RollService.RollInput in = RollService.parseInput(args, player);
        Integer amount = null;
        if (in.providedTotal() != null) amount = in.providedTotal();
        else if (in.providedRoll() != null) amount = in.providedRoll();
        else if (in.forceAuto()) {
            DiceRoller.Rolled r = DiceRoller.rollOrFlat(p.dice());
            if (r == null) { player.sendMessage(Component.text("There are no dice to roll — type the amount.", NamedTextColor.RED)); return; }
            tell(player, Component.text(r.display(), NamedTextColor.GRAY));
            amount = r.total();
        } else if (args.length > 0) {
            try { amount = Integer.parseInt(args[args.length - 1].trim()); } catch (NumberFormatException ignored) {}
        }
        if (amount == null) {
            player.sendMessage(Component.text("Usage: /character damage autoRoll | manualRoll <n> | total <n>", NamedTextColor.RED));
            return;
        }
        pendingDamage.remove(player.getUniqueId());
        int dealt = p.half() ? amount / 2 : amount;
        String type = p.damageType() != null ? " " + p.damageType() : "";
        if (p.target() == null) {
            // A thing, not a creature: nothing has HP to take it. The DM narrates.
            tell(player, Component.text(p.source() + " deals " + dealt + type + " damage to " + p.objectLabel() + ".", NamedTextColor.GOLD));
            return;
        }
        if (p.half()) tell(player, Component.text("Saved — half damage: " + amount + " → " + dealt + ".", NamedTextColor.GRAY));
        DamageHandler.applyDamage(p.target().session(), p.target().combatant(), dealt, p.damageType(), false);
    }

    // ==================== WEAPONS ====================

    private static final Map<UUID, Long> lastWeaponAsk = new HashMap<>();

    /**
     * A left-click on a creature with a weapon, out of a fight. It's a fight if the DM says so; there's
     * no one-off resolution for weapons (sparring is a check, or a fight).
     */
    public static void weaponAttack(Player player, String weaponName, String weaponId, Combatant target) {
        long now = System.currentTimeMillis();
        Long last = lastWeaponAsk.get(player.getUniqueId());
        if (last != null && now - last < 5000) return; // one ask per swing flurry
        lastWeaponAsk.put(player.getUniqueId(), now);
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(player);
        String who = sheet != null ? sheet.getCharacterName() : player.getName();
        String cmd = "/combat attack " + quote(target.getDisplayName()) + " " + weaponId + " ";
        askDm(player, who + " attacks " + target.getDisplayName() + " with " + weaponName, target,
                weaponName + " attack on " + target.getDisplayName(), cmd, null);
    }

    // ==================== THE DM'S CALL ====================

    /**
     * Ask the DM what happens. {@code letItHappen} null means that option isn't offered.
     * @param heldCommand what the attacker gets back on their first turn if the DM starts a fight
     */
    private static void askDm(Player attacker, String what, Combatant target, String label, String heldCommand,
                              Runnable letItHappen) {
        List<Player> dms = DMManager.getOnlineDMs();
        if (dms.isEmpty()) {
            attacker.sendMessage(Component.text("Attacking outside a fight needs the DM, and no DM is online.", NamedTextColor.RED));
            return;
        }
        attacker.sendMessage(Component.text("You're not in a fight — asking the DM.", NamedTextColor.GRAY));
        UUID attackerId = attacker.getUniqueId();
        String targetArg = target.isPlayer() && target.getPlayer() != null ? target.getPlayer().getName() : target.getDisplayName();

        Component ask = Component.text("⚔ " + what + ". You're not in a fight. ", NamedTextColor.GOLD)
                .append(button("[Start combat]", NamedTextColor.RED, "Start a fight with both of them. Add anyone else and mark "
                        + "who's surprised before rolling initiative. The attack comes back on their first turn.",
                        a -> { if (a instanceof Player dm) startCombat(dm, attackerId, targetArg, label, heldCommand); }))
                .append(Component.text(" "));
        if (letItHappen != null) {
            ask = ask.append(button("[Let it happen]", NamedTextColor.GREEN, "Resolve it once, no fight: attack roll, then damage",
                    a -> letItHappen.run())).append(Component.text(" "));
        }
        ask = ask.append(button("[Deny]", NamedTextColor.GRAY, "It doesn't happen", a -> {
            Player p = Bukkit.getPlayer(attackerId);
            if (p != null) p.sendMessage(Component.text("The DM stops that.", NamedTextColor.GRAY));
        }));
        for (Player dm : dms) dm.sendMessage(ask);
    }

    private static void grant(Player caster, DndSpell spell, UUID targetId, String retry) {
        permits.put(caster.getUniqueId(), new Permit(spell.getId(), targetId, System.currentTimeMillis()));
        caster.sendMessage(Component.text("The DM lets it happen.", NamedTextColor.GREEN));
        rollPrompt(caster, retry);
    }

    private static void startCombat(Player dm, UUID attackerId, String targetArg, String label, String heldCommand) {
        Player attacker = Bukkit.getPlayer(attackerId);
        if (attacker == null) return;
        CombatSession session = null;
        for (CombatSession s : CombatSession.getAllSessions()) if (dm.getUniqueId().equals(s.getDmId())) session = s;
        if (session == null) dm.performCommand("combat start");
        dm.performCommand("combat add " + attacker.getName());
        dm.performCommand("combat add " + targetArg);
        openings.put(attackerId, new Opening(label, heldCommand, System.currentTimeMillis()));
        dm.sendMessage(Component.text("Anyone else in this? Add them (Add tool, or /combat add). Caught anyone off guard? "
                + "Mark them surprised (/combat surprise <who>). Then roll initiative.", NamedTextColor.YELLOW));
        attacker.sendMessage(Component.text("It's a fight! Your " + label + " comes back to you on your first turn.", NamedTextColor.GOLD));
    }

    /** Called at the start of each turn: hand an attacker back the move that started the fight. */
    public static void offerOpening(Combatant current) {
        if (current == null || !current.isPlayer()) return;
        Opening o = openings.remove(current.getId());
        Player p = current.getPlayer();
        if (o == null || p == null || System.currentTimeMillis() - o.at() > Duration.ofMinutes(30).toMillis()) return;
        p.sendMessage(Component.text("⚔ Your opening move: " + o.label() + " ", NamedTextColor.GOLD)
                .append(fill("[do it]", o.command())));
    }

    // ==================== AIMING ====================

    /** What the caster is pointing at: a creature or character, or a thing (with its annotation, if any). */
    private record Aim(CombatTargets.Target target, String objectLabel, String objectNote) {
        String targetName() { return target.combatant().getDisplayName(); }
    }

    private static Aim aim(Player player, String typed) {
        if (typed != null && !typed.isBlank()) {
            CombatTargets.Target t = CombatTargets.resolveOrError(player, typed);
            return t == null ? null : new Aim(t, null, null);
        }
        RayTraceResult hit = player.rayTraceEntities(40);
        Entity e = hit != null ? hit.getHitEntity() : null;
        if (e instanceof ArmorStand stand && DndEntityInstance.getByArmorStand(stand) != null) {
            return new Aim(CombatTargets.forEntity(DndEntityInstance.getByArmorStand(stand)), null, null);
        }
        if (e instanceof Player other && ActiveCharacterTracker.getActiveCharacter(other) != null) {
            return new Aim(CombatTargets.forPlayer(other), null, null);
        }
        Block block = player.getTargetBlockExact(40);
        if (block == null) return new Aim(null, null, null);
        String label = "the " + block.getType().name().toLowerCase().replace('_', ' ');
        InteractiveObjectManager.Obj obj = InteractiveObjectManager.getForBlock(block);
        return new Aim(null, label, obj != null ? obj.description : null);
    }

    private static final Pattern FEET = Pattern.compile("(\\d+)\\s*(ft|feet|foot)", Pattern.CASE_INSENSITIVE);

    /** A spell's range against where the target stands (1 block = 5 ft, with a block of slack). */
    private static boolean inRange(Player player, Aim aim, DndSpell spell) {
        String range = spell.getRange() == null ? "" : spell.getRange().trim();
        int feet;
        if (range.equalsIgnoreCase("touch")) feet = 5;
        else {
            Matcher m = FEET.matcher(range);
            if (!m.find()) return true; // Self, Sight, Unlimited…: not enforced
            feet = Integer.parseInt(m.group(1));
        }
        var there = aim.target.combatant().getLocation();
        if (there == null || !there.getWorld().equals(player.getWorld())) return true;
        double distFeet = there.distance(player.getLocation()) * 5.0;
        if (distFeet <= feet + 5) return true;
        player.sendMessage(Component.text(aim.targetName() + " is about " + Math.round(distFeet) + " ft away — "
                + spell.getName() + " reaches " + range + ".", NamedTextColor.RED));
        return false;
    }

    // ==================== HELPERS ====================

    private static boolean permitted(Player caster, DndSpell spell, UUID targetId) {
        Permit p = permits.get(caster.getUniqueId());
        return p != null && p.covers(spell, targetId);
    }

    private static RollService.RollResult attackRoll(Player player, CharacterSheet sheet, DndSpell spell, Advantage adv,
                                                     RollService.RollInput roll, boolean rerollNat1) {
        int mod = sheet.getProficiencyBonus() + modFor(sheet, spell);
        if (adv != Advantage.NONE) {
            player.sendMessage(Component.text("↯ You have " + adv.label() + " on this spell attack.",
                    adv.isAdvantage() ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        return RollService.resolve(roll, mod, signed(mod) + "[Spell]", rerollNat1, adv);
    }

    private static int modFor(CharacterSheet sheet, DndSpell spell) {
        Ability a = sheet.castingAbilityFor(spell);
        return a != null ? sheet.getModifier(a) : 0;
    }

    /** Spend the slot or use and handle concentration: only once the spell actually goes off. */
    public static void commit(Player player, CharacterSheet sheet, DndSpell spell, SpellCost cost) {
        if (spell.isConcentration() && sheet.isConcentrating()) {
            DndSpell was = sheet.getConcentratingOn();
            sheet.breakConcentration();
            player.sendMessage(Component.text("Concentration on " + was.getName() + " ends.", NamedTextColor.YELLOW));
        }
        cost.spend(sheet, spell);
        if (spell.isConcentration()) sheet.setConcentratingOn(spell);
        String spent = cost.spentLabel(sheet);
        if (!spent.isEmpty()) player.sendMessage(Component.text("   Spent " + spent + ".", NamedTextColor.GRAY));
    }

    private static void rollPrompt(Player player, String retry) {
        player.sendMessage(Component.text("Roll your d20: ", NamedTextColor.YELLOW)
                .append(fill("[I rolled…]", retry + "manualRoll "))
                .append(Component.text(" "))
                .append(fill("[Let the game roll]", retry + "autoRoll")));
    }

    /** The caster, every DM, and anyone within 30 blocks: an attack out of combat is public. */
    private static void tell(Player caster, Component message) {
        java.util.Set<UUID> told = new java.util.HashSet<>();
        caster.sendMessage(message);
        told.add(caster.getUniqueId());
        for (Player dm : DMManager.getOnlineDMs()) if (told.add(dm.getUniqueId())) dm.sendMessage(message);
        for (Player p : caster.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(caster.getLocation()) <= 900 && told.add(p.getUniqueId())) p.sendMessage(message);
        }
    }

    private static void toDms(Component message) {
        for (Player dm : DMManager.getOnlineDMs()) dm.sendMessage(message);
    }

    private static Component button(String text, NamedTextColor color, String hover, java.util.function.Consumer<net.kyori.adventure.audience.Audience> onClick) {
        return Component.text(text, color, TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text(hover)))
                .clickEvent(ClickEvent.callback(onClick::accept, ONCE));
    }

    private static Component fill(String text, String command) {
        return Component.text(text, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text("Fills: " + command)));
    }

    private static String quote(String name) { return name.contains(" ") ? "\"" + name + "\"" : name; }

    private static String signed(int n) { return n >= 0 ? "+" + n : String.valueOf(n); }

    private static Ability parseAbility(String name) {
        if (name == null) return null;
        Ability a = Ability.fromString(name.trim());
        if (a != null) return a;
        for (Ability ab : Ability.values()) if (ab.getAbbreviation().equalsIgnoreCase(name.trim())) return ab;
        return null;
    }
}
