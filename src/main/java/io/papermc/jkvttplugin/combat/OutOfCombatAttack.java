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
import java.util.Map;
import java.util.UUID;

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
            // Reach first: nobody should be asked to start a fight over a Shocking Grasp from 30 ft.
            if (!inRange(player, aim, spell, retry)) return true;
            // A creature or a character: that's a fight unless the DM says otherwise.
            if (!permitted(player, spell, aim.target.combatant().getId())) {
                String combatCmd = "/combat cast " + spell.getId() + " " + quote(aim.targetName())
                        + (castLevel != null ? " level " + castLevel : "") + " ";
                askDm(player, sheet.getCharacterName() + " wants to cast " + spell.getName() + " at " + aim.targetName(),
                        aim.target.combatant(), spell.getName() + " at " + aim.targetName(), combatCmd,
                        () -> grant(player, spell, aim.target.combatant().getId(), retry));
                return true;
            }
            if (!inRange(player, aim, spell, retry)) return true; // again: they may have walked off while the DM decided
            return resolveAtCreature(player, sheet, spell, aim, roll, cost, retry);
        }

        // Nothing living where they're looking: the torch on the wall, or just the wall.
        if (!permitted(player, spell, null)) {
            Component ask = Component.text("You're not aiming at a creature. Cast " + spell.getName() + " anyway? ", NamedTextColor.YELLOW)
                    .append(button("[Cast it]", NamedTextColor.GREEN, "Go ahead: roll to hit, and the DM decides what happens",
                            a -> { permits.put(player.getUniqueId(), new Permit(spell.getId(), null, System.currentTimeMillis()));
                                   rollPrompt(player, sheet, spell, retry, Advantage.NONE); }))
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
            Advantage adv = caster.attackAdvantageAgainst(target);
            RollService.RollResult r = attackRoll(player, sheet, spell, adv, roll, caster.rerollsNat1());
            if (r == null) { rollPrompt(player, sheet, spell, retry, adv); return true; }
            permits.remove(player.getUniqueId()); // one "let it happen" is one cast; the next asks again
            commit(player, sheet, spell, cost);
            int ac = target.getArmorClass();
            boolean hit = RollService.hits(r, ac);
            tell(player, spell.castLine("✨ " + who + " casts ", " at " + aim.targetName() + "!", NamedTextColor.LIGHT_PURPLE));
            SpellVisuals.play(spell, player.getLocation(), aim.target.combatant().getLocation()); // #230
            tell(player, Component.text("Spell attack: " + r.breakdown() + " vs AC " + ac + " — " + (hit ? (r.nat20() ? "CRITICAL HIT!" : "HIT!") : "MISS"),
                    hit ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (!r.nat20() && !r.nat1()) io.papermc.jkvttplugin.sound.Sounds.toPlayer(hit ? io.papermc.jkvttplugin.sound.Sounds.HIT : io.papermc.jkvttplugin.sound.Sounds.MISS, player); // #16
            if (hit) offerDamage(player, spell, r.nat20(), aim.target, null, false);
            else if (!r.nat1()) {
                // Bardic Inspiration after the roll (#40): a total that now reaches the AC is a hit after all.
                InspirationPrompt.offer(sheet, spell.getName() + " attack", r.total(), newTotal -> {
                    if (newTotal >= ac) {
                        tell(player, Component.text("   That's a hit (AC " + ac + ").", NamedTextColor.GREEN));
                        offerDamage(player, spell, false, aim.target, null, false);
                    } else {
                        tell(player, Component.text("   Still a miss (AC " + ac + ").", NamedTextColor.RED));
                    }
                }, msg -> tell(player, msg));
            }
            return true;
        }

        permits.remove(player.getUniqueId());
        commit(player, sheet, spell, cost);
        if (spell.isSaveSpell()) {
            Ability save = parseAbility(spell.getSaveType());
            int dc = sheet.getSpellSaveDc(spell);
            String abbr = save != null ? save.getAbbreviation() : spell.getSaveType();
            tell(player, spell.castLine("✨ " + who + " casts ", " at " + aim.targetName()
                    + " — DC " + sheet.getSpellSaveDcBreakdown(spell) + " " + abbr + " save!", NamedTextColor.LIGHT_PURPLE));
            SpellVisuals.play(spell, player.getLocation(), aim.target.combatant().getLocation()); // #230
            // What the graded save does (#245, #267): the same consequences as in a fight, through SpellSave:
            // the spell's effect (Bane) and its condition are applied, immunity is checked, and the damage
            // owed comes back for this path's own damage step. It used to offer the damage and leave the
            // condition as a note to the DM; an effect did nothing.
            SpellSave.Facts facts = SpellSave.Facts.of(spell, player.getUniqueId(), sheet.getCharacterId(), dc, save, java.util.Set.of());
            java.util.function.Consumer<Boolean> outcome = saved -> {
                SpellSave.Outcome o = SpellSave.apply(facts, SpellSave.subject(target, aim.target.session(), aim.targetName()), saved);
                if (!saved) {
                    tell(player, Component.text(aim.targetName() + " fails the save against " + spell.getName() + ".", NamedTextColor.RED));
                    for (Component line : o.effectLines()) tell(player, line);
                    if (o.damage() == SpellSave.Owed.FULL) offerDamage(player, spell, false, aim.target, null, false);
                    for (Component line : o.conditionLines()) tell(player, line);
                } else if (o.damage() == SpellSave.Owed.HALF) {
                    tell(player, Component.text(aim.targetName() + " saves: half damage.", NamedTextColor.YELLOW));
                    offerDamage(player, spell, false, aim.target, null, true);
                } else {
                    tell(player, Component.text(aim.targetName() + " saves, and resists " + spell.getName() + ".", NamedTextColor.GRAY));
                }
            };
            UUID saver = target.getId();
            SaveOutcome.await(saver, dc, outcome);

            // The DM's ruling without a roll stays possible, quietly, after the real buttons.
            Component rule = Component.text("  or rule it: ", NamedTextColor.DARK_GRAY)
                    .append(button("[failed]", NamedTextColor.DARK_GRAY, "No roll: they failed", a -> SaveOutcome.rule(saver, false)))
                    .append(Component.text(" "))
                    .append(button("[saved]", NamedTextColor.DARK_GRAY, "No roll: they saved", a -> SaveOutcome.rule(saver, true)));
            if (save == null) {
                toDms(Component.text("   DM: " + aim.targetName() + " makes a DC " + dc + " " + abbr + " save.", NamedTextColor.GRAY).append(rule));
                return true;
            }
            String check = "dm check " + quote(aim.targetName()) + " save " + save.getAbbreviation().toLowerCase() + " dc " + dc;
            if (target.isPlayer()) {
                // A player rolls their own: one button sends them the roll buttons, and the result comes back.
                toDms(Component.text("   DM: " + aim.targetName() + " makes a DC " + dc + " " + abbr + " save ("
                                + target.saveBreakdown(save) + "). ", NamedTextColor.GRAY)
                        .append(button("[Ask for their roll]", NamedTextColor.AQUA,
                                "They get [Roll it] [I rolled…] [My total…]; the result decides the damage",
                                a -> { if (a instanceof Player dm) dm.performCommand(check); }))
                        .append(rule));
            } else {
                // A creature's save is the DM's roll: the three roll buttons, right here.
                for (Player dm : DMManager.getOnlineDMs()) {
                    dm.performCommand(check);
                    dm.sendMessage(rule);
                }
            }
            return true;
        }

        // Hits automatically (Magic Missile) or just deals damage.
        tell(player, spell.castLine("✨ " + who + " casts ", " at " + aim.targetName() + ".", NamedTextColor.LIGHT_PURPLE));
        SpellVisuals.play(spell, player.getLocation(), aim.target.combatant().getLocation()); // #230
        offerDamage(player, spell, false, aim.target, null, false);
        return true;
    }

    private static boolean resolveAtThing(Player player, CharacterSheet sheet, DndSpell spell, Aim aim,
                                          RollService.RollInput roll, SpellCost cost, String retry) {
        String who = sheet.getCharacterName();
        String at = aim.objectLabel != null ? aim.objectLabel : "nothing in particular";
        if (spell.isAttackRoll()) {
            RollService.RollResult r = attackRoll(player, sheet, spell, Advantage.NONE, roll, false);
            if (r == null) { rollPrompt(player, sheet, spell, retry, Advantage.NONE); return true; }
            commit(player, sheet, spell, cost);
            permits.remove(player.getUniqueId());
            tell(player, spell.castLine("✨ " + who + " casts ", " at " + at + ": "
                    + r.breakdown() + " to hit.", NamedTextColor.LIGHT_PURPLE));
        } else {
            commit(player, sheet, spell, cost);
            permits.remove(player.getUniqueId());
            tell(player, spell.castLine("✨ " + who + " casts ", " at " + at + ".", NamedTextColor.LIGHT_PURPLE));
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
        if (aim.target != null && !inRange(player, aim, spell, "/character cast " + spell.getId() + " " + quote(name))) return true;
        // The same healing roll as in a fight (SpellCastHandler.healRoll).
        SpellCastHandler.HealRoll heal = SpellCastHandler.healRoll(sheet, spell, roll.providedRoll(), roll.providedTotal(), roll.forceAuto());
        if (heal == null) {
            String cmd = "/character cast " + spell.getId() + (aim.target != null ? " " + quote(name) : "") + " ";
            player.sendMessage(RollPrompt.line("💚 Roll " + spell.getName() + " (" + spell.getHealing() + "):", NamedTextColor.GREEN,
                    cmd, SpellCastHandler.healDice(spell), SpellCastHandler.healBonus(sheet, spell)));
            return true;
        }
        int amount = heal.amount();
        tell(player, Component.text(heal.work(), NamedTextColor.GRAY));
        commit(player, sheet, spell, cost);
        tell(player, spell.castLine("✨ " + sheet.getCharacterName() + " casts ", " on " + name + ".", NamedTextColor.LIGHT_PURPLE));
        SpellVisuals.play(spell, player.getLocation(), target.combatant().getLocation()); // #230
        DamageHandler.applyHealing(target.session(), target.combatant(), Math.max(1, amount));
        return true;
    }

    // ==================== DAMAGE ====================

    /** Hand the caster their damage roll: {@code /character damage}. */
    private static void offerDamage(Player caster, DndSpell spell, boolean crit, CombatTargets.Target target,
                                    String objectLabel, boolean half) {
        String dice = spell.getDamage() == null ? "" : (crit ? AttackHandler.doubleDice(spell.getDamage()) : spell.getDamage());
        pendingDamage.put(caster.getUniqueId(), new PendingDamage(spell.getName(), dice, spell.getDamageType(), target, objectLabel, half));
        // Roll the dice; the spell's own flat part (Magic Missile's +1) is added, labelled with the spell.
        RollPrompt.Formula f = RollPrompt.split(dice, spell.getName());
        caster.sendMessage(RollPrompt.line("💥 Roll " + (dice.isBlank() ? "damage" : dice) + (half ? " (halved)" : "") + " for "
                + spell.getName() + ":", NamedTextColor.YELLOW, "/character damage ", dice.isBlank() ? "the damage" : f.dice(), f.label()));
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
        String work = null;

        // Damage here is only rolled by the game on an explicit autoRoll, whatever the server's roll mode:
        // with no roll words, a bare number is the amount (see the DiceAmount tests for how the others differ).
        DiceAmount.Result rolled = DiceAmount.resolve(in, p.dice(), p.source(), 0, null, false);
        if (rolled.status() == DiceAmount.Status.BAD_DICE) {
            player.sendMessage(Component.text("There are no dice to roll — type the amount.", NamedTextColor.RED));
            return;
        }
        if (rolled.ok()) { amount = rolled.amount(); work = rolled.work(); }
        else if (args.length > 0) {
            try { amount = Integer.parseInt(args[args.length - 1].trim()); work = RollPrompt.yourTotal(amount); } catch (NumberFormatException ignored) {}
        }
        if (amount == null) {
            player.sendMessage(Component.text("Usage: /character damage autoRoll | manualRoll <n> | total <n>", NamedTextColor.RED));
            return;
        }
        tell(player, Component.text(work, NamedTextColor.GRAY));
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
        if (DMManager.getOnlineDMs().isEmpty()) {
            attacker.sendMessage(Component.text("Attacking outside a fight needs the DM, and no DM is online.", NamedTextColor.RED));
            return;
        }
        UUID attackerId = attacker.getUniqueId();
        String targetArg = target.isPlayer() && target.getPlayer() != null ? target.getPlayer().getName() : target.getDisplayName();

        Component ask = Component.text("⚔ " + what + ". You're not in a fight. ", NamedTextColor.GOLD)
                .append(DmRequests.button(attackerId, "[Start combat]", NamedTextColor.RED, "Start a fight with both of them. Add anyone else and mark "
                        + "who's surprised before rolling initiative. The attack comes back on their first turn.",
                        dm -> startCombat(dm, attackerId, targetArg, label, heldCommand)))
                .append(Component.text(" "));
        if (letItHappen != null) {
            ask = ask.append(DmRequests.button(attackerId, "[Let it happen]", NamedTextColor.GREEN, "Resolve it once, no fight: attack roll, then damage",
                    dm -> letItHappen.run())).append(Component.text(" "));
        }
        ask = ask.append(DmRequests.button(attackerId, "[Deny]", NamedTextColor.GRAY, "It doesn't happen", dm -> {
            Player p = Bukkit.getPlayer(attackerId);
            if (p != null) p.sendMessage(Component.text("The DM stops that.", NamedTextColor.GRAY));
        }));
        DmRequests.send(attacker, label, "You're not in a fight — asking the DM.", ask);
    }

    private static void grant(Player caster, DndSpell spell, UUID targetId, String retry) {
        permits.put(caster.getUniqueId(), new Permit(spell.getId(), targetId, System.currentTimeMillis()));
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(caster);
        // Only an attack spell rolls to hit; a save spell (Sacred Flame) just goes off. Say which, or the
        // two read as random: one comes back as roll buttons, the other as the cast command (#247).
        if (spell.isAttackRoll() && sheet != null) {
            caster.sendMessage(Component.text("The DM lets it happen. " + spell.getName() + " is an attack: roll to hit.", NamedTextColor.GREEN));
            rollPrompt(caster, sheet, spell, retry, Advantage.NONE);
        } else {
            caster.sendMessage(Component.text("The DM lets it happen. " + spell.getName()
                    + (spell.isSaveSpell() ? " needs no attack roll (the target saves instead): cast it again to go."
                                           : " needs no roll from you: cast it again to go."), NamedTextColor.GREEN)
                    .append(Component.text(" ")).append(fill("[cast it]", retry.trim())));
        }
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
                + "Mark them surprised (the Surprise tool, or /combat surprise <who>). Then roll initiative.", NamedTextColor.YELLOW));
        attacker.sendMessage(Component.text("It's a fight! Your " + label + " comes back to you on your first turn.", NamedTextColor.GOLD));
    }

    /** Called at the start of each turn: hand an attacker back the move that started the fight. */
    public static void offerOpening(Combatant current) {
        if (current == null || !current.isPlayer()) return;
        Opening o = openings.remove(current.getId());
        Player p = current.getPlayer();
        if (o == null || p == null || System.currentTimeMillis() - o.at() > Duration.ofMinutes(30).toMillis()) return;
        TurnState state = current.getTurnState();
        if (state != null) state.setOpening(o.label(), o.command()); // their Action is spoken for (openingHolds)
        // A tick later, so it's the LAST thing on screen: sent now it sat above the initiative order, the
        // round banner and "you get one action…", and was scrolled past (playtest).
        Bukkit.getScheduler().runTask(io.papermc.jkvttplugin.JkVttPlugin.getInstance(), () -> {
            p.sendMessage(Component.empty());
            p.sendMessage(Component.text("⚔ You started this fight with " + o.label() + ". ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(fill("[Do it now]", o.command())));
            p.sendMessage(Component.text("   That's your Action this turn. It fills the command; you then roll as usual.", NamedTextColor.GRAY));
            p.sendMessage(Component.empty());
        });
    }

    /** Whether a typed command is the opening move itself (the roll words after it don't matter). */
    static boolean isOpening(String openingCommand, String typed) {
        if (openingCommand == null || typed == null) return false;
        String want = openingCommand.trim().replaceAll("\\s+", " ").toLowerCase();
        String got = typed.trim().replaceAll("\\s+", " ").toLowerCase();
        if (!got.startsWith("/")) got = "/" + got;
        return got.equals(want) || got.startsWith(want + " ");
    }

    /**
     * The move that started the fight comes first (playtest): on that first turn, an attack, cast, action
     * or feature that isn't it is held, with [Do it now], and [Ask the DM] to be let off ([Something else]
     * for a DM). Making the opening move, or being let off, clears it. A bare {@code /combat attack} (the
     * list of options) is never held. Returns true when the command was held.
     */
    public static boolean openingHolds(Player player, String[] args) {
        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        Combatant current = session != null ? session.getCurrentCombatant() : null;
        if (current == null || !current.isPlayer() || !current.getId().equals(player.getUniqueId())) return false;
        TurnState state = current.getTurnState();
        if (state == null || state.getOpeningCommand() == null) return false;
        if (state.isActionUsed()) { state.clearOpening(); return false; } // the Action is gone: nothing left to hold
        if (args.length < 2) return false; // just looking at the options
        String typed = "/combat " + String.join(" ", args);
        if (isOpening(state.getOpeningCommand(), typed)) {
            // Without roll words the roll prompt comes next and the move is still owed; with them it's being made.
            for (String a : args) if (RollService.isRollKeyword(a)) { state.clearOpening(); break; }
            return false;
        }
        String label = state.getOpeningLabel(), command = state.getOpeningCommand();
        UUID id = player.getUniqueId();
        var once = net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();
        Component line = Component.text("You started this fight with " + label + ": that's your Action this turn. ", NamedTextColor.YELLOW)
                .append(fill("[Do it now]", command)).append(Component.text(" "));
        if (DMManager.isDM(player)) {
            line = line.append(Component.text("[Something else]", NamedTextColor.GOLD, TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(Component.text("DM: drop the opening move and do this instead")))
                    .clickEvent(ClickEvent.callback(a -> { state.clearOpening(); player.performCommand(typed.substring(1)); }, once)));
        } else {
            line = line.append(Component.text("[Ask the DM]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(Component.text("Ask the DM to let you do something else instead")))
                    .clickEvent(ClickEvent.callback(a -> {
                        if (!DmRequests.anyDmOnline(player)) return;
                        Component toDm = Component.text("⚔ " + player.getName() + " started the fight with " + label
                                + " and wants to do something else instead (" + typed + ") ", NamedTextColor.GOLD)
                                .append(DmRequests.button(id, "[Allow]", NamedTextColor.GREEN, "Drop the opening move", dm -> {
                                    state.clearOpening();
                                    Player p = Bukkit.getPlayer(id);
                                    if (p != null) p.sendMessage(Component.text("The DM lets you do something else. ", NamedTextColor.GREEN)
                                            .append(fill("[go again]", typed)));
                                }))
                                .append(Component.text(" "))
                                .append(DmRequests.button(id, "[Deny]", NamedTextColor.GRAY, "They make the opening move", dm -> {
                                    Player p = Bukkit.getPlayer(id);
                                    if (p != null) p.sendMessage(Component.text("The DM says: make the move you started with. ", NamedTextColor.GRAY)
                                            .append(fill("[Do it now]", command)));
                                }));
                        DmRequests.send(player, "something else than " + label, "Asked the DM.", toDm);
                    }, once)));
        }
        player.sendMessage(line);
        return true;
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

    /**
     * Does the spell reach? The one rule ({@link Reach}), with the DM's override: when it doesn't,
     * the caster is told why and offered [Ask the DM] (or [Do it anyway], as a DM).
     */
    private static boolean inRange(Player player, Aim aim, DndSpell spell, String retry) {
        Combatant target = aim.target.combatant();
        String why = Reach.spell(player.getLocation(), target.getLocation(), aim.targetName(),
                target.getId().equals(player.getUniqueId()), spell);
        if (why == null || Reach.isAllowed(player.getUniqueId(), "spell:" + spell.getId(), target.getId())) return true;
        Reach.refuse(player, why, "spell:" + spell.getId(), target.getId(), spell.getName() + " on " + aim.targetName(), retry.trim());
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
        RollService.RollResult r = RollService.resolve(roll, mod, sheet.getSpellAttackBreakdown(spell), rerollNat1, adv);
        if (r != null) SpellEffects.useUp(sheet, io.papermc.jkvttplugin.effect.ActiveEffect.ATTACKS); // a creature's Bardic Inspiration (a character's is asked for after the roll, #40)
        return r;
    }

    private static int modFor(CharacterSheet sheet, DndSpell spell) {
        Ability a = sheet.castingAbilityFor(spell);
        return a != null ? sheet.getModifier(a) : 0;
    }

    /** Spend the slot or use and handle concentration: only once the spell actually goes off. */
    public static void commit(Player player, CharacterSheet sheet, DndSpell spell, SpellCost cost) {
        Reach.spend(player.getUniqueId()); // a DM "close enough" covers this one cast
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

    private static void rollPrompt(Player player, CharacterSheet sheet, DndSpell spell, String retry, Advantage adv) {
        player.sendMessage(RollPrompt.line("🎲 Roll to hit with " + spell.getName() + ":", NamedTextColor.YELLOW, retry, RollPrompt.d20(adv),
                sheet.getSpellAttackBreakdown(spell)));
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

    private static Ability parseAbility(String name) {
        if (name == null) return null;
        Ability a = Ability.fromString(name.trim());
        if (a != null) return a;
        for (Ability ab : Ability.values()) if (ab.getAbbreviation().equalsIgnoreCase(name.trim())) return ab;
        return null;
    }
}
